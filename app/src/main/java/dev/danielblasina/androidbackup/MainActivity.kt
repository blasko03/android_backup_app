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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.ui.theme.MyApplicationTheme
import dev.danielblasina.androidbackup.workers.ChecksumCheckScheduler
import dev.danielblasina.androidbackup.workers.ChecksumCheckWorker
import dev.danielblasina.androidbackup.workers.FileChangeScheduler
import dev.danielblasina.androidbackup.workers.FileChangeWorker
import dev.danielblasina.androidbackup.workers.FileStateReconcileScheduler
import dev.danielblasina.androidbackup.workers.FileStateReconcileWorker
import dev.danielblasina.androidbackup.workers.FileUploadScheduler
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

    @Composable
    private fun MainApplication() {
        val scope = rememberCoroutineScope()
        val queueCount = remember { mutableIntStateOf(0) }
        val filesCount = remember { mutableIntStateOf(0) }
        val countNextServerCheck = remember { mutableIntStateOf(0) }
        val countNextHashCheck = remember { mutableIntStateOf(0) }

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
                    }, modifier = Modifier.padding(innerPadding)) {
                        Text("Start FileUploadWorker")
                    }
                    Button(onClick = {
                        logger.info { "ChecksumChecker was requested to start" }
                        ChecksumCheckWorker.start(applicationContext)
                    }, modifier = Modifier.padding(innerPadding)) {
                        Text("Start ChecksumChecker")
                    }
                    Button(onClick = {
                        logger.info { "FileStateReconcile was requested to start" }
                        FileStateReconcileWorker.start(applicationContext)
                    }, modifier = Modifier.padding(innerPadding)) {
                        Text("Start FileStateReconcile")
                    }
                    Button(onClick = {
                        logger.info { "Start schedule" }

                        FileChangeScheduler.start(applicationContext)
                        FileUploadScheduler.start(applicationContext)
                        FileStateReconcileScheduler.start(applicationContext)
                        ChecksumCheckScheduler.start(applicationContext)
                    }, modifier = Modifier.padding(innerPadding)) {
                        Text("Start schedule")
                    }
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(applicationContext)
                                queueCount.intValue = db.fileChangeQueueDao().count()
                                filesCount.intValue = db.fileStateDao().count()
                                countNextServerCheck.intValue = db.fileStateDao().countNextServerCheck(Instant.now().minus(FileStateReconcileWorker.checkFrequency))
                                countNextHashCheck.intValue = db.fileStateDao().countNextHashCheck(Instant.now().minus(ChecksumCheckWorker.checkFrequency))
                            }
                        }
                    }, modifier = Modifier.padding(innerPadding)) {
                        Text("refresh")
                    }

                    Text("Total in queue: ${queueCount.intValue}")
                    Text("Total files: ${filesCount.intValue}")
                    Text("Total reconciliation check: ${countNextServerCheck.intValue}")
                    Text("Total hash check: ${countNextHashCheck.intValue}")
                }
            }
        }
    }
}
