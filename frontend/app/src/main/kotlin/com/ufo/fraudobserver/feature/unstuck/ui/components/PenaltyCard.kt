package com.ufo.fraudobserver.feature.unstuck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.ui.theme.*
import java.util.Locale

@Composable
fun PenaltyCard(
    daysLate: Int,
    principalAmount: Double,
    moneyReturned: Boolean,
    modifier: Modifier = Modifier
) {
    val penaltyOwed = maxOf(0.0, daysLate * 100.0)
    val totalClaim = if (moneyReturned) penaltyOwed else (principalAmount + penaltyOwed)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Statutory Compensation Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                Text(
                    text = "RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(UfoIndigoSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "₹100/Day",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = UfoPrimaryIndigo
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Days Late
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UfoBg)
                    .border(1.dp, UfoBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "DELAY PERIOD",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextMuted
                )
                Text(
                    text = "$daysLate Days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = UfoTextPrimary
                )
                Text(
                    text = "Past TAT cutoff",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }

            // Penalty Owed
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UfoWarnBg)
                    .border(1.dp, UfoWarnText.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PENALTY DUE",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = UfoWarnText
                )
                Text(
                    text = "₹${String.format(Locale.US, "%.0f", penaltyOwed)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = UfoWarnText
                )
                Text(
                    text = "$daysLate × ₹100/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoWarnText
                )
            }

            // Total Claim
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UfoIndigoSoft)
                    .border(1.dp, UfoIndigoBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = if (moneyReturned) "TOTAL PENALTY" else "TOTAL CLAIM",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = UfoPrimaryIndigo
                )
                Text(
                    text = "₹${String.format(Locale.US, "%.2f", totalClaim)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = UfoPrimaryIndigo
                )
                Text(
                    text = if (moneyReturned) "Principal refunded" else "Principal + Penalty",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoIndigoDark
                )
            }
        }
    }
}

