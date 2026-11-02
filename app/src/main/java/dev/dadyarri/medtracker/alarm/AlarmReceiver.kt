package dev.dadyarri.medtracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val controller = GlobalContext.get().get<AlarmController>()
        controller.scope.launch {
            try {
                when (intent.action) {
                    AlarmController.DELIVER -> intent.getStringExtra(AlarmController.TOKEN)?.let { controller.deliver(it) }
                    AlarmController.MUTE, AlarmController.SNOOZE, AlarmController.CANCEL ->
                        intent.getStringExtra(AlarmController.TOKEN)?.let { controller.action(intent.action!!, it) }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> controller.reconcile(true,
                        if (intent.action == Intent.ACTION_BOOT_COMPLETED) "Перезагрузка" else "Обновление APK")
                    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> controller.reconcile()
                }
            } catch (_: Exception) {
                controller.message.value = "Не удалось обработать сигнал. Откройте приложение и повторите согласование."
            } finally { result.finish() }
        }
    }
}
