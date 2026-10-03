@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.totalUf
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

private data class Metric(val name:String,val unit:String,val values:(Treatment)->List<Double?>)
private val weight=Metric("체중","kg"){listOf(it.weightGrams?.div(1000.0))}
private val pressure=Metric("혈압","mmHg"){listOf(it.systolic?.toDouble(),it.diastolic?.toDouble())}
private val totalUf=Metric("총 제수량","mL"){listOf(it.totalUf()?.toDouble())}
private fun number(n:Double)=if(n%1.0==0.0)n.toLong().toString()else String.format(java.util.Locale.US,"%.1f",n)

@Composable fun StatsScreen(s:Snapshot,vm:JournalViewModel,open:(String)->Unit) {
    var range by vm.statsFilters.range
    var from by vm.statsFilters.from
    var to by vm.statsFilters.to
    var table by vm.statsFilters.table
    val entries=s.treatments.filter{it.kind=="MACHINE" && it.date in from..to}.sortedWith(compareBy<Treatment>{it.date}.thenBy{it.createdAt})
    Page("통계","내 기록의 변화를 확인해요") {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SelectionBox("통계 기간",range,listOf("7D","30D","90D","직접 선택"),{label->
                range=label
                if(label!="직접 선택"){to=today();from=LocalDate.now().minusDays(label.removeSuffix("D").toLong()-1).toString()}
            },Modifier.weight(1f))
            SelectionBox("표시 방식",if(table)"표"else"라인차트",listOf("라인차트","표"),{table=it=="표"},Modifier.weight(1f))
        }
        if(range=="직접 선택"){DateControl(from,{from=it},"시작");DateControl(to,{to=it},"종료")}
        Paper {
            Hint("조회 기간")
            if(from>to)Text("종료일을 시작일 이후로 선택해 주세요.",color=MaterialTheme.colorScheme.error)
            else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("$from ~",to).forEach { date ->
                    Text(date,style=MaterialTheme.typography.titleLarge.copy(fontSize=20.sp),color=MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Paper {
            Section("투석 기록")
            MetricChart(totalUf,entries,from,to,table,open)
        }
        Paper {
            Section("활력 상태")
            MetricChart(pressure,entries,from,to,table,open)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MetricChart(weight,entries,from,to,table,open)
        }
    }
}

@Composable private fun MetricChart(metric:Metric,entries:List<Treatment>,from:String,to:String,table:Boolean,open:(String)->Unit) {
    val values=entries.map{metric.values(it)}
    val components=if(metric.name=="혈압")2 else 1
    val averages=(0 until components).map{component->values.mapNotNull{it.getOrNull(component)}.takeIf{it.isNotEmpty()}?.average()}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(metric.name,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
        Text("평균 ${averages.joinToString(" / "){it?.let(::number) ?: "—"}} ${metric.unit}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(table) {
            if(entries.isEmpty())Hint("기록이 없어요.")
            entries.forEachIndexed{index,t->
                Column(Modifier.fillMaxWidth().clickable{open(t.id)},verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(t.date,style=MaterialTheme.typography.bodySmall)
                    Text(values[index].joinToString(" / "){it?.let(::number) ?: "—"}+" ${metric.unit}",style=MaterialTheme.typography.bodySmall)
                }
            }
        } else LineChart(entries,values,from,to,open)
        if(components==2) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("수축기",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                Text("이완기",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
            }
        } else Text(metric.unit,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun LineChart(entries:List<Treatment>,values:List<List<Double?>>,from:String,to:String,open:(String)->Unit) {
    val numbers=values.flatten().filterNotNull()
    if(numbers.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(150.dp),contentAlignment=Alignment.Center){Hint("입력된 값이 없어요.")}
        return
    }
    val minimum=numbers.min()
    val maximum=numbers.max()
    val padding=if(maximum>minimum)(maximum-minimum)*.1 else max(abs(maximum)*.02,1.0)
    val lower=minimum-padding
    val upper=maximum+padding
    val start=LocalDate.parse(from)
    val end=LocalDate.parse(to)
    val days=ChronoUnit.DAYS.between(start,end).coerceAtLeast(1)
    val positions=entries.map{ChronoUnit.DAYS.between(start,LocalDate.parse(it.date)).toDouble()/days}
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.secondary
    val grid=MaterialTheme.colorScheme.outlineVariant
    val surface=MaterialTheme.colorScheme.surface
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.width(36.dp).height(128.dp),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.End) {
                listOf(upper,(upper+lower)/2,lower).forEach{Text(number(it),style=MaterialTheme.typography.bodySmall.copy(fontSize=10.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Canvas(Modifier.weight(1f).height(128.dp).pointerInput(entries,values,from,to) {
                detectTapGestures{tap->
                    val inset=4.dp.toPx()
                    val width=(size.width-2*inset).coerceAtLeast(1f)
                    val nearest=positions.indices.minByOrNull{abs((inset+positions[it]*width)-tap.x)}
                    nearest?.let{open(entries[it].id)}
                }
            }) {
                val inset=4.dp.toPx()
                val width=(size.width-2*inset).coerceAtLeast(1f)
                val height=(size.height-2*inset).coerceAtLeast(1f)
                repeat(3){index->
                    val y=inset+height*index/2f
                    drawLine(grid,Offset(inset,y),Offset(size.width-inset,y),1.dp.toPx())
                }
                repeat(values.maxOfOrNull{it.size} ?: 1){component->
                    val color=if(component==0)primary else secondary
                    val path=Path()
                    var connected=false
                    val points=mutableListOf<Offset>()
                    values.forEachIndexed{index,row->
                        val value=row.getOrNull(component)
                        if(value==null)connected=false else {
                            val point=Offset((inset+positions[index]*width).toFloat(),(inset+(upper-value)/(upper-lower)*height).toFloat())
                            if(connected)path.lineTo(point.x,point.y)else path.moveTo(point.x,point.y)
                            connected=true;points+=point
                        }
                    }
                    drawPath(path,color,style=Stroke(width=2.dp.toPx(),cap=StrokeCap.Round))
                    points.forEach{point->drawCircle(color,3.dp.toPx(),point);drawCircle(surface,1.5.dp.toPx(),point)}
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start=40.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf(start,end).forEach{Text(it.toString().takeLast(5).replace('-','.'),style=MaterialTheme.typography.bodySmall.copy(fontSize=10.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
