import re
from datetime import datetime
from typing import Dict, List, Tuple, Any

def extract_currency_amounts(text: str) -> List[float]:
    """
    Extracts currency values from text like Rs. 1500, Rs. 1,500.00, ₹1500, etc.
    """
    # Pattern for Rs. / ₹ / INR followed by digits with commas and optional decimals
    pattern = r'(?:Rs\.?|INR|₹)\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?)'
    matches = re.findall(pattern, text, flags=re.IGNORECASE)
    amounts = []
    for m in matches:
        clean = m.replace(',', '').strip()
        try:
            amounts.append(float(clean))
        except ValueError:
            pass
    return amounts

def extract_potential_references(text: str) -> List[str]:
    """
    Finds numeric or alphanumeric reference / UTR codes mentioned in the letter.
    """
    # Matches patterns like Ref: 123456789012, UTR: 123456789012, RRN: ...
    ref_pattern = r'(?:Ref(?:erence)?(?:\s*No\.?|\s*Number)?|UTR(?:\s*No\.?)?|RRN(?:\s*No\.?)?)\s*[:#-]?\s*([A-Za-z0-9_-]{6,25})'
    found = re.findall(ref_pattern, text, flags=re.IGNORECASE)
    return [f.strip() for f in found if f.strip()]

def extract_dates(text: str) -> List[str]:
    """
    Extracts date strings like YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY, or DD Month YYYY.
    """
    iso_pattern = r'\b(\d{4}-\d{2}-\d{2})\b'
    dmy_pattern = r'\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b'
    named_pattern = r'\b(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})\b'
    
    dates = []
    dates.extend(re.findall(iso_pattern, text))
    dates.extend(re.findall(dmy_pattern, text))
    dates.extend(re.findall(named_pattern, text, flags=re.IGNORECASE))
    return dates

def normalize_date_to_day(d_str: str) -> str:
    """
    Normalizes a date string into YYYY-MM-DD for comparison.
    """
    clean = d_str.strip().replace('/', '-')
    for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d-%m-%y", "%d %b %Y", "%d %B %Y"):
        try:
            dt = datetime.strptime(clean, fmt)
            return dt.strftime("%Y-%m-%d")
        except ValueError:
            continue
    # Try ISO
    try:
        return datetime.fromisoformat(clean).strftime("%Y-%m-%d")
    except Exception:
        return clean

def validate_letter(text: str, case_data: Dict[str, Any]) -> Tuple[bool, List[str]]:
    """
    Pulls every number, date and reference out of the AI text and rejects it
    if any differs from the case data.
    
    case_data can contain:
    - amount: float
    - reference: str
    - date: str (transaction date)
    - deadline_date: Optional[str]
    - compensation_amount: Optional[float]
    - days_overdue: Optional[int]
    - tat_days: Optional[int]
    - penalty_rate: Optional[float] (defaults to 100.0)
    """
    issues = []
    
    expected_amount = float(case_data.get("amount", 0.0))
    expected_reference = str(case_data.get("reference", "")).strip()
    raw_date = str(case_data.get("date", "")).strip()
    expected_tx_date = normalize_date_to_day(raw_date) if raw_date else ""
    
    # 1. Reference check
    if expected_reference:
        if expected_reference not in text:
            issues.append(f"Required reference '{expected_reference}' was not found in letter text.")
        
        # Check if an altered reference was introduced next to Ref/UTR labels
        found_refs = extract_potential_references(text)
        for f_ref in found_refs:
            # If the label states a reference but it doesn't match expected_reference (or ticket_number if provided)
            ticket_no = str(case_data.get("ticket_number", "")).strip()
            if f_ref != expected_reference and (not ticket_no or f_ref != ticket_no):
                issues.append(f"Altered or unknown reference found in text: '{f_ref}' (expected '{expected_reference}').")

    # 2. Amount / Currency numbers check
    allowed_amounts = {
        expected_amount,
        100.0, # RBI standard penalty rate per day
        0.0
    }
    if "compensation_amount" in case_data and case_data["compensation_amount"] is not None:
        allowed_amounts.add(float(case_data["compensation_amount"]))
    if "total_claim" in case_data and case_data["total_claim"] is not None:
        allowed_amounts.add(float(case_data["total_claim"]))
    else:
        # Sum of amount + compensation
        comp = float(case_data.get("compensation_amount", 0.0) or 0.0)
        allowed_amounts.add(expected_amount + comp)
    
    found_amounts = extract_currency_amounts(text)
    for amt in found_amounts:
        # Check against allowed amounts with tolerance of 0.01
        is_allowed = any(abs(amt - allowed) < 0.01 for allowed in allowed_amounts)
        if not is_allowed:
            issues.append(f"Altered or unauthorized currency amount found: Rs. {amt} (allowed: {sorted(list(allowed_amounts))}).")

    # 3. Date check
    # Check if transaction date is present
    if expected_tx_date:
        # Check if the transaction date or some normalized form is present
        # If dates are in the text, ensure none are marked as transaction date falsely
        found_dates = extract_dates(text)
        normalized_found = [normalize_date_to_day(d) for d in found_dates]
        
        # Check for explicit transaction date mention
        tx_date_mentions = re.findall(
            r'(?:Transaction Date|Date of Transaction|Debited on|on)\s*[:#-]?\s*([0-9A-Za-z\s/-]+)',
            text,
            flags=re.IGNORECASE
        )
        for mention in tx_date_mentions:
            for d in extract_dates(mention[:30]):
                norm = normalize_date_to_day(d)
                if norm and norm != expected_tx_date:
                    # Allow if it matches deadline_date or today's date
                    deadline_raw = str(case_data.get("deadline_date", ""))
                    norm_deadline = normalize_date_to_day(deadline_raw) if deadline_raw else ""
                    today_norm = datetime.now().strftime("%Y-%m-%d")
                    if norm not in (expected_tx_date, norm_deadline, today_norm):
                        issues.append(f"Altered transaction date found: '{d}' -> normalized '{norm}' (expected '{expected_tx_date}').")

    is_valid = len(issues) == 0
    return is_valid, issues
