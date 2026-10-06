package com.poyal.perilog

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Run only on a disposable emulator: this fixture replaces synthetic app data. */
class SettingsFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private fun snapshot()=runBlocking{app.repository.snapshot()}
    private fun waitFor(test:()->Boolean)=ui.waitUntil(15000,test)
    private fun show(n:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=n.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[i].performScrollTo()}
        runCatching{n.performScrollTo()};return n
    }
    private fun click(text:String)=show(ui.onNode(hasText(text) and hasClickAction())).performClick()
    // Top feedback temporarily covers the header; system back remains available.
    private fun back() {ui.runOnIdle{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()}
    private fun systemBack(){InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);ui.waitForIdle()}
    private fun leaveExternalScreen() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        waitFor { !ui.activity.hasWindowFocus() }
        automation.waitForIdle(500,5000)
        val externalPackage=automation.rootInActiveWindow?.packageName?.toString()
        check(externalPackage!=null && externalPackage!=app.packageName)
        // A file picker can consume the first back to close its filename keyboard.
        // Accessibility can still expose the old window during the return animation;
        // wait for actual app focus before considering a second back.
        repeat(2) {
            systemBack()
            val returned=runCatching { ui.waitUntil(3000) { ui.activity.hasWindowFocus() } }.isSuccess
            if(returned) { ui.waitForIdle();return }
            automation.waitForIdle(500,5000)
            check(automation.rootInActiveWindow?.packageName?.toString()==externalPackage)
        }
        error("External screen did not return app window focus: $externalPackage")
    }
    private fun input(label:String,text:String) {
        show(ui.onNode(hasSetTextAction() and (hasText(label) or hasContentDescription(label)))).performTextReplacement(text)
        ui.runOnIdle{(ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)}
        waitFor { ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())!=true }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,10000)
        ui.waitForIdle()
    }
    @Before fun fixture() {
        runBlocking{app.repository.restore(Snapshot(preferences=Preferences(celebrate=false)))}
        waitFor{ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick()
    }
    @Test fun allSettingsOpenDirectlyAndBackRestoresTheListWithoutChangingData() {
        val groups=listOf("일반","투석 기록·물품","병원·연락처","백업·데이터","도움말·앱 정보")
        val positions=groups.map { ui.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        positions.zipWithNext().forEach { (above,below)->assertTrue(above<below) }
        groups.forEach { show(ui.onNodeWithText(it)).assertHasNoClickAction() }
        val before=snapshot()
        val menus=listOf(
            "화면·표시" to "화면 설정 저장","알림" to "알림 시각 저장","앱 잠금" to "앱 잠금 설정 저장","홈 화면 위젯" to "기록 위젯 2×2 추가",
            "자동 백업" to "백업 설정 저장","데이터 내보내기·가져오기" to "전체 데이터 내보내기","보호 백업" to "보호 백업","데이터 초기화" to "모든 앱 데이터 초기화",
            "사용 구성" to "사용 구성 관리","품목 관리" to "+ 품목 추가","투석 계산 기준" to "이 날짜부터 기준 저장","사용자 색상" to "사용자 색상",
            "병원 일정" to "병원 일정 관리","진료과" to "+ 진료과 등록","검사·치료 항목" to "+ 치료 항목 등록","연락처" to "+ 연락처 등록",
            "사용 안내" to "사용법 검색","업데이트" to "업데이트 확인","앱 정보·문의" to "제작자  Poyal")
        menus.forEach{(entry,anchor)->
            click(entry);show(ui.onNodeWithText(anchor)).assertIsDisplayed();systemBack()
            // Exactly one back returns to the same visible row in the settings list.
            ui.onNodeWithText("설정").assertIsDisplayed()
            ui.onNode(hasText(entry) and hasClickAction()).assertIsDisplayed()
        }
        assertEquals(before,snapshot().copy(exportedAt=before.exportedAt))
    }
    @Test fun savingSettingsDoesNotMoveButtonsAndFeedbackClearsOnNavigation() {
        for((entry,save,message) in listOf(
            Triple("앱 잠금","앱 잠금 설정 저장","앱 잠금 설정을 저장했어요"),
            Triple("화면·표시","화면 설정 저장","화면 설정을 저장했어요"),
            Triple("알림","알림 시각 저장","알림 시각을 저장했어요")
        )) {
            click(entry)
            val saveBefore=ui.onNodeWithText(save).fetchSemanticsNode().boundsInRoot
            val cancelBefore=ui.onNodeWithText("취소").fetchSemanticsNode().boundsInRoot
            click(save)
            waitFor{ui.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()}
            ui.waitForIdle()
            val feedback=ui.onNodeWithText(message).fetchSemanticsNode().boundsInRoot
            assertTrue(feedback.bottom<saveBefore.top)
            val safe=ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            assertTrue(feedback.top>=safe.top)
            assertEquals(saveBefore,ui.onNodeWithText(save).fetchSemanticsNode().boundsInRoot)
            assertEquals(cancelBefore,ui.onNodeWithText("취소").fetchSemanticsNode().boundsInRoot)
            if(entry=="앱 잠금") {
                val dir=File(app.filesDir,"e2e-artifacts").apply{mkdirs()}
                File(dir,"top-snackbar-lock.png").outputStream().use {
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)
                }
            }
            // A second save replaces feedback without moving the tappable controls.
            ui.onNodeWithText(save).performTouchInput{click()}
            waitFor{ui.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty()}
            assertEquals(saveBefore,ui.onNodeWithText(save).fetchSemanticsNode().boundsInRoot)
            click(save)
            waitFor{ui.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()}
            systemBack()
            ui.onNodeWithText("설정").assertIsDisplayed()
            ui.onNodeWithText(message).assertDoesNotExist()
        }
    }
    @Test fun feedbackStaysAboveKeyboardWithoutMovingLandscapeSaveButtons() {
        click("알림")
        try {
            for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                ui.activityRule.scenario.onActivity{it.requestedOrientation=orientation}
                val expected=if(orientation==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
                waitFor{ui.activity.resources.configuration.orientation==expected && ui.activity.hasWindowFocus()}
                show(ui.onNode(hasSetTextAction() and (hasText("알림 시각") or hasContentDescription("알림 시각")))).performClick()
                ui.runOnIdle {
                    WindowCompat.getInsetsController(ui.activity.window,ui.activity.window.decorView).show(WindowInsetsCompat.Type.ime())
                }
                waitFor{ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}
                InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,10000)
                ui.waitForIdle()
                val before=ui.onNodeWithText("알림 시각 저장").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                ui.onNodeWithText("알림 시각 저장").performTouchInput{click()}
                waitFor{ui.onAllNodesWithText("알림 시각을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
                val feedback=ui.onNodeWithText("알림 시각을 저장했어요").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(feedback.bottom<=before.top)
                assertEquals(before,ui.onNodeWithText("알림 시각 저장").fetchSemanticsNode().boundsInRoot)
                assertTrue(ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime()))
                ui.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.Dismiss))
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss)
                ui.runOnIdle{WindowCompat.getInsetsController(ui.activity.window,ui.activity.window.decorView).hide(WindowInsetsCompat.Type.ime())}
                waitFor{ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())!=true}
            }
        } finally {ui.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}}
    }
    @Test fun completedHomeDoesNotShowOrRecordLegacyCelebration() {
        runBlocking {
            app.repository.preferences(Preferences(celebrate=true))
            app.repository.save(Treatment(id="complete",date=today(),weightGrams=62000,systolic=120,diastolic=80,
                initialDrain=2200,machineUf=600),true)
        }
        val before=snapshot()
        back()
        waitFor{ui.onAllNodesWithText("오늘도 기록을 마쳤어요").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("🎀 오늘의 기록 완료!").assertDoesNotExist()
        ui.onNodeWithText("오늘 하루도 꼼꼼히 챙겼어요.").assertDoesNotExist()
        ui.activityRule.scenario.recreate()
        waitFor{ui.onAllNodesWithText("오늘도 기록을 마쳤어요").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(before.preferences,snapshot().preferences)
        ui.onNodeWithContentDescription("설정").performClick();click("화면·표시")
        ui.onNodeWithText("완료 축하 애니메이션").assertDoesNotExist()
    }
    @Test fun unsavedDisplaySurvivesRecreationAndSaveClearsDirtyState() {
        click("화면·표시");click("어둡게")
        ui.activityRule.scenario.recreate()
        waitFor{ui.onAllNodesWithText("어둡게").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("어둡게").assertIsSelected()
        systemBack();ui.onNodeWithText("변경 내용을 버릴까요?").assertIsDisplayed();ui.onNode(hasText("취소") and hasAnyAncestor(isDialog())).performClick()
        click("화면 설정 저장")
        waitFor{snapshot().preferences.darkMode=="DARK"}
        waitFor{ui.onAllNodesWithText("화면 설정을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
        back();ui.onNodeWithText("변경 내용을 버릴까요?").assertDoesNotExist()
        ui.onNodeWithText("어둡게").assertIsDisplayed()
        click("화면·표시");click("밝게");back();click("확인")
        assertEquals("DARK",snapshot().preferences.darkMode)
    }
    @Test fun settingsSaveIndependentlyAndInvalidInputSurvives() {
        click("알림")
        input("알림 시각","27");ui.onNodeWithText("알림 시각 저장").assertIsNotEnabled()
        ui.activityRule.scenario.recreate()
        waitFor{ui.onAllNodes(hasSetTextAction() and hasText("27")).fetchSemanticsNodes().isNotEmpty()}
        input("알림 시각","19");input("알림 분","45");click("알림 시각 저장")
        waitFor{snapshot().preferences.reminderHour==19 && snapshot().preferences.reminderMinute==45}
        back();click("자동 백업")
        click("매주");input("자동 백업 보관 개수","12");click("백업 설정 저장")
        waitFor{snapshot().preferences.keepBackups==12}
        back();click("투석 계산 기준")
        input("이전 최종 주입 설정","2100");click("이 날짜부터 기준 저장")
        waitFor{snapshot().preferences.basis.last().ml==2100}
        val p=snapshot().preferences
        assertEquals(19,p.reminderHour);assertEquals(45,p.reminderMinute);assertEquals(7,p.backupDays)
        assertEquals("SYSTEM",p.darkMode);assertFalse(p.lock)
        back();ui.onNodeWithText("변경 내용을 버릴까요?").assertDoesNotExist()
    }
    @Test fun osSettingsAndCancelledFilePickersReturnWithoutChangingAppData() {
        val before=snapshot()
        click("알림");click("시스템 알림 설정 열기")
        waitFor{InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.android.settings"}
        leaveExternalScreen()
        waitFor{runCatching{ui.onAllNodesWithText("알림 시각 저장").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        back();click("데이터 내보내기·가져오기")
        listOf("전체 데이터 내보내기","백업 파일 가져오기").forEach {label->
            click(label)
            waitFor{InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}
            leaveExternalScreen()
            waitFor{runCatching{ui.onAllNodesWithText("전체 데이터 내보내기").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        }
        back();click("데이터 초기화");click("모든 앱 데이터 초기화");click("취소")
        assertEquals(before,snapshot().copy(exportedAt=before.exportedAt))
    }
    @Test fun saveRemainsReachableInLandscapeAndInputsArePreserved() {
        click("알림");input("알림 시각","18")
        try {
            ui.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            waitFor{ui.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            show(ui.onNode(hasSetTextAction() and hasText("18"))).assertIsDisplayed()
            ui.onNodeWithText("알림 시각 저장").assertIsDisplayed();click("알림 시각 저장")
            waitFor{snapshot().preferences.reminderHour==18}
        } finally {ui.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}}
    }
}
