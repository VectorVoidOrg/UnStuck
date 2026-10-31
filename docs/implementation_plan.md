# Unstuck — Implementation Plan for D's Files (+ Google Setup)

Owner: **D (Dhawal)** · Scope: files marked **D** or **Both** in the file-structure PDF.
Stack: FastAPI · Pydantic v2 · Google Cloud Vision · Gemini.

---

## 0. Scope

### Files I will fill in (all already exist and are empty)

| # | File | Owner | Phase |
|---|------|-------|-------|
| 1 | [schemas.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/schemas.py) | Both | 1 |
| 2 | [validator.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/validator.py) | D | 1 |
| 3 | [test_validator.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/tests/test_validator.py) | D | 1 |
| 4 | [ocr.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/ocr.py) | D | 2 |
| 5 | [extractor.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/extractor.py) | D | 2 |
| 6 | [test_extractor.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/tests/test_extractor.py) | D | 2 |
| 7 | [crosscheck.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/crosscheck.py) | D | 2 |
| 8 | [risk.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/risk.py) | D | 2 |
| 9 | [routers/extract.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/routers/extract.py) | D | 3 |
| 10 | [letter.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/letter.py) | D | 4 |
| 11 | [routers/letters.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/routers/letters.py) | D | 4 |

### Setup files (you asked me to add these, D's needs only)

