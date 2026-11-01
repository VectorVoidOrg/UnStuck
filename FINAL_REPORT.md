# 🛸 UFO (Unified Fraud Observer) - Final Hackathon Engineering Report

---

## Executive Summary
This report summarizes the complete refactoring, backend engineering, and client-side implementation of **UFO (Unified Fraud Observer)** for the hackathon release. The codebase has been transitioned into a production-grade, highly optimized Android + FastAPI architecture that features:
1. Exactly **THREE bottom-nav destinations**: `PREVENTION`, `UNSTUCK`, `ACCOUNT` (starts on Prevention).
2. A standalone **Gateway Backend** in `gateway/` (FastAPI + SQLAlchemy) providing authentication, transactional atomic quota enforcement, Gemini multimodal OCR extraction, Google Play billing verification, and upstream ML proxying with resilience fallbacks.
3. Android client implementation with Truecaller-style high-priority heads-up interventions, zero privacy leakage, hardware Keystore AEAD encryption, and a dedicated `demo` flavor.
4. Deliverable signed APK at `dist/UFO-demo.apk` (2.78 MB, zero Gradle failures, 100% unit tests passing).

---

## Phase 0: Discovery & Contract Alignment
- Analyzed upstream ML backend contract (`/predict`) and documented request/response mapping in `BACKEND_CONTRACT.md`.
- Detected that the upstream ngrok tunnel (`https://capacity-property-vegan.ngrok-free.dev`) was offline (HTTP 404). Built an automatic local heuristic rule-engine fallback in `gateway/app/prevention.py` referencing the co-located `backend/` engine, guaranteeing zero demo downtime.
- Verified test scenarios:
  - `grocery.store@paytm` (₹120) $\rightarrow$ 0% Score (LIKELY_SAFE)
  - `swiggy@icici` (₹250) $\rightarrow$ 0% Score (LIKELY_SAFE)
  - `stranger99@oksbi` (₹4,999) $\rightarrow$ 46% Score (BE_CAREFUL)
  - `cbi.digitalarrest@ybl` (₹50,000) $\rightarrow$ 95% Score (LIKELY_SCAM)

---

## Phase 1: Cleanup & Dead Code Removal
To meet the strict hackathon constraints of a clean, focused, and minimal app, all non-essential modules, permissions, and dependencies were removed:
- **Permissions Removed from Manifest:**
  - `READ_PHONE_STATE`
  - `READ_CALL_LOG`
  - `SYSTEM_ALERT_WINDOW` (unused overlay permission)
  - Notification Listener permission
- **Components & Services Deleted:**
  - `CallStateMonitor.kt` (live vishing telephony monitor)
  - `UfoNotificationService.kt` (system-wide notification listener)
  - `UpiIntentInterceptorActivity.kt`
  - `FraudAlertDetailScreen.kt`, `CommunityReportScreen.kt`, `WhitelistScreen.kt`
  - `WhitelistedVpaDao.kt`, `WhitelistedVpaEntity.kt`, `SessionWhitelist.kt`
  - `BlacklistSyncWorker.kt`
- **Bottom Navigation Simplification:**
  - Reduced from 5 legacy items down to exactly three: `PREVENTION`, `UNSTUCK`, `ACCOUNT`.

---

## Phase 2: Gateway Backend (`gateway/`)
Implemented a standalone, asynchronous FastAPI backend service:
- **`gateway/app/config.py`:** Pydantic settings loading `.env` with production safeguards (validates secret length, prevents placeholder secrets when `DEMO_MODE=false`).
- **`gateway/app/db.py` & `models.py`:** SQLAlchemy engine supporting SQLite (local demo) and PostgreSQL. Schemas: `users`, `usage`, `check_events`, `refresh_tokens`, `subscriptions`, `nonces`.
- **`gateway/app/auth.py`:** Cryptographic 5-minute nonces, Google OAuth token verification, HS256 access tokens (15m), and rotating refresh tokens (30d) with family reuse revocation.
- **`gateway/app/quota.py`:** Concurrency-safe atomic conditional SQL updates (`UPDATE usage SET prevention_used = prevention_used + 1 ... WHERE prevention_used < :limit`), ensuring zero race conditions and returning `HTTP 402` on exhaustion.
- **`gateway/app/prevention.py`:** Proxy to upstream `/predict` with a 400ms retry, automatic local rule engine fallback, and 502 error quota release.
- **`gateway/app/unstuck.py`:** Image validation, EXIF stripping, Gemini Multimodal structured extraction with mathematical UTR validation.
- **`gateway/app/billing.py`:** Google Play Billing purchase token validation and HMAC SHA-256 account ID generation.
- **`gateway/app/demo.py`:** Mounted only when `DEMO_MODE=true`. Exposes `/demo/login`, `/demo/set-usage`, `/demo/simulate-purchase`, `/demo/simulate-cancel`, and `/demo/reset`.
- **`gateway/main.py`:** Main app with sensitive data log redactor filter and global error handling.

