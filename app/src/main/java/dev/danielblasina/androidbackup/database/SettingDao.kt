package dev.danielblasina.androidbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(setting: List<Setting>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(setting: Setting)

    @Query("SELECT value from Setting where type=:type LIMIT 1")
    fun get(type: SettingType): String?
}
