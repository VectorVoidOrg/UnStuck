from dataclasses import dataclass, field
from typing import List, Optional
from app.config import settings
from app.schemas import AgentVerdictSchema, ResolutionAction


@dataclass
class PolicyGuardrailDecision:
    approved_action: ResolutionAction
    approved_refund_inr: float
    place_lien: bool
    guardrail_intervened: bool = False
    escalate_to_hitl: bool = False
    hitl_reason: Optional[str] = None
    notes: List[str] = field(default_factory=list)


def apply_deterministic_policy_guardrail(
    verdict: AgentVerdictSchema,
    disputed_amount_inr: float,
    utr_verified_in_ledger: Optional[bool],
    prompt_injection_detected: bool,
) -> PolicyGuardrailDecision:
    """
    Step 5B Resilience #3: Deterministic Policy Guardrail.
    Hard, non-LLM safety rules sitting AFTER the Fraud Investigation Agent Core
    and BEFORE Layer 4 NPCI & Bank Core Tools:
      Rule A (₹5,000 Auto-Refund Cap): Strictly blocks automated refunds exceeding ₹5,000.
              Any refund > ₹5,000 still places an immediate protective NPCI Lien on the scammer
              account, but routes the refund release to Human-In-The-Loop (HITL).
      Rule B (Fake Receipt / Unverified UTR Protection): Strictly blocks auto-refunds if the
              12-digit UTR is missing or fails verification in the NPCI settlement ledger
              (prevents Failure #3: Hallucinated Auto-Refund on Fake Receipt).
      Rule C (Prompt Injection Quarantine): Blocks auto-refund if adversarial prompt injection
              was detected in the screenshot OCR.
      Rule D (Confidence Threshold): Routes verdicts with confidence < 0.75 to HITL audit.
    """
    notes: List[str] = []
    intervened = False
    escalate_hitl = False
    hitl_reason: Optional[str] = None

    action = verdict.recommended_action
    refund_inr = float(verdict.recommended_refund_inr)
    place_lien = bool(verdict.place_lien)

    # Rule D: Low Confidence Check (< 0.75) -> Escalate to HITL
    if verdict.confidence < settings.MIN_AGENT_CONFIDENCE_FOR_AUTO_ACTION:
        intervened = True
        escalate_hitl = True
        hitl_reason = (
            f"Low agent confidence ({verdict.confidence:.2f} < {settings.MIN_AGENT_CONFIDENCE_FOR_AUTO_ACTION})"
        )
        notes.append(f"Guardrail Rule D: {hitl_reason}. Routed to HITL queue.")
        return PolicyGuardrailDecision(
            approved_action=ResolutionAction.ESCALATE_TO_HITL,
            approved_refund_inr=0.0,
            place_lien=place_lien,
            guardrail_intervened=True,
            escalate_to_hitl=True,
            hitl_reason=hitl_reason,
            notes=notes,
        )

    # Rule C: Prompt Injection Quarantine
    if prompt_injection_detected and action == ResolutionAction.AUTO_REFUND:
        intervened = True
        escalate_hitl = True
        hitl_reason = "Adversarial Prompt Injection detected in screenshot OCR; auto-refund quarantined"
        notes.append(f"Guardrail Rule C: {hitl_reason}.")
        return PolicyGuardrailDecision(
            approved_action=ResolutionAction.ESCALATE_TO_HITL,
            approved_refund_inr=0.0,
            place_lien=False,
            guardrail_intervened=True,
            escalate_to_hitl=True,
            hitl_reason=hitl_reason,
            notes=notes,
        )

    # Rule B: Fake Receipt / Unverified UTR Block (Prevents Failure #3)
    if utr_verified_in_ledger is False and (action == ResolutionAction.AUTO_REFUND or refund_inr > 0):
        intervened = True
        notes.append(
            "Guardrail Rule B: Blocked hallucinated/requested auto-refund because UTR failed NPCI ledger verification (Fake Receipt protection)."
        )
        return PolicyGuardrailDecision(
            approved_action=ResolutionAction.BLOCK_SCAM,
            approved_refund_inr=0.0,
            place_lien=False,
            guardrail_intervened=True,
            escalate_to_hitl=False,
            notes=notes,
        )

    # Rule A: Strict ₹5,000 Auto-Refund Policy Cap (Slide 11 & 12)
    effective_amount = max(disputed_amount_inr, refund_inr)
    if (action == ResolutionAction.AUTO_REFUND or verdict.is_scam) and effective_amount > settings.MAX_AUTO_REFUND_INR:
        intervened = True
        escalate_hitl = True
        hitl_reason = (
            f"Disputed refund ₹{effective_amount:,.2f} exceeds hard deterministic auto-refund cap of ₹{settings.MAX_AUTO_REFUND_INR:,.2f}"
        )
        notes.append(
            f"Guardrail Rule A (₹5k Cap): Blocked auto-refund of ₹{effective_amount:,.2f} (> ₹{settings.MAX_AUTO_REFUND_INR:,.0f} limit). "
            f"Protective NPCI Lien authorized; refund escalated to HITL officer."
        )
        return PolicyGuardrailDecision(
            approved_action=ResolutionAction.PLACE_LIEN,
            approved_refund_inr=0.0,
            place_lien=True,
            guardrail_intervened=True,
            escalate_to_hitl=True,
            hitl_reason=hitl_reason,
            notes=notes,
        )

    # Passed all deterministic safety guardrails
    if action == ResolutionAction.AUTO_REFUND:
        approved_refund = min(disputed_amount_inr, settings.MAX_AUTO_REFUND_INR)
        notes.append(
            f"Guardrail Verified: Auto-refund of ₹{approved_refund:,.2f} is within ₹{settings.MAX_AUTO_REFUND_INR:,.0f} cap and ledger checks passed."
        )
    else:
        approved_refund = 0.0
        notes.append("Guardrail Verified: Non-refund protective action authorized.")

    return PolicyGuardrailDecision(
        approved_action=action,
        approved_refund_inr=approved_refund,
        place_lien=place_lien,
        guardrail_intervened=intervened,
        escalate_to_hitl=escalate_hitl,
        hitl_reason=hitl_reason,
        notes=notes,
    )
