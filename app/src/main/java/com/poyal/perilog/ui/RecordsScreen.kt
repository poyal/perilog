@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable fun RecordsScreen(s: Snapshot,vm: JournalViewModel,snackbar: SnackbarHostState,edit: (String?,String,String)->Unit) {
    var mode by vm.recordFilters.mode
    var range by vm.recordFilters.range
    val calendar=mode=="캘린더"
    var month by vm.recordFilters.month
    var selected by vm.recordFilters.selected
    var type by vm.recordFilters.type
    var status by vm.recordFilters.status
    var from by vm.recordFilters.from
    var to by vm.recordFilters.to
    var period by vm.recordFilters.period
    var adding by rememberSaveable{mutableStateOf(false)}
    var newDate by rememberSaveable{mutableStateOf(today())}
    var deleting by remember{mutableStateOf<Treatment?>(null)}
    var cancelling by remember{mutableStateOf<Usage?>(null)}
    var itemDetailsId by rememberSaveable{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val entries=s.visibleRecords().sortedWith(compareByDescending<Treatment>{it.date}.thenByDescending{it.createdAt})
    Page("기록","하루의 기록을 차곡차곡") {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("리스트","캘린더","표").forEach{label->FilterChip(mode==label,{
                mode=label
                if(label=="표" && range=="전체"){range="7D";period=true;from=LocalDate.now().minusDays(6).toString();to=today()}
            },{Text(label)})}
            TextButton(onClick={newDate=if(calendar)selected else today();adding=!adding}){Text("+ 기록")}
        }
        if(adding)Paper {
            Section("기록 추가")
            DateControl(newDate,{newDate=it})
            if(newDate>today())Hint("오늘 또는 과거 날짜를 선택해 주세요.")
            Action("기계투석 기록 추가",{edit(null,"MACHINE",newDate)},newDate<=today())
            OutlinedButton(onClick={edit(null,"MANUAL",newDate)},enabled=newDate<=today(),modifier=Modifier.fillMaxWidth()){Text("추가투석 기록 추가")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(!calendar)SelectionBox("조회 기간",range,listOf("전체","7D","30D","기간 지정"),{label->
                range=label;period=label!="전체"
                if(label=="7D" || label=="30D"){from=LocalDate.now().minusDays(if(label=="7D")6 else 29).toString();to=today()}
            },Modifier.weight(1f))
            SelectionBox("투석 종류",type,listOf("전체","기계투석","추가투석"),{type=it},Modifier.weight(1f))
            SelectionBox("완료 상태",status,listOf("전체 상태","미완료","완료"),{status=it},Modifier.weight(1f))
        }
        if(calendar) Paper {
            val ym=YearMonth.parse(month)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={month=ym.minusMonths(1).toString()}){Text("이전")};Section("${ym.year}년 ${ym.monthValue}월");TextButton(onClick={month=ym.plusMonths(1).toString()}){Text("다음")}
            }
            Row { listOf("월","화","수","목","금","토","일").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Hint(it)}} }
            val offset=ym.atDay(1).dayOfWeek.value-1
            val rows=(offset+ym.lengthOfMonth()+6)/7
            repeat(rows) { week -> Row(Modifier.fillMaxWidth()) {
                repeat(7){weekday ->
                    val day=week*7+weekday-offset+1
                    if(day !in 1..ym.lengthOfMonth()) Spacer(Modifier.weight(1f)) else {
                        val date=ym.atDay(day).toString();val count=entries.count{it.date==date}
                        val color=if(date==selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        Column(Modifier.weight(1f).heightIn(min=54.dp).background(color,RoundedCornerShape(10.dp)).clickable{selected=date}.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(day.toString());if(count>0) Hint(if(entries.dayComplete(date))"✓ $count"else"· $count")
                        }
                    }
                }
            } }
            Text("$selected · ${entries.count{it.date==selected}}건")
        } else {
            if(range=="기간 지정"){DateControl(from,{from=it},"시작");DateControl(to,{to=it},"종료")}
            if(period)Hint(if(from>to)"종료일을 시작일 이후로 선택해 주세요."else"$from ~ $to")
        }
        val filtered=entries.filter{t->(if(calendar)t.date==selected else !period || t.date in from..to) &&
            (type=="전체" || t.kind==if(type=="기계투석")"MACHINE"else"MANUAL") &&
            (status=="전체 상태" || t.complete()==(status=="완료"))}
        if(filtered.isEmpty()) Paper { Text("아직 기록이 없어요.");if(!calendar || selected<=today())Action("이 날짜에 기록하기",{newDate=if(calendar)selected else today();adding=true}) }
        if(mode=="표" && filtered.isNotEmpty())RecordTable(filtered){t->edit(t.id,t.kind,t.date)}
        if(mode!="표")filtered.forEach { t -> key(t.id) {
            RecordCard(t,t.compositionName(s),t.compositionColor(s),open={edit(t.id,t.kind,t.date)},delete={deleting=t},items={itemDetailsId=t.id})
        } }
        val orphans=s.usages.filter{!it.cancelled && it.items.isNotEmpty() && s.treatments.none{t->t.id==it.id}}
        if(orphans.isNotEmpty()) Paper { Section("기록 삭제 후 유지한 사용 내역");orphans.forEach{u->Text("${u.date} · ${u.items.joinToString{it.name+" ${it.quantity}EA"}}");TextButton(onClick={cancelling=u}){Text("잘못된 사용 취소")}} }
    }
    entries.find{it.id==itemDetailsId}?.let{t->
        val usage=s.usages.find{it.id==t.id}
        AlertDialog(onDismissRequest={itemDetailsId=null},title={
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                t.compositionColor(s)?.let{ColorDot(it,20,"${t.compositionName(s)} 대표")}
                Text(t.compositionName(s),Modifier.weight(1f))
            }
        },
            text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Hint("${t.date} · ${if(t.kind=="MACHINE")"기계투석"else"추가투석"}")
                if(t.items.isEmpty())Hint("사용한 물품이 없어요.")
                t.items.forEach{item->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        ProductLine(s,item)
                        item.batchId?.let{batchId->
                            s.receipts.firstOrNull{receipt->receipt.lines.any{it.id==batchId}}?.let{receipt->
                                val line=receipt.lines.first{it.id==batchId}
                                Hint("${receipt.date} 입고 · ${line.expiry?.let{"사용기한 $it"} ?: "사용기한 무관"}")
                            }
                            s.counts.find{it.id==batchId}?.let{Hint("${it.date} 재고 확인분")}
                        }
                    }
                }
                if(usage?.cancelled==true)Hint("사용이 취소된 내역이에요.")
            }},containerColor=MaterialTheme.colorScheme.surface,
            confirmButton={TextButton(onClick={itemDetailsId=null}){Text("닫기")}},
            dismissButton={if(usage!=null && !usage.cancelled && usage.items.isNotEmpty())TextButton(onClick={itemDetailsId=null;cancelling=usage}){Text("사용 취소",color=MaterialTheme.colorScheme.secondary)}})
    }
    deleting?.let{t->Confirm("${if(t.saved)"기록"else"초안"}을 삭제할까요?","실제 사용한 물품의 재고 차감은 유지돼요. 잘못 입력한 사용은 사용 구성 상세의 ‘사용 취소’로 되돌릴 수 있어요.",{deleting=null}){
        vm.act {
            if(t.saved)vm.repository.deleteTreatment(t.id)else vm.repository.discardDraft(t.id)
            deleting=null
            if(t.saved)scope.launch { if(snackbar.showSnackbar("기록을 삭제했어요","되돌리기",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed)vm.act{vm.repository.undoDelete(t)} }
        }
    }}
    cancelling?.let{u->Confirm("잘못 입력한 사용을 취소할까요?","${u.items.joinToString{it.name+" ${it.quantity}EA"}}의 차감을 되돌립니다. 실제 사용한 물품이면 취소하지 마세요.",{cancelling=null}){vm.act("사용 내역을 취소했어요"){vm.repository.cancelUsage(u.id);cancelling=null}}}
}

