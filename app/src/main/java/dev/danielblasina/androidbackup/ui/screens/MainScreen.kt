package dev.danielblasina.androidbackup.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.workers.ChecksumCheckWorker
import dev.danielblasina.androidbackup.workers.FileChangeScheduler
import dev.danielblasina.androidbackup.workers.FileChangeWorker
import dev.danielblasina.androidbackup.workers.FileStateReconcileWorker
import dev.danielblasina.androidbackup.workers.FileUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.logging.Logger

private fun statusColor(state: WorkInfo.State?): Color = when (state) {
    WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50)
    WorkInfo.State.FAILED -> Color(0xFFE53935)
    WorkInfo.State.RUNNING -> Color(0xFF2196F3)
    WorkInfo.State.ENQUEUED -> Color(0xFFFF9800)
    else -> Color.Gray
}

private fun statusLabel(state: WorkInfo.State?): String = when (state) {
    WorkInfo.State.SUCCEEDED -> "Succeeded"
    WorkInfo.State.FAILED -> "Failed"
    WorkInfo.State.RUNNING -> "Running"
    WorkInfo.State.ENQUEUED -> "Queued"
    WorkInfo.State.CANCELLED -> "Cancelled"
    WorkInfo.State.BLOCKED -> "Blocked"
    else -> "—"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    context: Context,
) {
    val logger = remember { Logger.getLogger("MainScreen") }
    val scope = rememberCoroutineScope()
    var queueCount by remember { mutableIntStateOf(0) }
    var filesCount by remember { mutableIntStateOf(0) }
    var countNextServerCheck by remember { mutableIntStateOf(0) }
    var countNextHashCheck by remember { mutableIntStateOf(0) }
    var runsCount by remember { mutableIntStateOf(0) }
    var schedulerStatus by remember { mutableStateOf("Not started") }
    var detectorState by remember { mutableStateOf<WorkInfo.State?>(null) }
    var uploaderState by remember { mutableStateOf<WorkInfo.State?>(null) }
    var checksumState by remember { mutableStateOf<WorkInfo.State?>(null) }
    var reconcileState by remember { mutableStateOf<WorkInfo.State?>(null) }

    fun workState(name: String): WorkInfo.State? = WorkManager.getInstance(context)
        .getWorkInfosByTag(name)
        .get()
        .firstOrNull()?.state

    val refreshData: suspend () -> Unit = {
        WorkManager.getInstance(context)
        val schedulerInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(FileChangeScheduler.Companion::class.java.name)
            .get()
        schedulerStatus = if (schedulerInfos.isNullOrEmpty()) {
            "Not scheduled"
        } else {
            schedulerInfos.first().state.name
        }

        detectorState = workState(FileChangeWorker::class.java.name)
        uploaderState = workState(FileUploadWorker::class.java.name)
        checksumState = workState(ChecksumCheckWorker::class.java.name)
        reconcileState = workState(FileStateReconcileWorker::class.java.name)

        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            queueCount = db.fileChangeQueueDao().count()
            filesCount = db.fileStateDao().count()
            countNextServerCheck = db.fileStateDao().countNextServerCheck(Instant.now().minus(FileStateReconcileWorker.checkFrequency))
            countNextHashCheck = db.fileStateDao().countNextHashCheck(Instant.now().minus(ChecksumCheckWorker.checkFrequency))
            runsCount = db.workersRunsDao().count()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(5_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Android Backup") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WorkersCard(
                schedulerStatus = schedulerStatus,
                detectorState = detectorState,
                uploaderState = uploaderState,
                checksumState = checksumState,
                reconcileState = reconcileState,
                onStartDetector = {
                    logger.info { "FileChangeDetector was requested to start" }
                    FileChangeWorker.start(context)
                },
                onStartUploader = {
                    logger.info { "FileUploadWorker was requested to start" }
                    FileUploadWorker.start(context)
                },
                onStartChecksum = {
                    logger.info { "ChecksumChecker was requested to start" }
                    ChecksumCheckWorker.start(context)
                },
                onStartReconcile = {
                    logger.info { "FileStateReconcile was requested to start" }
                    FileStateReconcileWorker.start(context)
                },
                onStartSchedule = {
                    logger.info { "Start schedule" }
                    FileChangeScheduler.start(context)
                },
            )

            StatsCard(
                queueCount = queueCount,
                filesCount = filesCount,
                countNextServerCheck = countNextServerCheck,
                countNextHashCheck = countNextHashCheck,
                runsCount = runsCount,
            )

            FilledTonalButton(
                onClick = { scope.launch { refreshData() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun WorkerButton(
    label: String,
    state: WorkInfo.State?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
        ) {
            Text(label)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "● ${statusLabel(state)}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor(state),
        )
    }
}

@Composable
private fun WorkersCard(
    schedulerStatus: String,
    detectorState: WorkInfo.State?,
    uploaderState: WorkInfo.State?,
    checksumState: WorkInfo.State?,
    reconcileState: WorkInfo.State?,
    onStartDetector: () -> Unit,
    onStartUploader: () -> Unit,
    onStartChecksum: () -> Unit,
    onStartReconcile: () -> Unit,
    onStartSchedule: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workers", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            WorkerButton("Start FileChangeDetector", detectorState, onStartDetector)
            WorkerButton("Start FileUploadWorker", uploaderState, onStartUploader)
            WorkerButton("Start ChecksumChecker", checksumState, onStartChecksum)
            WorkerButton("Start FileStateReconcile", reconcileState, onStartReconcile)

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onStartSchedule,
                    enabled = schedulerStatus != "ENQUEUED" && schedulerStatus != "RUNNING",
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start Schedule")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = schedulerStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    queueCount: Int,
    filesCount: Int,
    countNextServerCheck: Int,
    countNextHashCheck: Int,
    runsCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Statistics", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            StatRow("Queue", queueCount)
            StatRow("Files tracked", filesCount)
            StatRow("Pending reconciliation", countNextServerCheck)
            StatRow("Pending hash check", countNextHashCheck)
            StatRow("Runs", runsCount)
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
