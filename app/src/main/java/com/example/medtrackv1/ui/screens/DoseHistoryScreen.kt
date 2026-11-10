package com.example.medtrackv1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.CounterHit
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.util.DurationFormatter
import com.example.medtrackv1.ui.viewmodel.MedicationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface HistoryEntry {
    val id: Long
    val timestamp: Long
    val stableKey: String

    data class Dose(val log: DoseLog) : HistoryEntry {
        override val id = log.id
        override val timestamp = log.timestamp
        override val stableKey = "dose-" + id
    }

    data class Counter(val hit: CounterHit) : HistoryEntry {
        override val id = hit.id
        override val timestamp = hit.timestamp
        override val stableKey = "counter-" + id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseHistoryScreen(viewModel: MedicationViewModel, modifier: Modifier = Modifier) {
    val doseLogs by viewModel.filteredDoseLogs.collectAsState()
    val counterHits by viewModel.counterHits.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val selectedFilterId by viewModel.selectedMedicationFilterId.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val medMap = remember(medications) { medications.associateBy { it.id } }
    val entries = remember(doseLogs, counterHits) {
        (doseLogs.map { HistoryEntry.Dose(it) } + counterHits.map { HistoryEntry.Counter(it) })
            .sortedWith(compareByDescending<HistoryEntry> { it.timestamp }.thenByDescending { it.id })
    }
    var filterDropdownExpanded by remember { mutableStateOf(false) }
    var doseLogToDelete by remember { mutableStateOf<DoseLog?>(null) }
    var counterHitToDelete by remember { mutableStateOf<CounterHit?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = filterDropdownExpanded,
            onExpandedChange = { filterDropdownExpanded = !filterDropdownExpanded }
        ) {
            val filterText = if (selectedFilterId == null) {
                "All History (" + entries.size + ")"
            } else {
                medMap[selectedFilterId]?.name ?: "Selected Medication"
            }
            OutlinedTextField(
                value = filterText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter History by Medication") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterDropdownExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = filterDropdownExpanded,
                onDismissRequest = { filterDropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All History") },
                    onClick = {
                        viewModel.setFilterMedicationId(null)
                        filterDropdownExpanded = false
                    }
                )
                medications.forEach { medication ->
                    DropdownMenuItem(
                        text = { Text(medication.name) },
                        onClick = {
                            viewModel.setFilterMedicationId(medication.id)
                            filterDropdownExpanded = false
                        }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.padding(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No history recorded", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Medication doses and counter resets appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.stableKey }) { entry ->
                    when (entry) {
                        is HistoryEntry.Dose -> {
                            val doseLog = entry.log
                            val medication = medMap[doseLog.medicationId]
                            val medName = medication?.name ?: "Unknown Medication"
                            val unit = medication?.unit ?: "mg"
                            val concentration = viewModel.calculateConcentrationAtDoseTime(doseLog)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(medName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Dose: " + doseLog.doseAmount + " " + unit, color = MaterialTheme.colorScheme.primary)
                                        Text("Time: " + dateFormat.format(Date(doseLog.timestamp)), style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Concentration when taken: " + String.format(Locale.US, "%.4f", concentration) + " " + unit,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(onClick = { doseLogToDelete = doseLog }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Dose Log", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        is HistoryEntry.Counter -> {
                            val hit = entry.hit
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(hit.counterName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Reset time: " + dateFormat.format(Date(hit.timestamp)), style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Timer total: " + formatHistoryDuration(hit.elapsedMillis),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { counterHitToDelete = hit }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete " + hit.counterName + " reset", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    doseLogToDelete?.let { doseLog ->
        val medName = medMap[doseLog.medicationId]?.name ?: "this medication"
        AlertDialog(
            onDismissRequest = { doseLogToDelete = null },
            title = { Text("Delete Dose Log") },
            text = { Text("Are you sure you want to delete this dose log entry for " + medName + "?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDoseLog(doseLog)
                    doseLogToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { doseLogToDelete = null }) { Text("Cancel") } }
        )
    }

    counterHitToDelete?.let { hit ->
        AlertDialog(
            onDismissRequest = { counterHitToDelete = null },
            title = { Text("Delete Tracker Reset") },
            text = { Text("Delete the " + hit.counterName + " reset from history?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCounterHit(hit)
                    counterHitToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { counterHitToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun formatHistoryDuration(durationMillis: Long): String =
    DurationFormatter.format(durationMillis)
