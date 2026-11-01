from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class RiskStatus(str, Enum):
    SAFE = "SAFE"
    WARNING = "WARNING"
    FRAUD = "FRAUD"
    HOLD_AND_WARN = "HOLD_AND_WARN"
    BLOCKED = "BLOCKED"


class InvestigationTier(str, Enum):
    DETERMINISTIC_ONLY = "DETERMINISTIC_ONLY"
    SEMANTIC_CACHE = "SEMANTIC_CACHE"
    SMALL_QUANTIZED_MODEL = "SMALL_QUANTIZED_MODEL"
    FRONTIER_LLM_RAG = "FRONTIER_LLM_RAG"


class ResolutionAction(str, Enum):
    ALLOW_PAYMENT = "ALLOW_PAYMENT"
    WARN_USER = "WARN_USER"
    BLOCK_SCAM = "BLOCK_SCAM"
    PLACE_LIEN = "PLACE_LIEN"
    AUTO_REFUND = "AUTO_REFUND"
    ESCALATE_TO_HITL = "ESCALATE_TO_HITL"


# =====================================================================
# 1. Backward-Compatible & Enhanced Payment Check Schemas (/predict)
# =====================================================================

class TransactionRequest(BaseModel):
    user_id: str = Field(..., description="Unique user identifier or VPA")
    receiver: str = Field(..., description="Receiver UPI ID (VPA) or account identifier")
    amount: float = Field(..., gt=0, description="Transaction amount in INR (₹)")
    time: int = Field(default=12, ge=0, le=23, description="Hour of the day (0-23)")
    fast_action: bool = Field(default=False, description="Whether user rushed through confirmation screen")

    # Extended behavioral & contextual signals for UFO Architecture
    copy_pasted_vpa: bool = Field(default=False, description="Whether receiver VPA was copy-pasted from chat/SMS")
    active_phone_call: bool = Field(default=False, description="Whether user is on an active unknown voice/video call")
    screen_sharing_detected: bool = Field(default=False, description="Whether AnyDesk/TeamViewer/screen overlay is active")
    is_collect_request: bool = Field(default=False, description="True if 'Collect/Pull' request disguised as receiving money")
    device_changed: bool = Field(default=False, description="True if new SIM/device/IP change detected recently")
    hesitation_score: float = Field(default=0.0, ge=0.0, le=1.0, description="UI hesitation/backspace telemetry score")
    note_or_message: Optional[str] = Field(default=None, description="Transaction note, SMS text, or chat context")
    screenshot_ocr_text: Optional[str] = Field(default=None, description="Client-side OCR text from payment/scam screenshot")
    utr_number: Optional[str] = Field(default=None, description="12-digit UPI Transaction Reference (UTR) for verification")


class TransactionResponse(BaseModel):
    risk: float = Field(..., description="Risk score between 0.0 and 1.0")
    status: str = Field(..., description="SAFE | WARNING | FRAUD")
    reasons: List[str] = Field(default_factory=list, description="Explainable reasons for the risk decision")

    # Extended Production Architecture metadata (optional for legacy clients, rich for V4 clients)
    decision_action: Optional[str] = Field(default=None, description="ALLOW_PAYMENT | WARN_USER | BLOCK_SCAM | HOLD_AND_INVESTIGATE")
    latency_ms: Optional[float] = Field(default=None, description="Sync execution latency in milliseconds (<150ms target)")
    async_investigation_id: Optional[str] = Field(default=None, description="Async Event Queue ticket ID if escalated to Agent Core")
    pii_redacted: bool = Field(default=True, description="Confirms zero raw PII leaked to cloud")
    rule_breakdown: Optional[Dict[str, float]] = Field(default=None, description="Deterministic Rule + ML + Reputation scores")


# =====================================================================
# 2. Dispute & Scam Investigation Schemas (Step 1, 3, 4, 5B)
# =====================================================================

