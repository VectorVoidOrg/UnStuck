# 🏗️ UFO Backend — Technical Architecture & Engineering Specification

**Project:** UFO (Unified Fraud Observer)  
**Version:** 4.0.0 (Production-Grade 5-Layer Agentic Architecture)  
**Scope:** Backend Engineering & System Design (`backend/`)

---

## 1. System Design Philosophy

UFO is engineered around the **5-Layer System Design Framework** from `docs/Designing reliable agentic systems.pdf`:
1. **Decompose:** Split deterministic high-speed checks (`<50ms`, `$0.0001` cost) from probabilistic agentic reasoning (`2–4s`, triggered only on the ~5% anomalous/disputed traffic).
2. **Components:** Isolate macro boundaries across Gateway, Privacy Vault, Agent Core, Banking Tools, and Audit/Resilience.
3. **Subcomponents:** Unpack the Agent Core into a `<10ms` Semantic Cache, Complexity Router, Small Quantized Model (80% traffic), and Frontier LLM + RBI RAG (20% complex traffic).
4. **Interactions:** Decouple live user HTTP threads (`<150ms` SLA) from multi-step agent loops via an asynchronous event queue and idempotent tool schemas.
5. **Resilience:** Harden every failure point with Edge OCR Prompt Injection sanitization, a 3-Loop Token Budget Circuit Breaker, a deterministic `₹5,000` Auto-Refund Policy Guardrail, and a Human-In-The-Loop (HITL) Dead Letter Queue (`504` timeout recovery).

---

## 2. Layer-by-Layer Mathematical & Algorithmic Specification

### 2.1 Layer 1: Deterministic Rule Engine, Velocity & Fast Tabular ML (`backend/app/layer1_gateway/`)

#### A. Composite Risk Scoring Formula (`rule_engine.py`)
For every transaction not immediately hard-blocked by the Hall of Shame blacklist (`risk = 0.95`), Layer 1 computes a bounded composite risk score in `<5ms`:

$$R_{\text{final}} = \min\Big(1.0,\; 0.60 \cdot R_{\text{rules}} + 0.25 \cdot R_{\text{ML}} + 0.15 \cdot R_{\text{rep}}\Big)$$

Where:
- **$R_{\text{rules}} \in [0, 1]$** accumulates deterministic behavioral and velocity signals:
  - `new_receiver AND high_amount (> ₹3,000) AND fast_action`: $+0.80$
  - `new_receiver AND high_amount (> ₹3,000)`: $+0.60$
  - `new_receiver AND fast_action`: $+0.40$
  - `new_receiver AND unusual_time (hour >= 22 OR hour <= 5)`: $+0.20$
  - `deceptive_vpa_pattern` (I4C impersonation regex match): $+0.65$
  - `screen_sharing_detected` (AnyDesk/TeamViewer overlay): $+0.55$
  - `is_collect_request AND new_receiver`: $+0.40$
  - `active_phone_call AND new_receiver`: $+0.35$
  - `device_changed`: $+0.20$
  - `copy_pasted_vpa AND new_receiver`: $+0.15$
  - `velocity_boost` from `VelocityTracker`: up to $+0.75$

- **$R_{\text{ML}} \in [0, 1]$ (`ml_scorer.py`)**:
  Computes fraud probability using either the `scikit-learn` `RandomForestClassifier` (`fraud_model.pkl`) or the portable calibrated logistic/interaction ensemble (`fraud_model_weights.json`):
  $$z = b + w_1 \frac{\ln(1 + \text{amount})}{10} + w_2 \mathbb{I}_{\text{high}} + w_3 \mathbb{I}_{\text{fast}} + w_4 \mathbb{I}_{\text{night}} + w_5 \mathbb{I}_{\text{new}} + w_6 (\mathbb{I}_{\text{new}}\mathbb{I}_{\text{high}}\mathbb{I}_{\text{fast}}) + \sum_{k} w_k \mathbb{I}_{\text{signal}_k}$$
  $$R_{\text{ML}} = \frac{1}{1 + e^{-z}}$$

- **$R_{\text{rep}} \in [0, 1]$ (`reputation_service.py`)**:
  $$R_{\text{rep}} = \min\Big(1.0,\; 0.70 \cdot \frac{\text{total\_risk}}{\text{transactions}} + 0.06 \cdot \min(\text{flag\_count}, 5)\Big)$$

#### B. Decision Thresholds
- $R_{\text{final}} < 0.30 \implies \text{SAFE}$ (`ALLOW_PAYMENT`, 0 LLM calls)
- $0.30 \le R_{\text{final}} < 0.60 \implies \text{WARNING}$ (`WARN_USER`)
- $R_{\text{final}} \ge 0.60 \implies \text{FRAUD}$ (`HOLD_AND_INVESTIGATE`, dispatches to `AsyncEventQueue`)

---

### 2.2 Layer 2: Reversible HMAC PII Token Vault & OCR Shield (`backend/app/layer2_privacy/`)

1. **HMAC-SHA256 Tokenization (`pii_vault.py`)**:
   Every sensitive entity $v$ of type $T \in \{\text{UPI\_VPA}, \text{PAN}, \text{IFSC}, \text{AADHAAR}, \text{PHONE}, \text{BANK\_AC}\}$ is replaced before leaving Layer 2:
   $$\text{Token}(T, v) = \texttt{<}T\texttt{\_TOKEN\_} \text{ Upper}\big(\text{HMAC-SHA256}(K_{\text{vault}}, T \mathbin{\Vert} \texttt{":"} \mathbin{\Vert} v)[0..10]\big)\texttt{>}$$
   - **Deterministic:** Identical PII values map to the same token within a case so the Agent Core can reason about entity relationships without seeing raw PII.
   - **Reversible only inside Layer 4:** `pii_vault.detokenize(token)` is restricted to internal banking tool execution.

