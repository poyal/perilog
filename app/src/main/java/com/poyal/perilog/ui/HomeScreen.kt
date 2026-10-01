package com.poyal.perilog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay

@Composable fun HomeScreen(s: Snapshot,vm: JournalViewModel,date: String,settings: ()->Unit,edit: (String?,String,String)->Unit,records: ()->Unit,stockScreen: ()->Unit) {
    val entries=s.visibleRecords()
    val complete=entries.dayComplete(date)
    val todayEntries=entries.filter{it.date==date}
    val machine=todayEntries.find{it.kind=="MACHINE" && !it.complete()} ?: todayEntries.find{it.kind=="MACHINE"}
    val stock=remember(s,date){inventory(s,date)}
    val resume=if(machine==null || !machine.complete()) machine else todayEntries.firstOrNull{!it.complete()} ?: machine
    var celebration by remember{mutableStateOf(false)}
    LaunchedEffect(complete,date) {
        if(complete && date !in s.preferences.celebratedDates) {
            celebration=s.preferences.celebrate
            vm.preferences(s.preferences.copy(celebratedDates=s.preferences.celebratedDates+date))
            delay(2200);celebration=false
        }
    }
    Page("나의 하루","나의 투석 기록 · $date",actions={IconButton(onClick=settings){Icon(Icons.Outlined.Settings,"설정")}}) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Bow(64);Text(if(complete)"오늘도 기록을 마쳤어요"else"오늘의 기록을 이어가요",style=MaterialTheme.typography.headlineSmall)
        }
        AnimatedVisibility(celebration) { Paper { Text("🎀 오늘의 기록 완료!",style=MaterialTheme.typography.titleLarge);Text("오늘 하루도 꼼꼼히 챙겼어요.") } }
        Paper {
            Section("오늘의 기록")
            listOf("시작 전 기록" to (machine?.let{it.saved && it.weightGrams!=null && it.systolic!=null && it.diastolic!=null}==true),
                "사용 구성 확인" to (machine?.usageConfirmed==true),"종료 후 기록" to (machine?.let{it.saved && it.initialDrain!=null && it.machineUf!=null}==true)).forEach{(name,done)->
                Row(Modifier.fillMaxWidth()) { Text(if(done)"✓  $name"else"○  $name",Modifier.weight(1f));Text(if(done)"완료"else"미입력",color=if(done)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) }
            }
            if(machine!=null && !machine.complete()) Hint("남은 항목: ${machine.missing().joinToString(" · ")}")
            Action(if(complete)"오늘 기록 확인"else if(machine==null)"오늘 기록 시작"else if(resume?.kind=="MANUAL")"추가투석 이어쓰기"else"이어서 입력하기",{edit(resume?.id,resume?.kind ?: "MACHINE",date)})
            todayEntries.filter{it.kind=="MANUAL"}.forEach { t -> TextButton(onClick={edit(t.id,t.kind,t.date)}){Text("추가투석 · ${if(t.complete())"완료"else t.missing().joinToString()}")} }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={edit(null,"MANUAL",date)},Modifier.weight(1f).heightIn(min=52.dp)){Text("추가투석")}
            OutlinedButton(onClick=records,Modifier.weight(1f).heightIn(min=52.dp)){Text("기록 보기")}
        }
        val finished=entries.map{it.date}.distinct().filter{entries.dayComplete(it)}
        val weekStart=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        Paper {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Hint("이번 주 기록");Text("${finished.count{it>=weekStart && it<=date}}일",style=MaterialTheme.typography.headlineMedium) }
                Column { Hint("누적 기록");Text("${finished.size}일",style=MaterialTheme.typography.headlineMedium) }
            }
        }
        entries.filter{it.date<date && !it.complete()}.sortedByDescending{it.date}.take(3).forEach { t ->
            Paper(Modifier.clickable{edit(t.id,t.kind,t.date)}){Section("${t.date} 미완료 기록");Hint(t.missing().joinToString(" · "))}
        }
        val limit=LocalDate.now().plusDays(s.preferences.expiryDays.toLong()).toString()
        stock.products.forEach{(id,balance)->
            val p=s.products.find{it.id==id} ?: return@forEach
            val near=balance.lots.filter{it.remaining>0 && it.expiry!=null && it.expiry<=limit}
            if(near.isNotEmpty() || balance.unallocated>0 || p.lowStock?.let{balance.registered && balance.balance<=it}==true) Paper(Modifier.clickable(onClick=stockScreen)) {
                Section(p.name)
                near.forEach { lot -> Text("${expiryState(lot.expiry,lot.remaining,date,s.preferences.expiryDays)} · ${lot.remaining}EA") }
                if(balance.unallocated>0) Text("재고 확인 필요 · 배정하지 못한 사용 ${balance.unallocated}EA")
                if(p.lowStock?.let{balance.registered && balance.balance<=it}==true) Text("재고 부족 안내 · 현재 ${balance.balance}EA")
            }
        }
    }
}
