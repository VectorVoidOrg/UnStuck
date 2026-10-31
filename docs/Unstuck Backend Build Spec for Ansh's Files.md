# Unstuck Backend: Build Spec for Ansh's Files (for Antigravity)

Paste this whole doc to the agent. It is written so every decision is already made. Build in the order in section 3 and run the stated check after each step.

## 0. Context and agent rules

**Product:** Unstuck helps a bank customer whose payment failed (money debited, service not delivered). It finds the RBI turnaround rule for that failure, computes the deadline and delay, tracks escalation, and produces complaint PDFs. This backend is a FastAPI app. Another developer (Dhawal) owns extraction, AI letter writing and risk checks. You build only the files listed here.

**Rules for the agent**

1. Create only the files listed in section 2. Do not edit files outside them, except where a section says so.
2. Never invent legal data, deadlines, helpline numbers or emails. Where data is unknown, use `null` and a `# VERIFY:` comment.
3. All deadlines come from `rules.json`. Never hard-code a deadline in Python.
4. Count **calendar days** by default. The RBI circular defines T as the calendar date.
5. Run `pytest` after each step. Do not move on while a test fails.
6. Keep code short and typed. No extra frameworks.
7. Never write secrets into code. Never commit `.env`.

## 1. Conventions

- Python 3.10+ syntax, Pydantic v2, FastAPI. Run: `uvicorn app.main:app --reload` from `backend/`.
- `requirements.txt`: `fastapi`, `uvicorn[standard]`, `pydantic>=2`, `python-multipart`, `python-dotenv`, `reportlab`, `pytest`, `httpx`.
- Timezone: `Asia/Kolkata` (`zoneinfo.ZoneInfo`). Naive datetimes are treated as IST. The transaction date T is `txn_datetime` converted to IST, then `.date()`.
- JSON: dates as ISO strings (`2026-10-13`), datetimes as ISO with offset, amounts as numbers.
- Errors always return `{"detail": {"code": "...", "message": "..."}}` with codes: `CASE_NOT_FOUND` (404), `RULE_NOT_FOUND` (404), `RULE_UNVERIFIED` (422), `INVALID_REVERSAL_DATE` (422), `VALIDATION_ERROR` (422).
- IDs: `uuid4().hex[:12]`.

## 2. Files you own

```
backend/
  requirements.txt
  .env.example  .gitignore  README.md   (repo root or backend/)
  app/
    main.py  config.py  schemas.py (shared with Dhawal)
    routers/   cases.py  reference.py
    services/  deadline.py  penalty.py  escalation.py  pdf.py  store.py
    data/      rules.json  banks.json  templates/  fonts/
  tests/       test_deadline.py  test_penalty.py
```

## 3. Build order (stop and check after each)

| # | Build | Check |
| --- | --- | --- |
| 1 | Repo files, `config.py`, `schemas.py`, `main.py` | `/health` returns ok, `/docs` loads |
| 2 | `rules.json`, `penalty.py`, `deadline.py`, both test files | `pytest` all green |
| 3 | `store.py`, `cases.py` | create, evaluate, patch, event all work in `/docs` |
| 4 | `banks.json`, `reference.py` | `/rules` and `/banks` return data |
| 5 | `escalation.py` and its endpoint | unlock date correct for a sample case |
| 6 | `templates/`, `pdf.py` | both PDFs open, show ₹, fit A4 |
| 7 | README, `.env.example`, `.gitignore` | fresh clone runs from README alone |

## 4. Shared contract: `schemas.py` (agree with Dhawal before coding)

