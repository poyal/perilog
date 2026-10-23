package com.poyal.perilog

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TemporalControlsTest {
    @get:Rule val ui=createComposeRule()
    private fun click(text:String)=ui.onNodeWithText(text).performClick()
    private fun openDuration()=ui.onNodeWithContentDescription("평균저류시간 선택").performClick()
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.filesDir,"e2e-artifacts").apply{mkdirs()}
        File(dir,name).outputStream().use {instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun durationDraftRestoresCancelsAndDistinguishesEmptyFromZero() {
        var value:Int?=null
        val restore=StateRestorationTester(ui)
        restore.setContent {PerilogTheme("LIGHT") {
            var saved by rememberSaveable{mutableStateOf<Int?>(null)}
            DurationControl(saved){saved=it;value=it}
        }}
        ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        openDuration()
        ui.onNodeWithContentDescription("평균저류 분").performTextReplacement("05")
        restore.emulateSavedInstanceStateRestore()
        ui.onNodeWithContentDescription("평균저류 분").assertTextContains("05")
        assertNull(value);click("취소");assertNull(value)
        openDuration();ui.onNodeWithContentDescription("평균저류 분").performTextReplacement("0");click("확인")
        assertEquals(0,value)
        ui.onNodeWithContentDescription("평균저류시간 선택").assertTextContains("0시간 0분")
        openDuration();ui.onNodeWithContentDescription("평균저류 시간").performTextReplacement("23")
        ui.onNodeWithContentDescription("평균저류 분").performTextReplacement("59");click("확인")
        assertEquals(1439,value)
        ui.onNodeWithContentDescription("평균저류시간 해제").performClick();assertNull(value)
    }
    @Test fun clockDraftRestoresRejectsOutOfRangeAndKeepsTargetsIndependent() {
        var a by mutableStateOf<String?>(null)
        var b by mutableStateOf<String?>("23:59")
        val restore=StateRestorationTester(ui)
        restore.setContent {PerilogTheme("LIGHT"){Column {
            TimeControl("예약시간",a,{a=it})
            TimeControl("피검사 시간",b,{b=it},clearable=true)
        }}}
        ui.openTime("예약시간");ui.setClock("24","60")
        val fields=ui.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].assertTextContains("09");fields[1].assertTextContains("00")
        ui.setClock("12","34");restore.emulateSavedInstanceStateRestore()
        fields[0].assertTextContains("12");fields[1].assertTextContains("34")
        assertNull(a);click("취소");assertNull(a)
        ui.chooseTime("예약시간","00:00");assertEquals("00:00",a)
        ui.openTime("피검사 시간");fields[0].assertTextContains("23");fields[1].assertTextContains("59")
        click("취소");assertEquals("23:59",b)
        ui.onNodeWithContentDescription("피검사 시간 해제").performClick();assertNull(b)
        assertEquals("00:00",a)
    }
    @Test fun calendarCancelAndMonthRestoreDoNotChangeDate() {
        var date by mutableStateOf("2024-02-28")
        val restore=StateRestorationTester(ui)
        restore.setContent {PerilogTheme("LIGHT"){DateControl(date,{date=it},"예약일")}}
        ui.onNodeWithContentDescription("예약일 날짜 선택").performClick()
        ui.onNodeWithContentDescription("다음 달").performClick()
        restore.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("3월").assertExists();assertEquals("2024-02-28",date)
        click("취소");assertEquals("2024-02-28",date)
        ui.onNodeWithContentDescription("예약일 날짜 선택").performClick()
        ui.onNodeWithContentDescription("2024-02-29").performClick();assertEquals("2024-02-29",date)
    }
    @Test fun durationRejectsInvalidPartsAndRequiresExplicitLegacyCorrection() {
        var value by mutableStateOf<Int?>(348000)
        ui.setContent{PerilogTheme("LIGHT"){DurationControl(value){value=it}}}
        openDuration()
        val hours=ui.onNodeWithContentDescription("평균저류 시간")
        val minutes=ui.onNodeWithContentDescription("평균저류 분")
        hours.assertTextContains("5800");ui.onNodeWithText("확인").assertIsNotEnabled()
        minutes.performTextReplacement("30");assertEquals(348000,value)
        hours.performTextReplacement("23");minutes.performTextReplacement("59")
        for(bad in listOf("24","99","100","-1","1.5"," 1","1:30","abc","１２","2147483647")) {
            hours.performTextReplacement(bad);hours.assertTextContains("23")
        }
        for(bad in listOf("60","99","100","-1","1.5"," 1","1:30","abc","１２","2147483647")) {
            minutes.performTextReplacement(bad);minutes.assertTextContains("59")
        }
        click("확인");assertEquals(1439,value)
        openDuration();hours.performTextReplacement("");minutes.performTextReplacement("01")
        click("확인");assertEquals(1,value)
        openDuration();hours.performTextReplacement("");minutes.performTextReplacement("")
        click("확인");assertNull(value)
    }
    @Test fun numericPickersFitBothThemesAndFontScales() {
        var dark by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                PerilogTheme(if(dark)"DARK"else"LIGHT") {Column {
                    TimeControl("알림 시각","23:59",{})
                    DurationControl(1439){}
                }}
            }
        }
        for(font in listOf(1f,1.5f))for(night in listOf(false,true)) {
            ui.runOnIdle{dark=night;scale=font}
            for(duration in listOf(false,true)) {
                if(duration)openDuration()else ui.openTime("알림 시각")
                // Material TimeInput exposes an inactive editor as well as its visible number.
                // Inspect the rendered number instead of scrolling the hidden editor into view.
                for(number in listOf("23","59")) {
                    val candidates=ui.onAllNodes(hasText(number) and hasAnyAncestor(isDialog()))
                    if(duration)runCatching{candidates[0].performScrollTo()}
                    val visible=candidates.fetchSemanticsNodes().indices.firstOrNull {
                        runCatching{candidates[it].assertIsDisplayed()}.isSuccess
                    }
                    assertNotNull("Missing $number in picker $duration/$font/$night",visible)
                    val layouts=mutableListOf<TextLayoutResult>()
                    candidates[visible!!].performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                    layouts.forEach{layout->
                        assertTrue("Clipped picker $duration/$font",layout.multiParagraph.height<=layout.size.height+1f)
                        for(line in 0 until layout.lineCount)assertTrue("Clipped picker digits $duration/$font",layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1f)
                    }
                }
                ui.onNodeWithText("확인").assertIsDisplayed()
                screenshot("numeric-picker-$duration-$font-$night.png")
                click("취소")
            }
        }
    }
    @Test fun labelsAndButtonsFitNarrowWidthsLargeFontsAndBothThemes() {
        var width by mutableStateOf(320)
        var scale by mutableStateOf(1f)
        var dark by mutableStateOf(false)
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                PerilogTheme(if(dark)"DARK"else"LIGHT") {
                    Column(Modifier.width(width.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                        Paper {
                            DateControl("2026-10-23",{},"예약일")
                            TimeControl("긴 진료과 이름 예약시간","23:59",{})
                            DurationControl(1439){}
                            DateRangeControl(LocalDate.of(2026,10,1),LocalDate.of(2026,10,23),{},true)
                        }
                    }
                }
            }
        }
        for(w in listOf(320,360,412))for(font in listOf(1f,1.5f))for(night in listOf(false,true)) {
            ui.runOnIdle{width=w;scale=font;dark=night}
            ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            val nodes=ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
            for(i in nodes.fetchSemanticsNodes().indices) {
                val node=nodes[i];runCatching{node.performScrollTo()};node.assertIsDisplayed()
                val layouts=mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                layouts.forEach{layout->
                    val detail="Text clipped at $w / $font / $night: ${layout.layoutInput.text}"
                    assertTrue(detail,layout.multiParagraph.height<=layout.size.height+1f)
                    for(line in 0 until layout.lineCount)assertTrue(detail,layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1f)
                }
            }
            if(w==320)screenshot("temporal-fields-$font-${if(night)"dark"else"light"}.png")
        }
    }
}
