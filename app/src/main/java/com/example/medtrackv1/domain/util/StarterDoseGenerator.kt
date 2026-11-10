package com.example.medtrackv1.domain.util

import com.example.medtrackv1.data.model.DoseLog

object StarterDoseGenerator {
    /**
     * Generates historical DoseLog entries starting from:
     * currentTime - (starterDoseCount * starterIntervalHours * 3600 * 1000)
     * up to
     * currentTime - (1 * starterIntervalHours * 3600 * 1000)
     */
    fun generateStarterDoses(
        medicationId: Long,
        doseAmount: Double,
        starterDoseCount: Int,
        starterIntervalHours: Double,
        currentTime: Long = System.currentTimeMillis()
    ): List<DoseLog> {
        if (starterDoseCount <= 0 || starterIntervalHours <= 0.0) return emptyList()

        val doseLogs = ArrayList<DoseLog>(starterDoseCount)
        val intervalMillis = (starterIntervalHours * 3600 * 1000.0).toLong()

        for (i in starterDoseCount downTo 1) {
            val timestamp = currentTime - (i * intervalMillis)
            doseLogs.add(
                DoseLog(
                    medicationId = medicationId,
                    timestamp = timestamp,
                    doseAmount = doseAmount
                )
            )
        }

        return doseLogs
    }
}
