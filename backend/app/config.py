import os
from datetime import date, datetime
from functools import lru_cache
from pathlib import Path
from zoneinfo import ZoneInfo
from dotenv import load_dotenv
from pydantic import BaseModel, Field

# Load environment variables
load_dotenv()

IST = ZoneInfo("Asia/Kolkata")


class AppError(Exception):
    """Domain exception for expected application errors with specific codes and status."""

    def __init__(self, code: str, message: str, status: int = 422):
        self.code = code
        self.message = message
        self.status = status
        super().__init__(message)


def today_ist() -> date:
    """Returns today's date in IST."""
    return datetime.now(IST).date()


class Settings(BaseModel):
    allowed_origins: list[str] = Field(
        default_factory=lambda: [
            "http://localhost:8444",
            "http://127.0.0.1:8444",
            "http://localhost:3000",
            "http://127.0.0.1:3000",
        ]
    )
    data_dir: Path = Field(
        default_factory=lambda: Path(__file__).resolve().parent / "data"
    )
    persist_path: str | None = None
    ombudsman_wait_days: int = 30  # VERIFY: current RBI scheme
    default_day_type: str = "calendar"


@lru_cache()
def get_settings() -> Settings:
    origins_raw = os.getenv(
        "ALLOWED_ORIGINS",
        "http://localhost:8444,http://127.0.0.1:8444,http://localhost:3000,http://127.0.0.1:3000",
    )
    allowed_origins = [o.strip() for o in origins_raw.split(",") if o.strip()]
    if not allowed_origins:
        allowed_origins = [
            "http://localhost:8444",
            "http://127.0.0.1:8444",
            "http://localhost:3000",
            "http://127.0.0.1:3000",
        ]

    custom_data_dir = os.getenv("DATA_DIR")
    data_dir = (
        Path(custom_data_dir).resolve()
        if custom_data_dir
        else (Path(__file__).resolve().parent / "data")
    )

    persist_path = os.getenv("PERSIST_PATH")
    if persist_path and not persist_path.strip():
        persist_path = None

    ombudsman_wait_days = int(os.getenv("OMBUDSMAN_WAIT_DAYS", "30"))
    default_day_type = os.getenv("DEFAULT_DAY_TYPE", "calendar")

    return Settings(
        allowed_origins=allowed_origins,
        data_dir=data_dir,
        persist_path=persist_path,
        ombudsman_wait_days=ombudsman_wait_days,
        default_day_type=default_day_type,
    )
