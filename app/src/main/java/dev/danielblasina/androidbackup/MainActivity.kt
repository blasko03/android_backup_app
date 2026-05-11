package dev.danielblasina.androidbackup

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.database.Setting
import dev.danielblasina.androidbackup.database.SettingType
import dev.danielblasina.androidbackup.ui.theme.MyApplicationTheme
import dev.danielblasina.androidbackup.workers.ChecksumCheckWorker
import dev.danielblasina.androidbackup.workers.FileChangeScheduler
import dev.danielblasina.androidbackup.workers.FileChangeWorker
import dev.danielblasina.androidbackup.workers.FileStateReconcileWorker
import dev.danielblasina.androidbackup.workers.FileUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.logging.Logger

class MainActivity : ComponentActivity() {
    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Environment.isExternalStorageManager()) {
            // Permission granted
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }

        enableEdgeToEdge()
        setContent {
            MainApplication()
        }
    }

    fun updateSetting(type: SettingType, value: String) {
        val db = AppDatabase.getDatabase(applicationContext)
        db.settingDao().add(Setting(type, value))
    }

    @Composable
    private fun MainApplication() {

        val scope = rememberCoroutineScope()
        var queueCount by remember { mutableIntStateOf(0) }
        var filesCount by remember { mutableIntStateOf(0) }
        var countNextServerCheck by remember { mutableIntStateOf(0) }
        var countNextHashCheck by remember { mutableIntStateOf(0) }
        var uuid by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var server by remember { mutableStateOf("") }

        MyApplicationTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Column {
                    Button(onClick = {
                        logger.info { "FileChangeDetector was requested to start" }
                        FileChangeWorker.start(applicationContext)
                    }, modifier = Modifier.padding(innerPadding)) {
                        Text("Start FileChangeDetector")
                    }
                    Button(onClick = {
                        logger.info { "FileUploadWorker was requested to start" }
                        FileUploadWorker.start(applicationContext)
                    }) {
                        Text("Start FileUploadWorker")
                    }
                    Button(onClick = {
                        logger.info { "ChecksumChecker was requested to start" }
                        ChecksumCheckWorker.start(applicationContext)
                    }) {
                        Text("Start ChecksumChecker")
                    }
                    Button(onClick = {
                        logger.info { "FileStateReconcile was requested to start" }
                        FileStateReconcileWorker.start(applicationContext)
                    }) {
                        Text("Start FileStateReconcile")
                    }
                    Button(onClick = {
                        logger.info { "Start schedule" }

                        FileChangeScheduler.start(applicationContext)
                    }) {
                        Text("Start schedule")
                    }
                    Button(onClick = {
                        WorkManager.getInstance(applicationContext)
                            .getWorkInfos(
                                WorkQuery.fromStates(
                                    WorkInfo.State.ENQUEUED,
                                    WorkInfo.State.CANCELLED,
                                    WorkInfo.State.SUCCEEDED,
                                    WorkInfo.State.FAILED,
                                    WorkInfo.State.BLOCKED,
                                    WorkInfo.State.RUNNING,
                                ),
                            )
                            .get()
                            .forEach { wi -> logger.info(wi.id.toString() + " : " + wi.state + " : " + wi.nextScheduleTimeMillis + " : " + wi.tags + " : " + wi.toString()) }

                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(applicationContext)
                                queueCount = db.fileChangeQueueDao().count()
                                filesCount = db.fileStateDao().count()
                                countNextServerCheck = db.fileStateDao().countNextServerCheck(Instant.now().minus(FileStateReconcileWorker.checkFrequency))
                                countNextHashCheck = db.fileStateDao().countNextHashCheck(Instant.now().minus(ChecksumCheckWorker.checkFrequency))
                                uuid = db.settingDao().get(SettingType.UUID).toString();
                                password = db.settingDao().get(SettingType.PASSWORD).toString();
                                server = db.settingDao().get(SettingType.SERVER_ADDRESS).toString();

                            }
                        }
                    }) {
                        Text("refresh")
                    }

                    Text("Total in queue: ${queueCount}")
                    Text("Total files: ${filesCount}")
                    Text("Total reconciliation check: ${countNextServerCheck}")
                    Text("Total hash check: ${countNextHashCheck}")

                    OutlinedTextField(
                        value = uuid,
                        onValueChange = { value ->
                            uuid = value
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    updateSetting(SettingType.UUID, value)
                                }} },
                        label = { Text("uuid") }
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { value ->
                            password = value
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    updateSetting(SettingType.PASSWORD, value)
                                }} },
                        label = { Text("password") }
                    )
                    OutlinedTextField(
                        value = server,
                        onValueChange = { value ->
                            server = value
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    updateSetting(SettingType.SERVER_ADDRESS, value)
                                }} },
                        label = { Text("server address") }
                    )
                }
            }
        }
    }
}