```python
from datetime import date, datetime
from typing import Literal
from pydantic import BaseModel, Field

class AppError(Exception):
    def __init__(self, code: str, message: str, status: int = 422):
        self.code, self.message, self.status = code, message, status

EventType = Literal["bank_complaint_sent", "bank_replied", "nodal_complaint_sent",
    "principal_nodal_sent", "ombudsman_filed", "reversal_received", "compensation_received"]

class Complainant(BaseModel):
    name: str | None = None
    phone: str | None = None
    email: str | None = None
    address: str | None = None

class Transaction(BaseModel):
    utr: str = Field(min_length=6, max_length=40)
    amount: float = Field(gt=0)
    bank: str                       # display name; matched to banks.json by alias
    txn_datetime: datetime
    rule_id: str                    # id from rules.json
    role: Literal["sender", "receiver"] = "sender"
    payee: str | None = None

class Event(BaseModel):
    type: EventType
    at: datetime
    note: str | None = None
    reference: str | None = None    # bank's complaint reference number
    outcome: Literal["resolved", "rejected", "unsatisfactory", "no_reply"] | None = None  # for bank_replied

class LetterRecord(BaseModel):
    level: Literal["bank", "nodal", "principal_nodal", "ombudsman"]
    text: str
    created_at: datetime

class CaseCreate(BaseModel):
    transaction: Transaction
    complainant: Complainant = Complainant()
    reversal_date: date | None = None

class Case(CaseCreate):
    id: str
    created_at: datetime
    events: list[Event] = []
    letters: list[LetterRecord] = []
    risk: dict | None = None        # set by Dhawal's risk service

class EvaluationResult(BaseModel):
    case_id: str
    rule_id: str
    rule_label: str
    rule_status: Literal["confirmed", "verify"]
    tat_days: int
    day_type: Literal["calendar", "business"]
    txn_date: date
    due_date: date
    today: date
    reversal_date: date | None
    status: Literal["within_window", "due_today", "overdue", "reversed_on_time", "reversed_late"]
    delay_days: int
    per_day: int | None
    compensation_amount: int        # 0 when rule has no compensation
    compensation_recipient: str     # "account_holder" | "verify"
    explanation: list[str]          # human-readable steps for the UI panel
    circular_ref: str
    source_url: str

class Step(BaseModel):
    id: str
    label: str
    state: Literal["done", "available", "locked"]
    unlocks_on: date | None = None
    tip: str | None = None

class EscalationState(BaseModel):
    case_id: str
    steps: list[Step]
    current_step: str
    ombudsman_unlocks_on: date | None
    days_until_ombudsman: int | None
    closed: bool
```

## 5. File specs

### `config.py`

- Load `.env` with `python-dotenv`. Expose `get_settings()` (cached) returning: `allowed_origins` (list, from `ALLOWED_ORIGINS`, default `http://localhost:3000`), `data_dir` (default `app/data`), `persist_path` (optional `PERSIST_PATH`, empty means memory only), `ombudsman_wait_days` (default 30, `# VERIFY: current RBI scheme`), `default_day_type` (default `calendar`).
- Helpers: `IST = ZoneInfo("Asia/Kolkata")`, `today_ist() -> date`.

### `main.py`

- `FastAPI(title="Unstuck API")`. CORS from settings. `GET /health` returns `{"status":"ok"}`.
- Register an `AppError` exception handler returning the error shape in section 1. Convert Pydantic request validation errors to `VALIDATION_ERROR`.
- Include `cases` and `reference` routers. Include Dhawal's `extract` and `letters` routers **only if importable** (wrap in `try/except ImportError`) so your app runs without them.

### `data/rules.json`

Top level: `{"circular": {"ref": "DPSS.CO.PD No.629/02.01.014/2019-20", "title": "Harmonisation of Turn Around Time (TAT) and customer compensation for failed transactions using authorised payment systems", "url": "https://www.rbi.org.in/scripts/NotificationUser.aspx?Id=11693"}, "rules": [...]}`.

Each rule:

```json
{"id": "upi_p2p_not_credited", "rail": "UPI", "failure": "beneficiary_not_credited",
 "label": "UPI payment to a person did not arrive",
 "user_prompt": "I sent money by UPI and the receiver did not get it",
 "tat_days": 1, "day_type": "calendar", "compensation_per_day": 100,
 "compensation_recipient": "verify", "responsible_party": "remitter_or_beneficiary_bank",
 "circular_row": "UPI: beneficiary not credited", "status": "confirmed",
 "last_verified": "2026-10-30"}
```

Create these entries (id, tat\_days, status):

| id | tat\_days | status | notes |
| --- | --- | --- | --- |
| `atm_cash_not_dispensed` | 5 | confirmed | recipient `account_holder` |
| `card_to_card_not_credited` | 1 | confirmed |  |
| `pos_no_confirmation` | 5 | confirmed | card at shop |
| `cnp_no_confirmation` | **null** | verify | online card payment; fill after reading the circular PDF |
| `imps_not_credited` | 1 | confirmed |  |
| `upi_p2p_not_credited` | 1 | confirmed | recipient `verify` |
| `upi_merchant_no_confirmation` | 5 | confirmed |  |
| `aeps_merchant_no_confirmation` | 5 | confirmed |  |
| `aeps_other` | **null** | verify |  |
| `apbs_not_credited` | 1 | confirmed |  |
| `nach_credit_delay` | 1 | confirmed |  |
| `nach_debit_after_revocation` | 1 | verify | `compensation_per_day` null |
| `ppi_on_us_not_credited` | 1 | confirmed | wallet or prepaid, on-us; off-us wallets use the underlying rail |

