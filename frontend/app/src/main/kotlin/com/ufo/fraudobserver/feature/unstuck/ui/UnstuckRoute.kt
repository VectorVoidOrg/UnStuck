package com.ufo.fraudobserver.feature.unstuck.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ufo.fraudobserver.feature.unstuck.data.model.ExtractResponse
import com.ufo.fraudobserver.feature.unstuck.data.model.UnstuckTransaction
import com.ufo.fraudobserver.feature.unstuck.ui.screens.*

@Composable
fun UnstuckRoute(
    viewModel: UnstuckViewModel = viewModel(),
    onNavigateBackToHome: () -> Unit = {}
) {
    val currentSubScreen by viewModel.currentSubScreen.collectAsState()
    val cases by viewModel.cases.collectAsState()
    val activeCase by viewModel.activeCase.collectAsState()
    val activeExtractResponse by viewModel.activeExtractResponse.collectAsState()
    val rules by viewModel.rules.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentSubScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "UnstuckSubScreenTransition"
        ) { subScreen ->
            when (subScreen) {
                UnstuckSubScreen.HUB -> {
                    UnstuckHomeScreen(
                        cases = cases,
                        onOpenScanner = { viewModel.navigateTo(UnstuckSubScreen.SCANNER) },
                        onSelectCase = { c -> viewModel.selectCase(c) },
                        onOpenRights = { viewModel.navigateTo(UnstuckSubScreen.RIGHTS) },
                        onLoadDemo = { viewModel.loadFounderDemoCase() }
                    )
                }

                UnstuckSubScreen.SCANNER -> {
                    UnstuckScannerScreen(
                        onBack = { viewModel.navigateTo(UnstuckSubScreen.HUB) },
                        onExtract = { sms -> viewModel.extractFromSms(sms) }
                    )
                }

                UnstuckSubScreen.REVIEW -> {
                    val extract = activeExtractResponse ?: ExtractResponse(
                        transaction = UnstuckTransaction(
                            amount = 1500.0,
                            reference = "428912345678",
                            bank = "HDFC Bank",
                            date = "2026-10-15 14:32:10"
                        )
                    )
                    UnstuckReviewScreen(
                        extractResponse = extract,
                        rules = rules,
                        onBack = { viewModel.navigateTo(UnstuckSubScreen.SCANNER) },
                        onSubmitCase = { req -> viewModel.submitNewCase(req) }
                    )
                }

                UnstuckSubScreen.DETAIL -> {
                    activeCase?.let { c ->
                        UnstuckDetailScreen(
                            caseData = c,
                            onBack = { viewModel.navigateTo(UnstuckSubScreen.HUB) },
                            onOpenEscalation = { viewModel.navigateTo(UnstuckSubScreen.ESCALATION) },
                            onOpenRights = { viewModel.navigateTo(UnstuckSubScreen.RIGHTS) },
                            onUpdateTicket = { caseId, ticketNo -> viewModel.updateTicketNumber(caseId, ticketNo) },
                            onMarkReversed = { caseId -> viewModel.markMoneyReversed(caseId) },
                            onAddNote = { caseId, note -> viewModel.addTimelineNote(caseId, note) }
                        )
                    } ?: run {
                        viewModel.navigateTo(UnstuckSubScreen.HUB)
                    }
                }

                UnstuckSubScreen.ESCALATION -> {
                    activeCase?.let { c ->
                        UnstuckEscalationScreen(
                            caseData = c,
                            onBack = { viewModel.navigateTo(UnstuckSubScreen.DETAIL) }
                        )
                    } ?: run {
                        viewModel.navigateTo(UnstuckSubScreen.HUB)
                    }
                }

                UnstuckSubScreen.RIGHTS -> {
                    UnstuckRightsScreen(
                        onBack = {
                            if (activeCase != null) {
                                viewModel.navigateTo(UnstuckSubScreen.DETAIL)
                            } else {
                                viewModel.navigateTo(UnstuckSubScreen.HUB)
                            }
                        }
                    )
                }
            }
        }
    }
}

