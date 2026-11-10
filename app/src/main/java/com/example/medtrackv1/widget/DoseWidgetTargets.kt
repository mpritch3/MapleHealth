package com.example.medtrackv1.widget

import android.util.Base64
import com.example.medtrackv1.data.model.Medication

internal data class DoseWidgetTarget(
    val key: String,
    val label: String,
    val medicationIds: List<Long>
)

internal object DoseWidgetTargets {
    fun fromMedications(medications: List<Medication>): List<DoseWidgetTarget> {
        val medTargets = medications.map { medication ->
            DoseWidgetTarget(
                key = medicationKey(medication.id),
                label = medication.name,
                medicationIds = listOf(medication.id)
            )
        }
        val groupTargets = medications
            .filter { !it.groupName.isNullOrBlank() }
            .groupBy { it.groupName!! }
            .map { (name, members) ->
                DoseWidgetTarget(
                    key = groupKey(name),
                    label = "${name} (${members.size})",
                    medicationIds = members.map { it.id }
                )
            }
        return medTargets + groupTargets
    }

    fun medicationKey(id: Long) = "med:$id"

    fun groupKey(name: String): String =
        "group:" + Base64.encodeToString(
            name.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun resolve(key: String, medications: List<Medication>): DoseWidgetTarget? =
        fromMedications(medications).firstOrNull { it.key == key }
}
