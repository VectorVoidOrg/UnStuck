package com.ufo.fraudobserver.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelisted_vpas")
data class WhitelistedVpaEntity(
    @PrimaryKey
    val vpa: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)

