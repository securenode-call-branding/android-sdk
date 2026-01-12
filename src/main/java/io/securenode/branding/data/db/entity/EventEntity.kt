package io.securenode.branding.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneE164: String,
    val outcome: String,
    val surface: String?,
    val displayedAtEpochMs: Long? = null,
    val idempotencyKey: String? = null,
    val metaJson: String? = null,
    val createdAtEpochMs: Long,
    val uploaded: Boolean = false,
    val attempts: Int = 0,
    val lastError: String? = null
)
