from datetime import datetime, date, timedelta
from typing import Optional, Tuple
from app.schemas import EvaluationResult

def parse_iso_or_date(d_val: str | datetime | date) -> datetime:
    if isinstance(d_val, datetime):
        return d_val
    if isinstance(d_val, date):
        return datetime.combine(d_val, datetime.min.time())
    
    # Try multiple formats
    clean_val = d_val.strip()
    # Try ISO
    try:
        return datetime.fromisoformat(clean_val)
    except Exception:
        pass
    
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%d %b %Y", "%d %B %Y"):
        try:
            return datetime.strptime(clean_val, fmt)
        except ValueError:
            continue
    raise ValueError(f"Unable to parse date string: {d_val}")

def calculate_deadline(
    tx_date_input: str | datetime | date,
    tat_days: int = 1,
    day_type: str = "calendar"
) -> datetime:
    """
    Computes deadline date = T + tat_days.
    RBI circular specifies T as the calendar date.
    """
    dt = parse_iso_or_date(tx_date_input)
    # T is the calendar date of the transaction
    if day_type == "business":
        current = dt
        added = 0
        while added < tat_days:
            current += timedelta(days=1)
            # Monday=0, Sunday=6
            if current.weekday() < 5:
                added += 1
        return current
    else:
        # Standard calendar day addition
        return dt + timedelta(days=tat_days)

def evaluate_status(
    tx_date_input: str | datetime | date,
    tat_days: int = 1,
    day_type: str = "calendar",
    money_returned: bool = False,
    reversal_date_input: Optional[str | datetime | date] = None,
    as_of: Optional[str | datetime | date] = None
) -> Tuple[datetime, str, int]:
    """
    Returns: (deadline_datetime, status, days_late)
    Statuses:
    - WITHIN_WINDOW
    - DUE
    - OVERDUE
    - REVERSED_ON_TIME
    - REVERSED_LATE
    """
    tx_dt = parse_iso_or_date(tx_date_input)
    deadline_dt = calculate_deadline(tx_dt, tat_days=tat_days, day_type=day_type)
    
    current_dt = parse_iso_or_date(as_of) if as_of is not None else datetime.now()
    deadline_date = deadline_dt.date()

    if money_returned and reversal_date_input is not None:
        rev_dt = parse_iso_or_date(reversal_date_input)
        rev_date = rev_dt.date()
        if rev_date <= deadline_date:
            return deadline_dt, "REVERSED_ON_TIME", 0
        else:
            days_late = (rev_date - deadline_date).days
            return deadline_dt, "REVERSED_LATE", max(1, days_late)
    
    # Not yet reversed
    curr_date = current_dt.date()
    if curr_date < deadline_date:
        return deadline_dt, "WITHIN_WINDOW", 0
    elif curr_date == deadline_date:
        return deadline_dt, "DUE", 0
    else:
        days_late = (curr_date - deadline_date).days
        return deadline_dt, "OVERDUE", max(1, days_late)
