package com.ufo.fraudobserver.ui.dashboard

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ufo.fraudobserver.data.local.FlaggedTransactionEntity
import com.ufo.fraudobserver.data.local.WhitelistedVpaEntity
import com.ufo.fraudobserver.engine.RiskEvaluationResult
import com.ufo.fraudobserver.engine.RiskStatus
import com.ufo.fraudobserver.ui.components.UfoIcons
import com.ufo.fraudobserver.ui.onboarding.PermissionHelper
import com.ufo.fraudobserver.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class UfoScreen {
    HOME, ACTIVITY, TRUSTED, SETTINGS, INPUT, REVIEW, RESULT, WARNING
}

enum class ActivityFilter(val label: String) {
    ALL("All"), FLAGGED("Flagged"), REVIEWED("Reviewed")
}

enum class PillTone {
    SAFE, WARN, DANGER
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToReport: () -> Unit,
    onNavigateToWhitelist: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activityList by viewModel.flaggedTransactions.collectAsState()
    val trustedList by viewModel.whitelistedVpas.collectAsState()

    var currentScreen by remember { mutableStateOf(UfoScreen.HOME) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    val showBottomNav = currentScreen in listOf(
        UfoScreen.HOME,
        UfoScreen.ACTIVITY,
        UfoScreen.TRUSTED,
        UfoScreen.SETTINGS
    )

    Scaffold(
        containerColor = UfoBg,
        bottomBar = {
            if (showBottomNav) {
                UfoBottomNav(
                    currentScreen = currentScreen,
                    onSelect = { currentScreen = it }
                )
            }
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
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                label = "UfoScreenTransition"
            ) { screen ->
                when (screen) {
                    UfoScreen.HOME -> HomeScreenContent(
                        uiState = uiState,
                        activityList = activityList,
                        onGo = { currentScreen = it },
                        onToggleBackgroundGuard = viewModel::setBackgroundGuardEnabled,
                        onTriggerPopupPreview = viewModel::triggerTruecallerPopupPreview,
                        onSelectActivity = { item ->
                            viewModel.selectActivityItem(item)
                            currentScreen = if (item.riskScore >= 60) UfoScreen.WARNING else UfoScreen.RESULT
                        },
                        onClearAll = viewModel::clearHistory,
                        onReportScam = onNavigateToReport
                    )

                    UfoScreen.INPUT -> InputScreenContent(
                        uiState = uiState,
                        onUpdateText = viewModel::updateRequestText,
                        onUpdateAmount = viewModel::updateAmountInput,
                        onAttach = viewModel::setAttachment,
                        onApplyPreset = viewModel::applyQuickPreset,
                        onBack = { currentScreen = UfoScreen.HOME },
                        onNext = { currentScreen = UfoScreen.REVIEW }
                    )

                    UfoScreen.REVIEW -> ReviewScreenContent(
                        uiState = uiState,
                        onUpdateReceiver = viewModel::updateReceiverName,
                        onUpdateVpa = viewModel::updateVpa,
                        onUpdateAmount = viewModel::updateAmountInput,
                        onUpdateCollect = viewModel::updateCollectRequest,
                        onBack = { currentScreen = UfoScreen.INPUT },
                        onConfirmCheck = {
                            viewModel.evaluateCurrentRequest { result ->
                                currentScreen = if (result.status == RiskStatus.FRAUD || result.status == RiskStatus.WARNING) {
                                    UfoScreen.WARNING
                                } else {
                                    UfoScreen.RESULT
                                }
                            }
                        }
                    )

                    UfoScreen.WARNING -> WarningScreenContent(
                        uiState = uiState,
                        onBack = { currentScreen = UfoScreen.HOME },
                        onAbort = { currentScreen = UfoScreen.HOME },
                        onDismissAlert = {
                            viewModel.dismissWarningForCurrentVpa()
                        },
                        onViewResult = { currentScreen = UfoScreen.RESULT },
                        onReportScammer = onNavigateToReport
                    )

                    UfoScreen.RESULT -> ResultScreenContent(
                        uiState = uiState,
                        onBack = { currentScreen = UfoScreen.HOME },
                        onDone = { currentScreen = UfoScreen.HOME },
                        onAddTrusted = { vpa ->
                            viewModel.addTrustedVpa(vpa)
                            currentScreen = UfoScreen.TRUSTED
                        }
                    )

                    UfoScreen.ACTIVITY -> ActivityScreenContent(
                        activityList = activityList,
                        onBack = { currentScreen = UfoScreen.HOME },
                        onSelectActivity = { item ->
                            viewModel.selectActivityItem(item)
                            currentScreen = if (item.riskScore >= 60) UfoScreen.WARNING else UfoScreen.RESULT
                        },
                        onDeleteActivityItem = viewModel::deleteActivityItem,
                        onClearAll = viewModel::clearHistory
                    )

                    UfoScreen.TRUSTED -> TrustedScreenContent(
                        trustedList = trustedList,
                        onAddTrusted = viewModel::addTrustedVpa,
                        onRemoveTrusted = viewModel::removeTrustedVpa,
                        onGoHome = { currentScreen = UfoScreen.HOME }
                    )

                    UfoScreen.SETTINGS -> SettingsScreenContent(
                        uiState = uiState,
                        onToggleBackgroundGuard = viewModel::setBackgroundGuardEnabled,
                        onRefreshPermissions = viewModel::refreshStatus,
                        onPreviewWarning = viewModel::triggerTruecallerPopupPreview,
                        onOpenFullOnboarding = onNavigateToOnboarding,
                        onSyncBlacklist = viewModel::syncDeltaNow
                    )
                }
            }

            // Truecaller-Sized Floating Notification Popup Banner (appears when UPI screen evaluated or previewed)
            val popup = uiState.activePopupBanner
            if (popup != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    UfoTruecallerPopupBanner(
                        result = popup,
                        onDismiss = viewModel::dismissPopupBanner,
                        onInspect = {
                            viewModel.dismissPopupBanner()
                            currentScreen = if (popup.riskScore >= 60) UfoScreen.WARNING else UfoScreen.RESULT
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UfoTruecallerPopupBanner(
    result: RiskEvaluationResult,
    onDismiss: () -> Unit,
    onInspect: () -> Unit
) {
    val isHighRisk = result.riskScore >= 60
    val isWarning = result.riskScore in 30..59
    val badgeBg = when {
        isHighRisk -> Color(0xFFDC2626)
        isWarning -> Color(0xFFD97706)
        else -> Color(0xFF059669)
    }
    val badgeLabel = when {
        isHighRisk -> "HIGH RISK · ${result.riskScore}/100"
        isWarning -> "CAUTION · ${result.riskScore}/100"
        else -> "SAFE · ${result.riskScore}/100"
    }
    val borderAccent = when {
        isHighRisk -> Color(0xFFEF4444)
        isWarning -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }
    val displayPayee = result.payeeName?.takeIf { it.isNotBlank() } ?: result.vpa.substringBefore("@")
    val formattedAmount = result.amount?.takeIf { it > 0 }?.let { formatInr(it) } ?: "UPI Check"
    val sourceAppLabel = (result.sourceApp ?: "UPI App").substringAfterLast(".").uppercase(Locale.getDefault())
    val engineTag = if (result.backendVerified) "BACKEND SCORE" else "LIVE SCORE"
    val primaryReason = result.triggers.firstOrNull() ?: "Evaluated by UFO Guard"

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderAccent.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Micro-Bar (Truecaller message header style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(UfoPrimaryIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = UfoIcons.Orbit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = "UFO UPI GUARD • $sourceAppLabel • $engineTag",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Middle Compact Row: Score Badge + Receiver/VPA/Reason + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg.copy(alpha = 0.2f))
                        .border(1.dp, badgeBg.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${result.riskScore}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "/100",
                            color = Color(0xFFCBD5E1),
                            fontSize = 7.5.sp,
                            lineHeight = 8.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    Text(
                        text = "$displayPayee (${result.vpa})",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = primaryReason,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formattedAmount,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Compact Truecaller Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isHighRisk) Color(0xFFDC2626) else UfoPrimaryIndigo)
                        .clickable { onInspect() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isHighRisk) "Abort & Inspect" else "Full Report",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun UfoTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null
) {
    Surface(
        color = UfoBg.copy(alpha = 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = UfoIcons.Back,
                        contentDescription = "Go back",
                        tint = UfoTextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(UfoPrimaryIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = UfoIcons.Orbit,
                            contentDescription = "UFO Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "ufo",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = UfoTextPrimary,
                        letterSpacing = (-1).sp
                    )
                }
            }

            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onHelpClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = UfoIcons.Help,
                    contentDescription = "Help",
                    tint = UfoTextMuted,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
fun UfoBottomNav(
    currentScreen: UfoScreen,
    onSelect: (UfoScreen) -> Unit
) {
    val items = listOf(
        Triple(UfoScreen.HOME, "Home", UfoIcons.Shield),
        Triple(UfoScreen.ACTIVITY, "Activity", UfoIcons.Clock),
        Triple(UfoScreen.TRUSTED, "Trusted", UfoIcons.Users),
        Triple(UfoScreen.SETTINGS, "Settings", UfoIcons.Sliders)
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
                    .padding(horizontal = 12.dp, vertical = 7.dp),
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
                                    .padding(horizontal = 18.dp, vertical = 7.dp)
                            } else {
                                Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = label,
                                tint = if (isActive) UfoPrimaryIndigo else UfoTextMuted,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
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
fun UfoPill(
    text: String,
    tone: PillTone = PillTone.SAFE
) {
    val bgColor = when (tone) {
        PillTone.SAFE -> UfoSafeBg
        PillTone.WARN -> UfoWarnBg
        PillTone.DANGER -> UfoDangerBg
    }
    val textColor = when (tone) {
        PillTone.SAFE -> UfoSafeText
        PillTone.WARN -> UfoWarnText
        PillTone.DANGER -> UfoDangerText
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun UfoDisclosure(bloomEntries: Int = 0) {
    Text(
        text = if (bloomEntries > 0) {
            "LIVE ENGINE ACTIVE · ${bloomEntries} local fraud signatures loaded"
        } else {
            "CONCEPT DEMO · Sample data & live on-device protection"
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        textAlign = TextAlign.Center,
        color = UfoTextMuted,
        fontSize = 10.5.sp
    )
}

@Composable
fun UfoStepper(step: Int) {
    val steps = listOf("1 Input", "2 Review", "3 Result")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { index, label ->
            val isDone = index + 1 <= step
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDone) UfoPrimaryIndigo else UfoBorder)
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDone) UfoPrimaryIndigo else UfoTextMuted
                )
            }
        }
    }
}

@Composable
fun UfoPrimaryButton(
    text: String,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = UfoTextPrimary,
            contentColor = Color.White,
            disabledContainerColor = UfoTextPrimary.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun UfoSecondaryButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = UfoTextPrimary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 1. HOME SCREEN
// ---------------------------------------------------------------------------
@Composable
fun HomeScreenContent(
    uiState: DashboardUiState,
    activityList: List<FlaggedTransactionEntity>,
    onGo: (UfoScreen) -> Unit,
    onToggleBackgroundGuard: (Boolean) -> Unit,
    onTriggerPopupPreview: () -> Unit,
    onSelectActivity: (FlaggedTransactionEntity) -> Unit,
    onClearAll: () -> Unit,
    onReportScam: () -> Unit
) {
    val latestFlagged = remember(activityList) {
        activityList.firstOrNull { it.riskScore >= 60 } ?: activityList.firstOrNull()
    }
    val guardOn = uiState.isBackgroundGuardEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(onHelpClick = onReportScam)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Intro compact
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "BACKGROUND UPI OBSERVER",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (guardOn) "Active in background" else "Protection paused",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
            }

            // Hero Card with User's Choice Background UPI Guard Toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 196.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (guardOn) UfoHeroDark else Color(0xFF1E293B))
            ) {
                // Decorative Orbit Ring in top-right corner
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 38.dp, y = (-30).dp)
                ) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        style = Stroke(width = 18.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 196.dp)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allActive = uiState.isAccessibilityActive && uiState.isNotificationListenerActive
                        UfoPill(
                            text = when {
                                !guardOn -> "Background Guard · Off"
                                allActive -> "Active · Watching UPI Apps"
                                else -> "Running · Monitoring UPI"
                            },
                            tone = if (guardOn) PillTone.SAFE else PillTone.WARN
                        )
                        Icon(
                            imageVector = if (guardOn) UfoIcons.ShieldCheck else UfoIcons.Shield,
                            contentDescription = null,
                            tint = if (guardOn) UfoHeroIconTint else UfoHeroSubtext,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column {
                        Text(
                            text = if (guardOn) {
                                "Watching UPI screens automatically"
                            } else {
                                "Background UPI guard is off"
                            },
                            color = Color.White,
                            fontSize = 23.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.7).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (guardOn) {
                                "Extracts receiver details & amount as soon as a UPI payment screen opens, checks the backend, and triggers a Truecaller-style popup."
                            } else {
                                "You have paused background UPI monitoring. Turn the switch below on whenever you want automatic protection."
                            },
                            color = UfoHeroSubtext,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Choice Master Switch Bar inside Hero Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onToggleBackgroundGuard(!guardOn) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background UPI Guard",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (guardOn) "Enabled • Tap to pause anytime" else "Paused • Tap to enable anytime",
                                color = UfoHeroSubtext,
                                fontSize = 10.5.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(25.dp)
                                .clip(CircleShape)
                                .background(if (guardOn) Color(0xFF10B981) else Color(0xFF475569))
                                .padding(3.dp),
                            contentAlignment = if (guardOn) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            // Live Background Architecture Card (Replaces manual "Check request")
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live background pipeline",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = "Preview popup",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoPrimaryIndigo,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTriggerPopupPreview() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    BackgroundPipelineStepRow(
                        icon = UfoIcons.Scan,
                        title = "1. UPI App Triggered",
                        subtitle = "Detects Google Pay, PhonePe, Paytm, BHIM & banking UPI screens",
                        badgeText = if (guardOn) "Watching" else "Paused",
                        badgeTone = if (guardOn) PillTone.SAFE else PillTone.WARN
                    )

                    HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                    BackgroundPipelineStepRow(
                        icon = UfoIcons.ScanText,
                        title = "2. Real-Time Screen Extraction",
                        subtitle = "Extracts Receiver Name, VPA & ₹ Amount and sends to Backend",
                        badgeText = "Auto",
                        badgeTone = PillTone.SAFE
                    )

                    HorizontalDivider(color = UfoBorder, thickness = 1.dp)

                    BackgroundPipelineStepRow(
                        icon = UfoIcons.ShieldAlert,
                        title = "3. Truecaller-Style Risk Popup",
                        subtitle = "Shows compact floating risk score banner over the payment screen",
                        badgeText = "Instant",
                        badgeTone = PillTone.SAFE
                    )
                }
            }

            // Recent Interceptions Section
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent interceptions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activityList.isNotEmpty()) {
                            Text(
                                text = "Clear",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = UfoDangerText,
                                modifier = Modifier.clickable { onClearAll() }
                            )
                        }
                        Text(
                            text = "See all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoPrimaryIndigo,
                            modifier = Modifier.clickable { onGo(UfoScreen.ACTIVITY) }
                        )
                    }
                }

                if (latestFlagged != null) {
                    HomeRecentActivityCard(
                        item = latestFlagged,
                        onClick = { onSelectActivity(latestFlagged) }
                    )
                } else {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No UPI screens intercepted yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = UfoTextPrimary
                            )
                            Text(
                                text = "When you open a UPI app and reach the receiver & amount screen, UFO will automatically evaluate it in the background.",
                                fontSize = 12.sp,
                                color = UfoTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

@Composable
private fun BackgroundPipelineStepRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeTone: PillTone
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(UfoIndigoSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UfoPrimaryIndigo,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = UfoTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = UfoTextSecondary
            )
        }
        UfoPill(text = badgeText, tone = badgeTone)
    }
}

