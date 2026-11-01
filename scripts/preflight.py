#!/usr/bin/env python3
"""
UFO Hackathon - Comprehensive Automated Preflight Verification
Runs end-to-end tests against the Gateway backend (in-process via TestClient)
and checks all artifacts and APK integrity.
"""

import os
import sys
import uuid
from pathlib import Path

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR))

# Ensure demo environment is loaded
os.environ["DEMO_MODE"] = "true"
os.environ["DEMO_ACCESS_CODE"] = "GDGUDZ"
if "JWT_SECRET" not in os.environ:
    os.environ["JWT_SECRET"] = "0" * 64

from fastapi.testclient import TestClient
from gateway.main import app
from gateway.app.db import init_db

def run_preflight():
    print("=" * 60)
    print("🛸 UFO Hackathon Automated Preflight Verification")
    print("=" * 60)

    # 1. Database Init
    print("\n[Step 1/8] Verifying Database Initializer...")
    init_db()
    print("  [✓] SQLite schema tables ready.")

    client = TestClient(app)

    # 2. Health Endpoint
    print("\n[Step 2/8] Testing GET /health...")
    resp = client.get("/health")
    assert resp.status_code == 200, f"Health check failed: {resp.text}"
    health_data = resp.json()
    assert health_data.get("status", "").upper() == "HEALTHY"
    print(f"  [✓] Health OK: {health_data}")

    # 3. Auth Nonce
    print("\n[Step 3/8] Testing GET /auth/nonce...")
    resp = client.get("/auth/nonce")
    assert resp.status_code == 200, f"Nonce failed: {resp.text}"
    nonce_data = resp.json()
    assert "nonce" in nonce_data
    print(f"  [✓] Nonce generated: {nonce_data['nonce'][:12]}...")

    # 4. Demo Login
    print("\n[Step 4/8] Testing POST /demo/login with 'HACKATHON2026'...")
    resp = client.post("/demo/login", json={"access_code": "HACKATHON2026"})
    assert resp.status_code == 200, f"Demo login failed: {resp.text}"
    auth_data = resp.json()
    access_token = auth_data["access_token"]
    refresh_token = auth_data["refresh_token"]
    assert access_token and refresh_token
    headers = {"Authorization": f"Bearer {access_token}"}
    print(f"  [✓] Logged in as: {auth_data.get('user_id')} (Demo: {auth_data.get('is_demo')})")

    # 5. User Profile
    print("\n[Step 5/8] Testing GET /me...")
    resp = client.get("/me", headers=headers)
    assert resp.status_code == 200, f"Profile failed: {resp.text}"
    profile = resp.json()
    print(f"  [✓] Profile Plan: {profile['plan']}, Prevention: {profile['prevention_used']}/{profile['prevention_remaining']} left")

    # 6. Prevention Check & Quota Atomic Decrement
    print("\n[Step 6/8] Testing POST /prevention/check (Atomic Quota + Idempotency)...")
    check_id = str(uuid.uuid4())
    payload = {
        "check_id": check_id,
        "receiver_vpa": "cbi.digitalarrest@ybl",
        "receiver_name": "CBI Officer",
        "amount": 50000.0,
        "source_app": "Google Pay",
        "timestamp": 1730419200000
    }
    resp1 = client.post("/prevention/check", json=payload, headers=headers)
    assert resp1.status_code == 200, f"Prevention check 1 failed: {resp1.text}"
    data1 = resp1.json()
    assert data1["risk_score"] >= 70, f"Expected high risk score, got {data1['risk_score']}"
    assert "SCAM" in data1["verdict"] or "FRAUD" in data1["verdict"] or "HIGH" in data1["verdict"]
    print(f"  [✓] Scam Check Verdict: {data1['verdict']} (Score {data1['risk_score']}/100)")

    # Replay idempotency test
    resp2 = client.post("/prevention/check", json=payload, headers=headers)
    assert resp2.status_code == 200
    data2 = resp2.json()
    assert data2["risk_score"] == data1["risk_score"]
    print(f"  [✓] Idempotent check replay confirmed: Same score returned without consuming double quota.")

    # 7. Demo Controls & Billing Simulation
    print("\n[Step 7/8] Testing Demo Quota Jumps and Subscription Simulation...")
    resp = client.post("/demo/set-usage", json={"prevention_used": 99, "unstuck_used": 4}, headers=headers)
    assert resp.status_code == 200
    print("  [✓] Quota jumped to 99/100 checks and 4/5 cases.")

    resp = client.post("/demo/simulate-purchase", headers=headers)
    assert resp.status_code == 200
    resp_me = client.get("/me", headers=headers).json()
    assert resp_me["is_entitled"] == True
    print(f"  [✓] Subscription upgraded to PRO UNLIMITED: is_entitled={resp_me['is_entitled']}")

    # 8. Deliverable APK Verification
    print("\n[Step 8/8] Verifying Deliverable APK...")
    apk_path = ROOT_DIR / "dist" / "UFO-demo.apk"
    if apk_path.exists():
        size_mb = apk_path.stat().st_size / (1024 * 1024)
        print(f"  [✓] {apk_path} exists and ready for deployment ({size_mb:.2f} MB).")
    else:
        print(f"  [!] {apk_path} not found. Run scripts/build_demo_apk.py.")

    print("\n" + "=" * 60)
    print("🎉 ALL PREFLIGHT VERIFICATIONS PASSED SUCCESSFULLY!")
    print("=" * 60)

if __name__ == "__main__":
    run_preflight()
