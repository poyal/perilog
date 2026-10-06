package com.poyal.perilog.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Legacy resize calls can leave an old Android 12 size list in the merged options. */
internal fun normalizedWidgetOptions(options:Bundle):Bundle {
    val minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    if(minW<=0 || minH<=0 || maxW<minW || maxH<minH)return options
    @Suppress("DEPRECATION")
    val sizes=options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) ?: return options
    val fitting=sizes.filter { it.width.isFinite() && it.height.isFinite() && it.width.toInt() in minW..maxW && it.height.toInt() in minH..maxH }
    if(fitting.size==sizes.size)return options
    val current=fitting.ifEmpty {listOf(SizeF(minW.toFloat(),maxH.toFloat()),SizeF(maxW.toFloat(),minH.toFloat())).distinct()}
    return Bundle(options).apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,ArrayList(current))}
}

abstract class PerilogWidgetReceiver:GlanceAppWidgetReceiver() {
    override fun onAppWidgetOptionsChanged(context:Context,appWidgetManager:AppWidgetManager,appWidgetId:Int,newOptions:Bundle) {
        val normalized=normalizedWidgetOptions(newOptions)
        if(normalized!==newOptions) {
            // Persist the correction for later worker updates; the next broadcast is already normalized.
            appWidgetManager.updateAppWidgetOptions(appWidgetId,normalized)
        }
        super.onAppWidgetOptionsChanged(context,appWidgetManager,appWidgetId,normalized)
    }
}
