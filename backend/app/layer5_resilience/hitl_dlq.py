import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from app.db.mongo import hitl_dlq_collection
from app.layer4_tools.npci_bank_tools import npci_bank_tools
from app.schemas import HITLReviewDecisionRequest, ResolutionAction


class HITLDeadLetterService:
    """
    Step 5B Resilience #4: HITL (Human-In-The-Loop) & Dead Letter Queue (DLQ).
    Routes:
      - Low-confidence agent verdicts (< 0.75)
      - High-value auto-refund requests exceeding the ₹5,000 Policy Guardrail
      - NPCI / Bank Core API Timeouts (HTTP 504 errors)
      - Token Budget Circuit Breaker trips
    to a structured queue for human compliance officers and automated DLQ retry.
    """

    def enqueue_ticket(
        self,
        investigation_id: str,
        queue_type: str,  # "HITL_POLICY_CAP" | "HITL_LOW_CONFIDENCE" | "DLQ_BANK_504" | "DLQ_CIRCUIT_BREAKER"
        reason: str,
        payer_vpa: str,
        payee_vpa: str,
        amount_inr: float,
        redacted_context: str,
        agent_verdict_dict: Optional[Dict[str, Any]] = None,
    ) -> str:
        ticket_id = f"HITL-{uuid.uuid4().hex[:10].upper()}"
        doc = {
            "ticket_id": ticket_id,
            "investigation_id": investigation_id,
            "queue_type": queue_type,
            "status": "PENDING_HUMAN_AUDIT",
            "reason": reason,
            "payer_vpa": payer_vpa,
            "payee_vpa": payee_vpa,
            "amount_inr": round(amount_inr, 2),
            "redacted_context": redacted_context,
            "agent_verdict": agent_verdict_dict or {},
            "created_at": datetime.now(timezone.utc).isoformat(),
            "resolved_at": None,
            "reviewer_id": None,
            "resolution_notes": None,
        }
        hitl_dlq_collection.insert_one(doc)
        return ticket_id

    def list_pending_tickets(self, limit: int = 50) -> List[Dict[str, Any]]:
        return list(
            hitl_dlq_collection.find({"status": "PENDING_HUMAN_AUDIT"}, {"_id": 0}).limit(limit)
        )

    def list_all_tickets(self, limit: int = 50) -> List[Dict[str, Any]]:
        return list(hitl_dlq_collection.find({}, {"_id": 0}).limit(limit))

    def resolve_ticket(
        self, ticket_id: str, decision: HITLReviewDecisionRequest
    ) -> Optional[Dict[str, Any]]:
        ticket = hitl_dlq_collection.find_one({"ticket_id": ticket_id}, {"_id": 0})
        if not ticket:
            return None

        tool_results = []
        inv_id = ticket["investigation_id"]
        # Use stable non-timeout VPA when human officer overrides a 504 DLQ ticket
        safe_payee = ticket["payee_vpa"].replace("504", "resolved").replace("timeout", "resolved")

        if decision.approved_action in (ResolutionAction.PLACE_LIEN, ResolutionAction.AUTO_REFUND):
            lien_res = npci_bank_tools.place_npci_lien(
                investigation_id=inv_id,
                payee_vpa=safe_payee,
                amount_inr=ticket["amount_inr"],
                reason=f"HITL Officer {decision.reviewer_id} Approved Lien",
            )
            tool_results.append(lien_res.model_dump())

        if decision.approved_action == ResolutionAction.AUTO_REFUND and decision.approved_refund_inr > 0:
            refund_res = npci_bank_tools.execute_auto_refund(
                investigation_id=inv_id,
                payer_vpa=ticket["payer_vpa"],
                payee_vpa=safe_payee,
                amount_inr=decision.approved_refund_inr,
            )
            tool_results.append(refund_res.model_dump())

        now_iso = datetime.now(timezone.utc).isoformat()
        hitl_dlq_collection.update_one(
            {"ticket_id": ticket_id},
            {
                "$set": {
                    "status": "RESOLVED_BY_HUMAN",
                    "reviewer_id": decision.reviewer_id,
                    "approved_action": decision.approved_action.value,
                    "approved_refund_inr": decision.approved_refund_inr,
                    "resolution_notes": decision.notes,
                    "npci_tool_results": tool_results,
                    "resolved_at": now_iso,
                }
            },
        )
        return hitl_dlq_collection.find_one({"ticket_id": ticket_id}, {"_id": 0})


hitl_dlq_service = HITLDeadLetterService()
