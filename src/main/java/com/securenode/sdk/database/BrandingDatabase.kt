package com.securenode.sdk.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BrandingEntity::class,
        PendingEventEntity::class,
        PendingTelemetryEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BrandingDatabase : RoomDatabase() {
    abstract fun brandingDao(): BrandingDao
    abstract fun pendingEventDao(): PendingEventDao
    abstract fun pendingTelemetryDao(): PendingTelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: BrandingDatabase? = null

        fun getDatabase(context: Context): BrandingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrandingDatabase::class.java,
                    "securenode_branding_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Existing v1 table: branding
                // New in v2: pending_events, pending_telemetry
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_events (
                      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      phoneNumberE164 TEXT NOT NULL,
                      outcome TEXT NOT NULL,
                      surface TEXT,
                      displayedAt TEXT NOT NULL,
                      createdAt INTEGER NOT NULL,
                      status TEXT NOT NULL DEFAULT 'queued',
                      attempts INTEGER NOT NULL DEFAULT 0,
                      lastError TEXT,
                      dropReason TEXT,
                      updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_events_status_id ON pending_events(status, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_events_createdAt ON pending_events(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_telemetry (
                      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      level TEXT NOT NULL,
                      message TEXT NOT NULL,
                      metaJson TEXT,
                      occurredAt TEXT NOT NULL,
                      createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_telemetry_createdAt ON pending_telemetry(createdAt)")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add idempotency + meta payload for event upload semantics.
                db.execSQL("ALTER TABLE pending_events ADD COLUMN eventKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pending_events ADD COLUMN metaJson TEXT")
                // Backfill any existing rows (should be rare in practice)
                db.execSQL("UPDATE pending_events SET eventKey = 'legacy-' || id WHERE eventKey = ''")
            }
        }
    }
}

