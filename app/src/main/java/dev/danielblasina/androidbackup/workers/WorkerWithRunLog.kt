package dev.danielblasina.androidbackup.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.danielblasina.androidbackup.database.AppDatabase
import dev.danielblasina.androidbackup.database.WorkersRun
import dev.danielblasina.androidbackup.database.WorkersState
import java.time.Instant
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

abstract class WorkerWithRunLog(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    val db = AppDatabase.getDatabase(applicationContext)
    private val runLogger: Logger = Logger.getLogger(this.javaClass.name)

    override fun doWork(): Result {
        val started = Instant.now()
        var state = WorkersState.FAILED
        return try {
            val result = doWorkAction()
            if (result == Result.success()) {
                state = WorkersState.SUCCEEDED
            }
            result
        } catch (e: Exception) {
            runLogger.log(Level.SEVERE, "${this.javaClass.name} failed", e)
            Result.failure()
        } finally {
            persistRun(started, state)
        }
    }

    private fun persistRun(started: Instant, state: WorkersState) {
        try {
            val run = WorkersRun(
                id = UUID.randomUUID(),
                workerClass = this::class.java.name,
                startedAt = started,
                completedAt = Instant.now(),
                state = state,
            )
            db.workersRunsDao().add(run)
        } catch (e: Exception) {
            runLogger.log(Level.WARNING, "Could not persist run of ${this::class.java.name}", e)
        }
    }

    abstract fun doWorkAction(): Result
}
