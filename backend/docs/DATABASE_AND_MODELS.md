# 🗄️ UFO Backend — Database Schema, Datasets & Model Training Guide

---

## 1. Database Engine Architecture (`backend/app/db/mongo.py`)

UFO implements a dual-mode persistence layer:
1. **Live MongoDB Mode (`UFO_FORCE_MONGO=true`):** Connects to MongoDB Atlas (`mongodb+srv://...`) or a local MongoDB daemon (`mongodb://localhost:27017`) using `pymongo` (`db = client["ufo_fraud_db"]`).
2. **Zero-Config Local & Serverless Persistence Mode (`UFO_FORCE_MONGO=false`):** Uses `LocalMongoDatabase` backed by `backend/data/ufo_local_db.json` (with automatic fallback to `/tmp/ufo_local_db.json` on read-only serverless containers like Vercel). Supports `find_one`, `find`, `.sort()`, `.limit()`, `insert_one`, `insert_many`, `update_one` (`$set`, `$inc`, `$addToSet`, `$push`, `upsert=True`), `count_documents`, and `delete_many`.

---

## 2. Complete Schema of All 10 Collections

### 2.1 `users` (User Trusted Payee History)
```json
{
  "user_id": "dhawal@okicici",
  "receivers": ["mom@oksbi", "grocery.store@paytm", "zomato@hdfcbank"]
}
```

### 2.2 `receivers` (Receiver Rolling Reputation Telemetry)
```json
{
  "receiver": "cbi.digitalarrest@ybl",
  "transactions": 32,
  "total_risk": 31.68,
  "flag_count": 28
}
```

### 2.3 `hall_of_shame` (301+ Blacklisted Fraudster UPI IDs)
```json
{
  "receiver": "cbi.digitalarrest@ybl",
  "avg_risk": 0.99,
  "flag_count": 28,
  "transactions": 32,
  "scam_type": "DIGITAL_ARREST_EXTORTION",
  "source": "I4C_SUSPECT_PATTERN_DATASET",
  "status": "BLACKLISTED"
}
```

### 2.4 `transactions` (Anonymized Sync Payment Logs)
```json
{
  "utr_number": "481920374651",
  "user_token": "<UPI_VPA_TOKEN_4D2A19BC01>",
  "receiver_token": "<UPI_VPA_TOKEN_9F8E7D6C5B>",
  "amount": 500.0,
  "risk": 0.01,
  "status": "SAFE",
  "decision_action": "ALLOW_PAYMENT",
  "async_investigation_id": null,
  "latency_ms": 4.21,
  "timestamp": "2026-10-31T12:39:23Z"
}
```

### 2.5 `npci_ledger` (Settled 12-Digit UTR Switch Ledger)
Used by Layer 4 (`verify_utr_in_ledger`) and Layer 5 (`policy_guardrail.py`) to verify receipt authenticity before issuing any refund.
```json
{
  "utr_number": "412345678901",
  "payer_vpa": "victim@okaxis",
  "payee_vpa": "merchant.fake@ybl",
  "amount": 2400.0,
  "status": "SETTLED",
  "verified_in_npci": true
}
```

### 2.6 `pii_token_vault` (Reversible HMAC Token Vault Metadata)
```json
{
  "token": "<AADHAAR_TOKEN_A970D619D6>",
  "pii_type": "AADHAAR",
  "masked_preview": "**********2345",
  "created_at": "2026-10-31T12:39:23Z"
}
```

### 2.7 `semantic_cache` (`<10ms` Scam Templates)
```json
{
  "template_id": "tpl_electricity_bill_02",
  "pattern_text": "dear consumer electricity power supply will be disconnected tonight 9:30 pm bill not updated call officer whatsapp apk",
  "verdict": {
    "is_scam": true,
    "scam_category": "FAKE_KYC",
    "confidence": 0.96,
    "recommended_action": "AUTO_REFUND",
    "recommended_refund_inr": 4500.0,
    "place_lien": true,
    "rbi_circular_citations": [
      "RBI/2017-18/15 Limited Liability Circular",
      "NPCI/UPI/OC-UDIR/2023 Real-Time Fraud Reversal"
    ],
    "reasoning_summary": "Matched known 'Electricity Disconnection Tonight' social engineering scam template."
  }
}
```

### 2.8 `rbi_guidelines` (RBI & NPCI Regulatory RAG Corpus)
Stores official regulatory circulars (`RBI-2017-18-15-CL6` Zero Liability, `RBI-2017-18-15-CL7` Limited Liability, `RBI-2017-18-15-CL10` 10-Day Shadow Reversal, `NPCI-UDIR-2023-01` Lien & UTR Verification, and `MHA-I4C-1930-SOP` Digital Arrest Golden Hour Lien).

### 2.9 `investigations` & `hitl_dead_letter_queue` & `audit_logs`
Store complete `InvestigationRecord` outputs, Human-In-The-Loop / `504` DLQ tickets (`HITL-...`), and immutable compliance audit trails.

---

## 3. Datasets Included in `backend/data/`

| File | Records | Purpose |
| :--- | :--- | :--- |
| `public_fraud_vpa_dataset.csv` | **300 VPAs** | 30 scam VPA prefixes across 7 Indian Cybercrime (I4C / 1930) scam types × 10 UPI bank handles (`@okaxis`, `@ybl`, `@paytm`, `@okicici`, `@oksbi`, `@axl`, `@ibl`, `@apl`, `@upi`, `@barodampay`). |
| `upi_fraud_training_data.csv` | **12,000 Txns** | Synthetic Indian UPI transactions with behavioral features (`amount`, `high_amount`, `fast_action`, `unusual_time`, `new_receiver`, `screen_share`, `active_call`, `collect_req`, `copy_pasted`, `device_changed`, `is_fraud`). |
| `ufo_slm_finetune_dataset.jsonl` | **100 Pairs** | Instruction-tuning JSONL dataset of PII-redacted UPI scam narratives paired with strict `AgentVerdictSchema` JSON targets. |

---

## 4. Model Training & Seeding Scripts (`backend/scripts/`)

1. **`python backend/scripts/init_db.py`**
   - Creates unique indexes on all collections and seeds initial `npci_ledger`, `rbi_guidelines`, `semantic_cache`, and baseline user history.
2. **`python backend/scripts/seed_fraudster_vpa_dataset.py`**
   - Generates `backend/data/public_fraud_vpa_dataset.csv` and upserts 300+ fraudster VPAs into `hall_of_shame`. Supports importing custom CSVs via `--csv path/to/file.csv`.
3. **`python backend/scripts/train_fraud_ml_model.py`**
   - Generates `upi_fraud_training_data.csv` (`12,000` samples) and trains class-weighted logistic/interaction ensemble weights saved to `backend/models/fraud_model_weights.json` (**Accuracy: `93.71%`, Precision: `77.05%`, Recall: `87.15%`, F1: `81.79%`**), plus `backend/fraud_model.pkl` (`RandomForestClassifier`) when `scikit-learn` is installed.
4. **`python backend/scripts/train_complexity_router.py`**
   - Calibrates and saves `backend/models/complexity_router.json` (`frontier_threshold = 0.55`).
5. **`python backend/scripts/finetune_slm_lora.py`**
   - Generates `ufo_slm_finetune_dataset.jsonl` (`--generate-only`) or runs 4-bit NF4 QLoRA fine-tuning on `Qwen/Qwen2.5-3B-Instruct` using `transformers`, `peft`, and `bitsandbytes`.
