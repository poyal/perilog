package com.poyal.perilog

import android.net.Uri
import android.content.pm.ActivityInfo
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
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val p=Product(id="test-fluid",name="투석액 1.5% · 테스트")
    @Before fun reset()=runBlocking {
        app.repository.restore(Snapshot(products=listOf(p),templates=listOf(UsageTemplate(name="밤 구성",items=listOf(Item(p.id,p.name,2)))),
            receipts=listOf(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))))
    }
    private fun click(text:String) {compose.onNodeWithText(text).performScrollTo().performClick()}
    private fun input(label:String,value:String) {compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)}
    @Test fun machineEntryAndAdditionalUseWorkOffline() {
        compose.waitUntil(10000){compose.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
        click("오늘 기록 시작")
        input("몸무게","62.3");input("수축기 혈압","120");input("이완기 혈압","80")
        click("구성 변경");click("밤 구성");compose.onNodeWithText("이 구성 사용").performClick()
        input("초기배액량","2300");input("기계 제수량","600")
        compose.onNodeWithText("900 mL").performScrollTo().assertIsDisplayed()
        screenshot("treatment.png")
        click("기록 저장")
        compose.waitUntil(10000){runBlocking{app.repository.snapshot().treatments.size==1}}
        compose.onNodeWithText("오늘도 기록을 마쳤어요").assertExists()
        runBlocking {
            val s=app.repository.snapshot()
            assertEquals(900,s.treatments.single().totalUf())
            assertEquals(8,inventory(s).products.getValue(p.id).balance)
        }
        compose.waitUntil(10000){compose.onAllNodesWithText("기록을 저장했어요").fetchSemanticsNodes().isEmpty()}
        click("추가투석");click("구성 변경");click("밤 구성");compose.onNodeWithText("이 구성 사용").performClick()
        click("기록 저장")
        try { compose.waitUntil(10000){runBlocking{app.repository.snapshot().treatments.size==2}} } catch(e:Throwable) {
            compose.onRoot(useUnmergedTree=true).printToLog("PERILOG_TEST")
            println("SNAPSHOT_TEST="+runBlocking{app.repository.snapshot().treatments.toString()})
            throw e
        }
        runBlocking {
            val s=app.repository.snapshot()
            assertTrue(s.treatments.single{it.kind=="MANUAL"}.complete())
            assertNull(s.treatments.single{it.kind=="MANUAL"}.manualDrain)
            assertEquals(6,inventory(s).products.getValue(p.id).balance)
        }
        screenshot("home.png")
        compose.onNodeWithText("재고",useUnmergedTree=true).performClick()
        compose.onNodeWithText("6 EA").assertExists()
        screenshot("inventory.png")
    }
    @Test fun productAndBulkReceiptCanBeCreatedFromTheScreens() {
        runBlocking { app.repository.restore(Snapshot()) }
        compose.onNodeWithText("재고",useUnmergedTree=true).performClick()
        click("첫 품목 등록");click("+ 품목 추가")
        input("제품명 · 농도 · 규격","카세트 테스트")
        compose.onNodeWithText("저장",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){runBlocking{app.repository.snapshot().products.size==1}}
        compose.waitUntil(10000){compose.onAllNodesWithText("품목을 저장했어요").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithContentDescription("뒤로").performClick()
        click("입고 등록 · 이력");click("+ 일괄 입고 등록")
        input("입고 수량","12")
        compose.onNodeWithText("함께 저장").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.snapshot().receipts.size==1}}
        runBlocking {
            val s=app.repository.snapshot()
            assertEquals(12,inventory(s).products.values.single().balance)
            assertNull(s.receipts.single().lines.single().expiry)
        }
    }
    @Test fun statisticsSelectionSurvivesOpeningARecord() {
        runBlocking { app.repository.save(Treatment(weightGrams=62300,systolic=120,diastolic=80,initialDrain=2300,machineUf=600),true) }
        compose.onNodeWithText("통계",useUnmergedTree=true).performClick()
        click("30D");click("표")
        click(today())
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("30D").assertIsSelected()
        compose.onNodeWithText("표").assertIsSelected()
    }
    @Test fun backupRoundTripAndBrokenFilePreserveData()=runBlocking {
        val t=Treatment(id="backup-treatment",kind="MANUAL",items=listOf(Item(p.id,p.name,2)),manualDrain=2195,drainUnit="kg",basisMl=null)
        app.repository.save(t,true)
        val file=File(app.cacheDir,"round-trip.json")
        app.backup.export(Uri.fromFile(file))
        val imported=app.backup.read(Uri.fromFile(file))
        app.repository.save(t.copy(items=listOf(Item(p.id,p.name,3))),true)
        app.backup.restore(imported)
        val restored=app.repository.snapshot()
        assertEquals(8,inventory(restored).products.getValue(p.id).balance)
        assertEquals("kg",restored.treatments.single().drainUnit)
        assertEquals(2195,restored.treatments.single().manualDrain)
        assertTrue(File(app.filesDir,"protection").listFiles()!!.isNotEmpty())
        file.writeText("{broken")
        try{app.backup.read(Uri.fromFile(file));fail("broken JSON must fail")}catch(_:Exception){}
        assertEquals(restored.treatments,app.repository.snapshot().treatments)
    }
    @Test fun typedDraftSurvivesActivityRecreationWithoutUsingStock() {
        compose.waitUntil(10000){compose.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
        click("오늘 기록 시작");input("몸무게","61.5")
        compose.waitUntil(5000){runBlocking{app.repository.snapshot().drafts.any{it.treatment.weightGrams==61500}}}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000){compose.onAllNodes(hasSetTextAction() and hasText("61.5")).fetchSemanticsNodes().isNotEmpty()}
        runBlocking{assertEquals(10,inventory(app.repository.snapshot()).products.getValue(p.id).balance)}
        compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("몸무게")).assertExists()
        compose.activityRule.scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.filesDir,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