@Composable private fun RecordCard(t:Treatment,compositionName:String,compositionColor:Long?,open:()->Unit,delete:()->Unit,items:()->Unit) {
    var menu by rememberSaveable(t.id){mutableStateOf(false)}
    Card(Modifier.fillMaxWidth().testTag("record-card-${t.id}"),shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f).padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Section("${t.date} · ${if(t.kind=="MACHINE")"기계투석"else"추가투석"}")
                    Hint(if(t.complete())"✓ 기록 완료"else"남은 항목: ${t.missing().joinToString()}")
                }
                if(t.kind=="MACHINE")Text("몸무게 ${t.weightGrams?.let{"${it/1000.0} kg"} ?: "—"} · 혈압 ${t.systolic ?: "—"}/${t.diastolic ?: "—"}",style=MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                    if(t.kind=="MACHINE" || t.manualDrain!=null)Box(Modifier.heightIn(min=48.dp),contentAlignment=Alignment.CenterStart) {
                        Text(if(t.kind=="MACHINE")"총 제수량 ${t.totalUf()?.let{"$it mL"} ?: "—"}"
                            else"배액 ${if(t.drainUnit=="kg")t.manualDrain!!/1000.0 else t.manualDrain} ${t.drainUnit}",style=MaterialTheme.typography.bodyMedium)
                    }
                    AssistChip(onClick=items,label={
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            compositionColor?.let{ColorDot(it,14,"$compositionName 대표")}
                            Text(compositionName,style=MaterialTheme.typography.bodyMedium)
                        }
                    },modifier=Modifier.testTag("record-items-${t.id}"))
                }
                if(t.memo.isNotEmpty())Text(t.memo,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick={menu=true},modifier=Modifier.testTag("record-menu-${t.id}")) {
                    Icon(Icons.Outlined.MoreVert,"${t.date} ${if(t.kind=="MACHINE")"기계투석"else"추가투석"} 메뉴")
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("수정")},onClick={menu=false;open()},leadingIcon={Icon(Icons.Outlined.Edit,null)})
                    DropdownMenuItem(text={Text("삭제",color=MaterialTheme.colorScheme.error)},onClick={menu=false;delete()},
                        leadingIcon={Icon(Icons.Outlined.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)})
                }
            }
        }
    }
}