All recipients except ATM are `verify`. All `compensation_per_day` are 100 except the one marked null. Every entry needs `user_prompt` in plain words.

### `services/penalty.py`

```python
def compute_compensation(delay_days: int, per_day: int | None) -> int:
    # 0 if per_day is None or delay_days <= 0, else per_day * delay_days
```

### `services/deadline.py`

```python
def compute_due_date(txn_date, tat_days, day_type="calendar", holidays=frozenset()) -> date
# calendar: txn_date + tat_days. business: skip Sat, Sun and dates in holidays.
def evaluate_case(case: Case, rule: dict, today: date | None = None) -> EvaluationResult
```

`evaluate_case` steps:

1. If `rule["tat_days"]` is null, raise `AppError("RULE_UNVERIFIED", ...)`.
2. `txn_date` = IST date of `txn_datetime`. `due = compute_due_date(...)`. `today` defaults to `today_ist()`.
3. If `reversal_date` is set: if `reversal_date < txn_date` raise `INVALID_REVERSAL_DATE`. `delay = max(0, (reversal_date - due).days)`. Status `reversed_on_time` if delay is 0, else `reversed_late`.
4. Else: `today < due` is `within_window` (delay 0); `today == due` is `due_today` (delay 0); `today > due` is `overdue` with `delay = (today - due).days`.
5. `compensation_amount = compute_compensation(delay, rule["compensation_per_day"])`.
6. `explanation`: ordered plain sentences, e.g. `"Transaction date (T): 12 Oct 2026"`, `"Deadline: T + 1 calendar day = 13 Oct 2026"`, `"Money returned on 21 Oct 2026, 8 days after the deadline"` or `"Still pending: 3 days past the deadline"`, `"Rule: <circular_row>. Source: <source_url>"`.
7. Copy `circular_ref` and `source_url` from rules top level.

### `tests/` (write before the code)

Use T = Mon 2026-10-12, rule `tat_days=1` unless stated.

| # | Setup | Expected |
| --- | --- | --- |
| 1 | reversal 2026-10-13 | delay 0, `reversed_on_time`, ₹0 |
| 2 | reversal 2026-10-14 | delay 1, `reversed_late`, ₹100 |
| 3 | pending, today 2026-10-12 | `within_window`, delay 0 |
| 4 | pending, today 2026-10-13 | `due_today`, delay 0 |
| 5 | pending, today 2026-10-16 | `overdue`, delay 3, ₹300 |
| 6 | `txn_datetime` 2026-10-12T23:58+05:30 | T is 2026-10-12 |
| 7 | founder case: reversal 2026-10-21 | delay 8, ₹800 |
| 8 | ATM `tat_days=5`, reversal 2026-10-19 | due 2026-10-17, delay 2, ₹200 |
| 9 | T = 2026-10-30, `tat_days=5` | due 2026-11-04 |
| 10 | reversal 2026-10-11 | raises `INVALID_REVERSAL_DATE` |
| 11 | rule `tat_days` null | raises `RULE_UNVERIFIED` |
| 12 | `compensation_per_day` null, 3 days late | amount 0 |

`test_penalty.py`: `(0,100)->0`, `(-2,100)->0`, `(3,100)->300`, `(3,None)->0`.

### `services/store.py`

```python
class CaseStore:  # in-memory dict + threading.Lock
    def create(self, data: CaseCreate) -> Case
    def get(self, case_id: str) -> Case          # raises AppError CASE_NOT_FOUND
    def update(self, case_id: str, **fields) -> Case
    def add_event(self, case_id: str, event: Event) -> Case
    def add_letter(self, case_id: str, record: LetterRecord) -> Case   # used by Dhawal
    def set_risk(self, case_id: str, risk: dict) -> Case               # used by Dhawal
    def list(self) -> list[Case]
    def find_by_utr(self, utr: str) -> list[Case]                       # used by Dhawal
store = CaseStore()   # module-level singleton
```

If `persist_path` is set, write all cases to that JSON file after every change and load it at start. Keep every method behind this interface so Firestore can replace the class later.

### `routers/cases.py`

All under `/cases`.

