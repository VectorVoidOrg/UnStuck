import json
from typing import List, Dict, Any
from fastapi import APIRouter
from app.config import settings

router = APIRouter(prefix="", tags=["Reference"])

@router.get("/rules", response_model=List[Dict[str, Any]])
async def get_rules():
    """
    Feeds the 'what failed?' picker with rail and failure rules.
    """
    if settings.RULES_FILE_PATH.exists():
        with open(settings.RULES_FILE_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return []

@router.get("/banks", response_model=List[Dict[str, Any]])
async def get_banks():
    """
    Serves Nodal Officer contacts and grievance links for major Indian banks.
    """
    if settings.BANKS_FILE_PATH.exists():
        with open(settings.BANKS_FILE_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return []

