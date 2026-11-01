from datetime import datetime, date
from app.services.deadline import calculate_deadline, evaluate_status

def test_founder_case_deadline():
    """
    Founder case hand-check:
    UPI transfer on T, TAT is T+1, reversed on T+9 -> 8 days late.
    """
    t_date = "2026-10-01"
    reversal_date = "2026-10-10" # T+9
    
    deadline_dt = calculate_deadline(t_date, tat_days=1, day_type="calendar")
    assert deadline_dt.strftime("%Y-%m-%d") == "2026-10-02"

    deadline_res, status, days_late = evaluate_status(
        tx_date_input=t_date,
        tat_days=1,
        day_type="calendar",
        money_returned=True,
        reversal_date_input=reversal_date
    )

    assert status == "REVERSED_LATE"
    assert days_late == 8

def test_reversed_on_time():
    t_date = "2026-10-01"
    rev_date = "2026-10-02" # T+1
    deadline_res, status, days_late = evaluate_status(
        tx_date_input=t_date,
        tat_days=1,
        day_type="calendar",
        money_returned=True,
        reversal_date_input=rev_date
    )
    assert status == "REVERSED_ON_TIME"
    assert days_late == 0

def test_within_window_and_due():
    t_date = "2026-10-01"
    # Within window on day T
    _, status_t, days_t = evaluate_status(
        tx_date_input=t_date,
        tat_days=1,
        as_of="2026-10-01"
    )
    assert status_t == "WITHIN_WINDOW"
    assert days_t == 0

    # Due on day T+1
    _, status_due, days_due = evaluate_status(
        tx_date_input=t_date,
        tat_days=1,
        as_of="2026-10-02"
    )
    assert status_due == "DUE"
    assert days_due == 0

def test_overdue_unreversed():
    t_date = "2026-10-01"
    # Checked on T+5 (2026-10-06)
    _, status, days_late = evaluate_status(
        tx_date_input=t_date,
        tat_days=1,
        as_of="2026-10-06"
    )
    assert status == "OVERDUE"
    assert days_late == 4

def test_atm_five_day_rule():
    # ATM cash not dispensed is T+5
    t_date = "2026-10-01"
    deadline_dt = calculate_deadline(t_date, tat_days=5, day_type="calendar")
    assert deadline_dt.strftime("%Y-%m-%d") == "2026-10-06"

