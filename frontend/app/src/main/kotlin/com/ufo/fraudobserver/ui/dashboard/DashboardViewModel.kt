package com.ufo.fraudobserver.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ufo.fraudobserver.data.local.FlaggedTransactionEntity
import com.ufo.fraudobserver.data.repository.FraudRepository
import com.ufo.fraudobserver.engine.BloomFilterEngine
import com.ufo.fraudobserver.engine.CallStateMonitor
import com.ufo.fraudobserver.engine.RiskEvaluationResult
import com.ufo.fraudobserver.engine.RiskEvaluator
import com.ufo.fraudobserver.ui.onboarding.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isAccessibilityActive: Boolean = false,
    val isNotificationListenerActive: Boolean = false,
    val isCallMonitorActive: Boolean = false,
    val bloomFilterEntries: Int = 0,
    val simulationResult: RiskEvaluationResult? = null,
    val isSimulating: Boolean = false,
    val statusMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FraudRepository(application)
    private val bloomFilter = BloomFilterEngine.getInstance(application)
    private val callStateMonitor = CallStateMonitor.getInstance(application)
    private val riskEvaluator = RiskEvaluator.getInstance(application)

    val flaggedTransactions: StateFlow<List<FlaggedTransactionEntity>> =
        repository.flaggedTransactions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val totalThreatsCount: StateFlow<Int> =
        repository.totalThreatCount.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            isAccessibilityActive = PermissionHelper.isAccessibilityServiceEnabled(context),
            isNotificationListenerActive = PermissionHelper.isNotificationListenerEnabled(context),
            isCallMonitorActive = callStateMonitor.isCallActive(),
            bloomFilterEntries = bloomFilter.getEstimatedCount()
        )
    }

    /**
     * Executes instant zero-latency risk simulation on Dispatchers.Default
     * directly testing all 3 engine layers (Bloom Filter, Heuristic/Typosquatting, Call Telemetry).
     */
    fun simulateRiskCheck(
        vpa: String,
        amount: Double?,
        isCollectRequest: Boolean,
        simulateActiveCall: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSimulating = true)

            val result = riskEvaluator.evaluate(
                vpa = vpa,
                payeeName = "Simulated Receiver",
                amount = amount,
                isCollectRequest = isCollectRequest,
                contextText = if (isCollectRequest) "Payment collect request" else null,
                sourceApp = "UfoSimulator",
                triggerIntervention = true
            )

            _uiState.value = _uiState.value.copy(
                isSimulating = false,
                simulationResult = result,
                statusMessage = "Evaluated in ${result.latencyMs}ms -> Risk Score: ${result.riskScore}%"
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun syncDeltaNow() {
        viewModelScope.launch {
            val count = repository.syncBlacklistDelta()
            _uiState.value = _uiState.value.copy(
                bloomFilterEntries = bloomFilter.getEstimatedCount(),
                statusMessage = "Delta sync complete: $count new VPAs received"
            )
        }
    }
}

