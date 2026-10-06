package com.poyal.perilog.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.action.ActionParameters
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.R
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

val widgetRefreshKey = longPreferencesKey("widget_updated_at")

abstract class PerilogWidget(private val records:Boolean):GlanceAppWidget() {
    // Fixed, non-scrolling content fitted to the actual space supplied by the launcher.
    override val sizeMode=SizeMode.Exact
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        val initialTime=System.currentTimeMillis()
        val initialSnapshot=runCatching {(context.applicationContext as PerilogApplication).repository.snapshot()}
        provideContent {
            val stamp=currentState<androidx.datastore.preferences.core.Preferences>()[widgetRefreshKey] ?: initialTime
            val loaded by produceState(initialSnapshot,stamp) {
                value=runCatching {(context.applicationContext as PerilogApplication).repository.snapshot()}
            }
            val snapshot=loaded.getOrNull()
            val colors=WidgetColors(context,snapshot?.preferences?.darkMode ?: "SYSTEM")
            val now=Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val size=LocalSize.current
            val wide=records && size.width>=260.dp
            val viewport=widgetViewport(size.width.value,size.height.value,records)
            val width=viewport.width;val height=viewport.height;val header=viewport.header
            val shortHeader=records && size.height<110.dp
            val recordPlan=recordWidgetLayout(size.width.value,size.height.value)
            Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(colors.surface))
                .cornerRadius(android.R.dimen.system_app_widget_background_radius).padding(viewport.padding.dp)) {
                Row(GlanceModifier.fillMaxWidth().height(header.dp),verticalAlignment=Alignment.CenterVertically) {
                    Row(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget(if(records) "home" else "appointments")))),
                        verticalAlignment=Alignment.CenterVertically) {
                        Image(ImageProvider(if(records) R.drawable.ic_widget_records else R.drawable.ic_widget_calendar),
                            if(records) "기록" else "병원 일정",GlanceModifier.size((if(shortHeader) minOf(16f,header-2) else if(wide) 19f else 16f).dp),colorFilter=ColorFilter.tint(colors.primary))
                        Spacer(GlanceModifier.width(5.dp))
                        Text(if(records) {if(wide) "어제·오늘 기록" else "어제·오늘"} else "병원 일정",GlanceModifier.defaultWeight(),
                            style=colors.text(if(shortHeader) minOf(12f,header*.65f) else if(wide) 15f else if(width<110) 9f else if(width<135) 11.5f else 13f,true),maxLines=1)
                    }
                    Image(ImageProvider(R.drawable.ic_widget_refresh),"위젯 새로고침",
                        GlanceModifier.size(header.dp).padding(if(shortHeader) 1.dp else 5.dp).clickable(actionRunCallback<RefreshWidgetAction>()),colorFilter=ColorFilter.tint(colors.secondary))
                }
                Spacer(GlanceModifier.height(viewport.gap.dp))
                when {
                    snapshot==null -> Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionRunCallback<RefreshWidgetAction>()),contentAlignment=Alignment.Center) {
                        Text("불러오지 못했어요\n눌러서 다시 시도",style=colors.text())
                    }
                    records -> {
                        val yesterday=snapshot.dailyProgress(now.toLocalDate().minusDays(1).toString())
                        val today=snapshot.dailyProgress(now.toLocalDate().toString())
                        if(wide) Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            RecordDay(yesterday,"어제",false,colors,recordPlan,GlanceModifier.defaultWeight().fillMaxHeight())
                            Spacer(GlanceModifier.width(6.dp))
                            RecordDay(today,"오늘",true,colors,recordPlan,GlanceModifier.defaultWeight().fillMaxHeight())
                        } else if(recordPlan.mode==RecordWidgetMode.STACKED) Column(GlanceModifier.fillMaxWidth().height(height.dp)) {
                            RecordDay(yesterday,"어제",false,colors,recordPlan,GlanceModifier.fillMaxWidth().height(recordPlan.cardHeight.dp))
                            Spacer(GlanceModifier.height(6.dp))
                            RecordDay(today,"오늘",true,colors,recordPlan,GlanceModifier.fillMaxWidth().height(recordPlan.cardHeight.dp))
                        } else CompactDays(yesterday,today,colors,recordPlan,height)
                    }
                    else -> {
                        val next=upcomingWidgetAppointments(snapshot,now).firstOrNull()
                        if(next==null) Box(GlanceModifier.fillMaxWidth().defaultWeight().background(ImageProvider(colors.panel))
                            .clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget("newAppointment")))),contentAlignment=Alignment.Center) {
                            Text("예정된 병원 일정이 없어요",GlanceModifier.padding(8.dp),style=colors.text(),maxLines=3)
                        } else AppointmentBody(next,now,colors,width,height)
                    }
                }
            }
        }
    }
}

