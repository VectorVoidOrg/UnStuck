from datetime import date, datetime
from zoneinfo import ZoneInfo
from typing import Literal
import pytest

from app.services.deadline import compute_due_date, evaluate_case, AppError
from pydantic import BaseModel, Field

class Complainant(BaseModel):
    name: str | None = None
    phone: str | None = None
    email: str | None = None
    address: str | None = None

class Transaction(BaseModel):
    utr: str = Field(min_length=6, max_length=40)
    amount: float = Field(gt=0)
    bank: str
    txn_datetime: datetime
    rule_id: str
    role: Literal["sender", "receiver"] = "sender"
    payee: str | None = None

class Case(BaseModel):
    id: str
    created_at: datetime
    transaction: Transaction
    complainant: Complainant = Complainant()
    reversal_date: date | None = None
    events: list = []
    letters: list = []
    risk: dict | None = None

IST = ZoneInfo("Asia/Kolkata")


def make_case(
    txn_datetime: datetime = datetime(2026, 10, 12, 10, 0, tzinfo=IST),
    reversal_date: date | None = None,
    rule_id: str = "upi_p2p_not_credited",
) -> Case:
    return Case(
        id="case12345678",
        created_at=datetime(2026, 10, 12, 10, 5, tzinfo=IST),
        transaction=Transaction(
            utr="123456789012",
            amount=1000.0,
            bank="HDFC Bank",
            txn_datetime=txn_datetime,
            rule_id=rule_id,
        ),
        complainant=Complainant(name="John Doe"),
        reversal_date=reversal_date,
    )


DEFAULT_RULE = {
    "id": "upi_p2p_not_credited",
    "rail": "UPI",
    "failure": "beneficiary_not_credited",
    "label": "UPI payment to a person did not arrive",
    "tat_days": 1,
    "day_type": "calendar",
    "compensation_per_day": 100,
    "compensation_recipient": "verify",
    "circular_row": "UPI: beneficiary not credited",
    "status": "confirmed",
}


def test_reversal_on_time():
    case = make_case(reversal_date=date(2026, 10, 13))
    res = evaluate_case(case, DEFAULT_RULE)
    assert res.delay_days == 0
    assert res.status == "reversed_on_time"
    assert res.compensation_amount == 0


def test_reversal_late():
    case = make_case(reversal_date=date(2026, 10, 14))
    res = evaluate_case(case, DEFAULT_RULE)
    assert res.delay_days == 1
    assert res.status == "reversed_late"
    assert res.compensation_amount == 100


def test_pending_within_window():
    case = make_case(reversal_date=None)
    res = evaluate_case(case, DEFAULT_RULE, today=date(2026, 10, 12))
    assert res.status == "within_window"
    assert res.delay_days == 0


def test_pending_due_today():
    case = make_case(reversal_date=None)
    res = evaluate_case(case, DEFAULT_RULE, today=date(2026, 10, 13))
    assert res.status == "due_today"
    assert res.delay_days == 0


def test_pending_overdue():
    case = make_case(reversal_date=None)
    res = evaluate_case(case, DEFAULT_RULE, today=date(2026, 10, 16))
    assert res.status == "overdue"
    assert res.delay_days == 3
    assert res.compensation_amount == 300


def test_txn_datetime_ist_conversion():
    dt = datetime.fromisoformat("2026-10-12T23:58:00+05:30")
    case = make_case(txn_datetime=dt)
    res = evaluate_case(case, DEFAULT_RULE)
    assert res.txn_date == date(2026, 10, 12)


def test_founder_case():
    case = make_case(reversal_date=date(2026, 10, 21))
    res = evaluate_case(case, DEFAULT_RULE)
    assert res.delay_days == 8
    assert res.compensation_amount == 800
    assert res.status == "reversed_late"


def test_atm_case():
    atm_rule = {
        "id": "atm_cash_not_dispensed",
        "rail": "ATM",
        "failure": "cash_not_dispensed",
        "label": "ATM cash not dispensed",
        "tat_days": 5,
        "day_type": "calendar",
        "compensation_per_day": 100,
        "compensation_recipient": "account_holder",
        "circular_row": "ATM: cash not dispensed",
        "status": "confirmed",
    }
    case = make_case(reversal_date=date(2026, 10, 19), rule_id="atm_cash_not_dispensed")
    res = evaluate_case(case, atm_rule)
    assert res.due_date == date(2026, 10, 17)
    assert res.delay_days == 2
    assert res.compensation_amount == 200


def test_compute_due_date_month_crossing():
    due = compute_due_date(date(2026, 10, 30), 5)
    assert due == date(2026, 11, 4)


def test_invalid_reversal_date():
    case = make_case(reversal_date=date(2026, 10, 11))
    with pytest.raises(AppError) as exc_info:
        evaluate_case(case, DEFAULT_RULE)
    assert exc_info.value.code == "INVALID_REVERSAL_DATE"


def test_rule_tat_days_null():
    unverified_rule = {
        "id": "cnp_no_confirmation",
        "tat_days": None,
        "status": "verify",
    }
    case = make_case()
    with pytest.raises(AppError) as exc_info:
        evaluate_case(case, unverified_rule)
    assert exc_info.value.code == "RULE_UNVERIFIED"


def test_compensation_per_day_null():
    rule = {
        "id": "nach_debit_after_revocation",
        "tat_days": 1,
        "day_type": "calendar",
        "compensation_per_day": None,
        "status": "verify",
    }
    case = make_case(reversal_date=date(2026, 10, 16))
    res = evaluate_case(case, rule)
    assert res.delay_days == 3
    assert res.compensation_amount == 0
