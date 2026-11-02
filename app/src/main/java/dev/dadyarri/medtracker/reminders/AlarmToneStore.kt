package dev.dadyarri.medtracker.reminders

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.alarmTonePreferences by preferencesDataStore("alarm_tone")
class AlarmToneStore(private val context: Context) {
    private val key = stringPreferencesKey("uri")
    val tone = context.alarmTonePreferences.data.map { it[key]?.toUri() ?: defaultUri() }
    suspend fun current(): Uri = tone.first()
    suspend fun save(uri: Uri) { context.alarmTonePreferences.edit { it[key] = uri.toString() } }
    companion object {
        fun defaultUri(): Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }
}
