package com.poyal.perilog

import android.appwidget.AppWidgetManager.*
import android.os.Bundle
import android.util.SizeF
import com.poyal.perilog.widget.normalizedWidgetOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=android.app.Application::class)
class WidgetHostOptionsTest {
    private fun options(size:SizeF)=Bundle().apply {
        putInt(OPTION_APPWIDGET_MIN_WIDTH,146);putInt(OPTION_APPWIDGET_MAX_WIDTH,146)
        putInt(OPTION_APPWIDGET_MIN_HEIGHT,274);putInt(OPTION_APPWIDGET_MAX_HEIGHT,274)
        putParcelableArrayList(OPTION_APPWIDGET_SIZES,arrayListOf(size))
        putString("host-extra","preserved")
    }
    @Test fun staleSizesAreCorrectedOnceWithoutChangingValidFractionalSizes() {
        val old=options(SizeF(303f,274f))
        val current=normalizedWidgetOptions(old)
        @Suppress("DEPRECATION")
        assertEquals(listOf(SizeF(146f,274f)),current.getParcelableArrayList<SizeF>(OPTION_APPWIDGET_SIZES))
        assertEquals("preserved",current.getString("host-extra"))
        assertSame(current,normalizedWidgetOptions(current))
        val fractional=options(SizeF(146.5f,274.8f))
        assertSame(fractional,normalizedWidgetOptions(fractional))
        assertSame(Bundle.EMPTY,normalizedWidgetOptions(Bundle.EMPTY))
    }
}
