package com.example.medtrackv1.data.repository

import com.example.medtrackv1.data.local.CounterDao
import com.example.medtrackv1.data.model.Counter
import com.example.medtrackv1.data.model.CounterHit
import kotlinx.coroutines.flow.Flow

class CounterRepository(private val counterDao: CounterDao) {
    val counters: Flow<List<Counter>> = counterDao.getCounters()
    val counterHits: Flow<List<CounterHit>> = counterDao.getCounterHits()

    suspend fun getHitsForCounter(counterId: Long): List<CounterHit> =
        counterDao.getHitsForCounter(counterId)

    suspend fun getCountersSync(): List<Counter> = counterDao.getCountersSync()

    suspend fun getCounterById(counterId: Long): Counter? = counterDao.getCounterById(counterId)

    suspend fun getCounterHitsSync(): List<CounterHit> = counterDao.getCounterHitsSync()

    suspend fun insertCounter(counter: Counter): Long = counterDao.insertCounter(counter)

    suspend fun insertCounterHit(hit: CounterHit): Long = counterDao.insertCounterHit(hit)

    suspend fun updateCounterHit(hit: CounterHit) = counterDao.updateCounterHit(hit)

    suspend fun updateCounter(counter: Counter) = counterDao.updateCounter(counter)

    suspend fun renameCounterHits(counterId: Long, name: String) = counterDao.renameCounterHits(counterId, name)

    suspend fun deleteCounterHit(hitId: Long) = counterDao.deleteCounterHitById(hitId)

    suspend fun deleteCounter(counterId: Long) {
        counterDao.deleteHitsForCounter(counterId)
        counterDao.deleteCounterById(counterId)
    }
}