| Endpoint | Behavior |
| --- | --- |
| `POST /cases` | Body `CaseCreate`. Validate `rule_id` exists (else `RULE_NOT_FOUND`), txn date not in the future, `reversal_date` not before T. Create and return `{"case": Case, "warnings": [str]}`. Add a warning if rail is UPI and `utr` is not 12 digits, or if `find_by_utr` returns another case |
| `GET /cases/{id}` | Return `Case` |
| `GET /cases` | Return list |
| `PATCH /cases/{id}` | Body `{"reversal_date": "YYYY-MM-DD" \| null}`. Validate, update. Return `Case` |
| `POST /cases/{id}/evaluate` | Load rule, run `evaluate_case`, return `EvaluationResult` |
| `POST /cases/{id}/events` | Body `Event`. Append. Return `Case`. If `type` is `reversal_received` and `reversal_date` is empty, set it from `at` |
| `GET /cases/{id}/escalation` | Return `EscalationState` |

Evaluate response example:

```json
{"case_id": "a1b2c3d4e5f6", "rule_id": "upi_p2p_not_credited", "rule_status": "confirmed",
 "tat_days": 1, "day_type": "calendar", "txn_date": "2026-10-12", "due_date": "2026-10-13",
 "today": "2026-10-21", "reversal_date": "2026-10-21", "status": "reversed_late",
 "delay_days": 8, "per_day": 100, "compensation_amount": 800,
 "compensation_recipient": "verify",
 "explanation": ["Transaction date (T): 12 Oct 2026", "Deadline: T + 1 calendar day = 13 Oct 2026",
  "Money returned on 21 Oct 2026, 8 days after the deadline"],
 "circular_ref": "DPSS.CO.PD No.629/02.01.014/2019-20", "source_url": "https://www.rbi.org.in/..."}
```

### `data/banks.json` and `routers/reference.py`

- `banks.json`: list of `{"name", "aliases": [], "nodal_officer_email": null, "grievance_url": null, "contact_status": "unverified", "last_verified": null}`. Seed **names and aliases only** for: State Bank of India (SBI), HDFC Bank, ICICI Bank, Axis Bank, Punjab National Bank (PNB), Bank of Baroda, Kotak Mahindra Bank, Canara Bank, Union Bank of India, IDFC FIRST Bank, Yes Bank, IndusInd Bank, Bank of India. **Do not invent emails or URLs.** Leave `null`.
- `GET /rules`: return `{"circular": {...}, "rules": [id, rail, failure, label, user_prompt, tat_days, status, requires_verification (bool, true when status is verify or tat_days is null), compensation_per_day]}`.
- `GET /banks?q=`: case-insensitive match on name or alias; empty `q` returns all.

### `services/escalation.py`

```python
def build_escalation(case: Case, today: date | None = None) -> EscalationState
```

Let `first = date of the earliest bank_complaint_sent event`, `wait = settings.ombudsman_wait_days`.

| Step id | Label | State rule |
| --- | --- | --- |
| `bank_complaint` | Complain to the bank (branch, app or customer care) | `done` if event exists, else `available`. Tip: ask for a written acknowledgement and a complaint reference number |
| `nodal_officer` | Escalate to the bank's Nodal Officer | `locked` until `bank_complaint` done, then `available`; `done` if `nodal_complaint_sent` exists |
| `principal_nodal_officer` | Escalate to the Principal Nodal Officer | `locked` until `nodal_officer` done, then `available`; `done` if `principal_nodal_sent` exists |
| `ombudsman_pack` | Prepare the RBI Ombudsman complaint | Unlocked when `today >= first + wait` **or** a `bank_replied` event has outcome `rejected` or `unsatisfactory`. Before that `locked` with `unlocks_on = first + wait`. `done` if `ombudsman_filed` exists |

- `ombudsman_unlocks_on`: the earlier of `first + wait` and the date of a `rejected` or `unsatisfactory` reply. `None` if no complaint sent yet. `days_until_ombudsman = max(0, unlocks_on - today)`.
- `closed = True` if a `bank_replied` event has outcome `resolved`, or both `reversal_received` and `compensation_received` exist.
- `current_step`: the first step that is `available`.
- Test: complaint sent 2026-11-03, wait 30 gives unlock 2026-12-03.

### `data/templates/` (plain text with `{{placeholders}}`)

Files: `01_bank_complaint.txt`, `02_nodal_officer.txt`, `03_principal_nodal_officer.txt`, `04_ombudsman_summary.txt`.

