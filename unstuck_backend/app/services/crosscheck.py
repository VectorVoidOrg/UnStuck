import re
from typing import Dict, List, Tuple, Any

def normalize_digits(s: str) -> str:
    return re.sub(r'\D', '', s)

def crosscheck_extraction(
    extracted_data: Dict[str, Any],
    raw_vision_text: str
) -> Tuple[str, float, List[str], Dict[str, float]]:
    """
    Compares Gemini's reference and amount with the Vision text.
    A match means high confidence, and a mismatch is flagged for the user.
    
    Returns:
    (crosscheck_status, overall_confidence, flags, confidence_breakdown)
    """
    flags: List[str] = []
    breakdown: Dict[str, float] = {
        "amount": 0.5,
        "reference": 0.5,
        "bank": 0.5,
        "date": 0.5
    }

    if not raw_vision_text:
        return "UNVERIFIED", 0.60, ["Raw OCR text unavailable for verification."], breakdown

    text_clean = raw_vision_text.lower()
    text_digits = normalize_digits(raw_vision_text)

    # 1. Amount verification
    amount = float(extracted_data.get("amount", 0.0) or 0.0)
    amount_matched = False
    if amount > 0:
        amt_str = f"{amount:.2f}"
        amt_int = str(int(amount))
        amt_comma = f"{amount:,.2f}"
        amt_comma_no_dec = f"{int(amount):,}"

        if (amt_int in text_clean or amt_str in text_clean or 
            amt_comma in text_clean or amt_comma_no_dec in text_clean):
            amount_matched = True
            breakdown["amount"] = 0.95
        else:
            breakdown["amount"] = 0.40
            flags.append(f"Amount Rs. {amount} was not clearly recognized in the raw screenshot text.")
    else:
        flags.append("No valid amount detected.")
        breakdown["amount"] = 0.20

    # 2. Reference / UTR verification
    reference = str(extracted_data.get("reference", "")).strip()
    ref_matched = False
    if reference:
        ref_digits = normalize_digits(reference)
        if reference.lower() in text_clean or (len(ref_digits) >= 8 and ref_digits in text_digits):
            ref_matched = True
            breakdown["reference"] = 0.98
        else:
            breakdown["reference"] = 0.35
            flags.append(f"Reference '{reference}' could not be matched directly with the screenshot OCR text.")
    else:
        flags.append("Transaction reference / UTR is missing.")
        breakdown["reference"] = 0.20

    # 3. Bank name verification
    bank = str(extracted_data.get("bank", "")).strip().lower()
    if bank and (bank in text_clean or any(word in text_clean for word in bank.split() if len(word) > 3)):
        breakdown["bank"] = 0.90
    else:
        breakdown["bank"] = 0.50

    # 4. Overall status and score
    if amount_matched and ref_matched:
        status = "MATCH"
        confidence = 0.95
    elif amount_matched or ref_matched:
        status = "PARTIAL_MATCH"
        confidence = 0.70
        flags.append("Partial match: please verify the highlighted transaction details before proceeding.")
    else:
        status = "MISMATCH"
        confidence = 0.40
        flags.append("Discrepancy detected between AI extraction and raw screenshot OCR text.")

    return status, confidence, flags, breakdown

