# 🛸 UFO (Unified Fraud Observer) - Demo Runbook

This runbook provides complete, step-by-step instructions to run, verify, and demonstrate UFO during the Hackathon presentation.

---

## ⚡ Quick Start (1-Minute Setup)

### 1. Initialize Backend & Configuration
Run the automated setup script to generate `.env` and initialize the SQLite database:
```bash
python scripts/demo_setup.py
```
*(Enter your Gemini API key when prompted, or leave blank to utilize automatic heuristic fallback).*

### 2. Start Gateway Backend Server
Launch the FastAPI gateway server:
```bash
python -m uvicorn gateway.main:app --host 0.0.0.0 --port 8000 --reload
```
*Health check URL: [http://localhost:8000/health](http://localhost:8000/health)*

### 3. Run Automated Preflight Verification
Verify the entire system end-to-end:
```bash
python scripts/preflight.py
```
*Executes database verification, health checks, authentication, atomic quota consumption, idempotent replay, subscription upgrades, and APK validation.*

---

## 📱 Mobile App Deployment & Verification

### Built Deliverable APK Location
The standalone signed demo APK is located at:
```
dist/UFO-demo.apk
```
- **Size:** ~2.78 MB (heavily optimized via R8 code & resource shrinking)
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 35 (Android 15 Vanilla Ice Cream)
- **Signing:** Pre-signed with Android debug certificate (immediately installable)

### Installing on Physical Phone or Emulator
Connect your device via USB with USB Debugging enabled, then run:
```bash
python scripts/install_on_phone.py
```
Or directly via ADB:
```bash
adb install -r -d dist/UFO-demo.apk
```

### Port Forwarding (For Physical USB Devices)
To allow your physical phone to communicate with the local gateway backend over USB:
```bash
adb reverse tcp:8000 tcp:8000
```
*(On Android emulators, `10.0.2.2:8000` is mapped automatically in the demo flavor).*

---

## 🛡️ Step-by-Step Demo Walkthrough

### 1. First Launch & Permissions Onboarding
1. Open **UFO Demo** from your device launcher.
2. Tap **Enable Accessibility** -> Toggle on **UFO Screen Observer**.
   > **Android 13+ Note:** If Accessibility is greyed out as a "Restricted Setting":
   > Tap *App Info* -> 3-dot menu at top right -> *Allow restricted settings*.
3. Tap **Grant Notification Access** -> Allow notification alerts.
4. Tap **🛡️ Activate UFO Protection Shield** to proceed to the main interface.

### 2. Navigation Architecture (Exact 3-Tab Interface)
The app features three streamlined tabs:
1. **PREVENTION** (Default landing tab): Real-time screen shield, system readiness cards, and live simulation triggers.
2. **UNSTUCK**: Screenshot upload, Gemini OCR extraction, UTR validation, RBI TAT calculator, and Ombudsman complaint generator.
3. **ACCOUNT**: Google Sign-In, Jury Fast-Track login, quota trackers, and Demo Tools.

### 3. Testing Prevention (Truecaller-Style Heads-Up Alert)
#### Method A: Built-in Live Simulator
1. On the **Prevention** tab, scroll to **Live Test & Demonstration**.
2. Tap **Scam** (`cbi.digitalarrest@ybl` - ₹50,000).
3. **Result:** An instant Truecaller-sized high-priority Heads-Up Notification pops up at the top:
   - **Score:** 95/100 (HIGH RISK SCAM)
   - **Reasons:** High-risk impersonation keyword, collect trap
   - **Actions:** Safe Dismiss / View Details

#### Method B: Realistic UPI Payment Screen
1. Go to the **Account** tab -> Tap **Demo Tools ⚙️** -> **Launch Simulated Payment App Screen 🚀**.
2. Switch between **Safe Merchant**, **Suspicious Lucky Draw**, and **Digital Arrest Scam**.
3. Observe UFO's Background Accessibility Service detecting the VPA and popping the scam intervention *before* PIN entry.

### 4. Testing Unstuck (Failed Payment Ombudsman Resolution)
1. Switch to the **Unstuck** tab.
2. Select a failed transaction screenshot (or use one of the samples).
3. The gateway analyzes the image via Gemini (or offline heuristic fallback) and extracts:
   - Date & Time
   - 12-digit UTR Number
   - Amount
   - Bank Name & Status
4. Review extracted fields -> Tap **Generate Formal Ombudsman Complaint**.
5. View deterministic complaint letter referencing RBI Ombudsman Scheme 2021 with auto-calculated ₹100/day penalty interest.
6. Tap **Share / Email Complaint** to copy or dispatch.

### 5. Testing Account, Quota Limits & Paywall
1. Switch to the **Account** tab.
2. Under *Jury Demo Fast-Track*, tap **Continue as Jury Demo** (Code: `HACKATHON2026`).
3. View current plan: Free Tier (100 checks, 5 Unstuck cases).
4. Tap **Demo Tools ⚙️**:
   - Tap **Jump 99/100 Checks** -> Quota immediately jumps to 99 used. Performing 1 more check hits HTTP 402 and prompts the paywall bottom sheet.
   - Tap **Simulate Pro Sub** -> Upgrades to PRO UNLIMITED (`is_entitled = true`).
   - Tap **Reset Account Usage** -> Restores fresh state.

---

## 🧹 Teardown & Reset
To wipe the temporary database and return the environment to fresh:
```bash
python scripts/demo_teardown.py
```

