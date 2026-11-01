import io
from datetime import datetime
from typing import List, Optional
from reportlab.lib.pagesizes import letter, A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable, KeepTogether
)
from app.schemas import Case, Event

def render_letter_pdf(letter_text: str, case: Case) -> bytes:
    """
    Renders formal legal complaint letter as an A4 PDF with transaction summary table.
    """
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        rightMargin=40,
        leftMargin=40,
        topMargin=40,
        bottomMargin=40
    )
    
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=15,
        leading=18,
        textColor=colors.HexColor('#0F172A'),
        alignment=0,
        spaceAfter=4
    )
    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9,
        leading=12,
        textColor=colors.HexColor('#2563EB'),
        spaceAfter=12
    )
    body_style = ParagraphStyle(
        'BodyDark',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=14,
        textColor=colors.HexColor('#1E293B'),
        spaceAfter=8
    )
    table_label_style = ParagraphStyle(
        'TableLabel',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8.5,
        leading=11,
        textColor=colors.HexColor('#334155')
    )
    table_val_style = ParagraphStyle(
        'TableVal',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=11,
        textColor=colors.HexColor('#0F172A')
    )

    story = []

    # Title & Legal Citation
    story.append(Paragraph("FORMAL GRIEVANCE & STATUTORY COMPENSATION DEMAND", title_style))
    story.append(Paragraph("Pursuant to RBI Harmonisation Circular DPSS.CO.PD No.629/02.01.014/2019-20", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#CBD5E1'), spaceAfter=10))

    # Transaction Summary Table
    tx = case.transaction
    eval_res = case.evaluation
    comp_amt = eval_res.penalty_amount if eval_res else 0.0
    days_late = eval_res.days_late if eval_res else 0

    table_data = [
        [
            Paragraph("Bank Name", table_label_style),
            Paragraph(str(tx.bank), table_val_style),
            Paragraph("Payment Rail", table_label_style),
            Paragraph(str(tx.rail or "UPI"), table_val_style),
        ],
        [
            Paragraph("Reference / UTR", table_label_style),
            Paragraph(str(tx.reference), table_val_style),
            Paragraph("Transaction Amount", table_label_style),
            Paragraph(f"INR {tx.amount:,.2f}", table_val_style),
        ],
        [
            Paragraph("Transaction Date", table_label_style),
            Paragraph(str(tx.date), table_val_style),
            Paragraph("Days Overdue", table_label_style),
            Paragraph(f"{days_late} Days", table_val_style),
        ],
        [
            Paragraph("Statutory Penalty", table_label_style),
            Paragraph(f"INR {comp_amt:,.2f} (@ Rs 100/day)", table_val_style),
            Paragraph("Current Status", table_label_style),
            Paragraph(str(eval_res.status if eval_res else "OVERDUE"), table_val_style),
        ]
    ]

    t = Table(table_data, colWidths=[110, 145, 110, 150])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor('#F8FAFC')),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E2E8F0')),
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor('#CBD5E1')),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
    ]))
    story.append(t)
    story.append(Spacer(1, 12))

    # Letter Body Paragraphs
    paragraphs = letter_text.split('\n\n')
    for p_text in paragraphs:
        cleaned = p_text.strip()
        if not cleaned:
            continue
        # Convert internal single line breaks to HTML breaks for reportlab
        formatted = cleaned.replace('\n', '<br/>')
        story.append(Paragraph(formatted, body_style))

    doc.build(story)
    buffer.seek(0)
    return buffer.getvalue()


