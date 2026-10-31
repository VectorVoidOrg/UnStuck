package com.ufo.fraudobserver.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.data.repository.FraudRepository
import com.ufo.fraudobserver.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityReportScreen(
    prefilledVpa: String = "",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { FraudRepository(context) }
    val scope = rememberCoroutineScope()

    var vpa by remember { mutableStateOf(prefilledVpa) }
    var selectedCategory by remember { mutableStateOf("BRAND_IMPERSONATION") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "BRAND_IMPERSONATION" to "Brand Impersonation / Fake Support",
        "COLLECT_TRAP" to "Collect-to-Receive Trap",
        "LOTTERY_PRIZE" to "Lottery / Prize Scam",
        "VISHING_CALL" to "Voice Call Coaching Scam",
        "FAKE_REFUND" to "Fake Refund / Cashback"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Malicious UPI ID", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Community Threat Submission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Submitting immediately flips bits in your local Bloom filter and syncs to the central fraud registry to protect all UFO users.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = vpa,
                onValueChange = { vpa = it },
                label = { Text("Fraudulent VPA (e.g. scammer@ybl)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlertRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Select Scam Category", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            categories.forEach { (key, label) ->
                val isSelected = selectedCategory == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(if (isSelected) SurfaceElevated else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = key }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedCategory = key },
                        colors = RadioButtonDefaults.colors(selectedColor = AlertRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = if (isSelected) Color.White else TextSecondary, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Incident Details (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlertRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (vpa.isNotBlank()) {
                        isSubmitting = true
                        scope.launch {
                            val res = repository.submitScamReport(
                                vpa = vpa.trim(),
                                category = selectedCategory,
                                notes = notes.ifBlank { null },
                                sourceApp = context.packageName
                            )
                            isSubmitting = false
                            submitResult = res.getOrDefault("Report recorded!")
                        }
                    }
                },
                enabled = vpa.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSubmitting) "Submitting to Registry..." else "Submit to Global Blacklist",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            submitResult?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CrimsonCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SafeGreen, RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("✅ Report Processed", fontWeight = FontWeight.Bold, color = SafeGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(msg, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

