"""
Backward-compatible ml_model module re-exporting Layer 1 fast tabular ML scorer.
"""
from app.layer1_gateway.ml_scorer import ml_score

__all__ = ["ml_score"]
