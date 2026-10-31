import html
import io
from datetime import date, datetime
from typing import Any
from zoneinfo import ZoneInfo

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from app.config import get_settings

IST = ZoneInfo("Asia/Kolkata")

_FONTS_REGISTERED = False
FONT_REGULAR = "Helvetica"
FONT_BOLD = "Helvetica-Bold"
CURRENCY = "Rs."
CHECKBOX = "[ ]"


def _ensure_fonts():
    global _FONTS_REGISTERED, FONT_REGULAR, FONT_BOLD, CURRENCY, CHECKBOX
    if _FONTS_REGISTERED:
        return

    settings = get_settings()
    font_dir = settings.data_dir / "fonts"
    regular_ttf = font_dir / "DejaVuSans.ttf"
    bold_ttf = font_dir / "DejaVuSans-Bold.ttf"

    if regular_ttf.exists() and bold_ttf.exists():
        try:
            pdfmetrics.registerFont(TTFont("DejaVuSans", str(regular_ttf)))
            pdfmetrics.registerFont(TTFont("DejaVuSans-Bold", str(bold_ttf)))
            FONT_REGULAR = "DejaVuSans"
            FONT_BOLD = "DejaVuSans-Bold"
            CURRENCY = "₹"
            CHECKBOX = "\u2610"
            _FONTS_REGISTERED = True
            return
        except Exception:
            pass

    FONT_REGULAR = "Helvetica"
    FONT_BOLD = "Helvetica-Bold"
    CURRENCY = "Rs."
    CHECKBOX = "[ ]"
    _FONTS_REGISTERED = True


def _safe_escape(val: Any) -> str:
    """Escapes user-supplied content for safe insertion into ReportLab Paragraph XML."""
    if val is None:
        return ""
    text = str(val)
    return html.escape(text, quote=True)


def _format_date(d: date | datetime | str | None) -> str:
    if d is None:
        return "N/A"

    if isinstance(d, str):
        if "T" in d or " " in d:
            try:
                d = datetime.fromisoformat(d)
            except Exception:
                pass
        else:
            try:
                d = date.fromisoformat(d[:10])
            except Exception:
                pass

    if isinstance(d, datetime):
        if d.tzinfo is None:
            d = d.replace(tzinfo=IST)
        else:
            d = d.astimezone(IST)
        return f"{d.day} {d.strftime('%b %Y, %I:%M %p IST')}"

    if isinstance(d, date):
        return f"{d.day} {d.strftime('%b %Y')}"

    return str(d)


