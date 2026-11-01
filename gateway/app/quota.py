import json
from datetime import datetime, timezone
from typing import Optional, Tuple, Any, Dict
from sqlalchemy.orm import Session
from sqlalchemy import text
from fastapi import HTTPException, status

from gateway.app.config import settings
from gateway.app.models import User, Usage, CheckEvent, Subscription, utc_now

def is_user_entitled(db: Session, user: User) -> Tuple[bool, str, Optional[Any]]:
    """
    Entitlement function:
    ACTIVE, IN_GRACE_PERIOD and CANCELED-until-expiry are entitled.
    ON_HOLD, PAUSED, EXPIRED, PENDING, refunded/revoked are not.
    """
    now = utc_now()
    subs = db.query(Subscription).filter(
        Subscription.user_id == user.id
    ).order_by(Subscription.id.desc()).all()

    def _ensure_utc(dt):
        if dt is None:
            return None
        if dt.tzinfo is None:
            return dt.replace(tzinfo=timezone.utc)
        return dt

    for sub in subs:
        sub_exp = _ensure_utc(sub.expires_at)
        if sub.state in ("ACTIVE", "IN_GRACE_PERIOD"):
            if sub_exp is None or sub_exp > now:
                return True, sub.product_id, sub_exp
        elif sub.state == "CANCELED":
            if sub_exp is not None and sub_exp > now:
                return True, f"{sub.product_id}_CANCELED", sub_exp

    return False, "FREE", None

def get_user_usage(db: Session, user: User) -> Usage:
    usage = db.query(Usage).filter(Usage.user_id == user.id).first()
    if not usage:
        usage = Usage(user_id=user.id, prevention_used=0, unstuck_used=0)
        db.add(usage)
        db.commit()
        db.refresh(usage)
    return usage

def consume_quota(db: Session, user: User, kind: str, event_id: str) -> Optional[Dict[str, Any]]:
    """
    Single transactional consumption:
    1. If event_id already exists in check_events, return stored outcome (idempotent, no double count).
    2. If user is entitled, allow and record check_event.
    3. Else, increment ONLY if below limit via conditional UPDATE.
    4. If limit reached, raise HTTP 402 QUOTA_EXHAUSTED.
    """
    # 1. Idempotency check
    existing = db.query(CheckEvent).filter(
        CheckEvent.id == event_id,
        CheckEvent.user_id == user.id
    ).first()
    if existing:
        if existing.outcome:
            try:
                return json.loads(existing.outcome)
            except Exception:
                return {"cached": True, "raw": existing.outcome}
        return {"idempotent_replay": True}

    entitled, plan, exp = is_user_entitled(db, user)
    if entitled:
        # Record check event
        event = CheckEvent(
            id=event_id,
            user_id=user.id,
            kind=kind,
            outcome=None,
            created_at=utc_now()
        )
        db.add(event)
        db.commit()
        return None

    # 2. Free tier enforcement via conditional UPDATE
    get_user_usage(db, user) # ensure usage row exists

    if kind == "prevention":
        limit = settings.FREE_PREVENTION_LIMIT
        # Atomic conditional update: only increments if current count < limit
        stmt = text(
            "UPDATE usage SET prevention_used = prevention_used + 1 "
            "WHERE user_id = :uid AND prevention_used < :limit"
        )
        result = db.execute(stmt, {"uid": user.id, "limit": limit})
        if result.rowcount == 0:
            db.rollback()
            raise HTTPException(
                status_code=status.HTTP_402_PAYMENT_REQUIRED,
                detail={"code": "QUOTA_EXHAUSTED", "kind": kind, "limit": limit}
            )
    elif kind == "unstuck":
        limit = settings.FREE_UNSTUCK_LIMIT
        stmt = text(
            "UPDATE usage SET unstuck_used = unstuck_used + 1 "
            "WHERE user_id = :uid AND unstuck_used < :limit"
        )
        result = db.execute(stmt, {"uid": user.id, "limit": limit})
        if result.rowcount == 0:
            db.rollback()
            raise HTTPException(
                status_code=status.HTTP_402_PAYMENT_REQUIRED,
                detail={"code": "QUOTA_EXHAUSTED", "kind": kind, "limit": limit}
            )
    else:
        raise ValueError(f"Unknown kind {kind}")

    # Record check event
    event = CheckEvent(
        id=event_id,
        user_id=user.id,
        kind=kind,
        outcome=None,
        created_at=utc_now()
    )
    db.add(event)
    db.commit()
    return None

def store_check_outcome(db: Session, event_id: str, outcome: Dict[str, Any]) -> None:
    event = db.query(CheckEvent).filter(CheckEvent.id == event_id).first()
    if event:
        event.outcome = json.dumps(outcome)
        db.commit()

def release_consumed(db: Session, user: User, kind: str, event_id: str) -> None:
    """
    If an upstream call fails, idempotently roll back the consumed unit.
    """
    event = db.query(CheckEvent).filter(
        CheckEvent.id == event_id,
        CheckEvent.user_id == user.id
    ).first()
    
    if event:
        db.delete(event)
        if kind == "prevention":
            db.execute(
                text("UPDATE usage SET prevention_used = CASE WHEN prevention_used > 0 THEN prevention_used - 1 ELSE 0 END WHERE user_id = :uid"),
                {"uid": user.id}
            )
        elif kind == "unstuck":
            db.execute(
                text("UPDATE usage SET unstuck_used = CASE WHEN unstuck_used > 0 THEN unstuck_used - 1 ELSE 0 END WHERE user_id = :uid"),
                {"uid": user.id}
            )
        db.commit()

