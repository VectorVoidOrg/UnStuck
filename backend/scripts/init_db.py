"""
UFO Database Initialization & Seeding Script (`backend/scripts/init_db.py`).
Initializes all MongoDB / Local-Persistent collections, creates indexes, and seeds:
  1. `hall_of_shame` (Known blacklisted scammer VPAs)
  2. `npci_ledger` (Sample settled 12-digit UTRs for receipt verification)
  3. `rbi_guidelines` (RBI & NPCI Regulatory RAG Knowledge Base)
  4. `semantic_cache` (10ms Scam Templates)
  5. `users` & `receivers` (Sample baseline profiles)

Usage:
    python backend/scripts/init_db.py
"""
import sys
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from app.db.mongo import (
    db,
    get_db_status,
    user_collection,
    receiver_collection,
    hall_collection,
    ledger_collection,
    semantic_cache_collection,
    rbi_guidelines_collection,
)
from app.layer3_agent.rbi_rag import RBI_REGULATORY_KNOWLEDGE_BASE
from app.layer3_agent.semantic_cache import DEFAULT_SCAM_TEMPLATES


def init_and_seed_database() -> None:
    print("🛸 Initializing UFO (Unified Fraud Observer) Database...")

    # Create indexes (no-op on local JSON engine, creates B-tree indexes on live MongoDB)
    user_collection.create_index("user_id", unique=True)
    receiver_collection.create_index("receiver", unique=True)
    hall_collection.create_index("receiver", unique=True)
    ledger_collection.create_index("utr_number", unique=True)
    db["investigations"].create_index("investigation_id", unique=True)
    db["hitl_dead_letter_queue"].create_index("ticket_id", unique=True)
    db["pii_token_vault"].create_index("token", unique=True)

    # Seed RBI RAG Knowledge Base
    rbi_guidelines_collection.delete_many({})
    rbi_guidelines_collection.insert_many(RBI_REGULATORY_KNOWLEDGE_BASE)

    # Seed Semantic Cache Templates (strip runtime _vec before saving)
    clean_templates = [
        {k: v for k, v in tpl.items() if k != "_vec"} for tpl in DEFAULT_SCAM_TEMPLATES
    ]
    semantic_cache_collection.delete_many({})
    semantic_cache_collection.insert_many(clean_templates)

    # Seed sample trusted user history
    user_collection.update_one(
        {"user_id": "dhawal@okicici"},
        {
            "$set": {
                "user_id": "dhawal@okicici",
                "receivers": ["mom@oksbi", "grocery.store@paytm", "zomato@hdfcbank"],
            }
        },
        upsert=True,
    )

    status = get_db_status()
    print(f"✅ Database Mode: {status['backend_mode']} ({status['db_name']})")
    print("📊 Collection Counts:")
    for coll_name, count in status["collections"].items():
        print(f"   - {coll_name}: {count} documents")
    print(f"   - rbi_guidelines: {rbi_guidelines_collection.count_documents({})} documents")
    print(f"   - semantic_cache: {semantic_cache_collection.count_documents({})} templates")


if __name__ == "__main__":
    init_and_seed_database()
