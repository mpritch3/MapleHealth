package com.example.medtrackv1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.Counter
import com.example.medtrackv1.data.util.DurationFormatter
import com.example.medtrackv1.ui.viewmodel.MedicationViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val counterDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

@Composable
fun CountersScreen(
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier,
    requestedAddCounter: Boolean = false,
    onTrackerActionHandled: () -> Unit = {}
) {
    val counters by viewModel.counters.collectAsState()
    val counterHits by viewModel.counterHits.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAddCounter by remember { mutableStateOf(false) }
    var counterToEdit by remember { mutableStateOf<Counter?>(null) }
    var counterToDelete by remember { mutableStateOf<Counter?>(null) }
    var pastHitCounter by remember { mutableStateOf<Counter?>(null) }
    var counterName by remember { mutableStateOf("") }
    var pastHitTimestamp by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedAddCounter) {
        if (requestedAddCounter) {
            counterToEdit = null
            counterName = ""
            inputError = null
            showAddCounter = true
            onTrackerActionHandled()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (counters.isEmpty()) {
            Text(
                "Add a tracker from the menu to start tracking events.",
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(counters, key = { it.id }) { counter ->
                    val latestHit = counterHits
                        .asSequence()
                        .filter { it.counterId == counter.id }
                        .maxByOrNull { it.timestamp }
                    val timerStart = latestHit?.timestamp ?: counter.createdAt
                    val elapsed = (now - timerStart).coerceAtLeast(0L)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(counter.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    counterToEdit = counter
                                    counterName = counter.name
                                    inputError = null
                                    showAddCounter = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit ${counter.name}", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { counterToDelete = counter }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${counter.name}", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(
                                formatCounterDuration(elapsed),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                latestHit?.let { "Since last reset: ${counterDateFormatter.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.timestamp), ZoneId.systemDefault()))}" }
                                    ?: "Timer started when this tracker was created",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.recordCounterHit(counter) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reset")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pastHitCounter = counter
                                        pastHitTimestamp = counterDateFormatter.format(
                                            LocalDateTime.now()
                                        )
                                        inputError = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Add past reset")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCounter) {
        AlertDialog(
            onDismissRequest = { showAddCounter = false; counterToEdit = null },
            title = { Text(if (counterToEdit == null) "Add tracker" else "Edit tracker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = counterName,
                        onValueChange = { counterName = it },
                        label = { Text("Tracker name") },
                        singleLine = true
                    )
                    inputError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (counterName.isBlank()) {
                        inputError = "Enter a tracker name"
                    } else {
                        counterToEdit?.let { viewModel.updateCounterName(it, counterName) }
                            ?: viewModel.addCounter(counterName)
                        showAddCounter = false
                        counterToEdit = null
                    }
                }) { Text(if (counterToEdit == null) "Add" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCounter = false; counterToEdit = null }) { Text("Cancel") }
            }
        )
    }

    counterToDelete?.let { counter ->
        AlertDialog(
            onDismissRequest = { counterToDelete = null },
            title = { Text("Delete tracker") },
            text = { Text("Delete tracker ${counter.name} and all of its recorded resets? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCounter(counter)
                    counterToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { counterToDelete = null }) { Text("Cancel") }
            }
        )
    }

    pastHitCounter?.let { counter ->
        AlertDialog(
            onDismissRequest = { pastHitCounter = null },
            title = { Text("Add past reset for ${counter.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the local date and time as yyyy-MM-dd HH:mm.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pastHitTimestamp,
                        onValueChange = { pastHitTimestamp = it },
                        label = { Text("Date and time") },
                        singleLine = true
                    )
                    inputError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val timestamp = runCatching {
                        LocalDateTime.parse(pastHitTimestamp.trim(), counterDateFormatter)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
                    when {
                        timestamp == null -> inputError = "Enter a valid date and time"
                        timestamp > System.currentTimeMillis() -> inputError = "Reset time cannot be in the future"
                        else -> {
                            viewModel.recordCounterHit(counter, timestamp)
                            pastHitCounter = null
                        }
                    }
                }) { Text("Save reset") }
            },
            dismissButton = {
                TextButton(onClick = { pastHitCounter = null }) { Text("Cancel") }
            }
        )
    }
}

private fun formatCounterDuration(durationMillis: Long): String =
    DurationFormatter.format(durationMillis)
