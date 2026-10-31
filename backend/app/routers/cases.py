import json
from datetime import date, datetime
from typing import Any, Literal
from fastapi import APIRouter, Query
from pydantic import BaseModel, Field

from app.config import AppError as ConfigAppError, IST, get_settings, today_ist
from app.services.deadline import evaluate_case
from app.services.store import store

try:
    from app.schemas import AppError, Case, CaseCreate, EvaluationResult, Event
except ImportError:
    AppError = ConfigAppError

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

    class Event(BaseModel):
        type: str
        at: datetime
        note: str | None = None
        reference: str | None = None
        outcome: (
            Literal["resolved", "rejected", "unsatisfactory", "no_reply"] | None
        ) = None

    class CaseCreate(BaseModel):
        transaction: Transaction
        complainant: Complainant = Complainant()
        reversal_date: date | None = None

    class Case(CaseCreate):
        id: str
        created_at: datetime
        events: list[Event] = []
        letters: list = []
        risk: dict | None = None

    EvaluationResult = Any  # type: ignore

router = APIRouter(prefix="/cases", tags=["cases"])

VALID_EVENT_TYPES = {
    "bank_complaint_sent",
    "bank_replied",
    "nodal_complaint_sent",
    "principal_nodal_sent",
    "ombudsman_filed",
    "reversal_received",
    "compensation_received",
}


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


class CaseCreateResponse(BaseModel):
    case: Any
    warnings: list[str]


class CasePatch(BaseModel):
    reversal_date: date | None = None


_RULES_CACHE: dict[str, Any] = {}
_RULES_MTIME: float = 0.0


def _load_rules_cached() -> dict[str, Any]:
    global _RULES_CACHE, _RULES_MTIME
    settings = get_settings()
    rules_file = settings.data_dir / "rules.json"
    if not rules_file.exists():
        raise AppError("RULE_NOT_FOUND", "Rules file not found.", 404)

    try:
        current_mtime = rules_file.stat().st_mtime
        if not _RULES_CACHE or current_mtime != _RULES_MTIME:
            data = json.loads(rules_file.read_text(encoding="utf-8"))
            _RULES_CACHE = data
            _RULES_MTIME = current_mtime
        return _RULES_CACHE
    except Exception as e:
        raise AppError("RULE_NOT_FOUND", f"Error reading rules: {e}", 500)


def get_rule_by_id(rule_id: str) -> dict[str, Any]:
    data = _load_rules_cached()
    for r in data.get("rules", []):
        if r.get("id") == rule_id:
            res = dict(r)
            if "circular" in data:
                res.setdefault("circular_ref", data["circular"].get("ref"))
                res.setdefault("source_url", data["circular"].get("url"))
            return res
    raise AppError("RULE_NOT_FOUND", f"Rule '{rule_id}' not found.", 404)


@router.post("", response_model=CaseCreateResponse)
def create_case(payload: CaseCreate):
    rule = get_rule_by_id(payload.transaction.rule_id)

    txn_date = _parse_to_ist_date(payload.transaction.txn_datetime)
    today = today_ist()

    if txn_date > today:
        raise AppError(
            code="VALIDATION_ERROR",
            message="Transaction date cannot be in the future.",
            status=422,
        )

    if payload.reversal_date is not None:
        rev_date = _parse_to_ist_date(payload.reversal_date)
        if rev_date < txn_date:
            raise AppError(
                code="INVALID_REVERSAL_DATE",
                message="Reversal date cannot be earlier than transaction date.",
                status=422,
            )

    warnings: list[str] = []
    existing = store.find_by_utr(payload.transaction.utr)
    if existing:
        warnings.append(
            f"A case with UTR '{payload.transaction.utr}' already exists."
        )

    if rule.get("rail") == "UPI":
        utr_clean = payload.transaction.utr.strip()
        if not (len(utr_clean) == 12 and utr_clean.isdigit()):
            warnings.append(
                "UPI UTR typically consists of exactly 12 numeric digits."
            )

    case = store.create(payload)
    return CaseCreateResponse(case=case, warnings=warnings)


@router.get("")
def list_cases(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    all_cases = store.list()
    return all_cases[offset : offset + limit]


@router.get("/{case_id}")
def get_case(case_id: str):
    return store.get(case_id)


@router.patch("/{case_id}")
def patch_case(case_id: str, patch: CasePatch):
    case = store.get(case_id)
    txn = getattr(case, "transaction", None) or case.get("transaction", {})
    dt_val = getattr(txn, "txn_datetime", None) or txn.get("txn_datetime")
    txn_date = _parse_to_ist_date(dt_val)

    if patch.reversal_date is not None:
        rev_date = _parse_to_ist_date(patch.reversal_date)
        if rev_date < txn_date:
            raise AppError(
                code="INVALID_REVERSAL_DATE",
                message="Reversal date cannot be earlier than transaction date.",
                status=422,
            )

    return store.update(case_id, reversal_date=patch.reversal_date)


@router.post("/{case_id}/evaluate")
def evaluate_case_endpoint(case_id: str):
    case = store.get(case_id)
    txn = getattr(case, "transaction", None) or case.get("transaction", {})
    rule_id = getattr(txn, "rule_id", None) or txn.get("rule_id")
    rule = get_rule_by_id(rule_id)
    return evaluate_case(case, rule)


@router.post("/{case_id}/events")
def add_case_event(case_id: str, event: Event):
    if event.type not in VALID_EVENT_TYPES:
        raise AppError(
            code="VALIDATION_ERROR",
            message=f"Invalid event type '{event.type}'. Allowed types: {', '.join(sorted(VALID_EVENT_TYPES))}",
            status=422,
        )

    case = store.get(case_id)
    txn = getattr(case, "transaction", None) or case.get("transaction", {})
    txn_date = _parse_to_ist_date(
        getattr(txn, "txn_datetime", None) or txn.get("txn_datetime")
    )

    rev_date = getattr(case, "reversal_date", None)
    if rev_date is None and isinstance(case, dict):
        rev_date = case.get("reversal_date")

    if event.type == "reversal_received" and rev_date is None:
        event_date = _parse_to_ist_date(event.at)
        if event_date < txn_date:
            raise AppError(
                code="INVALID_REVERSAL_DATE",
                message="Reversal event date cannot be earlier than transaction date.",
                status=422,
            )
        store.update(case_id, reversal_date=event_date)

    return store.add_event(case_id, event)


@router.get("/{case_id}/escalation")
def get_case_escalation(case_id: str):
    case = store.get(case_id)
    from app.services.escalation import build_escalation

    return build_escalation(case)
