package com.ufo.fraudobserver.parser

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.regex.Pattern

data class ParsedPaymentPayload(
    val vpa: String?,
    val payeeName: String?,
    val amount: Double?,
    val isCollectRequest: Boolean,
    val rawTextDump: String
)

object PayloadParser {

    // Regex specified in PRD Module A.4
    private val VPA_PATTERN = Pattern.compile(
        "[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}",
        Pattern.CASE_INSENSITIVE
    )

    private val AMOUNT_PATTERN = Pattern.compile(
        "(?:₹|Rs\\.?|INR)\\s?(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val EXCLUDE_WORDS = listOf(
        "balance", "bal", "available", "limit", "cashback", "reward", "voucher",
        "off", "save", "saving", "upto", "up to", "ending", "a/c", "account", "rate"
    )

    private val COLLECT_KEYWORDS = listOf(
        "requested", "collect", "pay", "mandate", "autopay", "decline"
    )

    /**
     * Extracts VPA from arbitrary string text.
     */
    fun extractVpa(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = VPA_PATTERN.matcher(text)
        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }

    /**
     * Extracts Amount from arbitrary string text.
     * Guaranteed to ignore balances, limits, and promotional cashbacks.
     */
    fun extractAmount(text: CharSequence?): Double? {
        if (text.isNullOrBlank()) return null
        val str = text.toString()
        val lower = str.lowercase()

        // Ignore account balance or reward strings
        if (EXCLUDE_WORDS.any { lower.contains(it) }) return null

        val matcher = AMOUNT_PATTERN.matcher(str)
        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            val parsed = amountStr?.toDoubleOrNull()
            if (parsed != null && parsed in 0.5..1000000.0 && amountStr.length !in listOf(10, 12)) {
                return parsed
            }
        }

        // Support plain numeric amounts entered in input fields (e.g. "500", "500.00", "1,500")
        val clean = str.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("INR", "").replace(",", "").trim()
        val pureNumber = clean.toDoubleOrNull()
        if (pureNumber != null && pureNumber in 1.0..1000000.0 && clean.length !in listOf(10, 12) && !clean.startsWith("0")) {
            if (str.contains("₹") || str.contains("Rs", ignoreCase = true) || str.contains("INR", ignoreCase = true) || clean.matches(Regex("""^\d{1,6}(?:\.\d{1,2})?$"""))) {
                return pureNumber
            }
        }

