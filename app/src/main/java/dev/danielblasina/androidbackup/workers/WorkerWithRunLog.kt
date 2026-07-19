package dev.danielblasina.androidbackup.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.database.WorkersRun
import dev.danielblasina.androidbackup.database.WorkersState
import java.time.Instant

abstract class WorkerWithRunLog(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    val db = AppDatabase.getDatabase(applicationContext)

    override fun doWork(): Result {
        val started = Instant.now()
        var state = WorkersState.FAILED
        try {
            val result = doWorkAction()
            if (result == Result.success()) {
                state = WorkersState.SUCCEEDED
            }
            return result
        } finally {
            val wr = WorkersRun(
                id = this.id,
                workerClass = this::class.java.name,
                startedAt = started,
                completedAt = Instant.now(),
                state = state,
            )
            db.workersRunsDao().add(wr)
        }
    }

    abstract fun doWorkAction(): Result
}
