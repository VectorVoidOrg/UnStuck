FROM python:3.12-slim

WORKDIR /app

# Install backend dependencies
COPY backend/requirements.txt ./backend/requirements.txt
RUN pip install --no-cache-dir -r backend/requirements.txt

# Copy backend source code, pre-trained models, and seed datasets
COPY backend/ ./backend/

# Initialize database & pre-seed 300+ fraudster UPI IDs on container build
RUN python backend/scripts/init_db.py && python backend/scripts/seed_fraudster_vpa_dataset.py

EXPOSE 8000

CMD ["sh", "-c", "uvicorn backend.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
