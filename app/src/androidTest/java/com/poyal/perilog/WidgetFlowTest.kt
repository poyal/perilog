package com.poyal.perilog

import android.appwidget.*
import android.content.ComponentName
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.*
import android.util.SizeF
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.orderedContacts
import com.poyal.perilog.ui.JournalViewModel
import com.poyal.perilog.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

/** Real Android AppWidgetHost/RemoteViews, on a disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class WidgetFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private var host:AppWidgetHost?=null
    private var overlay:FrameLayout?=null
    private var widgetView:AppWidgetHostView?=null
    private fun snapshot()=runBlocking {app.repository.snapshot()}
    private fun await(condition:()->Boolean)=ui.waitUntil(25000,condition)
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=node.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching {parents[i].performScrollTo()}
        runCatching {node.performScrollTo()};return node
    }
    private fun click(text:String)=show(ui.onNodeWithText(text)).performClick()
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap {views(view.getChildAt(it))}else emptyList())
    private fun texts():List<String> {var result=emptyList<String>();ui.runOnUiThread {result=widgetView?.let {views(it).filterIsInstance<TextView>().map {v->v.text.toString()}} ?: emptyList()};return result}
    private fun descriptions():List<String> {var result=emptyList<String>();ui.runOnUiThread {result=widgetView?.let {views(it).mapNotNull {v->v.contentDescription?.toString()}} ?: emptyList()};return result}
    private fun assertFitsWithoutScrolling() {
        ui.runOnUiThread {
            val root=requireNotNull(widgetView)
            assertFalse(views(root).any {it is AbsListView || it is ScrollView})
            views(root).filterIsInstance<TextView>().filter {it.visibility==View.VISIBLE && it.text.isNotEmpty()}.forEach {view->
                val layout=requireNotNull(view.layout)
                assertTrue("Clipped text: ${view.text}; layout=${layout.height}, height=${view.height}, padding=${view.paddingTop+view.paddingBottom}",layout.height<=view.height-view.paddingTop-view.paddingBottom+2)
                for(line in 0 until layout.lineCount) assertEquals("Ellipsis: ${view.text}",0,layout.getEllipsisCount(line))
                val rect=android.graphics.Rect();assertTrue("Hidden text: ${view.text}",view.getGlobalVisibleRect(rect))
                assertTrue("Clipped bounds: ${view.text}",rect.height()>=view.height-2)
                assertTrue("Clipped width: ${view.text}",rect.width()>=view.width-2)
                // Android can retain invisible spaces past a wrap. Check visible glyphs, not trailing whitespace.
                for(line in 0 until layout.lineCount) assertTrue("Text exceeds width: ${view.text}; line=$line, text=${layout.getLineMax(line)}, available=${view.width-view.paddingLeft-view.paddingRight}",layout.getLineMax(line)<=view.width-view.paddingLeft-view.paddingRight+2)
            }
        }
    }
    private fun mount(provider:Class<*>,height:Int=172,width:Int=320) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appwidget grantbind --package ${app.packageName} --user 0")).use {it.readBytes()}
        ui.runOnIdle {
            val manager=AppWidgetManager.getInstance(app)
            val h=AppWidgetHost(ui.activity,9026);host=h
            h.deleteHost();val id=h.allocateAppWidgetId()
            val options=Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height)
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(width.toFloat(),height.toFloat())))
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(app,provider),options))
            val density=ui.activity.resources.displayMetrics.density
            val frame=FrameLayout(ui.activity).apply {setBackgroundColor(0xffc8d7e8.toInt())};overlay=frame
            ui.activity.addContentView(frame,ViewGroup.LayoutParams(-1,-1))
            val view=h.createView(ui.activity,id,manager.getAppWidgetInfo(id));widgetView=view
            view.setPadding(0,0,0,0)
            frame.addView(view,FrameLayout.LayoutParams((width*density).toInt(),(height*density).toInt(),Gravity.CENTER))
            h.startListening()
        }
        runBlocking {WidgetUpdates.refresh(app)}
    }
    private fun unmount() {
        ui.runOnUiThread {overlay?.let {(it.parent as? ViewGroup)?.removeView(it)};host?.stopListening();host?.deleteHost();host=null;overlay=null;widgetView=null}
    }
    private fun tapText(prefix:String) {
        // RemoteViews can render while the Activity's opening transition still
        // routes touches to Android's ActivityRecordInputSink (system UID).
        // Wait for the actual window and accessibility transitions before tapping.
        await {
            var focused=false
            ui.runOnUiThread {focused=ui.activity.window.decorView.hasWindowFocus() && widgetView?.isShown==true}
            focused
        }
        instrumentation.uiAutomation.waitForIdle(250,5000)
        val point=IntArray(2)
        ui.runOnUiThread {val view=views(requireNotNull(widgetView)).filterIsInstance<TextView>().first {it.text.toString()==prefix}
            view.getLocationOnScreen(point);point[0]+=view.width/2;point[1]+=view.height/2}
        val now=android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach {action->
            val event=MotionEvent.obtain(now,now+50,action,point[0].toFloat(),point[1].toFloat(),0)
            instrumentation.sendPointerSync(event);event.recycle()
        }
    }
    @Before fun fixture() {
        runBlocking {app.repository.restore(Snapshot(preferences=Preferences(celebrate=false,darkMode="LIGHT")))}
        await {ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun cleanup() {unmount();runBlocking {WidgetUpdates.refresh(app)}}
    @Test fun realRecordWidgetShowsOnlyThreeStagesRefreshesAndOpensDisplayedYesterday() {
        val yesterday=LocalDate.now().minusDays(1).toString()
        val record=Treatment(id="yesterday",date=yesterday,saved=true,usageConfirmed=true,weightGrams=62000,systolic=120,diastolic=80)
        runBlocking {app.repository.save(record,true)}
        mount(DailyRecordWidgetReceiver::class.java)
        await {texts().contains("오늘") && descriptions().containsAll(listOf("어제 활력 상태 완료","어제 사용 구성 완료","어제 투석 기록 미완료"))}
        assertFitsWithoutScrolling()
        assertFalse(texts().any {it.contains("62000") || it.contains("초기배액") || it.contains("남은 입력")})
        val before=snapshot()
        tapText("어제")
        await {ui.onAllNodesWithText("기록 저장").fetchSemanticsNodes().isNotEmpty()}
        unmount()
        ui.runOnIdle {assertEquals(yesterday,ViewModelProvider(ui.activity)[JournalViewModel::class.java].editor.value?.date)}
        assertEquals(before.treatments,snapshot().treatments);assertEquals(before.usages,snapshot().usages)
        runBlocking {app.repository.save(record.copy(initialDrain=2100,machineUf=800),true)}
        mount(DailyRecordWidgetReceiver::class.java)
        await {descriptions().contains("어제 투석 기록 완료")}
        runBlocking {app.repository.preferences(snapshot().preferences.copy(lock=true))}
        await {texts().contains("잠금 해제 후 확인 ›")}
        assertFalse(texts().any {it=="어제" || it.contains("활력 상태")})
        assertFalse(descriptions().any {it.contains("어제 활력")})
    }
    @Test fun realAppointmentWidgetShowsEachTimeAndRemovedAppointmentOpensList() {
        val a=Department(id="a",name="신장내과");val b=Department(id="b",name="내분비내과")
        val care=listOf(CareTask(id="c1",name="피검사",iconKey="blood"),CareTask(id="c2",name="드레싱",iconKey="healing"),
            CareTask(id="c3",name="소변검사",iconKey="lab"),CareTask(id="c4",name="체성분검사",iconKey="medical"))
        val visit=Appointment(id="visit",date=LocalDate.now().plusDays(3).toString(),departments=listOf(a,b),departmentTimes=mapOf("a" to "09:30","b" to "11:20"),careItems=care,memo="위젯에 숨길 메모")
        runBlocking {app.repository.appointment(visit)}
        mount(AppointmentWidgetReceiver::class.java,width=156)
        await {texts().contains("D-3") && texts().any {it.contains("09:30  신장내과") && it.contains("11:20  내분비내과")} && texts().containsAll(care.map {it.name})}
        assertFitsWithoutScrolling()
        assertFalse(texts().any {it.contains(visit.memo)})
        tapText("D-3")
        await {ui.onAllNodesWithText("병원 일정 수정").fetchSemanticsNodes().isNotEmpty()}
        unmount()
        runBlocking {app.repository.deleteAppointment(visit.id)}
        ui.runOnUiThread {ui.activity.startActivity(WidgetNavigation.intent(app,WidgetTarget("appointment",visit.id)))}
        await {ui.onAllNodesWithText("병원 일정 관리").fetchSemanticsNodes().isNotEmpty()}
        mount(AppointmentWidgetReceiver::class.java)
        await {texts().contains("예정된 병원 일정이 없어요")}
    }
    @Test fun compactRecordAndDenseCareFitInBothThemesWithoutScrolling() {
        val today=LocalDate.now().toString()
        runBlocking {app.repository.save(Treatment(id="today",date=today,saved=true,usageConfirmed=true,weightGrams=62000,systolic=120,diastolic=80),true)}
        listOf("LIGHT","DARK").forEach {mode ->
            runBlocking {app.repository.preferences(snapshot().preferences.copy(darkMode=mode))}
            mount(DailyRecordWidgetReceiver::class.java,width=148,height=156)
            await {texts().containsAll(listOf("어제·오늘","어제","오늘","활력","구성","투석")) && descriptions().contains("오늘 투석 기록 미완료")}
            assertTrue(descriptions().contains("오늘 활력 상태 완료"));assertFitsWithoutScrolling();unmount()
        }
        val department=Department(id="long",name="신장내과 복막투석 외래")
        val items=listOf("피검사","드레싱","소변검사","체성분검사","복막평형 기능검사","투석 도관 상태 확인").mapIndexed {i,name->CareTask(id="long$i",name=name,iconKey="lab")}
        runBlocking {app.repository.appointment(Appointment(id="dense",date=LocalDate.now().plusDays(2).toString(),departments=listOf(department),
            departmentTimes=mapOf(department.id to "09:30"),careItems=items,memo="표시 금지"))}
        mount(AppointmentWidgetReceiver::class.java,width=148,height=156)
        await {texts().containsAll(items.map {it.name})}
        assertFitsWithoutScrolling();assertFalse(texts().any {it.contains("표시 금지")})
    }

    @Test fun adaptiveHostSizesKeepAllContentAndTallVisitsUseSingleColumn() {
        val a=Department(id="a",name="신장내과 (샘플)");val b=Department(id="b",name="내분비내과 (샘플)")
        val care=listOf("피검사","드레싱","소변검사","체성분검사").mapIndexed {i,name->CareTask(id="c$i",name=name,iconKey="lab")}
        runBlocking {app.repository.appointment(Appointment(id="adaptive",date=LocalDate.now().plusDays(1).toString(),
            departments=listOf(a,b),departmentTimes=mapOf("a" to "09:30","b" to "11:20"),careItems=care,memo="표시 금지"))}
        // The two very wide landscape hosts are inspected with the device rotated, separately.
        val sizes=listOf(110 to 110,148 to 156,158 to 172,146 to 274,303 to 274,328 to 172,382 to 274,265 to 135)
        for(mode in listOf("LIGHT","DARK")) {
            runBlocking {app.repository.preferences(snapshot().preferences.copy(darkMode=mode))}
            for((width,height) in sizes) for(provider in listOf(DailyRecordWidgetReceiver::class.java,AppointmentWidgetReceiver::class.java)) {
                instrumentation.sendStatus(0,Bundle().apply {putString("adaptive_case","$mode ${width}x$height ${provider.simpleName}")})
                mount(provider,height,width)
                val records=provider==DailyRecordWidgetReceiver::class.java
                await {if(records) texts().contains("어제") else texts().containsAll(care.map {it.name})}
                assertFitsWithoutScrolling()
                if(records) assertEquals(6,descriptions().count {it.endsWith("완료")})
                else {
                    care.forEach {item->assertEquals(1,texts().count {it==item.name})}
                    assertFalse(texts().contains("표시 금지"))
                    if(width==146 && height==274) ui.runOnUiThread {
                        val root=requireNotNull(widgetView)
                        val names=views(root).filterIsInstance<TextView>().filter {it.text.toString() in care.map {c->c.name}}
                        val positions=names.map {v->IntArray(2).also {v.getLocationOnScreen(it)}}
                        assertEquals(1,positions.map {it[0]}.distinct().size)
                        assertTrue(positions.zipWithNext().all {(first,next)->first[1]<next[1]})
                        assertTrue(names.all {it.layout.lineCount==1})
                        val bottom=IntArray(2);root.getLocationOnScreen(bottom)
                        val last=names.last();val position=IntArray(2);last.getLocationOnScreen(position)
                        val density=ui.activity.resources.displayMetrics.density
                        assertTrue("Excess trailing whitespace",bottom[1]+root.height-(position[1]+last.height)<32*density)
                    }
                }
                unmount()
            }
        }
    }

    @Test fun adaptiveDenseOddAndLegacyCarePreservesEveryName() {
        val names=(0 until 12).map {if(it%3==0) "복막평형 기능검사 및 도관 상태 확인 $it" else "처치 항목 $it"}
        for(count in listOf(1,5,12)) {
            val tasks=names.take(count).mapIndexed {i,name->CareTask(id="c$i",name=name)}
            runBlocking {app.repository.appointment(Appointment(id="dense-adaptive",date=LocalDate.now().plusDays(365).toString(),
                time="09:30",care=CareTemplate(name="이전 치료 구성",tasks=tasks)))}
            for((width,height) in listOf(110 to 110,148 to 156,146 to 274,265 to 135)) {
                instrumentation.sendStatus(0,Bundle().apply {putString("dense_case","$count items ${width}x$height")})
                mount(AppointmentWidgetReceiver::class.java,height,width)
                await {texts().containsAll(tasks.map {it.name})}
                assertFitsWithoutScrolling();unmount()
            }
        }
    }
    @Test fun landscapeHostsKeepEveryStageAndCareVisible() {
        val orientation=ui.activity.requestedOrientation
        try {
            ui.runOnUiThread {ui.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            await {ui.activity.resources.configuration.screenWidthDp>=681}
            val items=listOf("피검사","드레싱","소변검사","체성분검사").mapIndexed {i,name->CareTask(id="c$i",name=name)}
            runBlocking {app.repository.appointment(Appointment(id="landscape",date=LocalDate.now().plusDays(2).toString(),
                departments=listOf(Department(id="a",name="신장내과"),Department(id="b",name="내분비내과")),
                departmentTimes=mapOf("a" to "09:30","b" to "11:20"),careItems=items))}
            for(width in listOf(542,681)) for(provider in listOf(DailyRecordWidgetReceiver::class.java,AppointmentWidgetReceiver::class.java)) {
                mount(provider,135,width)
                await {if(provider==DailyRecordWidgetReceiver::class.java) texts().contains("어제") else texts().containsAll(items.map {it.name})}
                assertFitsWithoutScrolling();unmount()
            }
            for(width in listOf(542,681)) {
                mount(DailyRecordWidgetReceiver::class.java,51,width)
                await {texts().contains("어제")}
                assertFitsWithoutScrolling();assertEquals(6,descriptions().count {it.endsWith("완료")});unmount()
            }
        } finally {
            unmount()
            ui.runOnUiThread {ui.activity.requestedOrientation=orientation}
        }
    }
    @Test fun singleRowRecordsResizeWithoutClippingAndKeepStagesHorizontal() {
        val sizes=listOf(303 to 131,276 to 102,328 to 80,303 to 48,110 to 48,146 to 80)
        for(mode in listOf("LIGHT","DARK")) {
            runBlocking {app.repository.preferences(snapshot().preferences.copy(darkMode=mode))}
            for((width,height) in sizes) {
                instrumentation.sendStatus(0,Bundle().apply {putString("single_row_case","$mode ${width}x$height")})
                mount(DailyRecordWidgetReceiver::class.java,height,width)
                await {texts().containsAll(listOf("어제","오늘"))}
                assertFitsWithoutScrolling();assertEquals(6,descriptions().count {it.endsWith("완료")})
                ui.runOnUiThread {
                    val root=requireNotNull(widgetView)
                    for(day in listOf("어제","오늘")) {
                        val stages=views(root).filter {it.contentDescription?.toString()?.startsWith("$day ")==true}
                        val points=stages.map {v->IntArray(2).also {v.getLocationOnScreen(it);it[0]+=v.width/2;it[1]+=v.height/2}}
                        assertEquals(3,points.size)
                        assertTrue(points.zipWithNext().all {(a,b)->a[0]<b[0] && kotlin.math.abs(a[1]-b[1])<=2})
                    }
                }
                unmount()
            }
        }
    }
    @Test fun systemThemeChangesWidgetColorsAndKeepsContentFitted() {
        val manager=app.getSystemService(android.app.UiModeManager::class.java)
        val initial=manager.nightMode
        fun night(mode:String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cmd uimode night $mode")).use {it.readBytes()}
        }
        try {
            runBlocking {app.repository.preferences(snapshot().preferences.copy(darkMode="SYSTEM"))}
            for(dark in listOf(false,true)) {
                night(if(dark) "yes" else "no")
                await {(ui.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==
                    if(dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO}
                mount(DailyRecordWidgetReceiver::class.java,274,146)
                await {texts().contains("어제")}
                assertFitsWithoutScrolling()
                ui.runOnUiThread {
                    val title=views(requireNotNull(widgetView)).filterIsInstance<TextView>().first {it.text.toString()=="어제"}
                    assertEquals(if(dark) 0xfff0f5fc.toInt() else 0xff12304f.toInt(),title.currentTextColor)
                }
                unmount()
            }
        } finally {
            unmount()
            night(when(initial) {android.app.UiModeManager.MODE_NIGHT_YES->"yes";android.app.UiModeManager.MODE_NIGHT_NO->"no";else->"auto"})
        }
    }
    @Test fun widgetNavigationFlushesImmediateDraftAndResumesItAcrossWarmTapsAndRecreation() {
        val yesterday=LocalDate.now().minusDays(1).toString()
        val draft=Treatment(id="yesterday-draft",date=yesterday,memo="어제의 초안")
        runBlocking {app.repository.draft(draft)}
        click("오늘 기록 시작")
        var todayId=""
        ui.runOnIdle {
            val vm=ViewModelProvider(ui.activity)[JournalViewModel::class.java]
            todayId=vm.editor.value!!.id
            vm.change(vm.editor.value!!.copy(weightGrams=61000,memo="방금 입력한 오늘 초안"))
            ui.activity.startActivity(WidgetNavigation.intent(app,WidgetTarget("record",yesterday)))
        }
        await {snapshot().drafts.any {it.id==todayId && it.treatment.memo=="방금 입력한 오늘 초안"}}
        await {var okay=false;ui.runOnUiThread {okay=ViewModelProvider(ui.activity)[JournalViewModel::class.java].editor.value?.id==draft.id};okay}
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("기록 저장").assertExists()
        ui.runOnUiThread {ui.activity.startActivity(WidgetNavigation.intent(app,WidgetTarget("record",today())))}
        await {var okay=false;ui.runOnUiThread {okay=ViewModelProvider(ui.activity)[JournalViewModel::class.java].editor.value?.id==todayId};okay}
        ui.runOnIdle {assertEquals("방금 입력한 오늘 초안",ViewModelProvider(ui.activity)[JournalViewModel::class.java].editor.value?.memo)}
        assertTrue(snapshot().treatments.isEmpty());assertTrue(snapshot().usages.isEmpty())
    }
    @Test fun contactOrderPersistsOnlyOnSaveAndSurvivesHelpAndRecreation() {
        val names=listOf("투석실","간호사","고객센터")
        names.forEachIndexed {i,name->runBlocking {app.repository.createContact(Contact(id="c$i",name=name,phone="02-123-4567",createdAt=i.toLong()))}}
        ui.onNodeWithContentDescription("설정").performClick();click("연락처 관리")
        val register=ui.onNodeWithText("+ 연락처 등록").fetchSemanticsNode().boundsInRoot
        val reorder=ui.onNodeWithText("순서 변경").fetchSemanticsNode().boundsInRoot
        assertTrue(register.right<=reorder.left);assertTrue(kotlin.math.abs(register.center.y-reorder.center.y)<2f)
        click("순서 변경")
        show(ui.onNodeWithContentDescription("간호사 위로")).performClick()
        assertEquals(names,snapshot().orderedContacts().map {it.name})
        ui.activityRule.scenario.recreate()
        show(ui.onNodeWithContentDescription("간호사 위로")).assertIsNotEnabled()
        ui.onNodeWithContentDescription("이 화면 사용 안내").performClick()
        ui.onNodeWithContentDescription("뒤로").performClick()
        show(ui.onNodeWithContentDescription("간호사 위로")).assertIsNotEnabled()
        click("순서 저장");await {snapshot().orderedContacts().first().name=="간호사"}
        click("순서 변경");show(ui.onNodeWithContentDescription("투석실 위로")).performClick()
        click("취소");click("확인")
        assertEquals(listOf("간호사","투석실","고객센터"),snapshot().orderedContacts().map {it.name})
        repeat(2){ui.onNodeWithContentDescription("뒤로").performClick()}
        show(ui.onNodeWithContentDescription("간호사 연락처"))
        val first=ui.onNodeWithContentDescription("간호사 연락처").fetchSemanticsNode().boundsInRoot
        val second=ui.onNodeWithContentDescription("투석실 연락처").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left<second.left)
    }
}
