package dev.dadyarri.medtracker.reminders

import android.app.*
import android.content.*
import androidx.room.withTransaction
import dev.dadyarri.medtracker.MainActivity
import dev.dadyarri.medtracker.alarm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.UUID

class ReminderAlarmController(private val context: Context, private val db: AlarmDatabase,
                              private val store: ReminderSettingsStore) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val message = MutableStateFlow<String?>(null)
    val dao = db.reminders()
    private val mutex = Mutex()
    private val manager = context.getSystemService(AlarmManager::class.java)
    private var observation: Job? = null
    private fun now() = System.currentTimeMillis()
    private fun token() = UUID.randomUUID().toString()
    fun start() {
        if (observation != null) return
        observation = scope.launch {
            combine(store.settings, db.meals().cycle(), db.meals().observe()) { _, _, _ -> Unit }
                .collect { try { reconcile() } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message.value = "Не удалось обновить будильники. Вернитесь в приложение." } }
        }
    }
    private fun pending(at: Long) = PendingIntent.getBroadcast(context, 72,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(DELIVER).putExtra(AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private suspend fun refreshRows(recovery: Boolean = false): List<ReminderOccurrence> {
        val desired = ReminderSchedule.desired(store.settings.first(), db.meals().cycle().first(),
            db.meals().observe().first(), Instant.ofEpochMilli(now()), ZoneId.systemDefault())
        val rows = ReminderSchedule.merge(dao.all(), desired, now(), recovery)
        dao.save(rows)
        return rows
    }
    private suspend fun schedule(rows: List<ReminderOccurrence>) {
        manager.cancel(pending(0))
        val at = rows.nextWake() ?: run {
            message.value = null; return
        }
        val error = try {
            if (!manager.canScheduleExactAlarms()) "Разрешите точные будильники в настройках Android."
            else {
                val show = PendingIntent.getActivity(context, 72, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(at))
                null
            }
        } catch (_: SecurityException) { "Доступ к точным будильникам отозван." }
          catch (_: RuntimeException) { "Не удалось зарегистрировать будильники. Вернитесь в приложение." }
        message.value = error
        dao.save(rows.map { it.copy(diagnostic = error) })
    }
    suspend fun reconcile(recovery: Boolean = false) = mutex.withLock {
        val rows = db.withTransaction { refreshRows(recovery) }
        schedule(rows)
        updatePresentation(rows)
    }
    suspend fun deliver(expectedAt: Long) = mutex.withLock {
        var rows = db.withTransaction { refreshRows() }
        val earliest = rows.nextWake()
        if (earliest == null || expectedAt != earliest || now() < earliest) {
            schedule(rows); updatePresentation(rows); return@withLock
        }
        val groupToken = token()
        var delivered = false
        rows = rows.map { row ->
            if (row.active && row.nextAt?.let { it <= now() } == true) {
                val plan = AlarmPolicy.deliver(row.plan(), row.token, now(), groupToken)
                if (plan?.status == AlarmStatus.RINGING) delivered = true
                if (plan != null) row.withPlan(plan) else row
            } else row
        }
        if (delivered) rows = rows.map { row ->
            if (row.status in listOf("RINGING", "MUTED") && row.endAt > now()) row.copy(token = groupToken) else row
        }
        dao.save(rows)
        schedule(rows)
        if (delivered) {
            context.stopService(Intent(context, ReminderSoundService::class.java))
            ReminderNotifications(context).cancel()
            try {
                context.startForegroundService(Intent(context, ReminderSoundService::class.java)
                    .putExtra(AlarmController.TOKEN, groupToken))
            } catch (_: RuntimeException) {
                message.value = "Система запретила звук. Проверьте ограничения работы в фоне."
                ReminderNotifications(context).show(rows.presentation(groupToken), true)
            }
        } else updatePresentation(rows)
    }
    /** The record and its explicit task link commit together; scheduling outlives the editor. */
    suspend fun saveEntry(key: String, revision: String, write: suspend (ReminderOccurrence) -> String) = mutex.withLock {
        db.withTransaction {
            val row = dao.all().find { it.key == key && it.revision == revision && it.canRecord }
                ?: error("Reminder no longer available")
            val recordId = write(row)
            dao.save(listOf(row.copy(status = "COMPLETED", nextAt = null, recordId = recordId)))
        }
        val rows = refreshRows()
        schedule(rows)
        updatePresentation(rows)
    }
    suspend fun ringing(token: String): List<ReminderOccurrence> = mutex.withLock {
        dao.all().presentation(token).takeIf { rows -> rows.any { it.status == "RINGING" } }.orEmpty()
    }
    suspend fun action(action: String, expectedToken: String, key: String? = null) = mutex.withLock {
        val rows = dao.all()
        if (rows.presentation(expectedToken).isEmpty()) return@withLock
        val updated = rows.map { row ->
            if (row.token != expectedToken || row.status !in listOf("RINGING", "MUTED") ||
                (key != null && row.key != key)) row
            else when (action) {
                AlarmController.MUTE -> row.copy(status = "MUTED")
                AlarmController.CANCEL -> row.copy(status = "CANCELLED", nextAt = null)
                AlarmController.SNOOZE -> AlarmPolicy.snooze(row.plan(), now(), token())?.let(row::withPlan) ?: row
                else -> row
            }
        }
        dao.save(updated)
        schedule(updated)
        updatePresentation(updated)
    }
    private fun updatePresentation(rows: List<ReminderOccurrence>) {
        val shown = rows.filter { it.status in listOf("RINGING", "MUTED") && it.endAt > now() }
        if (shown.none { it.status == "RINGING" }) context.stopService(Intent(context, ReminderSoundService::class.java))
        if (shown.isEmpty()) ReminderNotifications(context).cancel()
        else if (shown.none { it.status == "RINGING" } ||
            context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == ReminderNotifications.ID })
            ReminderNotifications(context).show(shown, false)
    }
    companion object {
        const val DELIVER = "dev.dadyarri.medtracker.REMINDER_DELIVER"
        const val AT = "reminder_at"
        const val KEY = "reminder_key"
    }
}
internal fun List<ReminderOccurrence>.nextWake(): Long? = filter { it.active }.flatMap { row ->
    listOfNotNull(row.nextAt, row.endAt.takeIf { row.status in listOf("RINGING", "MUTED") })
}.minOrNull()

internal fun List<ReminderOccurrence>.presentation(token: String) = filter {
    it.token == token && it.status in listOf("RINGING", "MUTED") && it.endAt > System.currentTimeMillis()
}
