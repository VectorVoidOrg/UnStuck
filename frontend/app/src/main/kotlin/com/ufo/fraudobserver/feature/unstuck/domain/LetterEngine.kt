package com.ufo.fraudobserver.feature.unstuck.domain

import com.ufo.fraudobserver.feature.unstuck.data.model.LetterResponse
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LetterEngine {
    fun generate(case: UnstuckCase, level: String = "bank_complaint"): LetterResponse {
        val tx = case.transaction
        val evaluation = case.evaluation
        val daysLate = evaluation?.daysLate ?: 0
        val compAmt = evaluation?.penaltyAmount ?: (daysLate * 100.0)
        val todayStr = SimpleDateFormat("dd MMMM yyyy", Locale.US).format(Date())
        val ticketNo = case.ticketNumber ?: "UNSTUCK-GRV-88219"

        val bankEmail = when (tx.bank.lowercase()) {
            "sbi", "state bank of india" -> "nodalofficer@sbi.co.in"
            "icici", "icici bank" -> "headservicequality@icicibank.com"
            "axis", "axis bank" -> "nodal.officer@axisbank.com"
            else -> "pno@hdfcbank.com"
        }

        val text = when (level) {
            "nodal_officer" -> """
To,
The Principal Nodal Officer,
${tx.bank}
Email: $bankEmail

Subject: Escalation: Unresolved Failed ${tx.rail} Transaction of Rs. ${String.format(Locale.US, "%.2f", tx.amount)} (Ref: ${tx.reference}) - Ticket Ref: $ticketNo

Date: $todayStr

Dear Principal Nodal Officer,

I am writing to formally escalate my grievance regarding an unresolved failed ${tx.rail} transaction. Despite lodging a prior complaint with the bank (Ticket Reference: $ticketNo), the issue remains unresolved beyond the prescribed regulatory Turn Around Time (TAT).

1. Summary of Dispute:
- Customer Name: ${tx.customerName ?: "Account Holder"}
- Contact Details: ${tx.customerPhone ?: "9876543210"} | ${tx.customerEmail ?: "customer@example.com"}
- Account Identifier: ${tx.accountNumber ?: "XX1234"}
- Transaction Date: ${tx.date}
- Transaction Amount: Rs. ${String.format(Locale.US, "%.2f", tx.amount)}
- Reference / UTR Number: ${tx.reference}
- Payment Rail: ${tx.rail}
- Prior Complaint Ticket Number: $ticketNo

2. Non-Compliance with RBI Guidelines:
As per RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20 on "Harmonisation of Turn Around Time (TAT) and customer compensation for failed transactions", the bank was required to auto-reverse the failed transaction within T + ${evaluation?.tatDays ?: 1} calendar day(s).

As of today, the delay stands at $daysLate calendar day(s).

3. Requested Relief:
In accordance with RBI directions:
1. Immediate reversal and restoration of the debited sum of Rs. ${String.format(Locale.US, "%.2f", tx.amount)} to my account.
2. Payment of accrued statutory penalty compensation at the rate of Rs. 100 per day of delay (amounting to Rs. ${String.format(Locale.US, "%.2f", compAmt)} for $daysLate days).

Please be advised that if this escalation is not settled within the regulatory timeframe, this matter will be escalated directly to the Reserve Bank of India Ombudsman under the Reserve Bank - Integrated Ombudsman Scheme, 2021 (RB-IOS, 2021).

Yours faithfully,
${tx.customerName ?: "Account Holder"}
Phone: ${tx.customerPhone ?: "9876543210"}
""".trimIndent()

            "ombudsman" -> """
To,
The Reserve Bank of India Integrated Ombudsman,
Centralised Receipt and Processing Centre (CRPC),
Reserve Bank of India, 4th Floor, Sector 17, Chandigarh - 160017
(Or via Complaint Management System portal: https://cms.rbi.org.in)

Subject: Formal Complaint under Reserve Bank - Integrated Ombudsman Scheme, 2021 against ${tx.bank} for Deficiency in Service and Non-Payment of Mandated Compensation

Date: $todayStr

Respected Ombudsman,

I hereby submit this formal complaint under Clause 10 of the Reserve Bank - Integrated Ombudsman Scheme, 2021 (RB-IOS, 2021) regarding the failure and gross deficiency of service by ${tx.bank} in resolving a failed electronic transaction and refusing/delaying statutory compensation mandated by the Reserve Bank of India.

1. Complainant Information:
- Full Name: ${tx.customerName ?: "Account Holder"}
- Mobile Number: ${tx.customerPhone ?: "9876543210"}
- Email Address: ${tx.customerEmail ?: "customer@example.com"}
- Account / Registered Identifier: ${tx.accountNumber ?: "XX1234"}

2. Regulated Entity Details:
- Bank / Financial Institution: ${tx.bank}
- Primary Redressal Ticket No: $ticketNo

3. Transaction Particulars:
- Transaction Date: ${tx.date}
- Payment Rail: ${tx.rail}
- Disputed Principal Amount: Rs. ${String.format(Locale.US, "%.2f", tx.amount)}
- Reference / UTR Number: ${tx.reference}

4. Statutory Compensation Computation:
- Turnaround Deadline: T + ${evaluation?.tatDays ?: 1} calendar day(s)
- Days Overdue: $daysLate calendar days
- Statutory Delay Compensation Claimed: Rs. ${String.format(Locale.US, "%.2f", compAmt)}

5. Prayers for Relief:
I respectfully pray that the Hon'ble Ombudsman direct ${tx.bank} to:
a) Immediately refund the principal sum of Rs. ${String.format(Locale.US, "%.2f", tx.amount)} to my account.
b) Pay statutory delay compensation of Rs. ${String.format(Locale.US, "%.2f", compAmt)} (at Rs. 100 per day for $daysLate days of delay).
c) Award compensation for hardship incurred under the provisions of the RB-IOS, 2021.

Verification:
I, ${tx.customerName ?: "Account Holder"}, do hereby declare that the particulars stated above are true and correct.

Yours faithfully,
${tx.customerName ?: "Account Holder"}
""".trimIndent()

            else -> """
To,
The Branch Manager / Customer Service Officer,
${tx.bank}

Subject: Immediate Reversal of Failed ${tx.rail} Transaction of Rs. ${String.format(Locale.US, "%.2f", tx.amount)} (Ref: ${tx.reference}) and Compensation under RBI Circular

Date: $todayStr

Dear Sir/Madam,

I am writing to officially register a formal grievance regarding a failed ${tx.rail} transaction debited from my account, which has neither credited the recipient nor been reversed within the statutory Turn Around Time (TAT).

Transaction Particulars:
- Transaction Date & Time: ${tx.date}
- Transaction Amount: Rs. ${String.format(Locale.US, "%.2f", tx.amount)}
- Reference / UTR Number: ${tx.reference}
- Payment Rail: ${tx.rail}
- Failure Description: Account debited but beneficiary not credited / reversal delayed
- Account / Registered Identifier: ${tx.accountNumber ?: "XX1234"}
- Customer Name: ${tx.customerName ?: "Account Holder"}
- Contact Phone: ${tx.customerPhone ?: "9876543210"}
- Contact Email: ${tx.customerEmail ?: "customer@example.com"}

Regulatory Framework and Statutory Turn Around Time (TAT):
Under Reserve Bank of India (RBI) Circular DPSS.CO.PD No.629/02.01.014/2019-20 dated September 20, 2019 ("Harmonisation of Turn Around Time (TAT) and customer compensation for failed transactions"), the stipulated turnaround time for auto-reversal of this ${tx.rail} transaction is T + ${evaluation?.tatDays ?: 1} calendar day(s).

As on date, the transaction is overdue by $daysLate calendar day(s).

Demand for Redressal and Compensation:
In accordance with the aforementioned RBI circular:
1. I request an immediate reversal and credit of the principal debited amount of Rs. ${String.format(Locale.US, "%.2f", tx.amount)} back to my account.
2. I request the statutory compensation of Rs. 100 per day of delay (totalling Rs. ${String.format(Locale.US, "%.2f", compAmt)} for $daysLate days of delay) as mandated by the circular.

Kindly issue a formal grievance acknowledgement and ticket reference number immediately. If this matter is not resolved satisfactorily, I reserve the right to escalate this complaint to the Principal Nodal Officer and subsequently to the RBI Ombudsman under the Reserve Bank - Integrated Ombudsman Scheme, 2021.

Thanking you.

Yours sincerely,
${tx.customerName ?: "Account Holder"}
Phone: ${tx.customerPhone ?: "9876543210"}
Email: ${tx.customerEmail ?: "customer@example.com"}
""".trimIndent()
        }

        // Validate text with FigureValidator
        val validation = FigureValidator.validate(
            letterText = text,
            expectedAmount = tx.amount,
            expectedReference = tx.reference,
            expectedDate = tx.date,
            compensationAmount = compAmt
        )

        return LetterResponse(
            caseId = case.id,
            level = level,
            letterText = text,
            validated = validation.isValid,
            generatedBy = "deterministic_engine",
            recipient = mapOf(
                "bank_name" to tx.bank,
                "email" to bankEmail,
                "portal" to "https://www.bank.com/grievance"
            ),
            warnings = validation.issues
        )
    }
}

