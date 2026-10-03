package com.poyal.perilog.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.poyal.perilog.MainActivity
import com.poyal.perilog.data.newId
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class WidgetTarget(val screen: String, val value: String = "") {
    fun valid(on: LocalDate = LocalDate.now()): Boolean = when (screen) {
        "record" -> runCatching { LocalDate.parse(value).toString() == value && LocalDate.parse(value) <= on }.getOrDefault(false)
        "appointment" -> value.isNotBlank() && value.length <= 200
        "home", "appointments", "newAppointment" -> value.isEmpty()
        else -> false
    }
}

@Serializable
data class WidgetOpenRequest(val target: WidgetTarget, val id: String = newId())

object WidgetNavigation {
    private const val ACTION = "com.poyal.perilog.OPEN_WIDGET"
    fun intent(context: Context, target: WidgetTarget) = Intent(context, MainActivity::class.java)
        .setAction(ACTION)
        .setData(Uri.Builder().scheme("perilog-widget").authority(context.packageName)
            .appendPath(target.screen).appendPath(target.value).build())
        .putExtra("widget_screen", target.screen).putExtra("widget_value", target.value)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun read(intent: Intent?): WidgetOpenRequest? {
        if (intent?.action != ACTION) return null
        val target = WidgetTarget(intent.getStringExtra("widget_screen") ?: "", intent.getStringExtra("widget_value") ?: "")
        return WidgetOpenRequest(if (target.valid()) target else WidgetTarget("home"))
    }
}
