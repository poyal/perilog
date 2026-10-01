package com.poyal.perilog.capture

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poyal.perilog.MainActivity
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Manual documentation capture, only compiled with -PcaptureScreenshots. Uses synthetic data. */
@RunWith(AndroidJUnit4::class)
class DocumentationCapture {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val today=LocalDate.now()
    private val products=listOf(
        Product(id="d15",name="투석액 1.5%",color=0xFF2167B8),
        Product(id="d25",name="투석액 2.5%",color=0xFF47956E),
        Product(id="d425",name="투석액 4.25%",color=0xFFEF8752),
        Product(id="d75",name="투석액 7.5%",color=0xFF30343B),
        Product(id="cassette",name="카세트",kind="소모품",color=0xFF647789),
        Product(id="line",name="손투석 라인",kind="소모품",color=0xFF8772B5))
    private val composition=listOf(0,1,4).map { Item(products[it].id,products[it].name,1) }
    private fun seed(completeToday:Boolean=false) {
        val history=(1..14).map { n->
            Treatment(id="day-$n",date=today.minusDays(n.toLong()).toString(),weightGrams=62000+(n%5)*100,
                systolic=118+n%8,diastolic=76+n%5,initialDrain=2180+n*10,machineUf=460+n*12,dwellMinutes=110,
                items=composition,usageConfirmed=true,saved=true,createdAt=100L+n)
        }
        val current=Treatment(id="today",date=today.toString(),weightGrams=62300,systolic=120,diastolic=80,
            initialDrain=if(completeToday)2300 else null,machineUf=if(completeToday)600 else null,
            items=composition,usageConfirmed=true,saved=true,sourceDate=today.minusDays(1).toString(),createdAt=1000L)
        val entries=history+current
        val receipt=Receipt(id="delivery",date=today.minusDays(20).toString(),createdAt=1,
            lines=products.map{ReceiptLine(id="lot-${it.id}",productId=it.id,quantity=if(it.kind=="투석액")30 else 40,
                expiry=if(it.id=="d15")today.plusDays(3).toString()else null)},memo="월 정기 물품 입고")
        runBlocking { app.repository.restore(Snapshot(products=products,
            templates=listOf(UsageTemplate(id="night",name="밤 투석 · 1.5 + 2.5",items=composition),
                UsageTemplate(id="manual",name="추가투석 · 1.5 + 라인",items=listOf(Item("d15","투석액 1.5%",1),Item("line","손투석 라인",1)))),
            treatments=entries,usages=entries.map{Usage(it.id,it.date,it.items,it.kind,it.createdAt)},receipts=listOf(receipt),
            preferences=Preferences(celebrate=false,darkMode="LIGHT"))) }
    }
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {val parents=node.onAncestors().filter(hasScrollAction())
        for(index in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[index].performScrollTo()}
        runCatching{node.performScrollTo()}
        return node}
    private fun click(text:String) {show(ui.onNodeWithText(text)).performClick()}
    private fun tab(text:String) {ui.onNodeWithText(text,useUnmergedTree=true).performClick()}
    private fun back() {ui.onNodeWithContentDescription("뒤로").performClick()}
    private fun input(label:String,value:String) {ui.onNode(hasSetTextAction() and (hasText(label) or hasContentDescription(label))).performScrollTo().performTextReplacement(value)}
    private fun shot(name:String) {
        ui.runOnIdle { (ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0) }
        ui.waitForIdle(); SystemClock.sleep(650)
        val directory=File(app.filesDir,"manual-screenshots").apply{mkdirs()}
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun captureManual() {
        seed()
        ui.waitUntil(10000){ui.onAllNodesWithText("기록하기").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick()
        click("밝게");click("표시·알림·잠금 설정 저장");back()
        shot("01-home")
        click("기록하기");click("수정")
        shot("02-before-treatment")
        click("접기");click("구성 변경")
        shot("03-usage-template")
        click("구성 선택 접기")
        input("초기배액량","2300");input("기계 제수량","600")
        ui.onNodeWithText("투석 기록").performScrollTo()
        shot("04-after-treatment")
        show(ui.onNodeWithContentDescription("제수량 도움말")).performClick()
        shot("05-calculation")
        click("닫기")
        click("기록 저장")
        ui.waitUntil(10000){ui.onAllNodesWithText("오늘도 기록을 마쳤어요").fetchSemanticsNodes().isNotEmpty()}
        ui.waitUntil(10000){ui.onAllNodesWithText("기록을 저장했어요").fetchSemanticsNodes().isEmpty()}
        shot("06-completed")
        click("추가투석");click("구성 변경");click("추가투석 · 1.5 + 라인")
        input("배액무게","2150")
        ui.onNodeWithText("배액 기록 · 선택").performScrollTo()
        shot("07-manual-treatment")
        back()
        tab("기록");shot("08-record-list")
        click("캘린더")
        if(today.dayOfMonth<8) {click("이전");click("28")}
        shot("09-calendar")
        tab("통계");shot("10-statistics")
        show(ui.onNodeWithContentDescription("표시 방식")).performClick();click("표");shot("11-statistics-table")
        tab("재고");shot("12-inventory")
        click("입고 이력");shot("13-receipt-history")
        click("+ 일괄 입고 등록")
        shot("14-bulk-receipt")
        ui.onNodeWithText("취소").performClick();back()
        click("품목 관리 · 색상");click("투석액 1.5%")
        shot("15-product-settings")
        click("컬러 피커 · 색 추가");show(ui.onNode(hasSetTextAction() and hasText("HEX 색상")));shot("16-color-picker")
        click("컬러 피커 접기");click("취소");back()
        click("사용 구성 관리");shot("17-templates")
        ui.onAllNodesWithText("수정").onFirst().performClick();shot("22-template-editor");back()
        back();tab("홈");ui.onNodeWithContentDescription("설정").performClick()
        shot("18-settings")
        ui.onNodeWithText("지금 백업").performScrollTo()
        shot("19-backup")
        back()
        tab("기록");click("표");shot("23-record-table")
        tab("홈")
        val before=runBlocking{app.repository.snapshot()}
        runBlocking{app.repository.restore(before.copy(treatments=before.treatments.filterNot{it.id=="day-1"},usages=before.usages.filterNot{it.id=="day-1"}))}
        ui.waitUntil(10000){ui.onAllNodesWithText("어제의 기록").fetchSemanticsNodes().isNotEmpty()}
        shot("21-yesterday-prompt")
        seed(completeToday=true)
        val p=runBlocking{app.repository.snapshot().preferences}
        runBlocking{app.repository.preferences(p.copy(darkMode="DARK"))}
        SystemClock.sleep(800)
        shot("20-dark-mode")
    }
}
