package com.poyal.perilog

import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

/** Device E2E: drive real Compose screens and verify persisted Room data/stock, without APIs. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val failureCapture=object:TestWatcher() {
        override fun failed(e:Throwable,description:Description) {runCatching{screenshot("failure-${description.methodName}.png")}}
    }
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val p=Product(id="test-fluid",name="투석액 1.5% · 테스트",color=0xFF2167B8)
    private val yesterday get()=LocalDate.now().minusDays(1).toString()
    private fun snapshot()=runBlocking{app.repository.snapshot()}
    @Before fun reset() {
        runBlocking {app.repository.restore(Snapshot(products=listOf(p),
            preferences=Preferences(darkMode="LIGHT",celebrate=false),
            templates=listOf(UsageTemplate(id="night",name="밤 구성",items=listOf(Item(p.id,p.name,2)))),
            receipts=listOf(Receipt(date=LocalDate.now().minusDays(10).toString(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))))}
        await("Home after fixture restore"){compose.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun await(description:String="Expected application state",timeoutMs:Long=10000,condition:()->Boolean) {
        try {compose.waitUntil(timeoutMs,condition)}
        catch(error:ComposeTimeoutException){throw AssertionError("$description was not ready after $timeoutMs ms",error)}
    }
    private fun node(text:String)=compose.onNodeWithText(text)
    private fun show(n:SemanticsNodeInteraction):SemanticsNodeInteraction {val parents=n.onAncestors().filter(hasScrollAction())
        for(index in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[index].performScrollTo()}
        runCatching{n.performScrollTo()}
        return n}
    private fun click(text:String) {show(node(text)).performClick()}
    private fun tab(text:String) {compose.onNodeWithText(text,useUnmergedTree=true).performClick()}
    private fun field(label:String)=compose.onNode(hasSetTextAction() and (hasText(label) or hasContentDescription(label)))
    private fun input(label:String,value:String) {show(field(label)).performTextReplacement(value)}
    private fun back() {compose.onNodeWithContentDescription("뒤로").performClick()}
    private fun hideKeyboard() {compose.runOnIdle{(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)};compose.waitForIdle()}
    private fun showKeyboard(label:String,phase:String) {
        await("$phase window focus",30000){compose.activity.hasWindowFocus()}
        show(field(label)).performClick()
        compose.runOnIdle {
            val activity=compose.activity
            WindowCompat.getInsetsController(activity.window,activity.window.decorView).show(WindowInsetsCompat.Type.ime())
        }
        await("$phase software keyboard visible",30000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true
        }
        await("$phase navigation hidden above keyboard",30000) {
            compose.onAllNodesWithText("재고",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()
        }
    }
    private fun stock(q:Int) {assertEquals(q,inventory(snapshot()).products.getValue(p.id).balance)}
    private fun openTemplates(){compose.onNodeWithContentDescription("설정").performClick();click("사용 구성 관리")}
    private fun beforeAndTemplate() {
        input("몸무게","62.3");input("수축기 혈압","120");input("이완기 혈압","80")
        click("구성 변경");click("밤 구성")
    }
    @Test fun machineEntryAndAdditionalUseWorkOffline() {
        click("오늘 기록 시작");beforeAndTemplate()
        input("초기배액량","2300");input("기계 제수량","600")
        show(node("900 mL")).assertIsDisplayed()
        click("기록 저장");await{snapshot().treatments.size==1}
        node("오늘도 기록을 마쳤어요").assertExists()
        assertEquals(900,snapshot().treatments.single().totalUf());stock(8)
        click("추가투석");click("구성 변경");click("밤 구성");click("기록 저장")
        await{snapshot().treatments.size==2}
        assertTrue(snapshot().treatments.single{it.kind=="MANUAL"}.complete())
        assertNull(snapshot().treatments.single{it.kind=="MANUAL"}.manualDrain);stock(6)
        tab("재고");node("6").assertExists()
    }
    @Test fun productAndBulkReceiptCanBeCreatedEditedAndCancelled() {
        runBlocking { app.repository.restore(Snapshot(preferences=Preferences(celebrate=false))) }
        tab("재고");click("첫 품목 등록");click("+ 품목 추가")
        input("제품명 · 농도 · 규격","카세트 테스트")
        field("제품명 · 농도 · 규격").performClick()
        node("저장").assertIsDisplayed().performClick()
        await{snapshot().products.size==1 && snapshot().products.single().name=="카세트 테스트"}
        back();click("입고 등록");input("입고 수량","12");click("함께 저장")
        await{snapshot().receipts.size==1}
        assertEquals(12,inventory(snapshot()).products.values.single().balance)
        assertNull(snapshot().receipts.single().lines.single().expiry)
        click("입고 이력");click("수정");input("입고 수량","15");click("함께 저장")
        await{snapshot().receipts.single().lines.single().quantity==15}
        click("입고 취소");click("확인");await{snapshot().receipts.single().cancelled}
        assertEquals(0,inventory(snapshot()).products.values.single().balance)
    }
    @Test fun templateQuantityCanBeClearedRetypedAndOnlyThisRecordAdjusted() {
        openTemplates();click("수정");input("${p.name} 수량","")
        field("${p.name} 수량").assertExists();node("저장").assertIsNotEnabled()
        input("${p.name} 수량","3");click("+1");click("저장")
        await{snapshot().templates.single().items.single().quantity==4}
        stock(10);back();back();click("오늘 기록 시작");beforeAndTemplate()
        click("이번 기록만 수량 조정");input("${p.name} 수량","3");click("수량 조정 마치기")
        stock(10);click("기록 저장");await{snapshot().treatments.size==1};stock(7)
        assertEquals(4,snapshot().templates.single().items.single().quantity)
        click("종료 후 기록하기");input("초기배액량","2300");input("기계 제수량","600");click("기록 저장")
        await{snapshot().treatments.single().complete()};stock(7)
        assertEquals(1,snapshot().usages.size)
    }
    @Test fun productColorFollowsItsIdInManagementChooserAndSelectedChips() {
        tab("재고");click("품목 관리 · 색상");click(p.name)
        show(compose.onNodeWithContentDescription("색상 #47956E")).performClick();click("저장")
        await{snapshot().products.single().color==0xFF47956E}
        back();click("사용 구성 관리")
        compose.onNodeWithContentDescription("${p.name} 색상 #47956E").assertExists()
        click("수정");compose.onNodeWithContentDescription("${p.name} 색상 #47956E").assertExists()
        back();back();tab("홈");click("오늘 기록 시작");click("구성 변경")
        compose.onNodeWithContentDescription("${p.name} 색상 #47956E").assertExists()
        click("밤 구성");compose.onNodeWithContentDescription("${p.name} 색상 #47956E").assertExists()
        stock(10)
    }
    @Test fun unsavedProductSurvivesRecreationAndBackRequiresDiscard() {
        tab("재고");click("품목 관리 · 색상");click(p.name)
        input("제품명 · 농도 · 규격","저장 전 이름")
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodes(hasSetTextAction() and hasText("저장 전 이름")).fetchSemanticsNodes().isNotEmpty()}
        back();node("변경 내용을 버릴까요?").assertExists();compose.onNode(hasText("취소") and hasAnyAncestor(isDialog())).performClick()
        field("제품명 · 농도 · 규격").assertTextContains("저장 전 이름")
        back();click("확인")
        assertEquals(p.name,snapshot().products.single().name);stock(10)
    }
    @Test fun unsavedTemplateAndReceiptSurviveRecreationWithoutMovingStock() {
        openTemplates();click("수정");input("${p.name} 수량","5")
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodes(hasSetTextAction() and hasText("5")).fetchSemanticsNodes().isNotEmpty()}
        stock(10);back();click("확인");assertEquals(2,snapshot().templates.single().items.single().quantity)
        back();back();tab("재고");click("입고 등록");input("입고 수량","8")
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodes(hasSetTextAction() and hasText("8")).fetchSemanticsNodes().isNotEmpty()}
        stock(10);back();click("확인");assertEquals(1,snapshot().receipts.size)
    }
    @Test fun yesterdayMorningCompletionKeepsYesterdayAndHidesItsPrompt() {
        runBlocking {app.repository.save(Treatment(date=yesterday,weightGrams=62000,systolic=120,diastolic=80,items=listOf(Item(p.id,p.name,2))),true)}
        await{compose.onAllNodesWithText("어제 기록 작성하기").fetchSemanticsNodes().isNotEmpty()}
        click("작성하기");node("날짜 ${yesterday.replace('-','.')}").assertExists()
        input("초기배액량","2200");input("기계 제수량","500");click("기록 저장")
        await{snapshot().treatments.single().complete()}
        assertEquals(yesterday,snapshot().treatments.single().date);stock(8)
        node("어제 기록 작성하기").assertDoesNotExist();node("오늘 기록 시작").assertExists()
    }
    @Test fun pastDateCanBeChosenAndFutureDateDoesNotChangeTheRecord() {
        click("오늘 기록 시작");click("어제");node("날짜 ${yesterday.replace('-','.')}").assertExists()
        click("날짜 선택")
        val future=LocalDate.now().plusDays(1)
        if(future.monthValue!=LocalDate.parse(yesterday).monthValue)compose.onNodeWithContentDescription("다음 달").performClick()
        show(compose.onNodeWithContentDescription(future.toString())).performClick()
        node("미래 날짜에는 치료 기록을 등록할 수 없어요.").assertExists()
        node("날짜 ${yesterday.replace('-','.')}").assertExists()
        click("기록 저장");await{snapshot().treatments.isNotEmpty()}
        assertEquals(yesterday,snapshot().treatments.single().date)
    }
    @Test fun decimalTypingClearAndStepControlsKeepExactValues() {
        click("오늘 기록 시작")
        input("몸무게","61.");field("몸무게").assertTextContains("61.")
        field("몸무게").performTextInput("5");field("몸무게").assertTextContains("61.5")
        click("+0.1");field("몸무게").assertTextContains("61.6")
        click("-0.5");field("몸무게").assertTextContains("61.1")
        show(compose.onNodeWithContentDescription("몸무게 전체 지우기")).performClick()
        input("몸무게","62.25");click("기록 저장")
        await{snapshot().treatments.isNotEmpty()};assertEquals(62250,snapshot().treatments.single().weightGrams)
    }
    @Test fun invalidWholeQuantityCannotSaveThenCanBeCorrected() {
        tab("재고");click("입고 등록");input("입고 수량","1.5")
        node("함께 저장").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodes(hasSetTextAction() and hasText("1.5")).fetchSemanticsNodes().isNotEmpty()}
        node("함께 저장").assertIsNotEnabled();stock(10)
        input("입고 수량","5");click("함께 저장");await{snapshot().receipts.size==2};stock(15)
    }
    @Test fun statisticsSelectionSurvivesOpeningARecord() {
        runBlocking { app.repository.save(Treatment(weightGrams=62300,systolic=120,diastolic=80,initialDrain=2300,machineUf=600),true) }
        tab("통계");click("30D");click("표");click(today());back()
        node("30D").assertIsSelected();node("표").assertIsSelected()
    }
    @Test fun recordTableFiltersPeriodAndKeepsSelectionAfterEditing() {
        runBlocking {
            app.repository.save(Treatment(id="recent",date=yesterday,weightGrams=62300,systolic=120,diastolic=80,initialDrain=2300,machineUf=600),true)
            app.repository.save(Treatment(id="older",date=LocalDate.now().minusDays(15).toString(),weightGrams=61000),false)
            app.repository.save(Treatment(id="manual",date=yesterday,kind="MANUAL",manualDrain=2195,drainUnit="g"),true)
        }
        tab("기록");click("표");node("7D").assertIsSelected()
        compose.onNodeWithTag("record-row-recent").assertExists()
        compose.onNodeWithTag("record-row-manual").assertExists()
        compose.onNodeWithTag("record-row-older").assertDoesNotExist()
        show(node("2195 g")).assertIsDisplayed()
        click("30D");compose.onNodeWithTag("record-row-older").assertExists()
        show(compose.onNodeWithTag("record-row-recent")).performClick()
        node("날짜 ${yesterday.replace('-','.')}").assertExists();back()
        node("표").assertIsSelected();node("30D").assertIsSelected()
        click("기간 지정")
        node("시작 ${LocalDate.now().minusDays(29).toString().replace('-','.')}").assertExists()
        click("리스트");node("리스트").assertIsSelected()
        click("캘린더");node("캘린더").assertIsSelected()
    }
    @Test fun backupRoundTripAndBrokenFilePreserveData()=runBlocking {
        val t=Treatment(id="backup-treatment",kind="MANUAL",items=listOf(Item(p.id,p.name,2)),manualDrain=2195,drainUnit="kg",basisMl=null)
        app.repository.save(t,true)
        val file=File(app.cacheDir,"round-trip.json");app.backup.export(Uri.fromFile(file))
        val imported=app.backup.read(Uri.fromFile(file))
        app.repository.save(t.copy(items=listOf(Item(p.id,p.name,3))),true);app.backup.restore(imported)
        val restored=app.repository.snapshot()
        assertEquals(8,inventory(restored).products.getValue(p.id).balance)
        assertEquals("kg",restored.treatments.single().drainUnit);assertEquals(2195,restored.treatments.single().manualDrain)
        assertTrue(File(app.filesDir,"protection").listFiles()!!.isNotEmpty())
        file.writeText("{broken")
        try{app.backup.read(Uri.fromFile(file));fail("broken JSON must fail")}catch(_:Exception){}
        assertEquals(restored.treatments,app.repository.snapshot().treatments)
    }
    @Test fun typedDraftSurvivesActivityRecreationRotationAndDoesNotConsumeStock() {
        click("오늘 기록 시작");input("몸무게","61.5")
        compose.activityRule.scenario.recreate()
        await("Draft text after activity recreation",30000){compose.onAllNodes(hasSetTextAction() and hasText("61.5")).fetchSemanticsNodes().isNotEmpty()}
        stock(10)
        val originalOrientation=compose.activity.requestedOrientation
        try {
            showKeyboard("몸무게","Portrait")
            compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            await("Landscape configuration",30000){compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            await("Draft text after rotation",30000){compose.onAllNodes(hasSetTextAction() and hasText("61.5")).fetchSemanticsNodes().isNotEmpty()}
            showKeyboard("몸무게","Landscape")
            await("Landscape input fully visible above keyboard",30000) {
                val visible=field("몸무게").getBoundsInRoot().let{it.bottom-it.top}
                val full=field("몸무게").getUnclippedBoundsInRoot().let{it.bottom-it.top}
                full.value>0f && visible>=full*.95f
            }
            field("몸무게").assertTextContains("61.5").assertIsDisplayed()
            node("기록 저장").assertIsDisplayed();screenshot("landscape-keyboard.png")
            stock(10)
        } catch(error:Throwable) {
            runCatching{screenshot("failure-rotation-keyboard.png")};throw error
        } finally {
            compose.activityRule.scenario.onActivity{it.requestedOrientation=originalOrientation}
        }
    }
    @Test fun settingsUnsavedChangesSurviveRecreationAndDarkThemeCanBeSaved() {
        compose.onNodeWithContentDescription("설정").performClick();click("어둡게");node("어둡게").assertIsSelected()
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodesWithText("어둡게").fetchSemanticsNodes().isNotEmpty()}
        show(node("어둡게")).assertIsSelected()
        click("표시·알림·잠금 설정 저장");await{snapshot().preferences.darkMode=="DARK"}
        back();node("페리로그").assertExists();screenshot("dark-home.png")
    }
    private fun screenshot(name:String) {
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir=File(app.filesDir,"e2e-artifacts").apply{mkdirs()}
        File(dir,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
