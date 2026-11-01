# 🛰️ UFO Backend Architecture & Integration Requirements

This document specifies the technical architecture, database schemas, endpoint contracts, quota mechanics, and deployment expectations for the UFO Gateway Backend (`gateway/`) and Upstream Model Service.

---

## 1. System Overview

```
                               ┌──────────────────────────────────────────┐
                               │       UFO Android Client (Jetpack)       │
                               │  - Prevention: Accessibility Screen Obs  │
                               │  - Unstuck: Gemini OCR & Resolution      │
                               │  - Account: Hardware Keystore AEAD Auth  │
                               └────────────────────┬─────────────────────┘
                                                    │
                                                    ▼
                               ┌──────────────────────────────────────────┐
                               │          UFO Gateway (FastAPI)           │
                               │  - JWT Auth + Nonce + Refresh Rotation   │
                               │  - Atomic Transactional Quota Enforcer   │
                               │  - Gemini Multimodal Extraction Layer    │
                               │  - Google Play Billing Verification      │
                               │  - In-Memory / SQLite / PostgreSQL DB    │
                               └──────────────┬───────────────────────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
     ┌─────────────────────────────────┐             ┌─────────────────────────────────┐
     │ Upstream ML Model (/predict)    │             │ Google Gemini API               │
     │ - Real-time fraud classification│             │ - Multimodal OCR screenshot     │
     │ - Target latency: < 400ms       │             │   parsing & structured JSON     │
     └─────────────────────────────────┘             └─────────────────────────────────┘
```

---

## 2. API Endpoints Specification

### 2.1 Prevention Verification (`POST /prevention/check`)
- **Headers:** `Authorization: Bearer <jwt>`, `ngrok-skip-browser-warning: true`
- **Request Body:**
  ```json
  {
    "check_id": "c1387d7b-9441-47c0-bf38-51fbbbe1e43e",
    "receiver_vpa": "cbi.digitalarrest@ybl",
    "receiver_name": "CBI Officer",
    "amount": 50000.0,
    "source_app": "Google Pay",
    "timestamp": 1730419200000
  }
  ```
- **Response (`200 OK`):**
  ```json
  {
    "check_id": "c1387d7b-9441-47c0-bf38-51fbbbe1e43e",
    "risk_score": 95,
    "verdict": "LIKELY_SCAM",
    "reasons": [
      "High-risk impersonation keyword: cbi",
      "Payment collect trap heuristic triggered"
    ],
    "remaining_free": 99,
    "is_partial_check": false
  }
  ```
- **Error Codes:**
  - `401 Unauthorized`: Token missing or expired.
  - `402 Payment Required`: Free quota exhausted (`prevention_used >= 100`) and user not entitled.
  - `502 Bad Gateway`: Upstream model unreachable (atomically refunds reserved quota unit).

### 2.2 Unstuck Extraction (`POST /unstuck/extract`)
- **Headers:** `Authorization: Bearer <jwt>`, `Content-Type: multipart/form-data`
- **Form Fields:** `image: UploadFile` (JPEG/PNG/WebP, validated max 10MB)
- **Response (`200 OK`):**
  ```json
  {
    "date": "2026-10-31",
    "time": "14:30:00",
    "utr": "430512849102",
    "amount": 4999.0,
    "bank": "State Bank of India",
    "status": "FAILED",
    "failure_reason": "Debit successful, credited to beneficiary pending",
    "remaining_unstuck": 4
  }
  ```

### 2.3 Authentication (`/auth/*`)
- `GET /auth/nonce`: Returns a 5-minute cryptographic nonce for Google Credential Manager.
- `POST /auth/google`: Accepts `{ "id_token": "...", "nonce": "..." }`, validates Google signature, creates/fetches user, issues access token (15m TTL) and refresh token (30d TTL).
- `POST /auth/refresh`: Accepts `{ "refresh_token": "..." }`. Implements refresh token rotation with family reuse revocation.
- `POST /auth/logout`: Revokes token family.

### 2.4 Profile & Billing (`/me`, `/billing/*`)
- `GET /me`: Returns identity, active plan, entitlement status, and live usage counters.
- `DELETE /account`: Purges user record, check events, usage metrics, and active tokens (Right to be Forgotten).
- `GET /billing/account-id`: Generates HMAC SHA-256 hash of user ID for Play Billing obfuscated account ID.
- `POST /billing/verify`: Validates Google Play purchase token and activates `ufo_pro_annual` or `ufo_pro_monthly` subscription.

---

## 3. Database Schema Requirements

### `users`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY | Unique user ID |
| `google_sub` | VARCHAR(128) | UNIQUE, NULLABLE | Google Subject identifier |
| `email` | VARCHAR(255) | NULLABLE | Verified user email |
| `email_verified` | BOOLEAN | DEFAULT FALSE | Email verification status |
| `is_demo` | BOOLEAN | DEFAULT FALSE | Flag indicating jury demo user |
| `created_at` | DATETIME | NOT NULL | Registration timestamp (UTC) |

### `usage`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `user_id` | VARCHAR(64) | PRIMARY KEY, FK(users.id) | One-to-one user mapping |
| `prevention_used` | INTEGER | NOT NULL DEFAULT 0 | Consumed Prevention checks |
| `unstuck_used` | INTEGER | NOT NULL DEFAULT 0 | Consumed Unstuck resolutions |
| `updated_at` | DATETIME | NOT NULL | Last quota decrement timestamp |

### `check_events`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `check_id` | VARCHAR(64) | PRIMARY KEY | Client-generated idempotent UUID |
| `user_id` | VARCHAR(64) | FK(users.id) | Owning user |
| `feature` | VARCHAR(32) | NOT NULL | `prevention` or `unstuck` |
| `vpa_hash` | VARCHAR(64) | NULLABLE | SHA-256 hash of evaluated VPA |
| `amount` | FLOAT | DEFAULT 0.0 | Transaction amount |
| `risk_score` | INTEGER | DEFAULT 0 | Evaluated risk score (0-100) |
| `verdict` | VARCHAR(32) | DEFAULT 'SAFE' | Evaluated verdict |
| `created_at` | DATETIME | NOT NULL | Creation timestamp (UTC) |

### `subscriptions`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Subscription row ID |
| `user_id` | VARCHAR(64) | FK(users.id) | Subscribed user |
| `product_id` | VARCHAR(64) | NOT NULL | SKU (e.g. `ufo_pro_annual`) |
| `purchase_token` | VARCHAR(512)| UNIQUE | Google Play purchase token |
| `state` | VARCHAR(32) | NOT NULL | `ACTIVE`, `IN_GRACE_PERIOD`, `CANCELED`, `EXPIRED` |
| `expires_at` | DATETIME | NULLABLE | Entitlement expiration timestamp |
| `created_at` | DATETIME | NOT NULL | Purchase timestamp |

---

## 4. Quota Concurrency & Zero Race Condition Enforcement

Quota decrement MUST be executed as a single atomic SQL conditional update:
```sql
UPDATE usage 
SET prevention_used = prevention_used + 1, updated_at = :now 
WHERE user_id = :uid AND prevention_used < :limit
```
- If rows affected == 1: Quota successfully consumed.
- If rows affected == 0: Quota exhausted; transaction rolls back and returns `HTTP 402 Payment Required`.
- If upstream fails after quota decrement: `release_consumed()` decrements the usage counter back by 1 to prevent penalizing users for server-side failures.

