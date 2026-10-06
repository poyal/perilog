package com.poyal.perilog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StockForecastFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val p=Product(id="forecast-fluid",name="예측 물품")
    private val date=LocalDate.now()
    private fun snapshot()=runBlocking {app.repository.snapshot()}
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=node.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching {parents[i].performScrollTo()}
        runCatching {node.performScrollTo()};return node
    }
    private fun click(text:String)=show(ui.onNodeWithText(text)).performClick()
    private fun openForecast() {
        ui.onNodeWithContentDescription("설정").performClick();click("병원 일정");click("예상 잔량 계산 보기")
    }
    @Before fun fixture() {
        runBlocking {app.repository.restore(Snapshot(products=listOf(p),preferences=Preferences(celebrate=false),
            templates=listOf(UsageTemplate(name="매일 두 개",items=listOf(Item(p.id,p.name,2)))),
            appointments=listOf(Appointment(id="visit",date=date.plusDays(10).toString(),time="09:00")),
            counts=listOf(StockCount(productId=p.id,date=date.toString(),quantity=30,createdAt=1)),
            usages=(1..4).map {Usage("h$it",date.minusDays(it.toLong()).toString(),listOf(Item(p.id,p.name,2)),"MACHINE",0)}))}
        ui.waitUntil(10000) {ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun trendDetailsPreserveStateAndUpdateAfterRealStockChange() {
        ui.onNodeWithTag("home-stock-shortage").assertDoesNotExist()
        ui.onNodeWithText("지금과 같은 사용 추세라면").assertDoesNotExist()
        ui.onNodeWithText("예상 잔량 계산 보기").assertDoesNotExist()
        openForecast()
        show(ui.onNodeWithText("지금과 같은 사용 추세라면")).assertIsDisplayed()
        show(ui.onNodeWithText("방문일에 약 10 EA 남을 예상")).assertIsDisplayed()
        ui.onNodeWithContentDescription("이 화면 사용 안내").performClick()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.activityRule.scenario.recreate()
        show(ui.onNodeWithText("방문일에 약 10 EA 남을 예상")).assertIsDisplayed()
        runBlocking {app.repository.adjustment(StockAdjustment(productId=p.id,date=date.toString(),delta=-15,memo="검사용 차감",createdAt=2))}
        ui.waitUntil(10000) {ui.onAllNodesWithText("방문 전 약 5 EA 부족 예상").fetchSemanticsNodes().isNotEmpty()}
        show(ui.onNodeWithText("방문 전 약 5 EA 부족 예상")).assertIsDisplayed()
        assertEquals(15,inventory(snapshot()).products.getValue(p.id).balance)
        repeat(3) {ui.onNodeWithContentDescription("뒤로").performClick()}
        val icon=show(ui.onNodeWithTag("home-stock-shortage"))
        icon.assertIsDisplayed()
        val iconBounds=icon.fetchSemanticsNode().boundsInRoot
        val timeBounds=ui.onNodeWithTag("home-next-appointment-time",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(iconBounds.left>=timeBounds.right)
        assertTrue(kotlin.math.abs(iconBounds.center.y-timeBounds.center.y)<2f)
        icon.performClick()
        show(ui.onNodeWithText("방문 전 약 5 EA 부족 예상")).assertIsDisplayed()
        ui.onNodeWithContentDescription("뒤로").performClick()
        runBlocking {app.repository.count(StockCount(productId=p.id,date=date.toString(),quantity=20,createdAt=3))}
        ui.waitUntil(10000) {ui.onAllNodesWithTag("home-stock-shortage").fetchSemanticsNodes().isEmpty()}
        runBlocking {app.repository.restore(snapshot().copy(usages=emptyList()))}
        ui.onNodeWithTag("home-stock-shortage").assertDoesNotExist()
    }
    @Test fun homeAppointmentOpensReadOnlyDetailsAndReturnsAfterEditing() {
        val before=snapshot()
        show(ui.onNodeWithTag("home-appointment")).performClick()
        ui.onNodeWithText("병원 일정 상세").assertIsDisplayed()
        show(ui.onNodeWithText("예약일 기준 재고")).assertIsDisplayed()
        assertEquals(before,snapshot().copy(exportedAt=before.exportedAt))
        click("예상 잔량 계산 보기")
        show(ui.onNodeWithText("방문일에 약 10 EA 남을 예상")).assertIsDisplayed()
        ui.onNodeWithContentDescription("뒤로").performClick()
        click("일정 수정")
        show(ui.onNode(hasSetTextAction() and hasText("메모"))).performTextReplacement("상세에서 수정한 메모")
        click("저장")
        ui.waitUntil(10000) {ui.onAllNodesWithText("병원 일정 상세").fetchSemanticsNodes().isNotEmpty()}
        show(ui.onNodeWithText("상세에서 수정한 메모")).assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("병원 일정 상세").assertIsDisplayed()
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.onBackPressed()}
        show(ui.onNodeWithTag("home-appointment")).assertIsDisplayed()
        ui.onNodeWithText("병원 일정 상세").assertDoesNotExist()
        show(ui.onNodeWithContentDescription("병원 일정 추가")).performClick()
        ui.onNodeWithText("병원 일정 등록").assertIsDisplayed()
        assertEquals(1,snapshot().appointments.size)
    }
    @Test fun manualPatternIsSharedAndDoesNotModifyStockOrSavedRequests() {
        val before=snapshot()
        openForecast();click("평소 사용 바꾸기")
        click("사용 구성");click("기본 구성 선택");click("매일 두 개")
        ui.activityRule.scenario.recreate()
        click("이 사용으로 계산")
        ui.waitUntil(10000) {snapshot().preferences.stockForecastPattern.mode=="WEEKLY"}
        show(ui.onNodeWithText("지정한 사용 구성대로라면")).assertIsDisplayed()
        show(ui.onNodeWithText("방문일에 약 10 EA 남을 예상")).assertIsDisplayed()
        assertEquals(before.counts,snapshot().counts);assertEquals(before.usages,snapshot().usages)
        assertEquals(before.replenishmentPlans,snapshot().replenishmentPlans)
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithText("병원 일정 관리").assertIsDisplayed()
        show(ui.onNodeWithText("지정한 사용 구성대로라면")).assertIsDisplayed()
    }
}
