package com.ufo.fraudobserver.feature.unstuck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckCase
import com.ufo.fraudobserver.feature.unstuck.ui.components.DisclaimerBannerView
import com.ufo.fraudobserver.feature.unstuck.ui.components.PaymentClockView
import com.ufo.fraudobserver.feature.unstuck.ui.components.PenaltyCard
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnstuckDetailScreen(
    caseData: UnstuckCase,
    onBack: () -> Unit,
    onOpenEscalation: (UnstuckCase) -> Unit,
    onOpenRights: () -> Unit,
    onUpdateTicket: (String, String) -> Unit,
    onMarkReversed: (String) -> Unit,
    onAddNote: (String, String) -> Unit
) {
    var ticketInput by remember(caseData) { mutableStateOf(caseData.ticketNumber ?: "") }
    var noteInput by remember { mutableStateOf("") }

    val tx = caseData.transaction
    val evaluation = caseData.evaluation
    val deadlineStr = evaluation?.deadlineDate ?: "2026-10-16 14:32:10"
    val daysLate = evaluation?.daysLate ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Case #${caseData.id.take(10).uppercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = "${tx.bank} • UTR: ${tx.reference}",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UfoTextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenRights) {
                        Text(
                            text = "RBI Rights",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = UfoPrimaryIndigo
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UfoBg)
            )
        },
        containerColor = UfoBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PAYMENT CLOCK RING CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                PaymentClockView(
                    transactionDateStr = tx.date,
                    deadlineDateStr = deadlineStr,
                    moneyReturned = caseData.moneyReturned,
                    reversalDateStr = caseData.reversalDate
                )
            }

            // PENALTY CALCULATOR CARD
            PenaltyCard(
                daysLate = daysLate,
                principalAmount = tx.amount,
                moneyReturned = caseData.moneyReturned
            )

            // CTA TO OPEN ESCALATION LADDER & NOTICE
            Button(
                onClick = { onOpenEscalation(caseData) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UfoHeroDark,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Notice",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Draft Legal Notice & Escalation Ladder →",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // BANK RESPONSE CARD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bank Communication & Grievance Ticket",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                // Ticket input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ticketInput,
                        onValueChange = { ticketInput = it },
                        placeholder = {
                            Text(
                                text = "Enter Bank Ticket No.",
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoTextMuted
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = { onUpdateTicket(caseData.id, ticketInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Mark Reversed toggle button
                if (!caseData.moneyReturned) {
                    OutlinedButton(
                        onClick = { onMarkReversed(caseData.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Money Just Returned to Account?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = UfoSafeCheck,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(UfoSafeBg)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Funds successfully reversed to account",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UfoSafeCheck
                        )
                    }
                }

                // Log note
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = {
                        Text(
                            text = "Log call note / branch reply...",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextMuted
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = {
                        if (noteInput.isNotBlank()) {
                            onAddNote(caseData.id, noteInput)
                            noteInput = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UfoIndigoSoft,
                        contentColor = UfoPrimaryIndigo
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Add Timeline Entry",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // JOURNEY TIMELINE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Dispute Journey Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                caseData.events.forEach { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(UfoBg)
                            .border(1.dp, UfoBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ev.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = UfoTextPrimary
                            )
                            Text(
                                text = ev.timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoTextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(UfoIndigoSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ev.type,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = UfoPrimaryIndigo,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            DisclaimerBannerView()
        }
    }
}