Placeholders (Dhawal's `letter.py` fills these; use exactly these names): `{{complainant_name}} {{complainant_address}} {{complainant_phone}} {{complainant_email}} {{bank_name}} {{date_today}} {{utr}} {{amount}} {{txn_date}} {{txn_time}} {{rail_label}} {{tat_days}} {{due_date}} {{reversal_clause}} {{delay_days}} {{compensation_clause}} {{circular_ref}} {{circular_row}} {{prior_complaint_ref}} {{prior_complaint_date}}`

Wording rules:

- Polite, formal, first person, under one page.
- Facts stated as "as per my records" and "details provided by me". Never say "you owe" or "I am owed".
- Ask for: reversal if still pending, **any compensation applicable under the circular**, a written response, and a complaint reference number.
- Cite `{{circular_ref}}` and `{{circular_row}}`.
- Templates 02 and 03 refer to `{{prior_complaint_ref}}` and `{{prior_complaint_date}}` and say no satisfactory resolution was received.
- `{{reversal_clause}}` and `{{compensation_clause}}` are whole sentences supplied by code (empty strings allowed).

### `services/pdf.py` (ReportLab Platypus, A4)

```python
def build_letter_pdf(case, evaluation, letter_text: str, bank: dict | None) -> bytes
def build_ombudsman_pack_pdf(case, evaluation, escalation, bank: dict | None) -> bytes
```

- **Font:** the default Helvetica cannot draw ₹. Register DejaVu Sans (regular and bold) from `app/data/fonts/` and use it everywhere. If the font files are missing, print "Rs." instead of ₹.
- **Letter PDF:** date; To (bank name, plus Nodal Officer email only if `contact_status` is verified); Subject line; body = `letter_text` (paragraphs, preserved line breaks); table "Transaction details" (UTR, amount, date and time, failure type, role); table "Timeline" (T, deadline, reversal date or "pending", days after deadline); a compensation line only when `per_day` and delay are both above 0, worded "as applicable under the circular"; attachments list (screenshot, bank SMS, complaint acknowledgement); signature block with the complainant name; footer "Prepared with Unstuck from details provided by the complainant. This is not legal advice."
- **Ombudsman pack PDF:** page 1 titled "Details for your RBI Ombudsman complaint" with labeled fields in this order: complainant details, bank, transaction details, date and reference of the earlier complaint (from events), relief sought (reversal if pending, compensation as applicable), grounds (circular ref and row). Page 2: documents checklist using ☐ items (screenshot, bank SMS or statement line, copy of the complaint, bank acknowledgement, any bank reply) and filing options: online RBI complaint portal (cms.rbi.org.in), email crpc@rbi.org.in, or a physical form, with the line "Confirm current channels and time limits at rbi.org.in before filing."
- Return `bytes` from a `BytesIO`. Wrap long text so it never overflows the page.

### Repo files

- `.gitignore`: `venv/`, `.venv/`, `__pycache__/`, `*.pyc`, `.env`, `*.key`, `app/data/cases.json`, `node_modules/`, `.next/`.
- `.env.example`: `ALLOWED_ORIGINS=http://localhost:3000`, `PERSIST_PATH=`, `OMBUDSMAN_WAIT_DAYS=30`, `DEFAULT_DAY_TYPE=calendar`, plus Dhawal's names (`GEMINI_API_KEY=`, `GOOGLE_APPLICATION_CREDENTIALS=`, `MAX_UPLOAD_MB=8`). Names only.
- `README.md`: prerequisites, create a venv, `pip install -r requirements.txt`, copy `.env.example` to `.env`, run `uvicorn app.main:app --reload`, run `pytest`, the endpoint list, and the demo case below.

## 6. Acceptance check (founder case)

1. `POST /cases` with a UPI transaction dated 2026-10-12, `rule_id: upi_p2p_not_credited`, amount 1000.
2. `PATCH /cases/{id}` with `reversal_date: 2026-10-21`.
3. `POST /cases/{id}/evaluate` returns `delay_days: 8`, `compensation_amount: 800`, `status: reversed_late`.
4. `POST /cases/{id}/events` with `bank_complaint_sent` on 2026-11-03, then `GET /cases/{id}/escalation` shows `ombudsman_unlocks_on: 2026-12-03`.
5. `build_letter_pdf` and `build_ombudsman_pack_pdf` produce PDFs that open and show the ₹ symbol.
6. `pytest` is green.
