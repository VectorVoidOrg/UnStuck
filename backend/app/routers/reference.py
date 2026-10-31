import json
from pathlib import Path
from typing import Any
from fastapi import APIRouter, Query

from app.config import AppError as ConfigAppError, get_settings

try:
    from app.schemas import AppError
except ImportError:
    AppError = ConfigAppError

router = APIRouter(tags=["reference"])

# In-memory caches to avoid disk I/O bottlenecks
_RULES_DATA: dict[str, Any] = {}
_RULES_MTIME: float = 0.0

_BANKS_DATA: list[dict[str, Any]] = []
_BANKS_MTIME: float = 0.0


def _get_rules_cached() -> dict[str, Any]:
    global _RULES_DATA, _RULES_MTIME
    settings = get_settings()
    rules_file = settings.data_dir / "rules.json"
    if not rules_file.exists():
        raise AppError("RULE_NOT_FOUND", "Rules file not found.", 404)

    try:
        mtime = rules_file.stat().st_mtime
        if not _RULES_DATA or mtime != _RULES_MTIME:
            _RULES_DATA = json.loads(rules_file.read_text(encoding="utf-8"))
            _RULES_MTIME = mtime
        return _RULES_DATA
    except Exception as e:
        raise AppError("RULE_NOT_FOUND", f"Error reading rules: {e}", 500)


def _get_banks_cached() -> list[dict[str, Any]]:
    global _BANKS_DATA, _BANKS_MTIME
    settings = get_settings()
    banks_file = settings.data_dir / "banks.json"
    if not banks_file.exists():
        return []

    try:
        mtime = banks_file.stat().st_mtime
        if not _BANKS_DATA or mtime != _BANKS_MTIME:
            _BANKS_DATA = json.loads(banks_file.read_text(encoding="utf-8"))
            _BANKS_MTIME = mtime
        return _BANKS_DATA
    except Exception:
        return []


@router.get("/rules")
def get_rules() -> dict[str, Any]:
    data = _get_rules_cached()
    circular = data.get("circular", {})
    rules_out = []
    for r in data.get("rules", []):
        tat_days = r.get("tat_days")
        status = r.get("status")
        requires_verification = (status == "verify") or (tat_days is None)
        rules_out.append(
            {
                "id": r.get("id"),
                "rail": r.get("rail"),
                "failure": r.get("failure"),
                "label": r.get("label"),
                "user_prompt": r.get("user_prompt"),
                "tat_days": tat_days,
                "status": status,
                "requires_verification": requires_verification,
                "compensation_per_day": r.get("compensation_per_day"),
            }
        )

    return {"circular": circular, "rules": rules_out}


@router.get("/banks")
def get_banks(
    q: str = Query(default="", max_length=100)
) -> list[dict[str, Any]]:
    banks = _get_banks_cached()
    query = q.strip().lower()[:100]
    if not query:
        return banks

    matched = []
    for bank in banks:
        name = bank.get("name", "").lower()
        aliases = [a.lower() for a in bank.get("aliases", [])]
        if query in name or any(query in a for a in aliases):
            matched.append(bank)

    return matched
