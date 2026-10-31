package com.ufo.fraudobserver.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flagged_transactions")
data class FlaggedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vpa: String,
    val payeeName: String?,
    val amount: Double?,
    val riskScore: Int,
    val triggerReason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String?
)

