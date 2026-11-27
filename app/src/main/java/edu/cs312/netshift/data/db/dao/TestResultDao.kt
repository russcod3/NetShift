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

    @Query("DELETE FROM test_results")
    suspend fun clear()
}
