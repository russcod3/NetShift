package edu.cs312.netshift.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import edu.cs312.netshift.data.db.entity.TestResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestResultDao {
    @Insert
    suspend fun insert(result: TestResultEntity)

    @Update
    suspend fun update(result: TestResultEntity)

    @Query("SELECT * FROM test_results ORDER BY startedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<TestResultEntity>>

    @Query("SELECT * FROM test_results WHERE id = :id")
    fun observeById(id: String): Flow<TestResultEntity?>

    @Query("SELECT * FROM test_results WHERE id = :id")
    suspend fun getById(id: String): TestResultEntity?

    /**
     * Newest-first COMPLETE tests on one profile. The repository applies the stricter
     * eligibility rules (CompareModel.isEligible); keeping that logic in Kotlin keeps it unit-testable.
     */
    @Query(
        "SELECT * FROM test_results WHERE status = 'COMPLETE' AND profileId = :profileId " +
            "AND measuredTransport IN ('WIFI', 'CELLULAR') ORDER BY startedAtEpochMs DESC LIMIT :limit",
    )
    fun observeCompleteForCompare(profileId: String, limit: Int = 200): Flow<List<TestResultEntity>>

    /** Renames every test of one location; name and normalized key change in one statement. */
    @Query("UPDATE test_results SET locationName = :newName, locationKey = :newKey WHERE locationKey = :oldKey")
    suspend fun renameLocation(oldKey: String, newName: String, newKey: String): Int

    /** Marks RUNNING rows from an earlier process as ABORTED. Never resumes traffic. */
    @Query(
        "UPDATE test_results SET status = 'ABORTED', failureReason = 'PROCESS_ENDED', finishedAtEpochMs = :now " +
            "WHERE status = 'RUNNING' AND startedAtEpochMs < :startedBefore",
    )
    suspend fun abortRunning(now: Long, startedBefore: Long): Int

    @Query("DELETE FROM test_results")
    suspend fun clear()
}