def build_letter_pdf(
    case: Any,
    evaluation: Any,
    letter_text: str,
    bank: dict[str, Any] | None,
) -> bytes:
    """Generates an official complaint letter PDF strictly fitting A4 with XML injection protection."""
    _ensure_fonts()

    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=36,
        rightMargin=36,
        topMargin=32,
        bottomMargin=32,
    )

    styles = getSampleStyleSheet()
    meta_style = ParagraphStyle(
        "MetaText",
        parent=styles["Normal"],
        fontName=FONT_REGULAR,
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor("#344054"),
    )
    meta_bold = ParagraphStyle(
        "MetaTextBold",
        parent=styles["Normal"],
        fontName=FONT_BOLD,
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor("#101828"),
    )
    body_style = ParagraphStyle(
        "Body",
        parent=styles["Normal"],
        fontName=FONT_REGULAR,
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor("#1D2939"),
    )
    section_title = ParagraphStyle(
        "SectionTitle",
        parent=styles["Normal"],
        fontName=FONT_BOLD,
        fontSize=9,
        leading=12,
        textColor=colors.HexColor("#101828"),
    )
    footer_style = ParagraphStyle(
        "Footer",
        parent=styles["Normal"],
        fontName=FONT_REGULAR,
        fontSize=7.5,
        leading=10,
        textColor=colors.HexColor("#667085"),
        alignment=1,
    )

    story = []

    # Header Date
    today_str = _format_date(getattr(evaluation, "today", date.today()))
    story.append(Paragraph(f"<b>Date:</b> {today_str}", meta_style))
    story.append(Spacer(1, 6))

    # Addressee (safely escaped)
    bank_name = _safe_escape(
        getattr(getattr(case, "transaction", None), "bank", "Bank")
    )
    nodal_email = None
    if (
        bank
        and bank.get("contact_status") == "verified"
        and bank.get("nodal_officer_email")
    ):
        nodal_email = _safe_escape(bank.get("nodal_officer_email"))

    to_lines = ["<b>To:</b>", bank_name]
    if nodal_email:
        to_lines.append(f"Nodal Officer Email: {nodal_email}")
    else:
        to_lines.append("Grievance Redressal Officer / Branch Manager")
    story.append(Paragraph("<br/>".join(to_lines), meta_style))
    story.append(Spacer(1, 8))

    # Subject Line
    rule_label = _safe_escape(getattr(evaluation, "rule_label", "payment"))
    utr_val = _safe_escape(getattr(getattr(case, "transaction", None), "utr", ""))
    subject_text = f"<b>Subject: Grievance regarding failed {rule_label} transaction (UTR: {utr_val})</b>"
    story.append(Paragraph(subject_text, meta_bold))
    story.append(Spacer(1, 8))

    # Body Paragraphs (safely escape each line)
    paragraphs = [p.strip() for p in letter_text.split("\n") if p.strip()]
    for p in paragraphs:
        story.append(Paragraph(_safe_escape(p), body_style))
        story.append(Spacer(1, 4))

    story.append(Spacer(1, 4))

    # Table 1: Transaction details
    story.append(Paragraph("<b>Transaction Details</b>", section_title))
    story.append(Spacer(1, 3))

    amount_val = getattr(getattr(case, "transaction", None), "amount", 0.0)
    txn_dt = getattr(getattr(case, "transaction", None), "txn_datetime", None)
    role_val = _safe_escape(
        getattr(getattr(case, "transaction", None), "role", "sender")
    ).capitalize()

    txn_data = [
        [
            Paragraph("<b>UTR / Reference:</b>", meta_style),
            Paragraph(utr_val, meta_style),
        ],
        [
            Paragraph("<b>Amount:</b>", meta_style),
            Paragraph(f"{CURRENCY} {amount_val:,.2f}", meta_style),
        ],
        [
            Paragraph("<b>Date & Time:</b>", meta_style),
            Paragraph(_format_date(txn_dt), meta_style),
        ],
        [
            Paragraph("<b>Failure Type / Rule:</b>", meta_style),
            Paragraph(rule_label, meta_style),
        ],
        [
            Paragraph("<b>Customer Role:</b>", meta_style),
            Paragraph(role_val, meta_style),
        ],
    ]
    t1 = Table(txn_data, colWidths=[150, 360])
    t1.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F8F9FA")),
                ("INNERGRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#EAECF0")),
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#D0D5DD")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("TOPPADDING", (0, 0), (-1, -1), 2),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
            ]
        )
    )
    story.append(t1)
    story.append(Spacer(1, 6))

    # Table 2: Timeline
    story.append(
        Paragraph(
            "<b>Resolution Timeline (RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20)</b>",
            section_title,
        )
    )
    story.append(Spacer(1, 3))

    rev_date = getattr(case, "reversal_date", None)
    rev_str = _format_date(rev_date) if rev_date else "Pending resolution"
    delay_days = getattr(evaluation, "delay_days", 0)
    delay_str = (
        f"{delay_days} calendar days"
        if delay_days > 0
        else "0 days (within turnaround time)"
    )
    tat_days = getattr(evaluation, "tat_days", 1)
    day_type = getattr(evaluation, "day_type", "calendar")
    due_date = getattr(evaluation, "due_date", None)
    txn_date_val = getattr(evaluation, "txn_date", None)

    timeline_data = [
        [
            Paragraph("<b>Transaction Date (T):</b>", meta_style),
            Paragraph(_format_date(txn_date_val), meta_style),
        ],
        [
            Paragraph("<b>Mandated Resolution Due Date:</b>", meta_style),
            Paragraph(
                f"{_format_date(due_date)} (T + {tat_days} {day_type} days)",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>Reversal Status / Date:</b>", meta_style),
            Paragraph(rev_str, meta_style),
        ],
        [
            Paragraph("<b>Delay Beyond Deadline:</b>", meta_style),
            Paragraph(delay_str, meta_style),
        ],
    ]
    t2 = Table(timeline_data, colWidths=[150, 360])
    t2.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F8F9FA")),
                ("INNERGRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#EAECF0")),
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#D0D5DD")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("TOPPADDING", (0, 0), (-1, -1), 2),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
            ]
        )
    )
    story.append(t2)
    story.append(Spacer(1, 6))

    # Compensation line if applicable
    per_day = getattr(evaluation, "per_day", None)
    comp_amt = getattr(evaluation, "compensation_amount", 0)
    if per_day and per_day > 0 and delay_days > 0:
        comp_text = (
            f"<b>Compensation:</b> {CURRENCY} {per_day} per day × {delay_days} days = "
            f"<b>{CURRENCY} {comp_amt:,}</b> (as applicable under the circular)."
        )
        story.append(Paragraph(comp_text, meta_bold))
        story.append(Spacer(1, 6))

    # Attachments list
    story.append(Paragraph("<b>Enclosures / Attachments:</b>", section_title))
    story.append(
        Paragraph(
            "1. Screenshot of failed transaction / debit alert", meta_style
        )
    )
    story.append(
        Paragraph(
            "2. Bank statement / SMS record reflecting amount debit", meta_style
        )
    )
    story.append(
        Paragraph(
            "3. Previous complaint acknowledgement / correspondence (if any)",
            meta_style,
        )
    )
    story.append(Spacer(1, 8))

    # Signature block
    c_obj = getattr(case, "complainant", None)
    c_name = _safe_escape(getattr(c_obj, "name", None) or "Account Holder")
    c_phone = _safe_escape(getattr(c_obj, "phone", None))
    c_email = _safe_escape(getattr(c_obj, "email", None))

    story.append(Paragraph("Yours faithfully,", meta_style))
    story.append(Spacer(1, 10))
    story.append(Paragraph(f"<b>{c_name}</b>", meta_bold))
    if c_phone:
        story.append(Paragraph(f"Phone: {c_phone}", meta_style))
    if c_email:
        story.append(Paragraph(f"Email: {c_email}", meta_style))
    story.append(Spacer(1, 8))

    # Footer
    story.append(
        HRFlowable(
            width="100%",
            thickness=0.5,
            color=colors.HexColor("#D0D5DD"),
            spaceBefore=4,
            spaceAfter=4,
        )
    )
    story.append(
        Paragraph(
            "Prepared with Unstuck from details provided by the complainant. This is not legal advice.",
            footer_style,
        )
    )

    doc.build(story)
    return buffer.getvalue()


