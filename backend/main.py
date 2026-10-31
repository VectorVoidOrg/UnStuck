import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional

# Ensure `backend/` is on sys.path whether invoked from root or `backend/`
_BACKEND_DIR = Path(__file__).resolve().parent
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.config import settings
from app.db.mongo import (
    db,
    get_db_status,
    transactions_collection,
    rbi_guidelines_collection,
)
from app.layer1_gateway.reputation_service import update_history, update_reputation
from app.layer1_gateway.rule_engine import evaluate_transaction_sync
from app.layer2_privacy.ocr_masker import sanitize_and_mask_ocr
from app.layer2_privacy.pii_vault import pii_vault
from app.layer3_agent.orchestrator import agent_orchestrator
from app.layer5_resilience.async_queue import async_event_queue
from app.layer5_resilience.audit_service import audit_service
from app.layer5_resilience.circuit_breaker import global_token_governor
from app.layer5_resilience.hitl_dlq import hitl_dlq_service
from app.schemas import (
    DisputeInvestigationRequest,
    HITLReviewDecisionRequest,
    TransactionRequest,
    TransactionResponse,
)

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description=(
        "UFO (Unified Fraud Observer) — Production-Grade 5-Layer Agentic UPI Fraud & Scam Resolution System.\n\n"
        "- **Layer 1**: API Gateway & Deterministic Rule + Fast ML Engine (`<50ms` sync path, 95% traffic never hits an LLM)\n"
        "- **Layer 2**: Edge OCR Masking & Reversible PII Redaction Token Vault (Zero Raw PII to Cloud / RBI Compliant)\n"
        "- **Layer 3**: Fraud Investigation Agent Core (`10ms` Semantic Cache ➡ Complexity Router ➡ 80% SLM / 20% Frontier LLM + RBI RAG)\n"
        "- **Layer 4**: NPCI & Bank Core Tools (Strict JSON Schema + Idempotent Lien Freeze & Settlement Reversal)\n"
        "- **Layer 5**: Resilience & Guardrails (Max 3-Loop Token Budget Circuit Breaker, Hard ₹5,000 Auto-Refund Policy Cap, Async Event Queue, HITL & Dead Letter Queue)"
    ),
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class OCRRedactionRequest(BaseModel):
    ocr_text: str = Field(..., description="Raw text extracted from payment screenshot or SMS")
    client_side_masked: bool = Field(default=False, description="Whether on-device masking was already run")


class BehaviorTelemetryRequest(BaseModel):
    user_id: str
    session_id: Optional[str] = None
    copy_pasted_vpa: bool = False
    hesitation_ms: int = 0
    active_phone_call: bool = False
    screen_sharing_detected: bool = False
    device_changed: bool = False


# =====================================================================
# Root & System Health Endpoints
# =====================================================================

@app.get("/")
def home() -> Dict[str, Any]:
    return {
        "message": "UFO Fraud Detection Backend Running",
        "project": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "architecture": "V4 Production-Grade 5-Layer Agentic System",
    }


@app.get("/health")
def system_health() -> Dict[str, Any]:
    return {
        "status": "HEALTHY",
        "project": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "sla_targets": {
            "sync_payment_check_ms": f"<{settings.SYNC_LATENCY_SLA_MS}ms",
            "max_auto_refund_cap_inr": settings.MAX_AUTO_REFUND_INR,
            "max_agent_reasoning_loops": settings.MAX_REASONING_LOOPS,
            "min_confidence_for_auto_action": settings.MIN_AGENT_CONFIDENCE_FOR_AUTO_ACTION,
        },
        "database": get_db_status(),
        "async_queue": async_event_queue.get_metrics(),
        "token_governor_last_60s": global_token_governor.current_usage(),
    }


# =====================================================================
# Layer 1: Sync Payment Check (<50ms Critical Path) + Async Escalation
# =====================================================================

