package com.securenode.sdk.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BrandingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BrandingDatabase : RoomDatabase() {
    abstract fun brandingDao(): BrandingDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

