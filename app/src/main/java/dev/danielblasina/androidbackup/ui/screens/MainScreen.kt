package dev.danielblasina.androidbackup.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.database.WorkersState
import dev.danielblasina.androidbackup.workers.ChecksumCheckWorker
import dev.danielblasina.androidbackup.workers.FileChangeScheduler
import dev.danielblasina.androidbackup.workers.FileChangeWorker
import dev.danielblasina.androidbackup.workers.FileStateReconcileWorker
import dev.danielblasina.androidbackup.workers.FileUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.logging.Logger

private fun statusColor(state: WorkInfo.State?): Color = when (state) {
    WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50)
    WorkInfo.State.FAILED -> Color(0xFFE53935)
    WorkInfo.State.RUNNING -> Color(0xFF2196F3)
    WorkInfo.State.ENQUEUED -> Color(0xFFFF9800)
    else -> Color.Gray
}

private fun statusIcon(state: WorkInfo.State?): ImageVector = when (state) {
    WorkInfo.State.SUCCEEDED -> Icons.Default.CheckCircle
    WorkInfo.State.FAILED -> Icons.Default.Clear
    WorkInfo.State.RUNNING -> Icons.Default.PlayArrow
    WorkInfo.State.ENQUEUED -> Icons.Default.DateRange
    WorkInfo.State.CANCELLED -> Icons.Default.Clear
    WorkInfo.State.BLOCKED -> Icons.Default.Clear
    else -> Icons.Default.CheckCircle
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
    var chartSeries by remember { mutableStateOf<Map<Pair<String, WorkersState>, List<Instant>>>(emptyMap()) }
    var daysRange by remember { mutableIntStateOf(7) }
    var schedulerState by remember { mutableStateOf<WorkInfo.State?>(null) }
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
        schedulerState = schedulerInfos.firstOrNull()?.state

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
            val nowForFetch = Instant.now()
            val fetchStart = nowForFetch.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().minus(daysRange.toLong(), ChronoUnit.DAYS)
            val runs = db.workersRunsDao().list(fetchStart)
            val series = mutableMapOf<Pair<String, WorkersState>, MutableList<Instant>>()
            for (run in runs) {
                val key = run.workerClass to run.state
                series.getOrPut(key) { mutableListOf() }.add(run.startedAt)
            }
            chartSeries = series
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
                schedulerState = schedulerState,
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

            RunsChartCard(
                seriesMap = chartSeries,
                daysRange = daysRange,
                onDaysRangeChange = { daysRange = it },
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
        Icon(
            imageVector = statusIcon(state),
            contentDescription = statusLabel(state),
            tint = statusColor(state),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WorkersCard(
    schedulerState: WorkInfo.State?,
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
                    enabled = schedulerState != WorkInfo.State.ENQUEUED && schedulerState != WorkInfo.State.RUNNING,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start Schedule")
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = statusIcon(schedulerState),
                    contentDescription = statusLabel(schedulerState),
                    tint = statusColor(schedulerState),
                    modifier = Modifier.size(20.dp),
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

@Composable
private fun RunsChartCard(
    seriesMap: Map<Pair<String, WorkersState>, List<Instant>>,
    daysRange: Int = 7,
    onDaysRangeChange: (Int) -> Unit = {},
) {
    var filterState by remember { mutableStateOf<WorkersState?>(null) }
    var filterWorkerClass by remember { mutableStateOf<String?>(null) }

    val chartColors = listOf(
        Color(0xFFE53935),
        Color(0xFF43A047),
        Color(0xFF1E88E5),
        Color(0xFF8E24AA),
    )
    val workerClasses = seriesMap.keys.map { it.first }.distinct().sorted()
    val wcColors = workerClasses.mapIndexed { i, wc -> wc to chartColors[i % chartColors.size] }.toMap()
    val allFilteredSeries = seriesMap.filterKeys { (wc, state) ->
        (filterState == null || state == filterState) &&
            (filterWorkerClass == null || wc == filterWorkerClass)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Worker Runs (Last $daysRange Days)", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = daysRange == 1, onClick = { onDaysRangeChange(1) }, label = { Text("1d") })
                FilterChip(selected = daysRange == 3, onClick = { onDaysRangeChange(3) }, label = { Text("3d") })
                FilterChip(selected = daysRange == 7, onClick = { onDaysRangeChange(7) }, label = { Text("7d") })
                FilterChip(selected = daysRange == 30, onClick = { onDaysRangeChange(30) }, label = { Text("30d") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterState == null, onClick = { filterState = null }, label = { Text("All") })
                FilterChip(selected = filterState == WorkersState.SUCCEEDED, onClick = { filterState = WorkersState.SUCCEEDED }, label = { Text("Success") })
                FilterChip(selected = filterState == WorkersState.FAILED, onClick = { filterState = WorkersState.FAILED }, label = { Text("Failed") })
            }
            if (allFilteredSeries.isEmpty()) {
                Text(
                    "No run data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                RunsChart(
                    seriesMap = allFilteredSeries,
                    allWorkerClasses = workerClasses,
                    wcColors = wcColors,
                    selectedWorkerClass = filterWorkerClass,
                    onWorkerClassClick = { wc ->
                        filterWorkerClass = if (filterWorkerClass == wc) null else wc
                    },
                    daysRange = daysRange,
                )
            }
        }
    }
}

@Composable
private fun RunsChart(
    seriesMap: Map<Pair<String, WorkersState>, List<Instant>>,
    allWorkerClasses: List<String> = emptyList(),
    wcColors: Map<String, Color> = emptyMap(),
    selectedWorkerClass: String? = null,
    onWorkerClassClick: (String) -> Unit = {},
    daysRange: Int = 7,
) {
    val todayMidnight = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
    val startDate = todayMidnight.minus(daysRange.toLong(), ChronoUnit.DAYS)
    val endDate = todayMidnight.plus(1, ChronoUnit.DAYS)
    val rangeMs = endDate.toEpochMilli() - startDate.toEpochMilli()

    Canvas(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        val left = 8.dp.toPx()
        val bottom = 32.dp.toPx()
        val top = 8.dp.toPx()
        val right = 8.dp.toPx()
        val pw = size.width - left - right
        val ph = size.height - top - bottom

        val byWorkerClass = mutableMapOf<String, MutableList<Map.Entry<Pair<String, WorkersState>, List<Instant>>>>()
        for (entry in seriesMap) {
            byWorkerClass.getOrPut(entry.key.first) { mutableListOf() }.add(entry)
        }
        val wcEntries = byWorkerClass.entries.toList()
        val n = wcEntries.size.coerceAtLeast(1)

        for ((idx, wcEntry) in wcEntries.withIndex()) {
            val (workerClass, subEntries) = wcEntry
            val color = wcColors[workerClass] ?: Color.Gray
            val baseY = top + ph * (idx + 1).toFloat() / (n + 1)
            val dotRadius = 4.dp.toPx()

            data class DotInfo(val x: Float, val state: WorkersState)
            val dots = mutableListOf<DotInfo>()
            for (subEntry in subEntries) {
                val state = subEntry.key.second
                val instants = subEntry.value
                for (instant in instants) {
                    val fraction = (instant.toEpochMilli() - startDate.toEpochMilli()).toFloat() / rangeMs.toFloat()
                    val x = left + fraction.coerceIn(0f, 1f) * pw
                    dots.add(DotInfo(x, state))
                }
            }

            val overlapThreshold = 3 * dotRadius
            val groups = mutableListOf<MutableList<DotInfo>>()
            for (dot in dots.sortedBy { it.x }) {
                var added = false
                for (group in groups) {
                    if (kotlin.math.abs(group.first().x - dot.x) <= overlapThreshold) {
                        group.add(dot)
                        added = true
                        break
                    }
                }
                if (!added) {
                    groups.add(mutableListOf(dot))
                }
            }

            val horizontalSpacing = 3.dp.toPx()
            for (group in groups) {
                for ((i, dot) in group.withIndex()) {
                    val xOffset = (i - (group.size - 1) / 2f) * horizontalSpacing
                    val x = dot.x + xOffset
                    if (dot.state == WorkersState.SUCCEEDED) {
                        drawCircle(color, radius = dotRadius, center = Offset(x, baseY))
                    } else {
                        drawCircle(color, radius = dotRadius, center = Offset(x, baseY), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }

        drawLine(Color.Gray, Offset(left, top), Offset(left, top + ph))
        drawLine(Color.Gray, Offset(left, top + ph), Offset(left + pw, top + ph))

        val labelSize = 11.dp.toPx()
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = labelSize
        }

        val dateFormat = SimpleDateFormat("d/M", Locale.getDefault())
        val labelStep = if (daysRange <= 7) 1 else 5
        for (dayOffset in 0..daysRange + 1) {
            val labelTime = startDate.plus(dayOffset.toLong(), ChronoUnit.DAYS)
            val fraction = (labelTime.toEpochMilli() - startDate.toEpochMilli()).toFloat() / rangeMs.toFloat()
            val x = left + fraction.coerceIn(0f, 1f) * pw
            drawLine(Color.Gray.copy(alpha = 0.15f), Offset(x, top), Offset(x, top + ph))
            if (dayOffset % labelStep == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    dateFormat.format(Date(labelTime.toEpochMilli())),
                    x - 8f,
                    top + ph + 20.dp.toPx(),
                    textPaint,
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    allWorkerClasses.forEach { wc ->
        val isSelected = selectedWorkerClass == wc
        val alpha = if (isSelected || selectedWorkerClass == null) 1f else 0.4f
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onWorkerClassClick(wc) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = wcColors[wc] ?: Color.Gray
            val name = wc.substringAfterLast('.').removeSuffix("Worker")
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color, radius = size.minDimension / 2)
            }
            Spacer(Modifier.width(4.dp))
            Text(name, modifier = Modifier.alpha(alpha), style = MaterialTheme.typography.labelSmall)
        }
    }
}
