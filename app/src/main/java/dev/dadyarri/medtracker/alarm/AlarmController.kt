package dev.dadyarri.medtracker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AlarmController(private val context: Context, private val db: AlarmDatabase) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val message = MutableStateFlow<String?>(null)
    val dao = db.alarms()
    private val mutex = Mutex()
    private val manager = context.getSystemService(AlarmManager::class.java)
    private fun token() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()

    private fun pending(planToken: String) = PendingIntent.getBroadcast(context, 1,
        Intent(context, AlarmReceiver::class.java).setAction(DELIVER).putExtra(TOKEN, planToken),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelSystem() { manager.cancel(pending("")) }
    private fun stopSound() { context.stopService(Intent(context, AlarmSoundService::class.java)) }
    private fun clearPresentation() {
        stopSound()
        AlarmNotifications(context).cancel()
    }

    private suspend fun save(plan: AlarmPlan, event: String, diagnostic: String? = null) {
        db.withTransaction {
            dao.save(plan.record(diagnostic))
            dao.event(AlarmEvent(at = now(), description = event))
        }
    }

    /** Failures remain visible in Room; scheduling does not roll back saved intent. */
    private suspend fun scheduleSystem(plan: AlarmPlan) {
        cancelSystem()
        if (plan.nextAt == null) return
        val error = try {
            if (!manager.canScheduleExactAlarms()) "Нет доступа к точным будильникам. Разрешите и вернитесь в приложение."
            else {
                val show = PendingIntent.getActivity(context, 1, Intent(context, dev.dadyarri.medtracker.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(plan.nextAt, show), pending(plan.token))
                null
            }
        } catch (_: SecurityException) { "Система отозвала доступ к точным будильникам." }
          catch (_: IllegalStateException) { "Система не смогла поставить будильник. Повторите согласование." }
        dao.save(plan.record(error))
        if (error != null) message.value = error
    }

    suspend fun create(delay: Int, sound: Boolean, repeat: Int, window: Int, snooze: Int) = mutex.withLock {
        val plan = AlarmPolicy.create(now(), delay, sound, token(), repeat, window, snooze)
        clearPresentation()
        save(plan, "Назначен ${if (sound) "звуковой" else "тихий"} тест через $delay с (предыдущий заменён)")
        scheduleSystem(plan)
        message.value = dao.current()?.diagnostic ?: "Тестовый сигнал назначен"
    }

    suspend fun reconcile(recovery: Boolean = false, reason: String = "Возврат в приложение") = mutex.withLock {
        val row = dao.current() ?: return@withLock
        val previous = row.plan()
        val plan = if (recovery || now() >= previous.endAt ||
            (previous.nextAt?.let { it < now() - AlarmPolicy.DELIVERY_GRACE_MS } == true))
            AlarmPolicy.recover(previous, now(), token()) else previous
        if (plan != previous) {
            clearPresentation()
            save(plan, "$reason: просроченные сигналы не воспроизводятся")
        } else if (recovery) {
            dao.event(AlarmEvent(at = now(), description = "$reason: восстановление будущего сигнала"))
        }
        scheduleSystem(plan)
    }

    suspend fun deliver(deliveryToken: String) = mutex.withLock {
        val row = dao.current() ?: return@withLock
        val plan = AlarmPolicy.deliver(row.plan(), deliveryToken, now(), token()) ?: return@withLock
        clearPresentation()
        save(plan, if (plan.status == AlarmStatus.EXPIRED) "Просроченная доставка: звук не запущен" else "Сигнал доставлен receiver-у")
        scheduleSystem(plan)
        if (plan.status != AlarmStatus.RINGING) return@withLock
        val notifications = AlarmNotifications(context)
        if (plan.sound) {
            try {
                context.startForegroundService(Intent(context, AlarmSoundService::class.java).putExtra(TOKEN, plan.token))
            } catch (_: RuntimeException) {
                dao.save(plan.record("Система запретила запуск звука. Проверьте разрешения и ограничения батареи."))
                notifications.show(plan, allowFullscreen = true)
            }
        } else notifications.show(plan, allowFullscreen = false)
    }

    suspend fun action(action: String, expectedToken: String? = null) = mutex.withLock {
        val row = dao.current() ?: return@withLock
        val plan = row.plan()
        if (expectedToken != null && expectedToken != plan.token) return@withLock
        when (action) {
            CANCEL -> {
                cancelSystem(); clearPresentation()
                save(plan.copy(token = token(), nextAt = null, status = AlarmStatus.CANCELLED), "Тест отменён")
            }
            MUTE -> if (plan.status == AlarmStatus.RINGING) {
                stopSound()
                val muted = plan.copy(status = AlarmStatus.MUTED)
                save(muted, "Звук отключён; тест не подтверждён, автоматический повтор сохранён", row.diagnostic)
                AlarmNotifications(context).show(muted, allowFullscreen = false)
            }
            SNOOZE -> {
                val snoozed = AlarmPolicy.snooze(plan, now(), token())
                if (snoozed == null) message.value = "Отложение недоступно: максимум 3 раза и только внутри окна повторов"
                else {
                    clearPresentation()
                    save(snoozed, "Отложено на ${plan.snoozeMinutes} мин (${snoozed.snoozes}/3)")
                    scheduleSystem(snoozed)
                }
            }
        }
    }

    suspend fun ringingPlan(expectedToken: String): AlarmPlan? = mutex.withLock {
        dao.current()?.takeIf { it.token == expectedToken && it.status == AlarmStatus.RINGING.name && it.sound && now() < it.endAt }?.plan()
    }

    suspend fun audioFailure(expectedToken: String) = mutex.withLock {
        val row = dao.current() ?: return@withLock
        if (row.token == expectedToken) dao.save(row.copy(diagnostic = "Не удалось воспроизвести звук. Проверьте громкость будильников и системные настройки."))
    }

    companion object {
        const val TOKEN = "alarm_token"
        const val DELIVER = "dev.dadyarri.medtracker.DELIVER"
        const val MUTE = "dev.dadyarri.medtracker.MUTE"
        const val SNOOZE = "dev.dadyarri.medtracker.SNOOZE"
        const val CANCEL = "dev.dadyarri.medtracker.CANCEL"
    }
}
