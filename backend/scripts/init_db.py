"""
UFO Database Initialization & Seeding Script (`backend/scripts/init_db.py`).
Initializes all MongoDB Atlas (`ufo_fraud_db`) & Local-Persistent collections, creates indexes, and seeds:
  1. `hall_of_shame` (Known blacklisted scammer VPAs including `reyshastri-8@oksbi` & `sayasha36@oksbi`)
  2. `whitelisted_vpas` (Verified safe VPAs that must NEVER be flagged, including `8890032239@ptsbi`)
  3. `npci_ledger` (Settled 12-digit UTR test cases for both flagged and safe UPI IDs)
  4. `rbi_guidelines` (RBI & NPCI Regulatory RAG Knowledge Base)
  5. `semantic_cache` (10ms Scam Templates)
  6. `users` & `receivers` (Sample baseline profiles)

Usage:
    python backend/scripts/init_db.py
"""
import json
import sys
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from app.db.mongo import (
    DATA_DIR,
    LocalMongoDatabase,
    MANDATORY_FLAGGED_VPAS,
    MANDATORY_SAFE_VPAS,
    db,
    get_db_status,
    user_collection,
    receiver_collection,
    hall_collection,
    whitelist_collection,
    ledger_collection,
    semantic_cache_collection,
    rbi_guidelines_collection,
    _seed_defaults_if_empty,
)
from app.layer3_agent.rbi_rag import RBI_REGULATORY_KNOWLEDGE_BASE
from app.layer3_agent.semantic_cache import DEFAULT_SCAM_TEMPLATES


def init_and_seed_database() -> None:
    print("🛸 Initializing UFO (Unified Fraud Observer) Database (MongoDB Atlas + Local Backup)...")

    # Create indexes (no-op on local JSON engine, creates B-tree indexes on live MongoDB)
    user_collection.create_index("user_id", unique=True)
    receiver_collection.create_index("receiver", unique=True)
    hall_collection.create_index("receiver", unique=True)
    whitelist_collection.create_index("receiver", unique=True)
    ledger_collection.create_index("utr_number", unique=True)
    db["investigations"].create_index("investigation_id", unique=True)
    db["hitl_dead_letter_queue"].create_index("ticket_id", unique=True)
    db["pii_token_vault"].create_index("token", unique=True)

    # Seed mandatory defaults (hall_of_shame, whitelisted_vpas, npci_ledger test cases)
    _seed_defaults_if_empty()

    # Seed RBI RAG Knowledge Base
    rbi_guidelines_collection.delete_many({})
    rbi_guidelines_collection.insert_many(RBI_REGULATORY_KNOWLEDGE_BASE)

    # Seed Semantic Cache Templates (strip runtime _vec before saving)
    clean_templates = [
        {k: v for k, v in tpl.items() if k != "_vec"} for tpl in DEFAULT_SCAM_TEMPLATES
    ]
    semantic_cache_collection.delete_many({})
    semantic_cache_collection.insert_many(clean_templates)

    # Seed sample trusted user history (including 8890032239@ptsbi as trusted payee)
    for uid in ["dhawal@okicici", "citizen@sbi", "user@okicici", "unknown_user"]:
        user_collection.update_one(
            {"user_id": uid},
            {
                "$set": {
                    "user_id": uid,
                    "receivers": [
                        "8890032239@ptsbi",
                        "mom@oksbi",
                        "grocery.store@paytm",
                        "zomato@hdfcbank",
                        "swiggy@icici",
                    ],
                }
            },
            upsert=True,
        )

    # Seed flagged receiver stats for reyshastri-8@oksbi and sayasha36@oksbi
    for flagged in MANDATORY_FLAGGED_VPAS:
        receiver_collection.update_one(
            {"receiver": flagged["receiver"]},
            {
                "$set": {
                    "receiver": flagged["receiver"],
                    "transactions": flagged["transactions"],
                    "total_risk": round(flagged["avg_risk"] * flagged["transactions"], 2),
                    "flag_count": flagged["flag_count"],
                    "status": "BLACKLISTED",
                }
            },
            upsert=True,
        )

    # Also mirror into backend/data/ufo_local_db.json so offline/serverless fallback has identical data
    local_mirror = LocalMongoDatabase(DATA_DIR / "ufo_local_db.json")
    for coll_name in [
        "users",
        "receivers",
        "hall_of_shame",
        "whitelisted_vpas",
        "npci_ledger",
        "rbi_guidelines",
        "semantic_cache",
    ]:
        docs = list(db[coll_name].find({}, {"_id": 0}))
        local_mirror[coll_name].delete_many({})
        if docs:
            local_mirror[coll_name].insert_many(docs)

    status = get_db_status()
    print(f"✅ Database Mode: {status['backend_mode']} ({status['db_name']})")
    print("📊 Collection Counts:")
    for coll_name, count in status["collections"].items():
        print(f"   - {coll_name}: {count} documents")
    print(f"   - rbi_guidelines: {rbi_guidelines_collection.count_documents({})} documents")
    print(f"   - semantic_cache: {semantic_cache_collection.count_documents({})} templates")


if __name__ == "__main__":
    init_and_seed_database()
