package com.poyal.perilog

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiComponentsTest {
    @get:Rule(order=0) val ui=createComposeRule()
    @get:Rule(order=1) val failureCapture=object:TestWatcher() {
        override fun failed(e:Throwable,description:Description) {runCatching{screenshot("failure-${description.methodName}.png")}}
    }
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.filesDir,"e2e-artifacts").apply{mkdirs()}
        File(dir,name).outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun calendarFitsSevenColumnsAndSelectsEdgeDatesWithLargeText() {
        var width by mutableStateOf(320)
        var scale by mutableStateOf(1f)
        var date by mutableStateOf("2026-10-15")
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                PerilogTheme("LIGHT") {
                    key(width,scale) {
                        Column(Modifier.width(width.dp).verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
                            Paper {DateControl(date,{date=it},"예약일")}
                        }
                    }
                }
            }
        }
        for(w in listOf(320,360,412))for(font in listOf(1f,1.5f)) {
            ui.runOnIdle{width=w;scale=font;date="2026-10-15"}
            ui.onNodeWithText("오늘").assertDoesNotExist();ui.onNodeWithText("어제").assertDoesNotExist()
            ui.onNodeWithText("날짜 선택").performScrollTo().performClick()
            ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
            listOf("2026년","10월").forEach { title ->
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                assertEquals(1,layouts.single().lineCount)
            }
            val weekdays=listOf("일","월","화","수","목","금","토").map {
                ui.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot()
            }
            weekdays.zipWithNext().forEach{(left,right)->assertTrue(left.right<=right.left)}
            for(day in 1..31) {
                val cell=ui.onNodeWithContentDescription("2026-10-${day.toString().padStart(2,'0')}")
                cell.performScrollTo().assertIsDisplayed()
                val bounds=cell.getUnclippedBoundsInRoot()
                assertTrue(bounds.left.value>=0f && bounds.right.value<=w)
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithText(day.toString(),useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                val layout=layouts.single()
                val lineWidth=layout.getLineRight(0)-layout.getLineLeft(0)
                val details="width=$w font=$font day=$day size=${layout.size} lineWidth=$lineWidth lines=${layout.lineCount}"
                assertEquals(details,1,layout.lineCount)
                // The paragraph uses the whole cell width; measure the actual text line.
                assertTrue(details,lineWidth<=layout.size.width+1f)
                assertTrue(details,layout.multiParagraph.height<=layout.size.height+1f)
            }
            if(w==320 && font==1.5f) {
                screenshot("updated-calendar-narrow-large.png")
            }
            ui.onNodeWithContentDescription("2026-10-31").performClick()
            ui.runOnIdle{assertEquals("2026-10-31",date)}
            ui.onNodeWithText("날짜 선택").performScrollTo().performClick()
            ui.onNodeWithContentDescription("이전 달").performScrollTo().performClick()
            ui.onNodeWithContentDescription("이전 달").performClick()
            ui.onNodeWithContentDescription("2026-08-31").performScrollTo().performClick()
            ui.runOnIdle{assertEquals("2026-08-31",date)}
            ui.onNodeWithText("날짜 선택").performScrollTo().performClick()
            ui.onNodeWithContentDescription("다음 달").performScrollTo().performClick()
            ui.onNodeWithContentDescription("2026-09-06").performScrollTo().performClick()
            ui.runOnIdle{assertEquals("2026-09-06",date)}
        }
    }

    @Test fun memoHidesBlankContentAndExpandsMultilineTextInBothThemes() {
        var dark by mutableStateOf(false)
        var memo by mutableStateOf("")
        val longMemo=(1..7).joinToString("\n"){"메모 내용 $it"}
        ui.setContent {
            PerilogTheme(if(dark)"DARK"else"LIGHT") {
                Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {MemoBlock(memo)}
            }
        }
        ui.onNodeWithText("메모").assertDoesNotExist()
        for(night in listOf(false,true)) {
            ui.runOnIdle{dark=night;memo=longMemo}
            ui.onNodeWithText("메모").assertIsDisplayed()
            ui.onNodeWithText("더 보기").performScrollTo().performClick()
            val layouts=mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(longMemo).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertEquals(7,layouts.single().lineCount);assertFalse(layouts.single().hasVisualOverflow)
            ui.onNodeWithText("접기").performScrollTo().performClick()
            ui.onNodeWithText("더 보기").assertExists()
        }
        ui.runOnIdle{memo="짧은 메모"}
        ui.onNodeWithText("짧은 메모").assertIsDisplayed();ui.onNodeWithText("더 보기").assertDoesNotExist()
        ui.runOnIdle{memo="  \n "}
        ui.onNodeWithText("메모").assertDoesNotExist()
    }
}
