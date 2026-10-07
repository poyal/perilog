package com.poyal.perilog.capture

import android.graphics.Bitmap
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

/** Synthetic fixtures, only on a disposable capture emulator. */
class ImprovementsCapture {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private fun show(n:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=n.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching {parents[i].performScrollTo()}
        runCatching {n.performScrollTo()};return n
    }
    private fun click(text:String)=show(ui.onNodeWithText(text)).performClick()
    private fun shot(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(400)
        val dir=File(app.filesDir,"manual-screenshots").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun captureVitalActions() {
        val product=Product(id="fluid",name="투석액 1.5%")
        val template=UsageTemplate(id="base",name="기본 구성",items=listOf(Item(product.id,product.name,2)))
        val record=Treatment(id="vital-example",date=today(),weightGrams=62300,systolic=120,diastolic=80,
            initialDrain=2200,machineUf=800,saved=true,usageConfirmed=true,items=template.items,
            usageTemplateId=template.id,usageTemplateName=template.name)
        runBlocking {app.repository.restore(Snapshot(products=listOf(product),templates=listOf(template),treatments=listOf(record),
            usages=listOf(Usage(record.id,record.date,record.items,record.kind,record.createdAt)),
            preferences=Preferences(celebrate=false,darkMode=InstrumentationRegistry.getArguments().getString("theme") ?: "LIGHT")))}
        ui.waitUntil(10000) {ui.onAllNodesWithText("오늘 기록 확인").fetchSemanticsNodes().isNotEmpty()}
        click("오늘 기록 확인")
        show(ui.onNodeWithContentDescription("활력 상태 수정"));shot("92-vital-summary")
        ui.onNodeWithContentDescription("활력 상태 수정").performClick();shot("02-before-treatment")
        ui.onNodeWithContentDescription("활력 상태 저장").performClick()
        ui.onNodeWithText("62.3 kg").assertExists()
        show(ui.onNodeWithText("이번 기록만 수량 조정"));shot("03-usage-template")
    }
    @Test fun captureForecastExtraAndTable() {
        val now=LocalDate.now()
        val p=Product(id="fluid",name="투석액 1.5%")
        val q=Product(id="cap",name="보호 캡",color=0xFF47956E)
        val records=(1..4).map {n->Treatment(id="record$n",date=now.minusDays(n.toLong()).toString(),
            weightGrams=62000+n*100,systolic=110,diastolic=75,initialDrain=2200,machineUf=800,
            saved=true,usageConfirmed=true,items=listOf(Item(p.id,p.name,2),Item(q.id,q.name,1)))}
        val source=Snapshot(products=listOf(p,q),treatments=records,
            usages=records.map {Usage(it.id,it.date,it.items,it.kind,it.createdAt)},
            counts=listOf(StockCount(productId=p.id,quantity=30),StockCount(productId=q.id,quantity=6)),
            appointments=listOf(Appointment(id="visit",date=now.plusDays(10).toString(),time="09:30",
                departments=listOf(Department(id="dept",name="신장내과")),careItems=listOf(CareTask(name="피검사",iconKey="blood")))),
            preferences=Preferences(celebrate=false,darkMode=InstrumentationRegistry.getArguments().getString("theme") ?: "LIGHT"))
        val input=ReplenishmentInput(visitDate=now.toString(),nextVisitDate=now.plusDays(10).toString(),
            calculationVersion=2,bufferDays=3,extraQuantities=mapOf(p.id to 5))
        val plan=ReplenishmentPlan(id="extra",input=input,calculation=calculateReplenishment(source,input,today()))
        runBlocking {app.repository.restore(source.copy(replenishmentPlans=listOf(plan)))}
        ui.waitUntil(10000) {ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
        show(ui.onNodeWithText("피검사"));shot("71-appointment-forecast")
        show(ui.onNodeWithTag("home-appointment")).performClick();shot("75-appointment-detail")
        show(ui.onNodeWithTag("appointment-menu-visit")).performClick();shot("88-appointment-detail-actions")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);ui.waitForIdle()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithTag("home-stock-shortage").performClick();shot("72-stock-forecast")
        show(ui.onNodeWithTag("forecast-line-${q.id}"));shot("73-stock-forecast-detail")
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithContentDescription("설정").performClick();click("병원 일정")
        show(ui.onNodeWithTag("forecast-visit"));shot("39-appointment-list")
        show(ui.onNodeWithTag("appointment-menu-visit")).performClick();shot("89-appointment-list-actions")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);ui.waitForIdle()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNode(hasText("재고") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        click("입고 요청 계산기");click("${today()} 입고 요청");shot("48-request-detail");click("요청 수정")
        shot("49-request-dates")
        show(ui.onNodeWithText("직접 입력은 0~365일이에요.",substring=true));shot("74-request-extra-days")
        show(ui.onNodeWithContentDescription("${p.name} 실제 요청할 수량"));shot("50-request-calculation")
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNode(hasText("기록") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        click("표");shot("23-record-table")
        click("100%로");show(ui.onNodeWithTag("record-cell-record1-0")).performClick();shot("44-record-table-zoomed")
    }
}
