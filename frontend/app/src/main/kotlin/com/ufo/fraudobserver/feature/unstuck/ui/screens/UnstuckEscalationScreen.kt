package com.ufo.fraudobserver.feature.unstuck.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.feature.unstuck.data.model.EscalationStatus
import com.ufo.fraudobserver.feature.unstuck.data.model.LetterResponse
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckCase
import com.ufo.fraudobserver.feature.unstuck.domain.EscalationEngine
import com.ufo.fraudobserver.feature.unstuck.domain.LetterEngine
import com.ufo.fraudobserver.feature.unstuck.ui.components.DisclaimerBannerView
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnstuckEscalationScreen(
    caseData: UnstuckCase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedLevel by remember { mutableStateOf("bank_complaint") }

    val escalationStatus = remember(caseData, selectedLevel) {
        EscalationEngine.determineStatus(
            events = caseData.events,
            caseCreatedAtStr = caseData.createdAt,
            ticketNumber = caseData.ticketNumber
        )
    }

    val letterResponse = remember(caseData, selectedLevel) {
        LetterEngine.generate(caseData, selectedLevel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Legal Escalation & Notices",
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
            // Recommendation banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(UfoHeroDark)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "RECOMMENDED ACTION STEP",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = UfoHeroSubtext,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = escalationStatus.recommendation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 4-STEP ESCALATION LADDER SELECTOR
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
                    text = "Statutory Escalation Ladder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                listOf(
                    Triple("bank_complaint", "Step 1: Bank Branch / Portal Grievance", "Formal ticket logging"),
                    Triple("nodal_officer", "Step 2: Principal Nodal Officer (PNO)", "Escalation past TAT"),
                    Triple("ombudsman", "Step 3: RBI Integrated Ombudsman", "Final statutory relief via CMS")
                ).forEach { (levelKey, title, sub) ->
                    val isSelected = selectedLevel == levelKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) UfoIndigoSoft else UfoBg)
                            .border(
                                1.dp,
                                if (isSelected) UfoPrimaryIndigo else UfoBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedLevel = levelKey }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) UfoPrimaryIndigo else UfoTextPrimary
                            )
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoTextSecondary
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedLevel = levelKey },
                            colors = RadioButtonDefaults.colors(selectedColor = UfoPrimaryIndigo)
                        )
                    }
                }
            }

            // LETTER CONTAINER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Drafted Statutory Notice",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = if (letterResponse.validated) "Strict Figure Cross-Check: PASSED" else "Check figures",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (letterResponse.validated) UfoSafeCheck else UfoDangerText
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Legal Notice", letterResponse.letterText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Notice copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = UfoPrimaryIndigo)
                        }

                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Grievance Notice - Failed Transaction")
                                putExtra(Intent.EXTRA_TEXT, letterResponse.letterText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Notice via"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = UfoPrimaryIndigo)
                        }
                    }
                }

                // Recipient info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(UfoBg)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recipient: ${letterResponse.recipient["bank_name"] ?: "Bank"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    Text(
                        text = letterResponse.recipient["email"] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = UfoPrimaryIndigo
                    )
                }

                // Letter Text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(UfoBg)
                        .border(1.dp, UfoBorder, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = letterResponse.letterText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = UfoTextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }

            DisclaimerBannerView()
        }
    }
}

