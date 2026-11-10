package com.example.medtrackv1

import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import com.example.medtrackv1.data.util.DataExporterImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExporterImporterTest {

    @Test
    fun testCsvExportFormat() {
        val med = Medication(
            id = 1,
            name = "Aspirin",
            unit = "mg",
            doseAmount = 500.0,
            halfLifeHours = 2.0,
            absorptionTimeHours = 0.5,
            notificationMode = NotificationMode.TIME_BASED,
            scheduledTime = "08:00"
        )
        val dose = DoseLog(
            id = 10,
            medicationId = 1,
            timestamp = 1700000000000L,
            doseAmount = 500.0
        )

        val csv = DataExporterImporter.exportCsv(listOf(med), listOf(dose))

        assertTrue(csv.contains("Dose Time, Medication Name, Dose Amount (unit), Concentration When Taken"))
        assertTrue(csv.contains("Aspirin"))
        assertTrue(csv.contains("500.0 mg"))
    }

    @Test
    fun testJsonExportImportRoundtrip() {
        val med1 = Medication(
            id = 1,
            name = "Ibuprofen",
            unit = "mg",
            doseAmount = 400.0,
            halfLifeHours = 2.0,
            absorptionTimeHours = 0.5,
            notificationMode = NotificationMode.LOW_CONCENTRATION,
            targetLowConcentration = 10.0
        )
        val med2 = Medication(
            id = 2,
            name = "Paracetamol",
            unit = "mg",
            doseAmount = 500.0,
            halfLifeHours = 4.0,
            absorptionTimeHours = 1.0,
            notificationMode = NotificationMode.AVERAGE_CONCENTRATION,
            targetAvgConcentration = 15.0
        )
        val dose1 = DoseLog(id = 1, medicationId = 1, timestamp = 1700000000000L, doseAmount = 400.0)
        val dose2 = DoseLog(id = 2, medicationId = 2, timestamp = 1700003600000L, doseAmount = 500.0)

        val json = DataExporterImporter.exportJson(listOf(med1, med2), listOf(dose1, dose2))
        val (importedMeds, importedDoses) = DataExporterImporter.importJson(json)

        assertEquals(2, importedMeds.size)
        assertEquals(2, importedDoses.size)

        assertEquals("Ibuprofen", importedMeds[0].name)
        assertEquals(NotificationMode.LOW_CONCENTRATION, importedMeds[0].notificationMode)
        assertEquals(10.0, importedMeds[0].targetLowConcentration!!, 0.001)

        assertEquals("Paracetamol", importedMeds[1].name)
        assertEquals(NotificationMode.AVERAGE_CONCENTRATION, importedMeds[1].notificationMode)
        assertEquals(15.0, importedMeds[1].targetAvgConcentration!!, 0.001)

        assertEquals(400.0, importedDoses[0].doseAmount, 0.001)
        assertEquals(500.0, importedDoses[1].doseAmount, 0.001)
    }
}
