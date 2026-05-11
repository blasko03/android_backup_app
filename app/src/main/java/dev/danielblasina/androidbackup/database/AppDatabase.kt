package dev.danielblasina.androidbackup.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val DATABASE_NAME = "backups"

@Database(entities = [FileChangeQueue::class, FileState::class, Setting::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileChangeQueueDao(): FileChangeQueueDao

    abstract fun fileStateDao(): FileStateDao

    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var dbInstance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = dbInstance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).build()
            dbInstance = instance
            instance
        }
    }
}
