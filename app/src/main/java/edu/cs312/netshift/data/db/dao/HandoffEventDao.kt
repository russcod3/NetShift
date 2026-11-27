package edu.cs312.netshift.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import edu.cs312.netshift.data.db.entity.HandoffEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HandoffEventDao {
    @Insert
    suspend fun insert(event: HandoffEventEntity)

    @Update
    suspend fun update(event: HandoffEventEntity)

    @Query("SELECT * FROM handoff_events ORDER BY detectedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HandoffEventEntity>>

    @Query("SELECT * FROM handoff_events WHERE id = :id")
    suspend fun getById(id: String): HandoffEventEntity?

    @Query("DELETE FROM handoff_events")
    suspend fun clear()
}
