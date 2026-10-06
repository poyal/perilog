package com.poyal.perilog.capture

import android.appwidget.*
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.SizeF
import android.view.*
import android.widget.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.MainActivity
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import com.poyal.perilog.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.LocalDate

/** Actual AppWidgetHost views using synthetic data; only on the disposable capture emulator. */
class WidgetDesignCapture {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private var host:AppWidgetHost?=null
    private var overlay:FrameLayout?=null
    private val widgets=mutableListOf<AppWidgetHostView>()
    private fun views(v:View):List<View> = listOf(v)+(if(v is android.view.ViewGroup)(0 until v.childCount).flatMap {views(v.getChildAt(it))}else emptyList())
    @Before fun seed() {
        val date=LocalDate.now();val a=Department(id="a",name="신장내과");val b=Department(id="b",name="내분비내과")
        val care=listOf("피검사" to "blood","드레싱" to "healing","소변검사" to "lab","체성분검사" to "medical").mapIndexed {i,(name,icon)->CareTask(id="care$i",name=name,iconKey=icon,time=if(i==0)"07:30"else null)}
        runBlocking {
            app.repository.restore(Snapshot(preferences=Preferences(celebrate=false,darkMode="LIGHT"),appointments=listOf(
                Appointment(id="visit",date=date.plusDays(3).toString(),departments=listOf(a,b),departmentTimes=mapOf("a" to "09:30","b" to "11:20"),careItems=care))))
            val record=Treatment(id="today",date=date.toString(),saved=true,usageConfirmed=true,weightGrams=62000,systolic=120,diastolic=80)
            app.repository.save(record,true)
            app.repository.save(record.copy(id="yesterday",date=date.minusDays(1).toString(),initialDrain=2100,machineUf=800),true)
        }
    }
    @After fun cleanup() {ui.runOnUiThread {overlay?.let {(it.parent as? ViewGroup)?.removeView(it)};host?.stopListening();host?.deleteHost()}}
    private fun capture(dark:Boolean,name:String,tall:Boolean=false) {
        val widgetHeight=if(tall) 274 else 172
        runBlocking {app.repository.preferences(app.repository.snapshot().preferences.copy(darkMode=if(dark) "DARK" else "LIGHT"))}
        ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("appwidget grantbind --package ${app.packageName} --user 0")).use {it.readBytes()}
        ui.runOnUiThread {
            val context=ui.activity;val density=context.resources.displayMetrics.density
            fun dp(value:Int)=(value*density).toInt()
            val manager=AppWidgetManager.getInstance(app)
            val h=AppWidgetHost(context,9027);host=h;h.deleteHost()
            val frame=FrameLayout(context).apply {setBackgroundColor(if(dark) 0xff0d1723.toInt() else 0xffeef3f8.toInt())};overlay=frame
            context.addContentView(frame,ViewGroup.LayoutParams(-1,-1))
            val board=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
            frame.addView(board,FrameLayout.LayoutParams(dp(328),-2,Gravity.CENTER))
            fun label(text:String)=TextView(context).apply {this.text=text;textSize=13f;setTextColor(if(dark) 0xffb7c7d9.toInt() else 0xff536981.toInt());setPadding(0,dp(12),0,dp(6))}
            board.addView(label(if(dark) "페리로그 위젯 · 다크" else "페리로그 위젯 · 라이트"))
            fun widget(provider:Class<*>,width:Int):AppWidgetHostView {
                val id=h.allocateAppWidgetId()
                val options=Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,widgetHeight);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,widgetHeight)
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(width.toFloat(),widgetHeight.toFloat())))
                }
                check(manager.bindAppWidgetIdIfAllowed(id,ComponentName(app,provider),options))
                return h.createView(context,id,manager.getAppWidgetInfo(id)).apply {setPadding(0,0,0,0);widgets+=this}
            }
            board.addView(label("기록 · 4×2"))
            val recordWidth=if(tall) 303 else 328
            board.addView(widget(DailyRecordWidgetReceiver::class.java,recordWidth),LinearLayout.LayoutParams(dp(recordWidth),dp(widgetHeight)))
            board.addView(label("기록 · 2×2                      병원 일정 · 2×2"))
            val row=LinearLayout(context);board.addView(row)
            val smallWidth=if(tall) 146 else 158
            row.addView(widget(CompactRecordWidgetReceiver::class.java,smallWidth),LinearLayout.LayoutParams(dp(smallWidth),dp(widgetHeight)))
            row.addView(Space(context),LinearLayout.LayoutParams(dp(12),1))
            row.addView(widget(AppointmentWidgetReceiver::class.java,smallWidth),LinearLayout.LayoutParams(dp(smallWidth),dp(widgetHeight)))
            h.startListening()
        }
        runBlocking {WidgetUpdates.refresh(app)}
        ui.waitUntil(25000) {
            var ready=false
            ui.runOnUiThread {ready=widgets.size==3 && widgets.all {v->views(v).filterIsInstance<TextView>().any {it.text.toString() in listOf("어제","피검사 07:30")}}}
            ready
        }
        android.os.SystemClock.sleep(700)
        val directory=File(app.filesDir,"manual-screenshots").apply {mkdirs()}
        File(directory,"$name.png").outputStream().use {instrument.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun light()=capture(false,"63-widgets-light")
    @Test fun dark()=capture(true,"64-widgets-dark")
    @Test fun tallLight()=capture(false,"65-widgets-tall-light",true)
    @Test fun tallDark()=capture(true,"66-widgets-tall-dark",true)
    @Test fun prepareLauncher() { /* @Before loads only synthetic data on the capture device. */ }
}
