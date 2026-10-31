package com.ufo.fraudobserver.feature.unstuck

import com.ufo.fraudobserver.feature.unstuck.data.model.CaseEvent
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckCase
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckTransaction
import com.ufo.fraudobserver.feature.unstuck.domain.DeadlineEngine
import com.ufo.fraudobserver.feature.unstuck.domain.EscalationEngine
import com.ufo.fraudobserver.feature.unstuck.domain.FigureValidator
import com.ufo.fraudobserver.feature.unstuck.domain.LetterEngine
import com.ufo.fraudobserver.feature.unstuck.domain.PenaltyEngine
import org.junit.Assert.*
import org.junit.Test

class UnstuckEngineTests {

    @Test
    fun testDeadlineEngine_CalculatesCalendarDaysCorrectly() {
        // Transaction on 2026-10-15 10:00:00 with T+1
        val result = DeadlineEngine.evaluate(
            txDateStr = "2026-10-15 10:00:00",
            tatDays = 1,
            moneyReturned = false,
            reversalDateStr = null
        )

        assertNotNull(result.deadlineDate)
        val formatted = DeadlineEngine.formatDate(result.deadlineDate)
        assertTrue(formatted.startsWith("2026-10-16"))
        assertEquals("OVERDUE", result.status)
        assertTrue("Days late should be positive", result.daysLate > 0)
    }

    @Test
    fun testDeadlineEngine_ReversedWithinTAT() {
        val result = DeadlineEngine.evaluate(
            txDateStr = "2026-10-15 10:00:00",
            tatDays = 1,
            moneyReturned = true,
            reversalDateStr = "2026-10-15 18:00:00"
        )

        assertEquals("REVERSED_ON_TIME", result.status)
        assertEquals(0, result.daysLate)
    }

    @Test
    fun testPenaltyEngine_OneHundredRupeesPerDay() {
        assertEquals(0.0, PenaltyEngine.calculate(0), 0.001)
        assertEquals(100.0, PenaltyEngine.calculate(1), 0.001)
        assertEquals(500.0, PenaltyEngine.calculate(5), 0.001)
        assertEquals(1200.0, PenaltyEngine.calculate(12), 0.001)
    }

    @Test
    fun testFigureValidator_ValidationIntegrity() {
        val sampleLetter = "Dear Bank, transaction Ref 428912345678 of Rs. 1500.00 on 2026-10-15 is overdue. Compensation Rs. 300.00"
        val validResult = FigureValidator.validate(
            letterText = sampleLetter,
            expectedAmount = 1500.0,
            expectedReference = "428912345678",
            expectedDate = "2026-10-15",
            compensationAmount = 300.0
        )
        assertTrue(validResult.isValid)
        assertTrue(validResult.issues.isEmpty())

        // Tampered letter with unauthorized amounts
        val tamperedLetter = "Dear Bank, Ref 428912345678 of Rs. 99999.00 is overdue."
        val invalidResult = FigureValidator.validate(
            letterText = tamperedLetter,
            expectedAmount = 1500.0,
            expectedReference = "428912345678",
            expectedDate = "2026-10-15",
            compensationAmount = 300.0
        )
        assertFalse(invalidResult.isValid)
        assertTrue(invalidResult.issues.isNotEmpty())
    }

    @Test
    fun testEscalationEngine_Tiers() {
        // Without ticket, level 1 (step 1) should be active
        val status1 = EscalationEngine.determineStatus(
            events = emptyList(),
            caseCreatedAtStr = "2026-10-15 10:00:00",
            ticketNumber = null
        )
        assertEquals(1, status1.currentStep)

        // With bank ticket logged, step 2 (Nodal Officer) is recommended
        val status2 = EscalationEngine.determineStatus(
            events = listOf(
                CaseEvent(
                    id = "e1",
                    caseId = "c1",
                    timestamp = "2026-10-15 10:00:00",
                    type = "BANK_COMPLAINT",
                    description = "Ticket logged"
                )
            ),
            caseCreatedAtStr = "2026-10-15 10:00:00",
            ticketNumber = "HDFC-GRV-1234"
        )
        assertEquals(2, status2.currentStep)
        assertEquals("Escalate to Principal Nodal Officer", status2.recommendation)

        // When nodal officer escalated and 35 days elapsed (> 30 days statutory period)
        val status3 = EscalationEngine.determineStatus(
            events = listOf(
                CaseEvent(
                    id = "e1",
                    caseId = "c1",
                    timestamp = "2026-09-01 10:00:00",
                    type = "BANK_COMPLAINT",
                    description = "Ticket logged"
                ),
                CaseEvent(
                    id = "e2",
                    caseId = "c1",
                    timestamp = "2026-09-05 10:00:00",
                    type = "NODAL_OFFICER",
                    description = "Nodal officer ticket logged"
                )
            ),
            caseCreatedAtStr = "2026-09-01 10:00:00",
            ticketNumber = "HDFC-GRV-1234"
        )
        assertEquals(4, status3.currentStep)
    }

    @Test
    fun testLetterEngine_GeneratesCompliantNotice() {
        val tx = UnstuckTransaction(
            amount = 1500.0,
            reference = "428912345678",
            bank = "HDFC Bank",
            date = "2026-10-15 14:32:10",
            rail = "UPI",
            status = "FAILED",
            customerName = "Rohit Sharma",
            accountNumber = "XX1234"
        )
        val caseData = UnstuckCase(
            id = "CASE-TEST",
            createdAt = "2026-10-15 14:35:00",
            updatedAt = "2026-10-15 14:35:00",
            transaction = tx,
            ruleId = "upi_p2p_failed",
            customerRole = "Remitter",
            moneyReturned = false,
            ticketNumber = "HDFC-GRV-88219"
        )

        val letter = LetterEngine.generate(caseData, "bank_complaint")
        assertNotNull(letter.letterText)
        assertTrue(letter.letterText.contains("Rohit Sharma"))
        assertTrue(letter.letterText.contains("428912345678"))
        assertTrue(letter.letterText.contains("1500"))
        assertTrue(letter.letterText.contains("DPSS.CO.PD No.629/02.01.014/2019-20"))
    }
}
