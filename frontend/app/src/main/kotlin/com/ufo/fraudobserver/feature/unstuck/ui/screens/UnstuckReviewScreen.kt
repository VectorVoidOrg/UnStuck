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
import com.ufo.fraudobserver.feature.unstuck.data.model.CreateCaseRequest
import com.ufo.fraudobserver.feature.unstuck.data.model.ExtractResponse
import com.ufo.fraudobserver.feature.unstuck.data.model.Rule
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckTransaction
import com.ufo.fraudobserver.feature.unstuck.ui.components.DisclaimerBannerView
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnstuckReviewScreen(
    extractResponse: ExtractResponse,
    rules: List<Rule>,
    onBack: () -> Unit,
    onSubmitCase: (CreateCaseRequest) -> Unit
) {
    var selectedRuleId by remember { mutableStateOf("upi_p2p_failed") }
    var customerRole by remember { mutableStateOf("Remitter") }
    var moneyReturned by remember { mutableStateOf(false) }

    val tx = extractResponse.transaction
    var amountStr by remember(extractResponse) { mutableStateOf(tx.amount.toString()) }
    var referenceStr by remember(extractResponse) { mutableStateOf(tx.reference) }
    var bankStr by remember(extractResponse) { mutableStateOf(tx.bank) }
    var dateStr by remember(extractResponse) { mutableStateOf(tx.date) }
    var customerNameStr by remember(extractResponse) { mutableStateOf(tx.customerName ?: "Rohit Sharma") }

    val selectedRule = rules.find { it.id == selectedRuleId } ?: rules.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Confirm Claim Details",
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
            // Confidence badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(UfoSafeBg)
                    .border(1.dp, UfoSafeCheck.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Confidence",
                    tint = UfoSafeCheck,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${(extractResponse.confidence * 100).toInt()}% Extraction Confidence • Cross-Check Matched",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UfoSafeCheck
                )
            }

            // Editable Particulars
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
                    text = "Disputed Transaction Particulars",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Debited Amount (₹)", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = referenceStr,
                    onValueChange = { referenceStr = it },
                    label = { Text("UPI UTR / Bank Reference", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = bankStr,
                    onValueChange = { bankStr = it },
                    label = { Text("Remitter / Debited Bank", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Transaction Date & Time (YYYY-MM-DD HH:MM:SS)", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = customerNameStr,
                    onValueChange = { customerNameStr = it },
                    label = { Text("Account Holder Name", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // RBI Rule Selection
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
                    text = "RBI Harmonisation Rule Classification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                listOf(
                    "upi_p2p_failed" to "UPI (P2P Transfer) • T+1 Calendar Day",
                    "upi_p2m_failed" to "UPI (P2M Merchant) • T+1 Calendar Day",
                    "imps_failed" to "IMPS Transfer • T+1 Calendar Day",
                    "atm_cash_not_dispensed" to "ATM Cash Not Dispensed • T+5 Days"
                ).forEach { (id, label) ->
                    val isSelected = selectedRuleId == id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) UfoIndigoSoft else UfoBg)
                            .border(
                                1.dp,
                                if (isSelected) UfoPrimaryIndigo else UfoBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedRuleId = id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) UfoPrimaryIndigo else UfoTextPrimary
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedRuleId = id },
                            colors = RadioButtonDefaults.colors(selectedColor = UfoPrimaryIndigo)
                        )
                    }
                }
            }

            // Customer Role
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
                    text = "Your Role in Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Remitter (Sender)", "Beneficiary (Receiver)").forEach { role ->
                        val isSelected = (customerRole == "Remitter" && role.startsWith("Remitter")) ||
                                (customerRole == "Beneficiary" && role.startsWith("Beneficiary"))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) UfoHeroDark else UfoBg)
                                .border(
                                    1.dp,
                                    if (isSelected) UfoHeroDark else UfoBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    customerRole = if (role.startsWith("Remitter")) "Remitter" else "Beneficiary"
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = role,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else UfoTextPrimary
                            )
                        }
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    val finalTx = tx.copy(
                        amount = amountStr.toDoubleOrNull() ?: tx.amount,
                        reference = referenceStr,
                        bank = bankStr,
                        date = dateStr,
                        customerName = customerNameStr
                    )
                    val req = CreateCaseRequest(
                        transaction = finalTx,
                        ruleId = selectedRuleId,
                        customerRole = customerRole,
                        moneyReturned = moneyReturned
                    )
                    onSubmitCase(req)
                },
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
                    text = "Open Statutory Claim & Start Clock →",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            DisclaimerBannerView()
        }
    }
}

