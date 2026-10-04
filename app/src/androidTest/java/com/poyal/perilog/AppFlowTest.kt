package com.poyal.perilog

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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
        catch(error:ComposeTimeoutException){
            val interactive=app.getSystemService(PowerManager::class.java).isInteractive
            val locked=app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val focused=runCatching{compose.activity.hasWindowFocus()}.getOrNull()
            throw AssertionError("$description was not ready after $timeoutMs ms (interactive=$interactive, keyguardLocked=$locked, windowFocused=$focused)",error)
        }
    }
    private fun node(text:String)=compose.onNodeWithText(text)
    private fun show(n:SemanticsNodeInteraction):SemanticsNodeInteraction {val parents=n.onAncestors().filter(hasScrollAction())
        for(index in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[index].performScrollTo()}
        runCatching{n.performScrollTo()}
        return n}
    private fun click(text:String) {show(node(text)).performClick()}
    private fun select(label:String,value:String) {
        show(compose.onNodeWithContentDescription(label)).performClick()
        show(compose.onNode(hasText(value) and hasClickAction() and hasAnyAncestor(isPopup()))).performClick()
    }
    private fun selectedValue(label:String,value:String) {compose.onNodeWithContentDescription(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,value))}
    private fun tab(text:String) {
        hideKeyboard()
        val matcher=hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        await("Navigation tab $text after keyboard dismissal"){compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(matcher).performClick()
    }
    private fun field(label:String)=compose.onNode(hasSetTextAction() and (hasText(label) or hasContentDescription(label)))
    private fun input(label:String,value:String) {show(field(label)).performTextReplacement(value)}
    private fun back() {compose.onNodeWithContentDescription("뒤로").performClick()}
    private fun deviceBack() {compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
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
    @Test fun aboutWorksOfflineAndDarkThemeSurvivesRecreation() {
        compose.onNodeWithContentDescription("설정").performClick()
        click("어둡게"); click("표시·알림·잠금 설정 저장")
        await { snapshot().preferences.darkMode == "DARK" }
        click("페리로그 정보")
        node("제작자  Poyal").assertExists()
        click("업데이트 확인")
        await { compose.onAllNodesWithText("아직 공개된 정식 릴리즈가 없어요.").fetchSemanticsNodes().isNotEmpty() }
        screenshot("about-dark.png")
        show(node("다운로드 폴더 열기")).assertExists()
        show(node("업데이트와 데이터")).assertExists()
        compose.activityRule.scenario.recreate()
        await { compose.onAllNodesWithText("페리로그 정보").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("DARK", snapshot().preferences.darkMode)
        back()
        await { compose.onAllNodesWithText("설정").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun machineEntryAndAdditionalUseWorkOffline() {
        click("오늘 기록 시작");beforeAndTemplate()
        input("초기배액량","2300");input("기계 제수량","600")
        show(node("900 mL")).assertIsDisplayed()
        node("초기배액량 − 이전 최종 주입 설정값 + 기계 제수량").assertDoesNotExist()
        show(compose.onNodeWithContentDescription("제수량 도움말")).performClick()
        node("초기배액량 − 이전 최종 주입 설정값 + 기계 제수량").assertIsDisplayed()
        input("이 기록의 이전 주입 기준","2100");click("닫기")
        show(node("800 mL")).assertIsDisplayed()
        show(compose.onNodeWithContentDescription("제수량 도움말")).performClick()
        input("이 기록의 이전 주입 기준","2000");click("닫기")
        show(node("900 mL")).assertIsDisplayed()
        click("기록 저장");await{snapshot().treatments.size==1}
        node("오늘도 기록을 마쳤어요").assertExists()
        assertEquals(900,snapshot().treatments.single().totalUf());stock(8)
        tab("기록");compose.onNodeWithContentDescription("기록 추가").performClick();click("추가투석 기록 추가")
        await("Additional treatment editor after records navigation"){compose.onAllNodesWithText("구성 변경").fetchSemanticsNodes().isNotEmpty()}
        click("구성 변경");click("밤 구성");click("기록 저장")
        await{snapshot().treatments.size==2}
        assertTrue(snapshot().treatments.single{it.kind=="MANUAL"}.complete())
        assertNull(snapshot().treatments.single{it.kind=="MANUAL"}.manualDrain);stock(6)
        tab("재고");node("6").assertExists()
    }
    @Test fun deletingRecordCancelsUsageAndUndoRestoresItWithoutAllocationWarnings() {
        runBlocking {
            val s=snapshot()
            app.repository.restore(s.copy(receipts=s.receipts.map{r->r.copy(lines=r.lines.map{it.copy(quantity=1)})}))
            app.repository.save(Treatment(id="delete-undo",items=listOf(Item(p.id,p.name,2))),true)
        }
        val before=snapshot();stock(-1)
        await{compose.onAllNodesWithText("이어서 입력하기").fetchSemanticsNodes().isNotEmpty()}
        node("재고 확인 필요").assertDoesNotExist()
        tab("재고")
        await{compose.onAllNodesWithText("-1").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("미배정",substring=true).assertCountEquals(0)
        show(node(p.name)).performClick()
        node("사용 내역 중 1EA의 입고·재고를 확인해 주세요.").assertDoesNotExist()
        back();tab("기록")
        show(compose.onNodeWithTag("record-menu-delete-undo")).performClick();click("삭제")
        node("연결된 물품 사용도 함께 취소해 재고에 반영해요. ‘되돌리기’로 기록과 사용 내역을 함께 복구할 수 있어요.").assertExists()
        click("확인");await{snapshot().treatments.isEmpty()}
        stock(1);assertTrue(snapshot().usages.single().cancelled)
        click("되돌리기");await{snapshot().treatments.size==1}
        stock(-1);assertEquals(before.treatments,snapshot().treatments);assertEquals(before.usages,snapshot().usages)
        show(compose.onNodeWithTag("record-menu-delete-undo")).performClick();click("삭제");click("확인")
        await{snapshot().treatments.isEmpty()};stock(1);assertTrue(snapshot().usages.single().cancelled)
    }
    @Test fun stockMenuCanAdjustWithReasonCancelAndSetObservedQuantity() {
        val expiry=LocalDate.now().plusDays(2).toString()
        runBlocking {
            val receipt=snapshot().receipts.single()
            app.repository.receipt(receipt.copy(lines=receipt.lines.map{it.copy(expiry=expiry)}))
        }
        tab("재고");show(node(p.name)).performClick()
        node("재고 상세").assertExists();field("직접 확인한 수량").assertDoesNotExist()
        compose.onAllNodesWithText("사용기한",substring=true).assertCountEquals(0)
        deviceBack();node("재고 관리").assertExists()
        show(compose.onNodeWithContentDescription("${p.name} 재고 메뉴")).performClick()
        click("수량 추가·차감");node("조정 저장").assertIsNotEnabled()
        input("변경 수량","2");node("조정 저장").assertIsNotEnabled()
        input("변경 사유 · 필수","포장 손상")
        input("변경 수량","1.5");node("조정 저장").assertIsNotEnabled()
        input("변경 수량","2");compose.activityRule.scenario.recreate()
        await{compose.onAllNodes(hasSetTextAction() and hasText("포장 손상")).fetchSemanticsNodes().isNotEmpty()}
        field("변경 수량").assertTextContains("2");stock(10)
        show(node("적용 후 현재 재고 8 EA")).assertIsDisplayed()
        click("조정 저장");click("확인");await{snapshot().adjustments.size==1};stock(8)
        assertTrue(snapshot().counts.isEmpty() && snapshot().usages.isEmpty())
        assertEquals(expiry,inventory(snapshot()).products.getValue(p.id).lots.single().expiry)
        show(node(p.name)).performClick();click("이 품목 이력");show(node("포장 손상")).assertIsDisplayed()
        click("조정 취소");click("확인");await{snapshot().adjustments.single().cancelled};stock(10)
        deviceBack();node("재고 상세").assertExists()
        click("수량 추가·차감");click("추가");input("변경 수량","1");input("변경 사유 · 필수","누락 수량 추가")
        click("조정 저장");click("확인");await{snapshot().adjustments.size==2};stock(11)
        assertEquals(listOf(-2,1),snapshot().adjustments.sortedBy{it.createdAt}.map{it.delta})
        back();show(compose.onNodeWithContentDescription("${p.name} 재고 메뉴")).performClick();click("수량 맞추기")
        input("직접 확인한 수량","7");stock(11);click("현재 수량 저장");click("확인")
        await{snapshot().counts.size==1};stock(7)
        assertEquals(2,snapshot().adjustments.size);assertEquals(10,snapshot().receipts.single().lines.single().quantity)
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
        click("이력");click("수정");input("입고 수량","15");click("함께 저장")
        await{snapshot().receipts.single().lines.single().quantity==15}
        click("입고 취소");click("확인");await{snapshot().receipts.single().cancelled}
        assertEquals(0,inventory(snapshot()).products.values.single().balance)
    }
    @Test fun stockHistoryFiltersAllEventsAndSurvivesReceiptEditingAndRecreation() {
        val q=Product(id="history-line",name="라인 · 테스트",kind="소모품")
        val receipt=snapshot().receipts.single()
        runBlocking {
            app.repository.product(q)
            app.repository.receipt(receipt.copy(lines=receipt.lines+ReceiptLine(productId=q.id,quantity=4)))
            app.repository.save(Treatment(id="history-use",items=listOf(Item(p.id,p.name,2))),true)
            app.repository.adjustment(StockAdjustment(id="history-loss",productId=p.id,delta=-1,memo="포장 손상",cancelled=true))
            app.repository.adjustment(StockAdjustment(id="history-add",productId=p.id,delta=3,memo="누락 수량 추가"))
            app.repository.count(StockCount(id="history-count",productId=p.id,quantity=5))
        }
        tab("재고");click("이력");show(node("5건")).assertIsDisplayed()
        listOf("RECEIPT:${receipt.id}","USAGE:history-use","LOSS:history-loss","ADD:history-add","COUNT:history-count").forEach{
            compose.onNodeWithTag("stock-history-$it").assertExists()
        }
        show(node("기준 5 EA")).assertIsDisplayed();show(node("−2 EA")).assertIsDisplayed();show(node("+3 EA")).assertIsDisplayed()
        select("이력 종류","사용");compose.onNodeWithTag("stock-history-USAGE:history-use").assertExists()
        compose.onNodeWithTag("stock-history-RECEIPT:${receipt.id}").assertDoesNotExist()
        select("이력 종류","전체");select("이력 품목",q.name)
        node("+4 EA").assertExists();node(p.name).assertDoesNotExist()
        select("이력 기간","7D");node("선택한 조건에 해당하는 재고 이력이 없어요.").assertExists()
        select("이력 기간","전체 기간");select("이력 품목","전체 품목");select("이력 상태","취소된 내역")
        compose.onNodeWithTag("stock-history-LOSS:history-loss").assertExists()
        compose.onNodeWithTag("stock-history-USAGE:history-use").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodesWithTag("stock-history-LOSS:history-loss").fetchSemanticsNodes().isNotEmpty()}
        selectedValue("이력 상태","취소된 내역");node("취소됨").assertExists()
        select("이력 상태","유효한 내역");select("이력 종류","입고");select("이력 품목",q.name)
        click("수정");input("입고 메모","수정한 입고 메모");click("함께 저장")
        await{snapshot().receipts.single().memo=="수정한 입고 메모"}
        selectedValue("이력 종류","입고");selectedValue("이력 품목",q.name);selectedValue("이력 상태","유효한 내역")
        show(node("수정한 입고 메모")).assertIsDisplayed();stock(5)
        select("이력 종류","사용");select("이력 품목",p.name);click("기록 보기")
        await{compose.onAllNodesWithText("구성 변경").fetchSemanticsNodes().isNotEmpty()}
        back();selectedValue("이력 종류","사용");selectedValue("이력 품목",p.name)
        click("사용 취소");click("확인");await{snapshot().usages.single().cancelled}
        assertFalse(snapshot().treatments.single().usageConfirmed);stock(5)
        select("이력 상태","취소된 내역");compose.onNodeWithTag("stock-history-USAGE:history-use").assertExists()
    }
    @Test fun templateQuantityCanBeClearedRetypedAndOnlyThisRecordAdjusted() {
        openTemplates();click("수정");input("${p.name} 수량","")
        field("${p.name} 수량").assertExists();node("저장").assertIsNotEnabled()
        input("${p.name} 수량","4");click("저장")
        await{snapshot().templates.single().items.single().quantity==4}
        stock(10);back();back();click("오늘 기록 시작");beforeAndTemplate()
        click("이번 기록만 수량 조정");input("${p.name} 수량","3");click("수량 조정 마치기")
        stock(10);click("기록 저장");await{snapshot().treatments.size==1};stock(7)
        assertEquals(4,snapshot().templates.single().items.single().quantity)
        assertEquals("night",snapshot().treatments.single().usageTemplateId)
        assertEquals("밤 구성",snapshot().treatments.single().compositionName(snapshot()))
        click("기록하기");input("초기배액량","2300");input("기계 제수량","600");click("기록 저장")
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
        await{compose.onAllNodesWithText("어제의 기록").fetchSemanticsNodes().isNotEmpty()}
        click("어제 기록하기");node("날짜 ${yesterday.replace('-','.')}").assertExists()
        input("초기배액량","2200");input("기계 제수량","500");click("기록 저장")
        await{snapshot().treatments.single().complete()}
        assertEquals(yesterday,snapshot().treatments.single().date);stock(8)
        node("어제의 기록").assertDoesNotExist();node("오늘 기록 시작").assertExists()
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
    @Test fun decimalTypingAndClearKeepExactValues() {
        click("오늘 기록 시작")
        input("몸무게","61.");field("몸무게").assertTextContains("61.")
        field("몸무게").performTextInput("5");field("몸무게").assertTextContains("61.5")
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
        tab("통계");select("통계 기간","30D");select("표시 방식","항목별 도표")
        show(compose.onNodeWithTag("chart-dates-UF")).performClick();click(today().replace('-','.'))
        click("기록 열기");back()
        selectedValue("통계 기간","30D");selectedValue("표시 방식","항목별 도표")
    }
    @Test fun newRecordsStartEmptyAndDateChangesKeepDraftInputs() {
        runBlocking {
            app.repository.preferences(snapshot().preferences.copy(basis=listOf(Basis("1970-01-01",2100),Basis(today(),2000))))
            app.repository.save(Treatment(id="previous",date=yesterday,weightGrams=61000,systolic=110,diastolic=75,
                initialDrain=2300,machineUf=600,basisMl=1900,items=listOf(Item(p.id,p.name,2)),
                usageTemplateId="night",usageTemplateName="밤 구성",usageTemplateColor=0xFF2167B8),true)
            app.repository.save(Treatment(id="previous-manual",kind="MANUAL",manualDrain=2150,drainUnit="kg",
                items=listOf(Item(p.id,p.name,2)),usageTemplateId="night"),true)
        }
        click("오늘 기록 시작")
        listOf("몸무게","수축기 혈압","이완기 혈압","초기배액량","기계 제수량").forEach{label->
            field(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        }
        node("사용한 품목을 선택해 주세요.").assertExists()
        click("어제")
        field("몸무게").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        node("사용한 품목을 선택해 주세요.").assertExists()
        click("오늘");beforeAndTemplate();input("초기배액량","2300");input("기계 제수량","600")
        click("어제")
        field("몸무게").assertTextContains("62.3");field("수축기 혈압").assertTextContains("120")
        field("이완기 혈압").assertTextContains("80");field("초기배액량").assertTextContains("2300")
        field("기계 제수량").assertTextContains("600")
        await {snapshot().drafts.singleOrNull()?.treatment?.let{it.date==yesterday && it.basisMl==2100 && it.items.singleOrNull()?.quantity==2}==true}
        val draft=snapshot().drafts.single().treatment
        assertEquals("night",draft.usageTemplateId);assertEquals("밤 구성",draft.usageTemplateName)
        assertNull(draft.sourceDate);stock(6)
        compose.activityRule.scenario.recreate()
        await {compose.onAllNodes(hasSetTextAction() and hasText("62.3")).fetchSemanticsNodes().isNotEmpty()}
        back();click("어제 기록하기")
        field("몸무게").assertTextContains("62.3");field("초기배액량").assertTextContains("2300")
        field("기계 제수량").assertTextContains("600");node("밤 구성").assertExists()
        click("이 초안 버리기");click("확인");await {snapshot().drafts.isEmpty()}
        tab("기록");compose.onNodeWithContentDescription("기록 추가").performClick();click("추가투석 기록 추가")
        await("New additional treatment editor"){compose.onAllNodesWithText("구성 변경").fetchSemanticsNodes().isNotEmpty()}
        node("사용한 품목을 선택해 주세요.").assertExists()
        field("배액무게").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        back();tab("기록");click("표")
        show(compose.onNodeWithTag("record-cell-previous-0")).performClick();click("기록 열기")
        click("수정");field("몸무게").assertTextContains("61")
        show(compose.onNodeWithContentDescription("제수량 도움말")).performClick()
        field("이 기록의 이전 주입 기준").assertTextContains("1900");click("닫기")
        click("오늘")
        show(compose.onNodeWithContentDescription("제수량 도움말")).performClick()
        field("이 기록의 이전 주입 기준").assertTextContains("1900");click("닫기")
        stock(6)
    }
    @Test fun statisticsUseTotalUfAndExcludeMissingValuesFromAverage() {
        runBlocking {
            val base=Treatment(initialDrain=2300,machineUf=600,basisMl=2000)
            app.repository.save(base.copy(id="positive"),true)
            app.repository.save(base.copy(id="negative",initialDrain=1800,machineUf=0),true)
            app.repository.save(base.copy(id="zero",initialDrain=2000,machineUf=0),true)
            app.repository.save(base.copy(id="missing-drain",initialDrain=null),true)
            app.repository.save(base.copy(id="missing-machine",machineUf=null),true)
            app.repository.save(base.copy(id="missing-basis",basisMl=null),true)
            app.repository.save(base.copy(id="manual",kind="MANUAL",manualDrain=5000,previousFill=2000),true)
        }
        tab("통계")
        assertTrue(node("투석 기록").getUnclippedBoundsInRoot().top<node("활력 상태").getUnclippedBoundsInRoot().top)
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsDisplayed();screenshot("updated-statistics.png")
        show(node("총 제수량")).assertIsDisplayed()
        node("초기배액량").assertDoesNotExist();node("기계 제수량").assertDoesNotExist()
        show(node("평균 233.3 mL")).assertIsDisplayed()
        select("표시 방식","항목별 도표")
        show(compose.onNodeWithTag("chart-dates-UF")).performClick();click(today().replace('-','.'))
        assertTrue(node("투석 기록").getUnclippedBoundsInRoot().top<node("활력 상태").getUnclippedBoundsInRoot().top)
        show(node("900 mL")).assertIsDisplayed();show(node("-200 mL")).assertIsDisplayed()
        show(node("0 mL")).assertIsDisplayed()
        compose.onAllNodesWithText("— mL").assertCountEquals(3)
        node("3000 mL").assertDoesNotExist()
        show(compose.onNode(hasText("기록 열기") and hasAnyAncestor(hasTestTag("chart-value-UF-positive")))).performClick()
        show(node("900 mL")).assertIsDisplayed();back()
        selectedValue("표시 방식","항목별 도표")
    }
    @Test fun treatmentOptionsHideLegacyTimesAndKeepThemWhenMemoChanges() {
        val original=Treatment(id="legacy-times",date=yesterday,weightGrams=54000,systolic=110,diastolic=70,
            initialDrain=2200,machineUf=500,basisMl=2000,startTime="22:00",endTime="07:00",dwellMinutes=95,
            memo="최종주입과 관련한 메모\n두 번째 줄\n세 번째 줄\n네 번째 줄")
        runBlocking{app.repository.save(original,false)}
        tab("기록")
        show(node("메모")).assertIsDisplayed();click("더 보기")
        show(node("접기")).assertIsDisplayed();screenshot("updated-record-memo.png")
        compose.onNodeWithTag("record-menu-${original.id}").performScrollTo().performClick();click("수정")
        node("오늘").assertExists();node("어제").assertExists()
        click("평균저류시간 · 메모 추가")
        node("시작 시각 · 예: 22:00").assertDoesNotExist();node("종료 시각 · 예: 07:00").assertDoesNotExist()
        field("평균저류 시간").assertTextContains("1");field("분").assertTextContains("35")
        input("메모","수정한 투석 메모");hideKeyboard();screenshot("updated-treatment-options.png");click("기록 저장")
        await{snapshot().treatments.single().memo=="수정한 투석 메모"}
        val saved=snapshot().treatments.single()
        assertEquals(original.startTime,saved.startTime);assertEquals(original.endTime,saved.endTime)
        assertEquals(original.dwellMinutes,saved.dwellMinutes)
        compose.onNodeWithContentDescription("기록 추가").performClick();click("추가투석 기록 추가")
        click("메모 추가")
        node("시작 시각 · 예: 22:00").assertDoesNotExist();node("종료 시각 · 예: 07:00").assertDoesNotExist()
        input("메모","추가투석 메모");hideKeyboard();click("기록 저장")
        await{snapshot().treatments.size==2}
        assertEquals("",snapshot().treatments.single{it.kind=="MANUAL"}.startTime)
    }
    @Test fun recordTableFiltersPeriodAndKeepsSelectionAfterEditing() {
        runBlocking {
            app.repository.save(Treatment(id="recent",date=yesterday,weightGrams=62300,systolic=120,diastolic=80,initialDrain=2300,machineUf=600),true)
            app.repository.save(Treatment(id="older",date=LocalDate.now().minusDays(15).toString(),weightGrams=61000),false)
            app.repository.save(Treatment(id="manual",date=yesterday,kind="MANUAL",manualDrain=2195,drainUnit="g"),true)
        }
        tab("기록");click("표");click("필터");selectedValue("조회 기간","7D");click("닫기")
        compose.onNodeWithTag("record-row-recent").assertExists()
        compose.onNodeWithTag("record-row-manual").assertExists()
        compose.onNodeWithTag("record-row-older").assertDoesNotExist()
        show(node("2195 g")).assertIsDisplayed()
        click("필터");select("조회 기간","30D");click("닫기");compose.onNodeWithTag("record-row-older").assertExists()
        show(compose.onNodeWithTag("record-cell-recent-0")).performClick();click("기록 열기")
        node("날짜 ${yesterday.replace('-','.')}").assertExists();back()
        node("기록 표").assertIsDisplayed();click("필터");selectedValue("조회 기간","30D")
        select("조회 기간","기간 지정")
        compose.onNodeWithTag("date-range-dialog").assertExists()
        compose.onNodeWithContentDescription("기간 선택 취소").performClick();selectedValue("조회 기간","30D");click("닫기")
        back();node("리스트").assertIsSelected()
        click("캘린더");node("캘린더").assertIsSelected()
    }
    @Test fun spreadsheetZoomPinchFrozenHeadersAndThousandRows() {
        val records=(0 until 1000).map{n->Treatment(id="many-$n",date=LocalDate.now().minusDays(n.toLong()).toString(),
            weightGrams=62250,systolic=120,diastolic=80,initialDrain=2200,machineUf=580,saved=true,usageConfirmed=true)}
        runBlocking{app.repository.restore(snapshot().copy(treatments=records,usages=records.map{Usage(it.id,it.date,it.items,it.kind,it.createdAt)}))}
        tab("기록");click("표");click("필터");select("조회 기간","전체");click("닫기")
        compose.onNodeWithTag("record-cell-many-0-8").assertIsDisplayed()
        val originalZoom=nodeZoom()
        val viewport=compose.onNodeWithTag("record-table-viewport")
        assertTrue(compose.onNodeWithTag("record-table-controls").getUnclippedBoundsInRoot().bottom<=viewport.getUnclippedBoundsInRoot().top)
        viewport.performTouchInput {
            val cy=center.y
            down(0,androidx.compose.ui.geometry.Offset(width*.4f,cy))
            down(1,androidx.compose.ui.geometry.Offset(width*.6f,cy))
            for(step in 1..8) {
                moveTo(0,androidx.compose.ui.geometry.Offset(width*(.4f-step*.025f),cy),delayMillis=16)
                moveTo(1,androidx.compose.ui.geometry.Offset(width*(.6f+step*.025f),cy),delayMillis=16)
            }
            up(0);up(1)
        }
        assertTrue(nodeZoom()>originalZoom)
        click("100%로");assertEquals(100,nodeZoom())
        // Zoom anchors the row under the fingers; return to the first row for edge checks.
        compose.onNodeWithTag("record-table-rows").performScrollToIndex(0)
        val dateLeft=compose.onNodeWithTag("record-cell-many-0-0").getUnclippedBoundsInRoot().left
        compose.onNodeWithTag("record-table-horizontal").performTouchInput{swipeLeft()}
        assertEquals(dateLeft,compose.onNodeWithTag("record-cell-many-0-0").getUnclippedBoundsInRoot().left)
        val headerTop=compose.onNodeWithTag("record-table-header").getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("record-table-rows").performScrollToIndex(999)
        compose.onNodeWithTag("record-cell-many-999-0").assertIsDisplayed()
        assertEquals(headerTop,compose.onNodeWithTag("record-table-header").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("record-cell-many-999-0").performClick()
        compose.activityRule.scenario.recreate()
        await{compose.onAllNodesWithTag("record-cell-detail").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(100,nodeZoom());compose.onNodeWithTag("record-cell-many-999-0").assertIsDisplayed()
        screenshot("record-table-zoomed.png")
        val originalOrientation=compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            await("Landscape table"){compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            assertEquals(100,nodeZoom());compose.onNodeWithTag("record-cell-detail").assertIsDisplayed()
            node("기록 열기").assertIsDisplayed();node("전체 열").assertIsDisplayed()
            screenshot("record-table-landscape.png")
        } finally {
            compose.activityRule.scenario.onActivity{it.requestedOrientation=originalOrientation}
            await("Portrait table"){compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        }
        click("전체 열");compose.onNodeWithTag("record-cell-many-999-8").assertIsDisplayed()
        assertEquals(records.toSet(),snapshot().treatments.toSet())
    }
    private fun nodeZoom()=compose.onNodeWithTag("table-zoom").fetchSemanticsNode().config[SemanticsProperties.StateDescription].removeSuffix("%").toInt()

    @Test fun rangePickerCancelApplyAndRestoreAcrossAllThreeScreens() {
        tab("통계")
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsNotEnabled().performClick()
        compose.onNodeWithTag("date-range-dialog").assertDoesNotExist()
        select("통계 기간","30D")
        compose.onNodeWithContentDescription("조회 기간 변경").assertIsNotEnabled()
        select("통계 기간","기간 지정")
        compose.onNodeWithTag("date-range-dialog").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("date-range-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("기간 선택 취소").performClick()
        selectedValue("통계 기간","30D")
        select("통계 기간","기간 지정");click("적용");selectedValue("통계 기간","기간 지정")
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsEnabled().performClick()
        compose.onNodeWithTag("date-range-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("기간 선택 취소").performClick()
        tab("기록");select("조회 기간","7D")
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsNotEnabled()
        select("조회 기간","기간 지정");click("적용");selectedValue("조회 기간","기간 지정")
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsDisplayed().assertIsEnabled()
        click("표");click("필터");select("조회 기간","30D")
        compose.onNodeWithContentDescription("조회 기간 변경").assertIsNotEnabled()
        select("조회 기간","기간 지정");click("적용")
        compose.onNodeWithContentDescription("조회 기간 변경").assertIsEnabled()
        click("닫기");back()
        tab("재고");click("이력");select("이력 기간","기간 지정")
        compose.onNodeWithContentDescription("기간 선택 취소").performClick();selectedValue("이력 기간","전체 기간")
        select("이력 기간","7D")
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsNotEnabled()
        select("이력 기간","기간 지정");click("적용");selectedValue("이력 기간","기간 지정")
        show(compose.onNodeWithContentDescription("조회 기간 변경")).assertIsDisplayed().assertIsEnabled()
        screenshot("range-picker-applied.png")
    }

    @Test fun rangePickerDirectInputRejectsReversedDatesAndWorksInDarkLandscape() {
        runBlocking{app.repository.preferences(snapshot().preferences.copy(darkMode="DARK"))}
        tab("통계");select("통계 기간","기간 지정")
        compose.onNode(hasContentDescription("입력",substring=true) and hasClickAction()).performClick()
        val fields=compose.onAllNodes(hasSetTextAction())
        fields.assertCountEquals(2)
        show(fields[0]).performTextReplacement("20261231")
        show(fields[1]).performTextReplacement("20261230")
        await("Reversed date input validation") {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).fetchSemanticsNodes().isNotEmpty()
        }
        node("적용").assertIsNotEnabled()
        show(fields[1]).performTextReplacement("20270102")
        await("Valid date input validation"){compose.onAllNodes(hasText("적용") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        node("적용").assertIsEnabled()
        val originalOrientation=compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            await("Landscape range input"){compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            // Configuration changes before the recreated dialog finishes layout and IME insets.
            // Scroll and inspect the restored field only once its landscape window is ready.
            await("Landscape range dialog layout") {
                val decor=compose.activity.window.decorView
                decor.width>decor.height && runCatching {
                    show(field("종료일")).assertIsDisplayed().assertTextContains("20270102")
                    node("적용").assertIsDisplayed().assertIsEnabled()
                }.isSuccess
            }
            show(field("종료일")).assertIsDisplayed().assertTextContains("20270102")
            node("적용").assertIsDisplayed().assertIsEnabled()
            screenshot("range-picker-dark-landscape.png")
            click("적용")
            show(compose.onNodeWithContentDescription("조회 기간 변경")).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"2026.12.31 ~ 2027.01.02 · 3일"))
        } finally {
            compose.activityRule.scenario.onActivity{it.requestedOrientation=originalOrientation}
        }
    }

    @Test fun backupRoundTripAndBrokenFilePreserveData()=runBlocking {
        val t=Treatment(id="backup-treatment",kind="MANUAL",items=listOf(Item(p.id,p.name,2)),manualDrain=2195,drainUnit="kg",basisMl=null)
        app.repository.save(t,true)
        val adjustment=StockAdjustment(productId=p.id,delta=3,memo="누락 수량 추가")
        app.repository.adjustment(adjustment)
        val file=File(app.cacheDir,"round-trip.json");app.backup.export(Uri.fromFile(file))
        val imported=app.backup.read(Uri.fromFile(file))
        app.repository.save(t.copy(items=listOf(Item(p.id,p.name,3))),true);app.backup.restore(imported)
        val restored=app.repository.snapshot()
        assertEquals(11,inventory(restored).products.getValue(p.id).balance)
        assertEquals(listOf(adjustment),restored.adjustments)
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
        if(InstrumentationRegistry.getArguments().getString("skipScreenshots")=="true")return
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir=File(app.filesDir,"e2e-artifacts").apply{mkdirs()}
        File(dir,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
