package com.poyal.perilog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poyal.perilog.R
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay

@Composable fun HomeScreen(s:Snapshot,vm:JournalViewModel,date:String,settings:()->Unit,edit:(String?,String,String)->Unit,records:()->Unit,stockScreen:()->Unit) {
    val entries=s.visibleRecords()
    val complete=entries.dayComplete(date)
    val todayEntries=entries.filter{it.date==date}
    val machine=todayEntries.find{it.kind=="MACHINE" && !it.complete()} ?: todayEntries.find{it.kind=="MACHINE"}
    val before=machine?.let{it.saved && it.weightGrams!=null && it.systolic!=null && it.diastolic!=null}==true
    val usage=machine?.usageConfirmed==true
    val after=machine?.let{it.saved && it.initialDrain!=null && it.machineUf!=null}==true
    val progress=listOf(before,usage,after).count{it}
    val resume=if(machine==null || !machine.complete())machine else todayEntries.firstOrNull{!it.complete()} ?: machine
    val yesterday=s.yesterdaySummary(date)
    var showYesterday by rememberSaveable(yesterday.date){mutableStateOf(false)}
    var celebration by remember{mutableStateOf(false)}
    LaunchedEffect(complete,date) {
        if(complete && date !in s.preferences.celebratedDates) {
            celebration=s.preferences.celebrate
            vm.markCelebrated(date)
            delay(2200);celebration=false
        }
    }
    val openToday={edit(resume?.id,resume?.kind ?: "MACHINE",date)}
    val hero=when {complete->"오늘도 기록을 마쳤어요";before && usage->"종료 후 기록이 남았어요";else->"오늘의 기록을 이어가요"}
    val action=when{complete->"오늘 기록 확인";machine==null->"오늘 기록 시작";resume?.kind=="MANUAL"->"추가투석 이어쓰기";before && usage->"종료 후 기록하기";else->"이어서 입력하기"}
    Page(stringResource(R.string.app_name),stringResource(R.string.app_description),brand=true,
        actions={IconButton(onClick=settings){Icon(Icons.Outlined.Settings,"설정",Modifier.size(28.dp))}}) {
        Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy. MM. dd (E)",Locale.KOREAN)),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hero,style=MaterialTheme.typography.headlineMedium)
        if(yesterday.visible)Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f)){Section("어제 기록 작성하기");Hint(yesterday.date)}
                TextButton(onClick={
                    if(yesterday.pending.size+(if(yesterday.needsMachine)1 else 0)>1)showYesterday=!showYesterday
                    else {val t=yesterday.pending.firstOrNull();edit(t?.id,t?.kind ?: "MACHINE",yesterday.date)}
                }){Text(if(showYesterday)"접기"else"작성하기")}
            }
            Hint(if(yesterday.needsMachine)"어제의 기계투석 기록이 없어요."else yesterday.pending.firstOrNull()?.missing()?.joinToString(" · ") ?: "")
            if(showYesterday) {
                if(yesterday.needsMachine)TextButton(onClick={edit(null,"MACHINE",yesterday.date)}){Text("어제 기계투석 기록 추가")}
                yesterday.pending.forEachIndexed{i,t->MenuRow("${if(t.kind=="MACHINE")"기계투석"else"추가투석"} ${i+1}",t.missing().joinToString(" · ")){edit(t.id,t.kind,t.date)}}
            }
        }
        AnimatedVisibility(celebration) {Paper {Text("🎀 오늘의 기록 완료!",style=MaterialTheme.typography.titleLarge);Hint("오늘 하루도 꼼꼼히 챙겼어요.")}}
        Box {
            Paper {
                Text("오늘의 기록",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(end=36.dp))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                listOf("시작 전 기록" to before,"사용 구성 확인" to usage,"종료 후 기록" to after).forEach{(label,done)->
                    Row(Modifier.fillMaxWidth().clickable(onClick=openToday).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=CircleShape,color=if(done)MaterialTheme.colorScheme.primary else Color.Transparent,
                            border=if(done)null else BorderStroke(1.5.dp,Coral),modifier=Modifier.size(32.dp)) {
                            Box(contentAlignment=Alignment.Center){if(done)Icon(Icons.Outlined.Check,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.onPrimary)}
                        }
                        Text(label,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                        Text(if(done)"완료"else"미입력",style=MaterialTheme.typography.bodyMedium,color=if(done)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(progress={progress/3f},modifier=Modifier.weight(1f).height(8.dp),color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surfaceVariant)
                    Hint("$progress / 3")
                }
                Action(action,openToday,icon=Icons.Outlined.ChevronRight)
                todayEntries.filter{it.kind=="MANUAL"}.forEach{t->TextButton(onClick={edit(t.id,t.kind,t.date)}){Text("추가투석 · ${if(t.complete())"완료"else t.missing().joinToString()}")}}
            }
            Bookmark(Modifier.align(Alignment.TopEnd).padding(end=24.dp))
        }
        AdaptivePair(first={Paper(Modifier.clickable{edit(null,"MANUAL",date)}){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.AddCircleOutline,null,tint=MaterialTheme.colorScheme.primary);Text("추가투석",fontWeight=FontWeight.Bold)}
        }},second={Paper(Modifier.clickable(onClick=records)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.Description,null,tint=MaterialTheme.colorScheme.primary);Text("기록 보기",fontWeight=FontWeight.Bold)}
        }})
        val finished=entries.map{it.date}.distinct().filter{entries.dayComplete(it)}
        val weekStart=LocalDate.parse(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        Paper {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                Column(horizontalAlignment=Alignment.CenterHorizontally){Hint("이번 주 기록");Text("${finished.count{it>=weekStart && it<=date}}일",style=MaterialTheme.typography.headlineMedium)}
                VerticalDivider(Modifier.height(52.dp),color=MaterialTheme.colorScheme.outlineVariant)
                Column(horizontalAlignment=Alignment.CenterHorizontally){Hint("누적 기록");Text("${finished.size}일",style=MaterialTheme.typography.headlineMedium)}
            }
        }
        val stock=remember(s,date){inventory(s,date)}
        stock.products.forEach{(id,balance)->
            val p=s.products.find{it.id==id} ?: return@forEach
            val near=balance.lots.mapNotNull{lot->expiryState(lot.expiry,lot.remaining,date,s.preferences.expiryDays)?.let{lot to it}}
            val low=p.lowStock?.let{balance.registered && balance.balance<=it}==true
            if(near.isNotEmpty() || balance.unallocated>0 || low)Paper(Modifier.clickable(onClick=stockScreen)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Schedule,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f)) {
                        near.forEach{(lot,state)->Text(state,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.secondary);Hint("${p.name} · ${lot.remaining}EA")}
                        if(balance.unallocated>0){Section("재고 확인 필요");Hint("${p.name} · 미배정 ${balance.unallocated}EA")}
                        if(low){Section("재고 부족 안내");Hint("${p.name} · 현재 ${balance.balance}EA")}
                    }
                    Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
    }
}
