package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.totalUf
import java.time.LocalDate
import kotlin.math.abs

private data class Metric(val name:String,val unit:String,val values:(Treatment)->List<Double?>)
private val metrics=listOf(
    Metric("몸무게","kg"){listOf(if(it.kind=="MACHINE")it.weightGrams?.div(1000.0)else null)},
    Metric("혈압","mmHg"){if(it.kind=="MACHINE")listOf(it.systolic?.toDouble(),it.diastolic?.toDouble())else listOf(null,null)},
    Metric("총 제수량","mL"){listOf(if(it.kind=="MACHINE")it.totalUf()?.toDouble()else null)},
    Metric("기계 제수량","mL"){listOf(if(it.kind=="MACHINE")it.machineUf?.toDouble()else null)},
    Metric("초기배액량","mL"){listOf(if(it.kind=="MACHINE")it.initialDrain?.toDouble()else null)},
    Metric("평균저류시간","분"){listOf(if(it.kind=="MACHINE")it.dwellMinutes?.toDouble()else null)},
    Metric("손투석 배액량","mL"){listOf(if(it.kind=="MANUAL" && it.drainUnit=="mL")it.manualDrain?.toDouble()else null)},
    Metric("손투석 배액무게","g"){listOf(if(it.kind=="MANUAL" && it.drainUnit!="mL")it.manualDrain?.toDouble()else null)},
    Metric("손투석 제수량","mL"){listOf(if(it.kind=="MANUAL")it.totalUf()?.toDouble()else null)})
private fun number(n:Double)=if(n%1.0==0.0)n.toLong().toString()else String.format(java.util.Locale.US,"%.1f",n)
@Composable fun StatsScreen(s:Snapshot,vm:JournalViewModel,open:(String)->Unit) {
    var selected by vm.statsFilters.selected
    var range by vm.statsFilters.range
    var from by vm.statsFilters.from
    var to by vm.statsFilters.to
    var table by vm.statsFilters.table
    val metric=metrics[selected]
    val entries=s.treatments.filter{it.date in from..to && if(selected<6)it.kind=="MACHINE"else it.kind=="MANUAL"}.sortedWith(compareBy<Treatment>{it.date}.thenBy{it.createdAt})
    val values=entries.map{metric.values(it)}
    val max=values.flatten().filterNotNull().maxOfOrNull{abs(it)}?.coerceAtLeast(1.0) ?: 1.0
    val primary=MaterialTheme.colorScheme.primary;val secondary=MaterialTheme.colorScheme.secondary
    Page("통계","내 기록의 변화를 확인해요") {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("7D","30D","90D","직접 선택").forEach{label->FilterChip(range==label,{range=label;if(label!="직접 선택"){to=today();from=LocalDate.now().minusDays(label.removeSuffix("D").toLong()-1).toString()}},{Text(label)})}
        }
        if(range=="직접 선택"){DateControl(from,{from=it},"시작");DateControl(to,{to=it},"종료")}
        Hint("$from ~ $to")
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { metrics.forEachIndexed{i,m->FilterChip(selected==i,{selected=i},{Text(m.name)})} }
        Paper {
            Section("${metric.name} · ${metric.unit}")
            repeat(if(selected==1)2 else 1){component->
                val ns=values.mapNotNull{it.getOrNull(component)}
                if(selected==1) Hint(if(component==0)"수축기 · 파랑"else"이완기 · 코랄")
                if(ns.isEmpty()) Text("아직 입력된 값이 없어요.") else {
                    Text("${ns.size}건 · 평균 ${number(ns.average())} ${metric.unit}",style=MaterialTheme.typography.titleLarge)
                    Hint("최소 ${number(ns.min())} · 최대 ${number(ns.max())}")
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!table,{table=false},{Text("막대그래프")});FilterChip(table,{table=true},{Text("표")})}
        Paper {
            if(entries.isEmpty())Text("선택한 기간에 기록이 없어요.")
            entries.forEachIndexed{i,t->
                Column(Modifier.fillMaxWidth().clickable{open(t.id)}) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(t.date);Text(values[i].joinToString(" / "){it?.let(::number) ?: "—"}+" ${metric.unit}")}
                    if(!table) Canvas(Modifier.fillMaxWidth().height(if(selected==1)44.dp else 28.dp)) {
                        val middle=size.width/2f
                        drawLine(Color.Gray,Offset(middle,0f),Offset(middle,size.height),1.dp.toPx())
                        values[i].forEachIndexed{j,v->if(v!=null) {
                            val width=(abs(v)/max*(size.width/2f-4.dp.toPx())).toFloat()
                            drawRect(if(j==0)primary else secondary,Offset(if(v<0)middle-width else middle,j*20.dp.toPx()+3.dp.toPx()),Size(width,14.dp.toPx()))
                        }}
                    }
                    HorizontalDivider(Modifier.padding(vertical=8.dp))
                }
            }
            Hint("— 는 미입력입니다. 음수는 기준선 왼쪽에 표시해요. 기록을 누르면 상세 화면으로 이동합니다.")
        }
    }
}