        return null
    }

    /**
     * Checks if text contains Collect Request indicators.
     */
    fun isCollectRequest(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.toString().lowercase()
        return COLLECT_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Hard constraint: Detects UPI PIN input screens to abort reading.
     */
    fun isPinScreen(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.toString().lowercase()
        return lower.contains("enter upi pin") ||
               lower.contains("enter 4-digit") ||
               lower.contains("enter 6-digit") ||
               lower.contains("upi pin") ||
               lower.contains("enter mpim")
    }

    /**
     * Parses notification content.
     */
    fun parseNotification(title: CharSequence?, text: CharSequence?): ParsedPaymentPayload {
        val combined = "$title $text"
        val vpa = extractVpa(combined)
        val amount = extractAmount(combined)
        val isCollect = isCollectRequest(combined)

        return ParsedPaymentPayload(
            vpa = vpa,
            payeeName = title?.toString(),
            amount = amount,
            isCollectRequest = isCollect,
            rawTextDump = combined
        )
    }

    /**
     * Traverses the active window using Breadth-First Search (BFS) to safely extract payment data.
     * Guaranteed to avoid View ID queries and ignore invisible nodes.
     */
    fun parseAccessibilityTree(rootNode: AccessibilityNodeInfo?): ParsedPaymentPayload? {
        if (rootNode == null) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        val textCollector = StringBuilder()
        var extractedVpa: String? = null
        var isCollect = false
        var candidatePayeeName: String? = null

        var editableEnteredAmount: Double? = null
        var explicitAmountWithSymbol: Double? = null
        var nearbyNumericAmount: Double? = null
        var sawCurrencySymbol = false

        var nodeCount = 0
        val maxNodes = 250 // Hard limit to avoid deep traversal stalls

        try {
            while (queue.isNotEmpty() && nodeCount < maxNodes) {
                val node = queue.poll() ?: continue
                nodeCount++

                // Hard constraint: Skip all password and PIN nodes
                if (node.isPassword) {
                    continue
                }

                // Ignore invisible nodes to prevent false matches from hidden view hierarchies
                if (!node.isVisibleToUser) {
                    continue
                }

                val nodeText = node.text?.toString()
                val contentDesc = node.contentDescription?.toString()

                // Hard constraint: Do nothing on the PIN screen
                if (isPinScreen(nodeText) || isPinScreen(contentDesc)) {
                    return null
                }

                // Priority 1: Check if this node is an editable amount field where user enters the amount
                val isEditable = node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true
                if (isEditable) {
                    val rawInput = (nodeText ?: contentDesc)?.trim()
                    if (!rawInput.isNullOrBlank()) {
                        val clean = rawInput.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("INR", "").replace(",", "").trim()
                        val parsed = clean.toDoubleOrNull()
                        if (parsed != null && parsed in 0.5..1000000.0 && clean.length !in listOf(10, 12)) {
                            editableEnteredAmount = parsed
                        }
                    }
                }

                // Priority 2: Check for currency symbol adjacent to numeric text
                val rawTrimmed = (nodeText ?: contentDesc)?.trim() ?: ""
                if (rawTrimmed in listOf("₹", "Rs.", "Rs", "INR")) {
                    sawCurrencySymbol = true
                } else if (sawCurrencySymbol && nearbyNumericAmount == null && rawTrimmed.isNotBlank()) {
                    val clean = rawTrimmed.replace(",", "").trim()
                    val parsed = clean.toDoubleOrNull()
                    if (parsed != null && parsed in 1.0..1000000.0 && clean.length !in listOf(10, 12)) {
                        nearbyNumericAmount = parsed
                        sawCurrencySymbol = false
                    }
                }

                if (!nodeText.isNullOrBlank()) {
                    textCollector.append(nodeText).append(" ")
                    if (extractedVpa == null) {
                        extractedVpa = extractVpa(nodeText)
                    }
                    if (explicitAmountWithSymbol == null) {
                        explicitAmountWithSymbol = extractAmount(nodeText)
                    }
                    if (!isCollect && isCollectRequest(nodeText)) {
                        isCollect = true
                    }
                    if (candidatePayeeName == null && nodeText.length in 3..40 && !nodeText.contains("@")) {
                        candidatePayeeName = nodeText
                    }
                }

                if (!contentDesc.isNullOrBlank()) {
                    textCollector.append(contentDesc).append(" ")
                    if (extractedVpa == null) {
                        extractedVpa = extractVpa(contentDesc)
                    }
                    if (explicitAmountWithSymbol == null) {
                        explicitAmountWithSymbol = extractAmount(contentDesc)
                    }
                    if (!isCollect && isCollectRequest(contentDesc)) {
                        isCollect = true
                    }
                }

                // Add children to BFS queue
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                }
            }
        } catch (e: Exception) {
            // Defensive recovery if accessibility tree changes dynamically
        }

        val rawDump = textCollector.toString()
        if (extractedVpa == null) {
            extractedVpa = extractVpa(rawDump)
        }

        // Final Amount Resolution:
        // 1. If user typed an amount into an editable field, THAT is 100% the amount they entered.
        // 2. Otherwise use explicit currency symbol amount from payment details.
        // 3. Otherwise use nearby numeric view adjacent to currency symbol.
        // 4. Fallback to raw dump regex.
        val finalAmount = editableEnteredAmount ?: explicitAmountWithSymbol ?: nearbyNumericAmount ?: extractAmount(rawDump)

        return if (extractedVpa != null || isCollect) {
            ParsedPaymentPayload(
                vpa = extractedVpa,
                payeeName = candidatePayeeName,
                amount = finalAmount,
                isCollectRequest = isCollect,
                rawTextDump = rawDump
            )
        } else {
            null
        }
    }
}