@app.post("/predict", response_model=TransactionResponse)
def predict(data: TransactionRequest) -> Dict[str, Any]:
    """
    Step 1 & Step 4 Sync HTTP Endpoint (<150ms UX SLA):
    1. Runs deterministic velocity, blacklist, behavioral rules, reputation, and fast tabular ML (<50ms).
    2. Updates user receiver history & receiver reputation.
    3. If safe (95% of traffic), returns immediately without touching any LLM.
    4. If anomalous/fraudulent, holds/warns the user immediately and pushes the PII-redacted context
       to the Async Event Queue for background Agentic investigation.
    """
    data_dict = data.model_dump()
    eval_res = evaluate_transaction_sync(data_dict)

    risk = eval_res["risk"]
    status = eval_res["status"]
    reasons = eval_res["reasons"]

    update_history(data.user_id, data.receiver)
    update_reputation(data.receiver, risk)

    # Trigger non-blocking Async Event Queue ONLY if anomaly/dispute threshold met
    async_inv_id: Optional[str] = None
    if eval_res.get("needs_async_agent"):
        dispute_req = DisputeInvestigationRequest(
            user_id=data.user_id,
            payer_vpa=data.user_id if "@" in data.user_id else f"{data.user_id}@upi",
            payee_vpa=data.receiver,
            amount=data.amount,
            utr_number=data.utr_number,
            scam_description=data.note_or_message or "; ".join(reasons),
            screenshot_ocr_text=data.screenshot_ocr_text,
            client_side_masked=False,
            active_call_or_screen_share=bool(data.active_phone_call or data.screen_sharing_detected),
            force_sync=False,
        )
        async_inv_id = async_event_queue.enqueue_investigation(dispute_req)

    # Record anonymized transaction telemetry
    redacted_user, _ = pii_vault.redact_text(data.user_id)
    redacted_receiver, _ = pii_vault.redact_text(data.receiver)
    transactions_collection.insert_one({
        "user_token": redacted_user,
        "receiver_token": redacted_receiver,
        "amount": data.amount,
        "risk": round(risk, 2),
        "status": status,
        "decision_action": eval_res["decision_action"],
        "async_investigation_id": async_inv_id,
        "latency_ms": eval_res["latency_ms"],
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })

    return {
        "risk": round(risk, 2),
        "status": status,
        "reasons": reasons,
        "decision_action": eval_res["decision_action"],
        "latency_ms": eval_res["latency_ms"],
        "async_investigation_id": async_inv_id,
        "pii_redacted": True,
        "rule_breakdown": eval_res["rule_breakdown"],
    }


@app.post("/transaction")
def record_transaction(data: TransactionRequest) -> Dict[str, Any]:
    """Stores and evaluates a UPI transaction (README endpoint)."""
    return predict(data)


@app.post("/behavior")
def track_behavior(telemetry: BehaviorTelemetryRequest) -> Dict[str, Any]:
    """Tracks user interaction telemetry (copy-paste VPA, hesitation, call/screen-share state)."""
    redacted_user, _ = pii_vault.redact_text(telemetry.user_id)
    entry = audit_service.record_event(
        event_type="USER_BEHAVIOR_TELEMETRY",
        reference_id=redacted_user,
        summary="Recorded pre-transaction interaction signals",
        metadata=telemetry.model_dump(exclude={"user_id"}),
    )
    return {"status": "RECORDED", "user_token": redacted_user, "telemetry": entry}


# =====================================================================
# Layer 2: PII Redaction Vault & Local/Edge OCR Sanitizer Endpoint
# =====================================================================

@app.post("/privacy/redact-ocr")
def redact_ocr_endpoint(req: OCRRedactionRequest) -> Dict[str, Any]:
    """
    Step 2 & Step 5B Resilience #1:
    Masks Phone, Bank A/C, Aadhaar, PAN, IFSC & UPI VPA into reversible Vault Tokens
    AND strips adversarial Prompt Injections from screenshot OCR text.
    """
    res = sanitize_and_mask_ocr(req.ocr_text, client_side_masked=req.client_side_masked)
    return {
        "sanitized_text": res.sanitized_text,
        "pii_tokens_created": len(res.pii_tokens_map),
        "tokens": list(res.pii_tokens_map.keys()),
        "prompt_injection_detected": res.prompt_injection_detected,
        "injection_patterns_found": res.injection_patterns_found,
    }


