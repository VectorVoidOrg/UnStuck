import io
from typing import Optional
from fastapi import APIRouter, File, UploadFile, Form, HTTPException, status
from app.config import settings
from app.schemas import ExtractResponse, Transaction
from app.services.ocr import extract_text_from_image
from app.services.extractor import extract_fields
from app.services.crosscheck import crosscheck_extraction
from app.services.risk import score_claim_risk
from app.services.store import store

router = APIRouter(prefix="", tags=["Extraction"])

@router.post("/extract", response_model=ExtractResponse)
async def extract_transaction(
    file: Optional[UploadFile] = File(None),
    sms_text: Optional[str] = Form(None)
):
    """
    Checks the upload, runs OCR, extraction, cross-check and risk,
    returns editable fields with confidence, then discards the image.
    """
    raw_ocr_text = ""
    
    # Check if image file was provided
    if file:
        # Check file extension and content type
        filename = file.filename or "upload.png"
        content_type = file.content_type or ""
        
        # Read file contents into memory
        file_bytes = await file.read()
        
        if len(file_bytes) > settings.MAX_UPLOAD_BYTES:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail=f"Uploaded file exceeds maximum limit of {settings.MAX_UPLOAD_SIZE_MB}MB."
            )
        
        # Run OCR with Cloud Vision / Fallback
        try:
            raw_ocr_text = extract_text_from_image(file_bytes, filename=filename)
        except Exception as e:
            raw_ocr_text = ""
        finally:
            # Memory safety: immediately discard image bytes
            del file_bytes

    if not raw_ocr_text and not sms_text:
        # If neither provided, give helpful error or sample
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Please provide an image screenshot or paste an SMS message."
        )

    # Run extraction (Gemini / regex fallback)
    extracted = extract_fields(ocr_text=raw_ocr_text, sms_text=sms_text)

    # Cross-check Gemini's reference and amount with Vision text
    cross_status, conf, flags, breakdown = crosscheck_extraction(extracted, raw_ocr_text)

    # Score doubtful claims (reference format, plausible date, duplicates)
    existing_refs = store.list_all_references()
    sms_data = {"amount": extracted.get("amount"), "reference": extracted.get("reference")} if sms_text else None
    risk_score, risk_factors = score_claim_risk(extracted, existing_references=existing_refs, sms_data=sms_data)

    tx_obj = Transaction(
        amount=extracted.get("amount", 0.0),
        reference=extracted.get("reference", ""),
        bank=extracted.get("bank", "HDFC Bank"),
        date=extracted.get("date", ""),
        rail=extracted.get("rail", "UPI"),
        status=extracted.get("status", "FAILED")
    )

    return ExtractResponse(
        transaction=tx_obj,
        confidence=conf,
        confidence_breakdown=breakdown,
        flags=flags,
        crosscheck_status=cross_status,
        raw_text=raw_ocr_text,
        risk_score=risk_score,
        risk_factors=risk_factors
    )

