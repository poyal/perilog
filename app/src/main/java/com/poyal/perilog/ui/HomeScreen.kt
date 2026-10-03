@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poyal.perilog.R
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable fun HomeScreen(s:Snapshot,vm:JournalViewModel,date:String,settings:()->Unit,edit:(String?,String,String)->Unit,navigate:(String)->Unit,stockScreen:()->Unit) {
    val now by vm.localNow.collectAsState()
    val entries=s.visibleRecords()
    val daily=s.dailyProgress(date)
    val complete=daily.complete
    val todayEntries=entries.filter{it.date==date}
    val machine=daily.machine
    val before=daily.vitality
    val usage=daily.usage
    val after=daily.treatment
    val progress=daily.count
    val resume=daily.resume
    val yesterday=s.yesterdaySummary(date)
    var celebration by remember{mutableStateOf(false)}
    LaunchedEffect(complete,date) {
        if(complete && date !in s.preferences.celebratedDates) {
            celebration=s.preferences.celebrate
            vm.markCelebrated(date)
            delay(2200);celebration=false
        }
    }
    val openToday={edit(resume?.id,resume?.kind ?: "MACHINE",date)}
    val hero=when {complete->"오늘도 기록을 마쳤어요";before && usage->"투석 기록이 남았어요";else->"오늘의 기록을 이어가요"}
    val action=when{complete->"오늘 기록 확인";machine==null->"오늘 기록 시작";resume?.kind=="MANUAL"->"추가투석 이어쓰기";before && usage->"기록하기";else->"이어서 입력하기"}
    Page(stringResource(R.string.app_name),stringResource(R.string.app_description),brand=true,
        actions={SettingsIconButton(settings)}) {
        Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy. MM. dd (E)",Locale.KOREAN)),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hero,style=MaterialTheme.typography.headlineMedium)
        if(yesterday.visible)Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.secondary)
                FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("어제의 기록",style=MaterialTheme.typography.titleLarge)
                    Text(yesterday.date,Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            val pending=if(yesterday.needsMachine)listOf(Treatment(date=yesterday.date))+yesterday.pending else yesterday.pending
            pending.forEachIndexed{i,t->
                val open={edit(if(yesterday.needsMachine && i==0)null else t.id,t.kind,yesterday.date)}
                if(pending.size>1 || t.kind=="MANUAL")Section("${if(t.kind=="MACHINE")"기계투석"else"추가투석"}${if(pending.size>1)" ${i+1}"else""}")
                val stages=buildList {
                    if(t.kind=="MACHINE") {
                        if(t.weightGrams==null || t.systolic==null || t.diastolic==null)add("활력 상태" to t.missing().filter{it in listOf("몸무게","혈압")}.joinToString(" · "))
                        if(!t.usageConfirmed)add("사용 구성" to "")
                        if(t.initialDrain==null || t.machineUf==null)add("투석 기록" to t.missing().filter{it in listOf("초기배액량","제수량")}.joinToString(" · "))
                    } else if(!t.usageConfirmed)add("사용 구성" to "")
                    if(isEmpty() && !t.saved)add("기록 저장" to "")
                }
                stages.forEach{(label,detail)->
                    HomeRecordStep(label,false,open,detail)
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Action("어제 기록하기",{
                if(yesterday.needsMachine)edit(null,"MACHINE",yesterday.date)
                else yesterday.pending.firstOrNull()?.let{edit(it.id,it.kind,yesterday.date)}
            },icon=Icons.Outlined.ChevronRight)
        }
        AnimatedVisibility(celebration) {Paper {Text("🎀 오늘의 기록 완료!",style=MaterialTheme.typography.titleLarge);Hint("오늘 하루도 꼼꼼히 챙겼어요.")}}
        Box {
            Paper {
                Text("오늘의 기록",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(end=36.dp))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                listOf("활력 상태" to before,"사용 구성" to usage,"투석 기록" to after).forEach{(label,done)->
                    HomeRecordStep(label,done,openToday)
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
        HomeAppointment(s,now,navigate)
        HomeContacts(s,vm,navigate)
        val stock=remember(s,date){inventory(s,date)}
        stock.products.forEach{(id,balance)->
            val p=s.products.find{it.id==id} ?: return@forEach
            val low=p.lowStock?.let{balance.registered && balance.balance<=it}==true
            if(low)Paper(Modifier.clickable(onClick=stockScreen)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Schedule,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f)) {
                        Section("재고 부족 안내");Hint("${p.name} · 현재 ${balance.balance}EA")
                    }
                    Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
    }
}

@Composable private fun HomeRecordStep(label:String,done:Boolean,open:()->Unit,detail:String="") {
    Row(Modifier.fillMaxWidth().clickable(onClick=open).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        CompletionBadge(done)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(label,fontWeight=FontWeight.SemiBold)
            if(detail.isNotEmpty())Hint(detail)
        }
        Text(if(done)"완료"else"미입력",style=MaterialTheme.typography.bodyMedium,color=if(done)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
