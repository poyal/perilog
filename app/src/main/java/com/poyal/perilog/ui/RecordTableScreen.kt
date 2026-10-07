@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal val recordColumns=listOf("날짜 · 유형","몸무게\nkg","혈압\nmmHg","초기배액\nmL","기계 제수량\nmL","총 제수량\nmL","추가 배액\n원래 단위","평균저류\n시:분","상태")
internal val recordColumnWidths=listOf(120f,88f,108f,104f,116f,112f,124f,100f,88f)
internal fun recordValues(t:Treatment)=listOf(
    "${t.date}\n${if(t.kind=="MACHINE")"기계투석"else"추가투석"}",
    t.weightGrams?.let{BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString()} ?: "—",
    "${t.systolic ?: "—"}/${t.diastolic ?: "—"}",t.initialDrain?.toString() ?: "—",t.machineUf?.toString() ?: "—",
    t.totalUf()?.toString() ?: "—",
    t.manualDrain?.let{if(t.drainUnit=="kg")"${BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString()} kg"else"$it ${t.drainUnit}"} ?: "—",
    t.dwellMinutes?.let{"${it/60}:${(it%60).toString().padStart(2,'0')}"} ?: "—",if(t.complete())"완료"else"미완료")
internal fun filterRecords(s:Snapshot,f:RecordFilters)=s.visibleRecords().filter{t->
    (!f.period.value || t.date in f.from.value..f.to.value) &&
        (f.type.value=="전체" || t.kind==if(f.type.value=="기계투석")"MACHINE"else"MANUAL") &&
        (f.status.value=="전체 상태" || t.complete()==(f.status.value=="완료"))
}.sortedWith(compareByDescending<Treatment>{it.date}.thenByDescending{it.createdAt}.thenBy{it.id})

