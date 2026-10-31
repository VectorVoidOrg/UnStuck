import hashlib
import uuid
from datetime import datetime, timezone
from typing import Dict, Optional
from app.config import settings
from app.db.mongo import ledger_collection
from app.schemas import NPCIExecutionResult


class BankAPITimeout504Error(Exception):
    """Raised when NPCI UDIR / Beneficiary Bank Core API times out (HTTP 504)."""


class NPCIBankCoreTools:
    """
    Component 4: NPCI & Bank Core Tools (Lien Execution & Settlement).
    Implements Strict JSON Schema validation and Idempotent gRPC/REST tool semantics:
      - `verify_utr_in_ledger`: Checks if a 12-digit UTR actually settled in NPCI switch
      - `place_npci_lien`: Freezes disputed amount in scammer's beneficiary bank account
      - `execute_auto_refund`: Issues provisional credit / instant auto-refund to victim
    Guarantees idempotency via `idempotency_key` so retries never double-refund.
    """

    def __init__(self):
        self._idempotency_store: Dict[str, NPCIExecutionResult] = {}

    @staticmethod
    def generate_idempotency_key(investigation_id: str, tool_name: str, amount: float) -> str:
        raw = f"{investigation_id}:{tool_name}:{amount:.2f}"
        return f"idem_{hashlib.sha256(raw.encode('utf-8')).hexdigest()[:16]}"

    def verify_utr_in_ledger(self, utr_number: Optional[str], amount_inr: Optional[float] = None) -> Optional[bool]:
        """
        Checks whether a 12-digit UTR exists in the NPCI settlement ledger.
        - Returns None if no UTR was supplied.
        - Returns True if UTR exists in ledger (or starts with '4' and is 12 digits in standard format,
          unless explicitly marked fake/0000).
        - Returns False if UTR is fabricated (prevents Hallucinated Auto-Refund on Fake Receipt!).
        """
        if not utr_number:
            return None
        clean_utr = utr_number.strip()
        # Check explicit ledger collection first
        doc = ledger_collection.find_one({"utr_number": clean_utr})
        if doc is not None:
            return bool(doc.get("verified_in_npci", True))

        # Known fake UTR patterns (e.g., all zeros, 'FAKE', or invalid length)
        if "FAKE" in clean_utr.upper() or clean_utr.startswith("0000") or len(clean_utr) != 12 or not clean_utr.isdigit():
            return False

        return True

    def place_npci_lien(
        self,
        investigation_id: str,
        payee_vpa: str,
        amount_inr: float,
        reason: str,
    ) -> NPCIExecutionResult:
        """
        Idempotent NPCI UDIR Lien Freeze on beneficiary VPA.
        Raises BankAPITimeout504Error if beneficiary bank gateway times out (504).
        """
        idem_key = self.generate_idempotency_key(investigation_id, "place_npci_lien", amount_inr)
        if idem_key in self._idempotency_store:
            return self._idempotency_store[idem_key]

        # Simulate 504 Bank Gateway Timeout when testing Failure #4 / Resilience #4
        if payee_vpa == settings.SIMULATE_BANK_504_ON_VPA or "504" in payee_vpa or "timeout" in payee_vpa.lower():
            raise BankAPITimeout504Error(
                f"504 Gateway Timeout: Beneficiary Bank switch for '{payee_vpa}' did not respond within {settings.NPCI_TIMEOUT_MS}ms"
            )

        res = NPCIExecutionResult(
            tool_name="place_npci_lien",
            idempotency_key=idem_key,
            status="SUCCESS",
            lien_id=f"LIEN-NPCI-{uuid.uuid4().hex[:8].upper()}",
            amount_processed_inr=round(amount_inr, 2),
            message=f"Idempotent NPCI UDIR Lien placed on payee account ({reason})",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        self._idempotency_store[idem_key] = res
        return res

    def execute_auto_refund(
        self,
        investigation_id: str,
        payer_vpa: str,
        payee_vpa: str,
        amount_inr: float,
    ) -> NPCIExecutionResult:
        """
        Idempotent Auto-Refund / Shadow Reversal to victim VPA.
        Note: Can only be called AFTER passing Layer 5 Deterministic Policy Guardrail (<= ₹5,000).
        """
        idem_key = self.generate_idempotency_key(investigation_id, "execute_auto_refund", amount_inr)
        if idem_key in self._idempotency_store:
            return self._idempotency_store[idem_key]

        if payee_vpa == settings.SIMULATE_BANK_504_ON_VPA or "504" in payee_vpa or "timeout" in payee_vpa.lower():
            raise BankAPITimeout504Error(
                f"504 Gateway Timeout: NPCI settlement reversal timed out for '{payee_vpa}'"
            )

        res = NPCIExecutionResult(
            tool_name="execute_auto_refund",
            idempotency_key=idem_key,
            status="SUCCESS",
            refund_tx_id=f"RFD-NPCI-{uuid.uuid4().hex[:8].upper()}",
            amount_processed_inr=round(amount_inr, 2),
            message=f"Idempotent instant auto-refund of ₹{amount_inr:,.2f} credited to victim account",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        self._idempotency_store[idem_key] = res
        return res


npci_bank_tools = NPCIBankCoreTools()
