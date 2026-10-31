package com.ufo.fraudobserver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedVpaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(whitelisted: WhitelistedVpaEntity)

    @Query("SELECT * FROM whitelisted_vpas ORDER BY addedTimestamp DESC")
    fun getAllFlow(): Flow<List<WhitelistedVpaEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_vpas WHERE LOWER(vpa) = LOWER(:vpa))")
    suspend fun isWhitelisted(vpa: String): Boolean

    @Query("DELETE FROM whitelisted_vpas WHERE LOWER(vpa) = LOWER(:vpa)")
    suspend fun remove(vpa: String)
}

