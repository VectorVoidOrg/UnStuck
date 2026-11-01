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
     */
    fun extractAmount(text: CharSequence?): Double? {
        if (text.isNullOrBlank()) return null
        val matcher = AMOUNT_PATTERN.matcher(text)
        return if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            amountStr?.toDoubleOrNull()
        } else {
            null
        }
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
        var extractedAmount: Double? = null
        var isCollect = false
        var candidatePayeeName: String? = null

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

                if (!nodeText.isNullOrBlank()) {
                    textCollector.append(nodeText).append(" ")
                    if (extractedVpa == null) {
                        extractedVpa = extractVpa(nodeText)
                    }
                    if (extractedAmount == null) {
                        extractedAmount = extractAmount(nodeText)
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
                    if (extractedAmount == null) {
                        extractedAmount = extractAmount(contentDesc)
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
        if (extractedAmount == null) {
            extractedAmount = extractAmount(rawDump)
        }

        return if (extractedVpa != null || isCollect) {
            ParsedPaymentPayload(
                vpa = extractedVpa,
                payeeName = candidatePayeeName,
                amount = extractedAmount,
                isCollectRequest = isCollect,
                rawTextDump = rawDump
            )
        } else {
            null
        }
    }
}

