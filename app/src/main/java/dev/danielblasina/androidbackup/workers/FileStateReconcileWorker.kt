package dev.danielblasina.androidbackup.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.database.FileActionType
import dev.danielblasina.androidbackup.database.FileChangeQueue
import dev.danielblasina.androidbackup.files.FileUploadService
import dev.danielblasina.androidbackup.files.UploadedFile
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

class FileStateReconcileWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    val db = AppDatabase.getDatabase(applicationContext)
    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun doWork(): Result {
        logger.info { "FileStateReconcile started" }
        val fileStateDao = db.fileStateDao()
        var iteration = 0
        while (iteration < MAX_ITERATIONS) {
            iteration++
            val fileStates = fileStateDao.getNextServerCheck(
                from = Instant.now().minus(checkFrequency),
                limit = 1000,
            )
            if (fileStates.isEmpty()) {
                logger.info { "Completed reconciliation check for all files" }
                return Result.success()
            }

            val filesToCheck = fileStates.map { f -> UploadedFile(name = f.filePath, hash = f.hash) }
            val filesToCheckResult = FileUploadService().filesPresent(filesToCheck.toList()).getOrThrow()
            filesToCheckResult.filter { f -> !f.present }
                .forEach { notFoundFile ->
                    logger.fine { "Detected file not found on server for ${notFoundFile.name} adding to FileChangeQueue" }
                    val change = FileChangeQueue(
                        filePath = notFoundFile.name,
                        enqueuedAt = Instant.now(),
                        actionType = FileActionType.CHANGE,
                    )
                    db.fileChangeQueueDao().add(change)
                }
            fileStateDao.setServerCheck(
                filePath = fileStates.map { fs -> fs.filePath }.toList(),
                instant = Instant.now(),
            )
        }

        return Result.success()
    }
    companion object {
        val checkFrequency: Duration = Duration.ofDays(1)
        fun start(applicationContext: Context) {
            val work = OneTimeWorkRequestBuilder<FileStateReconcileWorker>()
                .build()
            WorkManager
                .getInstance(applicationContext)
                .enqueueUniqueWork(
                    this::class.java.name,
                    ExistingWorkPolicy.KEEP,
                    work,
                )
        }
    }
}
