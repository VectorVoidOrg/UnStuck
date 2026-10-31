from datetime import date, datetime, timedelta
from typing import Any, Literal

from app.config import IST, get_settings, today_ist

try:
    from app.schemas import Case, EscalationState, Step
except ImportError:
    from pydantic import BaseModel

    class Step(BaseModel):
        id: str
        label: str
        state: Literal["done", "available", "locked"]
        unlocks_on: date | None = None
        tip: str | None = None

    class EscalationState(BaseModel):
        case_id: str
        steps: list[Step]
        current_step: str
        ombudsman_unlocks_on: date | None
        days_until_ombudsman: int | None
        closed: bool

    Case = Any  # type: ignore


def _event_date(dt: datetime | str) -> date:
    """Safely extracts the IST calendar date from a datetime or ISO formatted string."""
    if isinstance(dt, str):
        try:
            dt = datetime.fromisoformat(dt)
        except Exception:
            try:
                return date.fromisoformat(dt[:10])
            except Exception:
                return date.today()

    if dt.tzinfo is None:
        return dt.replace(tzinfo=IST).date()
    return dt.astimezone(IST).date()


def build_escalation(case: Any, today: date | None = None) -> Any:
    """Builds the 4-step escalation hierarchy according to RBI grievance guidelines."""
    current_date = today if today is not None else today_ist()
    settings = get_settings()
    wait_days = settings.ombudsman_wait_days

    # Identify relevant events
    events = (
        getattr(case, "events", [])
        if hasattr(case, "events")
        else case.get("events", [])
    )
    bank_complaint_events = [
        e
        for e in events
        if getattr(e, "type", "") == "bank_complaint_sent"
        or (isinstance(e, dict) and e.get("type") == "bank_complaint_sent")
    ]
    nodal_events = [
        e
        for e in events
        if getattr(e, "type", "") == "nodal_complaint_sent"
        or (isinstance(e, dict) and e.get("type") == "nodal_complaint_sent")
    ]
    principal_nodal_events = [
        e
        for e in events
        if getattr(e, "type", "") == "principal_nodal_sent"
        or (isinstance(e, dict) and e.get("type") == "principal_nodal_sent")
    ]
    ombudsman_events = [
        e
        for e in events
        if getattr(e, "type", "") == "ombudsman_filed"
        or (isinstance(e, dict) and e.get("type") == "ombudsman_filed")
    ]
    reply_events = [
        e
        for e in events
        if getattr(e, "type", "") == "bank_replied"
        or (isinstance(e, dict) and e.get("type") == "bank_replied")
    ]

    first_complaint_date: date | None = None
    if bank_complaint_events:
        first_complaint_date = min(
            _event_date(getattr(e, "at", None) if hasattr(e, "at") else e["at"])
            for e in bank_complaint_events
        )

    unsatisfactory_reply_dates = [
        _event_date(getattr(e, "at", None) if hasattr(e, "at") else e["at"])
        for e in reply_events
        if (getattr(e, "outcome", None) if hasattr(e, "outcome") else e.get("outcome"))
        in ("rejected", "unsatisfactory")
    ]
    earliest_unsat_reply = (
        min(unsatisfactory_reply_dates) if unsatisfactory_reply_dates else None
    )

    ombudsman_unlocks_on: date | None = None
    if first_complaint_date is not None:
        standard_unlock = first_complaint_date + timedelta(days=wait_days)
        if earliest_unsat_reply is not None:
            ombudsman_unlocks_on = min(standard_unlock, earliest_unsat_reply)
        else:
            ombudsman_unlocks_on = standard_unlock

    days_until_ombudsman: int | None = None
    if ombudsman_unlocks_on is not None:
        days_until_ombudsman = max(0, (ombudsman_unlocks_on - current_date).days)

    # Step 1: bank_complaint
    s1_done = len(bank_complaint_events) > 0
    step1 = Step(
        id="bank_complaint",
        label="Complain to the bank (branch, app or customer care)",
        state="done" if s1_done else "available",
        tip="Ask for a written acknowledgement and a complaint reference number.",
    )

    # Step 2: nodal_officer
    s2_done = len(nodal_events) > 0
    s2_state = "done" if s2_done else ("available" if s1_done else "locked")
    step2 = Step(
        id="nodal_officer",
        label="Escalate to the bank's Nodal Officer",
        state=s2_state,
        tip="Reference your original complaint reference and state that resolution is pending.",
    )

    # Step 3: principal_nodal_officer
    s3_done = len(principal_nodal_events) > 0
    s3_state = "done" if s3_done else ("available" if s2_done else "locked")
    step3 = Step(
        id="principal_nodal_officer",
        label="Escalate to the Principal Nodal Officer",
        state=s3_state,
        tip="Escalate if Nodal Officer did not resolve satisfactorily within internal timelines.",
    )

    # Step 4: ombudsman_pack
    s4_done = len(ombudsman_events) > 0
    ombudsman_unlocked = (
        (ombudsman_unlocks_on is not None and current_date >= ombudsman_unlocks_on)
        or (earliest_unsat_reply is not None)
    )
    if s4_done:
        s4_state = "done"
        s4_unlocks = None
    elif ombudsman_unlocked:
        s4_state = "available"
        s4_unlocks = ombudsman_unlocks_on
    else:
        s4_state = "locked"
        s4_unlocks = ombudsman_unlocks_on

    step4 = Step(
        id="ombudsman_pack",
        label="Prepare the RBI Ombudsman complaint",
        state=s4_state,
        unlocks_on=s4_unlocks,
        tip="Download prefilled complaint package for the RBI CMS portal.",
    )

    steps = [step1, step2, step3, step4]

    has_resolved = any(
        (getattr(e, "outcome", None) if hasattr(e, "outcome") else e.get("outcome"))
        == "resolved"
        for e in reply_events
    )
    has_reversal = any(
        (getattr(e, "type", None) if hasattr(e, "type") else e.get("type"))
        == "reversal_received"
        for e in events
    )
    has_comp = any(
        (getattr(e, "type", None) if hasattr(e, "type") else e.get("type"))
        == "compensation_received"
        for e in events
    )
    closed = has_resolved or (has_reversal and has_comp)

    available_step = next(
        (
            (getattr(s, "id", None) if hasattr(s, "id") else s["id"])
            for s in steps
            if (getattr(s, "state", None) if hasattr(s, "state") else s["state"])
            == "available"
        ),
        "",
    )
    all_done = all(
        (getattr(s, "state", None) if hasattr(s, "state") else s["state"]) == "done"
        for s in steps
    )
    last_id = (
        getattr(steps[-1], "id", None)
        if hasattr(steps[-1], "id")
        else steps[-1]["id"]
    )
    current_step = available_step if available_step else (last_id if all_done else "")

    case_id = (
        getattr(case, "id", None) or (case.get("id") if isinstance(case, dict) else "")
    )

    return EscalationState(
        case_id=case_id,
        steps=steps,
        current_step=current_step,
        ombudsman_unlocks_on=ombudsman_unlocks_on,
        days_until_ombudsman=days_until_ombudsman,
        closed=closed,
    )
