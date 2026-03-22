package dev.danielblasina.androidbackup.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.util.logging.Logger

class ChecksumCheckScheduler(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun doWork(): Result {
        logger.info { "Started ${this.javaClass.name}" }
        FileUploadWorker.start(applicationContext)
        return Result.success()
    }

    companion object {
        fun start(applicationContext: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()
            val work = PeriodicWorkRequestBuilder<ChecksumCheckScheduler>(Duration.ofMillis(MIN_PERIODIC_INTERVAL_MILLIS))
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    this::class.java.name,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work,
                )
        }
    }
}
