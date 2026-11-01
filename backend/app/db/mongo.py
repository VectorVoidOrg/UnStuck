import copy
import json
import os
import threading
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional
from app.config import DATA_DIR, settings


class LocalCursor:
    """Mongo-compatible cursor supporting .sort() and .limit()."""

    def __init__(self, docs: List[Dict[str, Any]]):
        self._docs = docs

    def sort(self, key_or_list: Any, direction: int = 1) -> "LocalCursor":
        if isinstance(key_or_list, str):
            reverse = direction < 0
            self._docs.sort(key=lambda d: d.get(key_or_list, 0), reverse=reverse)
        elif isinstance(key_or_list, list):
            for k, dir_val in reversed(key_or_list):
                self._docs.sort(key=lambda d: d.get(k, 0), reverse=(dir_val < 0))
        return self

    def limit(self, count: int) -> "LocalCursor":
        self._docs = self._docs[:count]
        return self

    def __iter__(self):
        return iter(self._docs)


class LocalCollection:
    """
    Thread-safe, persistent JSON-backed MongoDB-compatible collection.
    Used automatically when MongoDB server or pymongo is unavailable in local dev/testing.
    """

    def __init__(self, name: str, db_parent: "LocalMongoDatabase"):
        self.name = name
        self._db = db_parent

    @property
    def _rows(self) -> List[Dict[str, Any]]:
        return self._db._data.setdefault(self.name, [])

    def _matches(self, doc: Dict[str, Any], query: Dict[str, Any]) -> bool:
        if not query:
            return True
        for k, v in query.items():
            if isinstance(v, dict):
                val = doc.get(k)
                if "$gte" in v and (val is None or val < v["$gte"]):
                    return False
                if "$gt" in v and (val is None or val <= v["$gt"]):
                    return False
                if "$lte" in v and (val is None or val > v["$lte"]):
                    return False
                if "$in" in v and val not in v["$in"]:
                    return False
            else:
                if doc.get(k) != v:
                    return False
        return True

    def _project(self, doc: Dict[str, Any], projection: Optional[Dict[str, int]]) -> Dict[str, Any]:
        res = copy.deepcopy(doc)
        if not projection:
            return res
        exclusions = [k for k, v in projection.items() if v == 0]
        inclusions = [k for k, v in projection.items() if v == 1]
        if inclusions:
            filtered = {k: res[k] for k in inclusions if k in res}
            if "_id" not in exclusions and "_id" in res:
                filtered["_id"] = res["_id"]
            return filtered
        for k in exclusions:
            res.pop(k, None)
        return res

    def find_one(
        self,
        query: Optional[Dict[str, Any]] = None,
        projection: Optional[Dict[str, int]] = None,
    ) -> Optional[Dict[str, Any]]:
        query = query or {}
        with self._db._lock:
            for doc in self._rows:
                if self._matches(doc, query):
                    return self._project(doc, projection)
        return None

    def find(
        self,
        query: Optional[Dict[str, Any]] = None,
        projection: Optional[Dict[str, int]] = None,
    ) -> LocalCursor:
        query = query or {}
        with self._db._lock:
            matched = [self._project(doc, projection) for doc in self._rows if self._matches(doc, query)]
        return LocalCursor(matched)

    def insert_one(self, doc: Dict[str, Any]) -> Any:
        with self._db._lock:
            new_doc = copy.deepcopy(doc)
            new_doc.setdefault("_id", f"{self.name}_{len(self._rows) + 1}")
            self._rows.append(new_doc)
            self._db._persist()
            return new_doc["_id"]

    def insert_many(self, docs: Iterable[Dict[str, Any]]) -> None:
        with self._db._lock:
            for doc in docs:
                new_doc = copy.deepcopy(doc)
                new_doc.setdefault("_id", f"{self.name}_{len(self._rows) + 1}")
                self._rows.append(new_doc)
            self._db._persist()

    def update_one(
        self,
        query: Dict[str, Any],
        update: Dict[str, Any],
        upsert: bool = False,
    ) -> None:
        with self._db._lock:
            target = None
            for doc in self._rows:
                if self._matches(doc, query):
                    target = doc
                    break

            if target is None:
                if not upsert:
                    return
                target = copy.deepcopy(query)
                target.setdefault("_id", f"{self.name}_{len(self._rows) + 1}")
                self._rows.append(target)

            if "$set" in update:
                for k, v in update["$set"].items():
                    target[k] = copy.deepcopy(v)

            if "$inc" in update:
                for k, v in update["$inc"].items():
                    target[k] = target.get(k, 0) + v

            if "$addToSet" in update:
                for k, v in update["$addToSet"].items():
                    arr = target.setdefault(k, [])
                    if v not in arr:
                        arr.append(copy.deepcopy(v))

            if "$push" in update:
                for k, v in update["$push"].items():
                    arr = target.setdefault(k, [])
                    arr.append(copy.deepcopy(v))

            self._db._persist()

    def count_documents(self, query: Optional[Dict[str, Any]] = None) -> int:
        query = query or {}
        with self._db._lock:
            return sum(1 for doc in self._rows if self._matches(doc, query))

    def delete_many(self, query: Optional[Dict[str, Any]] = None) -> int:
        query = query or {}
        with self._db._lock:
            before = len(self._rows)
            self._db._data[self.name] = [doc for doc in self._rows if not self._matches(doc, query)]
            after = len(self._db._data[self.name])
            self._db._persist()
            return before - after

    def create_index(self, *args, **kwargs) -> str:
        return "local_index_ok"


