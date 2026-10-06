package com.poyal.perilog

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ReplenishmentFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val capture=object:TestWatcher() {
        override fun failed(e:Throwable,d:Description) {
            runCatching {val dir=File(app.filesDir,"e2e-artifacts").apply {mkdirs()};File(dir,"failure-${d.methodName}.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
        }
    }
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val p=Product(id="fluid",name="테스트 물품 A")
    private val q=Product(id="line",name="테스트 물품 B")
    private fun snapshot()=runBlocking {app.repository.snapshot()}
    @Before fun reset() {
        runBlocking {app.repository.restore(Snapshot(products=listOf(p,q),preferences=Preferences(celebrate=false),
            templates=listOf(UsageTemplate(id="a",name="기본 구성 A",items=listOf(Item(p.id,p.name,2))),
                UsageTemplate(id="b",name="다른 구성 B",items=listOf(Item(p.id,p.name,3),Item(q.id,q.name,1)))),
            receipts=listOf(Receipt(id="initial",date=LocalDate.now().minusDays(1).toString(),lines=listOf(ReceiptLine(productId=p.id,quantity=20))))))}
        ui.waitUntil(10000) {ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=node.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching {parents[i].performScrollTo()}
        runCatching {node.performScrollTo()};return node
    }
    private fun click(text:String) {show(ui.onNodeWithText(text)).performClick()}
    private fun input(label:String,value:String) {show(ui.onNode(hasSetTextAction() and hasContentDescription(label))).performTextReplacement(value)}
    private fun hideKeyboard() {ui.runOnIdle {(ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)};ui.waitForIdle()}
    private fun back() {hideKeyboard();ui.onNodeWithContentDescription("뒤로").performClick()}
    private fun requests() {
        hideKeyboard();ui.onNode(hasText("재고") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        click("입고 요청 계산기")
    }
    private fun selectNextDate(value:LocalDate) {
        show(ui.onAllNodesWithText("날짜 선택")[1]).performClick()
        val months=ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1),value.withDayOfMonth(1)).toInt()
        repeat(months) {show(ui.onNodeWithContentDescription("다음 달")).performClick()}
        show(ui.onNodeWithContentDescription(value.toString())).performClick()
    }
    private fun savedPlan():ReplenishmentPlan {
        val s=snapshot()
        val input=ReplenishmentInput(visitDate=today(),nextVisitDate=LocalDate.now().plusDays(30).toString(),
            pattern=UsagePattern(mode="DIRECT",directPeriodDays=1,directItems=listOf(Item(p.id,p.name,1))),requestOverrides=mapOf(p.id to 30))
        val plan=ReplenishmentPlan(id="saved",input=input,calculation=calculateReplenishment(s,input,today()))
        runBlocking {app.repository.replenishmentPlan(plan)}
        return plan
    }
    @Test fun extraDaysAndItemsPersistAndManualFinalRemainsIndependent() {
        requests();click("새 입고 요청 계산");selectNextDate(LocalDate.now().plusDays(7))
        click("평소 사용 바꾸기");click("기본 구성 선택");click("기본 구성 A");click("이 사용으로 계산")
        click("3일")
        input("${p.name} 개별 추가분","1.5");hideKeyboard()
        ui.onNodeWithText("요청안 저장").assertIsNotEnabled()
        input("${p.name} 개별 추가분","5");hideKeyboard()
        ui.activityRule.scenario.recreate()
        show(ui.onNode(hasSetTextAction() and hasContentDescription("${p.name} 개별 추가분"))).assertTextContains("5")
        input("${p.name} 실제 요청할 수량","9");hideKeyboard()
        click("7일")
        show(ui.onNode(hasSetTextAction() and hasContentDescription("${p.name} 실제 요청할 수량"))).assertTextContains("9")
        click("요청안 저장")
        ui.waitUntil(10000) {snapshot().replenishmentPlans.size==1}
        val plan=snapshot().replenishmentPlans.single()
        assertEquals(2,plan.input.calculationVersion);assertEquals(5,plan.input.extraQuantities[p.id])
        assertEquals(19,plan.calculation.lines.first {it.productId==p.id}.suggested)
        assertEquals(9,plan.calculation.lines.first {it.productId==p.id}.requested)
        assertEquals(20,inventory(snapshot()).products.getValue(p.id).balance)
        click("${today()} 입고 요청");click("요청 수정");click("자동 계산값 적용");click("요청안 저장")
        ui.waitUntil(10000) {snapshot().replenishmentPlans.single().calculation.lines.first {it.productId==p.id}.requested==19}
    }
    @Test fun newWeeklyRequestWithoutHistoryHasAutomaticRemainderAndPersists() {
        requests();click("새 입고 요청 계산");selectNextDate(LocalDate.now().plusDays(7))
        click("평소 사용 바꾸기");click("기본 구성 선택");click("기본 구성 A")
        click("+ 다른 구성도 사용해요");click("다른 구성 B")
        show(ui.onNodeWithContentDescription("다른 구성 B 늘리기")).performClick()
        show(ui.onNodeWithText("나머지 5일 사용")).assertIsDisplayed()
        click("이 사용으로 계산");click("요청안 저장")
        ui.waitUntil(10000) {snapshot().replenishmentPlans.size==1}
        val plan=snapshot().replenishmentPlans.single()
        assertEquals(2,plan.input.pattern.alternatives.single().count)
        assertEquals("16",plan.calculation.lines.first {it.productId==p.id}.demand.label())
        assertEquals(2,plan.calculation.lines.first {it.productId==q.id}.requested)
        assertEquals(20,inventory(snapshot()).products.getValue(p.id).balance)
    }
    @Test fun visitDatePickersStackAndKeepAllSevenColumnsReadable() {
        requests();click("새 입고 요청 계산")
        val first=ui.onAllNodesWithText("날짜 선택")[0].getUnclippedBoundsInRoot()
        val second=ui.onAllNodesWithText("날짜 선택")[1].getUnclippedBoundsInRoot()
        assertTrue("방문일 선택은 위아래 두 줄이어야 합니다",second.top>=first.bottom)
        repeat(2) { index ->
            show(ui.onAllNodesWithText("날짜 선택")[index]).performClick()
            val weekdays=listOf("일","월","화","수","목","금","토").map {
                show(ui.onNode(hasText(it) and hasAnyAncestor(hasContentDescription("달력 요일")))).getUnclippedBoundsInRoot()
            }
            weekdays.zipWithNext().forEach { (left,right)->assertTrue(left.right<=right.left) }
            val month=LocalDate.now().withDayOfMonth(1)
            for(day in 10..month.lengthOfMonth()) {
                show(ui.onNodeWithContentDescription(month.withDayOfMonth(day).toString())).assertIsDisplayed()
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithText(day.toString(),useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
                val layout=layouts.single()
                assertEquals(1,layout.lineCount)
                assertTrue("두 자리 날짜가 칸 안에 들어가야 합니다",layout.getLineRight(0)-layout.getLineLeft(0)<=layout.size.width+1f)
            }
            click("날짜 선택 접기")
        }
        assertTrue(snapshot().replenishmentPlans.isEmpty())
    }
    @Test fun manualOverrideSurvivesHelpRecreationAndInvalidInputCanBeCorrected() {
        savedPlan();requests();click("${today()} 입고 요청");click("요청 수정")
        input("${p.name} 실제 요청할 수량","31");hideKeyboard()
        ui.onNodeWithContentDescription("이 화면 사용 안내").performClick()
        show(ui.onNodeWithText("전체 사용 안내")).assertExists()
        back()
        ui.activityRule.scenario.recreate()
        show(ui.onNode(hasSetTextAction() and hasContentDescription("${p.name} 실제 요청할 수량"))).assertTextContains("31")
        input("${p.name} 실제 요청할 수량","1000001");hideKeyboard()
        ui.onNodeWithText("요청안 저장").assertIsNotEnabled()
        input("${p.name} 실제 요청할 수량","32");hideKeyboard();click("요청안 저장")
        ui.waitUntil(10000) {snapshot().replenishmentPlans.single().calculation.lines.first {it.productId==p.id}.requested==32}
        assertEquals(20,inventory(snapshot()).products.getValue(p.id).balance)
    }
    @Test fun secondDeliveryAddsOnlyCheckedActualQuantityAndShowsRemaining() {
        val plan=savedPlan()
        runBlocking {app.repository.receipt(Receipt(id="first",date=today(),requestPlanId=plan.id,lines=listOf(ReceiptLine(productId=p.id,quantity=15))))}
        requests();click("${today()} 입고 요청");click("물품 받았어요")
        show(ui.onNodeWithContentDescription("${p.name} 받음")).performClick()
        input("${p.name} 이번에 받은 수량","10");hideKeyboard();click("받은 수량 저장")
        ui.waitUntil(10000) {snapshot().receipts.count {it.requestPlanId==plan.id}==2}
        assertEquals(45,inventory(snapshot()).products.getValue(p.id).balance)
        assertEquals(5,requestProgress(snapshot(),plan).first {it.productId==p.id}.remaining)
        click("물품 받았어요");show(ui.onNodeWithContentDescription("${p.name} 받음")).performClick()
        show(ui.onNode(hasSetTextAction() and hasContentDescription("${p.name} 이번에 받은 수량"))).assertTextContains("5")
        click("받은 수량 저장")
        ui.waitUntil(10000) {snapshot().receipts.count {it.requestPlanId==plan.id}==3}
        assertEquals(50,inventory(snapshot()).products.getValue(p.id).balance)
    }
    @Test fun offlineGuideSearchAndImageZoomReturnToUnsavedProduct() {
        ui.onNodeWithContentDescription("설정").performClick();click("사용 안내")
        show(ui.onNode(hasSetTextAction() and hasText("사용법 검색"))).performTextReplacement("나누어")
        hideKeyboard();click("받은 물품 확인 · 분할 입고")
        ui.waitUntil(10000) {ui.onAllNodesWithText("화면 크게 보기").fetchSemanticsNodes().isNotEmpty()}
        show(ui.onAllNodesWithText("화면 크게 보기").onFirst()).performClick()
        ui.onNodeWithContentDescription("확대 화면 닫기").performClick()
        back();back();click("품목 관리");click("+ 품목 추가")
        show(ui.onNode(hasSetTextAction() and hasText("제품명 · 농도 · 규격"))).performTextReplacement("작성 중인 품목")
        hideKeyboard();ui.onNodeWithContentDescription("이 화면 사용 안내").performClick();back()
        show(ui.onNode(hasSetTextAction() and hasText("제품명 · 농도 · 규격"))).assertTextContains("작성 중인 품목")
        assertEquals(2,snapshot().products.size)
    }
    @Test fun directPatternKeepsInputsThroughHelpAndDarkLandscapeRotation() {
        runBlocking {app.repository.preferences(snapshot().preferences.copy(darkMode="DARK"))}
        requests();click("새 입고 요청 계산");selectNextDate(LocalDate.now().plusDays(7))
        click("평소 사용 바꾸기");click("품목별 직접 입력");click("하루")
        show(ui.onNodeWithContentDescription("${p.name} 사용")).performClick()
        input("${p.name} 수량","3");hideKeyboard()
        ui.onNodeWithContentDescription("이 화면 사용 안내").performClick();back()
        show(ui.onNode(hasSetTextAction() and hasContentDescription("${p.name} 수량"))).assertTextContains("3")
        val orientation=ui.activity.requestedOrientation
        try {
            ui.activityRule.scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            ui.waitUntil(10000) {ui.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            input("${p.name} 수량","4");hideKeyboard()
            ui.onNodeWithText("이 사용으로 계산").assertIsDisplayed()
            click("이 사용으로 계산");click("요청안 저장")
            ui.waitUntil(10000) {snapshot().replenishmentPlans.size==1}
            val plan=snapshot().replenishmentPlans.single()
            assertEquals("DIRECT",plan.input.pattern.mode)
            assertEquals(8,plan.calculation.lines.first {it.productId==p.id}.requested)
            assertEquals(20,inventory(snapshot()).products.getValue(p.id).balance)
        } finally {
            ui.activityRule.scenario.onActivity {it.requestedOrientation=orientation}
        }
    }
}
