from datetime import datetime, timezone
from typing import Any, Dict
from app.db.mongo import (
    user_collection,
    receiver_collection,
    hall_collection,
    audit_logs_collection,
)


def is_new_receiver(user_id: str, receiver: str) -> bool:
    """Checks if the user has ever transacted with this receiver VPA before."""
    user = user_collection.find_one({"user_id": user_id})
    if not user:
        return True
    return receiver not in user.get("receivers", [])


def update_history(user_id: str, receiver: str) -> None:
    """Records receiver VPA in the user's known payee list."""
    user_collection.update_one(
        {"user_id": user_id},
        {"$addToSet": {"receivers": receiver}},
        upsert=True,
    )


def is_blacklisted(receiver: str) -> bool:
    """Checks if a receiver VPA is in the Hall of Shame / NPCI Blacklist."""
    data = hall_collection.find_one({"receiver": receiver})
    return data is not None


def send_to_rbi(receiver: str, avg_risk: float, flag_count: int) -> Dict[str, Any]:
    """Generates an RBI / Cybercrime 1930 compliance report and writes to audit log."""
    report = {
        "event_type": "RBI_FRAUD_ESCALATION",
        "receiver": receiver,
        "avg_risk": round(avg_risk, 3),
        "flag_count": flag_count,
        "message": "Repeated high-risk fraud patterns detected; VPA blacklisted in Hall of Shame",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    audit_logs_collection.insert_one(report)
    return report


def update_reputation(receiver: str, risk: float) -> None:
    """Updates receiver telemetry and promotes repeat offenders to Hall of Shame."""
    receiver_collection.update_one(
        {"receiver": receiver},
        {
            "$inc": {
                "transactions": 1,
                "total_risk": float(risk),
                "flag_count": 1 if risk > 0.7 else 0,
            }
        },
        upsert=True,
    )

    data = receiver_collection.find_one({"receiver": receiver})
    if not data:
        return

    transactions = data.get("transactions", 0)
    total_risk = data.get("total_risk", 0.0)
    flag_count = data.get("flag_count", 0)

    avg_risk = (total_risk / transactions) if transactions > 0 else 0.0

    # Strict Hall of Shame promotion rule
    if flag_count >= 3 and avg_risk >= 0.75 and transactions >= 5:
        hall_collection.update_one(
            {"receiver": receiver},
            {
                "$set": {
                    "receiver": receiver,
                    "avg_risk": round(avg_risk, 2),
                    "flag_count": flag_count,
                    "transactions": transactions,
                    "status": "BLACKLISTED",
                }
            },
            upsert=True,
        )
        send_to_rbi(receiver, avg_risk, flag_count)


def get_reputation_score(receiver: str) -> float:
    """Returns a normalized [0.0, 1.0] risk reputation score for the receiver VPA."""
    if is_blacklisted(receiver):
        return 1.0

    data = receiver_collection.find_one({"receiver": receiver})
    if not data or not data.get("transactions"):
        return 0.0

    avg_risk = data["total_risk"] / data["transactions"]
    score = (avg_risk * 0.7) + (min(data.get("flag_count", 0), 5) * 0.06)
    return float(min(score, 1.0))
