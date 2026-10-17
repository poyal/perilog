package com.poyal.perilog

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

    @Test fun dwellTimeClockAppliesExactMinutesCancelsAndDistinguishesZeroFromMissing() {
        var value by mutableStateOf<Int?>(null)
        ui.setContent {PerilogTheme("LIGHT"){Column {DwellTimeInput(value){value=it}}}}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun clockLabel(name:String,value:Int):String=context.getString(context.resources.getIdentifier(name,"string",context.packageName),value)
        fun open()=ui.onNodeWithText(if(value==null)"시간 선택"else"시간 변경").performClick()
        fun choose(hour:Int,minute:Int) {
            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,listOf(clockLabel("m3c_time_picker_hour_24h_suffix",hour)))).performScrollTo().performClick()
            ui.onNodeWithContentDescription(context.getString(context.resources.getIdentifier("m3c_time_picker_minute_selection","string",context.packageName))).performScrollTo().performClick()
            if(minute%5==0)ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,listOf(clockLabel("m3c_time_picker_minute_suffix",minute)))).performScrollTo().performClick()
            else {
                val dial=ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).onLast()
                dial.performScrollTo().performTouchInput {
                    val angle=Math.toRadians((minute*6-90).toDouble())
                    val radius=width*.38f
                    // Drag along the minute's radius so touch slop cannot shift the angle.
                    val direction=Offset(kotlin.math.cos(angle).toFloat(),kotlin.math.sin(angle).toFloat())
                    swipe(center+direction*(radius*.5f),center+direction*radius,300)
                }
            }
            ui.onNodeWithTag("dwell-time-selection").assertTextEquals("${hour}시간 ${minute.toString().padStart(2,'0')}분")
        }
        open();ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        ui.onNodeWithText("오전").assertDoesNotExist();ui.onNodeWithText("오후").assertDoesNotExist()
        choose(1,35);ui.onNodeWithText("취소").performClick();ui.runOnIdle{assertNull(value)}
        for(minutes in listOf(0,61,95,1439)) {
            open();choose(minutes/60,minutes%60)
            ui.onNodeWithText("적용").performClick();ui.runOnIdle{assertEquals(minutes,value)}
            open();ui.onNodeWithTag("dwell-time-selection").assertTextEquals("${minutes/60}시간 ${(minutes%60).toString().padStart(2,'0')}분")
            ui.onNodeWithText("취소").performClick()
        }
        ui.onNodeWithText("시간 지우기").performClick();ui.runOnIdle{assertNull(value)}
        ui.onNodeWithText("미입력").assertExists()
    }
    @Test fun dwellTimeClockFitsBothThemesAndFontScales() {
        var dark by mutableStateOf(false)
        val font=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale
        ui.setContent {
            PerilogTheme(if(dark)"DARK"else"LIGHT"){androidx.compose.material3.Surface{Column {DwellTimeInput(95){}}}}
        }
        for(theme in listOf(false,true)) {
            ui.runOnIdle{dark=theme}
            ui.onNodeWithText("시간 변경").performClick()
            ui.onNodeWithTag("dwell-time-dialog").assertIsDisplayed()
            for(text in listOf("평균저류시간 선택","취소","적용")) {
                ui.onNodeWithText(text).assertIsDisplayed()
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                layouts.forEach{layout->
                    assertTrue("Clipped $text at $font: ${layout.size}, height=${layout.multiParagraph.height}",layout.multiParagraph.height<=layout.size.height+1f)
                    for(line in 0 until layout.lineCount)assertTrue("Clipped $text at $font",layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1f)
                }
            }
            screenshot("dwell-clock-${if(theme)"dark"else"light"}-$font.png")
            ui.onNodeWithText("취소").performClick()
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
