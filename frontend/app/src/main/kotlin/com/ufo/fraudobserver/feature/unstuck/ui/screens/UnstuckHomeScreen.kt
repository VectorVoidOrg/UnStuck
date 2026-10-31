package com.ufo.fraudobserver.feature.unstuck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckCase
import com.ufo.fraudobserver.ui.components.UfoIcons
import com.ufo.fraudobserver.ui.theme.*
import java.util.Locale

@Composable
fun UnstuckHomeScreen(
    cases: List<UnstuckCase>,
    onOpenScanner: () -> Unit,
    onSelectCase: (UnstuckCase) -> Unit,
    onOpenRights: () -> Unit,
    onLoadDemo: () -> Unit
) {
    val totalPenaltyDue = cases.sumOf { it.evaluation?.penaltyAmount ?: 0.0 }
    val totalDisputedAmount = cases.sumOf { it.transaction.amount }
    val overdueCases = cases.count { it.evaluation?.status == "OVERDUE" || (it.evaluation?.daysLate ?: 0) > 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UfoBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Unstuck",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = UfoTextPrimary
                    )
                    Text(
                        text = "Statutory TAT & RBI Compensation Assistant",
                        style = MaterialTheme.typography.bodySmall,
                        color = UfoTextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = UfoIndigoSoft,
                    modifier = Modifier.clickable { onOpenRights() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Rights",
                            tint = UfoPrimaryIndigo,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "RBI Circular",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = UfoPrimaryIndigo
                        )
                    }
                }
            }
        }

        // Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(UfoHeroDark)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FAILED UPI CLAIM SUMMARY",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = UfoHeroSubtext,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(UfoPrimaryIndigo.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "₹100 / Day Delay",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = UfoHeroIconTint
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "₹${String.format(Locale.US, "%,.0f", totalPenaltyDue)}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Accrued Statutory Compensation",
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoHeroSubtext
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$overdueCases Overdue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (overdueCases > 0) UfoWarningGold else UfoSafeCheck
                            )
                            Text(
                                text = "Across ${cases.size} dispute(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoHeroSubtext
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Disputed Principal: ₹${String.format(Locale.US, "%,.2f", totalDisputedAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoHeroSubtext,
                            fontFamily = FontFamily.Monospace
                        )

                        TextButton(
                            onClick = onLoadDemo,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Demo Case ↻",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = UfoHeroIconTint
                            )
                        }
                    }
                }
            }
        }

        // Primary Action Button
        item {
            Button(
                onClick = onOpenScanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UfoPrimaryIndigo,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Track Failed Transaction / Breach",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active Claims Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Disputes & Cases",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                Text(
                    text = "${cases.size} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }
        }

        // Case Items
        if (cases.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Empty",
                            tint = UfoSafeCheck,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No pending failed transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap above to ingest a failed UPI debit SMS or screenshot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextSecondary
                        )
                    }
                }
            }
        } else {
            items(cases) { c ->
                val tx = c.transaction
                val eval = c.evaluation
                val isOverdue = (eval?.daysLate ?: 0) > 0

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                        .clickable { onSelectCase(c) }
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isOverdue) UfoDangerBg else UfoIndigoSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = if (isOverdue) UfoDangerText else UfoPrimaryIndigo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "${tx.bank} • ${tx.rail}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = UfoTextPrimary
                                    )
                                    Text(
                                        text = "UTR: ${tx.reference}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UfoTextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${String.format(Locale.US, "%,.2f", tx.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = UfoTextPrimary
                                )
                                Text(
                                    text = if (c.moneyReturned) "Reversed" else "Debited",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (c.moneyReturned) UfoSafeCheck else UfoTextSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isOverdue) UfoDangerBg else UfoSafeBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isOverdue) "${eval?.daysLate}d Overdue" else "Within TAT",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOverdue) UfoDangerText else UfoSafeCheck
                                    )
                                }

                                if (isOverdue && (eval?.penaltyAmount ?: 0.0) > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(UfoWarnBg)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "+₹${String.format(Locale.US, "%.0f", eval?.penaltyAmount)} penalty",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = UfoWarnText
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = UfoPrimaryIndigo
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = UfoPrimaryIndigo,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

