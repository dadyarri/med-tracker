package dev.dadyarri.medtracker.measurements

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dadyarri.medtracker.pressure.PressureDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.measurementPreferences by preferencesDataStore("measurements")
class MeasurementSettings(private val context: Context) {
    private val unitKey = stringPreferencesKey("glucose_unit")
    val unit = context.measurementPreferences.data.map { prefs ->
        GlucoseUnit.entries.find { it.name == prefs[unitKey] } ?: GlucoseUnit.MMOL
    }
    suspend fun setUnit(unit: GlucoseUnit) { context.measurementPreferences.edit { it[unitKey] = unit.name } }
}
class MeasurementViewModel(private val dao: MeasurementDao, private val settings: MeasurementSettings? = null) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val weights = dao.weights().catch { error.value = "Не удалось загрузить вес."; emit(emptyList()) }
        .map<List<WeightRecord>, List<WeightRecord>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val glucose = dao.glucose().catch { error.value = "Не удалось загрузить глюкозу."; emit(emptyList()) }
        .map<List<GlucoseRecord>, List<GlucoseRecord>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val unit = (settings?.unit ?: flowOf(GlucoseUnit.MMOL)).catch {
        error.value = "Не удалось загрузить единицы измерения."; emit(GlucoseUnit.MMOL)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlucoseUnit.MMOL)

    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch {
            try { settings?.setUnit(unit) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Не удалось сохранить единицы измерения." }
        }
    }
    fun save(id: String, editing: Boolean, draft: MeasurementDraft, done: () -> Unit) {
        draft.error()?.let { error.value = it; return }
        execute(done) {
            val now = System.currentTimeMillis()
            if (draft.type == MeasurementType.WEIGHT) {
                val old = if (editing) dao.weight(id) ?: kotlin.error("Missing record") else null
                val at = old?.measuredAt?.takeIf { PressureDraft.format(it) == draft.time } ?: draft.instant()!!
                val record = WeightRecord(id, draft.scaled()!!, draft.context, draft.comment, at, old?.createdAt ?: now, now)
                if (editing) check(dao.update(record) == 1) else dao.insert(record)
            } else {
                val old = if (editing) dao.glucose(id) ?: kotlin.error("Missing record") else null
                val at = old?.measuredAt?.takeIf { PressureDraft.format(it) == draft.time } ?: draft.instant()!!
                val unchangedLegacyValue = old != null && old.unit == GlucoseUnit.MG.name &&
                    draft.unit == GlucoseUnit.MMOL && old.valueIn(GlucoseUnit.MMOL)
                        .setScale(2, java.math.RoundingMode.HALF_UP).compareTo(java.math.BigDecimal.valueOf(draft.scaled()!!.toLong(), 2)) == 0
                val record = GlucoseRecord(id, if (unchangedLegacyValue) old.hundredths else draft.scaled()!!,
                    if (unchangedLegacyValue) old.unit else draft.unit.name, draft.food.name,
                    draft.tag?.name, draft.comment, at, old?.createdAt ?: now, now, draft.meal?.name, draft.mealEventId)
                if (editing) check(dao.update(record) == 1) else dao.insert(record)
            }
        }
    }
    fun delete(id: String, type: MeasurementType, done: () -> Unit) = execute(done) {
        if (type == MeasurementType.WEIGHT) dao.deleteWeight(id) else dao.deleteGlucose(id)
    }
    private fun execute(done: () -> Unit, operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try { operation(); done() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Не удалось сохранить изменение. Повторите попытку." }
            finally { busy.value = false }
        }
    }
}
