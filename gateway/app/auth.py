import hashlib
import secrets
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional, Tuple
import jwt
from fastapi import APIRouter, Depends, HTTPException, status, Header
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session

from gateway.app.config import settings
from gateway.app.db import get_db
from gateway.app.models import User, Usage, RefreshToken, Nonce, utc_now
from gateway.app.schemas import (
    NonceResponse,
    GoogleAuthRequest,
    TokenResponse,
    RefreshRequest,
)

router = APIRouter(prefix="/auth", tags=["Authentication"])
security = HTTPBearer(auto_error=False)

def hash_string(val: str) -> str:
    return hashlib.sha256(val.encode("utf-8")).hexdigest()

def create_access_token(user_id: str, is_demo: bool = False) -> Tuple[str, int]:
    # Parse ACCESS_TTL (default 15m)
    minutes = 15
    if settings.ACCESS_TTL.endswith("m"):
        minutes = int(settings.ACCESS_TTL[:-1])
    elif settings.ACCESS_TTL.endswith("h"):
        minutes = int(settings.ACCESS_TTL[:-1]) * 60
    
    expires_in = minutes * 60
    exp = utc_now() + timedelta(seconds=expires_in)
    
    payload = {
        "sub": user_id,
        "is_demo": is_demo,
        "iat": utc_now(),
        "exp": exp
    }
    token = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
    return token, expires_in

def create_refresh_token(user_id: str, db: Session, family_id: Optional[str] = None) -> str:
    days = 30
    if settings.REFRESH_TTL.endswith("d"):
        days = int(settings.REFRESH_TTL[:-1])
    
    raw_token = secrets.token_urlsafe(48)
    token_h = hash_string(raw_token)
    fam_id = family_id or str(uuid.uuid4())
    exp = utc_now() + timedelta(days=days)

    db_token = RefreshToken(
        token_hash=token_h,
        family_id=fam_id,
        user_id=user_id,
        expires_at=exp,
        revoked_at=None
    )
    db.add(db_token)
    db.commit()
    return raw_token

@router.get("/nonce", response_model=NonceResponse)
def get_nonce(db: Session = Depends(get_db)):
    raw_nonce = secrets.token_urlsafe(32)
    nonce_h = hash_string(raw_nonce)
    exp = utc_now() + timedelta(minutes=5)
    
    db_nonce = Nonce(nonce_hash=nonce_h, expires_at=exp, used_at=None)
    db.add(db_nonce)
    db.commit()
    
    return NonceResponse(nonce=raw_nonce, expires_in_seconds=300)

@router.post("/google", response_model=TokenResponse)
def auth_google(req: GoogleAuthRequest, db: Session = Depends(get_db)):
    # 1. Verify Nonce
    nonce_h = hash_string(req.nonce)
    db_nonce = db.query(Nonce).filter(
        Nonce.nonce_hash == nonce_h,
        Nonce.used_at == None,
        Nonce.expires_at > utc_now()
    ).first()
    if not db_nonce:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired nonce")
    
    db_nonce.used_at = utc_now()
    db.commit()

    # 2. Verify Google ID token
    google_sub: str
    email: Optional[str] = None
    email_verified: bool = False
    is_demo_user: bool = False

    if settings.DEMO_MODE and req.id_token.startswith("demo_id_token_"):
        google_sub = req.id_token.replace("demo_id_token_", "demo_sub_")
        email = f"{google_sub}@ufo.local"
        email_verified = True
        is_demo_user = True
    else:
        try:
            from google.oauth2 import id_token as g_id_token
            from google.auth.transport import requests as g_requests

            id_info = g_id_token.verify_oauth2_token(
                req.id_token,
                g_requests.Request(),
                audience=settings.GOOGLE_WEB_CLIENT_ID
            )
            google_sub = id_info["sub"]
            email = id_info.get("email")
            email_verified = bool(id_info.get("email_verified", False))
        except Exception as e:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=f"Google token verification failed: {e}")

    # 3. Locate or create user by google_sub only
    user = db.query(User).filter(User.google_sub == google_sub).first()
    if not user:
        user = User(
            id=str(uuid.uuid4()),
            google_sub=google_sub,
            email=email,
            email_verified=email_verified,
            is_demo=is_demo_user,
            created_at=utc_now()
        )
        db.add(user)
        db.flush()

        usage = Usage(user_id=user.id, prevention_used=0, unstuck_used=0)
        db.add(usage)
        db.commit()
    else:
        if user.deleted_at is not None:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account has been deleted")
        # Update email/verified if changed
        if email and user.email != email:
            user.email = email
            user.email_verified = email_verified
            db.commit()

    access_tok, exp_in = create_access_token(user.id, is_demo=user.is_demo)
    refresh_tok = create_refresh_token(user.id, db)

    return TokenResponse(
        access_token=access_tok,
        refresh_token=refresh_tok,
        token_type="bearer",
        expires_in=exp_in,
        user_id=user.id,
        email=user.email,
        is_demo=user.is_demo
    )

@router.post("/refresh", response_model=TokenResponse)
def refresh_token_endpoint(req: RefreshRequest, db: Session = Depends(get_db)):
    tok_h = hash_string(req.refresh_token)
    db_token = db.query(RefreshToken).filter(RefreshToken.token_hash == tok_h).first()
    
    if not db_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    
    # Reuse detection: if revoked token is used, revoke entire family
    if db_token.revoked_at is not None:
        db.query(RefreshToken).filter(RefreshToken.family_id == db_token.family_id).update({
            RefreshToken.revoked_at: utc_now()
        })
        db.commit()
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token reuse detected; family revoked")
    
    if db_token.expires_at < utc_now():
        db_token.revoked_at = utc_now()
        db.commit()
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token expired")

    user = db.query(User).filter(User.id == db_token.user_id).first()
    if not user or user.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User inactive or deleted")

    # Rotate token
    db_token.revoked_at = utc_now()
    db.commit()

    new_access, exp_in = create_access_token(user.id, is_demo=user.is_demo)
    new_refresh = create_refresh_token(user.id, db, family_id=db_token.family_id)

    return TokenResponse(
        access_token=new_access,
        refresh_token=new_refresh,
        token_type="bearer",
        expires_in=exp_in,
        user_id=user.id,
        email=user.email,
        is_demo=user.is_demo
    )

@router.post("/logout")
def logout(req: RefreshRequest, db: Session = Depends(get_db)):
    tok_h = hash_string(req.refresh_token)
    db.query(RefreshToken).filter(RefreshToken.token_hash == tok_h).update({
        RefreshToken.revoked_at: utc_now()
    })
    db.commit()
    return {"message": "Logged out successfully"}

def get_current_user(
    auth: Optional[HTTPAuthorizationCredentials] = Depends(security),
    db: Session = Depends(get_db)
) -> User:
    if not auth or not auth.credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing authorization header")
    
    try:
        payload = jwt.decode(
            auth.credentials,
            settings.JWT_SECRET,
            algorithms=[settings.JWT_ALGORITHM]
        )
        user_id = payload.get("sub")
        is_demo = payload.get("is_demo", False)
    except jwt.PyJWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid access token")

    if not settings.DEMO_MODE and is_demo:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Demo access tokens disabled in production")

    user = db.query(User).filter(User.id == user_id).first()
    if not user or user.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found or deleted")
    
    return user

