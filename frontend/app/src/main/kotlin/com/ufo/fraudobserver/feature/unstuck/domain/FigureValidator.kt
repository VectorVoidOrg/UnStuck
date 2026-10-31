package com.ufo.fraudobserver.feature.unstuck.domain

import java.util.regex.Pattern

object FigureValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>
    )

    fun validate(
        letterText: String,
        expectedAmount: Double,
        expectedReference: String,
        expectedDate: String,
        compensationAmount: Double
    ): ValidationResult {
        val issues = mutableListOf<String>()

        // 1. Reference Check
        if (expectedReference.isNotBlank()) {
            if (!letterText.contains(expectedReference)) {
                issues.add("Required transaction reference '$expectedReference' was not found.")
            }
        }

        // 2. Currency Amount Check
        val allowedAmounts = setOf(
            expectedAmount,
            100.0,
            0.0,
            compensationAmount,
            expectedAmount + compensationAmount
        )

        val pattern = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(letterText)
        while (matcher.find()) {
            val raw = matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: continue
            val isAllowed = allowedAmounts.any { Math.abs(it - raw) < 0.05 }
            if (!isAllowed) {
                issues.add("Altered or unauthorized currency amount found: Rs. $raw (Allowed: $allowedAmounts)")
            }
        }

        return ValidationResult(issues.isEmpty(), issues)
    }
}