class DisputeInvestigationRequest(BaseModel):
    user_id: str = Field(..., description="Payer user ID or phone/VPA")
    payer_vpa: str = Field(default="user@okaxis", description="Victim UPI VPA")
    payee_vpa: str = Field(..., description="Suspected scammer UPI VPA")
    amount: float = Field(..., gt=0, description="Disputed transaction amount in INR (₹)")
    utr_number: Optional[str] = Field(default=None, description="12-digit UPI UTR reference number")
    scam_description: str = Field(..., description="User's description of the scam or dispute context")
    screenshot_ocr_text: Optional[str] = Field(default=None, description="OCR text extracted from screenshot/receipt")
    client_side_masked: bool = Field(default=False, description="True if Local On-Device OCR Masking was already applied")
    active_call_or_screen_share: bool = Field(default=False, description="Whether victim was on call / screen share")
    force_sync: bool = Field(default=False, description="If True, waits for Agent Core instead of async queue (for testing/demo)")


class AgentVerdictSchema(BaseModel):
    """Strict JSON Schema output enforced on SLM and Frontier LLM + RAG (Step 4 & Step 5B)."""
    is_scam: bool = Field(..., description="True if transaction/dispute is assessed as fraud/scam")
    scam_category: str = Field(
        ...,
        description="Category e.g. FAKE_KYC, DIGITAL_ARREST, COLLECT_REQUEST_TRAP, FAKE_RECEIPT, LOTTERY_SCAM, SCREEN_SHARE, LEGITIMATE_DISPUTE, SAFE"
    )
    confidence: float = Field(..., ge=0.0, le=1.0, description="Agent confidence score (0.0 to 1.0)")
    recommended_action: ResolutionAction = Field(..., description="Recommended action before deterministic guardrail")
    recommended_refund_inr: float = Field(default=0.0, ge=0.0, description="Recommended auto-refund amount in INR")
    place_lien: bool = Field(default=False, description="Whether to place an immediate NPCI UDIR lien on payee VPA")
    rbi_circular_citations: List[str] = Field(default_factory=list, description="Relevant RBI/NPCI circular clauses cited")
    reasoning_summary: str = Field(..., description="Redacted, human-readable explanation of the verdict")


class NPCIExecutionResult(BaseModel):
    tool_name: str
    idempotency_key: str
    status: str  # SUCCESS | BLOCKED_BY_GUARDRAIL | TIMEOUT_504 | FAILED
    lien_id: Optional[str] = None
    refund_tx_id: Optional[str] = None
    amount_processed_inr: float = 0.0
    message: str
    timestamp: str


class InvestigationRecord(BaseModel):
    investigation_id: str
    status: str  # QUEUED | PROCESSING | COMPLETED | ESCALATED_HITL | DLQ_504
    tier_used: InvestigationTier
    cache_hit: bool = False
    redacted_context: str
    pii_tokens_created: int = 0
    prompt_injection_detected: bool = False
    reasoning_loops_used: int = 0
    tokens_consumed: int = 0
    circuit_breaker_tripped: bool = False
    agent_verdict: Optional[AgentVerdictSchema] = None
    policy_guardrail_applied: bool = False
    policy_guardrail_notes: List[str] = Field(default_factory=list)
    final_action: ResolutionAction = ResolutionAction.WARN_USER
    npci_tool_results: List[NPCIExecutionResult] = Field(default_factory=list)
    hitl_ticket_id: Optional[str] = None
    total_processing_ms: float = 0.0
    created_at: str


class HITLReviewDecisionRequest(BaseModel):
    reviewer_id: str = Field(..., description="Human compliance officer ID")
    approved_action: ResolutionAction = Field(..., description="Final approved action (e.g. AUTO_REFUND, PLACE_LIEN, ALLOW_PAYMENT)")
    approved_refund_inr: float = Field(default=0.0, ge=0.0, description="Approved refund amount (can exceed ₹5,000 with human sign-off)")
    notes: str = Field(..., description="Audit notes for RBI compliance")
