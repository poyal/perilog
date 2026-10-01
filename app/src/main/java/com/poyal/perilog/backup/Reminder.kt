package com.poyal.perilog.backup

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.poyal.perilog.*
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.dayComplete
import com.poyal.perilog.domain.visibleRecords
import kotlinx.coroutines.*
import java.time.ZonedDateTime

object Reminders {
    private fun pending(context: Context) = PendingIntent.getBroadcast(context,41,Intent(context,ReminderReceiver::class.java).setAction("com.poyal.perilog.REMIND"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context,p: Preferences) {
        val alarm=context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        if(!p.reminder) return
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
                if(intent.action=="com.poyal.perilog.REMIND" && s.preferences.reminder && !s.visibleRecords().dayComplete(today())) {
                    val allowed=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
                    if(allowed) {
                        val nm=context.getSystemService(NotificationManager::class.java)
                        nm.createNotificationChannel(NotificationChannel("journal","오늘의 기록",NotificationManager.IMPORTANCE_DEFAULT))
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