# =====================================================================
# Layer 3, 4 & 5: Fraud Investigation Agent Core & Dispute Resolution
# =====================================================================

@app.post("/dispute/investigate")
def investigate_dispute(req: DisputeInvestigationRequest) -> Dict[str, Any]:
    """
    Step 3, 4 & 5B Full Scam & Dispute Resolution Flow:
    - By default (`force_sync=False`), decouples latency by pushing to the Async Event Queue
      and returning an `investigation_id` in <20ms.
    - When `force_sync=True`, runs the entire Agent Core (Semantic Cache -> Complexity Router ->
      SLM / Frontier LLM + RBI RAG -> Circuit Breaker -> ₹5,000 Policy Guardrail -> Idempotent NPCI Tools ->
      HITL/DLQ) synchronously and returns the complete `InvestigationRecord`.
    """
    if req.force_sync:
        record = agent_orchestrator.run_investigation(req)
        return record.model_dump()

    inv_id = async_event_queue.enqueue_investigation(req)
    return {
        "investigation_id": inv_id,
        "status": "QUEUED",
        "message": "Payment held & user warned (<150ms sync UX). Fraud Investigation Agent Core is processing asynchronously.",
        "poll_url": f"/dispute/{inv_id}",
    }


@app.get("/dispute/{investigation_id}")
def get_dispute_status(investigation_id: str) -> Dict[str, Any]:
    record = async_event_queue.get_investigation(investigation_id)
    if not record:
        raise HTTPException(status_code=404, detail=f"Investigation '{investigation_id}' not found")
    return record


# =====================================================================
# Layer 5: HITL (Human-In-The-Loop) & Dead Letter Queue (DLQ) Endpoints
# =====================================================================

@app.get("/hitl/queue")
def get_hitl_queue(pending_only: bool = Query(default=True)) -> Dict[str, Any]:
    """
    Step 5B Resilience #4:
    Lists tickets routed to Human-In-The-Loop (HITL) & Dead Letter Queue (DLQ) due to:
    - Auto-refunds exceeding the hard ₹5,000 Policy Guardrail
    - Low-confidence agent verdicts (<0.75)
    - NPCI / Beneficiary Bank API 504 Gateway Timeouts
    - Token Budget Circuit Breaker trips (>3 loops)
    """
    tickets = (
        hitl_dlq_service.list_pending_tickets()
        if pending_only
        else hitl_dlq_service.list_all_tickets()
    )
    return {"count": len(tickets), "tickets": tickets}


@app.post("/hitl/resolve/{ticket_id}")
def resolve_hitl_ticket(ticket_id: str, decision: HITLReviewDecisionRequest) -> Dict[str, Any]:
    resolved = hitl_dlq_service.resolve_ticket(ticket_id, decision)
    if not resolved:
        raise HTTPException(status_code=404, detail=f"HITL ticket '{ticket_id}' not found")
    return {"status": "RESOLVED", "ticket": resolved}


# =====================================================================
# Hall of Shame, RBI RAG Knowledge Base & Audit Trail Endpoints
# =====================================================================

@app.get("/hall-of-shame")
def get_hall_of_shame() -> Dict[str, Any]:
    data = list(
        db["hall_of_shame"]
        .find({}, {"_id": 0})
        .sort("avg_risk", -1)
        .limit(10)
    )
    return {"data": data}


@app.get("/rbi-guidelines")
def list_rbi_guidelines() -> Dict[str, Any]:
    docs = list(rbi_guidelines_collection.find({}, {"_id": 0}))
    return {"count": len(docs), "guidelines": docs}


@app.get("/audit/logs")
def get_audit_logs(limit: int = Query(default=25, ge=1, le=200)) -> Dict[str, Any]:
    logs = audit_service.get_recent_logs(limit=limit)
    return {"count": len(logs), "logs": logs}
