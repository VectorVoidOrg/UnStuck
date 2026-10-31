package com.ufo.fraudobserver.feature.unstuck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ufo.fraudobserver.feature.unstuck.data.model.*
import com.ufo.fraudobserver.feature.unstuck.data.repository.UnstuckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UnstuckSubScreen {
    HUB,
    SCANNER,
    REVIEW,
    DETAIL,
    ESCALATION,
    RIGHTS
}

class UnstuckViewModel(
    private val repository: UnstuckRepository = UnstuckRepository()
) : ViewModel() {

    val cases: StateFlow<List<UnstuckCase>> = repository.casesList

    private val _currentSubScreen = MutableStateFlow(UnstuckSubScreen.HUB)
    val currentSubScreen: StateFlow<UnstuckSubScreen> = _currentSubScreen.asStateFlow()

    private val _activeCase = MutableStateFlow<UnstuckCase?>(null)
    val activeCase: StateFlow<UnstuckCase?> = _activeCase.asStateFlow()

    private val _activeExtractResponse = MutableStateFlow<ExtractResponse?>(null)
    val activeExtractResponse: StateFlow<ExtractResponse?> = _activeExtractResponse.asStateFlow()

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _rules.value = repository.getRules()
            // Set first case as active by default if available
            val list = repository.casesList.value
            if (list.isNotEmpty()) {
                _activeCase.value = list.first()
            }
        }
    }

    fun navigateTo(screen: UnstuckSubScreen) {
        _currentSubScreen.value = screen
    }

    fun selectCase(case: UnstuckCase) {
        _activeCase.value = case
        _currentSubScreen.value = UnstuckSubScreen.DETAIL
    }

    fun setExtractResponse(response: ExtractResponse) {
        _activeExtractResponse.value = response
        _currentSubScreen.value = UnstuckSubScreen.REVIEW
    }

    fun extractFromSms(smsText: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val resp = repository.extract(smsText = smsText)
            _activeExtractResponse.value = resp
            _isLoading.value = false
            _currentSubScreen.value = UnstuckSubScreen.REVIEW
            onDone()
        }
    }

    fun submitNewCase(req: CreateCaseRequest, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val created = repository.createCase(req)
            _activeCase.value = created
            _isLoading.value = false
            _currentSubScreen.value = UnstuckSubScreen.DETAIL
            onDone()
        }
    }

    fun updateTicketNumber(caseId: String, ticketNo: String) {
        viewModelScope.launch {
            val updated = repository.updateCase(caseId = caseId, ticketNumber = ticketNo)
            if (updated != null) {
                _activeCase.value = updated
            }
        }
    }

    fun markMoneyReversed(caseId: String) {
        viewModelScope.launch {
            val updated = repository.updateCase(
                caseId = caseId,
                moneyReturned = true,
                reversalDate = "2026-10-23 12:00:00"
            )
            if (updated != null) {
                _activeCase.value = updated
            }
        }
    }

    fun addTimelineNote(caseId: String, note: String) {
        viewModelScope.launch {
            repository.logEvent(caseId, "BANK_RESPONSE", note)
            val updated = repository.getCase(caseId)
            if (updated != null) {
                _activeCase.value = updated
            }
        }
    }

    fun loadFounderDemoCase() {
        val list = repository.casesList.value
        val demo = list.firstOrNull { it.ticketNumber == "HDFC-GRV-88219" } ?: list.firstOrNull()
        if (demo != null) {
            _activeCase.value = demo
            _currentSubScreen.value = UnstuckSubScreen.DETAIL
        }
    }
}

