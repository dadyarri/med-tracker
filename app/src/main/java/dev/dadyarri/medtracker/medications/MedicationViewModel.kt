package dev.dadyarri.medtracker.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MedicationViewModel(db: AlarmDatabase, private val service: MedicationService,
                          store: dev.dadyarri.medtracker.reminders.ReminderSettingsStore) : ViewModel() {
    val meals = db.meals().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mealSettings = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val data = combine(db.medications().medications(), db.medications().regimens(),
        db.medications().versions(), db.medications().intakes()) { drugs, regimens, versions, intakes ->
        MedicationData(drugs, regimens, versions, intakes)
    }.catch { error.value = "Не удалось загрузить лекарства."; emit(MedicationData()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(value: Medication, done: () -> Unit) = execute(done) { service.save(value) }
    fun archive(value: Medication, done: () -> Unit) = execute(done) { service.archive(value.id, !value.archived) }
    fun prescribe(regimen: MedicationRegimen, version: RegimenVersion, done: () -> Unit) =
        execute(done) { service.prescribe(regimen, version) }
    fun revise(version: RegimenVersion, done: () -> Unit) = execute(done) { service.revise(version) }
    fun finish(id: String, done: () -> Unit) = execute(done) { service.finish(id) }
    fun intake(value: MedicationIntake, editing: Boolean, done: () -> Unit) =
        execute(done) { service.saveIntake(value, editing) }
    fun deleteIntake(id: String, done: () -> Unit) = execute(done) { service.deleteIntake(id) }
    private fun execute(done: () -> Unit, operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { operation(); done() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Не удалось сохранить. Проверьте дозу, единицы и даты." }
            finally { busy.value = false }
        }
    }
}
