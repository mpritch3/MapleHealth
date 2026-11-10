package com.example.medtrackv1.data.local

import androidx.room.TypeConverter
import com.example.medtrackv1.data.model.NotificationMode

class Converters {
    @TypeConverter
    fun fromNotificationMode(mode: NotificationMode?): String {
        return mode?.name ?: NotificationMode.TIME_BASED.name
    }

    @TypeConverter
    fun toNotificationMode(value: String?): NotificationMode {
        if (value.isNullOrEmpty()) return NotificationMode.TIME_BASED
        return try {
            NotificationMode.valueOf(value)
        } catch (e: Exception) {
            NotificationMode.TIME_BASED
        }
    }
}
