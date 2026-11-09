import os
import sys
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent

class Settings(BaseSettings):
    UPSTREAM_BASE_URL: str = "https://temporary-snappy-maple-v2pe7o5-n4c5fhzfe-anon-delta-flame.vercel.app"
    UNSTUCK_BASE_URL: str = "https://temporary-rapid-walnut-8b8urq6-erhjg3k0f-anon-mu-indol.vercel.app"
    UPSTREAM_API_KEY: Optional[str] = None
    FREE_PREVENTION_LIMIT: int = 100
    FREE_UNSTUCK_LIMIT: int = 5
    UNSTUCK_EXTRACT_PER_DAY: int = 15
    ACCESS_TTL: str = "15m"
    REFRESH_TTL: str = "30d"
    JWT_SECRET: str = "insecure-demo-secret-key-replace-in-production-32chars"
    JWT_ALGORITHM: str = "HS256"
    GOOGLE_WEB_CLIENT_ID: Optional[str] = None
    PLAY_PACKAGE_NAME: str = "com.ufo.fraudobserver"
    PLAY_SERVICE_ACCOUNT: Optional[str] = None
    GEMINI_API_KEY: Optional[str] = None
    GEMINI_MODEL: str = "gemini-1.5-flash"
    DEMO_MODE: bool = False
    DEMO_ACCESS_CODE: str = "UFO2026D"
    DATABASE_URL: str = "sqlite:///./gateway.db"

    model_config = SettingsConfigDict(
        env_file=str(BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore"
    )

    def validate_production_secrets(self) -> None:
        if not self.DEMO_MODE:
            if "insecure" in self.JWT_SECRET or len(self.JWT_SECRET) < 32:
                raise ValueError("Production mode requires a strong, random JWT_SECRET with at least 32 characters.")

settings = Settings()
settings.validate_production_secrets()

