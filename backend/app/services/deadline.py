import json
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

from app.config import IST, get_settings, today_ist
from app.services.penalty import compute_compensation

try:
    from app.schemas import AppError, Case, EvaluationResult
except ImportError:
    from pydantic import BaseModel

    class AppError(Exception):
        def __init__(self, code: str, message: str, status: int = 422):
            self.code, self.message, self.status = code, message, status
            super().__init__(message)

    class EvaluationResult(BaseModel):
        case_id: str
        rule_id: str
        rule_label: str
        rule_status: str
        tat_days: int
        day_type: str
        txn_date: date
        due_date: date
        today: date
        reversal_date: date | None
        status: str
        delay_days: int
        per_day: int | None
        compensation_amount: int
        compensation_recipient: str
        explanation: list[str]
        circular_ref: str
        source_url: str

    Case = Any  # type: ignore

_CIRCULAR_CACHE: dict[str, Any] = {}
_RULES_MTIME: float = 0.0


def _get_circular_info() -> dict[str, str]:
    global _CIRCULAR_CACHE, _RULES_MTIME
    settings = get_settings()
    rules_path = settings.data_dir / "rules.json"
    if rules_path.exists():
        try:
            mtime = rules_path.stat().st_mtime
            if not _CIRCULAR_CACHE or mtime != _RULES_MTIME:
                data = json.loads(rules_path.read_text(encoding="utf-8"))
                _CIRCULAR_CACHE = data.get("circular", {})
                _RULES_MTIME = mtime
        except Exception:
            pass
    return {
        "circular_ref": _CIRCULAR_CACHE.get(
            "ref", "DPSS.CO.PD No.629/02.01.014/2019-20"
        ),
        "source_url": _CIRCULAR_CACHE.get(
            "url",
            "https://www.rbi.org.in/scripts/NotificationUser.aspx?Id=11693",
        ),
    }


def compute_due_date(
    txn_date: date,
    tat_days: int,
    day_type: str = "calendar",
    holidays: frozenset[date] = frozenset(),
) -> date:
    """Computes the deadline date based on transaction date, TAT days, and day type."""
    if tat_days < 0:
        raise ValueError("tat_days cannot be negative")

    if day_type == "calendar":
        return txn_date + timedelta(days=tat_days)

    cur = txn_date
    added = 0
    while added < tat_days:
        cur += timedelta(days=1)
        if cur.weekday() < 5 and cur not in holidays:
            added += 1
    return cur


def _format_date(d: date) -> str:
    return f"{d.day} {d.strftime('%b %Y')}"


def _parse_to_ist_date(val: Any) -> date:
    if isinstance(val, str):
        try:
            val = datetime.fromisoformat(val)
        except Exception:
            try:
                return date.fromisoformat(val[:10])
            except Exception:
                return date.today()

    if isinstance(val, datetime):
        if val.tzinfo is None:
            val = val.replace(tzinfo=IST)
        else:
            val = val.astimezone(IST)
        return val.date()

    if isinstance(val, date):
        return val

    return date.today()


def evaluate_case(
    case: Any, rule: dict[str, Any], today: date | None = None
) -> Any:
    """Evaluates case turnaround time, delays, and compensation per RBI rules."""
    tat_days = rule.get("tat_days")
    if tat_days is None:
        raise AppError(
            code="RULE_UNVERIFIED",
            message=f"Rule '{rule.get('id', 'unknown')}' does not have a confirmed turnaround time.",
            status=422,
        )

    # Convert txn_datetime safely to IST date
    txn = getattr(case, "transaction", None)
    dt_val = getattr(txn, "txn_datetime", None)
    if dt_val is None and isinstance(case, dict):
        dt_val = case.get("transaction", {}).get("txn_datetime")

    txn_date = _parse_to_ist_date(dt_val)

    day_type = rule.get("day_type", "calendar")
    due_date = compute_due_date(txn_date, tat_days, day_type=day_type)
    current_date = today if today is not None else today_ist()

    raw_reversal = getattr(case, "reversal_date", None)
    if raw_reversal is None and isinstance(case, dict):
        raw_reversal = case.get("reversal_date")

    reversal_date: date | None = None
    if raw_reversal is not None:
        reversal_date = _parse_to_ist_date(raw_reversal)

    if reversal_date is not None:
        if reversal_date < txn_date:
            raise AppError(
                code="INVALID_REVERSAL_DATE",
                message="Reversal date cannot be earlier than the transaction date.",
                status=422,
            )
        delay_days = max(0, (reversal_date - due_date).days)
        status = "reversed_on_time" if delay_days == 0 else "reversed_late"
    else:
        if current_date < due_date:
            status = "within_window"
            delay_days = 0
        elif current_date == due_date:
            status = "due_today"
            delay_days = 0
        else:
            status = "overdue"
            delay_days = (current_date - due_date).days

    per_day = rule.get("compensation_per_day")
    compensation_amount = compute_compensation(delay_days, per_day)

    explanation: list[str] = [
        f"Transaction date (T): {_format_date(txn_date)}",
        f"Deadline: T + {tat_days} {day_type} day{'s' if tat_days != 1 else ''} = {_format_date(due_date)}",
    ]

    if reversal_date is not None:
        if delay_days == 0:
            explanation.append(
                f"Money returned on {_format_date(reversal_date)}, on time"
            )
        else:
            explanation.append(
                f"Money returned on {_format_date(reversal_date)}, {delay_days} day{'s' if delay_days != 1 else ''} after the deadline"
            )
    else:
        if status == "within_window":
            explanation.append("Still pending: within resolution window")
        elif status == "due_today":
            explanation.append("Still pending: due today")
        else:
            explanation.append(
                f"Still pending: {delay_days} day{'s' if delay_days != 1 else ''} past the deadline"
            )

    circular_info = _get_circular_info()
    circular_ref = rule.get("circular_ref") or circular_info["circular_ref"]
    source_url = rule.get("source_url") or circular_info["source_url"]
    rule_row = rule.get("circular_row") or rule.get("label", rule.get("id"))
    explanation.append(f"Rule: {rule_row}. Source: {source_url}")

    case_id = (
        getattr(case, "id", None) or (case.get("id") if isinstance(case, dict) else "")
    )

    return EvaluationResult(
        case_id=case_id,
        rule_id=rule["id"],
        rule_label=rule.get("label", rule["id"]),
        rule_status=rule.get("status", "confirmed"),
        tat_days=tat_days,
        day_type=day_type,
        txn_date=txn_date,
        due_date=due_date,
        today=current_date,
        reversal_date=reversal_date,
        status=status,
        delay_days=delay_days,
        per_day=per_day,
        compensation_amount=compensation_amount,
        compensation_recipient=rule.get("compensation_recipient", "verify"),
        explanation=explanation,
        circular_ref=circular_ref,
        source_url=source_url,
    )
