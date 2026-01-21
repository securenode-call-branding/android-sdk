package com.securenode.sdk.sample.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_telemetry")
data class PendingTelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: String,
    val message: String,
    val metaJson: String?,
    val occurredAt: String,
    val createdAt: Long
)