def render_ombudsman_pack_pdf(case: Case, events: List[Event]) -> bytes:
    """
    Renders comprehensive RBI Ombudsman Dossier as an A4 PDF with transaction
    table, full chronological journey timeline, and statutory penalty calculations.
    """
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        rightMargin=36,
        leftMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=15,
        leading=18,
        textColor=colors.HexColor('#0F172A'),
        spaceAfter=4
    )
    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        leading=13,
        textColor=colors.HexColor('#1D4ED8'),
        spaceAfter=10
    )
    section_heading = ParagraphStyle(
        'SecHeading',
        parent=styles['Heading2'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=14,
        textColor=colors.HexColor('#1E293B'),
        spaceBefore=10,
        spaceAfter=6
    )
    body_style = ParagraphStyle(
        'Body',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor('#334155'),
        spaceAfter=4
    )
    table_cell = ParagraphStyle(
        'TCell',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8,
        leading=10,
        textColor=colors.HexColor('#0F172A')
    )
    table_cell_bold = ParagraphStyle(
        'TCellBold',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8,
        leading=10,
        textColor=colors.HexColor('#1E293B')
    )

    story = []

    # Cover Title
    story.append(Paragraph("RESERVE BANK - INTEGRATED OMBUDSMAN SCHEME, 2021", title_style))
    story.append(Paragraph("COMPREHENSIVE COMPLAINT DOSSIER & EVIDENCE PACK", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#1D4ED8'), spaceAfter=8))

    # Case Summary Details
    tx = case.transaction
    eval_res = case.evaluation
    comp_amt = eval_res.penalty_amount if eval_res else 0.0
    days_late = eval_res.days_late if eval_res else 0

    meta_table = [
        [
            Paragraph("Dossier ID:", table_cell_bold),
            Paragraph(f"DOSSIER-{case.id[:8].upper()}", table_cell),
            Paragraph("Date of Dossier:", table_cell_bold),
            Paragraph(datetime.now().strftime("%Y-%m-%d"), table_cell),
        ],
        [
            Paragraph("Complainant:", table_cell_bold),
            Paragraph(tx.customer_name or "Account Holder", table_cell),
            Paragraph("Contact:", table_cell_bold),
            Paragraph(f"{tx.customer_phone} | {tx.customer_email}", table_cell),
        ],
        [
            Paragraph("Respondent Bank:", table_cell_bold),
            Paragraph(tx.bank, table_cell),
            Paragraph("Bank Ticket Ref:", table_cell_bold),
            Paragraph(case.ticket_number or "UNSTUCK-GRV-88219", table_cell),
        ],
        [
            Paragraph("Disputed Amount:", table_cell_bold),
            Paragraph(f"INR {tx.amount:,.2f}", table_cell),
            Paragraph("Penalty Claimed:", table_cell_bold),
            Paragraph(f"INR {comp_amt:,.2f} ({days_late} days @ Rs 100/day)", table_cell),
        ],
    ]
    t_meta = Table(meta_table, colWidths=[105, 155, 105, 155])
    t_meta.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor('#F1F5F9')),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#CBD5E1')),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
    ]))
    story.append(t_meta)
    story.append(Spacer(1, 8))

    # Section 1: Transaction Particulars
    story.append(Paragraph("1. Disputed Transaction Particulars", section_heading))
    tx_table = [
        [Paragraph("Transaction Date", table_cell_bold), Paragraph(str(tx.date), table_cell)],
        [Paragraph("Payment Rail", table_cell_bold), Paragraph(str(tx.rail or "UPI"), table_cell)],
        [Paragraph("Reference / UTR No.", table_cell_bold), Paragraph(str(tx.reference), table_cell)],
        [Paragraph("Account Identifier", table_cell_bold), Paragraph(str(tx.account_number or "XX1234"), table_cell)],
        [Paragraph("Turn Around Time (TAT)", table_cell_bold), Paragraph(f"T + {eval_res.tat_days if eval_res else 1} calendar day(s)", table_cell)],
        [Paragraph("Statutory Deadline", table_cell_bold), Paragraph(eval_res.deadline_date.strftime("%Y-%m-%d") if eval_res else "T+1", table_cell)],
        [Paragraph("Regulatory Circular", table_cell_bold), Paragraph(eval_res.circular_reference if eval_res else "RBI/2019-20/67", table_cell)],
    ]
    t_tx = Table(tx_table, colWidths=[150, 370])
    t_tx.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor('#FFFFFF')),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E2E8F0')),
        ('TOPPADDING', (0, 0), (-1, -1), 3),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
    ]))
    story.append(t_tx)
    story.append(Spacer(1, 8))

    # Section 2: Journey Timeline
    story.append(Paragraph("2. Dispute & Escalation Journey Timeline", section_heading))
    timeline_rows = [
        [
            Paragraph("Timestamp", table_cell_bold),
            Paragraph("Event Type", table_cell_bold),
            Paragraph("Description & Recorded Details", table_cell_bold)
        ]
    ]

    # Include transaction creation
    timeline_rows.append([
        Paragraph(str(tx.date), table_cell),
        Paragraph("TRANSACTION_FAILED", table_cell_bold),
        Paragraph(f"Payment of Rs. {tx.amount:,.2f} debited; failed to credit beneficiary (Ref: {tx.reference}).", table_cell)
    ])

    for e in events:
        details_str = f" | {e.details}" if e.details else ""
        timeline_rows.append([
            Paragraph(e.timestamp.strftime("%Y-%m-%d %H:%M"), table_cell),
            Paragraph(e.type, table_cell_bold),
            Paragraph(f"{e.description}{details_str}", table_cell)
        ])

    t_timeline = Table(timeline_rows, colWidths=[100, 130, 290])
    t_timeline.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#E2E8F0')),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#CBD5E1')),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#F8FAFC')]),
        ('TOPPADDING', (0, 0), (-1, -1), 3),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
    ]))
    story.append(t_timeline)
    story.append(Spacer(1, 8))

    # Section 3: Statutory Compensation Calculation Table
    story.append(Paragraph("3. Statutory Delay Compensation Breakdown", section_heading))
    calc_rows = [
        [Paragraph("Item", table_cell_bold), Paragraph("Details", table_cell_bold)],
        [Paragraph("Governing Provision", table_cell), Paragraph("RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20", table_cell)],
        [Paragraph("Turn Around Time Mandate", table_cell), Paragraph(f"Auto-reversal required within T + {eval_res.tat_days if eval_res else 1} calendar day(s)", table_cell)],
        [Paragraph("Days Delayed Beyond TAT", table_cell), Paragraph(f"{days_late} calendar days", table_cell)],
        [Paragraph("Statutory Penalty Rate", table_cell), Paragraph("Rs. 100 per calendar day of delay", table_cell)],
        [Paragraph("Total Penalty Compensation", table_cell_bold), Paragraph(f"INR {comp_amt:,.2f}", table_cell_bold)],
        [Paragraph("Total Claim (Principal + Penalty)", table_cell_bold), Paragraph(f"INR {(tx.amount + comp_amt):,.2f}", table_cell_bold)]
    ]
    t_calc = Table(calc_rows, colWidths=[180, 340])
    t_calc.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#DBEAFE')),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#BFDBFE')),
        ('TOPPADDING', (0, 0), (-1, -1), 3),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
    ]))
    story.append(t_calc)
    story.append(Spacer(1, 10))

    # Declaration & Prayer
    story.append(Paragraph("4. Prayer for Relief", section_heading))
    story.append(Paragraph(
        "The complainant respectfully prays that the Hon'ble Ombudsman direct the respondent bank to: "
        "(1) Immediately refund the principal amount of Rs. " + f"{tx.amount:,.2f}" + " to the complainant's account; "
        "(2) Pay the accrued statutory penalty of Rs. " + f"{comp_amt:,.2f}" + " in accordance with RBI guidelines; "
        "and (3) Award appropriate damages for the hardship and delay experienced.",
        body_style
    ))
    story.append(Spacer(1, 10))

    doc.build(story)
    buffer.seek(0)
    return buffer.getvalue()