@Composable
private fun HomeRecentActivityCard(
    item: FlaggedTransactionEntity,
    onClick: () -> Unit
) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val appName = item.sourceApp?.substringAfterLast(".") ?: "PhonePe"
    val formattedAmount = item.amount?.let { formatInr(it) } ?: "₹4,999"
    val isHighRisk = item.riskScore >= 60

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isHighRisk) UfoDangerBg else UfoSafeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isHighRisk) UfoIcons.ShieldAlert else UfoIcons.Check,
                    contentDescription = null,
                    tint = if (isHighRisk) UfoDangerText else UfoSafeCheck,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.payeeName ?: item.vpa.substringBefore("@"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                Text(
                    text = "$appName · $timeStr",
                    fontSize = 10.5.sp,
                    color = UfoTextSecondary
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formattedAmount,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                UfoPill(
                    text = if (isHighRisk) "High risk · ${item.riskScore}" else "Reviewed",
                    tone = if (isHighRisk) PillTone.DANGER else PillTone.SAFE
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. INPUT SCREEN (Step 1 of 3)
// ---------------------------------------------------------------------------
@Composable
fun InputScreenContent(
    uiState: DashboardUiState,
    onUpdateText: (String) -> Unit,
    onUpdateAmount: (String) -> Unit,
    onAttach: (String) -> Unit,
    onApplyPreset: (String, String, String, Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Check a request", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp)
        ) {
            UfoStepper(step = 1)

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "STEP 1",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Paste the request.",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
                Text(
                    text = "UPI ID or request text",
                    color = UfoTextSecondary,
                    fontSize = 14.sp
                )
            }

            // Quick Sample Chips for easy testing on emulator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SamplePresetChip("Flipkart Trap") {
                    onApplyPreset("Flipkart Refund", "flipkart.refund@okaxis", "4,999", true)
                }
                SamplePresetChip("IRCTC Spoof") {
                    onApplyPreset("IRCTC Help", "irctcc@ybl", "1,280", true)
                }
                SamplePresetChip("Safe Contact") {
                    onApplyPreset("Asha Sharma", "asha.sharma@okicici", "650", false)
                }
            }

            // Request text textarea
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "Request text",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                OutlinedTextField(
                    value = uiState.requestText,
                    onValueChange = onUpdateText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = UfoPrimaryIndigo,
                        unfocusedBorderColor = UfoPrimaryIndigo,
                        focusedTextColor = UfoTextPrimary,
                        unfocusedTextColor = UfoTextPrimary
                    ),
                    textStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
                )
                Text(
                    text = "${uiState.requestText.length} characters",
                    fontSize = 11.sp,
                    color = UfoTextMuted
                )
            }

            // Amount input
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "Amount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                OutlinedTextField(
                    value = uiState.amountInput,
                    onValueChange = onUpdateAmount,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = UfoPrimaryIndigo,
                        unfocusedBorderColor = UfoBorder,
                        focusedTextColor = UfoTextPrimary,
                        unfocusedTextColor = UfoTextPrimary
                    ),
                    textStyle = TextStyle(fontSize = 14.sp)
                )
            }

            // Option Grid: Screenshot & QR reference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UploadOptionCard(
                    modifier = Modifier.weight(1f),
                    icon = UfoIcons.ImagePlus,
                    title = "Screenshot",
                    subtitle = uiState.attachedSource.ifBlank { "Upload image" },
                    onClick = { onAttach("Screenshot attached") }
                )
                UploadOptionCard(
                    modifier = Modifier.weight(1f),
                    icon = UfoIcons.QrCode,
                    title = "QR reference",
                    subtitle = uiState.attachedSource.ifBlank { "Upload QR" },
                    onClick = { onAttach("QR attached") }
                )
            }

            // Extracted details card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(UfoIndigoSoft)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = UfoIcons.ScanText,
                        contentDescription = null,
                        tint = UfoPrimaryIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Extracted details",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoPrimaryIndigo
                    )
                    UfoPill(text = "Live parser", tone = PillTone.SAFE)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.vpa,
                        fontSize = 12.sp,
                        color = UfoTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = uiState.amountInput.replace(" ", ""),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                }
            }

            UfoPrimaryButton(
                text = "Review details",
                trailingIcon = UfoIcons.ArrowRight,
                enabled = uiState.requestText.isNotBlank(),
                onClick = onNext
            )
        }

        UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

