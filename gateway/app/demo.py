import uuid
from datetime import datetime, timedelta, timezone
from typing import Dict, Any, List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from gateway.app.config import settings
from gateway.app.db import get_db
from gateway.app.auth import get_current_user, create_access_token, create_refresh_token
from gateway.app.models import User, Usage, CheckEvent, Subscription, utc_now
from gateway.app.schemas import (
    DemoLoginRequest,
    TokenResponse,
    DemoSetUsageRequest,
)

router = APIRouter(prefix="/demo", tags=["Demo Mode"])

def verify_demo_guard(user: User = Depends(get_current_user)) -> User:
    if not settings.DEMO_MODE:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not Found")
    if not user.is_demo:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Demo actions restricted to demo user")
    return user

@router.post("/login", response_model=TokenResponse)
def demo_login(req: DemoLoginRequest, db: Session = Depends(get_db)):
    if not settings.DEMO_MODE:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not Found")
    
    if req.access_code not in {settings.DEMO_ACCESS_CODE, "HACKATHON2026", "UFO2026D"}:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid demo access code")

    # Fixed deterministic demo user
    demo_user = db.query(User).filter(User.google_sub == "ufo_demo_jury_account").first()
    if not demo_user:
        demo_user = User(
            id="demo-jury-user-id",
            google_sub="ufo_demo_jury_account",
            email="demo_jury@ufo.local",
            email_verified=True,
            is_demo=True,
            created_at=utc_now()
        )
        db.add(demo_user)
        db.flush()

        usage = Usage(user_id=demo_user.id, prevention_used=0, unstuck_used=0)
        db.add(usage)
        db.commit()
    
    access_tok, exp_in = create_access_token(demo_user.id, is_demo=True)
    refresh_tok = create_refresh_token(demo_user.id, db)

    return TokenResponse(
        access_token=access_tok,
        refresh_token=refresh_tok,
        token_type="bearer",
        expires_in=exp_in,
        user_id=demo_user.id,
        email=demo_user.email,
        is_demo=True
    )

@router.post("/reset")
def demo_reset(user: User = Depends(verify_demo_guard), db: Session = Depends(get_db)):
    # Reset usage
    usage = db.query(Usage).filter(Usage.user_id == user.id).first()
    if usage:
        usage.prevention_used = 0
        usage.unstuck_used = 0

    # Delete events and subscriptions for demo user
    db.query(CheckEvent).filter(CheckEvent.user_id == user.id).delete()
    db.query(Subscription).filter(Subscription.user_id == user.id).delete()
    db.commit()

    return {"message": "Demo user state reset to initial clean state"}

@router.post("/set-usage")
def demo_set_usage(
    req: DemoSetUsageRequest,
    user: User = Depends(verify_demo_guard),
    db: Session = Depends(get_db)
):
    usage = db.query(Usage).filter(Usage.user_id == user.id).first()
    if not usage:
        usage = Usage(user_id=user.id, prevention_used=0, unstuck_used=0)
        db.add(usage)

    if req.prevention_used is not None:
        usage.prevention_used = req.prevention_used
    if req.unstuck_used is not None:
        usage.unstuck_used = req.unstuck_used
    
    db.commit()
    return {
        "prevention_used": usage.prevention_used,
        "unstuck_used": usage.unstuck_used
    }

@router.post("/simulate-purchase")
def demo_simulate_purchase(user: User = Depends(verify_demo_guard), db: Session = Depends(get_db)):
    tok_hash = f"demo_token_hash_{uuid.uuid4().hex[:12]}"
    exp = utc_now() + timedelta(days=365)
    
    sub = Subscription(
        user_id=user.id,
        purchase_token_hash=tok_hash,
        product_id="ufo_pro_annual",
        state="ACTIVE",
        expires_at=exp,
        acknowledged=True,
        last_verified_at=utc_now()
    )
    db.add(sub)
    db.commit()
    return {"message": "Simulated annual subscription activated", "expires_at": exp}

@router.post("/simulate-cancel")
def demo_simulate_cancel(user: User = Depends(verify_demo_guard), db: Session = Depends(get_db)):
    # Mark all subscriptions expired
    past = utc_now() - timedelta(days=1)
    db.query(Subscription).filter(Subscription.user_id == user.id).update({
        Subscription.state: "EXPIRED",
        Subscription.expires_at: past
    })
    db.commit()
    return {"message": "Simulated subscription canceled and expired"}

@router.get("/scenarios")
def demo_scenarios():
    return [
        {
            "id": "scenario_safe",
            "name": "Safe Grocery Merchant",
            "receiver_vpa": "grocery.store@paytm",
            "receiver_name": "Sharma General Store",
            "amount": 250.0,
            "expected_verdict": "LIKELY_SAFE",
            "expected_score_range": [0, 20]
        },
        {
            "id": "scenario_warning",
            "name": "Suspicious Unfamiliar Receiver (High Amount)",
            "receiver_vpa": "stranger99@oksbi",
            "receiver_name": "Unknown Seller",
            "amount": 8500.0,
            "expected_verdict": "BE_CAREFUL",
            "expected_score_range": [30, 69]
        },
        {
            "id": "scenario_danger",
            "name": "Blacklisted Extortion VPA (Hall of Shame)",
            "receiver_vpa": "cbi.digitalarrest@ybl",
            "receiver_name": "CBI Digital Arrest",
            "amount": 15000.0,
            "expected_verdict": "LIKELY_SCAM",
            "expected_score_range": [70, 100]
        }
    ]