private class WidgetColors(private val context:Context,mode:String) {
    private val system=mode !in listOf("LIGHT","DARK")
    private val dark=when(mode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
    }
    private fun color(light:Long,night:Long)=if(system) ColorProvider(day=Color(light),night=Color(night)) else ColorProvider(Color(if(dark) night else light))
    val surface=if(system) R.drawable.widget_surface_system else if(dark) R.drawable.widget_surface_dark else R.drawable.widget_surface_light
    val panel=if(system) R.drawable.widget_panel_system else if(dark) R.drawable.widget_panel_dark else R.drawable.widget_panel_light
    val accentPanel=if(system) R.drawable.widget_accent_system else if(dark) R.drawable.widget_accent_dark else R.drawable.widget_accent_light
    val chip=if(system) R.drawable.widget_chip_system else if(dark) R.drawable.widget_chip_dark else R.drawable.widget_chip_light
    val primary=color(0xFF1675DE,0xFF83C6F7)
    val completed=color(0xFF1878ED,0xFF318DF4)
    val foreground=color(0xFF12304F,0xFFF0F5FC)
    val secondary=color(0xFF536981,0xFFB5C8DD)
    val line=color(0xFFD9E4F1,0xFF36516B)
    val incomplete=color(0xFF8595A8,0xFF9BB1C8)
    fun text(size:Float=12f,bold:Boolean=false,color:ColorProvider=foreground,align:TextAlign?=null):TextStyle {
        // Convert the size fitted to the fixed widget back to sp, including nonlinear scaling.
        val metrics=context.resources.displayMetrics
        val pixels=size*metrics.density
        val font=if(Build.VERSION.SDK_INT>=34) TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP,pixels,metrics)
            else @Suppress("DEPRECATION") (pixels/metrics.scaledDensity)
        return TextStyle(color=color,fontSize=font.sp,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,textAlign=align)
    }
}

@Composable private fun Stage(done:Boolean,label:String,colors:WidgetColors,size:Float) {
    if(done) Box(GlanceModifier.size(size.dp).background(colors.completed).cornerRadius((size/2).dp),contentAlignment=Alignment.Center) {
        Image(ImageProvider(R.drawable.ic_widget_check),"$label 완료",GlanceModifier.size((size*.57f).dp),colorFilter=ColorFilter.tint(ColorProvider(Color.White)))
    } else Image(ImageProvider(R.drawable.ic_widget_ring),"$label 미완료",GlanceModifier.size(size.dp),colorFilter=ColorFilter.tint(colors.incomplete))
}

private fun DailyProgress.stages()=listOf("활력 상태" to vitality,"사용 구성" to usage,"투석 기록" to treatment)