| # | File | Action | Phase |
|---|------|--------|-------|
| S1 | `backend/requirements.txt` | **Create** (it's in the PDF's folder tree but doesn't exist yet) | 0 |
| S2 | [.env.example](file:///c:/Users/dhawal/Desktop/ABC/unstuck/.env.example) | Fill in (empty now) | 0 |
| S3 | [.gitignore](file:///c:/Users/dhawal/Desktop/ABC/unstuck/.gitignore) | Extend (only contains `.env` now) | 0 |
| S4 | [app/\_\_init\_\_.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/__init__.py), [routers/\_\_init\_\_.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/routers/__init__.py), [services/\_\_init\_\_.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/services/__init__.py), [tests/\_\_init\_\_.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/tests/__init__.py) | Fill in | 0 |

What goes in each one is in **section 2.0**. A adds their own lines (e.g. a PDF library, Firestore, CORS origin) later.

### Files I will NOT touch or create

- **A's:** `main.py`, `config.py`, `routers/cases.py`, `routers/reference.py`, `deadline.py`, `penalty.py`, `escalation.py`, `pdf.py`, `store.py`, `data/*`, `test_deadline.py`, `test_penalty.py`
- **Still out of scope:** `README.md`, the whole `frontend/`
- **Not in the spec:** no `conftest.py` and no `tests/fixtures/`. Sample data for the tests goes **inside** the test files.

> [!NOTE]
> **Already done:** [schemas.py](file:///c:/Users/dhawal/Desktop/ABC/unstuck/backend/app/schemas.py) was written after the earlier auto-approval, following section 2.1 exactly. Nothing else has been touched. I can keep it for your review or empty it again.

---

## 1. Design Principles

1. **Services don't import `config.py`.** Each service takes its settings as arguments (model name, timeout, and so on). Only the routers read A's `config.py`. This means my services and tests run before A's files exist.
2. **No network in tests.** Clients for Gemini and Vision are passed in, so tests can swap in fakes.
3. **The image is never stored.** It stays in memory only, is never written to disk or logged, and is released after `/extract` responds.
4. **Code owns the facts, Gemini only does wording.** Gemini can suggest extracted fields (the user confirms them) and can polish letter wording. Code builds every figure in a letter.
5. **Never say "verified".** The risk service only raises concerns.
6. **Letters ask, they don't demand.** They request "reversal and any compensation the circular provides" and never claim the bank owes ₹100/day (PDF §9).

---

## 2. File-by-File Plan

### 2.0 Setup files (Phase 0, D's needs only)

#### S1 `backend/requirements.txt`

Only what my 11 files and 2 test files import:

```
fastapi>=0.115
uvicorn[standard]>=0.30      # run the API locally
python-multipart>=0.0.9      # required by FastAPI for UploadFile / Form in /extract
pydantic>=2.7
google-cloud-vision>=3.7     # ocr.py
google-genai>=1.0            # extractor.py, letter.py (NOT google-generativeai, deprecated)
pytest>=8                    # test_validator.py, test_extractor.py
```

Left out on purpose:
- `pytest-asyncio`: async tests use `asyncio.run(...)` instead.
- `httpx`: none of my tests use FastAPI's `TestClient`.
- `python-dateutil`: date parsing uses a fixed list of day-first formats.
- `python-dotenv`: loading `.env` is `config.py`'s job (A).

#### S2 `.env.example` (names and safe defaults only, never real secrets)

```
# ---- Gemini (extractor.py, letter.py) ----
# Option A: AI Studio key
GEMINI_API_KEY=
# Option B: Vertex AI (set true and fill project/location; leave key empty)
GOOGLE_GENAI_USE_VERTEXAI=false
GOOGLE_CLOUD_PROJECT=
GOOGLE_CLOUD_LOCATION=asia-south1
GEMINI_MODEL=gemini-2.5-flash

# ---- Cloud Vision (ocr.py) ----
# Leave empty if using `gcloud auth application-default login`
GOOGLE_APPLICATION_CREDENTIALS=

# ---- /extract limits & timeouts ----
MAX_UPLOAD_MB=5
OCR_TIMEOUT_S=10
GEMINI_TIMEOUT_S=15
LETTER_POLISH_TIMEOUT_S=20
```

Not included: `ALLOWED_ORIGIN` (CORS in A's `main.py`) and Firestore settings (A's `store.py`). A's `config.py` needs to read the names above.

#### S3 `.gitignore` (keep `.env`, add Python and Google-credential entries)

```
# Secrets
.env
.env.*
!.env.example
*-key.json
service-account*.json
credentials*.json

# Python
__pycache__/
*.py[cod]
.venv/
venv/
.pytest_cache/
```

> [!WARNING]
> I'm deliberately **not** adding a blanket `*.json`. It would hide A's `rules.json` and `banks.json` and the frontend's `package.json`. The frontend's `node_modules/` and `.next/` are FE's to add.

#### S4 `__init__.py` files

| File | Content | Why |
|---|---|---|
| `app/__init__.py` | One-line docstring | Makes `app` a package so `from app.schemas import ...` works |
| `app/routers/__init__.py` | Docstring only, **no imports** | A's `main.py` will do `from app.routers import extract, letters`. Importing them here could cause circular imports. |
| `app/services/__init__.py` | Docstring only, **no imports** | Importing `validator` shouldn't pull in the Google SDKs or A's services |
| `tests/__init__.py` | Docstring | With it, running `pytest` from `backend/` puts `backend/` on `sys.path`, so `import app` works without a `conftest.py` |

---

### 2.1 `schemas.py` (Both, do first)

Draft it, then **share it with A before writing anything else**. A's `cases.py`, `deadline.py` and `store.py` depend on these shapes.

```python
# Enums (Literal / str Enum)
Confidence      = "high" | "medium" | "low" | "unverified"
TxnStatus       = "failed" | "pending" | "success" | "unknown"
EscalationLevel = "bank" | "nodal" | "ombudsman"
EventType       = "bank_complaint_filed" | "bank_response_received" | "nodal_complaint_filed"
                | "ombudsman_filed" | "reversal_received" | "note"
DeadlineStatus  = "within_window" | "due" | "overdue" | "reversed_on_time" | "reversed_late"

class Transaction:        amount: Decimal; reference: str; bank: str; date: date;
                          time: time | None; status: TxnStatus
class ExtractedField:     value: str | None; confidence: Confidence; note: str | None
class RiskFlag:           code: str; message: str; severity: "info" | "warn" | "high"
class RiskReport:         score: int (0-100); level: "low" | "medium" | "high"; flags: list[RiskFlag]
class ExtractResponse:    fields: dict[str, ExtractedField]   # amount, reference, bank, date, time, status
                          overall_confidence: Confidence; mismatches: list[str]
                          risk: RiskReport; warnings: list[str]; ocr_available: bool
class Event:              id: str; case_id: str; type: EventType; timestamp: datetime; note: str | None
class EvaluationResult:   rule_id: str; due_date: date; status: DeadlineStatus; days_late: int
                          penalty_amount: Decimal; compensation_applicable: bool
                          next_escalation: EscalationLevel | None; unlocked_at: datetime | None
class Case:               id: str; created_at: datetime; transaction: Transaction
                          rail: str; failure_type: str; role: "payer" | "payee"
                          reversed: bool; reversal_date: date | None
                          evaluation: EvaluationResult | None; events: list[Event]
class LetterRequest:      case_id: str; level: EscalationLevel; user_edits: str | None
class LetterResponse:     case_id: str; level: EscalationLevel; text: str
                          source: "ai_polished" | "template" | "user_edited"
                          validation_passed: bool; validation_reasons: list[str]
```

- Use `Decimal` for money, never `float`.
- Add a `ConfigDict` so JSON output uses ISO dates and serialises `Decimal` as a string.

---

### 2.2 `services/validator.py` (safety-critical)

**Signature:** `validate_letter(ai_text: str, case: Case, reference_text: str) -> ValidationResult`. `ValidationResult` holds `ok: bool` and `reasons: list[str]`.

> [!NOTE]
> This differs slightly from PLAN.md's `(letter_text, case) -> bool`. Two changes:
> - It returns the reasons as well as pass/fail.
> - It compares against the **filled template** (`reference_text`) as well as the case. The template also holds valid figures like rule days and the circular number, which the case doesn't have.

**Algorithm**

1. **Pull out facts** from both texts and normalise them:
   - Amounts: `₹ / Rs / Rs. / INR`, Indian commas (`1,23,456.00`), `/-` suffix. Stored as `Decimal`.
   - Dates: `31/10/2026`, `31-10-26`, `31 Oct 2026`, `October 31, 2026`, `2026-10-31`. **Day first, always.** Stored as `date`.
   - References: tokens of 10–22 alphanumeric characters containing digits. Uppercased, spaces removed.
   - Other numbers: `30 days`, `T+1` and similar. Stored as `int`.
2. **No new facts:** every fact in the AI text must also appear in the template. This catches altered and invented figures.
3. **No missing facts:** the case's amount, reference and transaction date must appear in the AI text, and so must the bank name (case-insensitive).
4. **Sanity checks:**
   - Reject if any placeholder is left over (`{`, `}`, `[`, `]`, `<<`).
   - Reject if the AI text is under 50% or over 200% of the template's length.

---

### 2.3 `tests/test_validator.py`

Sample data is defined inside the file: one `Case` plus a hand-written filled template.

| Test | Expected |
|---|---|
| Template text unchanged | pass |
| Polite rewording, same facts | pass |
| Amount ₹1,500 → ₹1,050 | reject, reason mentions the amount |
| Date 31/10 → 30/10, or written in another format | another format of the same date passes; a different date is rejected |
| Reference changed by one character | reject |
| Reference removed | reject (missing) |
| Invented "₹100 per day" not in the template | reject |
| Several changes at once | reject, every reason listed |
| Leftover `{amount}` placeholder | reject |

Plus parametrised tests for the amount and date parsers.

---

### 2.4 `services/ocr.py`

```python
class OcrError(Exception): ...
async def extract_text(image_bytes: bytes, *, timeout_s: float = 10.0, client=None) -> str
```

- Uses `vision.ImageAnnotatorAsyncClient().document_text_detection(...)`. This is denser than `TEXT_DETECTION` and works better on screenshots.
- Wrapped in `asyncio.wait_for(timeout_s)`.
- Errors are mapped to `OcrError` with a clear `kind`:
  - `response.error.message` → `invalid_image`
  - `DeadlineExceeded` → `timeout`
  - `ResourceExhausted` → `quota`
  - `PermissionDenied` / missing credentials → `auth`
- Returns `full_text_annotation.text`, or `""` if no text is found.
- Builds the client lazily on first use, so importing the module needs no credentials.

---

### 2.5 `services/extractor.py`

```python
class ExtractionError(Exception): ...
async def extract_fields(image_bytes, mime_type, sms_text=None, *, model, timeout_s=15, client=None) -> dict
def parse_and_normalise(raw: dict) -> dict          # pure, so tests can run it on saved outputs
def normalise_amount(s) -> Decimal | None
def normalise_date(s) -> date | None                # day first
def normalise_time(s) -> time | None                # "10:42 am", "22:42", "10.42 PM"
def regex_fallback(ocr_text) -> dict                # used if Gemini fails
```

- **Gemini call:** uses the `google-genai` SDK with `client.aio.models.generate_content`.
  - Inputs are `Part.from_bytes(image, mime_type)` plus the SMS text, if given.
  - Settings: `response_mime_type="application/json"`, `response_schema=<Pydantic model>`, `temperature=0`.
- **Prompt rules:**
  - Copy values exactly as shown.
  - Use `null` when a value isn't visible.
  - Never guess.
  - Map the status to failed/pending/success/unknown.
- **Normalising:** strip currency symbols, commas and `/-`. Map bank aliases loosely, e.g. "SBI" → "State Bank of India". The full alias list lives in A's `banks.json`, so this is only a small built-in fallback.
- **SMS text** fills fields the screenshot is missing. If the two disagree, keep both and let `risk.py` flag it.

---

### 2.6 `tests/test_extractor.py`

The saved Gemini JSON outputs are dicts inside the file. No network.

- A sample GPay, PhonePe or Paytm-style output gives the correct normalised fields.
- `normalise_amount` handles: `"₹1,23,456.00"`, `"Rs. 500"`, `"INR500"`, `"Rs 500/-"`, `"abc"` → `None`.
- `normalise_date` handles: `31/10/2026`, `31-10-26`, `31 Oct 2026`, `Oct 31, 2026`, `2026-10-31`. `05/04/2026` must be **5 April**.
- Missing or `null` fields come back as `None` without errors.
- SMS text fills a missing reference.
- `extract_fields` with a fake async client checks the whole flow end to end without the network. It runs with `asyncio.run`, so `pytest-asyncio` isn't needed.
- `regex_fallback` works on a sample OCR text.

---

### 2.7 `services/crosscheck.py`

```python
def crosscheck(extracted: dict, ocr_text: str | None) -> dict
# -> {"fields": {"reference": Confidence, "amount": Confidence}, "overall": Confidence, "mismatches": [...]}
```

- **Reference:** remove whitespace from the OCR text, then look for an exact substring match. A second, looser pass tolerates common OCR mix-ups (`O↔0, I/l↔1, S↔5, B↔8`). An exact match gives `high`; a loose match gives `medium`.
- **Amount:** pull every amount-like token out of the OCR text and compare them as `Decimal`s.
- **Overall:** both match → `high`; one matches → `medium`; neither → `low`; no OCR text → `unverified`.
- Each mismatch is named (e.g. `"reference"`) so the frontend can highlight that field.

---

### 2.8 `services/risk.py`

```python
def score_risk(extracted, ocr_text, *, sms_extracted=None, crosscheck_result=None,
               is_duplicate: Callable[[str], bool] | None = None, today=None) -> RiskReport
```

| Flag code | Rule | Weight |
|---|---|---|
| `REF_FORMAT` | Doesn't look like a UPI/IMPS RRN (12 digits), a NEFT/RTGS UTR (16–22 alphanumeric), or an app transaction ID | 20 |
| `DATE_FUTURE` | Date is after today | 40 |
| `DATE_OLD` | Older than 365 days (configurable) | 15 |
| `DUPLICATE_REF` | `is_duplicate(ref)` is true. A's store provides this check. | 35 |
| `STATUS_SUCCESS` | Screenshot says "success" but the user is reporting a failure | 25 |
| `SMS_MISMATCH` | SMS and screenshot disagree on amount or reference | 25 |
| `OCR_MISMATCH` | Taken from the crosscheck mismatches | 20 |
| `AMOUNT_IMPLAUSIBLE` | Amount ≤ 0, or above the UPI limit for a UPI transaction | 20 |

Score = sum of weights, capped at 100. Level: under 25 is low, under 50 is medium, otherwise high. The wording always reads "worth checking", never "verified" or "fraud".

---

### 2.9 `routers/extract.py`: `POST /extract`

```
multipart: file (image), sms_text (optional, ≤ 2000 chars)
```

1. **Check the upload:**
   - The content type must be in the config allow-list (jpeg, png or webp), **and** the file's magic bytes must match.
   - Read the file in chunks and stop at `MAX_UPLOAD_BYTES`.
   - Errors: wrong type → `415`, too large → `413`, empty file → `400`.
2. **Run OCR and Gemini at the same time** with `asyncio.gather(..., return_exceptions=True)`. This saves several seconds.
3. **Handle failures gracefully:**
   - Gemini fails, OCR works → use `regex_fallback`, mark everything `low`, add a warning.
   - OCR fails, Gemini works → crosscheck gives `unverified`, add a warning.
   - Both fail → return `200` with empty fields and a warning: "Couldn't read the screenshot, please enter details manually." The review page lets users edit fields anyway.
4. Then run crosscheck and risk, and build the `ExtractResponse`.
5. Delete the image bytes in a `finally` block. Nothing is logged except timings and error types.

Reads `config.py` for limits, model name and timeouts. The router is registered in A's `main.py`.

---

### 2.10 `services/letter.py`

```python
def build_context(case, evaluation, rule: dict, bank: dict) -> dict[str, str]  # pre-formatted strings
def fill_template(template_text, context) -> str
async def polish(text, *, model, timeout_s, client=None) -> str
async def generate_letter(case, level, *, template_text, rule, bank, evaluation, model, client=None) -> LetterResponse
```

1. Build the context with **fixed formatting**, e.g. `₹1,500.00` and `31 October 2026`. This makes the validator's job easy.
2. Fill A's template for the chosen level. Placeholder syntax still has to be agreed with A (section 3).
3. Send the filled text to Gemini at `temperature≈0.2`. Its instructions are to improve clarity and tone only, change no number, date, name, reference or citation, add no claims, and return only the letter.
4. Run `validate_letter(ai_text, case, reference_text=filled)`.
5. If it passes → `source="ai_polished"`. If it fails, times out or errors → return the filled template with `source="template"` and include the reasons.

If the user edits the letter, the validator runs only to **warn** them (`source="user_edited"`). It never blocks, because it's the user's own letter.

---

### 2.11 `routers/letters.py`

| Endpoint | Behaviour |
|---|---|
| `POST /letters` (body `LetterRequest`) | Loads the case from the store → gets the evaluation, rule and bank → `generate_letter` → saves the latest letter per (case, level) → returns `LetterResponse` |
| `GET /letters/{case_id}/pdf?level=bank` | Loads the saved letter → `pdf.render_letter(...)` → `Response(application/pdf)` with `Content-Disposition: attachment` |
| `GET /letters/{case_id}/ombudsman-pack` | Checks with `escalation.py` that the Ombudsman step is unlocked (otherwise `409`) → `pdf.render_ombudsman_pack(case, events, evaluation, letter)` |

Errors: `404` if the case doesn't exist, `409` if no letter has been generated yet for that level.

---

## 3. What I Need From A

These are A's files. I'll write against these interfaces and use fakes in tests until A's code is ready.

| A's file | What I need |
|---|---|
| `config.py` | `settings.MAX_UPLOAD_BYTES`, `ALLOWED_MIME_TYPES`, `GEMINI_MODEL`, `OCR_TIMEOUT_S`, `GEMINI_TIMEOUT_S` |
| `store.py` | `get_case(id) -> Case \| None`, `has_reference(ref) -> bool`, `save_letter(case_id, level, LetterResponse)`, `get_letter(case_id, level)` |
| `pdf.py` | `render_letter(text, case) -> bytes`, `render_ombudsman_pack(case, events, evaluation, letter_text) -> bytes` |
| `escalation.py` | `is_unlocked(case, level) -> bool` |
| `deadline.py` / `penalty.py` | A way to compute the `EvaluationResult` for a case |
| `data/templates/` | `bank.txt`, `nodal.txt`, `ombudsman.txt` using **`{placeholder}`** syntax (Python `str.format_map`) |
| `rules.json` / `banks.json` | A loader function, e.g. `reference.get_rule(rail, failure_type)` and `get_bank(name)` |
| `main.py` | `app.include_router(extract.router)` and `app.include_router(letters.router)` |

---

## 4. Python Packages

These now go into `backend/requirements.txt`, which I create (see S1 in section 2.0).

> [!WARNING]
> PLAN.md lists `google-generativeai`. Google has deprecated that SDK. Use **`google-genai`** instead (`from google import genai`). It supports both an AI Studio key and Vertex AI, so switching between them only means changing environment variables.

Set up locally (Python 3.12.3 is already installed):
```powershell
python -m venv backend\.venv
backend\.venv\Scripts\Activate.ps1
pip install -r backend\requirements.txt
```

---

## 5. Google Tools: What You Need to Set Up

### Step 1: Google Cloud project (needed for Cloud Vision)

1. Open [console.cloud.google.com](https://console.cloud.google.com) and create a project, e.g. `unstuck-vectorvoid`. Note the **Project ID**.
2. **Turn on billing.** Cloud Vision requires it, but the first 1,000 units each month are free, which covers a hackathon.
3. **Set a budget alert:** Billing → Budgets & alerts → e.g. ₹500, with alerts at 50/90/100%.
4. Go to **APIs & Services → Library** and enable **Cloud Vision API**.

### Step 2: Credentials for Vision on your machine

Install the [gcloud CLI](https://cloud.google.com/sdk/docs/install). Then pick one:

**Option A (recommended, no key file):**
```powershell
gcloud init
gcloud auth application-default login
gcloud auth application-default set-quota-project <PROJECT_ID>
```

**Option B (service-account key, needed if you deploy somewhere other than Google Cloud):**
- IAM → Service Accounts → create `unstuck-backend` → give it the role **Service Usage Consumer**.
- Keys → Add key → JSON. Save it **outside the repo**, e.g. `C:\Users\dhawal\keys\unstuck.json`.
- Set `GOOGLE_APPLICATION_CREDENTIALS` to that path.

> [!CAUTION]
> Never commit the JSON key or `.env`. The S3 `.gitignore` entries cover `.env`, `*-key.json`, `service-account*.json` and `credentials*.json`. Keeping the key **outside the repo** is still safer.

### Step 3: Gemini (pick one)

| | **AI Studio key (recommended for speed)** | **Vertex AI (same GCP project)** |
|---|---|---|
| Setup | [aistudio.google.com](https://aistudio.google.com) → Get API key → link it to the same project | Enable **Vertex AI API** in the project |
| Auth | `GEMINI_API_KEY` | Uses the same credentials as Vision (Step 2) |
| Env | `GEMINI_API_KEY=...` | `GOOGLE_GENAI_USE_VERTEXAI=true`, `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_LOCATION=asia-south1` |
| Note | Free tier has rate limits, which are fine for a demo | One credential for everything, and better if judges ask about "Google Cloud" |

Model: a current **Gemini Flash** model, e.g. `gemini-2.5-flash` or the newest Flash in AI Studio. Set it with `GEMINI_MODEL` so the code never hardcodes it.

### Step 4: Optional

- **Firestore:** A's `store.py` ("if time allows"). Enable it in Native mode, region `asia-south1`.
- **Cloud Run:** if you deploy the backend. Use `gcloud run deploy --source backend/` and attach a service account so no key file is needed.
- **Secret Manager:** to hold `GEMINI_API_KEY` in deployment.

### Step 5: Environment variables

The names are in `.env.example` (S2). Copy it to `.env` in the repo root and fill in real values. `.env` is git-ignored.

### Quick check that the setup works

```powershell
python -c "from google.cloud import vision; print(vision.ImageAnnotatorClient())"
python -c "from google import genai; print(genai.Client().models.generate_content(model='gemini-2.5-flash', contents='ping').text)"
```

---

## 6. Order and How I'll Check Each Phase

```
Phase 0  requirements.txt, .env.example, .gitignore, 4× __init__.py
                                                                  ✔ pip install -r backend/requirements.txt
                                                                  ✔ python -c "import app.schemas" (from backend/)
Phase 1  schemas.py → validator.py → test_validator.py           ✔ pytest tests/test_validator.py
Phase 2  ocr.py → extractor.py → test_extractor.py
         → crosscheck.py → risk.py                                ✔ pytest tests/test_extractor.py
Phase 3  routers/extract.py                                       ✔ once A's main.py exists: curl -F file=@shot.png /extract
Phase 4  letter.py → routers/letters.py                           ✔ once A's store/pdf/templates exist: POST /letters, GET pdf
```

- **Automated:** `pytest backend/tests/test_validator.py backend/tests/test_extractor.py`, with no network or credentials needed.
- **Manual (needs the Google setup):** one real smoke test each of Vision and Gemini with 2–3 real failed-UPI screenshots.

---

## 7. Open Questions

0. **`schemas.py`, which is already written:** keep it for review, or empty it again?
1. **Gemini auth:** AI Studio key or Vertex AI?
2. **Template placeholders:** OK to propose `{amount}`-style (`str.format_map`) to A?
3. **`schemas.py`:** shall I write the first draft (section 2.1) and A reviews it?
4. **Saving letters:** OK for `GET /letters/{id}/pdf` to need A's store to keep the latest letter for each case and level?
