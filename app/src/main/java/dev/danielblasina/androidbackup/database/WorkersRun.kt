package dev.danielblasina.androidbackup.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity
data class WorkersRun(@PrimaryKey val id: UUID,
                      val workerClass: String,
                      val startedAt: Instant,
                      val completedAt: Instant,
                      val state: WorkersState)

enum class WorkersState {SUCCEEDED, FAILED}