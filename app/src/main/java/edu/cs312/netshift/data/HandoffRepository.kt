package edu.cs312.netshift.data

import edu.cs312.netshift.data.db.dao.HandoffEventDao
import edu.cs312.netshift.data.db.entity.HandoffEventEntity

class HandoffRepository(private val dao: HandoffEventDao) {
    fun observeRecent() = dao.observeRecent()
    suspend fun insert(event: HandoffEventEntity) = dao.insert(event)
    suspend fun update(event: HandoffEventEntity) = dao.update(event)
}
