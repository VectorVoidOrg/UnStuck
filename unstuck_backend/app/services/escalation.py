from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
from app.schemas import Event, Case

def determine_escalation_status(
    events: List[Event],
    case_created_at: datetime,
    is_overdue: bool = True,
    ticket_number: Optional[str] = None
) -> Dict[str, Any]:
    """
    Evaluates which escalation step is unlocked, active, or completed.
    Ladder:
    1. bank_complaint: Bank Grievance
    2. nodal_officer: Principal Nodal Officer (PNO)
    3. thirty_day_mark: 30-day resolution window tracker
    4. ombudsman_pack: RBI Ombudsman Dossier
    """
    event_types = {e.type: e for e in events}
    
    # 1. Bank Complaint
    bank_complaint_event = event_types.get("BANK_COMPLAINT")
    complaint_logged = (bank_complaint_event is not None) or bool(ticket_number)
    complaint_date = bank_complaint_event.timestamp if bank_complaint_event else (case_created_at if ticket_number else None)

    step1_status = "COMPLETED" if complaint_logged else "UNLOCKED"
    
    # 2. Nodal Officer
    nodal_event = event_types.get("NODAL_OFFICER")
    if nodal_event:
        step2_status = "COMPLETED"
    elif complaint_logged:
        step2_status = "UNLOCKED"
    else:
        step2_status = "LOCKED"

    # 3. 30-day mark
    now = datetime.now()
    bank_rejected = any(
        e.type == "BANK_RESPONSE" and e.details.get("outcome") == "REJECTED" 
        for e in events
    )

    if complaint_date:
        days_since_complaint = (now.date() - complaint_date.date()).days
        unlock_date = complaint_date + timedelta(days=30)
        days_remaining = max(0, 30 - days_since_complaint)
        is_thirty_days_up = days_since_complaint >= 30
    else:
        days_since_complaint = 0
        unlock_date = now + timedelta(days=30)
        days_remaining = 30
        is_thirty_days_up = False

    if is_thirty_days_up:
        step3_status = "COMPLETED"
    elif complaint_logged:
        step3_status = "IN_PROGRESS"
    else:
        step3_status = "LOCKED"

    # 4. Ombudsman Pack
    ombudsman_event = event_types.get("OMBUDSMAN_PACK")
    if ombudsman_event:
        step4_status = "COMPLETED"
    elif complaint_logged and (is_thirty_days_up or bank_rejected):
        step4_status = "UNLOCKED"
    elif complaint_logged:
        # Can still view preview or prepare
        step4_status = "AVAILABLE_ON_DEMAND"
    else:
        step4_status = "LOCKED"

    # Current recommended next step
    if not complaint_logged:
        current_step = 1
        recommendation = "File Level 1 Grievance with Bank"
    elif step2_status == "UNLOCKED":
        current_step = 2
        recommendation = "Escalate to Principal Nodal Officer"
    elif step4_status in ("UNLOCKED", "AVAILABLE_ON_DEMAND"):
        current_step = 4
        recommendation = "Generate RBI Integrated Ombudsman Dossier"
    else:
        current_step = 3
        recommendation = f"Awaiting Bank Resolution ({days_remaining} days remaining in 30-day window)"

    return {
        "current_step": current_step,
        "recommendation": recommendation,
        "steps": {
            "bank_complaint": {
                "step": 1,
                "name": "Bank Complaint",
                "status": step1_status,
                "completed_at": complaint_date.isoformat() if complaint_date else None,
                "ticket_number": ticket_number or (bank_complaint_event.details.get("ticket_number") if bank_complaint_event else None)
            },
            "nodal_officer": {
                "step": 2,
                "name": "Principal Nodal Officer",
                "status": step2_status,
                "completed_at": nodal_event.timestamp.isoformat() if nodal_event else None
            },
            "thirty_day_mark": {
                "step": 3,
                "name": "30-Day Resolution Window",
                "status": step3_status,
                "days_elapsed": days_since_complaint,
                "days_remaining": days_remaining,
                "target_date": unlock_date.isoformat()
            },
            "ombudsman_pack": {
                "step": 4,
                "name": "RBI Ombudsman Dossier",
                "status": step4_status,
                "completed_at": ombudsman_event.timestamp.isoformat() if ombudsman_event else None
            }
        }
    }
