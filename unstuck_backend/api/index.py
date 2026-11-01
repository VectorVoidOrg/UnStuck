import sys
from pathlib import Path

# Add unstuck_backend directory to Python path so `from app...` imports resolve cleanly on Vercel
BACKEND_DIR = Path(__file__).resolve().parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.main import app