---

## Phase 3 & 4: Android Client Architecture
- **Design Tokens:** Strict adherence to UFO design system (`UfoPrimaryIndigo`, `UfoBg`, `UfoBorder`, `UfoSafeCheck`, `UfoDangerText`, `UfoWarnText`).
- **Real-Time Prevention:**
  - `UfoAccessibilityService`: Strictly restricted to supported UPI apps (`com.google.android.apps.nbu.paisa.user`, `net.one97.paytm`, `com.phonepe.app`, `in.org.npci.upiapp`, `com.dreamplug.androidapp`, `in.amazon.mShop.android.shopping`, plus the demo package).
  - Skips password and PIN entry screens immediately.
  - Debounced at 300ms, deduped per recipient/amount for 10 seconds.
- **Intervention Notification:**
  - `InterventionNotifier`: High-importance `IMPORTANCE_HIGH` heads-up notification styled like Truecaller.
  - Shows receiver name, VPA, amount, risk score, verdict, and specific triggers.
  - Set to `VISIBILITY_PRIVATE` so payment amounts never display on lockscreens.
- **Secure Token Storage:**
  - `SecureTokenStore`: Hardware-backed Android KeyStore AES-256 GCM authenticated encryption (AEAD) persisted via Jetpack DataStore Preferences.
- **Unstuck Resolution Feature:**
  - Integrated into the main NavHost and bottom bar.
  - Downscales screenshots to ~1600px and strips EXIF metadata prior to gateway upload.
  - Deterministic Ombudsman complaint generator citing RBI Ombudsman Scheme 2021 with auto-calculated ₹100/day delay compensation.
- **Demo Flavor (`src/demo`):**
  - "Continue as Jury Demo" (`POST /demo/login` with code `HACKATHON2026`).
  - Demo controls bottom sheet for jumping quotas (99/100 checks, 4/5 cases) and toggling Pro subscriptions.
  - Realistic UPI Payment Simulator Activity (`DemoPaymentActivity`) with instant preset switching.

---

## Phase 5: Verification & Benchmarks

| Metric / Check | Value / Result |
|---|---|
| **Deliverable APK Path** | `dist/UFO-demo.apk` |
| **APK Size** | **2.78 MB** (reduced from 21 MB) |
| **Min SDK / Target SDK** | Min SDK 26 (Android 8.0) / Target SDK 35 (Android 15) |
| **Kotlin / Gradle Compatibility** | Kotlin 2.0.21 / AGP 8.8.2 / Gradle 8.14.3 / Java 21 |
| **assembleDemoRelease Build** | **SUCCESS** (24s build time) |
| **testDemoDebugUnitTest** | **SUCCESS** (All test suites passed) |
| **Automated Preflight (`preflight.py`)** | **8/8 Steps PASSED** |

---

## Deliverables Summary
1. **Working Signed APK:** [`dist/UFO-demo.apk`](file:///b:/UnStuck/dist/UFO-demo.apk)
2. **Demo Runbook:** [`DEMO_RUNBOOK.md`](file:///b:/UnStuck/DEMO_RUNBOOK.md)
3. **Timed Presentation Script:** [`DEMO_SCRIPT.md`](file:///b:/UnStuck/DEMO_SCRIPT.md)
4. **Backend Architecture Specs:** [`BACKEND_REQUIREMENTS.md`](file:///b:/UnStuck/BACKEND_REQUIREMENTS.md)
5. **Backend Upstream Contract:** [`BACKEND_CONTRACT.md`](file:///b:/UnStuck/BACKEND_CONTRACT.md)
6. **Automation Scripts:**
   - [`scripts/demo_setup.py`](file:///b:/UnStuck/scripts/demo_setup.py)
   - [`scripts/build_demo_apk.py`](file:///b:/UnStuck/scripts/build_demo_apk.py)
   - [`scripts/install_on_phone.py`](file:///b:/UnStuck/scripts/install_on_phone.py)
   - [`scripts/preflight.py`](file:///b:/UnStuck/scripts/preflight.py)
   - [`scripts/demo_teardown.py`](file:///b:/UnStuck/scripts/demo_teardown.py)

