package com.ufo.fraudobserver.ui.dashboard

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ufo.fraudobserver.feature.unstuck.ui.UnstuckRoute
import com.ufo.fraudobserver.ui.account.AccountScreen
import com.ufo.fraudobserver.ui.components.UfoIcons
import com.ufo.fraudobserver.ui.onboarding.PermissionHelper
import com.ufo.fraudobserver.ui.theme.*

enum class UfoScreen {
    PREVENTION, UNSTUCK, ACCOUNT
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToOnboarding: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isSignedIn by viewModel.isSignedIn.collectAsState()

    var currentScreen by remember { mutableStateOf(UfoScreen.PREVENTION) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
        viewModel.refreshProfile()
    }

    Scaffold(
        containerColor = UfoBg,
        bottomBar = {
            UfoBottomNav(
                currentScreen = currentScreen,
                onSelect = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    UfoScreen.PREVENTION -> PreventionScreenContent(
                        uiState = uiState,
                        onToggleGuard = { viewModel.setBackgroundGuardEnabled(!uiState.isBackgroundGuardEnabled) },
                        onSimulateCheck = { vpa, amount, isCollect ->
                            viewModel.simulateRiskCheck(vpa, amount, isCollect, false)
                        },
                        onOpenSettings = onNavigateToOnboarding
                    )
                    UfoScreen.UNSTUCK -> UnstuckRoute(
                        onNavigateBackToHome = { currentScreen = UfoScreen.PREVENTION }
                    )
                    UfoScreen.ACCOUNT -> AccountScreen(
                        profile = userProfile,
                        isSignedIn = isSignedIn,
                        onGoogleSignInClick = { /* Google Sign In via CredentialManager */ },
                        onSignOutClick = { viewModel.signOut() },
                        onDeleteAccountClick = { viewModel.deleteAccount() },
                        onUpgradeClick = { /* Launch Paywall Sheet */ },
                        onDemoLoginSuccess = { token -> viewModel.handleDemoLogin(token, token) },
                        onOpenSettings = onNavigateToOnboarding
                    )
                }
            }
        }
    }
}

