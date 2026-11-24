package edu.cs312.netshift.data

import edu.cs312.netshift.data.db.dao.TestResultDao
import edu.cs312.netshift.data.db.entity.TestResultEntity
import java.util.Locale

class TestRepository(private val dao: TestResultDao) {
    fun observeRecent() = dao.observeRecent()
    fun observeById(id: String) = dao.observeById(id)

    suspend fun insert(result: TestResultEntity) {
        val name = result.locationName.trim().take(60).ifBlank { "Unlabelled" }
        dao.insert(result.copy(locationName = name, locationKey = normalizeLocation(name)))
    }

    suspend fun update(result: TestResultEntity) {
        val name = result.locationName.trim().take(60).ifBlank { "Unlabelled" }
        dao.update(result.copy(locationName = name, locationKey = normalizeLocation(name)))
    }

    private fun normalizeLocation(name: String) =
        name.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
