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
import com.ufo.fraudobserver.ui.dashboard.DashboardScreen
import com.ufo.fraudobserver.ui.dashboard.DashboardViewModel
import com.ufo.fraudobserver.ui.onboarding.OnboardingScreen
import com.ufo.fraudobserver.ui.onboarding.PermissionHelper
import com.ufo.fraudobserver.ui.theme.UfoFraudObserverTheme

class UfoMainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UfoFraudObserverTheme {
                val navController = rememberNavController()

                val isCriticalGranted = remember {
                    mutableStateOf(PermissionHelper.areCriticalPermissionsGranted(this@UfoMainActivity))
                }

                NavHost(
                    navController = navController,
                    startDestination = "dashboard"
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
                            onNavigateToOnboarding = { navController.navigate("onboarding") }
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
}

