package com.example.medtrackv1.data.repository

import com.example.medtrackv1.data.local.DoseLogDao
import com.example.medtrackv1.data.model.DoseLog
import kotlinx.coroutines.flow.Flow

class DoseLogRepository(private val doseLogDao: DoseLogDao) {
    val allDoseLogs: Flow<List<DoseLog>> = doseLogDao.getAllDoseLogs()

    fun getDoseLogsForMedication(medicationId: Long): Flow<List<DoseLog>> =
        doseLogDao.getDoseLogsForMedication(medicationId)

    suspend fun getDoseLogsForMedicationSync(medicationId: Long): List<DoseLog> =
        doseLogDao.getDoseLogsForMedicationSync(medicationId)

    suspend fun getDoseLogById(id: Long): DoseLog? =
        doseLogDao.getDoseLogById(id)

    suspend fun insertDoseLog(doseLog: DoseLog): Long = doseLogDao.insertDoseLog(doseLog)

    suspend fun insertDoseLogs(doseLogs: List<DoseLog>): List<Long> = doseLogDao.insertDoseLogs(doseLogs)

    suspend fun updateDoseLog(doseLog: DoseLog) = doseLogDao.updateDoseLog(doseLog)

    suspend fun deleteDoseLog(doseLog: DoseLog) = doseLogDao.deleteDoseLog(doseLog)

    suspend fun deleteDoseLogById(id: Long) = doseLogDao.deleteDoseLogById(id)

    suspend fun deleteDoseLogsForMedication(medicationId: Long) =
        doseLogDao.deleteDoseLogsForMedication(medicationId)
}