@Composable private fun RecordDay(day:DailyProgress,label:String,today:Boolean,colors:WidgetColors,plan:RecordWidgetLayout,modifier:GlanceModifier) {
    val context=LocalContext.current
    if(plan.mode==RecordWidgetMode.STRIP) {
        Row(modifier.background(ImageProvider(if(today) colors.accentPanel else colors.panel)).padding(2.dp)
            .clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget("record",day.date)))),verticalAlignment=Alignment.CenterVertically) {
            Column(GlanceModifier.width(plan.dateWidth.dp)) {
                Text(label,style=colors.text(plan.titleSize,true,if(today) colors.primary else colors.foreground),maxLines=1)
                Text(LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("M.d")),style=colors.text(plan.titleSize-1,color=colors.secondary),maxLines=1)
            }
            day.stages().forEachIndexed {index,(title,done) ->
                Column(GlanceModifier.defaultWeight(),horizontalAlignment=Alignment.CenterHorizontally) {
                    Stage(done,"$label $title",colors,plan.stageSize)
                    Spacer(GlanceModifier.height(3.dp))
                    Text(if(plan.cardWidth>=220) title else listOf("활력","구성","투석")[index],
                        style=colors.text(plan.labelSize,align=TextAlign.Center),maxLines=1)
                }
            }
        }
        return
    }
    Column(modifier.background(ImageProvider(if(today) colors.accentPanel else colors.panel)).padding(plan.inset.dp)
        .clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget("record",day.date))))) {
        Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,style=colors.text(plan.titleSize,true,if(today) colors.primary else colors.foreground))
            Spacer(GlanceModifier.width(7.dp))
            Text(LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("M.d")),style=colors.text(plan.titleSize-2,color=colors.secondary))
        }
        if(plan.mode==RecordWidgetMode.WIDE_TALL) {
            day.stages().forEachIndexed {index,(title,done) ->
                if(index>0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.line)) {}
                Row(GlanceModifier.fillMaxWidth().defaultWeight(),verticalAlignment=Alignment.CenterVertically) {
                    Stage(done,"$label $title",colors,plan.stageSize)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(title,GlanceModifier.defaultWeight(),style=colors.text(plan.labelSize),maxLines=1)
                }
            }
        } else Row(GlanceModifier.fillMaxWidth().defaultWeight(),verticalAlignment=Alignment.CenterVertically) {
            day.stages().forEachIndexed {index,(title,done) ->
                if(index>0) Box(GlanceModifier.width(1.dp).height((plan.stageSize+25).dp).background(colors.line)) {}
                Column(GlanceModifier.defaultWeight(),horizontalAlignment=Alignment.CenterHorizontally) {
                    Stage(done,"$label $title",colors,plan.stageSize)
                    Spacer(GlanceModifier.height(7.dp))
                    Text(if(plan.mode==RecordWidgetMode.STACKED) listOf("활력","구성","투석")[index] else title,
                        style=colors.text(plan.labelSize,align=TextAlign.Center),maxLines=2)
                }
            }
        }
    }
}

@Composable private fun CompactDays(yesterday:DailyProgress,today:DailyProgress,colors:WidgetColors,plan:RecordWidgetLayout,height:Float) {
    Column(GlanceModifier.fillMaxWidth().height(height.dp)) {
        if(!plan.compact) Row(GlanceModifier.fillMaxWidth().height(22.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Spacer(GlanceModifier.width(plan.dateWidth.dp))
            listOf("활력","구성","투석").forEach {Text(it,GlanceModifier.defaultWeight(),style=colors.text(if(plan.cardWidth<110) 7f else 10f,color=colors.secondary,align=TextAlign.Center),maxLines=1)}
        }
        CompactDay(yesterday,"어제",false,colors,plan)
        Spacer(GlanceModifier.height(if(plan.compact) 2.dp else 5.dp))
        CompactDay(today,"오늘",true,colors,plan)
    }
}

@Composable private fun CompactDay(day:DailyProgress,label:String,today:Boolean,colors:WidgetColors,plan:RecordWidgetLayout) {
    val context=LocalContext.current
    val date=LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("M.d"))
    if(plan.compact) {
        Row(GlanceModifier.fillMaxWidth().height(plan.cardHeight.dp).background(ImageProvider(if(today) colors.accentPanel else colors.panel)).padding(horizontal=2.dp)
            .clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget("record",day.date)))),verticalAlignment=Alignment.CenterVertically) {
            Row(GlanceModifier.width(plan.dateWidth.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(label,style=colors.text(7f,true,if(today) colors.primary else colors.foreground),maxLines=1)
                Spacer(GlanceModifier.width(2.dp));Text(date,style=colors.text(6f,color=colors.secondary),maxLines=1)
            }
            day.stages().forEachIndexed {index,(title,done) ->
                Row(GlanceModifier.defaultWeight(),verticalAlignment=Alignment.CenterVertically,horizontalAlignment=Alignment.CenterHorizontally) {
                    Stage(done,"$label $title",colors,plan.stageSize)
                    Spacer(GlanceModifier.width(2.dp))
                    Text(listOf("활력","구성","투석")[index],style=colors.text(plan.labelSize),maxLines=1)
                }
            }
        }
        return
    }
    Row(GlanceModifier.fillMaxWidth().height(plan.cardHeight.dp).background(ImageProvider(if(today) colors.accentPanel else colors.panel)).padding(horizontal=6.dp,vertical=3.dp)
        .clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget("record",day.date)))),verticalAlignment=Alignment.CenterVertically) {
        if(plan.inlineDate) Row(GlanceModifier.width(plan.dateWidth.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,style=colors.text(8f,true),maxLines=1)
            Spacer(GlanceModifier.width(2.dp));Text(date,style=colors.text(7f,color=colors.secondary),maxLines=1)
        } else Column(GlanceModifier.width(plan.dateWidth.dp)) {
            Text(label,style=colors.text(11f,true),maxLines=1)
            Text(date,style=colors.text(9f,color=colors.secondary),maxLines=1)
        }
        day.stages().forEach {(title,done) ->
            Box(GlanceModifier.defaultWeight(),contentAlignment=Alignment.Center) {Stage(done,"$label $title",colors,plan.stageSize)}
        }
    }
}