@Composable
private fun SamplePresetChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier
            .border(1.dp, UfoBorder, RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = UfoPrimaryIndigo,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun UploadOptionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .heightIn(min = 114.dp)
            .border(1.dp, UfoBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = UfoPrimaryIndigo,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = UfoTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = UfoTextMuted
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. REVIEW SCREEN (Step 2 of 3)
// ---------------------------------------------------------------------------
@Composable
fun ReviewScreenContent(
    uiState: DashboardUiState,
    onUpdateReceiver: (String) -> Unit,
    onUpdateVpa: (String) -> Unit,
    onUpdateAmount: (String) -> Unit,
    onUpdateCollect: (Boolean) -> Unit,
    onBack: () -> Unit,
    onConfirmCheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Review request", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp)
        ) {
            UfoStepper(step = 2)

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "STEP 2 OF 3",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Review request",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
                Text(
                    text = "Confirm who and how much.",
                    color = UfoTextSecondary,
                    fontSize = 14.sp
                )
            }

            // Notice warn
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (uiState.isCollectRequest) UfoWarnBg else UfoSafeBg)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (uiState.isCollectRequest) "Collect request" else "Payment request",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isCollectRequest) UfoWarnText else UfoSafeText
                )
                Text(
                    text = if (uiState.isCollectRequest) {
                        "This means money leaves your account."
                    } else {
                        "Standard outgoing peer/merchant payment."
                    },
                    fontSize = 12.sp,
                    color = if (uiState.isCollectRequest) UfoWarnText else UfoSafeText
                )
            }

            // Receiver input
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Receiver", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UfoTextPrimary)
                OutlinedTextField(
                    value = uiState.receiverName,
                    onValueChange = onUpdateReceiver,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = UfoPrimaryIndigo,
                        unfocusedBorderColor = UfoBorder
                    )
                )
            }

            // UPI ID / VPA input (Monospace)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("UPI ID / VPA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UfoTextPrimary)
                OutlinedTextField(
                    value = uiState.vpa,
                    onValueChange = onUpdateVpa,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = UfoPrimaryIndigo,
                        unfocusedBorderColor = UfoBorder
                    )
                )
            }

            // Amount requested input
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Amount requested", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UfoTextPrimary)
                OutlinedTextField(
                    value = uiState.amountInput,
                    onValueChange = onUpdateAmount,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = UfoPrimaryIndigo,
                        unfocusedBorderColor = UfoBorder
                    )
                )
            }

            // Request type selector
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Request type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UfoTextPrimary)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, UfoBorder, RoundedCornerShape(13.dp))
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onUpdateCollect(!uiState.isCollectRequest) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.isCollectRequest) {
                                "Collect request · You would pay"
                            } else {
                                "Payment request"
                            },
                            fontSize = 14.sp,
                            color = UfoTextPrimary
                        )
                        Icon(
                            imageVector = UfoIcons.ChevronDown,
                            contentDescription = "Toggle request type",
                            tint = UfoTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Call Telemetry Card
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UfoBorder, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = UfoIcons.Phone,
                        contentDescription = null,
                        tint = UfoWarnText,
                        modifier = Modifier.size(21.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isCallMonitorActive) "Call in progress" else "Call telemetry active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = "Unknown receiver",
                            fontSize = 11.sp,
                            color = UfoTextSecondary
                        )
                    }
                    UfoPill(text = "Be careful", tone = PillTone.WARN)
                }
            }

            UfoPrimaryButton(
                text = "Confirm & check risk",
                icon = UfoIcons.ShieldQuestion,
                onClick = onConfirmCheck
            )
        }

        UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

