import logging
import re
import sys
from contextlib import asynccontextmanager
from typing import Dict, Any, Optional
from fastapi import FastAPI, Depends, Request, Response, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from gateway.app.config import settings
from gateway.app.db import get_db, init_db
from gateway.app.models import User, utc_now
from gateway.app.auth import router as auth_router, get_current_user
from gateway.app.prevention import router as prevention_router
from gateway.app.unstuck import router as unstuck_router
from gateway.app.billing import router as billing_router
from gateway.app.quota import is_user_entitled, get_user_usage
from gateway.app.schemas import UserProfileResponse

# Configure Log Redaction Filter
class SensitiveDataFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.msg, str):
            # Redact VPAs (e.g. name@bank)
            record.msg = re.sub(r'[\w\.\-]+@[\w\-]+', '[REDACTED_VPA]', record.msg)
            # Redact Authorization Bearer tokens
            record.msg = re.sub(r'Bearer\s+[A-Za-z0-9\-\._~\+\/]+=*', 'Bearer [REDACTED_TOKEN]', record.msg)
            # Redact Purchase tokens
            record.msg = re.sub(r'purchase_token=[^&\s]+', 'purchase_token=[REDACTED]', record.msg)
        return True

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
root_logger = logging.getLogger()
root_logger.addFilter(SensitiveDataFilter())

# Initialize DB schemas
init_db()

@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.DEMO_MODE:
        logging.warning("⚠️  DEMO_MODE IS ACTIVE! Demo routes mounted at /demo/*. DO NOT USE IN PRODUCTION!")
    yield

app = FastAPI(
    title="UFO Gateway Backend",
    version="1.0.0",
    description="Central Gateway for UFO Fraud Observer & Unstuck Resolution Ecosystem",
    lifespan=lifespan
)

# Strict CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Mount core routers
app.include_router(auth_router)
app.include_router(prevention_router)
app.include_router(unstuck_router)
app.include_router(billing_router)

# Mount demo router ONLY if DEMO_MODE=True
if settings.DEMO_MODE:
    from gateway.app.demo import router as demo_router
    app.include_router(demo_router)

@app.get("/")
def root():
    return {
        "service": "UFO Gateway Backend",
        "status": "ONLINE",
        "demo_mode": settings.DEMO_MODE,
        "upstream_base_url": settings.UPSTREAM_BASE_URL
    }

@app.get("/health")
def health():
    return {
        "status": "HEALTHY",
        "demo_mode": settings.DEMO_MODE,
        "upstream_configured": bool(settings.UPSTREAM_BASE_URL)
    }

@app.get("/me", response_model=UserProfileResponse)
def get_current_user_profile(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    entitled, plan, exp = is_user_entitled(db, user)
    usage = get_user_usage(db, user)

    prev_rem = 999999 if entitled else max(0, settings.FREE_PREVENTION_LIMIT - usage.prevention_used)
    unstuck_rem = 999999 if entitled else max(0, settings.FREE_UNSTUCK_LIMIT - usage.unstuck_used)

    return UserProfileResponse(
        id=user.id,
        email=user.email,
        email_verified=user.email_verified,
        is_demo=user.is_demo,
        plan=plan,
        is_entitled=entitled,
        prevention_used=usage.prevention_used,
        prevention_remaining=prev_rem,
        unstuck_used=usage.unstuck_used,
        unstuck_remaining=unstuck_rem,
        entitlement_expires_at=exp
    )

@app.delete("/account")
def delete_account(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    user.deleted_at = utc_now()
    user.email = None  # Purge PII
    db.commit()
    return {"message": "Account successfully deleted and PII purged"}

