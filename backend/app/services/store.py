import json
import os
from pathlib import Path
import threading
from uuid import uuid4
from datetime import datetime
from typing import Any

from app.config import IST, get_settings

try:
    from app.schemas import AppError, Case, CaseCreate, Event, LetterRecord
except ImportError:
    from pydantic import BaseModel

    class AppError(Exception):
        def __init__(self, code: str, message: str, status: int = 422):
            self.code, self.message, self.status = code, message, status
            super().__init__(message)

    class Case(BaseModel):
        id: str
        created_at: datetime
        transaction: Any
        complainant: Any = None
        reversal_date: Any = None
        events: list = []
        letters: list = []
        risk: dict | None = None

    CaseCreate = Any  # type: ignore
    Event = Any  # type: ignore
    LetterRecord = Any  # type: ignore


class CaseStore:
    """Thread-safe case store with safe atomic persistence and non-blocking I/O."""

    def __init__(self, persist_path: str | Path | None = None):
        self._lock = threading.Lock()
        self._save_lock = threading.Lock()
        self._cases: dict[str, Any] = {}
        settings = get_settings()
        self._persist_path = (
            Path(persist_path)
            if persist_path
            else (Path(settings.persist_path) if settings.persist_path else None)
        )
        self._load()

    def _load(self) -> None:
        if self._persist_path and self._persist_path.exists():
            try:
                data = json.loads(self._persist_path.read_text(encoding="utf-8"))
                for c_data in data:
                    try:
                        case = Case.model_validate(c_data)
                        self._cases[case.id] = case
                    except Exception:
                        if isinstance(c_data, dict) and "id" in c_data:
                            self._cases[c_data["id"]] = c_data
            except Exception:
                pass

    def _save_snapshot(self, cases_snapshot: list[dict[str, Any]]) -> None:
        """Atomic write to disk outside the store lock to eliminate lock contention,

        with a dedicated save lock and unique temp file to avoid WinError 32 on Windows.
        """
        if not self._persist_path:
            return
        with self._save_lock:
            try:
                self._persist_path.parent.mkdir(parents=True, exist_ok=True)
                tmp_path = self._persist_path.with_name(
                    f"{self._persist_path.name}.{uuid4().hex[:8]}.tmp"
                )
                tmp_path.write_text(
                    json.dumps(cases_snapshot, indent=2), encoding="utf-8"
                )
                os.replace(tmp_path, self._persist_path)
            except Exception:
                pass

    def _dump_cases_locked(self) -> list[dict[str, Any]]:
        res = []
        for case in self._cases.values():
            if hasattr(case, "model_dump"):
                res.append(case.model_dump(mode="json"))
            elif isinstance(case, dict):
                res.append(case)
        return res

    def create(self, data: Any) -> Any:
        with self._lock:
            case_id = uuid4().hex[:12]
            now = datetime.now(IST)
            txn = getattr(data, "transaction", None) or (
                data.get("transaction") if isinstance(data, dict) else None
            )
            comp = getattr(data, "complainant", None) or (
                data.get("complainant") if isinstance(data, dict) else None
            )
            rev = getattr(data, "reversal_date", None) or (
                data.get("reversal_date") if isinstance(data, dict) else None
            )

            case = Case(
                id=case_id,
                created_at=now,
                transaction=txn,
                complainant=comp,
                reversal_date=rev,
                events=[],
                letters=[],
                risk=None,
            )
            self._cases[case_id] = case
            snapshot = self._dump_cases_locked()

        self._save_snapshot(snapshot)
        return case

    def get(self, case_id: str) -> Any:
        with self._lock:
            if case_id not in self._cases:
                raise AppError(
                    code="CASE_NOT_FOUND",
                    message=f"Case '{case_id}' not found.",
                    status=404,
                )
            return self._cases[case_id]

    def update(self, case_id: str, **fields) -> Any:
        with self._lock:
            if case_id not in self._cases:
                raise AppError(
                    code="CASE_NOT_FOUND",
                    message=f"Case '{case_id}' not found.",
                    status=404,
                )
            case = self._cases[case_id]
            if hasattr(case, "model_copy"):
                updated_case = case.model_copy(update=fields)
            else:
                updated_case = dict(case)
                updated_case.update(fields)
            self._cases[case_id] = updated_case
            snapshot = self._dump_cases_locked()

        self._save_snapshot(snapshot)
        return updated_case

    def add_event(self, case_id: str, event: Any) -> Any:
        with self._lock:
            if case_id not in self._cases:
                raise AppError(
                    code="CASE_NOT_FOUND",
                    message=f"Case '{case_id}' not found.",
                    status=404,
                )
            case = self._cases[case_id]
            if hasattr(case, "events"):
                case.events.append(event)
            else:
                case.setdefault("events", []).append(event)
            snapshot = self._dump_cases_locked()

        self._save_snapshot(snapshot)
        return case

    def add_letter(self, case_id: str, record: Any) -> Any:
        with self._lock:
            if case_id not in self._cases:
                raise AppError(
                    code="CASE_NOT_FOUND",
                    message=f"Case '{case_id}' not found.",
                    status=404,
                )
            case = self._cases[case_id]
            if hasattr(case, "letters"):
                case.letters.append(record)
            else:
                case.setdefault("letters", []).append(record)
            snapshot = self._dump_cases_locked()

        self._save_snapshot(snapshot)
        return case

    def set_risk(self, case_id: str, risk: dict) -> Any:
        with self._lock:
            if case_id not in self._cases:
                raise AppError(
                    code="CASE_NOT_FOUND",
                    message=f"Case '{case_id}' not found.",
                    status=404,
                )
            case = self._cases[case_id]
            if hasattr(case, "risk"):
                case.risk = risk
            else:
                case["risk"] = risk
            snapshot = self._dump_cases_locked()

        self._save_snapshot(snapshot)
        return case

    def list(self) -> list[Any]:
        with self._lock:
            return list(self._cases.values())

    def find_by_utr(self, utr: str) -> list[Any]:
        with self._lock:
            res = []
            for c in self._cases.values():
                c_utr = getattr(getattr(c, "transaction", None), "utr", None)
                if c_utr is None and isinstance(c, dict):
                    c_utr = c.get("transaction", {}).get("utr")
                if c_utr == utr:
                    res.append(c)
            return res


store = CaseStore()
