package com.securenode.sdk.sample.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * Idempotency key used by the backend to dedupe retries.
     * Should be stable for the logical event.
     */
    val eventKey: String,
    val phoneNumberE164: String,
    val outcome: String,
    val surface: String?,
    val displayedAt: String,
    /** Optional JSON payload forwarded to the server as `meta`. */
    val metaJson: String? = null,
    val createdAt: Long,
    /**
     * queued | sent | dropped
     */
    val status: String = "queued",
    val attempts: Int = 0,
    val lastError: String? = null,
    val dropReason: String? = null,
    val updatedAt: Long = createdAt
)


