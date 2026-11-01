package com.ufo.fraudobserver.demo

import android.content.Context
import androidx.compose.runtime.Composable

interface DemoProvider {
    val isDemoAvailable: Boolean
    val demoAccessCode: String

    @Composable
    fun DemoAccountSection(
        onDemoLoginSuccess: (String) -> Unit
    )

    @Composable
    fun DemoControlsSheet(
        isOpen: Boolean,
        onDismiss: () -> Unit,
        onResetSuccess: () -> Unit,
        onUsageUpdated: () -> Unit
    )

    fun openDemoPaymentActivity(context: Context)
}

object NoOpDemoProvider : DemoProvider {
    override val isDemoAvailable: Boolean = false
    override val demoAccessCode: String = ""

    @Composable
    override fun DemoAccountSection(onDemoLoginSuccess: (String) -> Unit) {}

    @Composable
    override fun DemoControlsSheet(
        isOpen: Boolean,
        onDismiss: () -> Unit,
        onResetSuccess: () -> Unit,
        onUsageUpdated: () -> Unit
    ) {}

    override fun openDemoPaymentActivity(context: Context) {}
}

object DemoProviderHolder {
    var provider: DemoProvider = NoOpDemoProvider
}

