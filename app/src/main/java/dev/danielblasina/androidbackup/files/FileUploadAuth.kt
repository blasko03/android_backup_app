package dev.danielblasina.androidbackup.files

import android.content.Context
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.database.SettingType
import java.net.URI
import java.util.UUID

data class FileUploadAuth(val uuid: UUID, val password: String, val address: URI){
    companion object{
        fun fromDatabase(context: Context): FileUploadAuth {
            val setting = AppDatabase.getDatabase(context).settingDao()
            return FileUploadAuth(UUID.fromString(setting.get(SettingType.UUID)),
                setting.get(SettingType.PASSWORD).toString(),
                URI.create(setting.get(SettingType.SERVER_ADDRESS)))
        }
    }
}