@Composable fun RecordTableScreen(s:Snapshot,vm:JournalViewModel,back:()->Unit,open:(Treatment)->Unit) {
    val filters=vm.recordFilters
    val entries=filterRecords(s,filters)
    var filterOpen by rememberSaveable{mutableStateOf(false)}
    var periodOpen by rememberSaveable{mutableStateOf(false)}
    var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedColumn by rememberSaveable{mutableIntStateOf(0)}
    var scale by rememberSaveable{mutableFloatStateOf(1f)}
    var fit by rememberSaveable{mutableStateOf(true)}
    val horizontal=rememberScrollState()
    val rows=rememberLazyListState()
    LaunchedEffect(entries.map{it.id}){if(entries.none{it.id==selectedId})selectedId=null}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"뒤로")}
            Text("기록 표",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            HelpIconButton()
        }
        Text((if(filters.period.value)"${filters.from.value.replace('-','.')} ~ ${filters.to.value.replace('-','.')}"else"전체 기간")+" · ${entries.size}건",
            Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density=LocalDensity.current
            val fontFactor=density.fontScale.coerceAtLeast(1f)
            val baseWidth=recordColumnWidths.sum()*fontFactor
            val fitScale=(maxWidth.value/baseWidth).coerceAtMost(1f)
            val zoom=if(fit)fitScale else scale.coerceIn(min(.25f,fitScale),2f)
            val widths=recordColumnWidths.map{(it*fontFactor*zoom).dp}
            val rowHeight=(64f*fontFactor*zoom).dp
            val rowPx=with(density){rowHeight.toPx()}
            var bodyHeight by remember{mutableIntStateOf(0)}
            // Opening the detail strip can shrink the viewport below the selected last row.
            // Keep nearby selections visible without jumping back to an old, distant selection.
            LaunchedEffect(selectedId,bodyHeight) {
                withFrameNanos{}
                val index=entries.indexOfFirst{it.id==selectedId}
                val visible=rows.layoutInfo.visibleItemsInfo
                if(index>=0 && visible.isNotEmpty() && index in (visible.first().index-1)..(visible.last().index+1)) {
                    val top=(index-rows.firstVisibleItemIndex)*rowPx-rows.firstVisibleItemScrollOffset
                    val bottom=top+rowPx
                    when {
                        top<0 -> rows.scrollBy(top)
                        bottom>bodyHeight -> rows.scrollBy(bottom-bodyHeight)
                    }
                }
            }
            var pendingX by remember{mutableStateOf<Int?>(null)}
            LaunchedEffect(pendingX,zoom) {
                pendingX?.let{target->withFrameNanos{};horizontal.scrollTo(target.coerceIn(0,horizontal.maxValue));pendingX=null}
            }
            fun changeZoom(target:Float,anchor:Offset=Offset(with(density){maxWidth.toPx()}/2,rowPx+bodyHeight/2f),pan:Offset=Offset.Zero) {
                val next=target.coerceIn(min(.25f,fitScale),2f)
                val ratio=next/zoom
                val anchorY=(anchor.y-rowPx).coerceAtLeast(0f)
                val scrollY=((rows.firstVisibleItemIndex*rowPx+rows.firstVisibleItemScrollOffset+anchorY)*ratio-anchorY-pan.y).coerceAtLeast(0f)
                val nextRowPx=rowPx*ratio
                val row=floor(scrollY/nextRowPx).toInt().coerceIn(0,(entries.size-1).coerceAtLeast(0))
                rows.requestScrollToItem(row,(scrollY-row*nextRowPx).roundToInt().coerceAtLeast(0))
                pendingX=((horizontal.value+anchor.x)*ratio-anchor.x-pan.x).roundToInt().coerceAtLeast(0)
                scale=next;fit=false
            }
            val onTransform by rememberUpdatedState<(Float,Offset,Offset)->Unit>({factor,anchor,pan->changeZoom(zoom*factor,anchor,pan)})
            val viewConfiguration=LocalViewConfiguration.current
            val exactCells=remember(viewConfiguration){object:ViewConfiguration by viewConfiguration {
                override val minimumTouchTargetSize=DpSize.Zero
            }}
            Column(Modifier.fillMaxSize()) {
                ButtonRow(Modifier.fillMaxWidth().testTag("record-table-controls").padding(8.dp),centered=true) {
                    SmallButton(onClick={filterOpen=true}){Icon(Icons.Outlined.FilterList,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("필터")}
                    SmallButton(onClick={changeZoom(zoom-.25f)},enabled=zoom>min(.25f,fitScale)+.001f,modifier=Modifier.semantics{contentDescription="표 축소"}){Text("−")}
                    Text("${(zoom*100).roundToInt()}%",Modifier.testTag("table-zoom").semantics{stateDescription="${(zoom*100).roundToInt()}%"})
                    SmallButton(onClick={changeZoom(zoom+.25f)},enabled=zoom<2f,modifier=Modifier.semantics{contentDescription="표 확대"}){Text("+")}
                    SmallButton(onClick={changeZoom(fitScale,Offset.Zero);fit=true;pendingX=0}){Text("전체 열")}
                    SmallButton(onClick={changeZoom(1f)}){Text("100%로")}
                }
                HorizontalDivider()
                if(entries.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("선택한 조건에 해당하는 기록이 없어요.")}
                else CompositionLocalProvider(LocalViewConfiguration provides exactCells) {
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("record-table-viewport").pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed=false)
                            var transforming=false
                            do {
                                val event=awaitPointerEvent(PointerEventPass.Initial)
                                if(event.changes.count{it.pressed}>=2) {
                                    transforming=true
                                    onTransform(event.calculateZoom(),event.calculateCentroid(useCurrent=false),event.calculatePan())
                                    event.changes.forEach{it.consume()}
                                } else if(transforming)event.changes.forEach{it.consume()}
                            } while(event.changes.any{it.pressed})
                        }
                    }) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(horizontal,enabled=false).testTag("record-table-header")) {
                                recordColumns.forEachIndexed{column,label->
                                    SheetCell(label,widths[column],rowHeight,zoom,true,false,
                                        if(column==0)Modifier.offset{IntOffset(horizontal.value,0)}.zIndex(2f)else Modifier)
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged{bodyHeight=it.height}.horizontalScroll(horizontal).testTag("record-table-horizontal")) {
                                LazyColumn(state=rows,modifier=Modifier.width(widths.sumOf{it.value.toDouble()}.toFloat().dp).fillMaxHeight()
                                    .testTag("record-table-rows").semantics{collectionInfo=CollectionInfo(entries.size,recordColumns.size)}) {
                                    itemsIndexed(entries,key={_,t->t.id}){index,t->
                                        val values=remember(t){recordValues(t)}
                                        Row(Modifier.testTag("record-row-${t.id}")) {
                                            values.forEachIndexed{column,value->
                                                SheetCell(value,widths[column],rowHeight,zoom,false,index%2==1,
                                                    (if(column==0)Modifier.offset{IntOffset(horizontal.value,0)}.zIndex(1f)else Modifier)
                                                        .testTag("record-cell-${t.id}-$column")
                                                        .semantics {collectionItemInfo=CollectionItemInfo(index,1,column,1);contentDescription="${t.date}, ${recordColumns[column].replace('\n',' ')}, $value"}
                                                        .clickable{selectedId=t.id;selectedColumn=column},
                                                    selected=selectedId==t.id && selectedColumn==column)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val thumbColor=MaterialTheme.colorScheme.primary.copy(alpha=.5f)
                        Canvas(Modifier.matchParentSize()) {
                            if(horizontal.maxValue>0) {
                                val fraction=size.width/(size.width+horizontal.maxValue)
                                val length=(size.width*fraction).coerceAtLeast(20.dp.toPx())
                                val left=(size.width-length)*horizontal.value/horizontal.maxValue
                                drawRoundRect(thumbColor,Offset(left,size.height-4.dp.toPx()),Size(length,3.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            }
                            val bodyHeight=(size.height-rowPx).coerceAtLeast(1f)
                            val totalHeight=entries.size*rowPx
                            if(totalHeight>bodyHeight) {
                                val length=(bodyHeight*bodyHeight/totalHeight).coerceAtLeast(20.dp.toPx()).coerceAtMost(bodyHeight)
                                val scrollY=rows.firstVisibleItemIndex*rowPx+rows.firstVisibleItemScrollOffset
                                val top=rowPx+(bodyHeight-length)*(scrollY/(totalHeight-bodyHeight)).coerceIn(0f,1f)
                                drawRoundRect(thumbColor,Offset(size.width-4.dp.toPx(),top),Size(3.dp.toPx(),length),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            }
                        }
                    }
                }
                val selected=entries.find{it.id==selectedId}
                Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface) {
                    if(selected==null)Text("셀을 누르면 전체 값을 확인할 수 있어요.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
                    else Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp).testTag("record-cell-detail"),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${selected.date} · ${if(selected.kind=="MACHINE")"기계투석"else"추가투석"}",style=MaterialTheme.typography.labelSmall)
                            Text("${recordColumns[selectedColumn].replace('\n',' ')}: ${recordValues(selected)[selectedColumn].replace('\n',' ')}")
                        }
                        SmallButton(onClick={open(selected)}){Text("기록 열기")}
                    }
                }

            }
        }
    }
    if(filterOpen)AlertDialog(onDismissRequest={filterOpen=false},title={Text("기록표 필터")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SelectionBox("조회 기간",filters.range.value,listOf("전체","7D","30D","기간 지정"),{label->
                if(label=="기간 지정")periodOpen=true else {
                    filters.range.value=label;filters.period.value=label!="전체"
                    if(label!="전체"){filters.from.value=LocalDate.now().minusDays(if(label=="7D")6 else 29).toString();filters.to.value=today()}
                }
            })
            if(filters.period.value)DateRangeControl(LocalDate.parse(filters.from.value),LocalDate.parse(filters.to.value),{periodOpen=true},enabled=filters.range.value=="기간 지정")
            SelectionBox("투석 종류",filters.type.value,listOf("전체","기계투석","추가투석"),{filters.type.value=it})
            SelectionBox("완료 상태",filters.status.value,listOf("전체 상태","미완료","완료"),{filters.status.value=it})
        }
    },confirmButton={SmallButton(onClick={filterOpen=false}){Text("닫기")}})
    if(periodOpen)DateRangeDialog(LocalDate.parse(filters.from.value),LocalDate.parse(filters.to.value),{periodOpen=false}){from,to->
        filters.from.value=from.toString();filters.to.value=to.toString();filters.range.value="기간 지정";filters.period.value=true;periodOpen=false
    }
}

@Composable private fun SheetCell(value:String,width:Dp,height:Dp,zoom:Float,header:Boolean,alternate:Boolean,
    modifier:Modifier=Modifier,selected:Boolean=false) {
    val colors=MaterialTheme.colorScheme
    Box(modifier.width(width).height(height).background(when{header->colors.primaryContainer;selected->colors.secondaryContainer;alternate->colors.surfaceContainerLow;else->colors.surface})
        .border(if(selected)2.dp else .5.dp,if(selected)colors.primary else colors.outlineVariant)
        .padding((6*zoom).dp),contentAlignment=Alignment.CenterStart) {
        Text(value,fontSize=(14*zoom).sp,lineHeight=(20*zoom).sp,fontWeight=if(header)FontWeight.Bold else FontWeight.Normal,
            maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
