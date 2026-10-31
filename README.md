# Unstuck Backend

Unstuck helps bank customers whose payment failed (money debited, service not delivered) by mapping the transaction to RBI turnaround rules (DPSS.CO.PD No.629/02.01.014/2019-20), computing deadlines and statutory delay compensation, tracking escalation steps, and generating legal complaint PDFs.

---

## Prerequisites

- **Python**: 3.10 or higher
- **Node.js**: (for Next.js frontend running on port 8444)

---

## Getting Started

### 1. Create and Activate a Virtual Environment

**Windows:**
```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

**macOS / Linux:**
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
```

### 2. Install Dependencies

```bash
pip install -r requirements.txt
```

### 3. Run the API Server

```bash
# Run from inside the backend/ folder:
uvicorn app.main:app --reload --port 8000
```

- API Base URL: `http://localhost:8000`
- Interactive Swagger Docs: `http://localhost:8000/docs`
- Health check: `http://localhost:8000/health`

### 4. Run the Test Suite

```bash
pytest
```

---

## API Endpoints

### System & Reference
- `GET /health` — Health check endpoint.
- `GET /rules` — List all RBI TAT harmonization rules, circular reference, turnaround times, and compensation amounts.
- `GET /banks?q={query}` — Search banks by name or alias (e.g. SBI, HDFC, ICICI).

### Cases
- `POST /cases` — Register a new failed payment case with transaction details and optional complainant info.
- `GET /cases` — List all active cases.
- `GET /cases/{case_id}` — Retrieve details of a specific case.
- `PATCH /cases/{case_id}` — Update case fields (e.g., set or clear `reversal_date`).
- `POST /cases/{case_id}/evaluate` — Compute resolution status, delay days, statutory compensation, and explanation.
- `POST /cases/{case_id}/events` — Log lifecycle events (`bank_complaint_sent`, `bank_replied`, `reversal_received`, etc.).
- `GET /cases/{case_id}/escalation` — Get full 4-step escalation hierarchy and unlock dates.

---

## Frontend Integration (Next.js Compatibility)

- CORS is preconfigured for Next.js development on `http://localhost:8444` via `CORSMiddleware`.
- All response schemas conform to TypeScript models with ISO 8601 timestamps and numbers for currency.
- Standardized error format:
  ```json
  {
    "detail": {
      "code": "CASE_NOT_FOUND",
      "message": "Case '...' not found."
    }
  }
  ```
