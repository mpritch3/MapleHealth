package com.example.medtrackv1

import android.app.Application
import com.example.medtrackv1.data.local.MedTrackDatabase
import com.example.medtrackv1.data.repository.CounterRepository
import com.example.medtrackv1.data.repository.DoseLogRepository
import com.example.medtrackv1.data.repository.MedicationRepository

class MedTrackApplication : Application() {
    val database: MedTrackDatabase by lazy { MedTrackDatabase.getDatabase(this) }
    val medicationRepository: MedicationRepository by lazy { MedicationRepository(database.medicationDao()) }
    val doseLogRepository: DoseLogRepository by lazy { DoseLogRepository(database.doseLogDao()) }
    val counterRepository: CounterRepository by lazy { CounterRepository(database.counterDao()) }
}
