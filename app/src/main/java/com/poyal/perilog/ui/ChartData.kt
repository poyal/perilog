package com.poyal.perilog.ui

import com.poyal.perilog.data.Treatment
import com.poyal.perilog.domain.totalUf
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

internal enum class ChartMetric(val label:String,val unit:String) {
    UF("총 제수량","mL"), PRESSURE("혈압","mmHg"), WEIGHT("체중","kg");
    fun values(t:Treatment):List<Double?> = when(this) {
        UF->listOf(t.totalUf()?.toDouble())
        PRESSURE->listOf(t.systolic?.toDouble(),t.diastolic?.toDouble())
        WEIGHT->listOf(t.weightGrams?.div(1000.0))
    }
}
internal fun chartNumber(n:Double)=if(n%1.0==0.0)n.toLong().toString()else String.format(java.util.Locale.US,"%.1f",n)
internal data class ChartDomain(val lower:Double,val upper:Double)
internal fun chartDomain(numbers:List<Double>,bars:Boolean):ChartDomain {
    if(numbers.isEmpty())return ChartDomain(0.0,1.0)
    val low=numbers.min();val high=numbers.max()
    val padding=if(high>low)(high-low)*.12 else max(abs(high)*.002,.1)
    return if(bars)ChartDomain(if(low<0)low-padding else 0.0,if(high>0)high+padding else if(low<0)0.0 else 1.0)
        else ChartDomain(low-padding,high+padding)
}
/** Duplicate records share a day's slot without aggregation. */
internal fun chartPositions(entries:List<Treatment>,from:LocalDate,to:LocalDate):List<Double> {
    val days=(ChronoUnit.DAYS.between(from,to)+1).coerceAtLeast(1)
    val offsets=entries.groupBy{it.date}.values.flatMap{group->group.mapIndexed{index,t->t.id to ((index+.5)/group.size-.5)*.7}}.toMap()
    return entries.map{(ChronoUnit.DAYS.between(from,LocalDate.parse(it.date))+.5+offsets.getValue(it.id))/days}
}
internal fun chartConnects(previousDate:String?,date:String)=previousDate!=null &&
    ChronoUnit.DAYS.between(LocalDate.parse(previousDate),LocalDate.parse(date)) in 0..1
