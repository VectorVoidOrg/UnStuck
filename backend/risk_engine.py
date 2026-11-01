"""
Backward-compatible risk_engine module re-exporting Layer 1 deterministic rule & ML risk engine.
"""
from app.layer1_gateway.rule_engine import calculate_risk, evaluate_transaction_sync

__all__ = ["calculate_risk", "evaluate_transaction_sync"]
