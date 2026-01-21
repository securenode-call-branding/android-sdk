package com.securenode.sdk.sample.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BrandingDao {
    @Query("SELECT * FROM branding WHERE phoneNumberE164 = :e164 LIMIT 1")
    suspend fun getBranding(e164: String): BrandingEntity?

    @Query("SELECT * FROM branding ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentBranding(limit: Int): List<BrandingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranding(branding: BrandingEntity)

    @Query("DELETE FROM branding WHERE updatedAt < :beforeTimestamp")
    suspend fun deleteOldBranding(beforeTimestamp: Long)
}

