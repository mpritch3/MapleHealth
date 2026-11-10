package com.example.medtrackv1

import com.example.medtrackv1.data.local.Converters
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryTest {

    @Test
    fun testNotificationModeConverter() {
        val converters = Converters()

        assertEquals("TIME_BASED", converters.fromNotificationMode(NotificationMode.TIME_BASED))
        assertEquals("LOW_CONCENTRATION", converters.fromNotificationMode(NotificationMode.LOW_CONCENTRATION))
        assertEquals("AVERAGE_CONCENTRATION", converters.fromNotificationMode(NotificationMode.AVERAGE_CONCENTRATION))

        assertEquals(NotificationMode.TIME_BASED, converters.toNotificationMode("TIME_BASED"))
        assertEquals(NotificationMode.LOW_CONCENTRATION, converters.toNotificationMode("LOW_CONCENTRATION"))
        assertEquals(NotificationMode.AVERAGE_CONCENTRATION, converters.toNotificationMode("AVERAGE_CONCENTRATION"))
        assertEquals(NotificationMode.TIME_BASED, converters.toNotificationMode("INVALID_MODE"))
        assertEquals(NotificationMode.TIME_BASED, converters.toNotificationMode(null))
    }

    @Test
    fun testMedicationModelInstantiation() {
        val med = Medication(
            id = 10L,
            name = "Aspirin",
            unit = "mg",
            doseAmount = 500.0,
            halfLifeHours = 3.0,
            absorptionTimeHours = 0.5,
            notificationMode = NotificationMode.LOW_CONCENTRATION,
            scheduledTime = "08:00",
            targetLowConcentration = 10.0,
            targetAvgConcentration = 50.0
        )

        assertEquals(10L, med.id)
        assertEquals("Aspirin", med.name)
        assertEquals("mg", med.unit)
        assertEquals(500.0, med.doseAmount, 1e-6)
        assertEquals(3.0, med.halfLifeHours, 1e-6)
        assertEquals(0.5, med.absorptionTimeHours, 1e-6)
        assertEquals(NotificationMode.LOW_CONCENTRATION, med.notificationMode)
        assertEquals("08:00", med.scheduledTime)
        assertEquals(10.0, med.targetLowConcentration!!, 1e-6)
        assertEquals(50.0, med.targetAvgConcentration!!, 1e-6)
    }

    @Test
    fun testDoseLogModelInstantiation() {
        val now = System.currentTimeMillis()
        val log = DoseLog(
            id = 5L,
            medicationId = 10L,
            timestamp = now,
            doseAmount = 250.0
        )

        assertEquals(5L, log.id)
        assertEquals(10L, log.medicationId)
        assertEquals(now, log.timestamp)
        assertEquals(250.0, log.doseAmount, 1e-6)
    }
}
