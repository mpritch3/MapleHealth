package com.example.medtrackv1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.medtrackv1.data.model.Counter
import com.example.medtrackv1.data.model.CounterHit
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {
    @Query("SELECT * FROM counters ORDER BY name COLLATE NOCASE ASC")
    fun getCounters(): Flow<List<Counter>>

    @Query("SELECT * FROM counter_hits ORDER BY timestamp DESC")
    fun getCounterHits(): Flow<List<CounterHit>>

    @Query("SELECT * FROM counter_hits WHERE counterId = :counterId ORDER BY timestamp ASC")
    suspend fun getHitsForCounter(counterId: Long): List<CounterHit>

    @Query("SELECT * FROM counters")
    suspend fun getCountersSync(): List<Counter>

    @Query("SELECT * FROM counters WHERE id = :counterId LIMIT 1")
    suspend fun getCounterById(counterId: Long): Counter?

    @Query("SELECT * FROM counter_hits ORDER BY timestamp ASC")
    suspend fun getCounterHitsSync(): List<CounterHit>

    @Insert
    suspend fun insertCounter(counter: Counter): Long

    @Insert
    suspend fun insertCounterHit(hit: CounterHit): Long

    @Update
    suspend fun updateCounterHit(hit: CounterHit)

    @Update
    suspend fun updateCounter(counter: Counter)

    @Query("UPDATE counter_hits SET counterName = :name WHERE counterId = :counterId")
    suspend fun renameCounterHits(counterId: Long, name: String)

    @Query("DELETE FROM counter_hits WHERE id = :hitId")
    suspend fun deleteCounterHitById(hitId: Long)

    @Query("DELETE FROM counter_hits WHERE counterId = :counterId")
    suspend fun deleteHitsForCounter(counterId: Long)

    @Query("DELETE FROM counters WHERE id = :counterId")
    suspend fun deleteCounterById(counterId: Long)
}