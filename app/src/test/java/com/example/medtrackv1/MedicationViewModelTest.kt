package com.example.medtrackv1

import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import com.example.medtrackv1.data.util.DataExporterImporter
import com.example.medtrackv1.domain.pk.PkEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationViewModelTest {

    @Test
    fun testDataExporterImporterCsvFormat() {
        val med = Medication(
            id = 1L,
            name = "Metformin",
            unit = "mg",
            doseAmount = 850.0,
            halfLifeHours = 6.0,
            absorptionTimeHours = 1.0,
            notificationMode = NotificationMode.TIME_BASED,
            scheduledTime = "09:00"
        )
        val timestamp = 1708675200000L
        val dose = DoseLog(id = 100L, medicationId = 1L, timestamp = timestamp, doseAmount = 850.0)

        val csv = DataExporterImporter.exportCsv(listOf(med), listOf(dose))

        assertTrue(csv.contains("Dose Time, Medication Name, Dose Amount (unit), Concentration When Taken"))
        assertTrue(csv.contains("Metformin"))
        assertTrue(csv.contains("850.0 mg"))

        val expectedConc = PkEngine.calculateConcentrationAt(med, listOf(dose), timestamp + 3600000L)
        assertTrue(expectedConc > 0.0)
    }

    @Test
    fun testDataExporterImporterJsonRoundtrip() {
        val med1 = Medication(
            id = 1L,
            name = "Lisopril",
            unit = "mg",
            doseAmount = 10.0,
            halfLifeHours = 12.0,
            absorptionTimeHours = 1.0,
            notificationMode = NotificationMode.TIME_BASED,
            scheduledTime = "08:00"
        )
        val med2 = Medication(
            id = 2L,
            name = "Atorvastatin",
            unit = "mg",
            doseAmount = 20.0,
            halfLifeHours = 14.0,
            absorptionTimeHours = 2.0,
            notificationMode = NotificationMode.LOW_CONCENTRATION,
            targetLowConcentration = 2.0
        )
        val dose1 = DoseLog(id = 10L, medicationId = 1L, timestamp = 1708675200000L, doseAmount = 10.0)
        val dose2 = DoseLog(id = 11L, medicationId = 2L, timestamp = 1708678800000L, doseAmount = 20.0)

        val json = DataExporterImporter.exportJson(listOf(med1, med2), listOf(dose1, dose2))
        assertNotNull(json)

        val (importedMeds, importedDoses) = DataExporterImporter.importJson(json)
        assertEquals(2, importedMeds.size)
        assertEquals(2, importedDoses.size)

        assertEquals("Lisopril", importedMeds[0].name)
        assertEquals("Atorvastatin", importedMeds[1].name)
        assertEquals(NotificationMode.LOW_CONCENTRATION, importedMeds[1].notificationMode)
        assertEquals(2.0, importedMeds[1].targetLowConcentration!!, 0.001)
    }
}
