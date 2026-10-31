package com.ufo.fraudobserver.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ufo.fraudobserver.ui.theme.*

@Composable
fun OnboardingScreen(
    onPermissionsCompleted: () -> Unit
) {
    val context = LocalContext.current

    var isAccessibilityEnabled by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isNotificationListenerEnabled by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context)) }
    var hasPostNotification by remember { mutableStateOf(PermissionHelper.hasPostNotificationsPermission(context)) }
    var hasPhoneState by remember { mutableStateOf(PermissionHelper.hasPhoneStatePermission(context)) }
    var isBatteryOptimized by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    // Re-check permissions when returning from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
        isNotificationListenerEnabled = PermissionHelper.isNotificationListenerEnabled(context)
        hasPostNotification = PermissionHelper.hasPostNotificationsPermission(context)
        hasPhoneState = PermissionHelper.hasPhoneStatePermission(context)
        isBatteryOptimized = PermissionHelper.isBatteryOptimizationIgnored(context)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPhoneState = permissions[android.Manifest.permission.READ_PHONE_STATE] ?: hasPhoneState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPostNotification = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: hasPostNotification
        }
    }

    val allCriticalReady = isAccessibilityEnabled && isNotificationListenerEnabled && hasPhoneState && hasPostNotification

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🛸 UFO Setup & Defense Activation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Unified Fraud Observer operates at the system level to intercept UPI scams prior to PIN entry. Grant required system permissions below.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Android 13+ Restricted Settings Warning & Guide
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CrimsonCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AlertRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android 13+ Sideloading Notice",
                            fontWeight = FontWeight.Bold,
                            color = AlertRed,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If Accessibility is greyed out with 'Restricted Setting':\n1. Tap 'Open App Info' below.\n2. Tap the 3-dot menu at top-right -> 'Allow restricted settings'.\n3. Return here and tap 'Enable Accessibility'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD1D1)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { PermissionHelper.openAppDetailsSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Open App Info (Allow Restricted)", color = TextPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 1. Accessibility Service Card
        PermissionCard(
            title = "Screen Scraping Accessibility",
            description = "Reads receiver VPA and payment amounts right before PIN entry without overlay conflicts.",
            isGranted = isAccessibilityEnabled,
            actionLabel = "Enable Accessibility",
            onAction = { PermissionHelper.openAccessibilitySettings(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Notification Listener Card
        PermissionCard(
            title = "Notification Collect Ingestion",
            description = "Intercepts incoming UPI collect requests from Google Pay, PhonePe, Paytm, etc. before opening app.",
            isGranted = isNotificationListenerEnabled,
            actionLabel = "Enable Notification Access",
            onAction = { PermissionHelper.openNotificationListenerSettings(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Runtime Permissions (Phone State & Post Notifications)
        val runtimeGranted = hasPhoneState && hasPostNotification
        PermissionCard(
            title = "Live Call & Heads-Up Alerts",
            description = "Detects active phone calls (vishing scam telemetry) and displays high-priority heads-up interventions.",
            isGranted = runtimeGranted,
            actionLabel = "Grant Telephony & Alerts",
            onAction = {
                val perms = mutableListOf(android.Manifest.permission.READ_PHONE_STATE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                requestPermissionLauncher.launch(perms.toTypedArray())
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Battery Optimization
        PermissionCard(
            title = "Battery Optimization Exemption",
            description = "Prevents aggressive OEM battery killers (MIUI, OneUI) from terminating protection listeners.",
            isGranted = isBatteryOptimized,
            actionLabel = "Ignore Optimizations",
            onAction = { PermissionHelper.requestIgnoreBatteryOptimization(context) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPermissionsCompleted,
            enabled = allCriticalReady,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allCriticalReady) SafeGreen else CardBorder,
                disabledContainerColor = SurfaceElevated
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (allCriticalReady) "🛡️ Activate UFO Protection Shield" else "Grant Critical Permissions to Activate",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (allCriticalReady) Color.Black else TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isGranted) SafeGreen.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (isGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = SafeGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(actionLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