@Composable
fun PreventionScreenContent(
    uiState: DashboardUiState,
    onToggleGuard: () -> Unit,
    onSimulateCheck: (String, Double?, Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UfoBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Prevention Guard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = UfoTextPrimary
                )
                Text(
                    text = "AI-Powered Real-Time Payment Observer",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (uiState.isBackgroundGuardEnabled) UfoIndigoSoft else UfoSurfaceDark)
                        .border(1.dp, if (uiState.isBackgroundGuardEnabled) UfoPrimaryIndigo else UfoBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (uiState.isBackgroundGuardEnabled) "● ACTIVE" else "○ PAUSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isBackgroundGuardEnabled) UfoPrimaryIndigo else UfoTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(UfoSurfaceDark)
                        .border(1.dp, UfoBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = UfoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Hero Shield Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = UfoSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(UfoBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isBackgroundGuardEnabled) UfoIndigoSoft else UfoSurfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.isBackgroundGuardEnabled) UfoIcons.ShieldCheck else UfoIcons.Shield,
                                contentDescription = null,
                                tint = if (uiState.isBackgroundGuardEnabled) UfoPrimaryIndigo else UfoTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (uiState.isBackgroundGuardEnabled) "Background Guard On" else "Protection Paused",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = UfoTextPrimary
                            )
                            Text(
                                text = if (uiState.isBackgroundGuardEnabled) "Active background interception" else "Tap toggle to resume defense",
                                style = MaterialTheme.typography.bodySmall,
                                color = UfoTextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isBackgroundGuardEnabled,
                        onCheckedChange = { onToggleGuard() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = UfoPrimaryIndigo,
                            uncheckedThumbColor = UfoTextMuted,
                            uncheckedTrackColor = UfoSurfaceMuted
                        )
                    )
                }

                HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                Text(
                    text = "UFO monitors payment authorization screens in Google Pay, PhonePe, Paytm, BHIM, Cred, and Amazon Pay. Recipient VPAs and amounts are analyzed before you confirm payments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary,
                    lineHeight = 19.sp
                )
            }
        }

        // System Readiness Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = UfoSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(UfoBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "System Readiness",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                // Accessibility Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Screen Observer",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = if (uiState.isAccessibilityActive) "Active & inspecting payment pages" else "Required to detect recipient VPAs",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isAccessibilityActive) UfoSafeCheck else UfoWarnText
                        )
                    }

                    if (!uiState.isAccessibilityActive) {
                        Button(
                            onClick = { PermissionHelper.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Ready ✓", color = UfoSafeCheck, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                // Heads-Up Alert Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Heads-Up Scam Notification",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = if (uiState.hasPostNotifications) "High-priority popups enabled" else "Required for instant Truecaller-style HUD",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.hasPostNotifications) UfoSafeCheck else UfoWarnText
                        )
                    }

                    if (!uiState.hasPostNotifications) {
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Ready ✓", color = UfoSafeCheck, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Live Simulation / Jury Test Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = UfoSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(UfoBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Live Test & Custom Amount Verification",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )

                Text(
                    text = "Enter any custom payment amount and UPI ID to trigger the exact heads-up risk popup:",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )

                var testVpa by remember { mutableStateOf("prize.winner99@ybl") }
                var testAmountStr by remember { mutableStateOf("1500") }

                OutlinedTextField(
                    value = testVpa,
                    onValueChange = { testVpa = it },
                    label = { Text("Receiver VPA (UPI ID)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = testAmountStr,
                    onValueChange = { testAmountStr = it },
                    label = { Text("Payment Amount (₹)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        val parsed = testAmountStr.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 1500.0
                        onSimulateCheck(testVpa.ifBlank { "test.receiver@upi" }, parsed, true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo)
                ) {
                    val previewAmt = testAmountStr.replace("₹", "").replace(",", "").trim()
                    Text("Trigger Popup with ₹" + (if (previewAmt.isNotBlank()) previewAmt else "0"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = UfoBorder.copy(alpha = 0.5f))

                Text(
                    text = "Or fill with quick presets:",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            testVpa = "swiggy@icici"
                            testAmountStr = "250"
                            onSimulateCheck("swiggy@icici", 250.0, false)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UfoSafeCheck)
                    ) {
                        Text("Safe ₹250", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            testVpa = "prize.winner99@ybl"
                            testAmountStr = "5000"
                            onSimulateCheck("prize.winner99@ybl", 5000.0, true)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UfoWarnText)
                    ) {
                        Text("Warn ₹5k", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            testVpa = "cbi.digitalarrest@ybl"
                            testAmountStr = "50000"
                            onSimulateCheck("cbi.digitalarrest@ybl", 50000.0, true)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UfoDangerText)
                    ) {
                        Text("Scam ₹50k", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

                UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

@Composable
fun UfoBottomNav(
    currentScreen: UfoScreen,
    onSelect: (UfoScreen) -> Unit
) {
    val items = listOf(
        Triple(UfoScreen.PREVENTION, "UFO", UfoIcons.Shield),
        Triple(UfoScreen.UNSTUCK, "Unstuck", UfoIcons.Orbit),
        Triple(UfoScreen.ACCOUNT, "Account", UfoIcons.Users)
    )

    Surface(
        color = Color.White.copy(alpha = 0.98f),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Column {
            HorizontalDivider(color = UfoBorder, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (screen, label, iconVec) ->
                    val isActive = currentScreen == screen
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(screen) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = if (isActive) {
                                Modifier
                                    .clip(CircleShape)
                                    .background(UfoIndigoSoft)
                                    .padding(horizontal = 20.dp, vertical = 7.dp)
                            } else {
                                Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = label,
                                tint = if (isActive) UfoPrimaryIndigo else UfoTextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) UfoPrimaryIndigo else UfoTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UfoDisclosure(bloomEntries: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Protected by UFO Bloom Filter ($bloomEntries blacklisted signatures active)",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

