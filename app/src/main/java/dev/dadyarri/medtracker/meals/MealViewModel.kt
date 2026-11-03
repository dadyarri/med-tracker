package dev.dadyarri.medtracker.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.measurements.MealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class MealViewModel(private val db: AlarmDatabase,
                    private val reminders: dev.dadyarri.medtracker.reminders.ReminderAlarmController? = null) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val meals = db.meals().observe().catch { error.value = "Не удалось загрузить питание."; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cycle = db.meals().cycle().catch { error.value = "Не удалось загрузить план."; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun start(type: MealType) {
        val now = System.currentTimeMillis()
        save(MealEvent(UUID.randomUUID().toString(), type.name, now, now, now), false) {}
    }
    fun save(meal: MealEvent, editing: Boolean, done: () -> Unit) = execute(done) {
        require(MealType.entries.any { it.name == meal.type })
        if (editing) {
            val old = db.meals().get(meal.id) ?: kotlin.error("Missing meal")
            check(db.meals().update(meal.copy(createdAt = old.createdAt, updatedAt = System.currentTimeMillis())) == 1)
        } else db.meals().insert(meal)
    }
    fun delete(id: String, done: () -> Unit) = execute(done) {
        db.withTransaction { db.meals().unlink(id); db.meals().delete(id) }
    }
    fun setCycle(value: GlucoseCycle) = execute {
        require(value.valid())
        db.meals().saveCycle(value)
    }
    fun toggleCycle() {
        val old = cycle.value ?: GlucoseCycle(startDate = LocalDate.now().toString())
        setCycle(if (old.active) old.copy(active = false) else old.resume(LocalDate.now()))
    }
    private fun execute(done: () -> Unit = {}, operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try {
                if (reminders != null) reminders.changeJournal(operation) else operation()
                done()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Не удалось сохранить изменение. Повторите попытку." }
            finally { busy.value = false }
        }
    }
}
