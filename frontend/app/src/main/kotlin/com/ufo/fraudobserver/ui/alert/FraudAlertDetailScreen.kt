package com.ufo.fraudobserver.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FraudAlertDetailScreen(
    vpa: String,
    payeeName: String?,
    amount: Double,
    riskScore: Int,
    triggerReason: String,
    onNavigateBack: () -> Unit,
    onWhitelistReceiver: (String) -> Unit,
    onReportScammer: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fraud Review & Defense", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonDark)
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Warning Icon Badge
            Surface(
                color = CrimsonCard,
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .size(80.dp)
                    .border(2.dp, AlertRed, RoundedCornerShape(50.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = "Threat Blocked",
                        tint = AlertRed,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PAYMENT INTERCEPTED",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = AlertRed
            )

            Text(
                text = "UFO successfully pulled you out of the transaction flow before PIN submission.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Transaction Detail Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow(label = "Target VPA", value = vpa, highlight = true)
                    if (!payeeName.isNullOrBlank()) {
                        DetailRow(label = "Payee Name", value = payeeName)
                    }
                    if (amount > 0) {
                        DetailRow(label = "Amount", value = "₹$amount")
                    }
                    DetailRow(label = "Calculated Risk Score", value = "$riskScore%", riskColor = AlertRed)
                    DetailRow(label = "Interception Reason", value = triggerReason)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Educational Protection Guidance
            Card(
                colors = CardDefaults.cardColors(containerColor = CrimsonCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AlertRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚠️ Why was this blocked?",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Scammers often use typosquatting VPAs (e.g. irctcc, bescom-refund) to impersonate trusted brands.\n" +
                                "• Scammers frequently maintain active voice calls to psychologically pressure you.\n" +
                                "• Remember: You NEVER need to enter a UPI PIN to receive money.",
                        color = Color(0xFFFFCCCC),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("✅ Keep Me Safe (Return to UFO)", fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { onReportScammer(vpa) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Report, contentDescription = null, tint = AlertRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report Scammer to Central Database", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = { onWhitelistReceiver(vpa) }
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("I know this person (Add to Trusted Whitelist)", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    riskColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = riskColor ?: if (highlight) Color.White else TextPrimary,
            fontSize = 13.sp
        )
    }
}

