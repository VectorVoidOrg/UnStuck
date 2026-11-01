import os
from pathlib import Path
from dotenv import load_dotenv

# Load .env from project root or backend directory
ROOT_DIR = Path(__file__).resolve().parent.parent.parent
BACKEND_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = BACKEND_DIR / "models"
DATA_DIR = BACKEND_DIR / "data"

load_dotenv(ROOT_DIR / ".env")
load_dotenv(BACKEND_DIR / ".env")


class Settings:
    PROJECT_NAME: str = "UFO - Unified Fraud Observer"
    VERSION: str = "4.0.0"
    ENVIRONMENT: str = os.getenv("UFO_ENV", "development")

    # ---- Database (MongoDB Atlas + Local Fallback) ----
    MONGO_URI: str = os.getenv(
        "MONGO_URI",
        "mongodb+srv://dhawalmenaria28_db_user:dRc9IHVSxxT0VvJ8@cluster0.uxbjucn.mongodb.net/",
    )
    MONGO_DB_NAME: str = os.getenv("MONGO_DB_NAME", "ufo_fraud_db")
    REDIS_URL: str = os.getenv("REDIS_URL", "")  # Optional: Redis for velocity/cache/queue

    # ---- Event Queue (Kafka / PubSub / In-Memory Async) ----
    EVENT_QUEUE_BACKEND: str = os.getenv("EVENT_QUEUE_BACKEND", "memory")  # memory | redis | kafka | pubsub
    KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    KAFKA_TOPIC_FRAUD_EVENTS: str = os.getenv("KAFKA_TOPIC_FRAUD_EVENTS", "ufo.fraud.investigations")

    # ---- Layer 2: PII Token Vault Security ----
    PII_VAULT_SECRET_KEY: str = os.getenv("PII_VAULT_SECRET_KEY", "ufo-rbi-compliant-vault-key-2026")

    # ---- Layer 3: AI Models & API Keys ----
    # 1. Frontier LLM (Complex 20% multi-step disputes + RBI RAG)
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    GOOGLE_GENAI_USE_VERTEXAI: bool = os.getenv("GOOGLE_GENAI_USE_VERTEXAI", "false").lower() == "true"
    GOOGLE_CLOUD_PROJECT: str = os.getenv("GOOGLE_CLOUD_PROJECT", "")
    GOOGLE_CLOUD_LOCATION: str = os.getenv("GOOGLE_CLOUD_LOCATION", "asia-south1")
    FRONTIER_LLM_MODEL: str = os.getenv("FRONTIER_LLM_MODEL", os.getenv("GEMINI_MODEL", "gemini-2.5-flash"))

    # 2. Small Quantized Model - SLM (Routine 80% checks)
    # Supports: "ollama" (local quantized Qwen/Llama), "groq" (ultra-fast Llama-3-8B), "gemini_lite", or "local_hf"
    SLM_PROVIDER: str = os.getenv("SLM_PROVIDER", "auto")
    OLLAMA_BASE_URL: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    OLLAMA_SLM_MODEL: str = os.getenv("OLLAMA_SLM_MODEL", "qwen2.5:3b-instruct-q4_K_M")
    GROQ_API_KEY: str = os.getenv("GROQ_API_KEY", "")
    GROQ_SLM_MODEL: str = os.getenv("GROQ_SLM_MODEL", "llama-3.1-8b-instant")
    GEMINI_SLM_MODEL: str = os.getenv("GEMINI_SLM_MODEL", "gemini-2.5-flash-lite")
    LOCAL_HF_SLM_PATH: str = os.getenv("LOCAL_HF_SLM_PATH", str(MODELS_DIR / "ufo-slm-lora"))

    # 3. Embeddings / Semantic Cache (10ms lookup)
    SEMANTIC_CACHE_SIMILARITY_THRESHOLD: float = float(os.getenv("SEMANTIC_CACHE_SIMILARITY_THRESHOLD", "0.82"))

    # ---- Layer 4: NPCI & Bank Core Tools ----
    NPCI_SANDBOX_URL: str = os.getenv("NPCI_SANDBOX_URL", "https://sandbox.npci.org.in/udir/v1")
    NPCI_API_KEY: str = os.getenv("NPCI_API_KEY", "")
    NPCI_TIMEOUT_MS: int = int(os.getenv("NPCI_TIMEOUT_MS", "2500"))
    SIMULATE_BANK_504_ON_VPA: str = "timeout@upi"  # For testing Resilience #4 (504 -> DLQ)

    # ---- Layer 5: Resilience & Hard Guardrails (Step 5B) ----
    MAX_REASONING_LOOPS: int = int(os.getenv("MAX_REASONING_LOOPS", "3"))
    MAX_TOKEN_BUDGET_PER_REQUEST: int = int(os.getenv("MAX_TOKEN_BUDGET_PER_REQUEST", "4096"))
    GLOBAL_TOKEN_BUDGET_PER_MINUTE: int = int(os.getenv("GLOBAL_TOKEN_BUDGET_PER_MINUTE", "250000"))
    MAX_AUTO_REFUND_INR: float = float(os.getenv("MAX_AUTO_REFUND_INR", "5000.0"))  # Hard ₹5,000 policy cap
    MIN_AGENT_CONFIDENCE_FOR_AUTO_ACTION: float = float(os.getenv("MIN_AGENT_CONFIDENCE_FOR_AUTO_ACTION", "0.75"))
    SYNC_LATENCY_SLA_MS: float = float(os.getenv("SYNC_LATENCY_SLA_MS", "150.0"))


settings = Settings()
