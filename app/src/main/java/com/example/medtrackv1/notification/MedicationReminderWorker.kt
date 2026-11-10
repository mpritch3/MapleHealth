package com.example.medtrackv1.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.medtrackv1.MedTrackApplication
import com.example.medtrackv1.R
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import com.example.medtrackv1.domain.pk.PkEngine
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max

class MedicationReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0) return Result.failure()
        val app = applicationContext as MedTrackApplication
        val medication = app.medicationRepository.getMedicationById(id) ?: return Result.success()
        val now = System.currentTimeMillis()
        val logs = app.doseLogRepository.getDoseLogsForMedicationSync(id)
        val concentration = PkEngine.calculateConcentrationAt(medication, logs, now)
        val shouldNotify = when (medication.notificationMode) {
            NotificationMode.TIME_BASED, NotificationMode.AVERAGE_CONCENTRATION -> true
            NotificationMode.LOW_CONCENTRATION -> concentration <= (medication.targetLowConcentration ?: 0.0)
        }
        if (shouldNotify) postReminder(medication, concentration)
        if (medication.notificationMode != NotificationMode.LOW_CONCENTRATION) {
            MedicationReminderScheduler.schedule(applicationContext, medication, logs)
        }
        return Result.success()
    }

    private fun postReminder(medication: Medication, concentration: Double) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Medication reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Medication reminder: ${medication.name}")
            .setContentText("Estimated level: %.2f %s".format(concentration, medication.unit))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(applicationContext).notify(medication.id.toInt(), notification) } catch (_: SecurityException) { }
    }

    companion object {
        const val KEY_ID = "medication_id"
        const val CHANNEL_ID = "medication_reminders"
    }
}

object MedicationReminderScheduler {
    fun schedule(context: Context, medication: Medication, logs: List<com.example.medtrackv1.data.model.DoseLog>) {
        val now = System.currentTimeMillis()
        val trigger = when (medication.notificationMode) {
            NotificationMode.TIME_BASED -> nextScheduledTime(reminderTimes(medication), now)
            NotificationMode.LOW_CONCENTRATION -> {
                val threshold = medication.targetLowConcentration ?: return cancel(context, medication.id)
                PkEngine.estimateTimeWhenConcentrationDropsTo(medication, logs, threshold, now) ?: return cancel(context, medication.id)
            }
            NotificationMode.AVERAGE_CONCENTRATION -> {
                if (logs.isEmpty()) return cancel(context, medication.id)
                val interval = PkEngine.calculateIntervalForAvgConcentration(medication, medication.targetAvgConcentration ?: 0.0)
                if (interval <= 0.0) return cancel(context, medication.id)
                max(now, logs.maxOf { it.timestamp }) + (interval * 3_600_000.0).toLong()
            }
        }
        val delay = max(1_000L, trigger - now)
        val request = OneTimeWorkRequestBuilder<MedicationReminderWorker>()
            .setInputData(workDataOf(MedicationReminderWorker.KEY_ID to medication.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(medication.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, medicationId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(medicationId))
    }

    private fun workName(id: Long) = "medication-reminder-$id"

    private fun reminderTimes(medication: Medication): List<LocalTime> {
        val configuredTimes = medication.scheduledTimes
            ?.takeIf { it.isNotBlank() }
            ?: medication.scheduledTime
            ?: "08:00"
        return configuredTimes.split(',')
            .mapNotNull { value -> runCatching { LocalTime.parse(value.trim()) }.getOrNull() }
            .distinct()
            .sorted()
            .ifEmpty { listOf(LocalTime.of(8, 0)) }
    }

    private fun nextScheduledTime(times: List<LocalTime>, now: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        return sequenceOf(today, today.plusDays(1))
            .flatMap { date -> times.asSequence().map { time ->
                date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            } }
            .filter { it > now }
            .minOrNull() ?: today.plusDays(2).atTime(times.first())
                .atZone(zone).toInstant().toEpochMilli()
    }
}
