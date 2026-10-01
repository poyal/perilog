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
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable fun RecordsScreen(s: Snapshot,vm: JournalViewModel,snackbar: SnackbarHostState,edit: (String?,String,String)->Unit) {
    var calendar by vm.recordFilters.calendar
    var month by vm.recordFilters.month
    var selected by vm.recordFilters.selected
    var type by vm.recordFilters.type
    var status by vm.recordFilters.status
    var from by vm.recordFilters.from
    var to by vm.recordFilters.to
    var period by vm.recordFilters.period
    var deleting by remember{mutableStateOf<Treatment?>(null)}
    var cancelling by remember{mutableStateOf<Usage?>(null)}
    val scope=rememberCoroutineScope()
    val entries=s.visibleRecords().sortedWith(compareByDescending<Treatment>{it.date}.thenByDescending{it.createdAt})
    Page("기록","하루의 기록을 차곡차곡") {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!calendar,{calendar=false},{Text("리스트")});FilterChip(calendar,{calendar=true},{Text("캘린더")})
            Spacer(Modifier.weight(1f));TextButton(onClick={edit(null,"MACHINE",if(calendar)selected else today())}){Text("+ 기록")}
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
            TextButton(onClick={period=!period}){Text(if(period)"기간 선택 접기"else"전체 기간 · 기간 지정")}
            if(period){DateControl(from,{from=it},"시작");DateControl(to,{to=it},"종료")}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("전체","기계투석","추가투석").forEach{FilterChip(type==it,{type=it},{Text(it)})}
            listOf("전체 상태","미완료","완료").forEach{FilterChip(status==it,{status=it},{Text(it)})}
        }
        val filtered=entries.filter{t->(if(calendar)t.date==selected else !period || t.date in from..to) &&
            (type=="전체" || t.kind==if(type=="기계투석")"MACHINE"else"MANUAL") &&
            (status=="전체 상태" || t.complete()==(status=="완료"))}
        if(filtered.isEmpty()) Paper { Text("아직 기록이 없어요.");if(!calendar || selected<=today())Action("이 날짜에 기록하기",{edit(null,"MACHINE",if(calendar)selected else today())}) }
        filtered.forEach { t -> Paper {
            Row(Modifier.fillMaxWidth().clickable{edit(t.id,t.kind,t.date)},verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Section("${t.date} · ${if(t.kind=="MACHINE")"기계투석"else"추가투석"}");Hint(if(t.complete())"✓ 기록 완료"else"남은 항목: ${t.missing().joinToString()}") }
                Text("›",style=MaterialTheme.typography.headlineMedium)
            }
            if(t.kind=="MACHINE") {
                Text("몸무게 ${t.weightGrams?.let{"${it/1000.0} kg"} ?: "—"} · 혈압 ${t.systolic ?: "—"}/${t.diastolic ?: "—"}")
                Text("총 제수량 ${t.totalUf()?.let{"$it mL"} ?: "—"}")
            } else if(t.manualDrain!=null) Text("배액 ${if(t.drainUnit=="kg")t.manualDrain/1000.0 else t.manualDrain} ${t.drainUnit}")
            t.items.forEach{Text("${it.name} × ${it.quantity}EA",style=MaterialTheme.typography.bodySmall)}
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
