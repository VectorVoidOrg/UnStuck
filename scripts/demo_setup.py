#!/usr/bin/env python3
"""
UFO Hackathon - Demo Setup Script
Initializes gateway configuration, database, and verifies services.
"""

import os
import sys
import secrets
import subprocess
import time
from pathlib import Path

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT_DIR = Path(__file__).resolve().parent.parent
GATEWAY_DIR = ROOT_DIR / "gateway"
ENV_FILE = GATEWAY_DIR / ".env"
ENV_EXAMPLE = GATEWAY_DIR / ".env.example"

def setup_env():
    print("=" * 60)
    print("🛸 UFO Hackathon Demo Setup")
    print("=" * 60)

    if not ENV_FILE.exists():
        print("[+] Creating gateway/.env from template...")
        jwt_secret = secrets.token_hex(32)
        demo_code = "HACKATHON2026"
        gemini_key = os.environ.get("GEMINI_API_KEY", "")

        if not gemini_key:
            print("[?] GEMINI_API_KEY not found in environment.")
            user_input = input("    Enter your GEMINI_API_KEY (leave empty to use heuristic fallback): ").strip()
            if user_input:
                gemini_key = user_input

        env_content = f"""# UFO Gateway Environment
DEMO_MODE=true
DEMO_ACCESS_CODE={demo_code}
JWT_SECRET={jwt_secret}
DATABASE_URL=sqlite:///./gateway_demo.db
GEMINI_API_KEY={gemini_key}
UPSTREAM_PREVENTION_URL=https://temporary-snappy-maple-v2pe7o5-n4c5fhzfe-anon-delta-flame.vercel.app
LOG_LEVEL=INFO
"""
        ENV_FILE.write_text(env_content, encoding="utf-8")
        print(f"[✓] Created {ENV_FILE}")
    else:
        print(f"[✓] Found existing {ENV_FILE}")

def init_database():
    print("[+] Initializing Gateway SQLite Database...")
    sys.path.insert(0, str(ROOT_DIR))
    try:
        from gateway.app.db import init_db
        init_db()
        print("[✓] Database initialized successfully.")
    except Exception as e:
        print(f"[!] Database initialization failed: {e}")
        sys.exit(1)

def main():
    setup_env()
    init_database()
    print("\n[✓] Demo Setup complete!")
    print("\nTo start the gateway server:")
    print("    python -m uvicorn gateway.main:app --host 0.0.0.0 --port 8000 --reload")
    print("\nTo run the automated preflight check:")
    print("    python scripts/preflight.py\n")

if __name__ == "__main__":
    main()
