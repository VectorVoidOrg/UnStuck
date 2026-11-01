"""
Backward-compatible user_store module re-exporting Layer 1 receiver history helpers.
"""
from app.layer1_gateway.reputation_service import is_new_receiver, update_history

__all__ = ["is_new_receiver", "update_history"]
