package com.ufo.fraudobserver.feature.unstuck.domain

object PenaltyEngine {
    fun calculate(
        daysLate: Int,
        compensationRate: Double = 100.0,
        hasCompensation: Boolean = true
    ): Double {
        if (!hasCompensation || daysLate <= 0) return 0.0
        return daysLate * compensationRate
    }
}

