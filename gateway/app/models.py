import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column,
    String,
    Boolean,
    Integer,
    DateTime,
    ForeignKey,
    Text,
    Index,
    UniqueConstraint
)
from sqlalchemy.orm import relationship
from gateway.app.db import Base

def utc_now() -> datetime:
    return datetime.now(timezone.utc)

class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    google_sub = Column(String(128), unique=True, index=True, nullable=True)
    email = Column(String(255), nullable=True)
    email_verified = Column(Boolean, default=False, nullable=False)
    is_demo = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    usage = relationship("Usage", back_populates="user", uselist=False, cascade="all, delete-orphan")
    check_events = relationship("CheckEvent", back_populates="user", cascade="all, delete-orphan")
    refresh_tokens = relationship("RefreshToken", back_populates="user", cascade="all, delete-orphan")
    subscriptions = relationship("Subscription", back_populates="user", cascade="all, delete-orphan")

class Usage(Base):
    __tablename__ = "usage"

    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    prevention_used = Column(Integer, default=0, nullable=False)
    unstuck_used = Column(Integer, default=0, nullable=False)

    user = relationship("User", back_populates="usage")

class CheckEvent(Base):
    __tablename__ = "check_events"

    id = Column(String(64), primary_key=True)  # check_id or case_id
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    kind = Column(String(32), nullable=False)   # "prevention" or "unstuck"
    outcome = Column(Text, nullable=True)       # Serialized JSON outcome
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    user = relationship("User", back_populates="check_events")

class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    token_hash = Column(String(64), nullable=False, index=True)
    family_id = Column(String(64), nullable=False, index=True)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    revoked_at = Column(DateTime(timezone=True), nullable=True)

    user = relationship("User", back_populates="refresh_tokens")

class Subscription(Base):
    __tablename__ = "subscriptions"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    purchase_token_hash = Column(String(64), unique=True, index=True, nullable=False)
    product_id = Column(String(128), nullable=False)
    state = Column(String(32), nullable=False)  # ACTIVE, IN_GRACE_PERIOD, CANCELED, EXPIRED, ON_HOLD, PAUSED
    expires_at = Column(DateTime(timezone=True), nullable=True)
    acknowledged = Column(Boolean, default=False, nullable=False)
    last_verified_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    user = relationship("User", back_populates="subscriptions")

class Nonce(Base):
    __tablename__ = "nonces"

    id = Column(Integer, primary_key=True, autoincrement=True)
    nonce_hash = Column(String(64), unique=True, index=True, nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    used_at = Column(DateTime(timezone=True), nullable=True)

