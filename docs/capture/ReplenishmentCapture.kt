package com.poyal.perilog.capture

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.MainActivity
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Synthetic, disposable emulator fixtures; never run against the user's preview app. */
class ReplenishmentCapture {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val now=LocalDate.now()
    private val p=Product(id="d15",name="투석액 1.5%")
    private val q=Product(id="d25",name="투석액 2.5%",color=0xFF47956E)
    private fun show(n:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=n.onAncestors().filter(hasScrollAction());for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching {parents[i].performScrollTo()}
        runCatching {n.performScrollTo()};return n
    }
    private fun click(text:String) {show(ui.onNodeWithText(text)).performClick()}
    private fun back() {ui.onNodeWithContentDescription("뒤로").performClick()}
    private fun shot(name:String) {
        ui.runOnIdle {(ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)}
        ui.waitForIdle();android.os.SystemClock.sleep(500)
        val dir=File(app.filesDir,"manual-screenshots").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun captureRequests() {
        val history=(1..18).map { n->Usage("history-$n",now.minusDays(n.toLong()).toString(),listOf(Item(p.id,p.name,if(n%2==0)1 else 2),Item(q.id,q.name,1)),"MACHINE",n.toLong()) }
        val templates=listOf(UsageTemplate(id="a",name="기본 구성 A",items=listOf(Item(p.id,p.name,1))),
            UsageTemplate(id="b",name="다른 구성 B",items=listOf(Item(q.id,q.name,2)),color=q.color),
            UsageTemplate(id="c",name="다른 구성 C",items=listOf(Item(p.id,p.name,1),Item(q.id,q.name,1)),color=0xFFEF8752))
        val source=Snapshot(products=listOf(p,q),templates=templates,usages=history,preferences=Preferences(celebrate=false),
            counts=listOf(StockCount(productId=p.id,date=today(),quantity=21),StockCount(productId=q.id,date=today(),quantity=8)))
        val input=ReplenishmentInput(visitDate=now.plusDays(6).toString(),nextVisitDate=now.plusDays(34).toString(),calculationVersion=2)
        val plan=ReplenishmentPlan(id="example-request",input=input,calculation=calculateReplenishment(source,input,today()),memo="두 번에 나누어 받을 예정이에요")
        runBlocking {app.repository.restore(source.copy(replenishmentPlans=listOf(plan)))}
        ui.waitUntil(10000) {ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(hasText("재고") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick();shot("12-inventory")
        runBlocking {app.repository.preferences(source.preferences.copy(darkMode="DARK"))};shot("28-dark-inventory")
        runBlocking {app.repository.preferences(source.preferences.copy(darkMode="LIGHT"))}
        show(ui.onNodeWithText("입고 요청 계산기"));shot("46-request-entry")
        click("입고 요청 계산기");shot("47-request-list")
        click("${input.visitDate} 입고 요청");shot("48-request-detail")
        click("요청 수정");shot("49-request-dates")
        show(ui.onAllNodesWithText("날짜 선택")[0]).performClick();shot("56-request-calendar")
        click("날짜 선택 접기")
        show(ui.onNodeWithTag("request-line-${p.id}"));shot("50-request-calculation")
        click("평소 사용 바꾸기");click("사용 구성");click("기본 구성 선택");click("기본 구성 A")
        click("+ 다른 구성도 사용해요");click("다른 구성 B");show(ui.onNodeWithContentDescription("다른 구성 B 늘리기")).performClick()
        click("+ 다른 구성도 사용해요");click("다른 구성 C");repeat(2) {show(ui.onNodeWithContentDescription("다른 구성 C 늘리기")).performClick()}
        show(ui.onNodeWithText("기본으로 사용하는 구성"));shot("51-request-patterns")
        click("이 사용으로 계산");click("취소");click("확인")
        val received=Receipt(id="first-delivery",date=today(),requestPlanId=plan.id,lines=listOf(ReceiptLine(productId=p.id,quantity=15),ReceiptLine(productId=q.id,quantity=12)))
        runBlocking {app.repository.receipt(received)}
        click("물품 받았어요");show(ui.onNodeWithContentDescription("${p.name} 받음")).performClick();shot("52-request-receive")
        click("받은 수량 저장");show(ui.onNodeWithText("이전에 받은 내역"));shot("53-request-deliveries")
    }
    @Test fun captureGuide() {
        runBlocking {app.repository.preferences(app.repository.snapshot().preferences.copy(darkMode="LIGHT"))}
        ui.waitUntil(10000) {ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick();click("사용 안내");shot("54-guide-index")
        click("입고 요청 계산");shot("55-guide-request")
    }
}
