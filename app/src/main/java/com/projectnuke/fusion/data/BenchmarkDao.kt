package com.projectnuke.fusion.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {
    @Insert
    suspend fun insert(result: BenchmarkResultEntity): Long

    @Query("SELECT * FROM benchmark_results ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BenchmarkResultEntity>>

    @Delete
    suspend fun delete(result: BenchmarkResultEntity)
}