// ---------------------------------------------------------------------------
// 4. WARNING SCREEN (Fraud Alert)
// ---------------------------------------------------------------------------
@Composable
fun WarningScreenContent(
    uiState: DashboardUiState,
    onBack: () -> Unit,
    onAbort: () -> Unit,
    onDismissAlert: () -> Unit,
    onViewResult: () -> Unit,
    onReportScammer: () -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }
    val eval = uiState.simulationResult
    val vpa = eval?.vpa ?: uiState.vpa.ifBlank { "flipkart.refund@okaxis" }
    val amountText = eval?.amount?.let { formatInr(it) } ?: uiState.amountInput.replace(" ", "")
    val reasonText = eval?.triggers?.firstOrNull() ?: "Refund collect trap"
    val score = eval?.riskScore ?: 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Fraud alert", onBack = onBack, onHelpClick = onReportScammer)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "UPI FRAUD ALERT",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Stop. Check first.",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
                Text(
                    text = "A collect request takes money from you.",
                    color = UfoTextSecondary,
                    fontSize = 14.sp
                )
            }

            if (!dismissed) {
                // Dark Crimson Warning Card (#2B0909 with 4dp #EF4444 left border accent)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(UfoWarningCardBg)
                        .height(IntrinsicSize.Min)
                ) {
                    // Left red accent bar (4dp wide)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(UfoDangerBorder)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Sender header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = UfoIcons.Orbit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "UFO · Critical alert",
                                    color = UfoWarningCardSubtext,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "now",
                                color = UfoWarningCardSubtext,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Headline: DO NOT ENTER YOUR PIN
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Icon(
                                imageVector = UfoIcons.TriangleAlert,
                                contentDescription = null,
                                tint = UfoWarningGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "DO NOT ENTER YOUR PIN",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Text(
                            text = vpa,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "$amountText · $reasonText",
                            color = UfoWarningCardSubtext,
                            fontSize = 13.sp
                        )

                        Text(
                            text = "Risk score $score/100",
                            color = UfoWarningGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f), thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(UfoWarningCardSubtext)
                                .clickable { onAbort() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ABORT PAYMENT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = UfoWarningCardBg
                            )
                        }

                        Column(
                            modifier = Modifier
                                .width(105.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    onDismissAlert()
                                    dismissed = true
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "DISMISS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        }
                    }
                }
            } else {
                // Empty state after dismissal
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, UfoSwitchOff, RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = UfoIcons.ShieldCheck,
                        contentDescription = null,
                        tint = UfoSafeCheck,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Alert dismissed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    UfoSecondaryButton(
                        text = "View check result",
                        onClick = onViewResult
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

// ---------------------------------------------------------------------------
// 5. RESULT SCREEN (Step 3 of 3)
// ---------------------------------------------------------------------------
@Composable
fun ResultScreenContent(
    uiState: DashboardUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onAddTrusted: (String) -> Unit
) {
    val eval = uiState.simulationResult
    val score = eval?.riskScore ?: 0
    val receiverName = eval?.payeeName ?: uiState.receiverName.ifBlank { "Asha Sharma" }
    val vpa = eval?.vpa ?: uiState.vpa.ifBlank { "asha.sharma@okicici" }
    val amountStr = eval?.amount?.let { formatInr(it) } ?: "₹650"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Intercepted screen report", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp)
        ) {
            // Result Hero
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(UfoSafeBg)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(UfoSafeIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = UfoIcons.ShieldCheck,
                        contentDescription = null,
                        tint = UfoSafeText,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = if (score < 30) "No known risk detected" else "Reviewed check summary",
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoSafeText,
                    letterSpacing = (-0.7).sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$score",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = UfoSafeText
                    )
                    Column {
                        Text(
                            text = "/ 100 score",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoSafeText
                        )
                        Text(
                            text = "No guarantee of safety",
                            fontSize = 11.sp,
                            color = UfoSafeText
                        )
                    }
                }
            }

            // Detail Card
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("Receiver", fontSize = 11.sp, color = UfoTextSecondary)
                            Text(
                                text = receiverName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UfoTextPrimary
                            )
                        }
                        Text(
                            text = amountStr,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                    }

                    Text("UPI ID", fontSize = 11.sp, color = UfoTextSecondary)
                    Text(
                        text = vpa,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = UfoTextPrimary
                    )

                    HorizontalDivider(
                        color = UfoBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Text(
                        text = "Google Pay · P2P\n31 Oct 2026, 09:12",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = UfoTextSecondary
                    )
                }
            }

            // What we checked
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(
                    text = "What we checked",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                val checks = listOf(
                    "No known-risk match",
                    "No suspicious terms",
                    "No collect-to-receive signal"
                )
                checks.forEach { checkItem ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = UfoIcons.Check,
                            contentDescription = null,
                            tint = UfoSafeCheck,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = checkItem,
                            fontSize = 13.sp,
                            color = UfoTextSecondary
                        )
                    }
                }
            }

            // Safe Notice
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(UfoSafeBg)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Verify before you pay.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoSafeText
                )
                Text(
                    text = "Confirm the person, amount and UPI ID.",
                    fontSize = 12.sp,
                    color = UfoSafeText
                )
            }

            UfoPrimaryButton(
                text = "Back to Home",
                onClick = onDone
            )

            TextButton(
                onClick = { onAddTrusted(vpa) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Add $vpa to Trusted people",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoPrimaryIndigo
                )
            }
        }

        UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

