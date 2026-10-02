package com.poyal.perilog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.poyal.perilog.ui.dark
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun darkReadingAndInteractivePairsHaveRequiredContrast() {
        val text = listOf(dark.onBackground to dark.background, dark.onSurface to dark.surface,
            dark.onSurfaceVariant to dark.surface, dark.onSurfaceVariant to dark.background,
            dark.onPrimary to dark.primary, dark.primary to dark.surface, dark.secondary to dark.surface,
            dark.onPrimaryContainer to dark.primaryContainer, dark.onErrorContainer to dark.errorContainer)
        text.forEach { (fg, bg) -> assertTrue("Text contrast ${ratio(fg,bg)}", ratio(fg, bg) >= 4.5) }
        assertTrue(ratio(dark.outline, dark.surfaceContainerLowest) >= 3)
        assertTrue(ratio(dark.outline, dark.surface) >= 3)
    }
}
