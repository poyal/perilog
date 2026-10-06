package com.poyal.perilog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.poyal.perilog.ui.PerilogTheme
import com.poyal.perilog.ui.TopSnackbarHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TopSnackbarHostTest {
    @get:Rule val ui = createComposeRule()
    private val host = SnackbarHostState()
    private lateinit var scope: CoroutineScope
    private var saves = 0

    private fun content(accessibility: AccessibilityManager? = null) {
        ui.setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAccessibilityManager provides accessibility) {
                PerilogTheme("LIGHT") {
                    Box(Modifier.fillMaxSize()) {
                        Text("본문", Modifier.align(Alignment.Center))
                        Button(onClick = { saves++ }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("저장") }
                        TopSnackbarHost(host, Modifier.align(Alignment.TopCenter).padding(top = 32.dp, start = 16.dp, end = 16.dp))
                    }
                }
            }
        }
        ui.mainClock.autoAdvance = false
    }

    private fun show(message: String, duration: SnackbarDuration = SnackbarDuration.Short,
                     action: String? = null, result: (SnackbarResult) -> Unit = {}): Job {
        lateinit var job: Job
        ui.runOnIdle { job = scope.launch { result(host.showSnackbar(message, action, duration = duration)) } }
        ui.mainClock.advanceTimeByFrame()
        ui.mainClock.advanceTimeByFrame()
        return job
    }

    @Test fun slidesDownAndUpWithoutMovingContentOrSaveAndTimesOut() {
        content()
        val before = ui.onNodeWithText("저장").fetchSemanticsNode().boundsInRoot
        val body = ui.onNodeWithText("본문").fetchSemanticsNode().boundsInRoot
        show("저장했어요")
        ui.mainClock.advanceTimeBy(80)
        val enteringTop = ui.onNodeWithText("저장했어요").getUnclippedBoundsInRoot().top
        ui.mainClock.advanceTimeBy(200)
        val restingTop = ui.onNodeWithText("저장했어요").getUnclippedBoundsInRoot().top
        assertTrue(enteringTop < restingTop)
        assertEquals(before, ui.onNodeWithText("저장").fetchSemanticsNode().boundsInRoot)
        assertEquals(body, ui.onNodeWithText("본문").fetchSemanticsNode().boundsInRoot)
        ui.onNodeWithText("저장").performTouchInput { click() }
        ui.runOnIdle { assertEquals(1, saves) }
        ui.mainClock.advanceTimeBy(3_400)
        ui.runOnIdle { assertNotNull(host.currentSnackbarData) }
        ui.mainClock.advanceTimeBy(600)
        ui.runOnIdle { assertNull(host.currentSnackbarData) }
        ui.onNodeWithText("저장했어요").assertDoesNotExist()
        assertEquals(before, ui.onNodeWithText("저장").fetchSemanticsNode().boundsInRoot)

        show("다시 저장했어요", SnackbarDuration.Indefinite)
        ui.mainClock.advanceTimeBy(250)
        ui.runOnIdle { host.currentSnackbarData!!.dismiss() }
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("다시 저장했어요").assertDoesNotExist() // Exit is immediately hidden from accessibility.
        ui.mainClock.advanceTimeBy(200)
        assertEquals(body, ui.onNodeWithText("본문").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun cancellationHidesOldActionAndQueuedUndoStillReturnsItsResult() {
        content()
        var staleResult: SnackbarResult? = null
        var undoResult: SnackbarResult? = null
        val first = show("이전 알림", SnackbarDuration.Indefinite, "이전 동작") { staleResult = it }
        ui.mainClock.advanceTimeBy(250)
        show("기록을 삭제했어요", SnackbarDuration.Long, "되돌리기") { undoResult = it }
        ui.onNodeWithText("기록을 삭제했어요").assertDoesNotExist()
        ui.runOnIdle { first.cancel() }
        ui.mainClock.advanceTimeByFrame()
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("이전 동작").assertDoesNotExist()
        ui.mainClock.advanceTimeBy(250)
        ui.onNodeWithText("되돌리기").performClick()
        ui.runOnIdle {
            assertNull(staleResult)
            assertEquals(SnackbarResult.ActionPerformed, undoResult)
            assertNull(host.currentSnackbarData)
        }
        ui.mainClock.advanceTimeBy(200)
    }

    @Test fun longAndIndefiniteDurationsAndAccessibilityTimeoutAreRespected() {
        var requestedTimeout = 0L
        var requestedControls = false
        content(object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(originalTimeoutMillis: Long, containsIcons: Boolean,
                containsText: Boolean, containsControls: Boolean): Long {
                requestedTimeout = originalTimeoutMillis
                requestedControls = containsControls
                return originalTimeoutMillis * 2
            }
        })
        show("삭제 알림", SnackbarDuration.Long, "되돌리기")
        ui.mainClock.advanceTimeBy(10_500)
        ui.runOnIdle {
            assertEquals(10_000L, requestedTimeout)
            assertTrue(requestedControls)
            assertNotNull(host.currentSnackbarData)
        }
        ui.mainClock.advanceTimeBy(10_000)
        ui.runOnIdle { assertNull(host.currentSnackbarData) }
        show("계속 표시", SnackbarDuration.Indefinite)
        ui.mainClock.advanceTimeBy(60_000)
        ui.onNodeWithText("계속 표시").assertIsDisplayed()
        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        ui.mainClock.advanceTimeBy(200)
        ui.runOnIdle { assertNull(host.currentSnackbarData) }
    }

    @Test fun longMessageAndActionFitNarrowWidthWithLargeTextInBothThemes() {
        var dark by mutableStateOf(false)
        val message = "작성 중인 기록을 보관하지 못했어요. 입력한 내용을 확인한 뒤 다시 시도해 주세요."
        ui.setContent {
            scope = rememberCoroutineScope()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PerilogTheme(if (dark) "DARK" else "LIGHT") {
                    Box(Modifier.width(320.dp)) {
                        TopSnackbarHost(host, Modifier.padding(16.dp).testTag("알림 영역"))
                    }
                }
            }
        }
        show(message, SnackbarDuration.Indefinite, "되돌리기")
        for (theme in listOf(false, true)) {
            ui.runOnIdle { dark = theme }
            ui.onNodeWithText(message).assertIsDisplayed()
            ui.onNodeWithText("되돌리기").assertIsDisplayed()
            val text = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(message).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(text) }
            assertFalse(text.single().hasVisualOverflow)
            val bounds = ui.onNodeWithTag("알림 영역").getUnclippedBoundsInRoot()
            val action = ui.onNodeWithText("되돌리기").getUnclippedBoundsInRoot()
            assertTrue(action.left >= bounds.left && action.right <= bounds.right)
        }
    }
}
