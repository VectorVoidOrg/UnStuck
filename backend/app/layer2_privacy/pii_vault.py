import hashlib
import hmac
import re
from datetime import datetime, timezone
from typing import Dict, List, Tuple
from app.config import settings
from app.db.mongo import pii_vault_collection


class PIITokenVault:
    """
    Component 2: PII Redaction & Token Vault (RBI Data Privacy Compliant).
    Strips and tokenizes sensitive Indian financial & identity PII at the edge
    BEFORE any SLM, Frontier LLM, or external log sees the text:
      - Aadhaar Numbers (12 digits, formatted or unformatted)
      - Indian Mobile Phone Numbers (+91 / 10-digit starting 6-9)
      - Bank Account Numbers (9 to 18 digits)
      - PAN Card Numbers (5 letters + 4 digits + 1 letter)
      - IFSC Codes (4 letters + 0 + 6 alphanumeric)
      - UPI IDs / VPAs (username@bankhandle)
    Stores reversible HMAC tokens in `pii_token_vault` so authorized Layer 4 Bank Core Tools
    can detokenize when executing a verified NPCI lien or refund.
    """

    # Ordered from most specific to general numeric patterns
    PATTERNS: List[Tuple[str, re.Pattern]] = [
        ("UPI_VPA", re.compile(r"\b[a-zA-Z0-9.\-_]{2,40}@[a-zA-Z]{2,20}\b")),
        ("PAN", re.compile(r"\b[A-Z]{5}[0-9]{4}[A-Z]\b")),
        ("IFSC", re.compile(r"\b[A-Z]{4}0[A-Z0-9]{6}\b")),
        ("AADHAAR", re.compile(r"\b(?:[2-9][0-9]{3}[\s\-]?[0-9]{4}[\s\-]?[0-9]{4})\b")),
        ("PHONE", re.compile(r"(?:\+91[\-\s]?)?\b[6-9][0-9]{9}\b")),
        ("BANK_AC", re.compile(r"\b[0-9]{9,18}\b")),
    ]

    def __init__(self, secret_key: str = settings.PII_VAULT_SECRET_KEY):
        self._secret = secret_key.encode("utf-8")
        self._memory_vault: Dict[str, str] = {}

    def _make_token(self, pii_type: str, raw_value: str) -> str:
        normalized = raw_value.strip()
        digest = hmac.new(self._secret, f"{pii_type}:{normalized}".encode("utf-8"), hashlib.sha256).hexdigest()[:10]
        token = f"<{pii_type}_TOKEN_{digest.upper()}>"

        if token not in self._memory_vault:
            self._memory_vault[token] = normalized
            pii_vault_collection.update_one(
                {"token": token},
                {
                    "$set": {
                        "token": token,
                        "pii_type": pii_type,
                        "masked_preview": self._mask_preview(pii_type, normalized),
                        "created_at": datetime.now(timezone.utc).isoformat(),
                    }
                },
                upsert=True,
            )
        return token

    @staticmethod
    def _mask_preview(pii_type: str, val: str) -> str:
        if len(val) <= 4:
            return "****"
        if pii_type == "UPI_VPA" and "@" in val:
            user, handle = val.split("@", 1)
            return f"{user[:2]}***@{handle}"
        return f"{'*' * (len(val) - 4)}{val[-4:]}"

    def redact_text(self, text: str) -> Tuple[str, Dict[str, str]]:
        """
        Replaces all PII occurrences in `text` with deterministic vault tokens.
        Returns (redacted_text, token_map_created).
        """
        if not text:
            return "", {}

        redacted = text
        created_tokens: Dict[str, str] = {}

        for pii_type, pattern in self.PATTERNS:
            def _replacer(match: re.Match) -> str:
                raw_val = match.group(0)
                # Skip already-tokenized placeholders
                if raw_val.startswith("<") and raw_val.endswith(">"):
                    return raw_val
                token = self._make_token(pii_type, raw_val)
                created_tokens[token] = raw_val
                return token

            redacted = pattern.sub(_replacer, redacted)

        return redacted, created_tokens

    def detokenize(self, token: str) -> str:
        """Resolves a vault token back to its raw value for authorized Layer 4 NPCI tool execution."""
        return self._memory_vault.get(token, token)


pii_vault = PIITokenVault()
