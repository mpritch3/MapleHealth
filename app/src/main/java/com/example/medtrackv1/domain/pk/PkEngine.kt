package com.example.medtrackv1.domain.pk

import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object PkEngine {

    private const val MILLIS_PER_HOUR = 3600000.0

    /**
     * Derives elimination rate constant k_e = ln(2) / t_{1/2} (in hours^-1).
     */
    fun calculateKe(halfLifeHours: Double): Double {
        return if (halfLifeHours > 0.0) {
            ln(2.0) / halfLifeHours
        } else {
            0.0
        }
    }

    /** Returns the midpoint of a stored half-life range, or the legacy scalar value. */
    fun effectiveHalfLifeHours(medication: Medication): Double =
        rangeMidpoint(medication.halfLifeMinHours, medication.halfLifeMaxHours)
            ?: medication.halfLifeHours

    /** Returns the midpoint of a stored Tmax range, or the legacy absorption-time value. */
    fun effectiveTmaxHours(medication: Medication): Double =
        rangeMidpoint(medication.tmaxMinHours, medication.tmaxMaxHours)
            ?: medication.absorptionTimeHours

    private fun rangeMidpoint(min: Double?, max: Double?): Double? =
        if (min != null && max != null) min + (max - min) / 2.0 else null

    /**
     * Derives first-order absorption rate k_a from the requested Tmax and elimination rate.
     * For the Bateman model, Tmax = ln(k_a / k_e) / (k_a - k_e).
     */
    fun calculateKa(tmaxHours: Double, ke: Double): Double {
        if (tmaxHours <= 0.0 || ke <= 0.0) return 0.0
        val target = ke * tmaxHours
        if (abs(target - 1.0) < 1e-12) return ke

        fun tmaxRatio(rateRatio: Double): Double =
            if (abs(rateRatio - 1.0) < 1e-8) 1.0 else ln(rateRatio) / (rateRatio - 1.0)

        var low: Double
        var high: Double
        if (target < 1.0) {
            low = 1.0
            high = 2.0
            while (tmaxRatio(high) > target && high < 1e12) high *= 2.0
            repeat(100) {
                val mid = (low + high) / 2.0
                if (tmaxRatio(mid) > target) low = mid else high = mid
            }
        } else {
            low = kotlin.math.exp(-(target + 2.0).coerceAtMost(700.0))
            high = 1.0
            repeat(100) {
                val mid = (low + high) / 2.0
                if (tmaxRatio(mid) > target) low = mid else high = mid
            }
        }
        return ke * ((low + high) / 2.0)
    }

    /**
     * Calculates concentration C(t) at [timestamp] based on superposition of all past logged doses.
     */
    fun calculateConcentrationAt(
        medication: Medication,
        doseLogs: List<DoseLog>,
        timestamp: Long
    ): Double {
        val ke = calculateKe(effectiveHalfLifeHours(medication))
        val ka = calculateKa(effectiveTmaxHours(medication), ke)

        if (ke <= 0.0) return 0.0

        var totalConcentration = 0.0

        for (dose in doseLogs) {
            if (dose.timestamp > timestamp) continue

            val deltaHours = (timestamp - dose.timestamp) / MILLIS_PER_HOUR
            if (deltaHours < 0.0) continue

            val doseAmount = dose.doseAmount

            val c = if (ka > 0.0) {
                if (abs(ka - ke) < 1e-6) {
                    // Edge case ka ≈ ke: C(t) = D * ke * t * exp(-ke * t)
                    doseAmount * ke * deltaHours * exp(-ke * deltaHours)
                } else {
                    // Bateman function: C(t) = (D * ka / (ka - ke)) * (exp(-ke * t) - exp(-ka * t))
                    (doseAmount * ka / (ka - ke)) * (exp(-ke * deltaHours) - exp(-ka * deltaHours))
                }
            } else {
                // Immediate absorption (1-compartment IV bolus model): C(t) = D * exp(-ke * t)
                doseAmount * exp(-ke * deltaHours)
            }

            if (!c.isNaN() && c > 0.0) {
                totalConcentration += c
            }
        }

        return totalConcentration
    }

    /**
     * Generates concentration curve points from [startTime] to [endTime].
     */
    fun calculateConcentrationCurve(
        medication: Medication,
        doseLogs: List<DoseLog>,
        startTime: Long,
        endTime: Long,
        pointsCount: Int = 100
    ): List<ConcentrationPoint> {
        if (pointsCount <= 1 || endTime <= startTime) {
            return listOf(
                ConcentrationPoint(
                    timestamp = startTime,
                    concentration = calculateConcentrationAt(medication, doseLogs, startTime)
                )
            )
        }

        val points = ArrayList<ConcentrationPoint>(pointsCount)
        val step = (endTime - startTime).toDouble() / (pointsCount - 1)

        for (i in 0 until pointsCount) {
            val t = if (i == pointsCount - 1) endTime else (startTime + i * step).toLong()
            val c = calculateConcentrationAt(medication, doseLogs, t)
            points.add(ConcentrationPoint(timestamp = t, concentration = c))
        }

        return points
    }

    /**
     * Estimates the timestamp when concentration drops down to [targetConcentration] starting from [fromTime].
     * Returns null if concentration never drops to target or if parameters are invalid.
     */
    fun estimateTimeWhenConcentrationDropsTo(
        medication: Medication,
        doseLogs: List<DoseLog>,
        targetConcentration: Double,
        fromTime: Long
    ): Long? {
        if (targetConcentration <= 0.0 || doseLogs.isEmpty()) {
            return null
        }

        val initialConcentration = calculateConcentrationAt(medication, doseLogs, fromTime)
        if (initialConcentration <= targetConcentration) {
            // Check if concentration will rise above target in the future (due to un-absorbed doses)
            val checkWindow = (effectiveTmaxHours(medication) * 5.0 * 3600 * 1000L).toLong().coerceAtLeast(3600000L)
            var risesAbove = false

            for (i in 1..20) {
                val sampleTime = fromTime + (checkWindow * i / 20)
                val conc = calculateConcentrationAt(medication, doseLogs, sampleTime)
                if (conc > targetConcentration) {
                    risesAbove = true
                    break
                }
            }

            if (!risesAbove) {
                return fromTime
            }
        }

        // Define search horizon (up to 10 half lives or minimum 48 hours)
        val horizonHours = maxOf(48.0, effectiveHalfLifeHours(medication) * 10.0)
        var maxSearchTime = (horizonHours * 3600 * 1000L).toLong()

        // Sample points to find the bracket where concentration drops below targetConcentration
        val numSamples = 200
        var foundBracketStart: Long? = null
        var foundBracketEnd: Long? = null

        for (_attempt in 1..2) {
            var prevTime = fromTime
            var prevConc = calculateConcentrationAt(medication, doseLogs, prevTime)

            for (i in 1..numSamples) {
                val currTime = fromTime + (maxSearchTime * i / numSamples)
                val currConc = calculateConcentrationAt(medication, doseLogs, currTime)

                if (prevConc >= targetConcentration && currConc <= targetConcentration) {
                    foundBracketStart = prevTime
                    foundBracketEnd = currTime
                    break
                }

                prevTime = currTime
                prevConc = currConc
            }

            if (foundBracketStart != null) {
                break
            }

            // Extend search horizon if needed
            maxSearchTime *= 3
        }

        val start = foundBracketStart ?: return null
        val end = foundBracketEnd ?: return null

        // Bisection search for precise timestamp
        var low: Long = start
        var high: Long = end

        repeat(40) {
            val mid = low + (high - low) / 2
            val midConc = calculateConcentrationAt(medication, doseLogs, mid)

            if (midConc >= targetConcentration) {
                low = mid
            } else {
                high = mid
            }

            if (high - low <= 1000L) { // Within 1 second precision
                return@repeat
            }
        }

        return (low + high) / 2
    }

    /**
     * Calculates interval τ (in hours) for maintaining a given target average concentration:
     * interval = doseAmount / (targetAvgConcentration * k_e)
     */
    fun calculateIntervalForAvgConcentration(
        medication: Medication,
        targetAvgConcentration: Double
    ): Double {
        val ke = calculateKe(effectiveHalfLifeHours(medication))
        return if (targetAvgConcentration > 0.0 && ke > 0.0) {
            medication.doseAmount / (targetAvgConcentration * ke)
        } else {
            0.0
        }
    }
}
