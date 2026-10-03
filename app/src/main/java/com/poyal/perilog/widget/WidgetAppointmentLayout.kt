package com.poyal.perilog.widget

import android.graphics.Typeface
import android.os.LocaleList
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.poyal.perilog.data.Appointment
import com.poyal.perilog.data.CareTask
import com.poyal.perilog.domain.departmentTime
import com.poyal.perilog.domain.selectedCareItems
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

data class WidgetTextSize(val height:Float,val lines:Int)
class WidgetTextMeasurer(private val density:Float=1f,private val locales:LocaleList=LocaleList.getDefault()) {
    private fun paint(font:Float,bold:Boolean)=TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize=font*this@WidgetTextMeasurer.density;typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        textLocales=locales
    }
    fun width(text:String,font:Float,bold:Boolean=false)=paint(font,bold).measureText(text)/density
    fun measure(text:String,width:Float,font:Float,bold:Boolean=false):WidgetTextSize {
        // Reserve two physical pixels for independent RemoteViews width/padding rounding.
        val layout=StaticLayout.Builder.obtain(text,0,text.length,paint(font,bold),floor(width*density-2).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setUseLineSpacingFromFallbacks(true).build()
        return WidgetTextSize((layout.height+2)/density,layout.lineCount)
    }
}

data class WidgetCareRow(val items:List<CareTask>,val height:Float)
data class WidgetAppointmentLayout(val scale:Float,val font:Float,val dateHeight:Float,val gap:Float,
    val departments:String,val departmentHeight:Float,val careRows:List<WidgetCareRow>,val totalHeight:Float,
    val columns:Int,val split:Boolean,val departmentWidth:Float,val careWidth:Float,val careHeight:Float,
    val dateLabel:String,val dateText:String,val stackedDate:Boolean,val headingHeight:Float,
    val wrappedLines:Int,val rowPadding:Float,val topInset:Float=0f)

/** Measure every candidate using the same physical font and widths used by the renderer. */
fun fitWidgetAppointment(appointment:Appointment,width:Float,height:Float,
    measure:WidgetTextMeasurer=WidgetTextMeasurer(),dayLabel:String="D-day"):WidgetAppointmentLayout {
    val departments=if(appointment.departments.isEmpty()) appointment.time else appointment.departments
        .sortedBy {appointment.departmentTime(it)}.joinToString("\n") {"${appointment.departmentTime(it)}  ${it.name}"}
    val items=appointment.selectedCareItems()
    val dateText=LocalDate.parse(appointment.date).format(DateTimeFormatter.ofPattern("M.d (E)",Locale.KOREAN))
    fun at(font:Float,columns:Int,split:Boolean,compact:Boolean=false):WidgetAppointmentLayout {
        val scale=font/12f
        val gap=(if(compact) 3f else 4f)*scale
        val rowPadding=(if(compact) 3f else 4f)*scale
        val departmentWidth=if(split) (width-gap)*.4f else width
        val careWidth=if(split) width-gap-departmentWidth else width
        val d=measure.measure(departments,departmentWidth-10*scale,font)
        val departmentHeight=d.height+10*scale
        val chipWidth=(careWidth-8*scale-(columns-1)*4*scale)/columns
        var wrapped=(d.lines-maxOf(1,appointment.departments.size)).coerceAtLeast(0)
        val rows=items.chunked(columns).map {group ->
            val sizes=group.map {measure.measure(it.name,chipWidth-23*scale,font)}
            wrapped+=sizes.sumOf {(it.lines-1).coerceAtLeast(0)}
            WidgetCareRow(group,maxOf(12*scale,sizes.maxOf {it.height})+2*rowPadding)
        }
        val heading=measure.measure("처치",careWidth-8*scale,10*scale,true).height
        val careHeight=if(rows.isEmpty()) 0f else 8*scale+heading+3*scale+
            rows.sumOf {it.height.toDouble()}.toFloat()+(rows.size-1)*3*scale
        val stackedDate=measure.width(dayLabel,23*scale,true)+measure.width(dateText,11*scale)+8*scale>width-14*scale-2
        val daySize=measure.measure(dayLabel,width-14*scale,23*scale,true)
        val dateSize=measure.measure(dateText,width-14*scale,11*scale)
        val dateHeight=if(stackedDate) daySize.height+dateSize.height+6*scale else maxOf(daySize.height,dateSize.height)+6*scale
        val total=dateHeight+gap+if(split) maxOf(departmentHeight,careHeight)
            else departmentHeight+if(rows.isEmpty()) 0f else gap+careHeight
        return WidgetAppointmentLayout(scale,font,dateHeight,gap,departments,departmentHeight,rows,total,
            columns,split,departmentWidth,careWidth,careHeight,dayLabel,dateText,stackedDate,heading,wrapped,rowPadding)
    }
    val splits=if(items.isNotEmpty() && width>=240 && height/width<.65f) listOf(false,true) else listOf(false)
    val candidates=mutableListOf<WidgetAppointmentLayout>()
    for(split in splits) for(columns in if(items.isEmpty()) listOf(1) else listOf(1,2)) {
        var font=if(height>=170 && width>=150) 13f else 12f
        var plan=at(font,columns,split)
        if(plan.totalHeight>height-2) plan=at(font,columns,split,compact=true)
        while(plan.totalHeight>height-2 && font>.5f) {
            font=(font-.25f).coerceAtLeast(.5f);plan=at(font,columns,split)
            if(plan.totalHeight>height-2) plan=at(font,columns,split,compact=true)
        }
        if(plan.totalHeight<=height-2) candidates+=plan
    }
    // Never return an overflowing plan as though it fitted.
    check(candidates.isNotEmpty()) {"Widget content exceeds the drawable space"}
    val preferredColumns=if(height>width*1.2f) 1 else 2
    // In tall narrow hosts, a tiny font reduction can avoid several broken care names.
    // Keep candidates within 0.75dp of the largest fitting font, then prefer fewer wraps.
    val largestFont=candidates.maxOf {it.font}
    val readableCandidates=candidates.filter {it.font>=largestFont-if(preferredColumns==1) .75f else 0f}
    var best=readableCandidates.sortedWith(compareBy<WidgetAppointmentLayout> {it.wrappedLines}
        .thenByDescending {it.font}.thenByDescending {it.totalHeight}
        .thenBy {if(it.columns==preferredColumns) 0 else 1}).first()
    if(best.careRows.isNotEmpty() && height>=170 && !best.split) {
        var remaining=(height-best.totalHeight-2).coerceAtLeast(0f)
        val extraGap=minOf((8-best.gap).coerceAtLeast(0f),remaining/2);remaining-=extraGap*2
        val rowExtra=minOf(12f,remaining/best.careRows.size);remaining-=rowExtra*best.careRows.size
        val departmentExtra=minOf(8f,remaining);remaining-=departmentExtra
        val dateExtra=minOf(6f,remaining)
        best=best.copy(gap=best.gap+extraGap,dateHeight=best.dateHeight+dateExtra,
            departmentHeight=best.departmentHeight+departmentExtra,
            careRows=best.careRows.map {it.copy(height=it.height+rowExtra)},
            careHeight=best.careHeight+rowExtra*best.careRows.size,
            totalHeight=best.totalHeight+extraGap*2+rowExtra*best.careRows.size+departmentExtra+dateExtra)
    }
    return best.copy(topInset=((height-best.totalHeight)/2).coerceAtLeast(0f))
}
