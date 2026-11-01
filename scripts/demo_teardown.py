#!/usr/bin/env python3
"""
UFO Hackathon - Demo Teardown Script
Resets local test database and cleans temporary files.
"""

import os
import sys
from pathlib import Path

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT_DIR = Path(__file__).resolve().parent.parent
GATEWAY_DB = ROOT_DIR / "gateway" / "gateway_demo.db"
ROOT_DB = ROOT_DIR / "gateway_demo.db"

def teardown():
    print("=" * 60)
    print("🛸 UFO Hackathon Demo Teardown")
    print("=" * 60)

    for db in [GATEWAY_DB, ROOT_DB]:
        if db.exists():
            print(f"[+] Removing demo database {db}...")
            try:
                db.unlink()
                print(f"[✓] Deleted {db.name}")
            except Exception as e:
                print(f"[!] Could not remove {db}: {e}")

    print("\n[✓] Teardown complete. Workspace reset for fresh run.")

if __name__ == "__main__":
    teardown()
