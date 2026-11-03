package dev.dadyarri.medtracker.reminders

import androidx.lifecycle.ViewModel
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.meals.MealEvent
import dev.dadyarri.medtracker.measurements.*
import dev.dadyarri.medtracker.pressure.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class ReminderEntryViewModel(private val db: AlarmDatabase, private val controller: ReminderAlarmController) : ViewModel() {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val meals = db.meals().observe()

    fun savePressure(row: ReminderOccurrence, draft: PressureDraft, done: () -> Unit) {
        draft.error()?.let { error.value = it; return }
        save(row, done) { current ->
            check(current.entryType == JournalType.PRESSURE)
            val now = System.currentTimeMillis()
            val id = current.entryId
            check(db.pressure().insert(PressureRecord(id, draft.systolic.toInt(), draft.diastolic.toInt(),
                draft.pulse.toIntOrNull(), draft.context, draft.comment, draft.instant()!!, now, now)) != -1L)
            id
        }
    }
    fun saveMeasurement(row: ReminderOccurrence, draft: MeasurementDraft, done: () -> Unit) {
        draft.error()?.let { error.value = it; return }
        save(row, done) { current ->
            val now = System.currentTimeMillis()
            val id = current.entryId
            val inserted = if (draft.type == MeasurementType.WEIGHT) {
                check(current.entryType == JournalType.WEIGHT)
                db.measurements().insert(WeightRecord(id, draft.scaled()!!, draft.context, draft.comment, draft.instant()!!, now, now))
            } else {
                check(current.entryType == JournalType.GLUCOSE)
                db.measurements().insert(GlucoseRecord(id, draft.scaled()!!, draft.unit.name, draft.food.name,
                    draft.tag?.name, draft.comment, draft.instant()!!, now, now, draft.meal?.name, draft.mealEventId))
            }
            check(inserted != -1L)
            id
        }
    }
    fun saveMeal(row: ReminderOccurrence, meal: MealEvent, done: () -> Unit) = save(row, done) { current ->
        check(current.entryType == JournalType.MEAL && MealType.entries.any { it.name == meal.type })
        val now = System.currentTimeMillis()
        val id = current.entryId
        check(db.meals().insert(meal.copy(id = id, createdAt = now, updatedAt = now)) != -1L)
        id
    }
    private fun save(row: ReminderOccurrence, done: () -> Unit, write: suspend (ReminderOccurrence) -> String) {
        if (busy.value) return
        busy.value = true; error.value = null
        // Completing a Room transaction can close the Activity before AlarmManager is updated.
        // The application scope deliberately owns the operation through reconciliation.
        controller.scope.launch {
            try {
                controller.saveEntry(row.key, row.revision, write)
                withContext(Dispatchers.Main.immediate) { done() }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { error.value = "Не удалось сохранить запись. Возможно, напоминание уже изменилось." }
            finally { busy.value = false }
        }
    }
}
