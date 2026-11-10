package com.example.medtrackv1.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.medtrackv1.data.model.Counter
import com.example.medtrackv1.data.model.CounterConcentrationSnapshot
import com.example.medtrackv1.data.model.CounterHit
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.repository.CounterRepository
import com.example.medtrackv1.data.repository.DoseLogRepository
import com.example.medtrackv1.data.repository.MedicationRepository
import com.example.medtrackv1.data.util.DataExporterImporter
import com.example.medtrackv1.domain.pk.PkEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MedicationViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseLogRepository: DoseLogRepository,
    private val counterRepository: CounterRepository
) : ViewModel() {
    private val counterHitMutex = Mutex()

    val medications: StateFlow<List<Medication>> = medicationRepository.allMedications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val doseLogs: StateFlow<List<DoseLog>> = doseLogRepository.allDoseLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val counters: StateFlow<List<Counter>> = counterRepository.counters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val counterHits: StateFlow<List<CounterHit>> = counterRepository.counterHits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMedicationFilterId = MutableStateFlow<Long?>(null)
    val selectedMedicationFilterId: StateFlow<Long?> = _selectedMedicationFilterId.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val filteredDoseLogs: StateFlow<List<DoseLog>> = combine(
        doseLogs,
        _selectedMedicationFilterId
    ) { logs, filterId ->
        if (filterId == null) {
            logs
        } else {
            logs.filter { it.medicationId == filterId }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilterMedicationId(medicationId: Long?) {
        _selectedMedicationFilterId.value = medicationId
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addCounter(name: String) {
        val cleanedName = name.trim()
        if (cleanedName.isEmpty()) {
            _userMessage.value = "Enter a tracker name"
            return
        }
        viewModelScope.launch {
            try {
                counterRepository.insertCounter(Counter(name = cleanedName, createdAt = System.currentTimeMillis()))
                _userMessage.value = "Tracker '$cleanedName' added"
            } catch (e: Exception) {
                _userMessage.value = "Failed to add tracker: ${e.message}"
            }
        }
    }

    fun recordCounterHit(counter: Counter, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            counterHitMutex.withLock {
                try {
                    val existingHits = counterRepository.getHitsForCounter(counter.id)
                    val previous = existingHits.lastOrNull { it.timestamp < timestamp }
                    val initialElapsed = (timestamp - (previous?.timestamp ?: counter.createdAt)).coerceAtLeast(0L)
                    val snapshots = medications.value.map { medication ->
                        val logs = doseLogs.value
                            .asSequence()
                            .filter { it.medicationId == medication.id && it.timestamp <= timestamp }
                            .toList()
                        val concentration = if (logs.isEmpty()) {
                            null
                        } else {
                            PkEngine.calculateConcentrationAt(medication, logs, timestamp)
                                .takeIf { it.isFinite() }
                        }
                        CounterConcentrationSnapshot(
                            medicationId = medication.id,
                            medicationName = medication.name,
                            unit = medication.unit,
                            concentration = concentration
                        )
                    }
                    counterRepository.insertCounterHit(
                        CounterHit(
                            counterId = counter.id,
                            counterName = counter.name,
                            timestamp = timestamp,
                            elapsedMillis = initialElapsed,
                            concentrationsJson = DataExporterImporter.encodeCounterConcentrationSnapshots(snapshots)
                        )
                    )
                    recalculateCounterHitDurations(counter.id, counter.createdAt)
                    _userMessage.value = "Logged reset for ${counter.name}"
                } catch (e: Exception) {
                    _userMessage.value = "Failed to log tracker reset: ${e.message}"
                }
            }
        }
    }

    fun deleteCounterHit(hit: CounterHit) {
        viewModelScope.launch {
            counterHitMutex.withLock {
                try {
                    val counter = counterRepository.getCounterById(hit.counterId)
                    counterRepository.deleteCounterHit(hit.id)
                    if (counter != null) recalculateCounterHitDurations(counter.id, counter.createdAt)
                    _userMessage.value = "Tracker reset deleted and timer history recalculated"
                } catch (e: Exception) {
                    _userMessage.value = "Failed to delete tracker reset: ${e.message}"
                }
            }
        }
    }

    fun updateCounterName(counter: Counter, name: String) {
        val cleanedName = name.trim()
        if (cleanedName.isEmpty()) {
            _userMessage.value = "Enter a tracker name"
            return
        }
        viewModelScope.launch {
            try {
                counterRepository.updateCounter(counter.copy(name = cleanedName))
                counterRepository.renameCounterHits(counter.id, cleanedName)
                _userMessage.value = "Tracker renamed to '$cleanedName'"
            } catch (e: Exception) {
                _userMessage.value = "Failed to rename tracker: ${e.message}"
            }
        }
    }

    fun deleteCounter(counter: Counter) {
        viewModelScope.launch {
            counterHitMutex.withLock {
                try {
                    counterRepository.deleteCounter(counter.id)
                    _userMessage.value = "Tracker '${counter.name}' and its resets deleted"
                } catch (e: Exception) {
                    _userMessage.value = "Failed to delete tracker: ${e.message}"
                }
            }
        }
    }

    private suspend fun recalculateCounterHitDurations(counterId: Long, createdAt: Long) {
        var previousTimestamp = createdAt
        counterRepository.getHitsForCounter(counterId)
            .sortedWith(compareBy<CounterHit> { it.timestamp }.thenBy { it.id })
            .forEach { hit ->
                val elapsed = (hit.timestamp - previousTimestamp).coerceAtLeast(0L)
                if (hit.elapsedMillis != elapsed) counterRepository.updateCounterHit(hit.copy(elapsedMillis = elapsed))
                previousTimestamp = hit.timestamp
            }
    }

    fun addMedication(medication: Medication) {
        viewModelScope.launch {
            try {
                val nextSortOrder = (medications.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
                medicationRepository.insertMedication(medication.copy(sortOrder = nextSortOrder))
                _userMessage.value = "Medication '${medication.name}' saved"
            } catch (e: Exception) {
                _userMessage.value = "Failed to save medication: ${e.message}"
            }
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            try {
                medicationRepository.updateMedication(medication)
                _userMessage.value = "Medication '${medication.name}' updated"
            } catch (e: Exception) {
                _userMessage.value = "Failed to update medication: ${e.message}"
            }
        }
    }

    fun moveMedication(medicationId: Long, direction: Int) {
        val ordered = medications.value.toMutableList()
        val currentIndex = ordered.indexOfFirst { it.id == medicationId }
        if (currentIndex < 0) return
        val destinationIndex = (currentIndex + direction).coerceIn(0, ordered.lastIndex)
        if (currentIndex == destinationIndex) return

        val moving = ordered[currentIndex]
        ordered[currentIndex] = ordered[destinationIndex]
        ordered[destinationIndex] = moving

        viewModelScope.launch {
            try {
                medicationRepository.updateMedications(
                    ordered.mapIndexed { index, medication -> medication.copy(sortOrder = index) }
                )
                _userMessage.value = "Medication order updated"
            } catch (e: Exception) {
                _userMessage.value = "Failed to update medication order: ${e.message}"
            }
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            try {
                medicationRepository.deleteMedication(medication)
                _userMessage.value = "Medication '${medication.name}' deleted"
            } catch (e: Exception) {
                _userMessage.value = "Failed to delete medication: ${e.message}"
            }
        }
    }

    fun takeDoseNow(medication: Medication, doseAmount: Double = medication.doseAmount) {
        val now = System.currentTimeMillis()
        logCustomDose(medication.id, doseAmount, now, medication.name)
    }

    fun takeGroupDose(groupMedications: List<Medication>) {
        if (groupMedications.isEmpty()) return
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            try {
                groupMedications.forEach { medication ->
                    doseLogRepository.insertDoseLog(
                        DoseLog(
                            medicationId = medication.id,
                            timestamp = timestamp,
                            doseAmount = medication.doseAmount * medication.groupDoseMultiplier.coerceAtLeast(1)
                        )
                    )
                }
                _userMessage.value = "Logged group doses for ${groupMedications.size} medications"
            } catch (e: Exception) {
                _userMessage.value = "Failed to log group doses: ${e.message}"
            }
        }
    }

    fun logCustomDose(medicationId: Long, doseAmount: Double, timestamp: Long, medName: String? = null) {
        viewModelScope.launch {
            try {
                val doseLog = DoseLog(
                    medicationId = medicationId,
                    timestamp = timestamp,
                    doseAmount = doseAmount
                )
                doseLogRepository.insertDoseLog(doseLog)
                val displayName = medName ?: medications.value.find { it.id == medicationId }?.name ?: "Medication"
                _userMessage.value = "Dose logged for $displayName ($doseAmount)"
            } catch (e: Exception) {
                _userMessage.value = "Failed to log dose: ${e.message}"
            }
        }
    }

    fun deleteDoseLog(doseLog: DoseLog) {
        viewModelScope.launch {
            try {
                doseLogRepository.deleteDoseLog(doseLog)
                _userMessage.value = "Dose log deleted"
            } catch (e: Exception) {
                _userMessage.value = "Failed to delete dose log: ${e.message}"
            }
        }
    }

    fun calculateConcentrationAtDoseTime(doseLog: DoseLog): Double {
        val med = medications.value.find { it.id == doseLog.medicationId } ?: return 0.0
        val medDoseLogs = doseLogs.value.filter { it.medicationId == med.id }
        return PkEngine.calculateConcentrationAt(med, medDoseLogs, doseLog.timestamp)
    }

    fun exportCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    DataExporterImporter.exportCsvToStream(medications.value, doseLogs.value, outputStream)
                }
                _userMessage.value = "CSV exported successfully (${doseLogs.value.size} dose records)"
            } catch (e: Exception) {
                _userMessage.value = "CSV Export failed: ${e.message}"
            }
        }
    }

    fun exportCounterCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val allCounters = counterRepository.getCountersSync()
                val allHits = counterRepository.getCounterHitsSync()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    DataExporterImporter.exportCounterCsvToStream(allCounters, allHits, outputStream)
                }
                _userMessage.value = "Tracker CSV exported successfully (${allHits.size} resets)"
            } catch (e: Exception) {
                _userMessage.value = "Tracker CSV export failed: ${e.message}"
            }
        }
    }

    fun exportMedicationCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    DataExporterImporter.exportMedicationCsvToStream(medications.value, output)
                }
                _userMessage.value = "Medication CSV exported successfully"
            } catch (e: Exception) {
                _userMessage.value = "Medication CSV export failed: ${e.message}"
            }
        }
    }

    fun importMedicationCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val csv = contentResolver.openInputStream(uri)?.use(DataExporterImporter::importCsvFromStream).orEmpty()
                val imported = DataExporterImporter.importMedicationCsv(csv)
                val current = medicationRepository.allMedications.first()
                val existingByName = current.associateBy { it.name.trim().lowercase() }
                val merged = linkedMapOf<String, Medication>()
                imported.forEach { row ->
                    val key = row.name.trim().lowercase()
                    val existing = merged[key] ?: existingByName[key]
                    merged[key] = if (existing == null) row.copy(id = 0, sortOrder = current.size + merged.size)
                    else row.copy(id = existing.id, sortOrder = existing.sortOrder)
                }
                val updates = merged.values.filter { it.id != 0L && existingByName.containsKey(it.name.trim().lowercase()) }
                val additions = merged.values.filter { it.id == 0L }
                if (updates.isNotEmpty()) medicationRepository.updateMedications(updates)
                if (additions.isNotEmpty()) medicationRepository.insertMedications(additions)
                _userMessage.value = "Medication CSV imported: ${additions.size} added, ${updates.size} merged"
            } catch (e: Exception) {
                _userMessage.value = "Medication CSV import failed: ${e.message}"
            }
        }
    }

    fun importCounterCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            counterHitMutex.withLock {
                try {
                    val csv = contentResolver.openInputStream(uri)?.use(DataExporterImporter::importCsvFromStream).orEmpty()
                    val imported = DataExporterImporter.importCounterCsv(csv)
                    val allCounters = counterRepository.getCountersSync().toMutableList()
                    val currentMeds = medicationRepository.allMedications.first()
                    val medByName = currentMeds.associateBy { it.name.trim().lowercase() }
                    val addedCounterNames = mutableSetOf<String>()
                    val addedHitKeys = mutableSetOf<String>()
                    val touched = mutableSetOf<Long>()
                    imported.groupBy { it.counterName.trim().lowercase() }.values.forEach { group ->
                        val firstRow = group.first()
                        val key = firstRow.counterName.trim().lowercase()
                        var counter = allCounters.firstOrNull { it.name.trim().lowercase() == key }
                        if (counter == null) {
                            val start = group.minOf { (it.timestamp - it.elapsedMillis).coerceAtLeast(0L) }
                            val id = counterRepository.insertCounter(Counter(name = firstRow.counterName.trim(), createdAt = start))
                            counter = Counter(id = id, name = firstRow.counterName.trim(), createdAt = start)
                            allCounters += counter
                            addedCounterNames += key
                        }
                        val existingHits = counterRepository.getHitsForCounter(counter.id).associateBy { it.timestamp }.toMutableMap()
                        group.sortedBy { it.timestamp }.forEach { importedHit ->
                            val snapshots = importedHit.snapshots.map { snapshot ->
                                val med = medByName[snapshot.medicationName.trim().lowercase()]
                                snapshot.copy(medicationId = med?.id ?: -(kotlin.math.abs(snapshot.medicationName.trim().lowercase().hashCode().toLong()) + 1L))
                            }
                            val prior = existingHits[ importedHit.timestamp ]
                            if (prior == null) {
                                val id = counterRepository.insertCounterHit(CounterHit(
                                    counterId = counter.id, counterName = counter.name, timestamp = importedHit.timestamp,
                                    elapsedMillis = importedHit.elapsedMillis,
                                    concentrationsJson = DataExporterImporter.encodeCounterConcentrationSnapshots(snapshots)
                                ))
                                val created = CounterHit(id, counter.id, counter.name, importedHit.timestamp, importedHit.elapsedMillis,
                                    DataExporterImporter.encodeCounterConcentrationSnapshots(snapshots))
                                existingHits[importedHit.timestamp] = created
                                addedHitKeys += "${counter.id}:${importedHit.timestamp}"
                            } else {
                                val combined = (DataExporterImporter.decodeCounterConcentrationSnapshots(prior.concentrationsJson) + snapshots)
                                    .groupBy { it.medicationId }.map { (_, values) -> values.lastOrNull { it.concentration != null } ?: values.last() }
                                val mergedHit = prior.copy(concentrationsJson = DataExporterImporter.encodeCounterConcentrationSnapshots(combined))
                                counterRepository.updateCounterHit(mergedHit)
                                existingHits[importedHit.timestamp] = mergedHit
                            }
                        }
                        touched += counter.id
                    }
                    touched.forEach { id ->
                        val counter = allCounters.first { it.id == id }
                        var previous = counter.createdAt
                        counterRepository.getHitsForCounter(id).sortedBy { it.timestamp }.forEach { hit ->
                            val elapsed = (hit.timestamp - previous).coerceAtLeast(0L)
                            if (hit.elapsedMillis != elapsed) counterRepository.updateCounterHit(hit.copy(elapsedMillis = elapsed))
                            previous = hit.timestamp
                        }
                    }
                    _userMessage.value = "Tracker CSV imported: ${addedCounterNames.size} trackers added, ${addedHitKeys.size} resets added; duplicate entries merged"
                } catch (e: Exception) {
                    _userMessage.value = "Tracker CSV import failed: ${e.message}"
                }
            }
        }
    }

    fun exportJson(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    DataExporterImporter.exportJsonToStream(medications.value, doseLogs.value, outputStream)
                }
                _userMessage.value = "JSON Backup exported successfully"
            } catch (e: Exception) {
                _userMessage.value = "JSON Export failed: ${e.message}"
            }
        }
    }

    fun importJson(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val (importedMeds, importedDoses) = contentResolver.openInputStream(uri)?.use { inputStream ->
                    DataExporterImporter.importJsonFromStream(inputStream)
                } ?: Pair(emptyList(), emptyList())

                if (importedMeds.isNotEmpty()) {
                    medicationRepository.insertMedications(importedMeds)
                }
                if (importedDoses.isNotEmpty()) {
                    doseLogRepository.insertDoseLogs(importedDoses)
                }
                _userMessage.value = "Backup imported: ${importedMeds.size} medications, ${importedDoses.size} doses"
            } catch (e: Exception) {
                _userMessage.value = "JSON Import failed: ${e.message}"
            }
        }
    }

    companion object {
        fun Factory(
            medicationRepository: MedicationRepository,
            doseLogRepository: DoseLogRepository,
            counterRepository: CounterRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MedicationViewModel(medicationRepository, doseLogRepository, counterRepository) as T
            }
        }
    }
}