@Composable private fun AppointmentBody(appointment:Appointment,now:LocalDateTime,colors:WidgetColors,width:Float,height:Float) {
    val context=LocalContext.current
    val density=context.resources.displayMetrics.density
    val locales=context.resources.configuration.locales
    val dayLabel=appointment.dayLabel(now)
    val plan=remember(appointment,width,height,density,locales,dayLabel) {
        fitWidgetAppointment(appointment,width,height,WidgetTextMeasurer(density,locales),dayLabel)
    }
    val scale=plan.scale
    Column(GlanceModifier.fillMaxWidth().height(height.dp).clickable(actionStartActivity(WidgetNavigation.intent(context,WidgetTarget("appointment",appointment.id))))) {
        Spacer(GlanceModifier.height(plan.topInset.dp))
        if(plan.stackedDate) Column(GlanceModifier.fillMaxWidth().height(plan.dateHeight.dp).background(ImageProvider(colors.accentPanel))
            .padding(horizontal=(7*scale).dp),verticalAlignment=Alignment.CenterVertically) {
            Text(plan.dateLabel,style=colors.text(23*scale,true,colors.primary),maxLines=1)
            Text(plan.dateText,style=colors.text(11*scale,color=colors.secondary),maxLines=1)
        } else Row(GlanceModifier.fillMaxWidth().height(plan.dateHeight.dp).background(ImageProvider(colors.accentPanel)).padding(horizontal=(7*scale).dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(plan.dateLabel,style=colors.text(23*scale,true,colors.primary),maxLines=1)
            Spacer(GlanceModifier.width((8*scale).dp))
            Text(plan.dateText,GlanceModifier.defaultWeight(),style=colors.text(11*scale,color=colors.secondary),maxLines=1)
        }
        Spacer(GlanceModifier.height(plan.gap.dp))
        if(plan.split) Row(GlanceModifier.fillMaxWidth().height(maxOf(plan.departmentHeight,plan.careHeight).dp)) {
            DepartmentPanel(plan,colors,GlanceModifier.width(plan.departmentWidth.dp).fillMaxHeight())
            Spacer(GlanceModifier.width(plan.gap.dp))
            CarePanel(plan,colors,GlanceModifier.width(plan.careWidth.dp).fillMaxHeight())
        } else {
            DepartmentPanel(plan,colors,GlanceModifier.fillMaxWidth().height(plan.departmentHeight.dp))
            if(plan.careRows.isNotEmpty()) {
                Spacer(GlanceModifier.height(plan.gap.dp))
                CarePanel(plan,colors,GlanceModifier.fillMaxWidth().height(plan.careHeight.dp))
            }
        }
    }
}

@Composable private fun DepartmentPanel(plan:WidgetAppointmentLayout,colors:WidgetColors,modifier:GlanceModifier) {
    Box(modifier.background(ImageProvider(colors.panel)).padding((5*plan.scale).dp),contentAlignment=Alignment.CenterStart) {
        Text(plan.departments,style=colors.text(plan.font),maxLines=Int.MAX_VALUE)
    }
}

@Composable private fun CarePanel(plan:WidgetAppointmentLayout,colors:WidgetColors,modifier:GlanceModifier) {
    Column(modifier.background(ImageProvider(colors.accentPanel)).padding((4*plan.scale).dp),verticalAlignment=Alignment.CenterVertically) {
        Text("처치",GlanceModifier.height(plan.headingHeight.dp),style=colors.text(10*plan.scale,true,colors.primary),maxLines=1)
        Spacer(GlanceModifier.height((3*plan.scale).dp))
        CareRows(plan.careRows,colors,plan.font,plan.scale,plan.columns,plan.rowPadding)
    }
}

@Composable private fun CareRows(rows:List<WidgetCareRow>,colors:WidgetColors,font:Float,scale:Float,columns:Int,rowPadding:Float) {
    // At most seven direct children per group; preserve gaps at group boundaries too.
    if(rows.size>4) Column {rows.chunked((rows.size+3)/4).forEachIndexed {index,group ->
        if(index>0) Spacer(GlanceModifier.height((3*scale).dp))
        CareRows(group,colors,font,scale,columns,rowPadding)
    }} else Column {
        rows.forEachIndexed {index,row ->
            Row(GlanceModifier.fillMaxWidth().height(row.height.dp),verticalAlignment=Alignment.CenterVertically) {
                row.items.forEachIndexed {itemIndex,item ->
                    if(itemIndex>0) Spacer(GlanceModifier.width((4*scale).dp))
                    Row(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(colors.chip)).padding(horizontal=(4*scale).dp,vertical=rowPadding.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Image(ImageProvider(careDrawable(item.iconKey)),null,GlanceModifier.size((12*scale).dp),colorFilter=ColorFilter.tint(colors.primary))
                        Spacer(GlanceModifier.width((3*scale).dp))
                        Text(item.displayLabel(),GlanceModifier.defaultWeight(),style=colors.text(font),maxLines=Int.MAX_VALUE)
                    }
                }
                if(row.items.size<columns) {Spacer(GlanceModifier.width((4*scale).dp));Spacer(GlanceModifier.defaultWeight())}
            }
            if(index<rows.lastIndex) Spacer(GlanceModifier.height((3*scale).dp))
        }
    }
}

private fun careDrawable(key:String):Int=when(key) {
    "blood" -> R.drawable.ic_widget_blood
    "lab" -> R.drawable.ic_widget_lab
    "healing" -> R.drawable.ic_widget_healing
    "medicine" -> R.drawable.ic_widget_medicine
    "injection" -> R.drawable.ic_widget_injection
    "dialysis" -> R.drawable.ic_widget_hospital
    "heart" -> R.drawable.ic_widget_heart
    "consult" -> R.drawable.ic_widget_shield
    "rehab" -> R.drawable.ic_widget_person
    else -> R.drawable.ic_widget_medical
}

class DailyRecordWidget:PerilogWidget(true)
class CompactRecordWidget:PerilogWidget(true)
class AppointmentWidget:PerilogWidget(false)
class DailyRecordWidgetReceiver:PerilogWidgetReceiver() {
    override val glanceAppWidget:GlanceAppWidget=DailyRecordWidget()
    override fun onEnabled(context:Context) {super.onEnabled(context);WidgetUpdates.request(context)}
    override fun onDisabled(context:Context) {super.onDisabled(context);WidgetUpdates.request(context)}
}
class CompactRecordWidgetReceiver:PerilogWidgetReceiver() {
    override val glanceAppWidget:GlanceAppWidget=CompactRecordWidget()
    override fun onEnabled(context:Context) {super.onEnabled(context);WidgetUpdates.request(context)}
    override fun onDisabled(context:Context) {super.onDisabled(context);WidgetUpdates.request(context)}
}
class AppointmentWidgetReceiver:PerilogWidgetReceiver() {
    override val glanceAppWidget:GlanceAppWidget=AppointmentWidget()
    override fun onEnabled(context:Context) {super.onEnabled(context);WidgetUpdates.request(context)}
    override fun onDisabled(context:Context) {super.onDisabled(context);WidgetUpdates.request(context)}
}
class RefreshWidgetAction:ActionCallback {
    override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters) {WidgetUpdates.refresh(context)}
}
