from app.services.validator import validate_letter

BASE_CASE_DATA = {
    "amount": 1500.0,
    "reference": "428912345678",
    "date": "2026-10-15",
    "deadline_date": "2026-10-16",
    "compensation_amount": 800.0,
    "days_overdue": 8,
    "ticket_number": "UNSTUCK-GRV-88219"
}

VALID_LETTER = """
Dear Branch Manager,
I am writing regarding my failed UPI transaction of Rs. 1,500.00 debited on 2026-10-15.
Reference No: 428912345678.
Under RBI circular DPSS.CO.PD No.629/02.01.014/2019-20, TAT expired on 2026-10-16.
The statutory penalty accrued is Rs. 800.00 at Rs. 100 per day.
Please credit Rs. 1,500.00 immediately.
"""

def test_valid_letter_passes():
    is_valid, issues = validate_letter(VALID_LETTER, BASE_CASE_DATA)
    assert is_valid is True
    assert len(issues) == 0

def test_altered_amount_is_rejected():
    # An altered amount (Rs. 4,500 instead of Rs. 1,500) must be rejected
    altered_text = VALID_LETTER.replace("Rs. 1,500.00", "Rs. 4,500.00")
    is_valid, issues = validate_letter(altered_text, BASE_CASE_DATA)
    assert is_valid is False
    assert any("Altered or unauthorized currency amount" in iss for iss in issues)

def test_altered_reference_is_rejected():
    # An altered reference number (999988887777 instead of 428912345678) must be rejected
    altered_text = VALID_LETTER.replace("428912345678", "999988887777")
    is_valid, issues = validate_letter(altered_text, BASE_CASE_DATA)
    assert is_valid is False
    assert any("reference" in iss.lower() for iss in issues)

def test_altered_date_is_rejected():
    # An altered transaction date (2026-10-25 instead of 2026-10-15) must be rejected
    altered_text = VALID_LETTER.replace("2026-10-15", "2026-10-25")
    is_valid, issues = validate_letter(altered_text, BASE_CASE_DATA)
    assert is_valid is False
    assert any("date" in iss.lower() for iss in issues)

