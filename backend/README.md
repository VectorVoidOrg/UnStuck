# 🛸 UFO (Unified Fraud Observer) — Production-Grade 5-Layer Agentic Backend

Implements the complete **V4 Production-Grade Real-Time UPI Fraud & Scam Resolution Architecture** from `docs/Designing reliable agentic systems.pdf`.

---

## 🏗️ 5-Layer System Architecture

1. **Component 1 — API Gateway & Deterministic Rule Engine (`backend/app/layer1_gateway/`)**
   - **`<50ms` Sync Critical Path** (`Rule #1`: 95% of traffic never touches an LLM).
   - Evaluates **Hall of Shame Blacklist**, **Sliding-Window Velocity & Structuring Rules**, **Behavioral Telemetry** (New Receiver, High Amount, Rushed Action, Late Night, Active Phone Call, Screen-Sharing App, Copy-Pasted VPA, Collect-Request Trap), **Fast Tabular ML Score**, and **Receiver Reputation Score**.
2. **Component 2 — PII Redaction & Token Vault + Local/Edge OCR Masking (`backend/app/layer2_privacy/`)**
   - **Zero Raw PII to Cloud (RBI Compliant)**: Tokenizes Phone Numbers, Bank A/C, Aadhaar, PAN, IFSC, and UPI VPAs into reversible HMAC Vault Tokens (`<PHONE_TOKEN_...>`, `<AADHAAR_TOKEN_...>`, `<UPI_VPA_TOKEN_...>`) before any model sees the prompt.
   - **Resilience #1 (Local/Edge OCR Sanitizer)**: Detects and neutralizes adversarial **Prompt Injections** (`IGNORE ALL PREVIOUS INSTRUCTIONS`, etc.) embedded in fake payment screenshots.
3. **Component 3 — Fraud Investigation Agent Core (`backend/app/layer3_agent/`)**
   - **3.1 Semantic Cache (`<10ms`)**: Instant vector + hash matching against known UPI scam templates (Digital Arrest, Electricity Disconnection, OLX QR Collect Trap, AnyDesk Screen Share, Fake Receipt).
   - **3.2 Complexity Router**: Routes **80% of routine checks** to the **Small Quantized Model (SLM)** and **20% of complex multi-step disputes** to the **Frontier LLM + RBI RAG**.
   - **3.3 Small Quantized Model (SLM)**: Supports local quantized models via Ollama (`qwen2.5:3b-instruct-q4_K_M`), Groq (`llama-3.1-8b-instant`), local 4-bit QLoRA adapters, or calibrated local statistical inference.
   - **3.4 Frontier LLM + RBI RAG**: Grounds complex disputes in actual **RBI Circulars (`RBI/2017-18/15` Zero/Limited Liability, Shadow Reversal)**, **NPCI UDIR Lien rules**, and **MHA 1930 Golden Hour SOPs**.
4. **Component 4 — NPCI & Bank Core Tools (`backend/app/layer4_tools/`)**
   - Enforces **Strict JSON Schema** and **Idempotent Execution (`idempotency_key`)** for `verify_utr_in_ledger`, `place_npci_lien`, and `execute_auto_refund`.
5. **Component 5 — Resilience, Guardrails, Async Queue, HITL & Audit (`backend/app/layer5_resilience/`)**
   - **Step 4 Async Event Queue**: Decouples the `<150ms` user HTTP thread from the 2–4s Agentic reasoning loop.
   - **Resilience #2 (Token Budget Circuit Breaker)**: Hard cap of **max 3 reasoning loops** per request + festival spike token governor.
   - **Resilience #3 (Deterministic Policy Guardrail)**: Hard non-LLM rule sitting *after* the Agent that **strictly blocks auto-refunds exceeding ₹5,000** and blocks unverified/fake UTR receipts.
   - **Resilience #4 (HITL & Dead Letter Queue)**: Routes low-confidence verdicts (`<0.75`), `>₹5,000` refunds, and **NPCI/Bank 504 Gateway Timeouts** to human audit.

---

## 📂 Organized Backend Directory Structure

```text
backend/
├── app/
│   ├── config.py                    # Central settings & .env configuration
│   ├── schemas.py                   # Strict Pydantic v2 schemas across all 5 layers
│   ├── db/
│   │   └── mongo.py                 # MongoDB client + zero-config local JSON persistence fallback
│   ├── layer1_gateway/              # Component 1: <50ms Rule Engine, Velocity, Reputation, Fast ML
│   ├── layer2_privacy/              # Component 2: Reversible PII Token Vault & OCR Injection Shield
│   ├── layer3_agent/                # Component 3: Semantic Cache, Complexity Router, SLM, Frontier+RAG
│   ├── layer4_tools/                # Component 4: Idempotent NPCI Lien, Auto-Refund & UTR Ledger Tools
│   └── layer5_resilience/           # Component 5: Circuit Breaker, ₹5k Guardrail, Async Queue, HITL/DLQ
├── scripts/
│   ├── init_db.py                   # Initializes DB collections, indexes, RBI RAG & scam templates
│   ├── train_fraud_ml_model.py      # Trains Layer 1 Fast Tabular Fraud ML Model
│   ├── train_complexity_router.py   # Calibrates Layer 3 Complexity Router (80/20 split)
│   └── finetune_slm_lora.py         # 4-bit QLoRA fine-tuning script for Layer 3 Small Model (SLM)
├── tests/
│   └── test_ufo_architecture.py     # End-to-end verification suite across all 5 layers
└── main.py                          # FastAPI application entrypoint
```

---

## 🚀 Quickstart Commands

```bash
# 1. Initialize & seed the database (MongoDB or local persistent store)
python backend/scripts/init_db.py

# 2. Train Layer 1 Fast Tabular ML Model & Layer 3 Router
python backend/scripts/train_fraud_ml_model.py
python backend/scripts/train_complexity_router.py

# 3. Generate Layer 3 SLM Fine-Tuning Dataset (and optionally run 4-bit QLoRA training)
python backend/scripts/finetune_slm_lora.py --generate-only

# 4. Run the end-to-end architecture test suite
pytest backend/tests/test_ufo_architecture.py -v

# 5. Start the UFO FastAPI Server
uvicorn backend.main:app --reload --port 8000
```
