package dev.dadyarri.medtracker.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.measurements.*
import dev.dadyarri.medtracker.pressure.*
import java.time.*
import java.util.UUID

internal val ReminderOccurrence.entryType: JournalType? get() = when (source) {
    "PRESSURE" -> JournalType.PRESSURE
    "WEIGHT" -> JournalType.WEIGHT
    "GLUCOSE" -> JournalType.GLUCOSE
    "BREAKFAST", "LUNCH", "DINNER", "SNACK" -> JournalType.MEAL
    else -> null
}
internal val ReminderOccurrence.icon: Int get() = when (entryType) {
    JournalType.PRESSURE -> R.drawable.ic_blood_pressure
    JournalType.WEIGHT -> R.drawable.ic_weight
    JournalType.GLUCOSE -> R.drawable.ic_glucose
    else -> R.drawable.ic_meal
}
internal val ReminderOccurrence.entryLabel: String get() = when (entryType) {
    JournalType.PRESSURE -> "Записать давление"
    JournalType.WEIGHT -> "Записать вес"
    JournalType.GLUCOSE -> "Записать глюкозу"
    else -> "Записать начало еды"
}
internal val ReminderOccurrence.entryId: String get() =
    UUID.nameUUIDFromBytes("$key/$revision".toByteArray(Charsets.UTF_8)).toString()

internal fun ReminderOccurrence.measurementDraft(now: Long): MeasurementDraft = MeasurementDraft(
    type = if (entryType == JournalType.WEIGHT) MeasurementType.WEIGHT else MeasurementType.GLUCOSE,
    time = PressureDraft.format(now),
    context = PressureContext.forHour(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour).name,
    food = FoodContext.entries.find { it.name == food } ?: FoodContext.NONE,
    meal = MealType.entries.find { it.name == meal },
    mealEventId = mealEventId,
    originalAt = now,
)
internal fun ReminderOccurrence.pressureInitial(now: Long) =
    listOf("", "", "", PressureDraft.format(now),
        PressureContext.forHour(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour).name, "", now.toString())

internal data class ReminderEntryRequest(val key: String?, val revision: String?, val token: String?) {
    fun accepts(row: ReminderOccurrence): Boolean = row.canRecord && row.entryType != null &&
        if (key != null) row.key == key && row.revision == revision && (token == null || row.token == token)
        else token != null && row.token == token && row.status in listOf("RINGING", "MUTED")
    companion object {
        const val REVISION = "reminder_revision"
        fun from(intent: Intent): ReminderEntryRequest? = try {
            val key = intent.getStringExtra(ReminderAlarmController.KEY)
            val revision = intent.getStringExtra(REVISION)
            val token = intent.getStringExtra(dev.dadyarri.medtracker.alarm.AlarmController.TOKEN)
            if (listOfNotNull(key, revision, token).any { it.isBlank() || it.length > 512 } ||
                (key == null && token == null) || (key != null && revision == null)) null
            else ReminderEntryRequest(key, revision, token)
        } catch (_: RuntimeException) { null }
    }
}
internal fun reminderEntryIntent(context: Context, row: ReminderOccurrence): Intent =
    Intent(context, ReminderEntryActivity::class.java)
        .setData(Uri.Builder().scheme("medtracker").authority("entry").appendPath(row.key).appendPath(row.revision).build())
        .putExtra(ReminderAlarmController.KEY, row.key)
        .putExtra(ReminderEntryRequest.REVISION, row.revision)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
