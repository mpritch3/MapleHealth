package com.example.medtrackv1

import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import com.example.medtrackv1.domain.pk.PkEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class PkEngineTest {

    private val testMedication = Medication(
        id = 1L,
        name = "TestMed",
        unit = "mg",
        doseAmount = 100.0,
        halfLifeHours = 4.0,
        absorptionTimeHours = 1.0,
        notificationMode = NotificationMode.TIME_BASED
    )

    @Test
    fun testCalculateKe() {
        val ke = PkEngine.calculateKe(4.0)
        assertEquals(ln(2.0) / 4.0, ke, 1e-6)

        // Invalid half-life returns 0
        assertEquals(0.0, PkEngine.calculateKe(0.0), 1e-6)
        assertEquals(0.0, PkEngine.calculateKe(-2.0), 1e-6)
    }

    @Test
    fun testCalculateKaMatchesRequestedTmax() {
        val ke = PkEngine.calculateKe(4.0)
        val tmaxHours = 2.0
        val ka = PkEngine.calculateKa(tmaxHours, ke)
        val calculatedTmax = ln(ka / ke) / (ka - ke)

        assertEquals(tmaxHours, calculatedTmax, 1e-6)
        assertEquals(0.0, PkEngine.calculateKa(0.0, ke), 1e-6)
        assertEquals(0.0, PkEngine.calculateKa(tmaxHours, 0.0), 1e-6)
    }

    @Test
    fun testCalculateConcentrationAt_singleDose() {
        val now = 1_000_000_000_000L
        val doseLogs = listOf(
            DoseLog(id = 1L, medicationId = 1L, timestamp = now, doseAmount = 100.0)
        )

        // At dose time (t=0), concentration should be near 0 due to absorption time
        val concAtStart = PkEngine.calculateConcentrationAt(testMedication, doseLogs, now)
        assertEquals(0.0, concAtStart, 1e-6)

        // At t = 2 hours after dose, concentration should be positive
        val concAt2Hours = PkEngine.calculateConcentrationAt(testMedication, doseLogs, now + 2 * 3600 * 1000L)
        assertTrue("Concentration after 2h should be > 0", concAt2Hours > 0.0)

        // At t = 24 hours after dose, concentration should decay close to 0
        val concAt24Hours = PkEngine.calculateConcentrationAt(testMedication, doseLogs, now + 24 * 3600 * 1000L)
        assertTrue("Concentration after 24h should be significantly smaller than after 2h", concAt24Hours < concAt2Hours)
    }

    @Test
    fun testCalculateConcentrationAt_whenTmaxMatchesKeMaintainsFiniteConcentration() {
        val medicationEquil = Medication(
            id = 2L,
            name = "EqualRateMed",
            unit = "mg",
            doseAmount = 100.0,
            halfLifeHours = 4.0,
            absorptionTimeHours = 4.0 / ln(2.0) // Tmax = 1 / ke, so ka = ke
        )

        val now = 1_000_000_000_000L
        val doseLogs = listOf(
            DoseLog(id = 1L, medicationId = 2L, timestamp = now, doseAmount = 100.0)
        )

        val concAt4Hours = PkEngine.calculateConcentrationAt(medicationEquil, doseLogs, now + 4 * 3600 * 1000L)
        assertTrue("Concentration when ka == ke should not be NaN or Infinite", !concAt4Hours.isNaN() && !concAt4Hours.isInfinite())
        assertTrue("Concentration should be positive", concAt4Hours > 0.0)
    }

    @Test
    fun testCalculateConcentrationCurve() {
        val now = 1_000_000_000_000L
        val endTime = now + 10 * 3600 * 1000L
        val doseLogs = listOf(
            DoseLog(id = 1L, medicationId = 1L, timestamp = now, doseAmount = 100.0)
        )

        val curve = PkEngine.calculateConcentrationCurve(testMedication, doseLogs, now, endTime, pointsCount = 11)
        assertEquals(11, curve.size)
        assertEquals(now, curve.first().timestamp)
        assertEquals(endTime, curve.last().timestamp)
    }

    @Test
    fun testEstimateTimeWhenConcentrationDropsTo() {
        val now = 1_000_000_000_000L
        val doseLogs = listOf(
            DoseLog(id = 1L, medicationId = 1L, timestamp = now, doseAmount = 100.0)
        )

        // Peak concentration for this dose is around 30-50
        val peakTime = now + (2 * 3600 * 1000L)
        val peakConc = PkEngine.calculateConcentrationAt(testMedication, doseLogs, peakTime)

        val targetConc = peakConc / 2.0
        val dropTime = PkEngine.estimateTimeWhenConcentrationDropsTo(testMedication, doseLogs, targetConc, peakTime)

        assertNotNull("Drop time should not be null", dropTime)
        assertTrue("Drop time should be after peak time", dropTime!! > peakTime)

        val concAtDropTime = PkEngine.calculateConcentrationAt(testMedication, doseLogs, dropTime)
        assertTrue(
            "Concentration at estimated drop time ($concAtDropTime) should be close to target ($targetConc)",
            abs(concAtDropTime - targetConc) < 0.5
        )
    }

    @Test
    fun testCalculateIntervalForAvgConcentration() {
        val medication = Medication(
            id = 1L,
            name = "TestMed",
            unit = "mg",
            doseAmount = 100.0,
            halfLifeHours = 12.0,
            absorptionTimeHours = 1.0
        )

        val ke = ln(2.0) / 12.0
        val targetAvgConc = 10.0
        val expectedInterval = 100.0 / (targetAvgConc * ke) // = 100 / (10 * 0.05776) = ~173.16 hours

        val interval = PkEngine.calculateIntervalForAvgConcentration(medication, targetAvgConc)
        assertEquals(expectedInterval, interval, 1e-4)
    }
}
