package com.poyal.perilog.capture

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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
    private val departments=listOf(Department("kidney","신장내과",0xFF2167B8),Department("heart","심장내과",0xFF47956E))
    private val careItems=listOf(CareTemplate("blood","피검사",iconKey="blood"),CareTemplate("medicine","약 처방",iconKey="medicine"))
    private fun seed(completeToday:Boolean=false) {
        val history=(1..14).map { n->
            Treatment(id="day-$n",date=today.minusDays(n.toLong()).toString(),weightGrams=62000+(n%5)*100,
                systolic=118+n%8,diastolic=76+n%5,initialDrain=2180+n*10,machineUf=460+n*12,dwellMinutes=110,
                items=composition,usageConfirmed=true,saved=true,memo="기록 수첩에 적어 둔 내용과 함께 확인했어요.",createdAt=100L+n)
        }
        val current=Treatment(id="today",date=today.toString(),weightGrams=62300,systolic=120,diastolic=80,
            initialDrain=if(completeToday)2300 else null,machineUf=if(completeToday)600 else null,
            items=composition,usageConfirmed=true,saved=true,createdAt=1000L)
        val entries=history+current
        val receipt=Receipt(id="delivery",date=today.minusDays(20).toString(),createdAt=1,
            lines=products.map{ReceiptLine(id="lot-${it.id}",productId=it.id,quantity=if(it.kind=="투석액")30 else 40,
                expiry=null)},memo="월 정기 물품 입고")
        runBlocking { app.repository.restore(Snapshot(products=products,
            templates=listOf(UsageTemplate(id="night",name="밤 투석 · 1.5 + 2.5",items=composition),
                UsageTemplate(id="manual",name="추가투석 · 1.5 + 라인",items=listOf(Item("d15","투석액 1.5%",1),Item("line","손투석 라인",1)))),
            treatments=entries,usages=entries.map{Usage(it.id,it.date,it.items,it.kind,it.createdAt)},receipts=listOf(receipt),
            preferences=Preferences(celebrate=false,darkMode="LIGHT"),departments=departments,careTemplates=careItems,
            appointments=listOf(Appointment(id="next-visit",date=today.plusDays(7).toString(),departments=departments,
                departmentTimes=mapOf("kidney" to "09:30","heart" to "10:20"),
                careItems=careItems.map{CareTask(it.id,it.name,it.iconKey)},memo="진료 전 피검사 · 기록 수첩 챙기기")),
            contacts=listOf(Contact("room","투석실","032-000-0000",emoji="🏥"),
                Contact("center","고객센터","1577-0000",emoji="☎️",allowSms=false),
                Contact("nurse","간호사","010-0000-0000",emoji="🧑‍⚕️")))) }
    }
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {val parents=node.onAncestors().filter(hasScrollAction())
        for(index in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[index].performScrollTo()}
        runCatching{node.performScrollTo()}
        return node}
    private fun click(text:String) {show(ui.onNodeWithText(text)).performClick()}
    private fun tab(text:String) {
        ui.runOnIdle { (ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0) }
        val matcher=hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ui.waitUntil(10000){ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(matcher).performClick()
    }
    private fun back() {ui.onNodeWithContentDescription("뒤로").performClick()}
    private fun input(label:String,value:String) {ui.onNode(hasSetTextAction() and (hasText(label) or hasContentDescription(label))).performScrollTo().performTextReplacement(value)}
    private fun shot(name:String) {
        ui.runOnIdle { (ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0) }
        ui.waitForIdle(); SystemClock.sleep(650)
        val directory=File(app.filesDir,"manual-screenshots").apply{mkdirs()}
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun captureSettingsNavigation() {
        seed()
        ui.waitUntil(15000){ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick();shot("18-settings")
        click("화면·표시");shot("30-settings-toggles");back()
        show(ui.onNodeWithText("데이터 초기화"));shot("80-settings-data")
        show(ui.onNodeWithText("사용자 색상"));shot("85-settings-treatment")
        show(ui.onNodeWithText("연락처"));shot("84-settings-hospital")
        show(ui.onNodeWithText("앱 정보·문의"));shot("86-settings-help")
        click("업데이트");shot("29-about-update")
    }
    @Test fun captureUsabilityChanges() {
        seed()
        ui.waitUntil(15000){ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick()
        click("홈 화면 위젯");shot("59-widget-settings");back()
        click("화면·표시");shot("30-settings-toggles");back()
        click("알림");shot("78-notification-settings");back();back()
        click("기록하기");show(ui.onNodeWithText("이번 기록만 수량 조정"));shot("03-usage-template");back()
        click("D-7");click("일정 수정")
        show(ui.onAllNodesWithText("시간 지정")[0]).performClick();click("확인")
        show(ui.onNodeWithText("치료 항목 · 여러 개 선택"));shot("32-appointment-editor")
    }
    @Test fun captureReleaseInformation() {
        seed()
        ui.waitUntil(10000){ui.onAllNodesWithText("기록하기").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick();click("앱 정보·문의")
        shot("25-about")
        runBlocking { app.repository.preferences(app.repository.snapshot().preferences.copy(darkMode="DARK")) }
        SystemClock.sleep(800);shot("26-about-dark");back();click("업데이트");shot("29-about-update")
    }
    @Test fun captureChartsAndTable() {
        seed(completeToday=true)
        ui.waitUntil(10000){ui.onAllNodesWithText("오늘 기록 확인").fetchSemanticsNodes().isNotEmpty()}
        tab("통계");shot("10-statistics")
        show(ui.onNodeWithContentDescription("표시 방식")).performClick();click("항목별 도표");shot("11-statistics-metric")
        show(ui.onNodeWithTag("chart-dates-WEIGHT"));shot("45-statistics-vitals")
        show(ui.onNodeWithContentDescription("통계 기간")).performClick();click("기간 지정");shot("43-date-range-picker")
        ui.onNodeWithContentDescription("기간 선택 취소").performClick()
        tab("기록");click("표");shot("23-record-table")
        click("100%로")
        ui.onNodeWithTag("record-cell-today-4").assertIsDisplayed().performClick()
        ui.onNodeWithTag("record-cell-detail").assertIsDisplayed();shot("44-record-table-zoomed")
        back()
    }
    @Test fun captureContactEditor() {
        seed()
        ui.waitUntil(10000){ui.onAllNodesWithText("기록하기").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick()
        click("연락처");show(ui.onNodeWithContentDescription("투석실 더보기")).performClick();click("수정")
        shot("34-contact-editor")
    }
    @Test fun captureManual() {
        seed()
        ui.waitUntil(10000){ui.onAllNodesWithText("기록하기").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick()
        click("화면·표시");click("밝게");click("화면 설정 저장");back();back()
        shot("01-home")
        click("기록하기");click("수정")
        shot("02-before-treatment")
        click("접기")
        shot("03-usage-template")
        input("초기배액량","2300");input("기계 제수량","600")
        ui.onNodeWithText("투석 기록").performScrollTo()
        shot("04-after-treatment")
        show(ui.onNodeWithContentDescription("제수량 도움말")).performClick()
        shot("05-calculation")
        click("닫기")
        click("평균저류시간 · 메모 추가")
        input("메모","기록 수첩을 함께 확인했어요.\n사용 물품을 정리했어요.\n다음 진료 때 보여 드릴 내용을 적었어요.\n필요한 준비물을 다시 확인할 예정이에요.")
        show(ui.onNodeWithText("평균저류시간 · 메모 접기"));shot("41-treatment-memo")
        click("평균저류시간 · 메모 접기")
        click("기록 저장")
        ui.waitUntil(10000){ui.onAllNodesWithText("오늘도 기록을 마쳤어요").fetchSemanticsNodes().isNotEmpty()}
        ui.waitUntil(10000){ui.onAllNodesWithText("기록을 저장했어요").fetchSemanticsNodes().isEmpty()}
        shot("06-completed")
        tab("기록");ui.onNodeWithContentDescription("기록 추가").performClick();click("추가투석 기록 추가");click("추가투석 · 1.5 + 라인")
        input("배액무게","2150")
        ui.onNodeWithText("배액 기록 · 선택").performScrollTo()
        shot("07-manual-treatment")
        back()
        tab("기록");shot("08-record-list")
        click("더 보기");show(ui.onNodeWithText("접기"));shot("42-record-memo-expanded");click("접기")
        click("캘린더")
        if(today.dayOfMonth<8) {click("이전");click("28")}
        shot("09-calendar")
        tab("통계");shot("10-statistics")
        show(ui.onNodeWithContentDescription("표시 방식")).performClick();click("항목별 도표");shot("11-statistics-metric")
        tab("재고");shot("12-inventory")
        click("이력");show(ui.onNodeWithContentDescription("이력 종류")).performClick()
        ui.onNode(hasText("입고") and hasClickAction() and hasAnyAncestor(isPopup())).performClick();shot("13-receipt-history")
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
        click("자동 백업");ui.onNodeWithText("지금 백업").performScrollTo()
        shot("19-backup")
        back();back()
        tab("기록");click("표");shot("23-record-table");back()
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
        click("오늘 기록 확인")
        show(ui.onNodeWithText("투석 기록"));shot("27-dark-treatment")
        back();tab("재고");shot("28-dark-inventory")
        tab("홈");ui.onNodeWithContentDescription("설정").performClick()
        click("앱 정보·문의");shot("26-about-dark")
        back();click("업데이트");shot("29-about-update")
        back();back()
        runBlocking { app.repository.preferences(app.repository.snapshot().preferences.copy(darkMode="LIGHT")) }
        ui.onNodeWithContentDescription("설정").performClick();click("앱 정보·문의")
        shot("25-about")
        back();back();tab("홈")
        show(ui.onNodeWithContentDescription("간호사 연락처"));shot("31-home-hospital-contacts")
        show(ui.onNodeWithContentDescription("투석실 연락처")).performClick();shot("35-contact-actions")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);ui.waitForIdle()
        ui.onNodeWithContentDescription("설정").performClick()
        click("병원 일정");shot("39-appointment-list")
        show(ui.onNodeWithContentDescription("next-visit 일정 수정")).performClick()
        click("날짜 선택");show(ui.onNodeWithText("예약일 ",substring=true));shot("40-appointment-calendar");click("날짜 선택 접기")
        show(ui.onNode(hasSetTextAction() and hasText("신장내과 예약시간 · HH:mm")));shot("32-appointment-editor");back();back()
        click("검사·치료 항목");shot("33-treatment-items");back()
        click("연락처");show(ui.onNodeWithContentDescription("투석실 더보기")).performClick();click("수정");shot("34-contact-editor");back();back()
        click("화면·표시");shot("30-settings-toggles");back();back()
        tab("재고");click("투석액 1.5%");shot("37-stock-detail")
        click("수량 추가·차감");input("변경 수량","1");input("변경 사유 · 필수","포장 손상")
        shot("36-stock-adjustment");click("조정 저장");click("확인");back()
        click("이력");shot("38-stock-history")
    }
}
