package dev.dadyarri.medtracker.reminders

import android.content.*
import dev.dadyarri.medtracker.alarm.AlarmController
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val controller = GlobalContext.get().get<ReminderAlarmController>()
        controller.scope.launch {
            try {
                if (intent.action == ReminderAlarmController.DELIVER)
                    controller.deliver(intent.getLongExtra(ReminderAlarmController.AT, -1))
                else intent.getStringExtra(AlarmController.TOKEN)?.let { token ->
                    controller.action(intent.action.orEmpty(), token, intent.getStringExtra(ReminderAlarmController.KEY))
                }
            } catch (_: Exception) { controller.message.value = "Не удалось обработать напоминание. Откройте приложение." }
            finally { pending.finish() }
        }
    }
}
