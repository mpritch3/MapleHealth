package com.example.medtrackv1.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val unit: String,
    val doseAmount: Double,
    val halfLifeHours: Double,
    /** Legacy-named scalar storing effective Tmax for existing database and backup compatibility. */
    val absorptionTimeHours: Double,
    val notificationMode: NotificationMode = NotificationMode.TIME_BASED,
    val scheduledTime: String? = null,
    /** Comma-separated daily reminder times in HH:mm format; scheduledTime remains the legacy first time. */
    val scheduledTimes: String? = null,
    val targetLowConcentration: Double? = null,
    val targetAvgConcentration: Double? = null,
    val groupName: String? = null,
    val halfLifeMinHours: Double? = null,
    val halfLifeMaxHours: Double? = null,
    val tmaxMinHours: Double? = null,
    val tmaxMaxHours: Double? = null,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val groupDoseMultiplier: Int = 1
)
