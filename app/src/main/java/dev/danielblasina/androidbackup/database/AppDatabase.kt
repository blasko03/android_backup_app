package dev.danielblasina.androidbackup.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val DATABASE_NAME = "backups"

@Database(entities = [FileChangeQueue::class, FileState::class, Setting::class, WorkersRun::class], version = 2)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileChangeQueueDao(): FileChangeQueueDao

    abstract fun fileStateDao(): FileStateDao

    abstract fun settingDao(): SettingDao

    abstract fun workersRunsDao(): WorkersRunDao

    companion object {
        @Volatile
        private var dbInstance: AppDatabase? = null

        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `WorkersRun` (
                    `id` BLOB NOT NULL,
                    `workerClass` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }

        fun getDatabase(context: Context): AppDatabase = dbInstance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2)
                .build()
            dbInstance = instance
            instance
        }
    }
}
