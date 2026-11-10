package com.example.medtrackv1.data.repository

import com.example.medtrackv1.data.local.MedicationDao
import com.example.medtrackv1.data.model.Medication
import kotlinx.coroutines.flow.Flow

class MedicationRepository(private val medicationDao: MedicationDao) {
    val allMedications: Flow<List<Medication>> = medicationDao.getAllMedications()

    fun getMedicationByIdFlow(id: Long): Flow<Medication?> = medicationDao.getMedicationByIdFlow(id)

    suspend fun getMedicationById(id: Long): Medication? = medicationDao.getMedicationById(id)

    suspend fun insertMedication(medication: Medication): Long = medicationDao.insertMedication(medication)

    suspend fun insertMedications(medications: List<Medication>): List<Long> = medicationDao.insertMedications(medications)

    suspend fun updateMedication(medication: Medication) = medicationDao.updateMedication(medication)

    suspend fun updateMedications(medications: List<Medication>) = medicationDao.updateMedications(medications)

    suspend fun deleteMedication(medication: Medication) = medicationDao.deleteMedication(medication)

    suspend fun deleteMedicationById(id: Long) = medicationDao.deleteMedicationById(id)
}
