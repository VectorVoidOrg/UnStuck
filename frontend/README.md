# 🛸 UFO (Unified Fraud Observer) - Android Frontend

High-performance, zero-latency Kotlin Jetpack Compose Android client for real-time UPI fraud interception prior to NPCI PIN entry.

---

## 🌟 Architecture & Implemented Modules

### 1. Ingestion & Data Extraction (`services/` & `parser/`)
- **`UfoNotificationService` (`NotificationListenerService`)**:
  - Filters: Google Pay, PhonePe, Paytm, BHIM, Amazon Pay.
  - 5000ms deduplication cooldown via `LruCache` on hash of `(PackageName + Content)`.
  - Auto-rebinds on listener disconnect to defeat OEM killers.
- **`UfoAccessibilityService` (`AccessibilityService`)**:
  - Listens strictly to `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED`.
  - 300ms debounce timer on content change to eliminate CPU starvation and UI stuttering.
  - Safe BFS node tree traversal ignoring hidden views (`isVisibleToUser == false`).
  - Zero View ID dependencies (uses resilient regex patterns against production obfuscation).
  - 15-second memory cache to retain transaction context across `FLAG_SECURE` blinding (NPCI PIN pad).
- **`UpiIntentInterceptorActivity`**:
  - Captures `upi://pay` deep links and QR codes.
  - Evaluates risk before launching payment chooser.
- **`PayloadParser`**:
  - VPA pattern: `[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}`
  - Amount pattern: `(?:₹|Rs\.?|INR)\s?(\d+(?:,\d+)*(?:\.\d{1,2})?)`
  - Collect Request keyword detection.

### 2. Multi-Layered Risk Evaluation Engine (`engine/`)
- **Layer 1: Local Bloom Filter (`BloomFilterEngine`)**:
  - Capacity: 100,000 entries, $p = 0.001$, $k = 10$ hash functions.
  - Double hashing with pure Kotlin `MurmurHash3`.
  - Sub-millisecond $O(1)$ offline lookup backed by binary storage.
- **Layer 2: Heuristics & Brand Spoofing (`HeuristicEngine`)**:
  - Levenshtein edit distance check against high-profile entities (`pmcares`, `irctc`, `bescom`, `sbi`, etc.).
  - Deceptive keywords evaluation (`refund`, `cashback`, `prize`, `lottery`, `verify`, `kyc`, etc.).
  - Collect-to-Receive trap detection (+75 risk).
- **Layer 3: Live Voice Call Telemetry (`CallStateMonitor`)**:
  - Detects active phone calls (`CALL_STATE_OFFHOOK`) using `TelephonyCallback` / `PhoneStateListener`.
  - Multiplier penalty (+50 risk) to block active vishing scams.
- **Session & Permanent Whitelist (`SessionWhitelist` & `Room DB`)**:
  - 5-minute session suppression when user dismisses alert.
  - Permanent trusted contact whitelist.

### 3. Non-Blocking Heads-Up Intervention UI (`intervention/`)
- Channel: `ufo_critical_alerts` (`IMPORTANCE_HIGH`, `USAGE_ALARM` sound, high vibration).
- Custom `RemoteViews` (`notification_fraud_notch.xml`) in dark crimson `#2B0909` with bright alert border.
- **Strictly NO `SYSTEM_ALERT_WINDOW` overlays** to prevent Android 12+ Untrusted Touch blocking and host app crashes.
- "ABORT PAYMENT" pulls user out of UPI app to UFO Safety Review.
- "DISMISS" adds VPA to 5-minute cooldown.

### 4. Persistence & Sync (`data/` & `sync/`)
- Room SQLite: `FlaggedTransactionEntity` and `WhitelistedVpaEntity`.
- `BlacklistSyncWorker`: WorkManager periodic sync (every 6 hours) calling `GET /api/v1/blacklist/delta`.
- Community Reporting: `POST /api/v1/report` flips local Bloom filter bits immediately.

### 5. Onboarding & Android 13+ Sideloading Helper (`ui/onboarding/`)
- Detects Android API $\ge 33$.
- Guides users to unlock Restricted Settings in App Info (3-dot menu) before opening Accessibility Settings.
- Verifies all critical permissions before entering Dashboard.