// ---------------------------------------------------------------------------
// 6. ACTIVITY SCREEN
// ---------------------------------------------------------------------------
@Composable
fun ActivityScreenContent(
    activityList: List<FlaggedTransactionEntity>,
    onBack: () -> Unit,
    onSelectActivity: (FlaggedTransactionEntity) -> Unit,
    onDeleteActivityItem: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    val filteredList = remember(activityList, selectedFilter) {
        activityList.filter { item ->
            when (selectedFilter) {
                ActivityFilter.ALL -> true
                ActivityFilter.FLAGGED -> item.riskScore >= 60
                ActivityFilter.REVIEWED -> item.riskScore < 30
            }
        }
    }

    val totalCount = activityList.size
    val flaggedCount = activityList.count { it.riskScore >= 60 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Your activity", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "ON THIS DEVICE",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Every check, in context.",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
            }

            // Stats Grid (Checks vs Flagged)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "$totalCount",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = "Checks",
                            fontSize = 12.sp,
                            color = UfoTextSecondary
                        )
                    }
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "$flaggedCount",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoDangerText
                        )
                        Text(
                            text = "Flagged",
                            fontSize = 12.sp,
                            color = UfoTextSecondary
                        )
                    }
                }
            }

            // Segmented Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(UfoFilterTrack)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActivityFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { selectedFilter = filter },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) UfoPrimaryIndigo else UfoTextSecondary
                        )
                    }
                }
            }

            // Section Title + Clear action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent checks",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    Text(
                        text = "${filteredList.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoPrimaryIndigo
                    )
                }

                if (activityList.isNotEmpty()) {
                    Text(
                        text = "Clear all",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoDangerText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClearAll() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            // Activity List Card or Empty State
            if (filteredList.isEmpty()) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = UfoIcons.Clock,
                            contentDescription = null,
                            tint = UfoTextMuted,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "No recent checks",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = "Checks you run on this device will appear here.",
                            fontSize = 13.sp,
                            color = UfoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        filteredList.forEachIndexed { index, item ->
                            ActivityListRow(
                                item = item,
                                onClick = { onSelectActivity(item) },
                                onDelete = { onDeleteActivityItem(item.id) }
                            )
                            if (index < filteredList.lastIndex) {
                                HorizontalDivider(color = UfoBorder, thickness = 1.dp)
                            }
                        }
                    }
                }

                UfoSecondaryButton(
                    text = "Clear all recent checks",
                    onClick = onClearAll
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        UfoDisclosure()
    }
}

