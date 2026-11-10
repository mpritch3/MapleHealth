package com.example.medtrackv1.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medtrackv1.data.model.DoseLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs ORDER BY timestamp DESC")
    fun getAllDoseLogs(): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY timestamp DESC")
    fun getDoseLogsForMedication(medicationId: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY timestamp DESC")
    suspend fun getDoseLogsForMedicationSync(medicationId: Long): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getDoseLogById(id: Long): DoseLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(doseLog: DoseLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLogs(doseLogs: List<DoseLog>): List<Long>

    @Update
    suspend fun updateDoseLog(doseLog: DoseLog)

    @Delete
    suspend fun deleteDoseLog(doseLog: DoseLog)

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun deleteDoseLogById(id: Long)

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun deleteDoseLogsForMedication(medicationId: Long)
}
