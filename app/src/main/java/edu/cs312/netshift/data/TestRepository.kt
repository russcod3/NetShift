package edu.cs312.netshift.data

import edu.cs312.netshift.data.db.dao.TestResultDao
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.MeasurementConfig
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TestRepository(private val dao: TestResultDao) {
    fun observeRecent() = dao.observeRecent()
    fun observeById(id: String) = dao.observeById(id)
    suspend fun getById(id: String) = dao.getById(id)

    /** COMPLETE, same-profile tests that pass the automatic-comparison rules, newest first (max 200 scanned). */
    fun observeComparable(): Flow<List<TestResultEntity>> =
        dao.observeCompleteForCompare(MeasurementConfig.PROFILE_ID)
            .map { rows -> rows.filter(CompareModel::isEligible) }

    /** Latest eligible Wi-Fi/mobile pair per location, ready for the paired charts. */
    fun observeComparisons(): Flow<List<LocationComparison>> =
        observeComparable().map(CompareModel::latestPairs)

    /** Oldest-to-newest series (at most [limit] points) for the history line chart of one transport. */
    fun observeHistorySeries(transport: String, limit: Int = 50): Flow<List<TestResultEntity>> =
        observeComparable().map { rows ->
            rows.filter { it.measuredTransport == transport }.take(limit).reversed()
        }

    suspend fun insert(result: TestResultEntity) {
        val name = displayName(result.locationName)
        dao.insert(result.copy(locationName = name, locationKey = normalizeLocation(name)))
    }

    suspend fun update(result: TestResultEntity) {
        val name = displayName(result.locationName)
        dao.update(result.copy(locationName = name, locationKey = normalizeLocation(name)))
    }

    /** Renames all tests that share [oldKey] so comparisons keep matching. Returns the rows changed. */
    suspend fun renameLocation(oldKey: String, newName: String): Int {
        val name = displayName(newName)
        return dao.renameLocation(oldKey, name, normalizeLocation(name))
    }

    /** Marks RUNNING tests from an earlier process ABORTED/PROCESS_ENDED. Does not resume any traffic. */
    suspend fun recoverInterrupted(startedBefore: Long, now: Long = System.currentTimeMillis()): Int =
        dao.abortRunning(now, startedBefore)

    suspend fun clear() = dao.clear()

    private fun displayName(raw: String) = raw.trim().take(60).ifBlank { "Unlabelled" }

    private fun normalizeLocation(name: String) =
        name.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
