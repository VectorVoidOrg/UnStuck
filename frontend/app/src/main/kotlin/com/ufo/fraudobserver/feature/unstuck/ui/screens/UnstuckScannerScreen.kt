package com.ufo.fraudobserver.feature.unstuck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.feature.unstuck.data.model.ExtractResponse
import com.ufo.fraudobserver.feature.unstuck.ui.components.DisclaimerBannerView
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnstuckScannerScreen(
    onBack: () -> Unit,
    onExtract: (String) -> Unit
) {
    var smsText by remember { mutableStateOf("") }
    var isSimulatedUpload by remember { mutableStateOf(false) }

    val sampleSms = "Dear Customer, your A/c XX1234 is debited for Rs 1,500.00 on 2026-10-15 14:32:10 towards UPI/428912345678/SharmaStores. If not done by you, contact HDFC Bank."

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan & Ingest Claim",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UfoTextPrimary)
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
            Column {
                Text(
                    text = "Did your transaction get stuck?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = UfoTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Banks owe ₹100/day for delayed reversals under RBI guidelines. Upload a screenshot or paste your debit SMS to begin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary,
                    lineHeight = 18.sp
                )
            }

            // Upload Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSimulatedUpload) UfoSafeBg else Color.White)
                    .border(
                        1.5.dp,
                        if (isSimulatedUpload) UfoSafeCheck else UfoBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        isSimulatedUpload = !isSimulatedUpload
                        if (isSimulatedUpload && smsText.isBlank()) {
                            smsText = sampleSms
                        }
                    }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isSimulatedUpload) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                    contentDescription = "Upload",
                    tint = if (isSimulatedUpload) UfoSafeCheck else UfoPrimaryIndigo,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isSimulatedUpload) "Screenshot Loaded (UPI Debit Receipt)" else "Tap to Select Payment Screenshot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                Text(
                    text = if (isSimulatedUpload) "OCR parsed UTR & Bank particulars" else "PNG, JPG or PDF receipt accepted",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }

            // SMS Text Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Or Paste Bank Debit SMS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    TextButton(
                        onClick = { smsText = sampleSms },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Paste Sample",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = UfoPrimaryIndigo
                        )
                    }
                }

                OutlinedTextField(
                    value = smsText,
                    onValueChange = { smsText = it },
                    placeholder = {
                        Text(
                            text = "Paste SMS stating debited amount, UTR reference and time...",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Submit Button
            Button(
                onClick = { onExtract(smsText.ifBlank { sampleSms }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UfoPrimaryIndigo,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Parse & Compute Compensation →",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            DisclaimerBannerView()
        }
    }
}