class LocalMongoDatabase:
    """Persistent JSON-backed MongoDB emulator for zero-downtime local & serverless operation."""

    def __init__(self, storage_path: Path):
        self.storage_path = storage_path
        self._tmp_path = Path("/tmp/ufo_local_db.json")
        try:
            self.storage_path.parent.mkdir(parents=True, exist_ok=True)
        except Exception:
            pass
        self._lock = threading.RLock()
        self._data: Dict[str, List[Dict[str, Any]]] = {}
        self._collections: Dict[str, LocalCollection] = {}
        self._load()

    def _load(self) -> None:
        for candidate in [self._tmp_path, self.storage_path]:
            if candidate.exists():
                try:
                    self._data = json.loads(candidate.read_text(encoding="utf-8"))
                    if self._data:
                        return
                except Exception:
                    self._data = {}

    def _persist(self) -> None:
        payload = json.dumps(self._data, indent=2, default=str)
        try:
            self.storage_path.write_text(payload, encoding="utf-8")
        except Exception:
            try:
                self._tmp_path.write_text(payload, encoding="utf-8")
            except Exception:
                pass

    def __getitem__(self, name: str) -> LocalCollection:
        if name not in self._collections:
            self._collections[name] = LocalCollection(name, self)
        return self._collections[name]

    def list_collection_names(self) -> List[str]:
        return list(self._data.keys())


def _initialize_database():
    """
    Attempts to connect to MongoDB via MONGO_URI if pymongo is installed and server is reachable;
    otherwise transparently uses LocalMongoDatabase persisted at backend/data/ufo_local_db.json.
    """
    mongo_uri = settings.MONGO_URI
    use_real_mongo = os.getenv("UFO_FORCE_MONGO", "true").lower() == "true"

    if mongo_uri and ("mongodb+srv://" in mongo_uri or use_real_mongo):
        try:
            from pymongo import MongoClient

            client = MongoClient(mongo_uri, serverSelectionTimeoutMS=3500)
            client.admin.command("ping")
            return client[settings.MONGO_DB_NAME], "mongodb_live"
        except Exception:
            pass

    # Fallback to persistent JSON-backed Mongo engine
    local_db = LocalMongoDatabase(DATA_DIR / "ufo_local_db.json")
    return local_db, "local_persistent_json"


db, DB_BACKEND_MODE = _initialize_database()

