import os
from pathlib import Path
from dotenv import load_dotenv

# Load .env file if present
BASE_DIR = Path(__file__).resolve().parent
ROOT_DIR = BASE_DIR.parent
load_dotenv(ROOT_DIR / ".env")
load_dotenv(ROOT_DIR.parent / ".env")

class Settings:
    # Model & AI Configuration
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    GOOGLE_APPLICATION_CREDENTIALS: str = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "")
    MODEL_NAME: str = os.getenv("MODEL_NAME", "gemini-1.5-flash")
    
    # Upload limits
    MAX_UPLOAD_SIZE_MB: int = int(os.getenv("MAX_UPLOAD_SIZE_MB", "10"))
    MAX_UPLOAD_BYTES: int = MAX_UPLOAD_SIZE_MB * 1024 * 1024
    ALLOWED_EXTENSIONS: set = {".png", ".jpg", ".jpeg", ".webp", ".heic"}
    ALLOWED_MIME_TYPES: set = {
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/webp",
        "image/heic",
        "application/octet-stream"
    }
    
    # CORS
    ALLOWED_ORIGIN: str = os.getenv("ALLOWED_ORIGIN", "*")
    ALLOWED_ORIGINS: list = [origin.strip() for origin in os.getenv("ALLOWED_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000,*").split(",")]

    # Data paths
    DATA_DIR: Path = Path(os.getenv("DATA_DIR", str(BASE_DIR / "data")))
    RULES_FILE_PATH: Path = DATA_DIR / "rules.json"
    BANKS_FILE_PATH: Path = DATA_DIR / "banks.json"
    TEMPLATES_DIR: Path = DATA_DIR / "templates"
    STORAGE_DIR: Path = DATA_DIR / "storage"

    # Default compensation rate
    DEFAULT_COMPENSATION_RATE_PER_DAY: float = 100.0

settings = Settings()
try:
    settings.STORAGE_DIR.mkdir(parents=True, exist_ok=True)
except Exception:
    pass
