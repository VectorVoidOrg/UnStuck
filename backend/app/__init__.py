"""
UFO (Unified Fraud Observer) — Production-Grade Agentic Fraud & Scam Resolution Backend.
Implements the 5-Layer System Design Framework:
1. API Gateway & Deterministic Rule Engine (<50ms)
2. PII Redaction & Token Vault + Edge OCR Masking
3. Fraud Investigation Agent Core (Semantic Cache + Complexity Router + SLM + Frontier LLM with RBI RAG)
4. NPCI & Bank Core Tools (Strict JSON Schema + Idempotent Lien & Settlement)
5. Resilience & Audit Layer (Token Budget Circuit Breaker, ₹5,000 Policy Guardrail, Async Event Queue, HITL & DLQ)
"""

__version__ = "4.0.0"
__project__ = "UFO"
