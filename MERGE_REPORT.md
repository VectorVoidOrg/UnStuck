# MERGE REPORT: Unstuck Feature Integration into UFO Main App

## Executive Summary
This report documents the architectural discovery, dependency alignment, theme harmonization, and single-module integration of the **"Unstuck"** failed transaction TAT & penalty resolution feature into the **"UFO" (UfoFraudObserver)** main Android application.

---

## Phase 0: Baseline Metrics

### Pre-Existing Failures Identified & Fixed
1. **Invalid macOS SDK Path in `local.properties`**:
   - `sdk.dir=/Users/nirbhaysinghparmar/Library/Android/sdk` caused build and Android Studio sync failures on Windows.
   - **Resolution:** Updated to local Windows SDK `C:\Users\Admin\AppData\Local\Android\Sdk`.
2. **Java 25 vs. Kotlin Compiler Incompatibility (`IllegalArgumentException: 25.0.4`)**:
   - Android Studio bundled runtime JBR 25.0.4 triggered crashes inside Kotlin 2.0.21's `JavaVersion.parse`.
   - **Resolution:** Re-pointed Gradle daemon to JetBrains Runtime 21.0.11 at `B:\UnStuck\tools\jdk-21`.
3. **Missing System `JAVA_HOME`**:
   - Resolved by setting user environment `JAVA_HOME=B:\UnStuck\tools\jdk-21`.

### Baseline Verification
- **Command:** `./gradlew assembleDebug`
- **Status:** **BUILD SUCCESSFUL** (22s)
- **Output Artifact:** `frontend/app/build/outputs/apk/debug/app-debug.apk`
- **Baseline APK Size:** **18,243,313 bytes (18.24 MB)**
- **minSdk:** 26
- **targetSdk:** 35
- **compileSdk:** 35
- **Android Gradle Plugin (AGP):** 8.7.2
- **Kotlin Version:** 2.0.21
- **Gradle Distribution:** 8.14.3
- **Compose BOM:** 2024.10.01

---

## Phase 1: Architectural Discovery & Comparison

| Characteristic | Main App (`ufo` / `frontend`) | Sub-App (`unstuck subparted`) | Harmonized Target |
| :--- | :--- | :--- | :--- |
| **Root Package / Namespace** | `com.ufo.fraudobserver` | `com.unstuck.app` | `com.ufo.fraudobserver.feature.unstuck` |
| **Application ID** | `com.ufo.fraudobserver` | `com.unstuck.app` | `com.ufo.fraudobserver` |
| **Architecture Pattern** | MVVM with Coroutines & StateFlow | ScreenState state-machine + service singletons | Clean MVVM (`UnstuckViewModel`) |
| **Navigation Mechanism** | Navigation-Compose + 4-tab `UfoBottomNav` | Hardcoded `when (currentScreen)` in Activity | 5th Destination in `UfoBottomNav` + nested composable sub-screens |
| **Dependency Injection** | Constructor Injection / `by viewModels()` | Direct singleton calls (`NetworkClient`) | Scoped ViewModel + Repository |
| **Dependency Management** | `gradle/libs.versions.toml` (Version Catalog) | Hardcoded strings in `build.gradle.kts` | Main app Version Catalog (`libs.versions.toml`) |
| **Networking Stack** | Retrofit 2.11 + OkHttp 4.12 + Gson | Direct OkHttp 4.12 + Gson | Reuse main app's OkHttp / Retrofit instance & Gson |
| **Base URL** | `http://10.0.2.2:8000/` | `http://10.0.2.2:8000` | Unified `http://10.0.2.2:8000/` |
| **Local Storage** | Room SQLite 2.6.1 + SharedPreferences | In-Memory `MutableMap<String, Case>` | Room entity persistence + in-memory fast cache |
| **Design System** | `UfoFraudObserverTheme` (Modern Indigo / Slate / Dark Hero) | `UnstuckTheme` (Beige / Camel / Sand) | **100% `UfoFraudObserverTheme`** (Beige theme deleted) |
| **Components & Icons** | Material3 + Custom SVG Vectors (`UfoIcons`) | Material3 + Material Icons Extended | Main app shared components & `UfoIcons` |
| **Manifest Strategy** | Production App with Accessibility & Services | Standalone single-Activity manifest | Embedded in `UfoMainActivity`, delete sub manifest |

---

## Sub-App Functional Inventory to Merge

1. **Services / Business Logic Engines**:
   - `DeadlineEngine`: RBI statutory TAT calculation (T+1 calendar days for UPI P2P/P2M).
   - `PenaltyEngine`: ₹100/day delay compensation calculation per RBI circular.
   - `EscalationEngine`: Multi-tier grievance routing (Level 1 Bank, Level 2 Nodal, Level 3 RBI Ombudsman).
   - `LetterEngine`: Markdown / plain-text legal complaint draft generator.
   - `FigureValidator`: Strict UTR, amount, and date format checks.

2. **Screens & Flows**:
   - **`UnstuckRoute` / Hub**: Primary tab screen with active cases, overdue counter, total penalty accrued, and quick "Check Failed UPI" action.
   - **Scanner / Input Screen**: Paste SMS text or enter UTR / amount.
   - **Review / Verification Screen**: Review parsed transaction details and confirm case opening.
   - **Case Dashboard Screen**: Live TAT countdown timer, daily penalty counter, responsible bank info, event timeline.
   - **Escalation & Letter Generator**: Generate and copy/share formal grievance letters.
   - **RBI Rights Screen**: Legal basis, reference to RBI Circular `DPSS.CO.PD No.629/02.01.014/2019-20`.

---

## Deviations & Strategic Decisions
1. **Single Module Strategy:** As specified in Phase 3.1, since the main app is a single application module (`:app`), the feature will reside under `com.ufo.fraudobserver.feature.unstuck`.
2. **Bottom Navigation Expansion:** `UfoBottomNav` currently has 4 items (`Home`, `Activity`, `Trusted`, `Settings`). Adding `Unstuck` (`Reversal`) makes exactly 5 items, staying within the standard Material3 navigation bar limit.
3. **Backend Contract Preservation:** The existing Python backend endpoints (`/extract`, `/rules`, `/cases`, etc.) and payload shapes are kept 100% intact.