@Composable
private fun ActivityListRow(
    item: FlaggedTransactionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val tone = when {
        item.riskScore >= 60 -> PillTone.DANGER
        item.riskScore >= 30 -> PillTone.WARN
        else -> PillTone.SAFE
    }
    val statusText = when {
        item.riskScore >= 60 -> "High risk"
        item.riskScore >= 30 -> "Review"
        item.vpa.contains("arjun") -> "No known risk"
        else -> "Reviewed"
    }
    val iconBg = when (tone) {
        PillTone.DANGER -> UfoDangerBg
        PillTone.WARN -> UfoWarnBg
        PillTone.SAFE -> UfoSafeBg
    }
    val iconVec = when (tone) {
        PillTone.DANGER -> UfoIcons.ShieldAlert
        PillTone.WARN -> UfoIcons.Scan
        PillTone.SAFE -> UfoIcons.Check
    }
    val iconTint = when (tone) {
        PillTone.DANGER -> UfoDangerText
        PillTone.WARN -> UfoWarnText
        PillTone.SAFE -> UfoSafeCheck
    }

    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val appName = item.sourceApp?.substringAfterLast(".") ?: "UPI App"
    val amountStr = item.amount?.let { formatInr(it) } ?: "₹0"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVec,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.payeeName ?: item.vpa.substringBefore("@"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = UfoTextPrimary
            )
            Text(
                text = item.vpa,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                color = UfoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$appName · $timeStr",
                fontSize = 10.5.sp,
                color = UfoTextSecondary
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = amountStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                Text(
                    text = "✕",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onDelete() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            UfoPill(text = statusText, tone = tone)
        }
    }
}

