from datetime import date, datetime
from zoneinfo import ZoneInfo
from fastapi.testclient import TestClient

from app.main import app
from app.services.pdf import build_letter_pdf, build_ombudsman_pack_pdf
from app.services.store import store

client = TestClient(app)
IST = ZoneInfo("Asia/Kolkata")


def test_founder_case_full_acceptance_flow():
    """Validates the complete founder case flow specified in Section 6."""
    # 1. POST /cases with a UPI transaction dated 2026-10-12, rule_id: upi_p2p_not_credited, amount 1000.
    res_create = client.post(
        "/cases",
        json={
            "transaction": {
                "utr": "123456789012",
                "amount": 1000.0,
                "bank": "HDFC Bank",
                "txn_datetime": "2026-10-12T10:00:00+05:30",
                "rule_id": "upi_p2p_not_credited",
                "role": "sender",
            },
            "complainant": {
                "name": "Ansh Sharma",
                "phone": "+91 9876543210",
                "email": "ansh@example.com",
                "address": "Mumbai, India",
            },
        },
    )
    assert res_create.status_code == 200
    data = res_create.json()
    case = data["case"]
    case_id = case["id"] if isinstance(case, dict) else case.id
    txn = case["transaction"] if isinstance(case, dict) else case.transaction
    amt = txn["amount"] if isinstance(txn, dict) else txn.amount
    rule_id = txn["rule_id"] if isinstance(txn, dict) else txn.rule_id
    assert amt == 1000.0
    assert rule_id == "upi_p2p_not_credited"

    # 2. PATCH /cases/{id} with reversal_date: 2026-10-21
    res_patch = client.patch(
        f"/cases/{case_id}",
        json={"reversal_date": "2026-10-21"},
    )
    assert res_patch.status_code == 200
    patched_case = res_patch.json()
    rev_date = patched_case["reversal_date"] if isinstance(patched_case, dict) else patched_case.reversal_date
    assert rev_date == "2026-10-21"

    # 3. POST /cases/{id}/evaluate returns delay_days: 8, compensation_amount: 800, status: reversed_late
    res_eval = client.post(f"/cases/{case_id}/evaluate")
    assert res_eval.status_code == 200
    evaluation = res_eval.json()
    assert evaluation["delay_days"] == 8
    assert evaluation["compensation_amount"] == 800
    assert evaluation["status"] == "reversed_late"
    assert evaluation["due_date"] == "2026-10-13"

    # 4. POST /cases/{id}/events with bank_complaint_sent on 2026-11-03, then GET /cases/{id}/escalation shows ombudsman_unlocks_on: 2026-12-03
    res_event = client.post(
        f"/cases/{case_id}/events",
        json={
            "type": "bank_complaint_sent",
            "at": "2026-11-03T10:00:00+05:30",
            "reference": "HDFC-COMP-001",
        },
    )
    assert res_event.status_code == 200

    res_esc = client.get(f"/cases/{case_id}/escalation")
    assert res_esc.status_code == 200
    escalation = res_esc.json()
    assert escalation["ombudsman_unlocks_on"] == "2026-12-03"

    # 5. build_letter_pdf and build_ombudsman_pack_pdf produce PDFs that open and show the ₹ symbol
    stored_case = store.get(case_id)
    from app.routers.cases import get_rule_by_id
    from app.services.deadline import evaluate_case
    from app.services.escalation import build_escalation

    r_id = getattr(getattr(stored_case, "transaction", None), "rule_id", None) or stored_case["transaction"]["rule_id"]
    rule_obj = get_rule_by_id(r_id)
    eval_obj = evaluate_case(stored_case, rule_obj, today=date(2026, 10, 21))
    esc_obj = build_escalation(stored_case, today=date(2026, 11, 3))

    letter_pdf = build_letter_pdf(
        stored_case,
        eval_obj,
        "Formal grievance regarding delayed reversal.",
        {"name": "HDFC Bank", "contact_status": "unverified"},
    )
    ombudsman_pdf = build_ombudsman_pack_pdf(
        stored_case,
        eval_obj,
        esc_obj,
        {"name": "HDFC Bank"},
    )

    assert len(letter_pdf) > 1000
    assert len(ombudsman_pdf) > 1000
    # Both PDFs embed DejaVu font which draws ₹
    assert b"DejaVu" in letter_pdf
    assert b"DejaVu" in ombudsman_pdf
