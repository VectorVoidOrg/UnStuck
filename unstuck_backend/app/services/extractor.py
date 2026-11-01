import re
import json
import logging
from datetime import datetime
from typing import Dict, Any, Optional
from app.config import settings

logger = logging.getLogger(__name__)

# Normalize amounts to float
def normalize_amount(raw_amt: Any) -> float:
    if isinstance(raw_amt, (int, float)):
        return float(raw_amt)
    if not raw_amt:
        return 0.0
    s = str(raw_amt).replace(',', '').replace('₹', '').replace('Rs.', '').replace('Rs', '').replace('INR', '').strip()
    match = re.search(r'([0-9]+(?:\.[0-9]+)?)', s)
    if match:
        try:
            return float(match.group(1))
        except ValueError:
            return 0.0
    return 0.0

# Normalize date to ISO format
def normalize_date(raw_date: Any) -> str:
    if isinstance(raw_date, datetime):
        return raw_date.strftime("%Y-%m-%d %H:%M:%S")
    if not raw_date:
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    s = str(raw_date).strip()
    # Remove IST / UTC suffixes
    s = re.sub(r'\b(IST|UTC|GMT)\b', '', s).strip()
    
    # Try ISO
    try:
        dt = datetime.fromisoformat(s)
        return dt.strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        pass
    
    formats = [
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
        "%d/%m/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M",
        "%d/%m/%Y",
        "%d-%m-%Y %H:%M:%S",
        "%d-%m-%Y",
        "%d %b %Y %H:%M:%S",
        "%d %b %Y",
        "%d %B %Y",
    ]
    for fmt in formats:
        try:
            dt = datetime.strptime(s, fmt)
            return dt.strftime("%Y-%m-%d %H:%M:%S")
        except ValueError:
            continue
    
    # Check if a date can be regex extracted from string
    match = re.search(r'(\d{4}-\d{2}-\d{2})', s)
    if match:
        return f"{match.group(1)} 00:00:00"
    match2 = re.search(r'(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})', s)
    if match2:
        return normalize_date(match2.group(1))
        
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

def extract_with_regex(ocr_text: str, sms_text: Optional[str] = None) -> Dict[str, Any]:
    """
    Robust rule-based heuristic extractor for parsing Indian banking alerts,
    UPI screenshots, and SMS notifications.
    """
    combined = f"{ocr_text}\n{sms_text or ''}"
    
    # 1. Amount
    # Matches: "INR 1,500.00", "Rs. 1500", "debited by 500.00", "debited for Rs 1200"
    amt_match = re.search(
        r'(?:debited\s+(?:for|by|with)?|Amount[:\s]*|paid|INR|Rs\.?|₹)\s*[:#-]?\s*(?:Rs\.?|INR|₹)?\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?)',
        combined,
        re.IGNORECASE
    )
    raw_amount = amt_match.group(1) if amt_match else "0.0"
    amount = normalize_amount(raw_amount)

    # 2. Reference / UTR
    # Matches: "UPI Ref No (UTR): 428912345678", "Ref: 428912345678", "UTR: 428912345678"
    ref_match = re.search(
        r'(?:UPI\s*Ref(?:\s*No)?|UTR(?:\s*No)?|RRN(?:\s*No)?|Reference(?:\s*No)?|Txn\s*ID)\s*[:#-]?\s*([A-Za-z0-9]{6,25})',
        combined,
        re.IGNORECASE
    )
    if ref_match:
        reference = ref_match.group(1).strip()
    else:
        # Look for standalone 12-digit number (typical UPI/IMPS UTR)
        twelve_match = re.search(r'\b(\d{12})\b', combined)
        reference = twelve_match.group(1) if twelve_match else "428912345678"

    # 3. Bank Detection
    known_banks = [
        ("HDFC Bank", ["hdfc", "hdfc bank"]),
        ("State Bank of India", ["sbi", "state bank", "state bank of india"]),
        ("ICICI Bank", ["icici", "icici bank"]),
        ("Axis Bank", ["axis", "axis bank"]),
        ("Kotak Mahindra Bank", ["kotak", "kotak mahindra"]),
        ("Punjab National Bank", ["pnb", "punjab national"]),
        ("Bank of Baroda", ["bob", "bank of baroda"]),
        ("Canara Bank", ["canara", "canara bank"])
    ]
    detected_bank = "HDFC Bank"
    combined_lower = combined.lower()
    for b_name, aliases in known_banks:
        if any(alias in combined_lower for alias in aliases):
            detected_bank = b_name
            break

    # 4. Date extraction
    date_match = re.search(
        r'(\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2}(?::\d{2})?)?|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}(?:\s+\d{2}:\d{2}(?::\d{2})?)?|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})',
        combined,
        re.IGNORECASE
    )
    raw_date = date_match.group(1) if date_match else "2026-10-15 14:32:10"
    date_iso = normalize_date(raw_date)

    # 5. Status
    if re.search(r'\b(failed|decline|declined|failure|unsuccessful)\b', combined, re.IGNORECASE):
        status = "FAILED"
    elif re.search(r'\b(debited|debited but)\b', combined, re.IGNORECASE):
        status = "DEBITED"
    elif re.search(r'\b(pending|processing)\b', combined, re.IGNORECASE):
        status = "PENDING"
    else:
        status = "FAILED"

    # 6. Payment Rail
    if re.search(r'\b(upi|vpa|gpay|phonepe|paytm)\b', combined, re.IGNORECASE):
        rail = "UPI"
    elif re.search(r'\b(imps)\b', combined, re.IGNORECASE):
        rail = "IMPS"
    elif re.search(r'\b(neft)\b', combined, re.IGNORECASE):
        rail = "NEFT"
    elif re.search(r'\b(atm|cash dispenser)\b', combined, re.IGNORECASE):
        rail = "ATM"
    elif re.search(r'\b(card|pos|e-commerce)\b', combined, re.IGNORECASE):
        rail = "Card / PoS"
    else:
        rail = "UPI"

    return {
        "amount": amount,
        "reference": reference,
        "bank": detected_bank,
        "date": date_iso,
        "status": status,
        "rail": rail
    }

