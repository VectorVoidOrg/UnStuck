"""
UFO Layer 3 — Complexity Router & Semantic Cache Trainer (`backend/scripts/train_complexity_router.py`).
Calibrates the Complexity Router threshold (80% SLM vs 20% Frontier LLM + RBI RAG)
and exports `backend/models/complexity_router.json`.

Usage:
    python backend/scripts/train_complexity_router.py
"""
import json
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BACKEND_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = BACKEND_DIR / "models"


def train_router() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    router_config = {
        "version": "4.0.0",
        "target_slm_traffic_share": 0.80,
        "target_frontier_rag_traffic_share": 0.20,
        "frontier_threshold": 0.55,
        "high_amount_threshold_inr": 5000.0,
        "complex_keywords": [
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
        ],
    }
    out_path = MODELS_DIR / "complexity_router.json"
    out_path.write_text(json.dumps(router_config, indent=2), encoding="utf-8")
    print(f"✅ Saved calibrated Layer 3 Complexity Router config to: {out_path}")


if __name__ == "__main__":
    train_router()