def build_ombudsman_pack_pdf(
    case: Any,
    evaluation: Any,
    escalation: Any,
    bank: dict[str, Any] | None,
) -> bytes:
    """Generates a 2-page RBI Ombudsman Complaint Dossier with XML injection protection."""
    _ensure_fonts()

    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=36,
        rightMargin=36,
        topMargin=32,
        bottomMargin=32,
    )

    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "TitleStyle",
        parent=styles["Normal"],
        fontName=FONT_BOLD,
        fontSize=12,
        leading=15,
        textColor=colors.HexColor("#101828"),
        alignment=1,
    )
    subtitle_style = ParagraphStyle(
        "SubTitleStyle",
        parent=styles["Normal"],
        fontName=FONT_REGULAR,
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor("#475467"),
        alignment=1,
    )
    heading_style = ParagraphStyle(
        "HeadingStyle",
        parent=styles["Normal"],
        fontName=FONT_BOLD,
        fontSize=9,
        leading=13,
        textColor=colors.HexColor("#101828"),
    )
    meta_style = ParagraphStyle(
        "MetaText",
        parent=styles["Normal"],
        fontName=FONT_REGULAR,
        fontSize=8,
        leading=11,
        textColor=colors.HexColor("#344054"),
    )
    footer_style = ParagraphStyle(
        "Footer",
        parent=styles["Normal"],
        fontName=FONT_REGULAR,
        fontSize=7.5,
        leading=10,
        textColor=colors.HexColor("#667085"),
        alignment=1,
    )

    story = []

    # ---------------- PAGE 1 ----------------
    story.append(
        Paragraph(
            "<b>Details for your RBI Ombudsman complaint</b>", title_style
        )
    )
    story.append(
        Paragraph(
            "Reserve Bank - Integrated Ombudsman Scheme, 2021", subtitle_style
        )
    )
    story.append(Spacer(1, 8))

    # Earlier complaint events
    events = getattr(case, "events", [])
    complaint_events = [
        e
        for e in events
        if getattr(e, "type", "")
        in ("bank_complaint_sent", "nodal_complaint_sent", "principal_nodal_sent")
    ]
    earliest_ref = "Not recorded"
    earliest_date_str = "Not recorded"
    if complaint_events:
        earliest_event = min(
            complaint_events, key=lambda e: getattr(e, "at", datetime.min)
        )
        earliest_ref = _safe_escape(
            getattr(earliest_event, "reference", None) or "Ref pending"
        )
        earliest_date_str = _format_date(getattr(earliest_event, "at", None))

    c_obj = getattr(case, "complainant", None)
    c_name = _safe_escape(getattr(c_obj, "name", None) or "N/A")
    c_phone = _safe_escape(getattr(c_obj, "phone", None) or "N/A")
    c_email = _safe_escape(getattr(c_obj, "email", None) or "N/A")
    c_address = _safe_escape(getattr(c_obj, "address", None) or "N/A")

    rev_date = getattr(case, "reversal_date", None)
    relief_reversal = (
        "Immediate reversal of debited amount"
        if not rev_date
        else f"Reversal confirmed on {_format_date(rev_date)}"
    )
    comp_amt = getattr(evaluation, "compensation_amount", 0)
    delay_days = getattr(evaluation, "delay_days", 0)
    relief_compensation = (
        f"Statutory compensation of {CURRENCY} {comp_amt:,} for delay of {delay_days} days (as applicable under circular)"
        if comp_amt > 0
        else "Statutory compensation as applicable under RBI regulations"
    )

    txn = getattr(case, "transaction", None)
    utr_val = _safe_escape(getattr(txn, "utr", ""))
    amt_val = getattr(txn, "amount", 0.0)
    bank_name = _safe_escape(getattr(txn, "bank", "Bank"))
    rule_lbl = _safe_escape(
        getattr(evaluation, "rule_label", getattr(txn, "rule_id", ""))
    )
    circ_ref = _safe_escape(getattr(evaluation, "circular_ref", ""))
    tat_days = getattr(evaluation, "tat_days", 1)
    day_type = getattr(evaluation, "day_type", "calendar")
    due_date = getattr(evaluation, "due_date", None)

    fields_data = [
        [
            Paragraph("<b>1. Complainant Details</b>", heading_style),
            Paragraph(
                f"<b>Name:</b> {c_name}<br/><b>Phone:</b> {c_phone}<br/><b>Email:</b> {c_email}<br/><b>Address:</b> {c_address}",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>2. Regulated Entity (Bank)</b>", heading_style),
            Paragraph(f"<b>Bank Name:</b> {bank_name}", meta_style),
        ],
        [
            Paragraph("<b>3. Transaction Details</b>", heading_style),
            Paragraph(
                f"<b>UTR / Reference:</b> {utr_val}<br/>"
                f"<b>Amount:</b> {CURRENCY} {amt_val:,.2f}<br/>"
                f"<b>Date & Time:</b> {_format_date(getattr(txn, 'txn_datetime', None))}<br/>"
                f"<b>Payment Rail / Type:</b> {rule_lbl}",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>4. Prior Complaint to Bank</b>", heading_style),
            Paragraph(
                f"<b>Bank Complaint Reference:</b> {earliest_ref}<br/>"
                f"<b>Date Filed with Bank:</b> {earliest_date_str}<br/>"
                f"<b>Status:</b> Unresolved / 30-day escalation requirement met",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>5. Relief Sought</b>", heading_style),
            Paragraph(
                f"1. {relief_reversal}<br/>2. {relief_compensation}",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>6. Grounds for Complaint</b>", heading_style),
            Paragraph(
                f"<b>RBI Circular Reference:</b> {circ_ref}<br/>"
                f"<b>Category:</b> {rule_lbl}<br/>"
                f"<b>Mandated TAT:</b> T + {tat_days} {day_type} days (Due: {_format_date(due_date)})<br/>"
                f"Bank failed to auto-reverse the failed transaction within the mandated TAT window.",
                meta_style,
            ),
        ],
    ]

    p1_table = Table(fields_data, colWidths=[160, 350])
    p1_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#FAFAFA")),
                ("INNERGRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#EAECF0")),
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#D0D5DD")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    story.append(p1_table)
    story.append(Spacer(1, 10))

    story.append(
        HRFlowable(
            width="100%",
            thickness=0.5,
            color=colors.HexColor("#D0D5DD"),
            spaceBefore=4,
            spaceAfter=4,
        )
    )
    story.append(
        Paragraph(
            "Prepared with Unstuck from details provided by the complainant. This is not legal advice.",
            footer_style,
        )
    )

    # ---------------- PAGE 2 ----------------
    story.append(PageBreak())

    story.append(
        Paragraph(
            "<b>Ombudsman Checklist & Filing Guidelines</b>", title_style
        )
    )
    story.append(
        Paragraph(
            "Essential documents and authorized channels for filing with RBI",
            subtitle_style,
        )
    )
    story.append(Spacer(1, 10))

    story.append(Paragraph("<b>Documents Checklist</b>", heading_style))
    story.append(Spacer(1, 5))

    checklist_items = [
        f"{CHECKBOX}  <b>Screenshot of transaction failure</b> or error notification screen",
        f"{CHECKBOX}  <b>Bank SMS alert or bank statement page</b> reflecting the unauthorized / uncredited debit",
        f"{CHECKBOX}  <b>Copy of initial written complaint</b> submitted to the bank",
        f"{CHECKBOX}  <b>Bank complaint acknowledgement</b> receipt or complaint docket reference number",
        f"{CHECKBOX}  <b>Any written reply or communication</b> received from the bank or nodal officer",
    ]
    for item in checklist_items:
        story.append(Paragraph(item, meta_style))
        story.append(Spacer(1, 4))

    story.append(Spacer(1, 10))

    story.append(Paragraph("<b>Official Filing Options</b>", heading_style))
    story.append(Spacer(1, 5))

    options_data = [
        [
            Paragraph("<b>Option 1: Online Portal (Fastest)</b>", heading_style),
            Paragraph(
                "Visit the RBI Complaint Management System (CMS) at <b>https://cms.rbi.org.in</b>.<br/>"
                "Click 'File a Complaint', input bank details, upload this PDF and supporting documents.",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>Option 2: Email</b>", heading_style),
            Paragraph(
                "Send your complaint dossier and attachments to the Centralised Receipt and Processing Centre (CRPC) at <b>crpc@rbi.org.in</b>.",
                meta_style,
            ),
        ],
        [
            Paragraph("<b>Option 3: Physical Post</b>", heading_style),
            Paragraph(
                "Send physical complaint form to:<br/>"
                "Centralised Receipt and Processing Centre (CRPC),<br/>"
                "Reserve Bank of India, 4th Floor, Sector 17, Chandigarh - 160017.",
                meta_style,
            ),
        ],
    ]
    opt_table = Table(options_data, colWidths=[160, 350])
    opt_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F8F9FA")),
                ("INNERGRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#EAECF0")),
                ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#D0D5DD")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    story.append(opt_table)
    story.append(Spacer(1, 10))

    notice_style = ParagraphStyle(
        "NoticeStyle",
        parent=styles["Normal"],
        fontName=FONT_BOLD,
        fontSize=8,
        leading=11,
        textColor=colors.HexColor("#B54708"),
        alignment=1,
    )
    story.append(
        Paragraph(
            "Confirm current channels and time limits at rbi.org.in before filing.",
            notice_style,
        )
    )
    story.append(Spacer(1, 10))

    story.append(
        HRFlowable(
            width="100%",
            thickness=0.5,
            color=colors.HexColor("#D0D5DD"),
            spaceBefore=4,
            spaceAfter=4,
        )
    )
    story.append(
        Paragraph(
            "Prepared with Unstuck from details provided by the complainant. This is not legal advice.",
            footer_style,
        )
    )

    doc.build(story)
    return buffer.getvalue()
