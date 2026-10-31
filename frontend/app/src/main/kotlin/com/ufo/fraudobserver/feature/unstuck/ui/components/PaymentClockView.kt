package com.ufo.fraudobserver.feature.unstuck.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.feature.unstuck.domain.DeadlineEngine
import com.ufo.fraudobserver.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun PaymentClockView(
    transactionDateStr: String,
    deadlineDateStr: String,
    moneyReturned: Boolean = false,
    reversalDateStr: String? = null,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val txDate = DeadlineEngine.parseDate(transactionDateStr)
    val deadlineDate = DeadlineEngine.parseDate(deadlineDateStr)
    val reversalDate = reversalDateStr?.let { DeadlineEngine.parseDate(it) }

    val totalWindowMs = maxOf(1L, deadlineDate.time - txDate.time)
    val effectiveNowMs = if (txDate.time > currentTime.time) {
        DeadlineEngine.parseDate("2026-10-23 10:00:00").time
    } else {
        currentTime.time
    }

    val (timeString, statusLabel, strokeColor, sweepAngle) = remember(currentTime, moneyReturned, reversalDateStr, effectiveNowMs) {
        if (moneyReturned && reversalDate != null) {
            val isLate = reversalDate.time > deadlineDate.time
            if (isLate) {
                val diff = reversalDate.time - deadlineDate.time
                val days = maxOf(1, (diff / TimeUnit.DAYS.toMillis(1)).toInt())
                Tuple4("Reversed ${days}d late", "REVERSED LATE", UfoWarnText, 360f)
            } else {
                Tuple4("Reversed on time", "REVERSED ON TIME", UfoSafeCheck, 360f)
            }
        } else if (effectiveNowMs < deadlineDate.time) {
            val rem = deadlineDate.time - effectiveNowMs
            val hours = rem / TimeUnit.HOURS.toMillis(1)
            val mins = (rem % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1)
            val secs = (rem % TimeUnit.MINUTES.toMillis(1)) / 1000
            val fraction = minOf(1f, maxOf(0f, (effectiveNowMs - txDate.time).toFloat() / totalWindowMs))
            Tuple4(
                String.format("%02dh %02dm %02ds left", hours, mins, secs),
                if (hours == 0L && mins < 30) "DUE SOON" else "WITHIN TAT WINDOW",
                if (hours == 0L && mins < 30) UfoWarnText else UfoPrimaryIndigo,
                fraction * 360f
            )
        } else {
            val overdueMs = effectiveNowMs - deadlineDate.time
            val days = (overdueMs / TimeUnit.DAYS.toMillis(1)).toInt()
            val hours = (overdueMs % TimeUnit.DAYS.toMillis(1)) / TimeUnit.HOURS.toMillis(1)
            val mins = (overdueMs % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1)
            val secs = (overdueMs % TimeUnit.MINUTES.toMillis(1)) / 1000
            val label = if (days >= 1) "${days}d ${hours}h overdue" else String.format("%02dh %02dm %02ds overdue", hours, mins, secs)
            Tuple4(label, "TAT BREACHED", UfoDangerText, 360f)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(190.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 10.dp.toPx()
            // Track circle
            drawCircle(
                color = UfoBorder,
                style = Stroke(width = strokeWidth)
            )
            // Progress arc
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PAYMENT CLOCK",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = UfoTextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeString,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (statusLabel == "TAT BREACHED") UfoDangerText else UfoTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = strokeColor
            )
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

