import re
from dataclasses import dataclass, field
from typing import Dict, List
from app.layer2_privacy.pii_vault import pii_vault


@dataclass
class OCRSanitizationResult:
    sanitized_text: str
    pii_tokens_map: Dict[str, str] = field(default_factory=dict)
    prompt_injection_detected: bool = False
    injection_patterns_found: List[str] = field(default_factory=list)


# Resilience #1 (Slide 10 & 11): Defend against Prompt Injection hidden in fake screenshot OCR
PROMPT_INJECTION_PATTERNS: List[re.Pattern] = [
    re.compile(r"ignore\s+(?:all\s+)?(?:previous|prior|above)\s+instructions", re.IGNORECASE),
    re.compile(r"system\s*override", re.IGNORECASE),
    re.compile(r"you\s+are\s+now\s+(?:in\s+)?developer\s+mode", re.IGNORECASE),
    re.compile(r"approve\s+(?:instant\s+)?auto[\-\s]?refund", re.IGNORECASE),
    re.compile(r"set\s+confidence\s*(?:to|=)\s*1\.0", re.IGNORECASE),
    re.compile(r"disregard\s+safety\s+guardrails", re.IGNORECASE),
    re.compile(r"<\s*system\s*>|<\s*/\s*system\s*>|\[INST\]", re.IGNORECASE),
]


def sanitize_and_mask_ocr(
    ocr_text: str, client_side_masked: bool = False
) -> OCRSanitizationResult:
    """
    Step 5B Resilience #1: Local On-Device / Edge OCR Masking & Prompt Injection Sanitizer.
    1. Detects adversarial prompt-injection strings embedded inside fake payment receipts.
    2. Strips/neutralizes the adversarial instructions so they cannot hijack the Agent Core.
    3. Scrubs any remaining unmasked Phone, Bank A/C, Aadhaar, PAN, or UPI VPA via PIITokenVault.
    """
    if not ocr_text:
        return OCRSanitizationResult(sanitized_text="")

    working_text = ocr_text
    injections_found: List[str] = []

    for pattern in PROMPT_INJECTION_PATTERNS:
        matches = pattern.findall(working_text)
        if matches:
            injections_found.extend(matches)
            working_text = pattern.sub("[REDACTED_PROMPT_INJECTION_ATTEMPT]", working_text)

    # Even if client_side_masked=True, run edge verification pass to guarantee zero PII leak
    redacted_text, token_map = pii_vault.redact_text(working_text)

    return OCRSanitizationResult(
        sanitized_text=redacted_text,
        pii_tokens_map=token_map,
        prompt_injection_detected=len(injections_found) > 0,
        injection_patterns_found=injections_found,
    )
