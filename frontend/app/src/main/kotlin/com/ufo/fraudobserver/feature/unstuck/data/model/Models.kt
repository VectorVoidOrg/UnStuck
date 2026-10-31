package com.ufo.fraudobserver.feature.unstuck.data.model

import com.google.gson.annotations.SerializedName

data class UnstuckTransaction(
    @SerializedName("amount") var amount: Double,
    @SerializedName("reference") var reference: String,
    @SerializedName("bank") var bank: String,
    @SerializedName("date") var date: String,
    @SerializedName("rail") var rail: String = "UPI",
    @SerializedName("status") var status: String = "FAILED",
    @SerializedName("customer_name") var customerName: String? = "Rohit Sharma",
    @SerializedName("customer_email") var customerEmail: String? = "rohit@example.com",
    @SerializedName("customer_phone") var customerPhone: String? = "9876543210",
    @SerializedName("account_number") var accountNumber: String? = "XX1234"
)

data class ExtractResponse(
    @SerializedName("transaction") val transaction: UnstuckTransaction,
    @SerializedName("confidence") val confidence: Double = 0.95,
    @SerializedName("confidence_breakdown") val confidenceBreakdown: Map<String, Double> = emptyMap(),
    @SerializedName("flags") val flags: List<String> = emptyList(),
    @SerializedName("crosscheck_status") val crosscheckStatus: String = "MATCH",
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("risk_score") val riskScore: Double = 0.05,
    @SerializedName("risk_factors") val riskFactors: List<String> = emptyList()
)

data class CaseEvent(
    @SerializedName("id") val id: String,
    @SerializedName("case_id") val caseId: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("type") val type: String,
    @SerializedName("description") val description: String,
    @SerializedName("details") val details: Map<String, Any> = emptyMap()
)

data class EvaluationResult(
    @SerializedName("deadline_date") val deadlineDate: String,
    @SerializedName("tat_days") val tatDays: Int = 1,
    @SerializedName("day_type") val dayType: String = "calendar",
    @SerializedName("status") val status: String, // WITHIN_WINDOW, DUE, OVERDUE, REVERSED_ON_TIME, REVERSED_LATE
    @SerializedName("days_late") val daysLate: Int = 0,
    @SerializedName("penalty_amount") val penaltyAmount: Double = 0.0,
    @SerializedName("responsible_party") val responsibleParty: String = "Beneficiary Bank / Remitter Bank",
    @SerializedName("circular_reference") val circularReference: String = "RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20",
    @SerializedName("rule_id") val ruleId: String? = null
)

data class UnstuckCase(
    @SerializedName("id") val id: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("transaction") val transaction: UnstuckTransaction,
    @SerializedName("rule_id") val ruleId: String = "upi_p2p_failed",
    @SerializedName("customer_role") val customerRole: String = "Remitter",
    @SerializedName("money_returned") var moneyReturned: Boolean = false,
    @SerializedName("reversal_date") var reversalDate: String? = null,
    @SerializedName("evaluation") var evaluation: EvaluationResult? = null,
    @SerializedName("events") var events: MutableList<CaseEvent> = mutableListOf(),
    @SerializedName("ticket_number") var ticketNumber: String? = null,
    @SerializedName("notes") var notes: String? = null
)

data class CreateCaseRequest(
    @SerializedName("transaction") val transaction: UnstuckTransaction,
    @SerializedName("rule_id") val ruleId: String,
    @SerializedName("customer_role") val customerRole: String = "Remitter",
    @SerializedName("money_returned") val moneyReturned: Boolean = false,
    @SerializedName("reversal_date") val reversalDate: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("ticket_number") val ticketNumber: String? = null
)

data class Rule(
    @SerializedName("id") val id: String,
    @SerializedName("rail") val rail: String,
    @SerializedName("sub_type") val subType: String? = null,
    @SerializedName("failure_type") val failureType: String,
    @SerializedName("deadline_days") val deadlineDays: Int,
    @SerializedName("day_type") val dayType: String = "calendar",
    @SerializedName("compensation") val compensation: String,
    @SerializedName("penalty_per_day") val penaltyPerDay: Double = 100.0,
    @SerializedName("responsible_party") val responsibleParty: String,
    @SerializedName("circular_row") val circularRow: String,
    @SerializedName("source_link") val sourceLink: String,
    @SerializedName("last_verified_date") val lastVerifiedDate: String,
    @SerializedName("status") val status: String = "active",
    @SerializedName("customer_role") val customerRole: String? = null
)

data class Bank(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("aliases") val aliases: List<String> = emptyList(),
    @SerializedName("nodal_officer_email") val nodalOfficerEmail: String,
    @SerializedName("nodal_officer_name") val nodalOfficerName: String? = null,
    @SerializedName("grievance_page_link") val grievancePageLink: String,
    @SerializedName("customer_care_phone") val customerCarePhone: String? = null
)

data class EscalationStep(
    @SerializedName("step") val step: Int,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("days_elapsed") val daysElapsed: Int? = null,
    @SerializedName("days_remaining") val daysRemaining: Int? = null,
    @SerializedName("target_date") val targetDate: String? = null
)

data class EscalationStatus(
    @SerializedName("current_step") val currentStep: Int,
    @SerializedName("recommendation") val recommendation: String,
    @SerializedName("steps") val steps: Map<String, EscalationStep>
)

data class LetterResponse(
    @SerializedName("case_id") val caseId: String,
    @SerializedName("level") val level: String,
    @SerializedName("letter_text") val letterText: String,
    @SerializedName("validated") val validated: Boolean,
    @SerializedName("generated_by") val generatedBy: String,
    @SerializedName("recipient") val recipient: Map<String, String>,
    @SerializedName("warnings") val warnings: List<String> = emptyList()
)

data class ComplaintLetter(
    val title: String,
    val level: String,
    val text: String,
    val targetRecipient: String,
    val warnings: List<String>
)

