package com.securenode.sdk.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PendingTelemetryEntity): Long

    @Query("SELECT * FROM pending_telemetry ORDER BY id ASC LIMIT :limit")
    suspend fun list(limit: Int): List<PendingTelemetryEntity>

    @Query("DELETE FROM pending_telemetry WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_telemetry WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}


