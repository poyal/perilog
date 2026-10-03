package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLayoutTest {
    private val care=listOf("피검사","드레싱","소변검사","체성분검사").mapIndexed {i,name->CareTask(id="c$i",name=name,iconKey="lab")}
    @Test fun compactVisitKeepsEveryDepartmentTimeAndCareName() {
        val a=Department(id="a",name="신장내과");val b=Department(id="b",name="내분비내과")
        val visit=Appointment(date="2026-10-06",departments=listOf(b,a),departmentTimes=mapOf("a" to "09:30","b" to "11:20"),careItems=care)
        val layout=fitWidgetAppointment(visit,142f,122f)
        assertEquals("09:30  신장내과\n11:20  내분비내과",layout.departments)
        assertEquals(care,layout.careRows.flatMap {it.items})
        assertTrue("height=${layout.totalHeight}",layout.totalHeight<=122);assertTrue("font=${layout.font}",layout.font>=7)
    }
    @Test fun narrowDenseVisitWrapsLegacyCareWithoutDroppingItems() {
        val items=care+listOf(CareTask(id="long",name="복막평형 기능검사 및 도관 상태 확인",iconKey="lab"))
        val visit=Appointment(date="2026-10-06",time="09:30",care=CareTemplate(name="예전 묶음",tasks=items))
        val layout=fitWidgetAppointment(visit,132f,110f)
        assertEquals(items.map {it.name to it.iconKey},layout.careRows.flatMap {it.items}.map {it.name to it.iconKey})
        assertTrue("height=${layout.totalHeight}",layout.totalHeight<=110)
        assertEquals("09:30",layout.departments)
    }
    @Test fun absentCareLeavesNoEmptyTreatmentSection() {
        val layout=fitWidgetAppointment(Appointment(date="2026-10-06",time="09:30"),142f,122f)
        assertTrue(layout.careRows.isEmpty());assertEquals(1f,layout.scale)
    }

    @Test fun actualTallLauncherUsesReadableSingleColumnAndFillsBody() {
        for(suffix in listOf(""," (샘플)")) {
        val a=Department(id="a",name="신장내과$suffix");val b=Department(id="b",name="내분비내과$suffix")
        val visit=Appointment(date="2026-10-04",departments=listOf(a,b),departmentTimes=mapOf("a" to "09:30","b" to "11:20"),careItems=care)
        val area=widgetViewport(145.5238f,273.5238f)
        val plan=fitWidgetAppointment(visit,area.width,area.height,WidgetTextMeasurer(2.625f),"D-1")
        assertEquals("$plan",1,plan.columns);assertFalse(plan.split);assertEquals(0,plan.wrappedLines)
        assertTrue("font=${plan.font}",plan.font>=11.5f)
        assertTrue(area.height-plan.totalHeight-plan.topInset<=16)
        assertEquals(care,plan.careRows.flatMap {it.items})
        }
    }

    @Test fun everyNameFitsAcrossHostSizesDensitiesAndItemCounts() {
        val sizes=listOf(110 to 110,148 to 156,158 to 172,146 to 274,303 to 274,328 to 172,382 to 274,265 to 135,542 to 135)
        for(density in listOf(1f,2f,2.625f,3f)) for((width,height) in sizes) for(count in listOf(0,1,4,6,12)) {
            val tasks=(0 until count).map {CareTask(id="$it",name=if(it%3==0) "복막평형 기능검사 및 도관 상태 확인" else care[it%4].name)}
            val area=widgetViewport(width.toFloat(),height.toFloat())
            val plan=fitWidgetAppointment(Appointment(date="2027-10-04",time="09:30",careItems=tasks),area.width,area.height,WidgetTextMeasurer(density),"D-365")
            assertEquals(tasks,plan.careRows.flatMap {it.items})
            assertTrue("${width}x$height density=$density count=$count height=${plan.totalHeight}",plan.totalHeight+plan.topInset<=area.height)
            assertTrue(plan.font>0 && plan.careRows.all {it.height>0})
        }
    }

    @Test fun recordModesUseBothDimensionsAndRemainInsideCards() {
        assertEquals(RecordWidgetMode.WIDE,recordWidgetLayout(328f,172f).mode)
        assertEquals(RecordWidgetMode.WIDE_TALL,recordWidgetLayout(303f,274f).mode)
        assertEquals(RecordWidgetMode.WIDE_TALL,recordWidgetLayout(382f,274f).mode)
        assertEquals(RecordWidgetMode.MATRIX,recordWidgetLayout(158f,172f).mode)
        assertEquals(RecordWidgetMode.STACKED,recordWidgetLayout(146f,274f).mode)
        assertEquals(RecordWidgetMode.MATRIX,recordWidgetLayout(259f,172f).mode)
        assertEquals(RecordWidgetMode.WIDE,recordWidgetLayout(260f,172f).mode)
        assertEquals(RecordWidgetMode.WIDE,recordWidgetLayout(303f,219f).mode)
        assertEquals(RecordWidgetMode.WIDE_TALL,recordWidgetLayout(303f,220f).mode)
        val minimum=recordWidgetLayout(110f,110f)
        assertTrue(minimum.inlineDate);assertTrue(minimum.stageSize<=minimum.cardHeight-6)
    }

    @Test fun textMeasurementUsesPhysicalDensityWithoutChangingDpFontSize() {
        val normal=WidgetTextMeasurer(1f).measure("D-3",100f,23f,true)
        val dense=WidgetTextMeasurer(2.625f).measure("D-3",100f,23f,true)
        assertTrue("$normal / $dense",kotlin.math.abs(normal.height-dense.height)<3f)
    }
    @Test fun oneRowRecordsKeepHorizontalStagesAtPortraitAndLandscapeHeights() {
        for((width,height) in listOf(303 to 131,276 to 102,328 to 80,554 to 51,303 to 48,110 to 48,146 to 80)) {
            val area=widgetViewport(width.toFloat(),height.toFloat(),records=true)
            val plan=recordWidgetLayout(width.toFloat(),height.toFloat())
            assertEquals(height.toFloat(),area.height+area.header+2*area.padding+area.gap,.01f)
            assertTrue(plan.stageSize<=plan.cardHeight-2)
            assertEquals(if(width<260) RecordWidgetMode.MATRIX else if(height<110) RecordWidgetMode.STRIP else RecordWidgetMode.WIDE,plan.mode)
        }
    }
}
