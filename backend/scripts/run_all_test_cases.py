"""
UFO Backend — Complete Test Cases Runner (`backend/scripts/run_all_test_cases.py`).

Runs all backend test cases against the FastAPI app (connected to MongoDB Atlas `ufo_fraud_db`
& `npci_ledger`) and prints the exact request payloads and live responses so you can test them yourself.

Usage:
    python backend/scripts/run_all_test_cases.py
"""
import json
import sys
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


def print_case(idx: int, title: str, endpoint: str, payload: dict, response_data: dict) -> None:
    print(f"\n{'=' * 78}")
    print(f"TEST CASE #{idx}: {title}")
    print(f"Endpoint : {endpoint}")
    print(f"Input    : {json.dumps(payload)}")
    print(f"Response : {json.dumps(response_data, indent=2)}")


def main() -> None:
    print("🛸 UFO BACKEND — RUNNING ALL LIVE TEST CASES")

    # Check DB health & Ledger first
    health = client.get("/health").json()
    print(f"\n📊 Active Database Mode: {health['database']['backend_mode']} ({health['database']['db_name']})")
    print(f"📊 Collection Counts   : {json.dumps(health['database']['collections'])}")

    test_cases = [
        (
            "MUST NOT FLAG (Whitelisted Safe Receiver): 8890032239@ptsbi",
            "POST /predict",
            {
                "user_id": "dhawal@okicici",
                "receiver": "8890032239@ptsbi",
                "amount": 2500.0,
                "time": 14,
                "fast_action": False,
            },
        ),
        (
            "MUST NOT FLAG Even Under High Amount / Late Night: 8890032239@ptsbi",
            "POST /predict",
            {
                "user_id": "new_user@okaxis",
                "receiver": "8890032239@ptsbi",
                "amount": 50000.0,
                "time": 2,
                "fast_action": True,
            },
        ),
        (
            "MUST FLAG AS FRAUD (Blacklisted Test Case #1): reyshastri-8@oksbi",
            "POST /predict",
            {
                "user_id": "dhawal@okicici",
                "receiver": "reyshastri-8@oksbi",
                "amount": 3200.0,
                "time": 14,
                "fast_action": False,
            },
        ),
        (
            "MUST FLAG AS FRAUD (Blacklisted Test Case #2): sayasha36@oksbi",
            "POST /predict",
            {
                "user_id": "dhawal@okicici",
                "receiver": "sayasha36@oksbi",
                "amount": 4800.0,
                "time": 15,
                "fast_action": False,
            },
        ),
        (
            "Android Gateway Endpoint (/prevention/check) — Safe: 8890032239@ptsbi",
            "POST /prevention/check",
            {
                "check_id": "chk-safe-01",
                "receiver_vpa": "8890032239@ptsbi",
                "receiver_name": "Verified Trusted Account",
                "amount": 1500.0,
                "source_app": "com.google.android.apps.nbu.paisa.user",
            },
        ),
        (
            "Android Gateway Endpoint (/prevention/check) — Flagged: reyshastri-8@oksbi",
            "POST /prevention/check",
            {
                "check_id": "chk-fraud-01",
                "receiver_vpa": "reyshastri-8@oksbi",
                "receiver_name": "Rey Shastri",
                "amount": 3200.0,
                "source_app": "com.google.android.apps.nbu.paisa.user",
            },
        ),
        (
            "Android Gateway Endpoint (/prevention/check) — Flagged: sayasha36@oksbi",
            "POST /prevention/check",
            {
                "check_id": "chk-fraud-02",
                "receiver_vpa": "sayasha36@oksbi",
                "receiver_name": "Sayasha",
                "amount": 4800.0,
                "source_app": "com.phonepe.app",
            },
        ),
        (
            "Routine Known Contact Safe Transfer: mom@oksbi",
            "POST /predict",
            {
                "user_id": "dhawal@okicici",
                "receiver": "mom@oksbi",
                "amount": 500.0,
                "time": 14,
                "fast_action": False,
            },
        ),
        (
            "Known Blacklisted Cyber Scam Handle: cbi.digitalarrest@ybl",
            "POST /predict",
            {
                "user_id": "dhawal@okicici",
                "receiver": "cbi.digitalarrest@ybl",
                "amount": 15000.0,
                "time": 16,
                "fast_action": True,
            },
        ),
        (
            "Zero-Day Behavioral Anomaly (New Receiver + High Amount + Rushed + Active Call)",
            "POST /predict",
            {
                "user_id": "dhawal@okicici",
                "receiver": "unknown.person99@ybl",
                "amount": 8500.0,
                "time": 23,
                "fast_action": True,
                "active_phone_call": True,
            },
        ),
    ]

    for i, (title, ep, body) in enumerate(test_cases, 1):
        path = ep.split(" ")[1]
        resp = client.post(path, json=body).json()
        print_case(i, title, ep, body, resp)

    # Also show Ledger test cases
    ledger_resp = client.get("/ledger?limit=10").json()
    print(f"\n{'=' * 78}")
    print("NPCI LEDGER TEST CASES (GET /ledger):")
    print(json.dumps(ledger_resp, indent=2))


if __name__ == "__main__":
    main()
