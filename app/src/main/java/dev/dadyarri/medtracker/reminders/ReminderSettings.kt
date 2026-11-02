package dev.dadyarri.medtracker.reminders

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dadyarri.medtracker.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit

enum class ReminderKind(val label: String, val icon: Int, val defaultMinute: Int) {
    PRESSURE("Давление", R.drawable.ic_blood_pressure, 480),
    WEIGHT("Вес", R.drawable.ic_weight, 480),
    GLUCOSE("Глюкоза", R.drawable.ic_glucose, 480),
    BREAKFAST("Завтрак", R.drawable.ic_context_morning, 480),
    LUNCH("Обед", R.drawable.ic_context_day, 780),
    DINNER("Ужин", R.drawable.ic_context_evening, 1140),
    SNACK("Перекус", R.drawable.ic_context_fasting, 960),
}
data class ReminderSetting(
    val kind: ReminderKind, val paused: Boolean = true,
    val minute: Int = kind.defaultMinute, val weeks: Int = 1,
    val anchor: LocalDate = LocalDate.now(),
) {
    fun valid() = minute in 0..1439 && weeks in 1..52
    fun next(now: Instant, zone: ZoneId): Instant? {
        require(valid())
        if (paused) return null
        val today = now.atZone(zone).toLocalDate()
        var day = today
        if (kind == ReminderKind.WEIGHT) {
            val period = weeks * 7L
            val elapsed = ChronoUnit.DAYS.between(anchor, today)
            day = if (elapsed < 0) anchor else anchor.plusDays(((elapsed + period - 1) / period) * period)
        }
        var result = day.atTime(minute / 60, minute % 60).atZone(zone).toInstant()
        if (result < now) {
            day = day.plusDays(if (kind == ReminderKind.WEIGHT) weeks * 7L else 1)
            result = day.atTime(minute / 60, minute % 60).atZone(zone).toInstant()
        }
        return result
    }
}
private val Context.reminderPreferences by preferencesDataStore("reminder_settings")
class ReminderSettingsStore(private val context: Context) {
    val settings = context.reminderPreferences.data.map { prefs ->
        ReminderKind.entries.map { kind ->
            val key = kind.name.lowercase()
            ReminderSetting(kind, prefs[booleanPreferencesKey("${key}_paused")] ?: true,
                prefs[intPreferencesKey("${key}_minute")]?.takeIf { it in 0..1439 } ?: kind.defaultMinute,
                prefs[intPreferencesKey("${key}_weeks")]?.takeIf { it in 1..52 } ?: 1,
                prefs[stringPreferencesKey("${key}_anchor")]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now())
        }
    }
    suspend fun save(value: ReminderSetting) {
        require(value.valid())
        val key = value.kind.name.lowercase()
        context.reminderPreferences.edit {
            it[booleanPreferencesKey("${key}_paused")] = value.paused
            it[intPreferencesKey("${key}_minute")] = value.minute
            it[intPreferencesKey("${key}_weeks")] = value.weeks
            it[stringPreferencesKey("${key}_anchor")] = value.anchor.toString()
        }
    }
}
class ReminderSettingsViewModel(private val store: ReminderSettingsStore) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val settings = store.settings.catch {
        error.value = "Не удалось загрузить настройки напоминаний."
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(value: ReminderSetting) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { store.save(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Не удалось сохранить настройки. Повторите попытку." }
            finally { busy.value = false }
        }
    }
}
