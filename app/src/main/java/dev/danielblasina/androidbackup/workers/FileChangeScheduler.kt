package dev.danielblasina.androidbackup.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.util.logging.Logger

class FileChangeScheduler(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun doWork(): Result {
        logger.info { "Started ${this.javaClass.name}" }
        FileChangeWorker.start(applicationContext)
        FileUploadWorker.start(applicationContext)
        FileStateReconcileWorker.start(applicationContext)
        ChecksumCheckWorker.start(applicationContext)
        return Result.success()
    }

    companion object {
        fun start(applicationContext: Context) {
            Logger.getLogger(Companion::class.java.name).info("ReqStart FileChangeScheduler")
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val work = PeriodicWorkRequestBuilder<FileChangeScheduler>(Duration.ofMillis(MIN_PERIODIC_INTERVAL_MILLIS))
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    this::class.java.name,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    work,
                )
        }
    }
}
