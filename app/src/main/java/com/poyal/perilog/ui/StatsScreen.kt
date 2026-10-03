@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poyal.perilog.data.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min

@Composable fun StatsScreen(s:Snapshot,vm:JournalViewModel,open:(String)->Unit) {
    var range by vm.statsFilters.range
    var from by vm.statsFilters.from
    var to by vm.statsFilters.to
    var mode by vm.statsFilters.chartMode
    var choosingPeriod by rememberSaveable{mutableStateOf(false)}
    val entries=remember(s.treatments,from,to){s.treatments.filter{it.kind=="MACHINE" && it.date in from..to}
        .sortedWith(compareBy<Treatment>{it.date}.thenBy{it.createdAt}.thenBy{it.id})}
    Page("통계","내 기록의 변화를 확인해요") {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SelectionBox("통계 기간",range,listOf("7D","30D","90D","기간 지정"),{label->
                if(label=="기간 지정")choosingPeriod=true else {
                    range=label;to=today();from=LocalDate.now().minusDays(label.removeSuffix("D").toLong()-1).toString()
                }
            },Modifier.weight(1f))
            SelectionBox("표시 방식",mode.label,StatsChartMode.entries.map{it.label},{label->mode=StatsChartMode.entries.first{it.label==label}},Modifier.weight(1f))
        }
        DateRangeControl(LocalDate.parse(from),LocalDate.parse(to),{choosingPeriod=true},enabled=range=="기간 지정")
        Paper {Section("투석 기록");MetricChart(ChartMetric.UF,entries,from,to,mode,open)}
        Paper {
            Section("활력 상태")
            MetricChart(ChartMetric.PRESSURE,entries,from,to,mode,open)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MetricChart(ChartMetric.WEIGHT,entries,from,to,mode,open)
        }
    }
    if(choosingPeriod)DateRangeDialog(LocalDate.parse(from),LocalDate.parse(to),{choosingPeriod=false}){start,end->
        from=start.toString();to=end.toString();range="기간 지정";choosingPeriod=false
    }
}

@Composable private fun MetricChart(metric:ChartMetric,entries:List<Treatment>,from:String,to:String,mode:StatsChartMode,open:(String)->Unit) {
    val values=remember(metric,entries){entries.map{metric.values(it)}}
    val components=if(metric==ChartMetric.PRESSURE)2 else 1
    val averages=(0 until components).map{component->values.mapNotNull{it.getOrNull(component)}.takeIf{it.isNotEmpty()}?.average()}
    var selectedDate by rememberSaveable(metric){mutableStateOf<String?>(null)}
    var choosing by rememberSaveable(metric){mutableStateOf(false)}
    LaunchedEffect(entries){if(entries.none{it.date==selectedDate})selectedDate=null}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(metric.label,style=MaterialTheme.typography.titleMedium)
        Text("평균 ${averages.joinToString(" / "){it?.let(::chartNumber) ?: "—"}} ${metric.unit}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        MetricPlot(metric,entries,values,LocalDate.parse(from),LocalDate.parse(to),mode,selectedDate){selectedDate=it}
        if(components==2)FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("● 수축기",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            Text("◆ 이완기",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
        }
        if(entries.isNotEmpty())TextButton(onClick={choosing=true},modifier=Modifier.testTag("chart-dates-${metric.name}")) {
            Text("날짜별 값 확인",style=MaterialTheme.typography.bodySmall)
        }
        entries.filter{it.date==selectedDate}.forEachIndexed{index,t->
            Column(Modifier.fillMaxWidth().testTag("chart-value-${metric.name}-${t.id}")) {
                Text("${t.date.replace('-','.')} · 기록 ${index+1}",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(metric.values(t).joinToString(" / "){it?.let{value->BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()} ?: "—"}+" ${metric.unit}",Modifier.weight(1f))
                    TextButton(onClick={open(t.id)}){Text("기록 열기")}
                }
            }
        }
    }
    if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text("${metric.label} · 날짜 선택")},text={
        LazyColumn {items(entries.map{it.date}.distinct(),key={it}){date->
            TextButton(onClick={selectedDate=date;choosing=false},modifier=Modifier.fillMaxWidth()){Text(date.replace('-','.'))}
        }}
    },confirmButton={TextButton(onClick={choosing=false}){Text("닫기")}})
}

