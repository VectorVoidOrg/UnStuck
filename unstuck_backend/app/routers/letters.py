import uuid
from datetime import datetime
from fastapi import APIRouter, HTTPException, Response, Query
from app.schemas import LetterRequest, LetterResponse, Event
from app.services.letter import generate_letter
from app.services.pdf import render_letter_pdf, render_ombudsman_pack_pdf
from app.services.store import store

router = APIRouter(prefix="", tags=["Letters & Documents"])

@router.post("/letters", response_model=LetterResponse)
@router.post("/letter", response_model=LetterResponse)
async def create_letter(payload: LetterRequest):
    case = store.get_case(payload.case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    
    # Generate the letter
    response = generate_letter(
        case=case,
        level=payload.level,
        custom_notes=payload.custom_notes or ""
    )
    
    # Log escalation event if level is nodal officer or ombudsman
    event_type_map = {
        "bank_complaint": "BANK_COMPLAINT",
        "nodal_officer": "NODAL_OFFICER",
        "ombudsman": "OMBUDSMAN_PACK"
    }
    event_type = event_type_map.get(payload.level, "NOTE")
    
    # Add logged event
    store.add_event(
        case.id,
        Event(
            id=str(uuid.uuid4()),
            case_id=case.id,
            timestamp=datetime.now(),
            type=event_type,
            description=f"Generated {payload.level.replace('_', ' ').title()} letter.",
            details={"level": payload.level, "generated_by": response.generated_by}
        )
    )
    
    return response

@router.get("/letters/{case_id}/pdf")
@router.get("/pdf")
async def download_letter_pdf(
    case_id: str,
    level: str = Query("bank_complaint")
):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    
    # Generate letter text
    letter_resp = generate_letter(case=case, level=level)
    pdf_bytes = render_letter_pdf(letter_resp.letter_text, case)
    
    filename = f"Unstuck_{level}_{case.transaction.reference}.pdf"
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'}
    )

@router.get("/letters/{case_id}/ombudsman-pack")
@router.get("/ombudsman-pack")
async def download_ombudsman_pack(case_id: str):
    case = store.get_case(case_id)
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    
    events = store.get_events(case_id)
    pdf_bytes = render_ombudsman_pack_pdf(case, events)
    
    # Log event
    store.add_event(
        case.id,
        Event(
            id=str(uuid.uuid4()),
            case_id=case.id,
            timestamp=datetime.now(),
            type="OMBUDSMAN_PACK",
            description="Generated full RBI Ombudsman Dossier PDF pack.",
            details={"reference": case.transaction.reference}
        )
    )
    
    filename = f"Unstuck_Ombudsman_Dossier_{case.transaction.reference}.pdf"
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'}
    )
