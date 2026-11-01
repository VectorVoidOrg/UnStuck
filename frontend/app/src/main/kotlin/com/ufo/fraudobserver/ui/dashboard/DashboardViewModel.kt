package com.ufo.fraudobserver.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ufo.fraudobserver.data.local.FlaggedTransactionEntity
import com.ufo.fraudobserver.data.repository.FraudRepository
import com.ufo.fraudobserver.engine.BloomFilterEngine
import com.ufo.fraudobserver.engine.RiskEvaluationResult
import com.ufo.fraudobserver.engine.RiskEvaluator
import com.ufo.fraudobserver.engine.RiskStatus
import com.ufo.fraudobserver.engine.UfoProtectionPrefs
import com.ufo.fraudobserver.intervention.InterventionNotifier
import com.ufo.fraudobserver.parser.PayloadParser
import com.ufo.fraudobserver.ui.onboarding.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isBackgroundGuardEnabled: Boolean = true,
    val isAccessibilityActive: Boolean = false,
    val hasPostNotifications: Boolean = false,
    val isBatteryOptimizedIgnored: Boolean = false,
    val bloomFilterEntries: Int = 0,
    // Step 1 & Step 2 flow state
    val requestText: String = "Flipkart Refund requested ₹4,999. Pay to flipkart.refund@okaxis.",
    val receiverName: String = "Flipkart Refund",
    val vpa: String = "flipkart.refund@okaxis",
    val amountInput: String = "₹ 4,999",
    val isCollectRequest: Boolean = true,
    val attachedSource: String = "",
    // Latest evaluated / selected check
    val simulationResult: RiskEvaluationResult? = null,
    val activePopupBanner: RiskEvaluationResult? = null,
    val isSimulating: Boolean = false,
    val statusMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FraudRepository(application)
    private val bloomFilter = BloomFilterEngine.getInstance(application)
    private val riskEvaluator = RiskEvaluator.getInstance(application)
    private val notifier = InterventionNotifier.getInstance(application)
    private val tokenStore = com.ufo.fraudobserver.data.local.SecureTokenStore.getInstance(application)
    private val apiService = com.ufo.fraudobserver.data.remote.UfoApiService.create(application)

    private val _userProfile = MutableStateFlow<com.ufo.fraudobserver.data.remote.UserProfileResponse?>(null)
    val userProfile: StateFlow<com.ufo.fraudobserver.data.remote.UserProfileResponse?> = _userProfile.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

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
        UfoProtectionPrefs.init(application)
        viewModelScope.launch {
            repository.cleanLegacySeededDataIfNeeded()
        }
        refreshStatus()
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            val token = tokenStore.getAccessToken()
            if (!token.isNullOrBlank()) {
                _isSignedIn.value = true
                try {
                    val resp = apiService.getProfile()
                    if (resp.isSuccessful && resp.body() != null) {
                        _userProfile.value = resp.body()
                    }
                } catch (e: Exception) {
                    // network error or expired
                }
            } else {
                _isSignedIn.value = false
                _userProfile.value = null
            }
        }
    }

    fun handleDemoLogin(accessToken: String, refreshToken: String) {
        viewModelScope.launch {
            tokenStore.saveTokens(accessToken, refreshToken)
            refreshProfile()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            tokenStore.clearTokens()
            _isSignedIn.value = false
            _userProfile.value = null
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                apiService.deleteAccount()
            } catch (e: Exception) {
                // proceed
            }
            tokenStore.clearTokens()
            _isSignedIn.value = false
            _userProfile.value = null
        }
    }

    fun refreshStatus() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            isBackgroundGuardEnabled = UfoProtectionPrefs.isBackgroundGuardEnabled(context),
            isAccessibilityActive = PermissionHelper.isAccessibilityServiceEnabled(context),
            hasPostNotifications = PermissionHelper.hasPostNotificationsPermission(context),
            isBatteryOptimizedIgnored = PermissionHelper.isBatteryOptimizationIgnored(context),
            bloomFilterEntries = bloomFilter.getEstimatedCount()
        )
    }

    fun setBackgroundGuardEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        UfoProtectionPrefs.setBackgroundGuardEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(
            isBackgroundGuardEnabled = enabled,
            statusMessage = if (enabled) {
                "Background UPI Guard enabled — watching UPI payment screens"
            } else {
                "Background UPI Guard paused by user"
            }
        )
    }

    fun dismissPopupBanner() {
        _uiState.value = _uiState.value.copy(activePopupBanner = null)
    }

    fun triggerTruecallerPopupPreview() {
        val current = _uiState.value
        val vpa = current.vpa.ifBlank { "flipkart.refund@okaxis" }
        val payee = current.receiverName.ifBlank { "Flipkart Refund" }
        val amount = current.amountInput
            .replace("₹", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull() ?: 4999.0
        val score = current.simulationResult?.riskScore ?: 95
        val triggers = current.simulationResult?.triggers ?: listOf(
            "Refund collect trap",
            "Blacklisted Fraud ID"
        )
        val status = when {
            score >= 60 -> RiskStatus.FRAUD
            score >= 30 -> RiskStatus.WARNING
            else -> RiskStatus.SAFE
        }

        notifier.showIntervention(
            vpa = vpa,
            riskScore = score,
            primaryTrigger = triggers.firstOrNull() ?: "Evaluated by UFO Guard",
            secondaryTrigger = triggers.drop(1).firstOrNull(),
            payeeName = payee,
            amount = amount,
            sourceApp = "Google Pay",
            backendVerified = current.simulationResult?.backendVerified ?: false
        )

        _uiState.value = _uiState.value.copy(
            activePopupBanner = RiskEvaluationResult(
                vpa = vpa,
                payeeName = payee,
                amount = amount,
                riskScore = score,
                status = status,
                triggers = triggers,
                isHardBlocked = score >= 90,
                latencyMs = 12L,
                backendVerified = current.simulationResult?.backendVerified ?: false,
                sourceApp = "Google Pay"
            )
        )
    }

    fun updateRequestText(text: String) {
        val extractedVpa = PayloadParser.extractVpa(text)
        val extractedAmount = PayloadParser.extractAmount(text)
        val isCollect = PayloadParser.isCollectRequest(text)

        val candidateName = text.substringBefore(" requested", missingDelimiterValue = "")
            .ifBlank {
                if (extractedVpa != null) {
                    extractedVpa.substringBefore("@")
                        .split(".", "-", "_")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                } else {
                    _uiState.value.receiverName
                }
            }

        _uiState.value = _uiState.value.copy(
            requestText = text,
            vpa = extractedVpa ?: _uiState.value.vpa,
            amountInput = if (extractedAmount != null) "₹ ${extractedAmount.toInt()}" else _uiState.value.amountInput,
            receiverName = candidateName,
            isCollectRequest = isCollect || _uiState.value.isCollectRequest
        )
    }

    fun updateAmountInput(amount: String) {
        _uiState.value = _uiState.value.copy(amountInput = amount)
    }

    fun updateReceiverName(name: String) {
        _uiState.value = _uiState.value.copy(receiverName = name)
    }

    fun updateVpa(vpa: String) {
        _uiState.value = _uiState.value.copy(vpa = vpa)
    }

    fun updateCollectRequest(isCollect: Boolean) {
        _uiState.value = _uiState.value.copy(isCollectRequest = isCollect)
    }

    fun setAttachment(label: String) {
        _uiState.value = _uiState.value.copy(attachedSource = label)
    }

    fun applyQuickPreset(presetName: String, presetVpa: String, presetAmount: String, isCollect: Boolean) {
        val text = if (isCollect) {
            "$presetName requested ₹$presetAmount. Pay to $presetVpa."
        } else {
            "Sending ₹$presetAmount to $presetName ($presetVpa)."
        }
        _uiState.value = _uiState.value.copy(
            requestText = text,
            receiverName = presetName,
            vpa = presetVpa,
            amountInput = "₹ $presetAmount",
            isCollectRequest = isCollect
        )
    }

    fun evaluateCurrentRequest(onComplete: (RiskEvaluationResult) -> Unit) {
        val current = _uiState.value
        val numericAmount = current.amountInput
            .replace("₹", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSimulating = true)
            val result = riskEvaluator.evaluate(
                vpa = current.vpa,
                payeeName = current.receiverName.ifBlank { "Receiver" },
                amount = numericAmount,
                isCollectRequest = current.isCollectRequest,
                contextText = current.requestText,
                sourceApp = "Google Pay",
                triggerIntervention = true
            )

            _uiState.value = _uiState.value.copy(
                isSimulating = false,
                simulationResult = result,
                activePopupBanner = result,
                statusMessage = "Evaluated in ${result.latencyMs}ms • Score ${result.riskScore}/100"
            )
            onComplete(result)
        }
    }

    fun selectActivityItem(item: FlaggedTransactionEntity) {
        val status = when {
            item.riskScore >= 60 -> RiskStatus.FRAUD
            item.riskScore >= 30 -> RiskStatus.WARNING
            else -> RiskStatus.SAFE
        }
        val triggers = item.triggerReason.split(" • ").filter { it.isNotBlank() }
        val result = RiskEvaluationResult(
            vpa = item.vpa,
            payeeName = item.payeeName,
            amount = item.amount,
            riskScore = item.riskScore,
            status = status,
            triggers = triggers.ifEmpty { listOf(item.triggerReason) },
            isHardBlocked = item.riskScore >= 90,
            latencyMs = 1L,
            sourceApp = item.sourceApp
        )
        _uiState.value = _uiState.value.copy(
            receiverName = item.payeeName ?: item.vpa.substringBefore("@"),
            vpa = item.vpa,
            amountInput = item.amount?.let { "₹ ${it.toInt()}" } ?: "₹ 0",
            simulationResult = result
        )
    }

    fun dismissWarningForCurrentVpa() {
        _uiState.value = _uiState.value.copy(activePopupBanner = null)
    }

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
                activePopupBanner = result,
                statusMessage = "Evaluated in ${result.latencyMs}ms -> Risk Score: ${result.riskScore}%"
            )
        }
    }

    fun deleteActivityItem(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
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
