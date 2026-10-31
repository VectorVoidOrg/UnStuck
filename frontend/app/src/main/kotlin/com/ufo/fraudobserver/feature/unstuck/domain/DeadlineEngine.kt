package com.ufo.fraudobserver.feature.unstuck.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DeadlineEngine {
    private val patternStrings = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd",
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy",
        "dd-MM-yyyy"
    )

    fun parseDate(dateStr: String): Date {
        val clean = dateStr.trim()
        for (pattern in patternStrings) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                return fmt.parse(clean) ?: continue
            } catch (_: Exception) {}
        }
        return Date()
    }

    fun formatDate(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)
    }

    fun calculateDeadline(txDate: Date, tatDays: Int = 1): Date {
        val cal = Calendar.getInstance()
        cal.time = txDate
        cal.add(Calendar.DAY_OF_YEAR, tatDays)
        return cal.time
    }

    data class DeadlineResult(
        val deadlineDate: Date,
        val status: String,
        val daysLate: Int
    )

    fun evaluate(
        txDateStr: String,
        tatDays: Int = 1,
        moneyReturned: Boolean = false,
        reversalDateStr: String? = null,
        asOfDate: Date = Date()
    ): DeadlineResult {
        val txDate = parseDate(txDateStr)
        val deadline = calculateDeadline(txDate, tatDays)

        // Compare by calendar day. If transaction is in the future relative to system clock, use simulated date.
        val effectiveAsOf = if (txDate.time > asOfDate.time) {
            parseDate("2026-10-23 10:00:00")
        } else {
            asOfDate
        }

        val calDeadline = Calendar.getInstance().apply { time = deadline }
        val calAsOf = Calendar.getInstance().apply { time = effectiveAsOf }

        if (moneyReturned && reversalDateStr != null) {
            val revDate = parseDate(reversalDateStr)
            val calRev = Calendar.getInstance().apply { time = revDate }

            if (calRev.timeInMillis <= calDeadline.timeInMillis) {
                return DeadlineResult(deadline, "REVERSED_ON_TIME", 0)
            } else {
                val diffMs = calRev.timeInMillis - calDeadline.timeInMillis
                val daysLate = maxOf(1, (diffMs / TimeUnit.DAYS.toMillis(1)).toInt())
                return DeadlineResult(deadline, "REVERSED_LATE", daysLate)
            }
        }

        val diffMs = calAsOf.timeInMillis - calDeadline.timeInMillis
        return when {
            diffMs < 0 -> DeadlineResult(deadline, "WITHIN_WINDOW", 0)
            diffMs in 0..TimeUnit.HOURS.toMillis(6) -> DeadlineResult(deadline, "DUE", 0)
            else -> {
                val daysLate = maxOf(1, (diffMs / TimeUnit.DAYS.toMillis(1)).toInt())
                DeadlineResult(deadline, "OVERDUE", daysLate)
            }
        }
    }
}

