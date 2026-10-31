package com.ufo.fraudobserver.feature.unstuck.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.feature.unstuck.ui.components.DisclaimerBannerView
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnstuckRightsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "RBI Consumer Rights",
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
                    text = "RBI Harmonisation TAT Matrix",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = UfoTextPrimary
                )
                Text(
                    text = "Circular DPSS.CO.PD No.629/02.01.014/2019-20",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }

            val rules = listOf(
                Triple("UPI (P2P / P2M)", "T + 1 Calendar Day", "₹100/day beyond T+1"),
                Triple("IMPS Transfers", "T + 1 Calendar Day", "₹100/day beyond T+1"),
                Triple("ATM Cash Not Dispensed", "T + 5 Calendar Days", "₹100/day beyond T+5"),
                Triple("Card (PoS / E-Commerce)", "T + 5 Calendar Days", "₹100/day beyond T+5"),
                Triple("NEFT Interbank", "T + 1 Calendar Day", "₹100/day delay penalty")
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rules.forEach { (rail, tat, comp) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(UfoBg)
                            .border(1.dp, UfoBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = rail,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = UfoTextPrimary
                            )
                            Text(
                                text = "Turnaround: $tat",
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(UfoIndigoSoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = comp,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = UfoPrimaryIndigo
                            )
                        }
                    }
                }
            }

            // Key Highlights
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
                    text = "Key Regulatory Protections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                Text(
                    text = "• Proactive Compensation: Banks are legally required to credit ₹100/day delay compensation automatically without requiring a separate customer claim.\n• Calendar Days: UPI turnaround calculation uses calendar days (not working days).\n• Integrated Ombudsman: If your bank fails to redress your claim within 30 days, you can lodge a complaint on the RBI CMS portal (cms.rbi.org.in).",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary,
                    lineHeight = 20.sp
                )
            }

            // External Link
            Button(
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cms.rbi.org.in"))
                    context.startActivity(browserIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UfoHeroDark,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Visit RBI CMS Ombudsman Portal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            DisclaimerBannerView()
        }
    }
}

