package com.poyal.perilog

import android.Manifest
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

/** Revoke READ_CALENDAR/WRITE_CALENDAR with adb BEFORE launching this class. */
class CalendarPermissionFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun denyingCalendarPermissionKeepsTheAppointmentAndConnectionOff() {
        val app = ApplicationProvider.getApplicationContext<PerilogApplication>()
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_CALENDAR))
        val visit = Appointment(date = LocalDate.now().plusDays(3).toString(), time = "09:00", memo = "권한 거절 검사")
        runBlocking {
            app.calendar.store.dao.connections().forEach {app.calendar.store.disconnect(it.id, false)}
            app.repository.restore(Snapshot(appointments = listOf(visit)))
        }
        ui.waitUntil(15000) {ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("설정").performClick()
        ui.onNode(hasText("캘린더 연동") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithText("휴대폰 캘린더 연결").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.waitUntil(15000) {
            automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")?.isNotEmpty() == true
        }
        assertTrue(automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button").first().performAction(AccessibilityNodeInfo.ACTION_CLICK))
        ui.waitUntil(10000) {ui.onAllNodesWithText("캘린더 읽기·쓰기 권한이 필요해요. 거절해도 병원 예약은 앱에 그대로 저장됩니다.").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(visit, runBlocking {app.repository.snapshot().appointments.single()})
        assertTrue(runBlocking {app.calendar.store.dao.jobs().isEmpty()})
        assertTrue(runBlocking {app.calendar.store.dao.connections().all {it.state == "OFF"}})
    }
}
