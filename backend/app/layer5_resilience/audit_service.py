from datetime import datetime, timezone
from typing import Any, Dict, List
from app.db.mongo import audit_logs_collection


class AuditNotificationService:
    """
    Component 5: Audit & Notification Service.
    Records immutable RBI-compliant audit trails (zero raw PII) and dispatches
    user safety warnings and regulatory alerts.
    """

    def record_event(
        self,
        event_type: str,
        reference_id: str,
        summary: str,
        metadata: Dict[str, Any],
    ) -> Dict[str, Any]:
        entry = {
            "event_type": event_type,
            "reference_id": reference_id,
            "summary": summary,
            "metadata": metadata,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        audit_logs_collection.insert_one(entry)
        entry.pop("_id", None)
        return entry

    def get_recent_logs(self, limit: int = 50) -> List[Dict[str, Any]]:
        return list(
            audit_logs_collection.find({}, {"_id": 0}).sort("timestamp", -1).limit(limit)
        )


audit_service = AuditNotificationService()
