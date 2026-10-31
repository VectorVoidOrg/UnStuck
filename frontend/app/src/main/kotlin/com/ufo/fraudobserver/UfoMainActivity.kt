package com.ufo.fraudobserver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ufo.fraudobserver.data.repository.FraudRepository
import com.ufo.fraudobserver.feature.unstuck.ui.UnstuckRoute
import com.ufo.fraudobserver.ui.alert.FraudAlertDetailScreen
import com.ufo.fraudobserver.ui.dashboard.DashboardScreen
import com.ufo.fraudobserver.ui.dashboard.DashboardViewModel
import com.ufo.fraudobserver.ui.onboarding.OnboardingScreen
import com.ufo.fraudobserver.ui.onboarding.PermissionHelper
import com.ufo.fraudobserver.ui.report.CommunityReportScreen
import com.ufo.fraudobserver.ui.theme.UfoFraudObserverTheme
import com.ufo.fraudobserver.ui.whitelist.WhitelistScreen
import kotlinx.coroutines.launch

class UfoMainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UfoFraudObserverTheme {
                val navController = rememberNavController()
                val repository = remember { FraudRepository(applicationContext) }
                val scope = rememberCoroutineScope()

                // Check permissions on startup
                val isCriticalGranted = remember {
                    mutableStateOf(PermissionHelper.areCriticalPermissionsGranted(this@UfoMainActivity))
                }

                // Handle Incoming Navigation Intent (e.g. from ABORT PAYMENT notification action)
                LaunchedEffect(intent) {
                    handleIntent(intent) { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }

                val startDestination = "dashboard"

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onPermissionsCompleted = {
                                isCriticalGranted.value = true
                                navController.navigate("dashboard") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("dashboard") {
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigateToReport = { navController.navigate("report") },
                            onNavigateToWhitelist = { navController.navigate("whitelist") },
                            onNavigateToOnboarding = { navController.navigate("onboarding") }
                        )
                    }

                    composable("alert_detail?vpa={vpa}&payee={payee}&amount={amount}&risk={risk}&trigger={trigger}") { backStackEntry ->
                        val vpa = backStackEntry.arguments?.getString("vpa") ?: intent.getStringExtra("EXTRA_VPA") ?: "scam@upi"
                        val payee = backStackEntry.arguments?.getString("payee") ?: intent.getStringExtra("EXTRA_PAYEE")
                        val amount = intent.getDoubleExtra("EXTRA_AMOUNT", 0.0)
                        val risk = intent.getIntExtra("EXTRA_RISK_SCORE", 95)
                        val trigger = intent.getStringExtra("EXTRA_TRIGGER") ?: "Critical Fraud Pattern Detected"

                        FraudAlertDetailScreen(
                            vpa = vpa,
                            payeeName = payee,
                            amount = amount,
                            riskScore = risk,
                            triggerReason = trigger,
                            onNavigateBack = {
                                navController.navigate("dashboard") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            },
                            onWhitelistReceiver = { trustedVpa ->
                                scope.launch {
                                    repository.addToWhitelist(trustedVpa)
                                    navController.navigate("dashboard") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            },
                            onReportScammer = { scamVpa ->
                                navController.navigate("report?vpa=$scamVpa")
                            }
                        )
                    }

                    composable("report") {
                        CommunityReportScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("report?vpa={vpa}") { entry ->
                        val vpa = entry.arguments?.getString("vpa") ?: ""
                        CommunityReportScreen(
                            prefilledVpa = vpa,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("whitelist") {
                        WhitelistScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("unstuck") {
                        UnstuckRoute(
                            onNavigateBackToHome = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, onNavigate: (String) -> Unit) {
        if (intent == null) return
        val target = intent.getStringExtra("NAVIGATE_TO")
        if (target == "ALERT_DETAIL") {
            val vpa = intent.getStringExtra("EXTRA_VPA") ?: ""
            val payee = intent.getStringExtra("EXTRA_PAYEE") ?: ""
            onNavigate("alert_detail?vpa=$vpa&payee=$payee&amount=0&risk=95&trigger=Alert")
        }
    }
}

