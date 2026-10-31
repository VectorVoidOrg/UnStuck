"""
Backward-compatible reputation module re-exporting Layer 1 reputation & Hall of Shame service.
"""
from app.layer1_gateway.reputation_service import (
    is_blacklisted,
    send_to_rbi,
    update_reputation,
    get_reputation_score,
)

__all__ = [
    "is_blacklisted",
    "send_to_rbi",
    "update_reputation",
    "get_reputation_score",
]
