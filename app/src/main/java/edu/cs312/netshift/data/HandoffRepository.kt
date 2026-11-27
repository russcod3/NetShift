package edu.cs312.netshift.data

import edu.cs312.netshift.data.db.dao.HandoffEventDao
import edu.cs312.netshift.data.db.entity.HandoffEventEntity

class HandoffRepository(private val dao: HandoffEventDao) {
    fun observeRecent() = dao.observeRecent()
    suspend fun getById(id: String) = dao.getById(id)
    suspend fun insert(event: HandoffEventEntity) = dao.insert(event)
    suspend fun update(event: HandoffEventEntity) = dao.update(event)

    /** Called when a tracking session ends; pending transitions keep an unknown recovery time. */
    suspend fun closePendingForSession(sessionId: String, now: Long = System.currentTimeMillis()): Int =
        dao.closePendingForSession(sessionId, now)

    /** Startup recovery for sessions that ended with the process. */
    suspend fun recoverInterrupted(startedBefore: Long, now: Long = System.currentTimeMillis()): Int =
        dao.closeStalePending(now, startedBefore)

    suspend fun clear() = dao.clear()
}
