from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "healthy"
    assert data["rules_loaded"] is True
    assert data["banks_loaded"] is True

def test_rules_and_banks():
    res_rules = client.get("/rules")
    assert res_rules.status_code == 200
    assert len(res_rules.json()) >= 6

    res_banks = client.get("/banks")
    assert res_banks.status_code == 200
    assert len(res_banks.json()) >= 6

def test_extract_and_case_lifecycle():
    # 1. Test Extract endpoint with SMS
    sms_text = "Debited INR 1500.00 from HDFC Bank A/c XX1234 on 2026-10-15. UPI Ref: 428912345678."
    ext_res = client.post("/extract", data={"sms_text": sms_text})
    assert ext_res.status_code == 200
    ext_data = ext_res.json()
    assert ext_data["transaction"]["amount"] == 1500.0
    assert ext_data["transaction"]["reference"] == "428912345678"

    # 2. Create Case
    case_payload = {
        "transaction": ext_data["transaction"],
        "rule_id": "upi_p2p_failed",
        "customer_role": "Remitter",
        "money_returned": False
    }
    create_res = client.post("/cases", json=case_payload)
    assert create_res.status_code == 201
    case = create_res.json()
    case_id = case["id"]
    assert case["id"] is not None
    assert case["evaluation"]["tat_days"] == 1

    # 3. Check Escalation status
    esc_res = client.get(f"/cases/{case_id}/escalation")
    assert esc_res.status_code == 200
    esc_data = esc_res.json()
    assert "steps" in esc_data
    assert esc_data["steps"]["bank_complaint"]["status"] in ("UNLOCKED", "COMPLETED")

    # 4. Generate Bank Complaint Letter
    letter_payload = {
        "case_id": case_id,
        "level": "bank_complaint"
    }
    let_res = client.post("/letters", json=letter_payload)
    assert let_res.status_code == 200
    let_data = let_res.json()
    assert "428912345678" in let_data["letter_text"]
    assert let_data["validated"] is True

    # 5. Download Letter PDF
    pdf_res = client.get(f"/letters/{case_id}/pdf")
    assert pdf_res.status_code == 200
    assert pdf_res.headers["content-type"] == "application/pdf"
    assert len(pdf_res.content) > 1000

    # 6. Download Ombudsman Pack PDF
    omb_res = client.get(f"/letters/{case_id}/ombudsman-pack")
    assert omb_res.status_code == 200
    assert omb_res.headers["content-type"] == "application/pdf"
    assert len(omb_res.content) > 1000

    # 7. Update Case (Reversal date)
    patch_res = client.patch(f"/cases/{case_id}", json={"money_returned": True, "reversal_date": "2026-10-24T12:00:00"})
    assert patch_res.status_code == 200
    patched_case = patch_res.json()
    assert patched_case["money_returned"] is True
    assert patched_case["evaluation"]["status"] == "REVERSED_LATE"

