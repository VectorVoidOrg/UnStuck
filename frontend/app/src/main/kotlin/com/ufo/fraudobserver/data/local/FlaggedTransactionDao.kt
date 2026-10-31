package com.ufo.fraudobserver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlaggedTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: FlaggedTransactionEntity): Long

    @Query("SELECT * FROM flagged_transactions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<FlaggedTransactionEntity>>

    @Query("SELECT * FROM flagged_transactions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<FlaggedTransactionEntity>

    @Query("SELECT COUNT(*) FROM flagged_transactions")
    fun getCountFlow(): Flow<Int>

    @Query("DELETE FROM flagged_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM flagged_transactions")
    suspend fun clearAll()
}

