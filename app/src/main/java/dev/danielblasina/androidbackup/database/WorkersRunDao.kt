package dev.danielblasina.androidbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant

@Dao
interface WorkersRunDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun add(fileChanges: WorkersRun)

    @Query("SELECT COUNT(*) FROM WorkersRun")
    fun count(): Int

    @Query("SELECT * FROM WorkersRun WHERE startedAt>=:from ORDER BY startedAt ASC LIMIT 1000")
    fun list(from: Instant): List<WorkersRun>
}