// ---------------------------------------------------------------------------
// 7. TRUSTED SCREEN (Safety Circle / Whitelist)
// ---------------------------------------------------------------------------
@Composable
fun TrustedScreenContent(
    trustedList: List<WhitelistedVpaEntity>,
    onAddTrusted: (String) -> Unit,
    onRemoveTrusted: (String) -> Unit,
    onGoHome: () -> Unit
) {
    var vpaInput by remember { mutableStateOf("") }
    var showAddInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Trusted people")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "YOUR SAFETY CIRCLE",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Verify with someone you know.",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
            }

            if (trustedList.isEmpty() && !showAddInput) {
                // Empty state matching Demo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, UfoSwitchOff, RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = UfoIcons.Users,
                        contentDescription = null,
                        tint = UfoTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No trusted contacts yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Trusted UPI IDs stay on this device and bypass false-positive alerts.",
                        fontSize = 14.sp,
                        color = UfoTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    UfoPrimaryButton(
                        text = "Back home",
                        onClick = onGoHome
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    UfoSecondaryButton(
                        text = "Add trusted UPI ID",
                        onClick = { showAddInput = true }
                    )
                }
            } else {
                // Add Trusted VPA Form + List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Add trusted UPI ID / VPA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = UfoTextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = vpaInput,
                            onValueChange = { vpaInput = it },
                            placeholder = { Text("friend@okaxis", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = UfoPrimaryIndigo,
                                unfocusedBorderColor = UfoBorder
                            )
                        )
                        Button(
                            onClick = {
                                if (vpaInput.isNotBlank()) {
                                    onAddTrusted(vpaInput)
                                    vpaInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = UfoTextPrimary),
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (trustedList.isNotEmpty()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, UfoBorder, RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            trustedList.forEachIndexed { idx, entity ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(UfoSafeBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = UfoIcons.Check,
                                                contentDescription = null,
                                                tint = UfoSafeCheck,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = entity.vpa,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = UfoTextPrimary
                                            )
                                            Text(
                                                text = "Trusted contact",
                                                fontSize = 11.sp,
                                                color = UfoTextSecondary
                                            )
                                        }
                                    }
                                    TextButton(onClick = { onRemoveTrusted(entity.vpa) }) {
                                        Text("Remove", color = UfoDangerText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (idx < trustedList.lastIndex) {
                                    HorizontalDivider(color = UfoBorder, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        UfoDisclosure()
    }
}

// ---------------------------------------------------------------------------
// 8. SETTINGS SCREEN (Protection Setup)
// ---------------------------------------------------------------------------
@Composable
fun SettingsScreenContent(
    uiState: DashboardUiState,
    onToggleBackgroundGuard: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    onPreviewWarning: () -> Unit,
    onOpenFullOnboarding: () -> Unit,
    onSyncBlacklist: () -> Unit
) {
    val context = LocalContext.current

    // Local interactive overrides so user can also toggle switches in emulator demo while launching real Android permissions
    var notificationsToggle by remember(uiState.hasPostNotifications) {
        mutableStateOf(uiState.hasPostNotifications)
    }
    var notifAccessToggle by remember(uiState.isNotificationListenerActive) {
        mutableStateOf(uiState.isNotificationListenerActive)
    }
    var accessibilityToggle by remember(uiState.isAccessibilityActive) {
        mutableStateOf(uiState.isAccessibilityActive)
    }
    var phoneToggle by remember(uiState.hasPhoneState) {
        mutableStateOf(uiState.hasPhoneState)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        UfoTopBar(title = "Protection setup", onHelpClick = onOpenFullOnboarding)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "REQUIRED ACCESS",
                    color = UfoPrimaryIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "A few permissions. A clearer warning.",
                    color = UfoTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionSwitchRow(
                    title = "Background UPI Guard (Your Choice)",
                    subtitle = "Auto-extract UPI receiver & amount and show Truecaller-style popup",
                    checked = uiState.isBackgroundGuardEnabled,
                    onToggle = {
                        onToggleBackgroundGuard(!uiState.isBackgroundGuardEnabled)
                    }
                )

                PermissionSwitchRow(
                    title = "Post notifications",
                    subtitle = "Show urgent warnings",
                    checked = notificationsToggle,
                    onToggle = {
                        notificationsToggle = !notificationsToggle
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.hasPostNotifications) {
                            permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                )

                PermissionSwitchRow(
                    title = "Notification access",
                    subtitle = "Read UPI request text",
                    checked = notifAccessToggle,
                    onToggle = {
                        notifAccessToggle = !notifAccessToggle
                        if (!uiState.isNotificationListenerActive) {
                            PermissionHelper.openNotificationListenerSettings(context)
                        }
                    }
                )

                PermissionSwitchRow(
                    title = "Accessibility monitoring",
                    subtitle = "Check payment screens",
                    checked = accessibilityToggle,
                    onToggle = {
                        accessibilityToggle = !accessibilityToggle
                        if (!uiState.isAccessibilityActive) {
                            PermissionHelper.openAccessibilitySettings(context)
                        }
                    }
                )

                PermissionSwitchRow(
                    title = "Phone state",
                    subtitle = "Detect active calls",
                    checked = phoneToggle,
                    onToggle = {
                        phoneToggle = !phoneToggle
                        if (!uiState.hasPhoneState) {
                            permissionLauncher.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE))
                        }
                    }
                )
            }

            UfoSecondaryButton(
                text = "Preview Truecaller-style risk popup",
                onClick = onPreviewWarning
            )

            // Info Notice
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(UfoIndigoSoft)
                    .clickable { onSyncBlacklist() }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "System-level protection connected",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoIndigoDark
                )
                Text(
                    text = uiState.statusMessage ?: "Android permissions & local Bloom filter (${uiState.bloomFilterEntries} rules) are wired to the Kotlin risk engine.",
                    fontSize = 12.sp,
                    color = UfoIndigoDark
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        UfoDisclosure(bloomEntries = uiState.bloomFilterEntries)
    }
}

@Composable
private fun PermissionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UfoBorder, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UfoTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = UfoTextSecondary
                )
            }

            // Custom pill switch matching Demo .switch
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(25.dp)
                    .clip(CircleShape)
                    .background(if (checked) UfoPrimaryIndigo else UfoSwitchOff)
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun formatInr(amount: Double): String {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    fmt.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    return "₹${fmt.format(amount)}"
}
