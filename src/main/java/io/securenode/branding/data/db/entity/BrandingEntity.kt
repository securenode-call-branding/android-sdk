package io.securenode.branding.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branding")
data class BrandingEntity(
    @PrimaryKey val phoneE164: String,
    val brandName: String?,
    val logoUrl: String?,
    val callReason: String?,
    val updatedAtEpochMs: Long
)
