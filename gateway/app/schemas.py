from datetime import datetime
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field

# Auth schemas
class NonceResponse(BaseModel):
    nonce: str
    expires_in_seconds: int = 300

class GoogleAuthRequest(BaseModel):
    id_token: str
    nonce: str

class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user_id: str
    email: Optional[str] = None
    is_demo: bool = False

class RefreshRequest(BaseModel):
    refresh_token: str

class UserProfileResponse(BaseModel):
    id: str
    email: Optional[str] = None
    email_verified: bool
    is_demo: bool
    plan: str  # "FREE" or "PRO_ANNUAL" / "PRO_MONTHLY"
    is_entitled: bool
    prevention_used: int
    prevention_remaining: int
    unstuck_used: int
    unstuck_remaining: int
    entitlement_expires_at: Optional[datetime] = None

# Prevention schemas
class PreventionCheckRequest(BaseModel):
    check_id: str = Field(..., description="Client generated UUID for idempotent execution")
    receiver_vpa: str
    receiver_name: Optional[str] = None
    amount: float = Field(..., ge=0.0)
    source_app: Optional[str] = None
    timestamp: Optional[int] = None

class PreventionCheckResponse(BaseModel):
    check_id: str
    risk_score: int = Field(..., ge=0, le=100)
    verdict: str  # "LIKELY_SAFE" | "BE_CAREFUL" | "LIKELY_SCAM"
    reasons: List[str]
    remaining_free: int
    is_partial_check: bool = False

# Billing schemas
class BillingAccountIdResponse(BaseModel):
    account_id_hash: str

class BillingVerifyRequest(BaseModel):
    purchase_token: str
    product_id: str

class BillingVerifyResponse(BaseModel):
    success: bool
    state: str
    is_entitled: bool
    expires_at: Optional[datetime] = None
    message: str

# Demo schemas
class DemoLoginRequest(BaseModel):
    access_code: str

class DemoSetUsageRequest(BaseModel):
    prevention_used: Optional[int] = None
    unstuck_used: Optional[int] = None

# Unstuck schemas
class ExtractedTransaction(BaseModel):
    amount: float
    reference: str
    bank: str
    date: str
    status: str
    rail: str

class UnstuckExtractResponse(BaseModel):
    case_id: str
    transaction: ExtractedTransaction
    confidence: float
    confidence_breakdown: Dict[str, float]
    flags: List[str] = []
    crosscheck_status: str
    raw_text: Optional[str] = None
    quota_remaining: int

