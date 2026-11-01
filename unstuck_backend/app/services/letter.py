import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Dict, Any, Tuple
from app.config import settings
from app.schemas import Case, LetterResponse
from app.services.validator import validate_letter

logger = logging.getLogger(__name__)

def load_template(level: str) -> str:
    templates_dir = settings.TEMPLATES_DIR
    level_map = {
        "bank_complaint": templates_dir / "bank_complaint.txt",
        "nodal_officer": templates_dir / "nodal_officer.txt",
        "ombudsman": templates_dir / "ombudsman.txt",
    }
    target = level_map.get(level, templates_dir / "bank_complaint.txt")
    if target.exists():
        with open(target, "r", encoding="utf-8") as f:
            return f.read()
    raise FileNotFoundError(f"Template not found at {target}")

def get_bank_info(bank_name: str) -> Dict[str, str]:
    if settings.BANKS_FILE_PATH.exists():
        try:
            with open(settings.BANKS_FILE_PATH, "r", encoding="utf-8") as f:
                banks = json.load(f)
                b_clean = bank_name.lower().strip()
                for b in banks:
                    if b["name"].lower() == b_clean or any(alias in b_clean for alias in b.get("aliases", [])):
                        return b
        except Exception as e:
            logger.warning(f"Error loading bank info: {e}")
            
    return {
        "name": bank_name or "HDFC Bank",
        "nodal_officer_email": "nodal.officer@bank.com",
        "nodal_officer_name": f"Principal Nodal Officer, {bank_name}",
        "grievance_page_link": "https://www.bank.com/grievance",
        "customer_care_phone": "1800 000 0000"
    }

def fill_template(template_str: str, placeholders: Dict[str, Any]) -> str:
    res = template_str
    for k, v in placeholders.items():
        placeholder_key = "{{ " + k + " }}"
        alt_key = "{{" + k + "}}"
        res = res.replace(placeholder_key, str(v)).replace(alt_key, str(v))
    return res

def generate_letter(
    case: Case,
    level: str = "bank_complaint",
    custom_notes: str = ""
) -> LetterResponse:
    """
    Fills the fixed template from code, has Gemini polish the wording only,
    runs the validator, and falls back to the plain template on failure.
    """
    tx = case.transaction
    eval_res = case.evaluation
    bank_info = get_bank_info(tx.bank)

    # Prepare data values
    amt = tx.amount
    days_overdue = eval_res.days_late if eval_res else 0
    comp_amt = eval_res.penalty_amount if eval_res else (days_overdue * 100.0)
    tat_days = eval_res.tat_days if eval_res else 1
    deadline_date = eval_res.deadline_date.strftime("%Y-%m-%d") if eval_res else "T+1"

    placeholders = {
        "bank_name": tx.bank or bank_info["name"],
        "nodal_officer_email": bank_info.get("nodal_officer_email", "nodal.officer@bank.com"),
        "customer_name": tx.customer_name or "Valued Customer",
        "customer_phone": tx.customer_phone or "9876543210",
        "customer_email": tx.customer_email or "customer@example.com",
        "account_number": tx.account_number or "XX1234",
        "transaction_date": tx.date,
        "amount": f"{amt:,.2f}",
        "reference": tx.reference,
        "rail": tx.rail or "UPI",
        "failure_reason": "Account debited but beneficiary not credited / reversal delayed",
        "tat_deadline": f"T + {tat_days} calendar day(s)",
        "deadline_date": deadline_date,
        "days_overdue": str(days_overdue),
        "compensation_amount": f"{comp_amt:,.2f}",
        "ticket_number": case.ticket_number or "UNSTUCK-GRV-88219",
        "escalation_date": datetime.now().strftime("%Y-%m-%d"),
        "letter_date": datetime.now().strftime("%d %B %Y"),
    }

    # 1. Plain filled template (Deterministic baseline)
    template_raw = load_template(level)
    plain_letter = fill_template(template_raw, placeholders)

    candidate_letter = plain_letter
    generated_by = "template"
    warnings = []

    # 2. Try Gemini polishing if key is provided
    if settings.GEMINI_API_KEY:
        try:
            prompt = (
                "You are an expert legal grievance editor in India. "
                "Polish the tone of the following bank grievance letter to be crisp, authoritative, and polite.\n\n"
                "CRITICAL NON-NEGOTIABLE RULE:\n"
                "You MUST NOT change, alter, omit, or invent ANY numbers, dates, references, or currency amounts.\n"
                f"Keep reference '{tx.reference}', amount '{amt:,.2f}', dates, and compensation numbers EXACTLY as written.\n\n"
                f"Letter to polish:\n{plain_letter}"
            )
            try:
                import google.generativeai as genai
                genai.configure(api_key=settings.GEMINI_API_KEY)
                model = genai.GenerativeModel(settings.MODEL_NAME)
                response = model.generate_content(prompt)
                polished_text = response.text.strip()
            except ImportError:
                import httpx
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.MODEL_NAME}:generateContent?key={settings.GEMINI_API_KEY}"
                r = httpx.post(url, json={"contents": [{"parts": [{"text": prompt}]}]}, timeout=10.0)
                r.raise_for_status()
                polished_text = r.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
            
            # 3. Run figure validator
            validator_data = {
                "amount": amt,
                "reference": tx.reference,
                "date": tx.date,
                "compensation_amount": comp_amt,
                "deadline_date": deadline_date,
                "ticket_number": placeholders["ticket_number"]
            }
            is_valid, issues = validate_letter(polished_text, validator_data)
            
            if is_valid:
                candidate_letter = polished_text
                generated_by = "gemini_polished"
            else:
                logger.warning(f"Validator rejected AI text: {issues}. Falling back to plain template.")
                warnings.extend([f"AI wording rejected by figure validator: {iss}" for iss in issues])
                candidate_letter = plain_letter
                generated_by = "template"
        except Exception as e:
            logger.warning(f"Gemini polish error: {e}. Falling back to template.")
            warnings.append(f"AI polishing error: {str(e)}")
            candidate_letter = plain_letter
            generated_by = "template"

    return LetterResponse(
        case_id=case.id,
        level=level,
        letter_text=candidate_letter,
        validated=True,
        generated_by=generated_by,
        recipient={
            "bank_name": placeholders["bank_name"],
            "email": bank_info.get("nodal_officer_email", "nodal.officer@bank.com"),
            "portal": bank_info.get("grievance_page_link", "")
        },
        warnings=warnings
    )
