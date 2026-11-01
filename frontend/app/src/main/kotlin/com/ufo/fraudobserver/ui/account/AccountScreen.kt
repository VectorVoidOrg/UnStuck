package com.ufo.fraudobserver.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.data.remote.UserProfileResponse
import com.ufo.fraudobserver.demo.DemoProviderHolder
import com.ufo.fraudobserver.ui.components.UfoIcons
import com.ufo.fraudobserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    profile: UserProfileResponse?,
    isSignedIn: Boolean,
    onGoogleSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onDemoLoginSuccess: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDemoControls by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UfoBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Account & Plan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = UfoTextPrimary
                )
                Text(
                    text = "Identity, Quotas & Subscriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = UfoTextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (DemoProviderHolder.provider.isDemoAvailable) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(UfoSurfaceDark)
                            .border(1.dp, UfoBorder, RoundedCornerShape(8.dp))
                            .clickable { showDemoControls = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Demo Tools ⚙️",
                            style = MaterialTheme.typography.labelSmall,
                            color = UfoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = UfoSurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(UfoBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(UfoPrimaryIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = UfoPrimaryIndigo,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSignedIn) (profile?.email ?: "UFO User") else "Guest User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = if (isSignedIn) {
                                if (profile?.isDemo == true) "Verified Jury Demo Account" else "Google Account Verified"
                            } else "Sign in to sync checks across devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextSecondary
                        )
                    }

                    if (isSignedIn) {
                        OutlinedButton(
                            onClick = onSignOutClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UfoDangerText),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UfoBorder)
                        ) {
                            Text(text = "Sign Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (!isSignedIn) {
                    Button(
                        onClick = onGoogleSignInClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text(
                            text = "Sign in with Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Demo login button if demo provider available
                    DemoProviderHolder.provider.DemoAccountSection(
                        onDemoLoginSuccess = onDemoLoginSuccess
                    )
                }
            }
        }

        // Plan & Quota Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
                    Text(
                        text = "Current Plan",
                        style = MaterialTheme.typography.titleSmall,
                        color = UfoTextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (profile?.isEntitled == true) UfoSafeBg else UfoBorder.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (profile?.isEntitled == true) "PRO UNLIMITED" else "FREE TIER",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (profile?.isEntitled == true) UfoSafeCheck else UfoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Prevention Quota
                val prevUsed = profile?.preventionUsed ?: 0
                val prevRem = profile?.preventionRemaining ?: 100
                val prevTotal = if (profile?.isEntitled == true) "Unlimited" else "100"

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Prevention Checks",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = if (profile?.isEntitled == true) "Unlimited ($prevUsed used)" else "$prevRem remaining of $prevTotal",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (profile?.isEntitled == true) 1f else (prevRem.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = UfoPrimaryIndigo,
                        trackColor = UfoBg
                    )
                }

                // Unstuck Quota
                val unstuckUsed = profile?.unstuckUsed ?: 0
                val unstuckRem = profile?.unstuckRemaining ?: 5
                val unstuckTotal = if (profile?.isEntitled == true) "Unlimited" else "5"

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Unstuck Resolution Cases",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UfoTextPrimary
                        )
                        Text(
                            text = if (profile?.isEntitled == true) "Unlimited ($unstuckUsed used)" else "$unstuckRem remaining of $unstuckTotal",
                            style = MaterialTheme.typography.bodySmall,
                            color = UfoTextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (profile?.isEntitled == true) 1f else (unstuckRem.toFloat() / 5f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = UfoSafeCheck,
                        trackColor = UfoBg
                    )
                }

                if (profile?.isEntitled != true) {
                    Button(
                        onClick = onUpgradeClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UfoPrimaryIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = "Upgrade", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upgrade to Pro Unlimited",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Account Actions
        if (isSignedIn) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSignOutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UfoTextPrimary)
                ) {
                    Text(text = "Sign Out", fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Delete Account & Purge Data",
                        color = UfoDangerText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = "Delete Account?", color = UfoTextPrimary) },
            text = {
                Text(
                    text = "This will permanently purge your account, revoke all tokens, and delete your usage history per RBI guidelines. This action cannot be undone.",
                    color = UfoTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAccountClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UfoDangerText)
                ) {
                    Text(text = "Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = "Cancel", color = UfoTextSecondary)
                }
            },
            containerColor = UfoSurfaceDark
        )
    }

    // Demo Controls Sheet
    DemoProviderHolder.provider.DemoControlsSheet(
        isOpen = showDemoControls,
        onDismiss = { showDemoControls = false },
        onResetSuccess = { showDemoControls = false },
        onUsageUpdated = { showDemoControls = false }
    )
}

