package io.securenode.branding.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.securenode.branding.data.db.entity.EventEntity

@Dao
interface EventDao {
    @Query("SELECT COUNT(*) FROM events WHERE uploaded = 0")
    suspend fun countPending(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventEntity): Long

    @Query("SELECT * FROM events WHERE uploaded = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<EventEntity>

    @Update
    suspend fun update(entity: EventEntity)

    @Query("UPDATE events SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("DELETE FROM events WHERE uploaded = 1 AND createdAtEpochMs < :cutoffEpochMs")
    suspend fun deleteUploadedOlderThan(cutoffEpochMs: Long): Int
}