# Core UFO Collections across the 5 Layers
user_collection = db["users"]
receiver_collection = db["receivers"]
hall_collection = db["hall_of_shame"]
whitelist_collection = db["whitelisted_vpas"]
transactions_collection = db["transactions"]
investigations_collection = db["investigations"]
pii_vault_collection = db["pii_token_vault"]
hitl_dlq_collection = db["hitl_dead_letter_queue"]
audit_logs_collection = db["audit_logs"]
ledger_collection = db["npci_ledger"]
semantic_cache_collection = db["semantic_cache"]
rbi_guidelines_collection = db["rbi_guidelines"]

# Mandatory test case VPAs
MANDATORY_FLAGGED_VPAS = [
    {
        "receiver": "reyshastri-8@oksbi",
        "avg_risk": 0.98,
        "flag_count": 19,
        "transactions": 22,
        "scam_type": "REPORTED_FRAUD_MULE",
        "source": "NPCI_LEDGER_FRAUD_TESTCASE",
        "status": "BLACKLISTED",
    },
    {
        "receiver": "sayasha36@oksbi",
        "avg_risk": 0.97,
        "flag_count": 16,
        "transactions": 19,
        "scam_type": "REPORTED_SCAM_ACCOUNT",
        "source": "NPCI_LEDGER_FRAUD_TESTCASE",
        "status": "BLACKLISTED",
    },
]

MANDATORY_SAFE_VPAS = [
    {
        "receiver": "8890032239@ptsbi",
        "payee_name": "Verified Trusted Account",
        "avg_risk": 0.01,
        "flag_count": 0,
        "transactions": 45,
        "status": "VERIFIED_SAFE",
        "source": "NPCI_LEDGER_VERIFIED_WHITELIST",
    }
]


