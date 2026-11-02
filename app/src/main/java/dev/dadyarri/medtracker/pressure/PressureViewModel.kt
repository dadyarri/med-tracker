package dev.dadyarri.medtracker.pressure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PressureViewModel(private val dao: PressureDao) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val records = dao.observe().catch {
        error.value = "Не удалось загрузить историю. Закройте и снова откройте приложение."
        emit(emptyList())
    }.map<List<PressureRecord>, List<PressureRecord>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val busy = MutableStateFlow(false)

    fun save(id: String, editing: Boolean, draft: PressureDraft, done: () -> Unit) {
        val validation = draft.error()
        if (validation != null) { error.value = validation; return }
        execute(done) {
            val old = if (editing) dao.get(id) ?: kotlin.error("Missing record") else null
            val now = System.currentTimeMillis()
            val record = PressureRecord(id, draft.systolic.toInt(), draft.diastolic.toInt(),
                draft.pulse.toIntOrNull(), draft.context, draft.comment,
                if (old != null && PressureDraft.format(old.measuredAt) == draft.time) old.measuredAt else draft.instant()!!,
                old?.createdAt ?: now, now)
            if (editing) check(dao.update(record) == 1) else dao.insert(record)
        }
    }

    fun delete(id: String, done: () -> Unit) = execute(done) { dao.delete(id) }

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
