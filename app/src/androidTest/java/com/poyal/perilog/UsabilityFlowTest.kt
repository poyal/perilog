package com.poyal.perilog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poyal.perilog.data.*
import com.poyal.perilog.ui.JournalViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UsabilityFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private fun snapshot()=runBlocking{app.repository.snapshot()}
    private fun await(condition:()->Boolean)=ui.waitUntil(15000,condition)
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=node.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[i].performScrollTo()}
        runCatching{node.performScrollTo()};return node
    }
    private fun click(text:String)=show(ui.onNodeWithText(text)).performClick()
    private fun back() {ui.runOnIdle{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()}
    @Before fun fixture() {
        runBlocking{app.repository.restore(Snapshot(preferences=Preferences(celebrate=false)))}
        await{ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun settingsSaveOnlyTheirSectionAndShowSuccess() {
        ui.onNodeWithContentDescription("설정").performClick()
        click("화면·표시");click("어둡게")
        click("화면 설정 저장")
        await{snapshot().preferences.darkMode=="DARK"}
        await{ui.onAllNodesWithText("화면 설정을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
        back();click("자동 백업")
        click("매주");click("백업 설정 저장")
        await{snapshot().preferences.backupDays==7}
        await{ui.onAllNodesWithText("백업 설정을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("DARK",snapshot().preferences.darkMode)
        back();click("알림")
        ui.onNodeWithText("미작성 항목 기기 알림").assertDoesNotExist()
        show(ui.onNodeWithText("시스템 알림 설정 열기")).assertExists()
        click("알림 시각 저장")
        await{ui.onAllNodesWithText("알림 시각을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun inlineTemplateSelectionPreservesAdjustedQuantitiesAndSavesOnce() {
        val product=Product(id="fluid",name="투석액")
        val one=UsageTemplate(id="one",name="기본 구성",items=listOf(Item(product.id,product.name,1)))
        val two=one.copy(id="two",name="다른 구성",items=listOf(Item(product.id,product.name,2)))
        runBlocking {app.repository.product(product);app.repository.template(one);app.repository.template(two)}
        click("오늘 기록 시작")
        show(ui.onNode(hasText("다른 구성") and hasClickAction())).performClick()
        val vm=ViewModelProvider(ui.activity)[JournalViewModel::class.java]
        ui.runOnIdle {
            val t=requireNotNull(vm.editor.value)
            assertEquals("two",t.usageTemplateId)
            vm.change(t.copy(items=t.items.map{it.copy(quantity=3)}))
        }
        show(ui.onNode(hasText("다른 구성") and hasClickAction())).performClick()
        ui.runOnIdle{assertEquals(3,vm.editor.value!!.items.single().quantity)}
        assertTrue(snapshot().usages.isEmpty())
        click("기록 저장")
        await{snapshot().treatments.size==1}
        await{ui.onAllNodesWithText("기록을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(3,snapshot().treatments.single().items.single().quantity)
    }
    @Test fun optionalCareTimeCanBeSelectedSavedAndRemoved() {
        val care=CareTemplate(id="blood",name="피검사",iconKey="blood")
        val visit=Appointment(id="visit",date=LocalDate.now().plusDays(2).toString(),time="09:45",
            careItems=listOf(CareTask(id="blood",name="피검사",iconKey="blood")))
        runBlocking {app.repository.careTemplate(care);app.repository.appointment(visit)}
        await{ui.onAllNodesWithText("D-2").fetchSemanticsNodes().isNotEmpty()}
        click("D-2");show(ui.onNodeWithTag("appointment-menu-${visit.id}")).performClick();click("일정 수정")
        click("시간 지정");click("확인");click("저장")
        await{snapshot().appointments.single().careItems.single().time=="09:45"}
        await{ui.onAllNodesWithText("병원 일정을 저장했어요").fetchSemanticsNodes().isNotEmpty()}
        show(ui.onNodeWithTag("appointment-menu-${visit.id}")).performClick();click("일정 수정")
        show(ui.onNodeWithContentDescription("피검사 시간 해제")).performClick()
        click("저장")
        await{snapshot().appointments.single().careItems.single().time==null}
    }
}
