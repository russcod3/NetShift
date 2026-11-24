package edu.cs312.netshift.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import edu.cs312.netshift.data.db.dao.HandoffEventDao
import edu.cs312.netshift.data.db.dao.TestResultDao
import edu.cs312.netshift.data.db.entity.HandoffEventEntity
import edu.cs312.netshift.data.db.entity.TestResultEntity

@Database(
    entities = [TestResultEntity::class, HandoffEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NetShiftDatabase : RoomDatabase() {
    abstract fun testResultDao(): TestResultDao
    abstract fun handoffEventDao(): HandoffEventDao
}
