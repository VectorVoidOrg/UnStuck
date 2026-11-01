from app.services.extractor import extract_fields, normalize_amount, normalize_date

SAMPLE_UPI_OCR = """
Payment to Sharma Stores
INR 1,500.00
Status: Payment Failed
Money debited from your HDFC Bank A/c XX1234
UPI Transaction ID: 428912345678
Date: 2026-10-15 14:32:10
If money was deducted, it will be refunded by your bank.
"""

SAMPLE_BANK_SMS = """
Dear Customer, your A/c XX4321 is debited for Rs 3,250.00 on 2026-10-18 10:15:00 
towards UPI/428987654321/Merchant. If not done by you, contact SBI.
"""

def test_normalize_amount():
    assert normalize_amount("1,500.00") == 1500.0
    assert normalize_amount("Rs. 250") == 250.0
    assert normalize_amount("₹ 10,999.50") == 10999.50
    assert normalize_amount("INR 500") == 500.0
    assert normalize_amount(1200) == 1200.0

def test_normalize_date():
    iso_date = normalize_date("2026-10-15 14:32:10 IST")
    assert "2026-10-15" in iso_date
    
    slash_date = normalize_date("15/10/2026 14:32:10")
    assert "2026-10-15" in slash_date

def test_extractor_sample_ocr():
    data = extract_fields(SAMPLE_UPI_OCR)
    assert data["amount"] == 1500.0
    assert data["reference"] == "428912345678"
    assert "HDFC" in data["bank"]
    assert "2026-10-15" in data["date"]
    assert data["status"] in ("FAILED", "DEBITED")
    assert data["rail"] == "UPI"

def test_extractor_sample_sms():
    data = extract_fields(ocr_text="", sms_text=SAMPLE_BANK_SMS)
    assert data["amount"] == 3250.0
    assert data["reference"] == "428987654321"
    assert "State Bank" in data["bank"] or "SBI" in data["bank"]
    assert "2026-10-18" in data["date"]

