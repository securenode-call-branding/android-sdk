package io.securenode.branding.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.securenode.branding.data.db.dao.BrandingDao
import io.securenode.branding.data.db.dao.EventDao
import io.securenode.branding.data.db.entity.BrandingEntity
import io.securenode.branding.data.db.entity.EventEntity

@Database(entities = [BrandingEntity::class, EventEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun brandingDao(): BrandingDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "securenode_branding.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