2. **Adversarial OCR Prompt Injection Shield (`ocr_masker.py`)**:
   Scans OCR strings extracted from user-uploaded payment screenshots for instruction-hijacking patterns (`ignore previous instructions`, `system override`, `approve auto-refund`, `set confidence = 1.0`, `<system>`, `[INST]`), replaces them with `[REDACTED_PROMPT_INJECTION_ATTEMPT]`, and sets `prompt_injection_detected = True`.

---

### 2.3 Layer 3: Unpacked Fraud Investigation Agent Core (`backend/app/layer3_agent/`)

1. **Subcomponent 3.1 — Semantic Cache (`semantic_cache.py`)**:
   - Strips PII token placeholders from the redacted text and extracts word unigrams + bigrams.
   - Computes hybrid cosine + keyword-overlap similarity against pre-seeded scam templates in `<10ms`:
     $$S(q, t) = 0.65 \cdot \frac{\vec{q} \cdot \vec{t}}{\|\vec{q}\| \|\vec{t}\|} + 0.35 \cdot \frac{|V_q \cap V_t|}{\min(|V_q|, |V_t|)}$$
   - If $S(q, t)$ exceeds the calibrated threshold and no prompt injection or fake UTR is present, returns the cached `AgentVerdictSchema` immediately (`0` LLM tokens consumed).

2. **Subcomponent 3.2 — Complexity Router (`complexity_router.py`)**:
   - Computes a complexity score $C \in [0, 1]$ based on:
     - Dispute amount $> ₹5,000$ ($+0.45$)
     - Screenshot OCR + UTR ledger cross-check required ($+0.35$)
     - Adversarial prompt injection detected ($+0.50$)
     - $\ge 2$ regulatory/legal keywords (`rbi`, `circular`, `ombudsman`, `chargeback`, `unauthorized`, `lien`) ($+0.30$)
     - Narrative length $> 40$ words ($+0.15$)
   - If $C \ge 0.55 \implies \texttt{FRONTIER\_LLM\_RAG}$ (20% complex tier); otherwise $\implies \texttt{SMALL\_QUANTIZED\_MODEL}$ (80% routine tier).

3. **Subcomponent 3.3 — Small Quantized Model Engine (`slm_engine.py`)**:
   - Executes fast structured JSON inference via Groq (`llama-3.1-8b-instant`), local Ollama (`qwen2.5:3b-instruct-q4_K_M`), or calibrated local statistical inference.

4. **Subcomponent 3.4 — Frontier LLM + RBI Regulatory RAG (`rbi_rag.py`, `frontier_agent.py`)**:
   - **Loop 1:** Retrieves top-3 RBI/NPCI regulatory clauses (`RBI-2017-18-15-CL6`, `CL7`, `CL10`, `NPCI-UDIR-2023-01`, `MHA-I4C-1930-SOP`).
   - **Loop 2:** Cross-examines `utr_verified_in_ledger` and OCR injection status.
   - **Loop 3:** Invokes Gemini (`gemini-2.5-flash`) with `responseMimeType: "application/json"` to synthesize a regulatory-grounded `AgentVerdictSchema`.

---

### 2.4 Layer 4: Idempotent NPCI & Bank Core Tools (`backend/app/layer4_tools/npci_bank_tools.py`)

Every tool call generates a deterministic SHA-256 idempotency key:
$$\text{IdempotencyKey} = \texttt{idem\_} \mathbin{\Vert} \text{SHA256}(\text{investigation\_id} \mathbin{\Vert} \text{tool\_name} \mathbin{\Vert} \text{amount})[0..16]$$
- Duplicate calls with the same `(investigation_id, tool_name, amount)` return the cached `NPCIExecutionResult` without re-executing bank mutations.

---

### 2.5 Layer 5: Deterministic Guardrails & Resilience (`backend/app/layer5_resilience/`)

| Guardrail / Module | File | Hard Rule Enforced |
| :--- | :--- | :--- |
| **Token Budget Circuit Breaker** | `circuit_breaker.py` | Raises `CircuitBreakerTrippedError` if `loop_index > 3`, `request_tokens > 4096`, or `global_tokens_per_min > 250,000`. |
| **Policy Guardrail Rule A (`₹5k` Cap)** | `policy_guardrail.py` | Blocks automated refunds when `amount > ₹5,000.00`; authorizes protective `place_npci_lien` and escalates refund to `HITL_POLICY_CAP`. |
| **Policy Guardrail Rule B (Fake UTR)** | `policy_guardrail.py` | Blocks automated refunds when `utr_verified_in_ledger == False` (defeats photoshopped receipt scams). |
| **Policy Guardrail Rule C (Injection)** | `policy_guardrail.py` | Quarantines automated refunds when `prompt_injection_detected == True`. |
| **Policy Guardrail Rule D (Confidence)** | `policy_guardrail.py` | Routes verdicts with `confidence < 0.75` to `HITL_LOW_CONFIDENCE`. |
| **HITL & 504 Dead Letter Queue** | `hitl_dlq.py` | Catches `BankAPITimeout504Error` (`DLQ_BANK_504`) and persists ticket for human compliance sign-off (`POST /hitl/resolve/{ticket_id}`). |
