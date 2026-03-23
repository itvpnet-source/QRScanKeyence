package com.keyence.qrscan

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(item: ScanItem): Long

    @Delete
    suspend fun delete(item: ScanItem)

    @Query("DELETE FROM scan_items WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM scan_items WHERE sessionId = :sessionId ORDER BY scannedAt DESC")
    fun getBySession(sessionId: Long): Flow<List<ScanItem>>

    @Query("SELECT * FROM scan_items WHERE sessionId = :sessionId ORDER BY scannedAt ASC")
    suspend fun getBySessionList(sessionId: Long): List<ScanItem>

    @Query("SELECT * FROM scan_items WHERE sessionId = :sessionId AND rawQr = :raw LIMIT 1")
    suspend fun findDuplicate(sessionId: Long, raw: String): ScanItem?

    @Query("SELECT DISTINCT sessionId FROM scan_items ORDER BY sessionId DESC")
    suspend fun getAllSessionIds(): List<Long>
}

@Database(entities = [ScanItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qrscan.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
