import json
import threading
from pathlib import Path
from typing import Dict, List, Optional, Any
from app.config import settings
from app.schemas import Case, Event, Transaction, EvaluationResult

class DataStore:
    def __init__(self, storage_dir: Optional[Path] = None):
        self.lock = threading.Lock()
        self.storage_dir = storage_dir or settings.STORAGE_DIR
        try:
            self.storage_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            pass
        self.cases_file = self.storage_dir / "cases.json"
        self.events_file = self.storage_dir / "events.json"
        self.tmp_cases_file = Path("/tmp/unstuck_cases.json")
        
        self._cases: Dict[str, Case] = {}
        self._load_from_disk()

    def _load_from_disk(self):
        with self.lock:
            target_file = self.tmp_cases_file if self.tmp_cases_file.exists() else self.cases_file
            if target_file.exists():
                try:
                    with open(target_file, "r", encoding="utf-8") as f:
                        data = json.load(f)
                        for item in data:
                            case_obj = Case.model_validate(item)
                            self._cases[case_obj.id] = case_obj
                except Exception as e:
                    print(f"Warning: Failed loading cases from disk: {e}")

    def _save_to_disk(self):
        dumped = [c.model_dump(mode="json") for c in self._cases.values()]
        try:
            with open(self.cases_file, "w", encoding="utf-8") as f:
                json.dump(dumped, f, indent=2, default=str)
        except Exception:
            try:
                self.tmp_cases_file.parent.mkdir(parents=True, exist_ok=True)
                with open(self.tmp_cases_file, "w", encoding="utf-8") as f:
                    json.dump(dumped, f, indent=2, default=str)
            except Exception as e:
                print(f"Warning: Failed saving cases to disk: {e}")

    def save_case(self, case: Case) -> Case:
        with self.lock:
            self._cases[case.id] = case
            self._save_to_disk()
            return case

    def get_case(self, case_id: str) -> Optional[Case]:
        with self.lock:
            return self._cases.get(case_id)

    def list_cases(self) -> List[Case]:
        with self.lock:
            # Return sorted by created_at desc
            return sorted(
                list(self._cases.values()),
                key=lambda c: c.created_at,
                reverse=True
            )

    def update_case(self, case_id: str, updates: Dict[str, Any]) -> Optional[Case]:
        with self.lock:
            case = self._cases.get(case_id)
            if not case:
                return None
            
            data = case.model_dump()
            data.update({k: v for k, v in updates.items() if v is not None})
            updated_case = Case.model_validate(data)
            self._cases[case_id] = updated_case
            self._save_to_disk()
            return updated_case

    def delete_case(self, case_id: str) -> bool:
        with self.lock:
            if case_id in self._cases:
                del self._cases[case_id]
                self._save_to_disk()
                return True
            return False

    def add_event(self, case_id: str, event: Event) -> Optional[Event]:
        with self.lock:
            case = self._cases.get(case_id)
            if not case:
                return None
            case.events.append(event)
            self._save_to_disk()
            return event

    def get_events(self, case_id: str) -> List[Event]:
        with self.lock:
            case = self._cases.get(case_id)
            if not case:
                return []
            return sorted(case.events, key=lambda e: e.timestamp)

    def list_all_references(self) -> List[str]:
        with self.lock:
            refs = []
            for c in self._cases.values():
                ref = c.transaction.reference
                if ref:
                    refs.append(ref)
            return refs

# Global store singleton
store = DataStore()
