import uuid
import json
from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, HTTPException, status, Query
from app.config import settings
from app.schemas import (
    Case, CreateCaseRequest, UpdateCaseRequest,
    Event, LogEventRequest, EvaluationResult
)
from app.services.deadline import evaluate_status
from app.services.penalty import calculate_penalty
from app.services.escalation import determine_escalation_status
from app.services.store import store

router = APIRouter(prefix="", tags=["Cases"])

def get_rule_by_id(rule_id: str) -> dict:
    if settings.RULES_FILE_PATH.exists():
        with open(settings.RULES_FILE_PATH, "r", encoding="utf-8") as f:
            rules = json.load(f)
            for r in rules:
                if r["id"] == rule_id:
                    return r
    # Default fallback rule for UPI
    return {
        "id": "upi_p2p_failed",
        "rail": "UPI",
        "deadline_days": 1,
        "day_type": "calendar",
        "compensation": "Rs 100 per day of delay beyond T + 1",
        "penalty_per_day": 100,
        "responsible_party": "Beneficiary Bank / Remitter Bank",
        "circular_row": "Harmonisation Circular Item 5(a)"
    }

def compute_case_evaluation(case: Case) -> EvaluationResult:
    rule = get_rule_by_id(case.rule_id)
    deadline_days = rule.get("deadline_days", 1)
    day_type = rule.get("day_type", "calendar")
    
    deadline_dt, stat, days_late = evaluate_status(
        tx_date_input=case.transaction.date,
        tat_days=deadline_days,
        day_type=day_type,
        money_returned=case.money_returned,
        reversal_date_input=case.reversal_date
    )
    
    penalty_amt = calculate_penalty(
        days_late=days_late,
        compensation_rate=rule.get("penalty_per_day", 100.0),
        has_compensation=True
    )
    
    return EvaluationResult(
        deadline_date=deadline_dt,
        tat_days=deadline_days,
        day_type=day_type,
        status=stat,
        days_late=days_late,
        penalty_amount=penalty_amt,
        responsible_party=rule.get("responsible_party", "Beneficiary Bank"),
        circular_reference=f"RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20 ({rule.get('circular_row', 'Item 5')})",
        rule_id=case.rule_id
    )

@router.post("/cases", response_model=Case, status_code=status.HTTP_201_CREATED)
async def create_case(payload: CreateCaseRequest):
    case_id = str(uuid.uuid4())
    now = datetime.now()
    
    case = Case(
        id=case_id,
        created_at=now,
        updated_at=now,
        transaction=payload.transaction,
        rule_id=payload.rule_id,
        customer_role=payload.customer_role,
        money_returned=payload.money_returned,
        reversal_date=payload.reversal_date,
        notes=payload.notes,
        ticket_number=payload.ticket_number
    )
    
    # Calculate initial evaluation
    case.evaluation = compute_case_evaluation(case)
    
    # Add initial event
    initial_event = Event(
        id=str(uuid.uuid4()),
        case_id=case_id,
        timestamp=now,
        type="TRANSACTION",
        description=f"Case opened for failed {payload.transaction.rail or 'UPI'} payment of Rs. {payload.transaction.amount:,.2f}.",
        details={"reference": payload.transaction.reference, "bank": payload.transaction.bank}
    )
    case.events.append(initial_event)
    
    store.save_case(case)
    return case

@router.get("/cases", response_model=List[Case])
async def list_cases():
    return store.list_cases()

@router.get("/cases/{case_id}", response_model=Case)
async def get_case(case_id: str):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    # Refresh evaluation live
    case.evaluation = compute_case_evaluation(case)
    return case

@router.patch("/cases/{case_id}", response_model=Case)
async def update_case(case_id: str, updates: UpdateCaseRequest):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    
    update_dict = updates.model_dump(exclude_unset=True)
    update_dict["updated_at"] = datetime.now()
    
    # If money returned is marked true without date, default to now
    if update_dict.get("money_returned") and not update_dict.get("reversal_date") and not case.reversal_date:
        update_dict["reversal_date"] = datetime.now()
        
    updated = store.update_case(case_id, update_dict)
    if updated:
        updated.evaluation = compute_case_evaluation(updated)
        store.save_case(updated)
        
        # Log event if money was reversed
        if update_dict.get("money_returned"):
            rev_event = Event(
                id=str(uuid.uuid4()),
                case_id=case_id,
                timestamp=updated.reversal_date or datetime.now(),
                type="REVERSAL",
                description="Money successfully reversed / credited to account.",
                details={"reversal_date": str(updated.reversal_date)}
            )
            store.add_event(case_id, rev_event)
            updated = store.get_case(case_id)
            
    return updated

@router.post("/cases/{case_id}/evaluate", response_model=EvaluationResult)
async def evaluate_case(case_id: str):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    evaluation = compute_case_evaluation(case)
    case.evaluation = evaluation
    store.save_case(case)
    return evaluation

# Alias endpoint POST /evaluate (can take case_id as query param)
@router.post("/evaluate", response_model=EvaluationResult)
async def evaluate_case_alias(case_id: str = Query(...)):
    return await evaluate_case(case_id)

@router.post("/cases/{case_id}/events", response_model=Event)
async def log_event(case_id: str, payload: LogEventRequest):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    
    event = Event(
        id=str(uuid.uuid4()),
        case_id=case_id,
        timestamp=payload.timestamp or datetime.now(),
        type=payload.type,
        description=payload.description,
        details=payload.details or {}
    )
    store.add_event(case_id, event)
    
    # If event includes a ticket number, update case ticket number
    if payload.details and payload.details.get("ticket_number"):
        store.update_case(case_id, {"ticket_number": payload.details.get("ticket_number")})
        
    return event

# Alias endpoint POST /events
@router.post("/events", response_model=Event)
async def log_event_alias(case_id: str = Query(...), payload: LogEventRequest = None):
    if not payload:
        raise HTTPException(status_code=400, detail="Missing payload")
    return await log_event(case_id, payload)

@router.get("/cases/{case_id}/escalation")
async def get_escalation(case_id: str):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    
    eval_res = compute_case_evaluation(case)
    is_overdue = eval_res.status in ("OVERDUE", "REVERSED_LATE")
    
    status_info = determine_escalation_status(
        events=case.events,
        case_created_at=case.created_at,
        is_overdue=is_overdue,
        ticket_number=case.ticket_number
    )
    return status_info

# Alias endpoint GET /escalation
@router.get("/escalation")
async def get_escalation_alias(case_id: str = Query(...)):
    return await get_escalation(case_id)
