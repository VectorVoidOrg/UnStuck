#!/usr/bin/env python3
r"""
UFO Gateway Server Runner
Can be executed from B:\UnStuck or B:\UnStuck\frontend.
"""

import sys
import os
from pathlib import Path

# Locate root directory containing 'gateway'
current = Path(__file__).resolve().parent
if (current / "gateway").exists():
    root = current
elif (current.parent / "gateway").exists():
    root = current.parent
else:
    root = current

sys.path.insert(0, str(root))
os.chdir(str(root))

import uvicorn

if __name__ == "__main__":
    print(f"[*] Starting UFO Gateway from {root} on http://0.0.0.0:8000...")
    uvicorn.run("gateway.main:app", host="0.0.0.0", port=8000, reload=True)
