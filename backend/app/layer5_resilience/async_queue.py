import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from app.db.mongo import investigations_collection
from app.schemas import DisputeInvestigationRequest, InvestigationTier, ResolutionAction


class AsyncEventQueue:
    """
    Step 4: Interactions — Decouple Latency with Async Flows.
    Key Principle: Never block a live user thread waiting for an Agentic reasoning loop.
    Pushes anomalous transactions / disputes into an asynchronous background worker pool
    (compatible with Kafka / PubSub / Redis Streams / ThreadPoolExecutor) and immediately
    returns an `investigation_id` in <2ms so the HTTP gateway responds in <150ms.
    """

    def __init__(self, max_workers: int = 4):
        self._executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="ufo-agent-worker")
        self._lock = threading.Lock()
        self._queued_count = 0
        self._processed_count = 0

    def enqueue_investigation(self, req: DisputeInvestigationRequest) -> str:
        inv_id = f"INV-{uuid.uuid4().hex[:10].upper()}"
        now_iso = datetime.now(timezone.utc).isoformat()

        # Create initial QUEUED record immediately (<1ms)
        investigations_collection.update_one(
            {"investigation_id": inv_id},
            {
                "$set": {
                    "investigation_id": inv_id,
                    "status": "QUEUED",
                    "tier_used": InvestigationTier.SEMANTIC_CACHE.value,
                    "cache_hit": False,
                    "redacted_context": "[QUEUED_FOR_PII_REDACTION]",
                    "pii_tokens_created": 0,
                    "prompt_injection_detected": False,
                    "reasoning_loops_used": 0,
                    "tokens_consumed": 0,
                    "circuit_breaker_tripped": False,
                    "agent_verdict": None,
                    "policy_guardrail_applied": False,
                    "policy_guardrail_notes": [],
                    "final_action": ResolutionAction.WARN_USER.value,
                    "npci_tool_results": [],
                    "hitl_ticket_id": None,
                    "total_processing_ms": 0.0,
                    "created_at": now_iso,
                }
            },
            upsert=True,
        )

        with self._lock:
            self._queued_count += 1

        self._executor.submit(self._worker_task, req, inv_id)
        return inv_id

    def _worker_task(self, req: DisputeInvestigationRequest, inv_id: str) -> None:
        from app.layer3_agent.orchestrator import agent_orchestrator

        try:
            agent_orchestrator.run_investigation(req, investigation_id=inv_id)
        finally:
            with self._lock:
                self._processed_count += 1

    def get_investigation(self, investigation_id: str) -> Optional[Dict[str, Any]]:
        return investigations_collection.find_one(
            {"investigation_id": investigation_id}, {"_id": 0}
        )

    def get_metrics(self) -> Dict[str, int]:
        with self._lock:
            return {
                "total_enqueued": self._queued_count,
                "total_processed": self._processed_count,
                "in_flight": max(0, self._queued_count - self._processed_count),
            }


async_event_queue = AsyncEventQueue()
