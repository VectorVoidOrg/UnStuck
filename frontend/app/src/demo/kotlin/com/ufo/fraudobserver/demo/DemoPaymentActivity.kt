package com.ufo.fraudobserver.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.ui.theme.*

class DemoPaymentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UfoFraudObserverTheme {
                DemoPaymentScreen(onClose = { finish() })
            }
        }
    }
}

private data class ScenarioPreset(
    val title: String,
    val payeeName: String,
    val vpa: String,
    val amount: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoPaymentScreen(onClose: () -> Unit) {
    val presets = listOf(
        ScenarioPreset("Safe Merchant", "Swiggy UPI", "swiggy@icici", "₹ 250"),
        ScenarioPreset("Suspicious Lucky Draw", "Prize Claim Dept", "lucky.draw99@ybl", "₹ 3,500"),
        ScenarioPreset("Digital Arrest Scam", "CBI Cyber Cell", "cbi.digitalarrest@ybl", "₹ 50,000")
    )

    var selectedIndex by remember { mutableIntStateOf(2) }
    val current = presets[selectedIndex]
    var showSuccessDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF1E1F24),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Google Pay (Simulated)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Simulated UPI payment gateway", fontSize = 11.sp, color = Color.LightGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1F24))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Preset Scenario Selector Chips
                Text(
                    text = "Select Simulation Preset:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEachIndexed { idx, preset ->
                        val isSel = idx == selectedIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) UfoPrimaryIndigo else Color(0xFF2C2D35))
                                .clickable { selectedIndex = idx }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.title,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else Color.LightGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Payee Avatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF383A45)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = current.payeeName.take(1).uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Payee Name & VPA (Parsed by UfoAccessibilityService)
                Text(
                    text = "Paying ${current.payeeName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = current.vpa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B3C0)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Large Amount Display
                Text(
                    text = current.amount,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "Banking Name: ${current.payeeName}",
                    fontSize = 12.sp,
                    color = Color(0xFF8E92A4)
                )

                // Info banner explaining the accessibility hook
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF282A36))
                        .border(1.dp, Color(0xFF44475A), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB86C), modifier = Modifier.size(18.dp))
                        Text(
                            text = "UFO Background Guard observes this simulated screen, intercepts receiver VPA & amount, and pops a heads-up notification in real-time.",
                            fontSize = 11.5.sp,
                            color = Color(0xFFF8F8F2),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Pay Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showSuccessDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Pay ${current.amount}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Simulated PIN Prompt", color = Color.White) },
            text = {
                Text(
                    "In a live scam scenario, UFO intercepts before this step so you never enter your UPI PIN.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSuccessDialog = false
                    onClose()
                }) {
                    Text("Done")
                }
            },
            containerColor = Color(0xFF2C2D35)
        )
    }
}

