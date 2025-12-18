package com.securenode.sdk.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branding")
data class BrandingEntity(
    @PrimaryKey
    val phoneNumberE164: String,
    val brandName: String,
    val logoUrl: String?,
    val callReason: String?,
    val updatedAt: Long
)

