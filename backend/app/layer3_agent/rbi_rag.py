import math
import re
from collections import Counter
from typing import Any, Dict, List
from app.db.mongo import rbi_guidelines_collection


RBI_REGULATORY_KNOWLEDGE_BASE: List[Dict[str, Any]] = [
    {
        "doc_id": "RBI-2017-18-15-CL6",
        "title": "RBI Circular DBR.No.Leg.BC.78/09.07.005/2017-18 — Zero Liability of Customer",
        "tags": ["unauthorized", "zero liability", "hacked", "third party breach", "3 days", "refund", "shadow reversal"],
        "content": (
            "Clause 6 (Zero Liability of a Customer): A customer's entitlement to zero liability shall arise where the "
            "unauthorised transaction occurs due to contributory fraud/negligence/deficiency on the part of the bank, or "
            "third-party breach where the deficiency lies neither with the bank nor with the customer but elsewhere in "
            "the system, and the customer notifies the bank within 3 working days of receiving the communication."
        ),
    },
    {
        "doc_id": "RBI-2017-18-15-CL7",
        "title": "RBI Circular DBR.No.Leg.BC.78/09.07.005/2017-18 — Limited Liability & Social Engineering",
        "tags": ["negligence", "pin shared", "collect request", "qr code", "social engineering", "kyc", "electricity"],
        "content": (
            "Clause 7 (Limited Liability of a Customer): In cases where the loss is due to negligence by a customer, "
            "such as where they have shared payment credentials or approved a deceptive UPI Collect Request, the customer "
            "will bear the loss until they report the unauthorised transaction to the bank. Any loss occurring after the "
            "reporting of the unauthorised transaction shall be borne by the bank. Immediate NPCI Lien Freeze on the "
            "beneficiary account must still be initiated to recover funds before cash-out."
        ),
    },
    {
        "doc_id": "RBI-2017-18-15-CL10",
        "title": "RBI Circular Clause 10 — Shadow Reversal / Provisional Credit within 10 Working Days",
        "tags": ["reversal", "provisional credit", "10 days", "auto refund", "dispute", "value dated"],
        "content": (
            "Clause 10 (Reversal Timeline): The bank shall credit (shadow reversal) the amount involved in the "
            "unauthorised electronic transaction to the customer's account within 10 working days from the date of "
            "such notification by the customer, value-dated to the date of the unauthorised transaction."
        ),
    },
    {
        "doc_id": "NPCI-UDIR-2023-01",
        "title": "NPCI Unified Dispute & Issue Resolution (UDIR) & Real-Time Lien Marking",
        "tags": ["npci", "udir", "lien", "freeze", "beneficiary", "utr", "chargeback", "auto refund"],
        "content": (
            "NPCI UDIR Protocol: Remitter banks and PSP apps can invoke real-time API calls to place an immediate lien "
            "freeze on the fraudulent Payee VPA / beneficiary bank account using the 12-digit UTR number. Before issuing "
            "any automated refund, the remitter system MUST verify that the 12-digit UTR exists as SETTLED in the NPCI "
            "central switch ledger to prevent fake screenshot / fabricated receipt refund attacks."
        ),
    },
    {
        "doc_id": "MHA-I4C-1930-SOP",
        "title": "MHA I4C National Cybercrime Reporting Portal (1930) — Golden Hour Lien & Digital Arrest Advisory",
        "tags": ["digital arrest", "cbi", "police", "customs", "screen share", "anydesk", "1930", "extortion"],
        "content": (
            "MHA I4C & RBI Joint Advisory on 'Digital Arrest' & Screen-Sharing Scams: Law enforcement agencies (CBI, ED, "
            "Police, Customs, RBI) NEVER conduct interrogations via video calls or demand money transfers to 'verification "
            "accounts'. Transactions flagged under active call or screen-sharing (AnyDesk/TeamViewer) must trigger "
            "immediate beneficiary lien freeze and automated 1930 CFCFRMS ticket generation."
        ),
    },
]


class RBIRAGEngine:
    """
    Step 3 Subcomponent 3.4a: RBI & NPCI Regulatory RAG Engine.
    Retrieves relevant Reserve Bank of India (RBI) and NPCI circulars to ground
    the Frontier LLM's dispute analysis in actual Indian banking regulations.
    """

    def __init__(self):
        if rbi_guidelines_collection.count_documents({}) == 0:
            rbi_guidelines_collection.insert_many(RBI_REGULATORY_KNOWLEDGE_BASE)
        self._docs = list(rbi_guidelines_collection.find({}, {"_id": 0}))
        if not self._docs:
            self._docs = list(RBI_REGULATORY_KNOWLEDGE_BASE)

    @staticmethod
    def _tokenize(text: str) -> Counter:
        return Counter(re.findall(r"[a-z0-9]{3,}", text.lower()))

    def retrieve(self, query: str, top_k: int = 3) -> List[Dict[str, Any]]:
        q_vec = self._tokenize(query)
        scored = []
        for doc in self._docs:
            corpus_text = f"{doc.get('title', '')} {' '.join(doc.get('tags', []))} {doc.get('content', '')}"
            d_vec = self._tokenize(corpus_text)
            common = set(q_vec.keys()) & set(d_vec.keys())
            score = sum(q_vec[k] * d_vec[k] for k in common)
            # Tag bonus
            for tag in doc.get("tags", []):
                if tag.lower() in query.lower():
                    score += 5.0
            scored.append((score, doc))

        scored.sort(key=lambda x: x[0], reverse=True)
        return [doc for _, doc in scored[:top_k]]


rbi_rag_engine = RBIRAGEngine()
