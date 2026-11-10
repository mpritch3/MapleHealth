package com.example.medtrackv1.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "counter_hits",
    indices = [Index("counterId")]
)
data class CounterHit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val counterId: Long,
    val counterName: String,
    val timestamp: Long,
    val elapsedMillis: Long,
    val concentrationsJson: String
)