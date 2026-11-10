package com.example.medtrackv1.data.model

data class CounterConcentrationSnapshot(
    val medicationId: Long,
    val medicationName: String,
    val unit: String,
    val concentration: Double?
)

data class CounterConcentrationSnapshotList(
    val medications: List<CounterConcentrationSnapshot>
)