/** Each row is one treatment: multiple manual sessions and original units stay separate. */
@Composable private fun RecordTable(entries:List<Treatment>,open:(Treatment)->Unit) {
    val rowHeight=(64 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val scroll=rememberScrollState()
    val labels=listOf("몸무게\nkg","혈압\nmmHg","초기배액\nmL","기계 제수량\nmL","총 제수량\nmL","추가 배액\n원래 단위","평균저류\n시:분","상태")
    Paper {
        Section("기간별 기록 표 · ${entries.size}건")
        Hint("좌우로 밀어 모든 항목을 확인해요. 날짜를 누르면 기록을 수정해요. 빈 항목은 —로 표시해요.")
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(120.dp)) {
                TableCell("날짜 · 유형",rowHeight,true)
                entries.forEach{t->Box(Modifier.testTag("record-row-${t.id}").clickable{open(t)}){
                    TableCell("${t.date}\n${if(t.kind=="MACHINE")"기계투석"else"추가투석"}",rowHeight)
                }}
            }
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                Row {labels.forEach{Box(Modifier.width(116.dp)){TableCell(it,rowHeight,true)}}}
                entries.forEach{t->Row(Modifier.clickable{open(t)}) {
                    val values=listOf(t.weightGrams?.let{java.math.BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString()} ?: "—",
                        "${t.systolic ?: "—"}/${t.diastolic ?: "—"}",t.initialDrain?.toString() ?: "—",t.machineUf?.toString() ?: "—",
                        t.totalUf()?.toString() ?: "—",t.manualDrain?.let{if(t.drainUnit=="kg")"${it/1000.0} kg"else"$it ${t.drainUnit}"} ?: "—",
                        t.dwellMinutes?.let{"${it/60}:${(it%60).toString().padStart(2,'0')}"} ?: "—",if(t.complete())"완료"else"미완료")
                    values.forEach{Box(Modifier.width(116.dp)){TableCell(it,rowHeight)}}
                }}
            }
        }
        Hint("기계 총 제수량은 설정값 기준 계산이에요. 배액무게와 mL를 합산하지 않아요.")
    }
}
@Composable private fun TableCell(value:String,height:androidx.compose.ui.unit.Dp,header:Boolean=false) {
    Column(Modifier.fillMaxWidth().height(height).background(if(header)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f).padding(8.dp),contentAlignment=Alignment.CenterStart){Text(value,style=MaterialTheme.typography.bodyMedium,fontWeight=if(header)FontWeight.Bold else FontWeight.Normal)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
