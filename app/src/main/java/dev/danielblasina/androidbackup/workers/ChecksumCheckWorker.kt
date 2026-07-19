package dev.danielblasina.androidbackup.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.danielblasina.androidbackup.database.FileActionType
import dev.danielblasina.androidbackup.database.FileChangeQueue
import dev.danielblasina.androidbackup.files.calculateHash
import java.io.File
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

class ChecksumCheckWorker(appContext: Context, workerParams: WorkerParameters) : WorkerWithRunLog(appContext, workerParams) {
    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun doWorkAction(): Result {
        val fileStateDao = db.fileStateDao()
        var iteration = 0
        while (iteration < MAX_ITERATIONS) {
            iteration += 1
            val fileState = fileStateDao.getNextHashCheck(from = Instant.now().minus(checkFrequency))
            if (fileState == null) {
                logger.info { "Completed hash check for all files" }
                return Result.success()
            }
            val checkInstant = Instant.now()
            try {
                logger.info { "Processing hashCheck for ${fileState.filePath}" }
                if (!fileState.hash.contentEquals(File(fileState.filePath).calculateHash())) {
                    logger.fine { "Detected hash inconsistency for ${fileState.filePath} adding to FileChangeQueue" }
                    val change = FileChangeQueue(
                        filePath = fileState.filePath,
                        enqueuedAt = Instant.now(),
                        actionType = FileActionType.CHANGE,
                    )
                    db.fileChangeQueueDao().add(change)
                }
            } catch (e: FileNotFoundException) {
                logger.warning { "Wasn't able to compute hash for file ${fileState.filePath} with error: ${e.message}" }
            }
            fileStateDao.setHashCheck(fileState.filePath, checkInstant)
        }
        return Result.success()
    }

    companion object {
        val checkFrequency: Duration = Duration.ofDays(7)
        fun start(applicationContext: Context) {
            val uploadWorkRequest = OneTimeWorkRequestBuilder<ChecksumCheckWorker>()
                .build()
            WorkManager
                .getInstance(applicationContext)
                .enqueueUniqueWork(
                    this::class.java.name,
                    ExistingWorkPolicy.KEEP,
                    uploadWorkRequest,
                )
        }
    }
}
