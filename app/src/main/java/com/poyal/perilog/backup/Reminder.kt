package com.poyal.perilog.backup

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import com.poyal.perilog.*
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.dayComplete
import com.poyal.perilog.domain.visibleRecords
import kotlinx.coroutines.*
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL="journal"
    fun ensureChannel(context:Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"오늘의 기록",NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun allowed(context:Context):Boolean {
        val manager=context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun settingsIntent(context:Context):Intent = if(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,CHANNEL)
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
    private fun pending(context: Context) = PendingIntent.getBroadcast(context,41,Intent(context,ReminderReceiver::class.java).setAction("com.poyal.perilog.REMIND"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context,p: Preferences) {
        val alarm=context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        ensureChannel(context)
        // Keep the daily check scheduled even while blocked: enabling the OS channel
        // must take effect without opening the app again. Delivery checks OS state.
        val now=ZonedDateTime.now()
        var next=now.withHour(p.reminderHour).withMinute(p.reminderMinute).withSecond(0).withNano(0)
        if(!next.isAfter(now)) next=next.plusDays(1)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pending(context))
    }
}
class ReminderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val s=(context.applicationContext as PerilogApplication).repository.snapshot()
                if(intent.action=="com.poyal.perilog.REMIND" && !s.visibleRecords().dayComplete(today())) {
                    if(Reminders.allowed(context)) {
                        val nm=context.getSystemService(NotificationManager::class.java)
                        val open=PendingIntent.getActivity(context,42,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
                        nm.notify(41,NotificationCompat.Builder(context,"journal").setSmallIcon(android.R.drawable.ic_menu_edit)
                            .setContentTitle("오늘의 기록을 이어가세요").setContentText("아직 입력하지 않은 항목을 확인해 주세요.")
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
                    }
                }
                Reminders.schedule(context,s.preferences)
            } finally { result.finish() }
        }
    }
}
