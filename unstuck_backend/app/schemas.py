from __future__ import annotations
from datetime import datetime
from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field

class Transaction(BaseModel):
    amount: float = Field(..., description="Transaction amount in INR")
    reference: str = Field(..., description="UTR, RRN, or reference transaction number")
    bank: str = Field(..., description="Name of the bank involved")
    date: str = Field(..., description="Transaction date and optional time (ISO 8601 or YYYY-MM-DD)")
    rail: Optional[str] = Field(default="UPI", description="Payment rail, e.g., UPI, IMPS, NEFT, RTGS, ATM, Card")
    status: Optional[str] = Field(default="FAILED", description="Status e.g. FAILED, DEBITED, PENDING")
    customer_name: Optional[str] = Field(default="Account Holder", description="Customer full name")
    customer_email: Optional[str] = Field(default="customer@example.com", description="Customer email")
    customer_phone: Optional[str] = Field(default="9876543210", description="Customer phone number")
    account_number: Optional[str] = Field(default="XX1234", description="Masked or full account / UPI VPA")

class ExtractResponse(BaseModel):
    transaction: Transaction
    confidence: float = Field(default=0.85, description="Overall extraction confidence score (0-1)")
    confidence_breakdown: Dict[str, float] = Field(default_factory=dict)
    flags: List[str] = Field(default_factory=list, description="Warnings or issues detected")
    crosscheck_status: str = Field(default="MATCH", description="Status comparing OCR text and LLM extracted data")
    raw_text: Optional[str] = Field(default=None, description="Raw OCR text extracted from screenshot")
    risk_score: float = Field(default=0.1, description="Claim risk score (0-1), higher is riskier")
    risk_factors: List[str] = Field(default_factory=list, description="List of detected risk factors")

class Event(BaseModel):
    id: str
    case_id: str
    timestamp: datetime = Field(default_factory=datetime.now)
    type: str = Field(..., description="Event type: TRANSACTION, BANK_COMPLAINT, BANK_RESPONSE, NODAL_OFFICER, OMBUDSMAN_PACK, REVERSAL, NOTE")
    description: str
    details: Dict[str, Any] = Field(default_factory=dict)

class EvaluationResult(BaseModel):
    deadline_date: datetime
    tat_days: int
    day_type: str = "calendar"
    status: str = Field(..., description="WITHIN_WINDOW, DUE, OVERDUE, REVERSED_ON_TIME, REVERSED_LATE")
    days_late: int = 0
    penalty_amount: float = 0.0
    responsible_party: str = "Beneficiary Bank / Remitter Bank"
    circular_reference: str = "RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20"
    rule_id: Optional[str] = None

class Case(BaseModel):
    id: str
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)
    transaction: Transaction
    rule_id: str = "upi_p2p_failed"
    customer_role: str = "Remitter"
    money_returned: bool = False
    reversal_date: Optional[datetime] = None
    evaluation: Optional[EvaluationResult] = None
    events: List[Event] = Field(default_factory=list)
    ticket_number: Optional[str] = None
    notes: Optional[str] = None

class CreateCaseRequest(BaseModel):
    transaction: Transaction
    rule_id: str = "upi_p2p_failed"
    customer_role: str = "Remitter"
    money_returned: bool = False
    reversal_date: Optional[datetime] = None
    notes: Optional[str] = None
    ticket_number: Optional[str] = None

class UpdateCaseRequest(BaseModel):
    money_returned: Optional[bool] = None
    reversal_date: Optional[datetime] = None
    ticket_number: Optional[str] = None
    notes: Optional[str] = None
    customer_role: Optional[str] = None
    rule_id: Optional[str] = None

class LogEventRequest(BaseModel):
    type: str
    description: str
    details: Optional[Dict[str, Any]] = None
    timestamp: Optional[datetime] = None

class LetterRequest(BaseModel):
    case_id: str
    level: str = "bank_complaint" # "bank_complaint" | "nodal_officer" | "ombudsman"
    custom_notes: Optional[str] = None
    ticket_number: Optional[str] = None
    recipient_email: Optional[str] = None

class LetterResponse(BaseModel):
    case_id: str
    level: str
    letter_text: str
    validated: bool = True
    generated_by: str = "template" # "template" or "gemini_polished"
    recipient: Dict[str, str] = Field(default_factory=dict)
    warnings: List[str] = Field(default_factory=list)
