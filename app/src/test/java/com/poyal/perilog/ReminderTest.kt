package com.poyal.perilog

import android.Manifest
import android.app.*
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.poyal.perilog.backup.Reminders
import com.poyal.perilog.data.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=Application::class)
class ReminderTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun osPermissionAndChannelAreAuthoritativeAndChannelSettingsArePreserved() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager=context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        Reminders.ensureChannel(context)
        assertTrue(Reminders.allowed(context))
        val channel=manager.getNotificationChannel(Reminders.CHANNEL)
        channel.importance=NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        Reminders.ensureChannel(context)
        assertFalse(Reminders.allowed(context))
        assertEquals(NotificationManager.IMPORTANCE_NONE,manager.getNotificationChannel(Reminders.CHANNEL).importance)
    }
    @Test fun legacyOffDoesNotDisableDailyChecksAndReschedulingDoesNotDuplicateThem() {
        Reminders.schedule(context,Preferences(reminder=false,reminderHour=19,reminderMinute=30))
        val alarm=shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertEquals(1,alarm.scheduledAlarms.size)
        Reminders.schedule(context,Preferences(reminder=true,reminderHour=22))
        assertEquals(1,alarm.scheduledAlarms.size)
    }
}
