package com.ufo.fraudobserver.demo

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.data.local.SecureTokenStore
import com.ufo.fraudobserver.data.remote.DemoLoginRequest
import com.ufo.fraudobserver.data.remote.DemoSetUsageRequest
import com.ufo.fraudobserver.data.remote.UfoApiService
import com.ufo.fraudobserver.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RealDemoProvider(private val appContext: Context) : DemoProvider {

    override val isDemoAvailable: Boolean = true
    override val demoAccessCode: String = "HACKATHON2026"

    private val apiService = UfoApiService.create(appContext)
    private val tokenStore = SecureTokenStore.getInstance(appContext)

    @Composable
    override fun DemoAccountSection(
        onDemoLoginSuccess: (String) -> Unit
    ) {
        val context = LocalContext.current
        var accessCode by remember { mutableStateOf(demoAccessCode) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(UfoIndigoSoft.copy(alpha = 0.5f))
                .border(1.dp, UfoPrimaryIndigo.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚡ Jury Demo Fast-Track",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = UfoPrimaryIndigo
            )

            Text(
                text = "Instant evaluation access without real Google OAuth credentials.",
                style = MaterialTheme.typography.bodySmall,
                color = UfoTextSecondary
            )

            OutlinedTextField(
                value = accessCode,
                onValueChange = { accessCode = it },
                label = { Text("Demo Access Code", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UfoPrimaryIndigo,
                    unfocusedBorderColor = UfoBorder,
                    focusedLabelColor = UfoPrimaryIndigo
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = UfoDangerText,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val resp = apiService.demoLogin(
                                DemoLoginRequest(accessCode = accessCode.trim())
                            )
                            val body = resp.body()
                            if (resp.isSuccessful && body != null) {
                                tokenStore.saveTokens(body.accessToken, body.refreshToken)
                                onDemoLoginSuccess(body.accessToken)
                                Toast.makeText(context, "Logged in as Jury Demo User", Toast.LENGTH_SHORT).show()
                            } else {
                                errorMessage = "Invalid code or demo login failed (${resp.code()})"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Gateway unreachable: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && accessCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Continue as Jury Demo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DemoControlsSheet(
        isOpen: Boolean,
        onDismiss: () -> Unit,
        onResetSuccess: () -> Unit,
        onUsageUpdated: () -> Unit
    ) {
        if (!isOpen) return

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var statusMsg by remember { mutableStateOf<String?>(null) }
        var isBusy by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = UfoSurfaceDark,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "⚙️ Jury Demo Control Panel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                Text(
                    text = "Adjust state, trigger edge cases, test paywalls, and simulate UPI screens directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )

                if (statusMsg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(UfoIndigoSoft)
                            .padding(10.dp)
                    ) {
                        Text(text = statusMsg ?: "", color = UfoPrimaryIndigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                // Section 1: Quota Jumps
                Text(
                    text = "1. Quota Edge-Case Jump",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = UfoTextPrimary
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try {
                                    val r = apiService.demoSetUsage(DemoSetUsageRequest(preventionUsed = 99))
                                    if (r.isSuccessful) {
                                        statusMsg = "Prevention usage jumped to 99/100 (1 check left)"
                                        onUsageUpdated()
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Jump 99/100 Checks", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try {
                                    val r = apiService.demoSetUsage(DemoSetUsageRequest(unstuckUsed = 4))
                                    if (r.isSuccessful) {
                                        statusMsg = "Unstuck usage jumped to 4/5 (1 case left)"
                                        onUsageUpdated()
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Jump 4/5 Cases", fontSize = 11.sp, maxLines = 1)
                    }
                }

                HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                // Section 2: Subscriptions
                Text(
                    text = "2. Subscription Simulation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = UfoTextPrimary
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try {
                                    val r = apiService.demoSimulatePurchase()
                                    if (r.isSuccessful) {
                                        statusMsg = "Subscribed to Pro Plan (Unlimited Quota)"
                                        onUsageUpdated()
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = UfoSafeCheck),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simulate Pro Sub", fontSize = 11.sp, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try {
                                    val r = apiService.demoSimulateCancel()
                                    if (r.isSuccessful) {
                                        statusMsg = "Subscription cancelled -> reverted to Free tier"
                                        onUsageUpdated()
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel Sub", fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                // Section 3: UPI Payment Screen Simulator
                Text(
                    text = "3. Live UPI Screen Simulation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = UfoTextPrimary
                )

                Button(
                    onClick = {
                        openDemoPaymentActivity(context)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Launch Simulated Payment App Screen 🚀", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                // Section 4: Reset Demo Account
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            try {
                                val r = apiService.demoReset()
                                if (r.isSuccessful) {
                                    statusMsg = "Account reset to 0 used, free tier"
                                    onResetSuccess()
                                }
                            } catch (e: Exception) {
                                statusMsg = "Reset failed: ${e.message}"
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UfoDangerText)
                ) {
                    Text("Reset Account Usage to Fresh Demo")
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    override fun openDemoPaymentActivity(context: Context) {
        val intent = Intent(context, DemoPaymentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

