package com.poyal.perilog.capture

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.MainActivity
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import com.poyal.perilog.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Synthetic captures only. prepareScreens replaces data: use the capture emulator. */
@RunWith(AndroidJUnit4::class)
class WidgetCapture {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private fun show(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=node.onAncestors().filter(hasScrollAction())
        for(i in parents.fetchSemanticsNodes().indices.reversed())runCatching {parents[i].performScrollTo()}
        runCatching {node.performScrollTo()};return node
    }
    private fun click(text:String)=show(ui.onNodeWithText(text)).performClick()
    private fun shot(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(500)
        val dir=File(app.filesDir,"manual-screenshots").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun captureContactMenu() {
        runBlocking {app.repository.restore(Snapshot(preferences=Preferences(celebrate=false,darkMode="LIGHT"),
            contacts=listOf(Contact(id="dialysis",name="투석실",phone="02-123-4567",emoji="🏥",createdAt=1),
                Contact(id="nurse",name="담당 간호사",phone="010-1234-5678",emoji="🧑‍⚕️",createdAt=2),
                Contact(id="service",name="고객센터",phone="1588-1234",emoji="☎️",createdAt=3))))}
        ui.waitUntil(15000) {ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick();click("연락처 관리")
        show(ui.onNodeWithContentDescription("투석실 더보기")).performClick();shot("62-contact-menu")
    }
    @Test fun prepareScreens() {
        val today=LocalDate.now();val a=Department(id="kidney",name="신장내과");val b=Department(id="endo",name="내분비내과")
        val machine=Treatment(id="machine-today",date=today.toString(),saved=true,usageConfirmed=true,weightGrams=62000,systolic=120,diastolic=80)
        runBlocking {app.repository.restore(Snapshot(preferences=Preferences(celebrate=false,darkMode="LIGHT"),
            departments=listOf(a,b),appointments=listOf(
                Appointment(id="next",date=today.plusDays(3).toString(),departments=listOf(a,b),departmentTimes=mapOf(a.id to "09:30",b.id to "11:20")),
                Appointment(id="later",date=today.plusDays(17).toString(),departments=listOf(a),departmentTimes=mapOf(a.id to "10:00"))),
            contacts=listOf(Contact(id="dialysis",name="투석실",phone="02-123-4567",emoji="🏥",createdAt=1),
                Contact(id="nurse",name="담당 간호사",phone="010-1234-5678",emoji="🧑‍⚕️",createdAt=2),
                Contact(id="service",name="고객센터",phone="1588-1234",emoji="☎️",createdAt=3))))
            app.repository.save(machine,true)
            app.repository.save(machine.copy(id="machine-yesterday",date=today.minusDays(1).toString(),initialDrain=2100,machineUf=800),true)
        }
        ui.waitUntil(15000) {ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick();shot("18-settings")
        click("연락처 관리");click("순서 변경")
        show(ui.onNodeWithContentDescription("담당 간호사 위로")).performClick();shot("60-contact-order")
        click("순서 저장");ui.waitForIdle()
        ui.onNodeWithContentDescription("뒤로").performClick();click("홈 화면 위젯");shot("59-widget-settings")
    }
    @Test fun pinRecord() {ui.runOnIdle {AppWidgetManager.getInstance(app).requestPinAppWidget(ComponentName(app,DailyRecordWidgetReceiver::class.java),null,null)};android.os.SystemClock.sleep(1500)}
    @Test fun pinAppointments() {ui.runOnIdle {AppWidgetManager.getInstance(app).requestPinAppWidget(ComponentName(app,AppointmentWidgetReceiver::class.java),null,null)};android.os.SystemClock.sleep(1500)}
    @Test fun darkWidgets() {runBlocking {app.repository.preferences(app.repository.snapshot().preferences.copy(darkMode="DARK"));WidgetUpdates.refresh(app)}}
    @Test fun lightWidgets() {runBlocking {app.repository.preferences(app.repository.snapshot().preferences.copy(darkMode="LIGHT"));WidgetUpdates.refresh(app)}}
}
