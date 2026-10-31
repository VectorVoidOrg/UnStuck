package com.ufo.fraudobserver.feature.unstuck.domain

import com.ufo.fraudobserver.feature.unstuck.data.model.CaseEvent
import com.ufo.fraudobserver.feature.unstuck.data.model.EscalationStatus
import com.ufo.fraudobserver.feature.unstuck.data.model.EscalationStep
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

object EscalationEngine {
    fun determineStatus(
        events: List<CaseEvent>,
        caseCreatedAtStr: String,
        ticketNumber: String? = null
    ): EscalationStatus {
        val eventTypes = events.associateBy { it.type }
        val bankComplaintEvent = eventTypes["BANK_COMPLAINT"]
        val complaintLogged = (bankComplaintEvent != null) || !ticketNumber.isNullOrBlank()

        val complaintDate = when {
            bankComplaintEvent != null -> DeadlineEngine.parseDate(bankComplaintEvent.timestamp)
            !ticketNumber.isNullOrBlank() -> DeadlineEngine.parseDate(caseCreatedAtStr)
            else -> null
        }

        val step1Status = if (complaintLogged) "COMPLETED" else "UNLOCKED"

        val nodalEvent = eventTypes["NODAL_OFFICER"]
        val step2Status = when {
            nodalEvent != null -> "COMPLETED"
            complaintLogged -> "UNLOCKED"
            else -> "LOCKED"
        }

        val now = Date()
        val daysElapsed = if (complaintDate != null) {
            val diff = now.time - complaintDate.time
            maxOf(0, (diff / TimeUnit.DAYS.toMillis(1)).toInt())
        } else 0

        val daysRemaining = maxOf(0, 30 - daysElapsed)
        val isThirtyDaysUp = daysElapsed >= 30

        val step3Status = when {
            isThirtyDaysUp -> "COMPLETED"
            complaintLogged -> "IN_PROGRESS"
            else -> "LOCKED"
        }

        val ombudsmanEvent = eventTypes["OMBUDSMAN_PACK"]
        val step4Status = when {
            ombudsmanEvent != null -> "COMPLETED"
            complaintLogged && isThirtyDaysUp -> "UNLOCKED"
            complaintLogged -> "AVAILABLE_ON_DEMAND"
            else -> "LOCKED"
        }

        val (currentStep, recommendation) = when {
            !complaintLogged -> 1 to "File Level 1 Grievance with Bank Branch / Portal"
            step2Status == "UNLOCKED" -> 2 to "Escalate to Principal Nodal Officer"
            isThirtyDaysUp -> 4 to "Generate RBI Integrated Ombudsman Dossier"
            else -> 3 to "Awaiting Bank Resolution ($daysRemaining days remaining in 30-day window)"
        }

        val calTarget = Calendar.getInstance().apply {
            time = complaintDate ?: now
            add(Calendar.DAY_OF_YEAR, 30)
        }

        val steps = mapOf(
            "bank_complaint" to EscalationStep(
                step = 1,
                name = "Bank Grievance",
                status = step1Status,
                completedAt = complaintDate?.let { DeadlineEngine.formatDate(it) },
                ticketNumber = ticketNumber
            ),
            "nodal_officer" to EscalationStep(
                step = 2,
                name = "Principal Nodal Officer",
                status = step2Status,
                completedAt = nodalEvent?.timestamp
            ),
            "thirty_day_mark" to EscalationStep(
                step = 3,
                name = "30-Day Window Tracker",
                status = step3Status,
                daysElapsed = daysElapsed,
                daysRemaining = daysRemaining,
                targetDate = DeadlineEngine.formatDate(calTarget.time)
            ),
            "ombudsman_pack" to EscalationStep(
                step = 4,
                name = "RBI Ombudsman Dossier",
                status = step4Status,
                completedAt = ombudsmanEvent?.timestamp
            )
        )

        return EscalationStatus(currentStep, recommendation, steps)
    }
}