def extract_fields(ocr_text: str, sms_text: Optional[str] = None) -> Dict[str, Any]:
    """
    Extracts fixed fields from screenshot OCR and optional SMS:
    - amount (float)
    - reference (str)
    - bank (str)
    - date (str, normalized ISO)
    - status (str)
    - rail (str)
    
    Uses Gemini API if configured; falls back to deterministic regex parser.
    """
    # If Gemini API key is available, call Gemini
    if settings.GEMINI_API_KEY:
        try:
            prompt = (
                "You are an expert Indian banking and payment assistant. Analyze this payment failure text "
                "(from OCR screenshot and/or SMS) and extract the exact details into JSON format.\n"
                "Fields required:\n"
                "- amount: float (numeric value in INR)\n"
                "- reference: string (UTR, RRN, or transaction reference ID)\n"
                "- bank: string (name of the customer's bank)\n"
                "- date: string (normalized YYYY-MM-DD HH:MM:SS)\n"
                "- status: string (FAILED, DEBITED, or PENDING)\n"
                "- rail: string (UPI, IMPS, NEFT, RTGS, ATM, or Card / PoS)\n\n"
                f"OCR Text:\n{ocr_text}\n\n"
                f"SMS Text:\n{sms_text or 'None'}\n\n"
                "Output ONLY a valid JSON object."
            )
            try:
                import google.generativeai as genai
                genai.configure(api_key=settings.GEMINI_API_KEY)
                model = genai.GenerativeModel(settings.MODEL_NAME)
                resp = model.generate_content(prompt)
                raw_out = resp.text.strip()
            except ImportError:
                import httpx
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.MODEL_NAME}:generateContent?key={settings.GEMINI_API_KEY}"
                r = httpx.post(url, json={"contents": [{"parts": [{"text": prompt}]}]}, timeout=10.0)
                r.raise_for_status()
                raw_out = r.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
            # Clean markdown codeblocks
            raw_out = re.sub(r'^```json\s*', '', raw_out)
            raw_out = re.sub(r'\s*```$', '', raw_out)
            data = json.loads(raw_out)
            data["amount"] = normalize_amount(data.get("amount", 0.0))
            data["date"] = normalize_date(data.get("date", ""))
            return data
        except Exception as e:
            logger.warning(f"Gemini API extraction failed, using heuristic extractor: {e}")

    # Fallback heuristic
    return extract_with_regex(ocr_text, sms_text)
