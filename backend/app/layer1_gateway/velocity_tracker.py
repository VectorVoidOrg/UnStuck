import time
import threading
from collections import defaultdict, deque
from typing import Dict, List, Tuple


class VelocityTracker:
    """
    Step 1 Deterministic Software (<1ms):
    In-memory sliding-window velocity & burst tracker for users and receivers.
    Detects:
    - High-frequency burst payments (>3 txns within 60 seconds)
    - Structuring / smurfing (multiple payments just below ₹5,000 guardrail)
    - Receiver fan-in spike (mule account receiving funds from many distinct users)
    """

    def __init__(self, window_seconds: int = 300):
        self.window_seconds = window_seconds
        self._lock = threading.Lock()
        # user_id -> deque of (timestamp, amount, receiver)
        self._user_events: Dict[str, deque] = defaultdict(deque)
        # receiver -> deque of (timestamp, amount, user_id)
        self._receiver_events: Dict[str, deque] = defaultdict(deque)

    def _prune(self, q: deque, now: float) -> None:
        cutoff = now - self.window_seconds
        while q and q[0][0] < cutoff:
            q.popleft()

    def check_and_record(
        self, user_id: str, receiver: str, amount: float
    ) -> Tuple[float, List[str]]:
        """
        Evaluates sliding-window velocity rules and records the current transaction attempt.
        Returns (velocity_risk_boost, velocity_reasons).
        """
        now = time.time()
        risk_boost = 0.0
        reasons: List[str] = []

        with self._lock:
            u_q = self._user_events[user_id]
            r_q = self._receiver_events[receiver]
            self._prune(u_q, now)
            self._prune(r_q, now)

            # 1. User burst check (last 60 seconds)
            recent_60s = [ev for ev in u_q if now - ev[0] <= 60.0]
            if len(recent_60s) >= 3:
                risk_boost += 0.35
                reasons.append(f"High velocity burst: {len(recent_60s) + 1} txns in <60s")

            # 2. Structuring check (multiple txns between ₹4,000 and ₹4,999 to evade ₹5k cap)
            structuring_txns = [
                ev for ev in u_q if 4000.0 <= ev[1] <= 5000.0
            ]
            if len(structuring_txns) >= 2 and 4000.0 <= amount <= 5000.0:
                risk_boost += 0.30
                reasons.append("Potential structuring pattern just below ₹5,000 policy limit")

            # 3. Mule receiver fan-in check (many distinct payers in 5 minutes)
            distinct_payers = {ev[2] for ev in r_q}
            distinct_payers.add(user_id)
            if len(distinct_payers) >= 5:
                risk_boost += 0.25
                reasons.append(f"Mule account velocity: {len(distinct_payers)} distinct payers in 5m")

            # Record current event
            u_q.append((now, amount, receiver))
            r_q.append((now, amount, user_id))

        return min(risk_boost, 0.75), reasons


velocity_tracker = VelocityTracker()
