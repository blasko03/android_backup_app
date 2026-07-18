package dev.danielblasina.androidbackup.workers

import android.content.Context
import android.os.Environment
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.files.FileChanges
import java.util.logging.Logger

class FileChangeWorker(appContext: Context, workerParams: WorkerParameters) : WorkerWithRunLog(appContext, workerParams) {
    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun doWorkAction(): Result {
        val fileChangeQueueDao = db.fileChangeQueueDao()
        val fileStateDao = db.fileStateDao()
        logger.info(
            "enqueue UniqueWork " + fileChangeQueueDao.count() + " : " + fileStateDao.listAll().size,
        )
        val dir = FileChanges(Environment.getExternalStoragePublicDirectory("/"))
        db.fileChangeQueueDao().add(dir.listChanges(recursive = true, fileStateDao.listAll()))
        logger.info("work scheduled and inserted " + fileChangeQueueDao.count())
        return Result.success()
    }

    companion object {
        fun start(applicationContext: Context) {
            val work = OneTimeWorkRequestBuilder<FileChangeWorker>()
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
