# Unstuck Backend Service

This directory contains the complete, standalone Unstuck FastAPI backend:
- **`app/`**:
  - `routers/`: Endpoints for OCR screenshot extraction (`extract.py`), case management (`cases.py`), escalation letter generation (`letters.py`), and reference banks/rules (`reference.py`).
  - `services/`: Gemini vision extractor, RBI deadline calculator, penalty calculator, escalation ladder, PDF generator, and schema validator.
  - `data/`: Storage for case JSONs, RBI circular rules (`rules.json`), bank contacts (`banks.json`), and legal escalation templates (`templates/`).
- **`tests/`**: Integration tests and unit tests for deadline, penalty, extractor, and validator logic.
- **`requirements.txt`**: Python dependencies required to run the Unstuck backend.

### Running Unstuck Backend Directly:
```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

