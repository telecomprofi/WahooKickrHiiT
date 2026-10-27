package com.telecomprofi.kickrhiit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackEntity::class, CatalogueEntity::class, RideEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun catalogueDao(): CatalogueDao
    abstract fun rideDao(): RideDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 0 = measured, which is what every existing row is.
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `bpmSource` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kickr_hiit.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
