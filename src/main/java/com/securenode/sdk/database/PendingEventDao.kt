package com.securenode.sdk.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PendingEventEntity): Long

    @Query("SELECT * FROM pending_events WHERE status = 'queued' ORDER BY id ASC LIMIT :limit")
    suspend fun listQueued(limit: Int): List<PendingEventEntity>

    @Query("DELETE FROM pending_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_events WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query(
        "UPDATE pending_events SET status = 'sent', updatedAt = :now WHERE id IN (:ids)"
    )
    suspend fun markSent(ids: List<Long>, now: Long)

    @Query(
        "UPDATE pending_events SET attempts = attempts + 1, lastError = :error, updatedAt = :now WHERE id = :id"
    )
    suspend fun recordFailure(id: Long, error: String, now: Long)

    @Query(
        "UPDATE pending_events SET status = 'dropped', dropReason = :reason, updatedAt = :now WHERE id = :id"
    )
    suspend fun markDropped(id: Long, reason: String, now: Long)

    @Query("DELETE FROM pending_events WHERE status IN ('sent','dropped') AND updatedAt < :beforeTimestamp")
    suspend fun deleteCompletedOlderThan(beforeTimestamp: Long)
}


