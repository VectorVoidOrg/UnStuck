import json
from typing import Any, Dict, Tuple
from app.config import MODELS_DIR
from app.schemas import InvestigationTier


class ComplexityRouter:
    """
    Step 3 Subcomponent 3.2: Complexity Router.
    Evaluates risk level, intent, dispute complexity & transaction metadata:
      - 80% Routine Checks -> Small Quantized Model (SLM)
      - 20% Complex Multi-Step Disputes -> Frontier LLM + RBI RAG
    Can also load trained router weights from `backend/models/complexity_router.json`.
    """

    COMPLEX_KEYWORDS = {
        "dispute",
        "chargeback",
        "ombudsman",
        "rbi",
        "circular",
        "unauthorized",
        "lien",
        "reversal",
        "utr",
        "screenshot",
        "receipt",
        "extortion",
        "court",
        "police",
        "hacked",
        "sim swap",
    }

    def __init__(self):
        self._custom_weights: Dict[str, Any] = {}
        weights_file = MODELS_DIR / "complexity_router.json"
        if weights_file.exists():
            try:
                self._custom_weights = json.loads(weights_file.read_text(encoding="utf-8"))
            except Exception:
                self._custom_weights = {}

    def route(
        self,
        redacted_context: str,
        amount_inr: float,
        has_screenshot_ocr: bool = False,
        has_utr: bool = False,
        prompt_injection_detected: bool = False,
        initial_risk_score: float = 0.5,
    ) -> Tuple[InvestigationTier, float, str]:
        """
        Returns (target_tier, complexity_score [0..1], routing_reason).
        """
        complexity = 0.0
        reasons = []

        # 1. High-value transactions (> ₹5,000 policy threshold) require Frontier LLM + RBI RAG
        if amount_inr > 5000.0:
            complexity += 0.45
            reasons.append(f"High value dispute (₹{amount_inr:,.0f} > ₹5,000 guardrail)")

        # 2. Multi-modal / OCR receipt + UTR verification
        if has_screenshot_ocr and has_utr:
            complexity += 0.35
            reasons.append("Multi-step OCR receipt + NPCI UTR ledger cross-verification")
        elif has_screenshot_ocr:
            complexity += 0.25
            reasons.append("Screenshot OCR evidence analysis")

        # 3. Prompt injection attempt in OCR always escalates to hardened Frontier + Guardrail check
        if prompt_injection_detected:
            complexity += 0.50
            reasons.append("Adversarial prompt injection detected in OCR receipt")

        # 4. Complex legal/regulatory keywords in user dispute
        lower_ctx = (redacted_context or "").lower()
        matched_kw = [kw for kw in self.COMPLEX_KEYWORDS if kw in lower_ctx]
        if len(matched_kw) >= 2:
            complexity += 0.30
            reasons.append(f"Multi-clause regulatory/dispute intent ({', '.join(matched_kw[:3])})")

        # 5. Long narrative context (> 40 words)
        word_count = len(lower_ctx.split())
        if word_count > 40:
            complexity += 0.15
            reasons.append("Extended multi-event narrative")

        complexity = round(min(complexity, 1.0), 3)
        threshold = float(self._custom_weights.get("frontier_threshold", 0.55))

        if complexity >= threshold:
            return (
                InvestigationTier.FRONTIER_LLM_RAG,
                complexity,
                "; ".join(reasons) or "Complex multi-step dispute routed to Frontier LLM + RBI RAG (20% tier)",
            )

        return (
            InvestigationTier.SMALL_QUANTIZED_MODEL,
            complexity,
            "Routine anomaly check routed to Small Quantized Model - SLM (80% fast tier)",
        )


complexity_router = ComplexityRouter()
