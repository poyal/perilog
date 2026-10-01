@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("전체","7D","30D","기간 지정").forEach{label->FilterChip(range==label,{
                    range=label;period=label!="전체"
                    if(label=="7D" || label=="30D"){from=LocalDate.now().minusDays(if(label=="7D")6 else 29).toString();to=today()}
                },{Text(label)})}
            }
            if(range=="기간 지정"){DateControl(from,{from=it},"시작");DateControl(to,{to=it},"종료")}
            if(period)Hint(if(from>to)"종료일을 시작일 이후로 선택해 주세요."else"$from ~ $to")
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("전체","기계투석","추가투석").forEach{FilterChip(type==it,{type=it},{Text(it)})}
            listOf("전체 상태","미완료","완료").forEach{FilterChip(status==it,{status=it},{Text(it)})}
        }
        val filtered=entries.filter{t->(if(calendar)t.date==selected else !period || t.date in from..to) &&
            (type=="전체" || t.kind==if(type=="기계투석")"MACHINE"else"MANUAL") &&
            (status=="전체 상태" || t.complete()==(status=="완료"))}
        if(filtered.isEmpty()) Paper { Text("아직 기록이 없어요.");if(!calendar || selected<=today())Action("이 날짜에 기록하기",{newDate=if(calendar)selected else today();adding=true}) }
        if(mode=="표" && filtered.isNotEmpty())RecordTable(filtered){t->edit(t.id,t.kind,t.date)}
        if(mode!="표")filtered.forEach { t -> Paper {
            Row(Modifier.fillMaxWidth().clickable{edit(t.id,t.kind,t.date)},verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Section("${t.date} · ${if(t.kind=="MACHINE")"기계투석"else"추가투석"}");Hint(if(t.complete())"✓ 기록 완료"else"남은 항목: ${t.missing().joinToString()}") }
                Text("›",style=MaterialTheme.typography.headlineMedium)
            }
            if(t.kind=="MACHINE") {
                Text("몸무게 ${t.weightGrams?.let{"${it/1000.0} kg"} ?: "—"} · 혈압 ${t.systolic ?: "—"}/${t.diastolic ?: "—"}")
                Text("총 제수량 ${t.totalUf()?.let{"$it mL"} ?: "—"}")
            } else if(t.manualDrain!=null) Text("배액 ${if(t.drainUnit=="kg")t.manualDrain/1000.0 else t.manualDrain} ${t.drainUnit}")
            ProductChips(s,t.items)
            if(t.memo.isNotEmpty())Text(t.memo)
            Row { TextButton(onClick={edit(t.id,t.kind,t.date)}){Text("열기 / 수정")};TextButton(onClick={deleting=t}){Text(if(t.saved)"기록 삭제"else"초안 삭제")}
                s.usages.find{it.id==t.id && !it.cancelled && it.items.isNotEmpty()}?.let{u->TextButton(onClick={cancelling=u}){Text("사용 취소")}}
            }
        } }
        val orphans=s.usages.filter{!it.cancelled && it.items.isNotEmpty() && s.treatments.none{t->t.id==it.id}}
        if(orphans.isNotEmpty()) Paper { Section("기록 삭제 후 유지한 사용 내역");orphans.forEach{u->Text("${u.date} · ${u.items.joinToString{it.name+" ${it.quantity}EA"}}");TextButton(onClick={cancelling=u}){Text("잘못된 사용 취소")}} }
    }
    deleting?.let{t->Confirm("${if(t.saved)"기록"else"초안"}을 삭제할까요?","실제 사용한 물품의 재고 차감은 유지돼요. 잘못 입력한 사용은 ‘사용 취소’로 되돌릴 수 있어요.",{deleting=null}){
        vm.act {
            if(t.saved)vm.repository.deleteTreatment(t.id)else vm.repository.discardDraft(t.id)
            deleting=null
            if(t.saved)scope.launch { if(snackbar.showSnackbar("기록을 삭제했어요","되돌리기",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed)vm.act{vm.repository.undoDelete(t)} }
        }
    }}
    cancelling?.let{u->Confirm("잘못 입력한 사용을 취소할까요?","${u.items.joinToString{it.name+" ${it.quantity}EA"}}의 차감을 되돌립니다. 실제 사용한 물품이면 취소하지 마세요.",{cancelling=null}){vm.act("사용 내역을 취소했어요"){vm.repository.cancelUsage(u.id);cancelling=null}}}
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
