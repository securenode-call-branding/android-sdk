package io.securenode.branding.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.securenode.branding.data.db.entity.BrandingEntity

@Dao
interface BrandingDao {
    @Query("SELECT COUNT(*) FROM branding")
    suspend fun count(): Int

    @Query("SELECT * FROM branding WHERE phoneE164 = :e164 LIMIT 1")
    suspend fun get(e164: String): BrandingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BrandingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BrandingEntity>)

    @Query("DELETE FROM branding WHERE updatedAtEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int
}
