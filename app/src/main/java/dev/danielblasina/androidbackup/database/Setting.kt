package dev.danielblasina.androidbackup.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Setting(
    @PrimaryKey val type: SettingType,
    val value: String,
)