@Composable private fun MetricPlot(metric:ChartMetric,entries:List<Treatment>,values:List<List<Double?>>,from:LocalDate,to:LocalDate,
    mode:StatsChartMode,selectedDate:String?,select:(String)->Unit) {
    val numbers=values.flatten().filterNotNull()
    if(numbers.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(150.dp),contentAlignment=Alignment.Center){Hint("입력된 값이 없어요.")}
        return
    }
    val bars=mode==StatsChartMode.METRIC && metric==ChartMetric.UF
    val domain=chartDomain(numbers,bars)
    val positions=remember(entries,from,to){chartPositions(entries,from,to)}
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.secondary
    val grid=MaterialTheme.colorScheme.outlineVariant
    val foreground=MaterialTheme.colorScheme.onSurface
    val measurer=rememberTextMeasurer()
    val labelStyle=TextStyle(fontSize=10.sp,color=foreground)
    val labels=remember(values,labelStyle){values.map{row->row.map{n->n?.let{measurer.measure(chartNumber(it),labelStyle)}}}}
    val days=(ChronoUnit.DAYS.between(from,to)+1).coerceAtLeast(1)
    val largestDay=entries.groupingBy{it.date}.eachCount().values.maxOrNull() ?: 1
    val kind=when{mode==StatsChartMode.LINE->"라인차트";bars->"막대그래프";metric==ChartMetric.PRESSURE->"범위 도표";else->"점그래프"}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(metric.unit,style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.width(44.dp).height(160.dp).padding(vertical=14.dp),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.End) {
                listOf(domain.upper,(domain.upper+domain.lower)/2,domain.lower).forEach{
                    Text(chartNumber(it),style=MaterialTheme.typography.bodySmall.copy(fontSize=10.sp),maxLines=1)
                }
            }
            Canvas(Modifier.weight(1f).height(160.dp).testTag("chart-${metric.name}").semantics {
                contentDescription="${metric.label} $kind. ${entries.size}개 기록. 날짜별 값 확인 버튼으로 각 값을 확인할 수 있어요."
            }.pointerInput(entries,positions) {
                detectTapGestures{tap->
                    val inset=10.dp.toPx()
                    positions.indices.minByOrNull{abs(inset+positions[it]*(size.width-2*inset)-tap.x)}
                        ?.let{select(entries[it].date)}
                }
            }) {
                val insetX=10.dp.toPx();val insetY=14.dp.toPx()
                val plotWidth=(size.width-2*insetX).coerceAtLeast(1f);val plotHeight=(size.height-2*insetY).coerceAtLeast(1f)
                fun x(index:Int)=(insetX+positions[index]*plotWidth).toFloat()
                fun y(value:Double)=(insetY+(domain.upper-value)/(domain.upper-domain.lower)*plotHeight).toFloat()
                repeat(3){index->val yy=insetY+plotHeight*index/2;drawLine(grid,Offset(insetX,yy),Offset(size.width-insetX,yy))}
                if(bars)drawLine(foreground.copy(alpha=.55f),Offset(insetX,y(0.0)),Offset(size.width-insetX,y(0.0)),1.dp.toPx())
                entries.forEachIndexed{index,t->if(t.date==selectedDate)drawLine(primary.copy(alpha=.12f),Offset(x(index),insetY),Offset(x(index),size.height-insetY),12.dp.toPx())}
                if(mode==StatsChartMode.LINE)repeat(values.maxOfOrNull{it.size} ?: 1){component->
                    val path=Path();var previousDate:String?=null
                    values.forEachIndexed{index,row->
                        val v=row.getOrNull(component)
                        if(v==null)previousDate=null else {
                            if(chartConnects(previousDate,entries[index].date))path.lineTo(x(index),y(v))else path.moveTo(x(index),y(v))
                            previousDate=entries[index].date
                        }
                    }
                    drawPath(path,if(component==0)primary else secondary,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                }
                val barWidth=min(22.dp.toPx(),plotWidth/days/largestDay*.65f).coerceAtLeast(1f)
                values.forEachIndexed{index,row->
                    if(mode==StatsChartMode.METRIC && metric==ChartMetric.PRESSURE && row.all{it!=null})
                        drawLine(foreground.copy(alpha=.4f),Offset(x(index),y(row[0]!!)),Offset(x(index),y(row[1]!!)),2.dp.toPx())
                    row.forEachIndexed{component,v->if(v!=null) {
                        val color=if(component==0)primary else secondary
                        if(bars) {
                            if(v==0.0)drawLine(color,Offset(x(index)-barWidth/2,y(0.0)),Offset(x(index)+barWidth/2,y(0.0)),3.dp.toPx())
                            else drawRect(color,Offset(x(index)-barWidth/2,min(y(v),y(0.0))),Size(barWidth,abs(y(v)-y(0.0))))
                        } else if(component==1) {
                            val radius=4.dp.toPx();val path=Path().apply{moveTo(x(index),y(v)-radius);lineTo(x(index)+radius,y(v));lineTo(x(index),y(v)+radius);lineTo(x(index)-radius,y(v));close()}
                            drawPath(path,color)
                        } else drawCircle(color,3.5.dp.toPx(),Offset(x(index),y(v)))
                        if(entries.size<=7 && days<=7 && largestDay==1)labels[index][component]?.let{label->
                            val xx=(x(index)-label.size.width/2f).coerceIn(0f,(size.width-label.size.width).coerceAtLeast(0f))
                            val yy=if(component==1)y(v)+5.dp.toPx()else y(v)-label.size.height-5.dp.toPx()
                            drawText(label,topLeft=Offset(xx,yy.coerceIn(0f,(size.height-label.size.height).coerceAtLeast(0f))))
                        }
                    }}
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start=48.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            val dates=if(from==to)listOf(from)else listOf(from,to)
            dates.forEach{Text(it.toString().takeLast(5).replace('-','.'),style=MaterialTheme.typography.labelSmall)}
        }
    }
}
