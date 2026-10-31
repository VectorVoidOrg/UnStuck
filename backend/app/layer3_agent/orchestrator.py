import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from app.db.mongo import investigations_collection
from app.layer2_privacy.ocr_masker import sanitize_and_mask_ocr
from app.layer2_privacy.pii_vault import pii_vault
from app.layer3_agent.complexity_router import complexity_router
from app.layer3_agent.frontier_agent import frontier_agent
from app.layer3_agent.semantic_cache import semantic_cache
from app.layer3_agent.slm_engine import slm_engine
from app.layer4_tools.npci_bank_tools import (
    BankAPITimeout504Error,
    npci_bank_tools,
)
from app.layer5_resilience.audit_service import audit_service
from app.layer5_resilience.circuit_breaker import (
    CircuitBreakerTrippedError,
    TokenBudgetCircuitBreaker,
)
from app.layer5_resilience.hitl_dlq import hitl_dlq_service
from app.layer5_resilience.policy_guardrail import (
    apply_deterministic_policy_guardrail,
)
from app.schemas import (
    AgentVerdictSchema,
    DisputeInvestigationRequest,
    InvestigationRecord,
    InvestigationTier,
    NPCIExecutionResult,
    ResolutionAction,
)


class FraudInvestigationOrchestrator:
    """
    Component 3, 4 & 5 End-to-End Orchestrator:
      1. Edge OCR Masking & PII Redaction Vault (Layer 2)
      2. Semantic Cache (<10ms lookup) (Layer 3.1)
      3. Complexity Router (80% SLM vs 20% Frontier LLM + RBI RAG) (Layer 3.2)
      4. Token Budget Circuit Breaker (Max 3 loops) (Layer 5 Resilience #2)
      5. Deterministic Policy Guardrail (₹5,000 Auto-Refund Cap & Ledger UTR Check) (Layer 5 Resilience #3)
      6. Idempotent NPCI & Bank Core Tools Execution (Layer 4)
      7. HITL & Dead Letter Queue on 504 Timeouts / Low Confidence (Layer 5 Resilience #4)
      8. Immutable Compliance Audit Logging (Component 5)
    """

    def run_investigation(
        self,
        req: DisputeInvestigationRequest,
        investigation_id: Optional[str] = None,
    ) -> InvestigationRecord:
        start_ns = time.perf_counter_ns()
        inv_id = investigation_id or f"INV-{uuid.uuid4().hex[:10].upper()}"
        created_at = datetime.now(timezone.utc).isoformat()

        # ------------------------------------------------------------------
        # STEP A: Layer 2 — Local/Edge OCR Masking & PII Redaction Token Vault
        # ------------------------------------------------------------------
        redacted_desc, desc_tokens = pii_vault.redact_text(req.scam_description)
        ocr_res = sanitize_and_mask_ocr(
            req.screenshot_ocr_text or "",
            client_side_masked=req.client_side_masked,
        )
        combined_tokens = {**desc_tokens, **ocr_res.pii_tokens_map}
        redacted_payer, _ = pii_vault.redact_text(req.payer_vpa)
        redacted_payee, _ = pii_vault.redact_text(req.payee_vpa)

        full_redacted_context = redacted_desc
        if ocr_res.sanitized_text:
            full_redacted_context += f" | [OCR_RECEIPT]: {ocr_res.sanitized_text}"

        # Verify UTR in NPCI Ledger before allowing any refund decision
        utr_verified = npci_bank_tools.verify_utr_in_ledger(req.utr_number, req.amount)

        # ------------------------------------------------------------------
        # STEP B: Layer 3.1 — Semantic Cache (<10ms)
        # ------------------------------------------------------------------
        cached_verdict, sim_score, _ = semantic_cache.lookup(full_redacted_context, req.amount)
        cache_hit = False
        tier_used = InvestigationTier.SEMANTIC_CACHE
        loops_used = 0
        tokens_consumed = 0
        cb_tripped = False
        verdict: Optional[AgentVerdictSchema] = None

        if cached_verdict is not None and not ocr_res.prompt_injection_detected and utr_verified is not False:
            cache_hit = True
            verdict = cached_verdict
        else:
            # ------------------------------------------------------------------
            # STEP C: Layer 3.2 — Complexity Router (80% SLM vs 20% Frontier+RAG)
            # ------------------------------------------------------------------
            tier_used, _, _ = complexity_router.route(
                redacted_context=full_redacted_context,
                amount_inr=req.amount,
                has_screenshot_ocr=bool(req.screenshot_ocr_text),
                has_utr=bool(req.utr_number),
                prompt_injection_detected=ocr_res.prompt_injection_detected,
            )

            # Initialize Layer 5 Token Budget Circuit Breaker (Max 3 loops)
            cb = TokenBudgetCircuitBreaker()

            try:
                if "loop_explosion_test" in full_redacted_context.lower():
                    # Demonstration trigger for Failure #2 -> Resilience #2 (Circuit Breaker)
                    for step_i in range(1, 5):
                        cb.record_step(loop_index=step_i, tokens_delta=900)

                if tier_used == InvestigationTier.SMALL_QUANTIZED_MODEL:
                    cb.record_step(loop_index=1, tokens_delta=280)
                    verdict, slm_tokens, _ = slm_engine.analyze_routine(
                        redacted_context=full_redacted_context,
                        amount_inr=req.amount,
                        metadata={"active_call": req.active_call_or_screen_share},
                    )
                    loops_used = 1
                    tokens_consumed = slm_tokens
                else:
                    verdict, loops_used, tokens_consumed, _ = frontier_agent.investigate_complex_dispute(
                        redacted_context=full_redacted_context,
                        amount_inr=req.amount,
                        utr_verified_in_ledger=utr_verified,
                        prompt_injection_detected=ocr_res.prompt_injection_detected,
                        circuit_breaker_loop_callback=cb.record_step,
                    )

                # Store high-confidence non-injected verdicts into Semantic Cache
                if verdict and verdict.is_scam and not ocr_res.prompt_injection_detected:
                    semantic_cache.store(full_redacted_context, verdict)

            except CircuitBreakerTrippedError as cb_err:
                cb_tripped = True
                loops_used = cb.loops_used
                tokens_consumed = cb.tokens_used
                hitl_id = hitl_dlq_service.enqueue_ticket(
                    investigation_id=inv_id,
                    queue_type="DLQ_CIRCUIT_BREAKER",
                    reason=str(cb_err),
                    payer_vpa=redacted_payer,
                    payee_vpa=req.payee_vpa,
                    amount_inr=req.amount,
                    redacted_context=full_redacted_context,
                )
                elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0
                record = InvestigationRecord(
                    investigation_id=inv_id,
                    status="ESCALATED_HITL",
                    tier_used=tier_used,
                    cache_hit=False,
                    redacted_context=full_redacted_context,
                    pii_tokens_created=len(combined_tokens),
                    prompt_injection_detected=ocr_res.prompt_injection_detected,
                    reasoning_loops_used=loops_used,
                    tokens_consumed=tokens_consumed,
                    circuit_breaker_tripped=True,
                    agent_verdict=None,
                    policy_guardrail_applied=True,
                    policy_guardrail_notes=[str(cb_err)],
                    final_action=ResolutionAction.ESCALATE_TO_HITL,
                    npci_tool_results=[],
                    hitl_ticket_id=hitl_id,
                    total_processing_ms=round(elapsed_ms, 3),
                    created_at=created_at,
                )
                self._save_record(record)
                return record

        # ------------------------------------------------------------------
        # STEP D: Layer 5 Resilience #3 — Deterministic Policy Guardrail (₹5k Cap)
        # ------------------------------------------------------------------
        guardrail = apply_deterministic_policy_guardrail(
            verdict=verdict,
            disputed_amount_inr=req.amount,
            utr_verified_in_ledger=utr_verified,
            prompt_injection_detected=ocr_res.prompt_injection_detected,
        )

        # ------------------------------------------------------------------
        # STEP E: Layer 4 — NPCI & Bank Core Tools + Resilience #4 (504 -> DLQ)
        # ------------------------------------------------------------------
        tool_results: List[NPCIExecutionResult] = []
        hitl_ticket_id: Optional[str] = None
        record_status = "COMPLETED"
        final_action = guardrail.approved_action

        try:
            if guardrail.place_lien:
                lien_res = npci_bank_tools.place_npci_lien(
                    investigation_id=inv_id,
                    payee_vpa=req.payee_vpa,
                    amount_inr=req.amount,
                    reason=verdict.scam_category,
                )
                tool_results.append(lien_res)

            if guardrail.approved_action == ResolutionAction.AUTO_REFUND and guardrail.approved_refund_inr > 0:
                refund_res = npci_bank_tools.execute_auto_refund(
                    investigation_id=inv_id,
                    payer_vpa=req.payer_vpa,
                    payee_vpa=req.payee_vpa,
                    amount_inr=guardrail.approved_refund_inr,
                )
                tool_results.append(refund_res)

        except BankAPITimeout504Error as timeout_err:
            # Step 5B Resilience #4: Route NPCI/Bank API 504 Timeout to Dead Letter Queue (DLQ)
            record_status = "DLQ_504"
            final_action = ResolutionAction.ESCALATE_TO_HITL
            guardrail.notes.append(f"Resilience #4 (DLQ): {str(timeout_err)} -> Routed to HITL & Dead Letter Queue.")
            hitl_ticket_id = hitl_dlq_service.enqueue_ticket(
                investigation_id=inv_id,
                queue_type="DLQ_BANK_504",
                reason=str(timeout_err),
                payer_vpa=redacted_payer,
                payee_vpa=req.payee_vpa,
                amount_inr=req.amount,
                redacted_context=full_redacted_context,
                agent_verdict_dict=verdict.model_dump() if verdict else {},
            )

        # If Policy Guardrail requested HITL escalation (e.g., > ₹5,000 refund cap or low confidence)
        if guardrail.escalate_to_hitl and hitl_ticket_id is None:
            record_status = "ESCALATED_HITL"
            queue_type = (
                "HITL_POLICY_CAP"
                if req.amount > 5000.0
                else "HITL_LOW_CONFIDENCE"
            )
            hitl_ticket_id = hitl_dlq_service.enqueue_ticket(
                investigation_id=inv_id,
                queue_type=queue_type,
                reason=guardrail.hitl_reason or "Escalated by Deterministic Policy Guardrail",
                payer_vpa=redacted_payer,
                payee_vpa=redacted_payee,
                amount_inr=req.amount,
                redacted_context=full_redacted_context,
                agent_verdict_dict=verdict.model_dump() if verdict else {},
            )

        elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0
        record = InvestigationRecord(
            investigation_id=inv_id,
            status=record_status,
            tier_used=tier_used,
            cache_hit=cache_hit,
            redacted_context=full_redacted_context,
            pii_tokens_created=len(combined_tokens),
            prompt_injection_detected=ocr_res.prompt_injection_detected,
            reasoning_loops_used=loops_used,
            tokens_consumed=tokens_consumed,
            circuit_breaker_tripped=cb_tripped,
            agent_verdict=verdict,
            policy_guardrail_applied=guardrail.guardrail_intervened,
            policy_guardrail_notes=guardrail.notes,
            final_action=final_action,
            npci_tool_results=tool_results,
            hitl_ticket_id=hitl_ticket_id,
            total_processing_ms=round(elapsed_ms, 3),
            created_at=created_at,
        )
        self._save_record(record)
        return record

    def _save_record(self, record: InvestigationRecord) -> None:
        investigations_collection.update_one(
            {"investigation_id": record.investigation_id},
            {"$set": record.model_dump()},
            upsert=True,
        )
        audit_service.record_event(
            event_type="AGENT_INVESTIGATION_COMPLETED",
            reference_id=record.investigation_id,
            summary=(
                f"Tier={record.tier_used.value} | Status={record.status} | "
                f"Action={record.final_action.value} | Guardrail={record.policy_guardrail_applied}"
            ),
            metadata={
                "cache_hit": record.cache_hit,
                "loops": record.reasoning_loops_used,
                "tokens": record.tokens_consumed,
                "hitl_ticket_id": record.hitl_ticket_id,
                "processing_ms": record.total_processing_ms,
            },
        )


agent_orchestrator = FraudInvestigationOrchestrator()
