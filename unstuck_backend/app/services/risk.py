import re
from datetime import datetime
from typing import Dict, List, Optional, Tuple, Any

def score_claim_risk(
    transaction: Dict[str, Any],
    existing_references: Optional[List[str]] = None,
    sms_data: Optional[Dict[str, Any]] = None
) -> Tuple[float, List[str]]:
    """
    Scores doubtful claims:
    - reference format
    - plausible date
    - duplicates
    - consistency across evidence
    
    IMPORTANT: Never outputs or says "verified".
    Risk score: 0.0 (low risk) to 1.0 (critical risk).
    """
    score = 0.05  # Base baseline
    factors: List[str] = []

    reference = str(transaction.get("reference", "")).strip()
    rail = str(transaction.get("rail", "UPI")).upper()
    amount = float(transaction.get("amount", 0.0) or 0.0)
    date_val = str(transaction.get("date", "")).strip()

    # 1. Reference format check
    if not reference:
        score += 0.40
        factors.append("Missing transaction reference or UTR.")
    elif "UPI" in rail:
        # Standard UPI UTR is 12 numeric digits
        if re.match(r'^\d{12}$', reference):
            factors.append("Standard 12-digit UPI UTR format detected.")
        else:
            score += 0.25
            factors.append(f"Non-standard UPI reference format ('{reference}'): expected 12 digits.")
    elif "IMPS" in rail:
        if re.match(r'^\d{12}$', reference):
            factors.append("Standard 12-digit IMPS RRN format detected.")
        else:
            score += 0.20
            factors.append(f"Non-standard IMPS reference format ('{reference}').")
    else:
        if len(reference) < 6:
            score += 0.25
            factors.append(f"Reference number is unusually short ({len(reference)} characters).")
        else:
            factors.append("Standard reference length observed.")

    # 2. Plausible date check
    if not date_val:
        score += 0.30
        factors.append("Missing transaction date.")
    else:
        try:
            from app.services.deadline import parse_iso_or_date
            tx_dt = parse_iso_or_date(date_val)
            now = datetime.now()
            
            # Future date check
            if tx_dt.date() > now.date():
                score += 0.50
                factors.append(f"Transaction date is in the future: {tx_dt.strftime('%Y-%m-%d')}.")
            else:
                days_ago = (now.date() - tx_dt.date()).days
                if days_ago > 365 * 2:
                    score += 0.30
                    factors.append(f"Transaction occurred over 2 years ago ({days_ago} days old).")
                elif days_ago > 90:
                    score += 0.10
                    factors.append(f"Transaction is older than 90 days ({days_ago} days old).")
                else:
                    factors.append("Transaction date is within normal claim submission window.")
        except Exception:
            score += 0.20
            factors.append(f"Unable to parse transaction date '{date_val}'.")

    # 3. Amount sanity check
    if amount <= 0:
        score += 0.40
        factors.append("Transaction amount must be greater than zero.")
    elif amount > 500000:
        score += 0.15
        factors.append(f"High-value transaction amount (Rs. {amount:,.2f}) requires enhanced scrutiny.")

    # 4. Duplicates check
    if existing_references and reference:
        if reference in existing_references:
            score += 0.45
            factors.append(f"Potential duplicate claim detected: Reference '{reference}' already exists in records.")

    # 5. Consistency across evidence (SMS vs Screenshot)
    if sms_data:
        sms_amt = float(sms_data.get("amount", 0.0) or 0.0)
        sms_ref = str(sms_data.get("reference", "")).strip()
        
        if sms_amt > 0 and abs(sms_amt - amount) > 0.01:
            score += 0.35
            factors.append(f"Discrepancy: Amount in SMS (Rs. {sms_amt}) differs from screenshot (Rs. {amount}).")
        
        if sms_ref and reference and sms_ref != reference:
            score += 0.35
            factors.append(f"Discrepancy: Reference in SMS ('{sms_ref}') differs from screenshot ('{reference}').")

    # Clamp score between 0.0 and 1.0
    final_score = min(1.0, max(0.0, round(score, 2)))
    return final_score, factors
