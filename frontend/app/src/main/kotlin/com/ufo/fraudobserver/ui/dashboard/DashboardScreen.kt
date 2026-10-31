package com.ufo.fraudobserver.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ufo.fraudobserver.data.local.FlaggedTransactionEntity
import com.ufo.fraudobserver.engine.RiskStatus
import com.ufo.fraudobserver.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToReport: () -> Unit,
    onNavigateToWhitelist: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val flaggedList by viewModel.flaggedTransactions.collectAsState()
    val threatCount by viewModel.totalThreatsCount.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    var testVpa by remember { mutableStateOf("pmcares.refund@okaxis") }
    var testAmount by remember { mutableStateOf("4500") }
    var testIsCollect by remember { mutableStateOf(false) }
    var testActiveCall by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛸 UFO", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fraud Observer", color = TextSecondary, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    IconButton(onClick = onNavigateToWhitelist) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Whitelist", tint = SafeGreen)
                    }
                    IconButton(onClick = onNavigateToReport) {
                        Icon(Icons.Default.ReportProblem, contentDescription = "Report Scam", tint = AlertRed)
                    }
                    IconButton(onClick = { viewModel.syncDeltaNow() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", tint = AccentBlue)
                    }
                }
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Protection Status Card
                val allActive = uiState.isAccessibilityActive && uiState.isNotificationListenerActive
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (allActive) SurfaceDark else CrimsonCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (allActive) SafeGreen.copy(alpha = 0.5f) else AlertRed,
                            RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (allActive) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (allActive) SafeGreen else AlertRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (allActive) "PROTECTION ACTIVE" else "DEFENSE DEGRADED",
                                fontWeight = FontWeight.Bold,
                                color = if (allActive) SafeGreen else AlertRed,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (allActive)
                                    "Zero-latency screening enabled before PIN entry"
                                else
                                    "Critical listeners inactive. Tap to configure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (!allActive) {
                            TextButton(onClick = onNavigateToOnboarding) {
                                Text("FIX", color = AlertRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Metric Counters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Threats Blocked",
                        value = threatCount.toString(),
                        accentColor = AlertRed
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Bloom Capacity",
                        value = "${uiState.bloomFilterEntries}",
                        subtitle = "100k Local DB",
                        accentColor = AccentBlue
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Eval Latency",
                        value = "< 50ms",
                        subtitle = "Zero UI Lock",
                        accentColor = SafeGreen
                    )
                }
            }

            // Interactive Simulator Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚡ Real-Time Engine Simulator",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick presets
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PresetChip("pmcares.refund@okaxis") { testVpa = it }
                            PresetChip("irctcc.ticket@ybl") { testVpa = it }
                            PresetChip("friend@okaxis") { testVpa = it }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = testVpa,
                            onValueChange = { testVpa = it },
                            label = { Text("Receiver VPA (UPI ID)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = testAmount,
                            onValueChange = { testAmount = it },
                            label = { Text("Amount (₹)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = testIsCollect,
                                onCheckedChange = { testIsCollect = it },
                                colors = CheckboxDefaults.colors(checkedColor = AlertRed)
                            )
                            Text("Collect Request Trap", color = TextPrimary, fontSize = 13.sp)

                            Spacer(modifier = Modifier.width(12.dp))

                            Checkbox(
                                checked = testActiveCall,
                                onCheckedChange = { testActiveCall = it },
                                colors = CheckboxDefaults.colors(checkedColor = AlertRed)
                            )
                            Text("Active Call (Vishing)", color = TextPrimary, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.simulateRiskCheck(
                                    vpa = testVpa,
                                    amount = testAmount.toDoubleOrNull(),
                                    isCollectRequest = testIsCollect,
                                    simulateActiveCall = testActiveCall
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🚨 Test Threat Interception & Heads-Up Alert", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Simulation output
                        uiState.simulationResult?.let { res ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (res.status == RiskStatus.SAFE) Color(0xFF0F2B14) else CrimsonCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "STATUS: ${res.status.name} (Risk: ${res.riskScore}%) • Latency: ${res.latencyMs}ms",
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.status == RiskStatus.SAFE) SafeGreen else AlertRed,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    res.triggers.forEach { t ->
                                        Text("• $t", color = Color(0xFFFFD1D1), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interception Threat Log Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Interceptions (${flaggedList.size})",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    if (flaggedList.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (flaggedList.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No threats intercepted yet. System is actively observing.", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(flaggedList, key = { it.id }) { item ->
                    ThreatItemCard(item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ThreatItemCard(item: FlaggedTransactionEntity) {
    val dateStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm:ss • dd MMM", Locale.getDefault()).format(Date(item.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CrimsonCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AlertRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.vpa,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "${item.riskScore}% RISK",
                    fontWeight = FontWeight.Bold,
                    color = AlertRed,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.triggerReason,
                color = Color(0xFFFFCCCC),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "App: ${item.sourceApp?.substringAfterLast(".") ?: "Unknown"}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = dateStr,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PresetChip(text: String, onSelect: (String) -> Unit) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable { onSelect(text) }
    ) {
        Text(
            text = text.substringBefore("@"),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