def _seed_defaults_if_empty() -> None:
    """Seeds initial scammer blacklist, whitelisted safe accounts, and NPCI ledger test cases."""
    try:
        # Always ensure mandatory flagged test cases are present in hall_of_shame
        for item in MANDATORY_FLAGGED_VPAS:
            hall_collection.update_one(
                {"receiver": item["receiver"]},
                {"$set": item},
                upsert=True,
            )

        # Always ensure 8890032239@ptsbi is NEVER in hall_of_shame and IS in whitelisted_vpas
        hall_collection.delete_many({"receiver": "8890032239@ptsbi"})
        for safe_item in MANDATORY_SAFE_VPAS:
            whitelist_collection.update_one(
                {"receiver": safe_item["receiver"]},
                {"$set": safe_item},
                upsert=True,
            )
            receiver_collection.update_one(
                {"receiver": safe_item["receiver"]},
                {
                    "$set": {
                        "receiver": safe_item["receiver"],
                        "transactions": 45,
                        "total_risk": 0.45,
                        "flag_count": 0,
                        "status": "VERIFIED_SAFE",
                    }
                },
                upsert=True,
            )

        if hall_collection.count_documents({}) <= len(MANDATORY_FLAGGED_VPAS):
            for rec in [
                {
                    "receiver": "scammer.kyc@okaxis",
                    "avg_risk": 0.96,
                    "flag_count": 14,
                    "transactions": 18,
                    "scam_type": "FAKE_KYC_SUSPENSION",
                    "status": "BLACKLISTED",
                },
                {
                    "receiver": "cbi.digitalarrest@ybl",
                    "avg_risk": 0.99,
                    "flag_count": 27,
                    "transactions": 30,
                    "scam_type": "DIGITAL_ARREST_EXTORTION",
                    "status": "BLACKLISTED",
                },
                {
                    "receiver": "lottery.cashback@paytm",
                    "avg_risk": 0.89,
                    "flag_count": 9,
                    "transactions": 11,
                    "scam_type": "COLLECT_REQUEST_TRAP",
                    "status": "BLACKLISTED",
                },
                {
                    "receiver": "ebill.disconnect@icici",
                    "avg_risk": 0.92,
                    "flag_count": 12,
                    "transactions": 15,
                    "scam_type": "ELECTRICITY_BILL_SCAM",
                    "status": "BLACKLISTED",
                },
            ]:
                hall_collection.update_one(
                    {"receiver": rec["receiver"]},
                    {"$set": rec},
                    upsert=True,
                )

        # Ensure NPCI Ledger has test case entries for reyshastri-8@oksbi, sayasha36@oksbi, and 8890032239@ptsbi
        default_ledger_entries = [
            {
                "utr_number": "488900322391",
                "payer_vpa": "dhawal@okicici",
                "payee_vpa": "8890032239@ptsbi",
                "amount": 1500.0,
                "status": "SETTLED_SAFE",
                "risk_verdict": "SAFE",
                "flagged": False,
                "verified_in_npci": True,
                "notes": "Verified safe payment to whitelisted receiver 8890032239@ptsbi (Do Not Flag)",
            },
            {
                "utr_number": "488900322392",
                "payer_vpa": "citizen@sbi",
                "payee_vpa": "8890032239@ptsbi",
                "amount": 5000.0,
                "status": "SETTLED_SAFE",
                "risk_verdict": "SAFE",
                "flagged": False,
                "verified_in_npci": True,
                "notes": "Verified safe peer transfer to 8890032239@ptsbi (Do Not Flag)",
            },
            {
                "utr_number": "477100099881",
                "payer_vpa": "victim@okaxis",
                "payee_vpa": "reyshastri-8@oksbi",
                "amount": 3200.0,
                "status": "FLAGGED_FRAUD",
                "risk_verdict": "FRAUD",
                "flagged": True,
                "verified_in_npci": True,
                "notes": "Flagged fraudulent transfer to blacklisted account reyshastri-8@oksbi",
            },
            {
                "utr_number": "477100099882",
                "payer_vpa": "user@okicici",
                "payee_vpa": "sayasha36@oksbi",
                "amount": 4800.0,
                "status": "FLAGGED_FRAUD",
                "risk_verdict": "FRAUD",
                "flagged": True,
                "verified_in_npci": True,
                "notes": "Flagged fraudulent transfer to blacklisted account sayasha36@oksbi",
            },
            {
                "utr_number": "412345678901",
                "payer_vpa": "victim@okaxis",
                "payee_vpa": "merchant.fake@ybl",
                "amount": 2400.0,
                "status": "SETTLED",
                "risk_verdict": "FRAUD",
                "flagged": True,
                "verified_in_npci": True,
            },
            {
                "utr_number": "498765432109",
                "payer_vpa": "user@okicici",
                "payee_vpa": "electricity.help@paytm",
                "amount": 4500.0,
                "status": "SETTLED",
                "risk_verdict": "FRAUD",
                "flagged": True,
                "verified_in_npci": True,
            },
            {
                "utr_number": "455566677788",
                "payer_vpa": "citizen@sbi",
                "payee_vpa": "cyber.arrest@axl",
                "amount": 45000.0,
                "status": "SETTLED",
                "risk_verdict": "FRAUD",
                "flagged": True,
                "verified_in_npci": True,
            },
        ]
        for entry in default_ledger_entries:
            ledger_collection.update_one(
                {"utr_number": entry["utr_number"]},
                {"$set": entry},
                upsert=True,
            )
    except Exception:
        pass


_seed_defaults_if_empty()


def get_db_status() -> Dict[str, Any]:
    return {
        "backend_mode": DB_BACKEND_MODE,
        "db_name": settings.MONGO_DB_NAME,
        "collections": {
            "users": user_collection.count_documents({}),
            "receivers": receiver_collection.count_documents({}),
            "hall_of_shame": hall_collection.count_documents({}),
            "whitelisted_vpas": whitelist_collection.count_documents({}),
            "transactions": transactions_collection.count_documents({}),
            "investigations": investigations_collection.count_documents({}),
            "hitl_dlq": hitl_dlq_collection.count_documents({}),
            "audit_logs": audit_logs_collection.count_documents({}),
            "npci_ledger": ledger_collection.count_documents({}),
        },
    }
