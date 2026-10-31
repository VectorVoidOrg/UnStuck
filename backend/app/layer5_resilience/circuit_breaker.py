import threading
import time
from collections import deque
from app.config import settings


class CircuitBreakerTrippedError(Exception):
    """Raised when an Agent exceeds max reasoning loops (3) or token budget cap."""


class GlobalTokenGovernor:
    """
    Protects against Failure #2 (Slide 10):
    'Token Budget Explosion during Festival Traffic Spike'.
    Tracks sliding 60-second token consumption across all concurrent requests.
    """

    def __init__(self, max_tokens_per_minute: int = settings.GLOBAL_TOKEN_BUDGET_PER_MINUTE):
        self.max_tokens_per_minute = max_tokens_per_minute
        self._lock = threading.Lock()
        self._window: deque = deque()  # (timestamp, tokens)

    def consume(self, tokens: int) -> bool:
        now = time.time()
        with self._lock:
            while self._window and now - self._window[0][0] > 60.0:
                self._window.popleft()
            current_total = sum(t for _, t in self._window)
            if current_total + tokens > self.max_tokens_per_minute:
                return False
            self._window.append((now, tokens))
            return True

    def current_usage(self) -> int:
        now = time.time()
        with self._lock:
            while self._window and now - self._window[0][0] > 60.0:
                self._window.popleft()
            return sum(t for _, t in self._window)


global_token_governor = GlobalTokenGovernor()


class TokenBudgetCircuitBreaker:
    """
    Step 5B Resilience #2: Token Budget Circuit Breaker.
    Caps Agent to max 3 reasoning loops per request and enforces a strict per-request
    and global token budget to eliminate runaway agent loops and cost explosions.
    """

    def __init__(
        self,
        max_loops: int = settings.MAX_REASONING_LOOPS,
        max_tokens: int = settings.MAX_TOKEN_BUDGET_PER_REQUEST,
    ):
        self.max_loops = max_loops
        self.max_tokens = max_tokens
        self.loops_used = 0
        self.tokens_used = 0
        self.tripped = False
        self.trip_reason = ""

    def record_step(self, loop_index: int, tokens_delta: int) -> None:
        if loop_index > self.max_loops or (self.loops_used + 1) > self.max_loops:
            self.tripped = True
            self.trip_reason = (
                f"Circuit Breaker tripped: Agent attempted reasoning loop #{loop_index}, "
                f"exceeding hard cap of {self.max_loops} loops."
            )
            raise CircuitBreakerTrippedError(self.trip_reason)

        if (self.tokens_used + tokens_delta) > self.max_tokens:
            self.tripped = True
            self.trip_reason = (
                f"Circuit Breaker tripped: Request token budget ({self.tokens_used + tokens_delta}) "
                f"exceeded cap of {self.max_tokens} tokens."
            )
            raise CircuitBreakerTrippedError(self.trip_reason)

        if not global_token_governor.consume(tokens_delta):
            self.tripped = True
            self.trip_reason = (
                "Circuit Breaker tripped: Global festival traffic spike token budget exceeded."
            )
            raise CircuitBreakerTrippedError(self.trip_reason)

        self.loops_used += 1
        self.tokens_used += tokens_delta
