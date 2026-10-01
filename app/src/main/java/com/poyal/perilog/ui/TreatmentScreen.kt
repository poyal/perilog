@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*

@Composable fun TreatmentScreen(s:Snapshot,vm:JournalViewModel,back:()->Unit) {
    val t by vm.editor.collectAsStateWithLifecycle()
    val current=t ?: return
    var picker by rememberSaveable(current.id){mutableStateOf(false)}
    var quantities by rememberSaveable(current.id){mutableStateOf(false)}
    var beforeExpanded by rememberSaveable(current.id){mutableStateOf(!current.saved || current.weightGrams==null || current.systolic==null || current.diastolic==null)}
    var options by rememberSaveable(current.id){mutableStateOf(false)}
    var calculation by rememberSaveable(current.id){mutableStateOf(false)}
    var discard by rememberSaveable{mutableStateOf(false)}
    var error by rememberSaveable{mutableStateOf("")}
    fun update(next:Treatment)=vm.change(next)
    val beforeComplete=current.saved && current.weightGrams!=null && current.systolic!=null && current.diastolic!=null
    Page(if(current.kind=="MACHINE")"치료 기록"else"추가투석",back=back,actions={Bow(32)},
        footer={Action("기록 저장",{vm.save(current.items.isNotEmpty() || current.usageConfirmed,back)},!vm.busy.collectAsState().value)}) {
        DateControl(current.date,{if(it<=today()){vm.changeDate(it);error=""}else error="미래 날짜에는 치료 기록을 등록할 수 없어요."})
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        if(current.kind=="MACHINE") {
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StageBadge("1",beforeComplete);Text("시작 전",fontWeight=FontWeight.SemiBold)
                HorizontalDivider(Modifier.weight(1f),color=MaterialTheme.colorScheme.primary.copy(alpha=.35f))
                StageBadge("2",current.saved && current.initialDrain!=null && current.machineUf!=null);Text("종료 후",fontWeight=FontWeight.SemiBold)
            }
            Paper {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Section("시작 전 기록");Spacer(Modifier.weight(1f));TextButton(onClick={beforeExpanded=!beforeExpanded}){Text(if(beforeExpanded)"접기"else"수정")}}
                if(!beforeExpanded)AdaptivePair(first={MeasurementSummary(current.weightGrams?.let{"${it/1000.0} kg"} ?: "—","몸무게")},second={MeasurementSummary("${current.systolic ?: "—"} / ${current.diastolic ?: "—"}","혈압 · mmHg")})
                else {
                    current.sourceDate?.let{Hint("$it 측정값을 참고해 불러왔어요. 이 기록의 측정값으로 확인해 주세요.")}
                    NumberInput("몸무게",current.weightGrams,{update(current.copy(weightGrams=it))},"kg",1000,listOf(100,500,1000))
                    AdaptivePair(first={NumberInput("수축기 혈압",current.systolic,{update(current.copy(systolic=it))},"mmHg",steps=listOf(1))},second={NumberInput("이완기 혈압",current.diastolic,{update(current.copy(diastolic=it))},"mmHg",steps=listOf(1))})
                }
            }
        }
        Paper {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Section("사용 구성");Spacer(Modifier.weight(1f));TextButton(onClick={picker=!picker;quantities=false}){Text(if(picker)"구성 선택 접기"else"구성 변경")}}
            if(current.items.isEmpty())Hint("사용한 품목을 선택해 주세요.")else ProductChips(s,current.items)
            if(picker) {
                if(s.templates.isEmpty())Hint("설정 → 사용 구성 관리에서 자주 쓰는 조합을 만들어 보세요.")
                s.templates.forEach{template->
                    Surface(onClick={update(current.copy(items=template.items.map{it.copy(batchId=null)}));picker=false;quantities=false},
                        shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){Text(template.name,Modifier.weight(1f),fontWeight=FontWeight.Bold);Icon(Icons.Outlined.ChevronRight,null)}
                            template.items.forEach{ProductLine(s,it,compact=true)}
                        }
                    }
                }
            }
            TextButton(onClick={quantities=!quantities;picker=false}){Text(if(quantities)"수량 조정 마치기"else"이번 기록만 수량 조정")}
            if(current.items.isEmpty())Row(verticalAlignment=Alignment.CenterVertically){Checkbox(current.usageConfirmed,{update(current.copy(usageConfirmed=it))});Text("이 기록에서는 사용한 물품이 없어요",Modifier.weight(1f))}
        }
        if(quantities) {
            Hint("이 기록의 품목과 수량만 바꿔요. 저장한 사용 구성은 그대로예요.")
            if(s.products.isEmpty())Paper{Hint("설정 또는 재고에서 사용하는 품목을 먼저 등록해 주세요.")}
            ItemQuantityEditor(s,current.items,{update(current.copy(items=it))},batches=true)
        }
        if(current.kind=="MACHINE")Paper {
            Section("종료 후 기록")
            AdaptivePair(first={NumberInput("초기배액량",current.initialDrain,{update(current.copy(initialDrain=it))},"mL",large=true)},second={
                NumberInput("기계 제수량",current.machineUf,{update(current.copy(machineUf=it))},"mL",signed=true,large=true)
            })
            if(current.machineUf!=null)TextButton(onClick={update(current.copy(machineUf=current.machineUf.let{-it}))}){Text("제수량 + / − 바꾸기",style=MaterialTheme.typography.bodyMedium)}
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("총 제수량",fontWeight=FontWeight.Bold)
                    Text(current.totalUf()?.let{"$it mL"} ?: "—",style=MaterialTheme.typography.headlineLarge)
                    TextButton(onClick={calculation=!calculation}){Text("계산 방법 ${if(calculation)"접기"else"보기"}");Icon(if(calculation)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)}
                    if(calculation){
                        Text("초기배액량 − 이전 최종 주입 설정값 + 기계 제수량",style=MaterialTheme.typography.bodyMedium)
                        Hint("설정값 기준 계산 · 이전 기준 ${current.basisMl?.let{"$it mL"} ?: "미확인"}")
                        NumberInput("이 기록의 이전 주입 기준",current.basisMl,{update(current.copy(basisMl=it))},"mL")
                        Hint("이전 주입 기준을 모르면 비워 두세요. 총 제수량은 계산하지 않아요.")
                    }
                }
            }
        }else Paper {
            Section("배액 기록 · 선택");Hint("물품만 저장해도 추가투석 기록이 완료돼요.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("g","kg","mL").forEach{unit->FilterChip(current.drainUnit==unit,{
                update(current.copy(drainUnit=unit,manualDrain=if((current.drainUnit=="mL")!=(unit=="mL"))null else current.manualDrain,previousFill=if(unit=="mL")current.previousFill else null))
            },{Text(unit)})}}
            NumberInput(if(current.drainUnit=="mL")"배액량"else"배액무게",current.manualDrain,{update(current.copy(manualDrain=it))},current.drainUnit,if(current.drainUnit=="kg")1000 else 1)
            if(current.drainUnit=="mL") {
                NumberInput("이 배액에 해당하는 이전 주입량 · 선택",current.previousFill,{update(current.copy(previousFill=it))},"mL")
                current.totalUf()?.let{Text("제수량 $it mL",style=MaterialTheme.typography.titleLarge)}
            }else Hint("무게 그대로 보관해요. 부피 환산이나 용기 무게 자동 차감은 하지 않아요.")
        }
        Paper {
            TextButton(onClick={options=!options}){Icon(Icons.Outlined.Notes,null);Spacer(Modifier.width(8.dp));Text(if(current.kind=="MACHINE")"평균저류시간 · 시간 · 메모 ${if(options)"접기"else"추가"}"else"시간 · 메모 ${if(options)"접기"else"추가"}")}
            if(options) {
                if(current.kind=="MACHINE")AdaptivePair(first={NumberInput("평균저류 시간",current.dwellMinutes?.div(60),{update(current.copy(dwellMinutes=it?.let{h->h*60+(current.dwellMinutes?.rem(60)?:0)}))},"시")},second={NumberInput("분",current.dwellMinutes?.rem(60),{if(it==null || it<60)update(current.copy(dwellMinutes=it?.let{m->(current.dwellMinutes?.div(60)?:0)*60+m}))},"분")})
                OutlinedTextField(current.startTime,{update(current.copy(startTime=it))},label={Text("시작 시각 · 예: 22:00")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(current.endTime,{update(current.copy(endTime=it))},label={Text("종료 시각 · 예: 07:00")},modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(current.interrupted,{update(current.copy(interrupted=it))});Text("중단·재시작 등 특이사항이 있었어요",Modifier.weight(1f))}
                OutlinedTextField(current.memo,{update(current.copy(memo=it))},label={Text("메모")},modifier=Modifier.fillMaxWidth(),minLines=2)
            }
        }
        Hint("입력한 내용은 초안으로 보관해요. 사용 물품은 기록 저장 시 재고에 반영해요.")
        TextButton(onClick={discard=true}){Text("이 초안 버리기",color=MaterialTheme.colorScheme.secondary)}
    }
    if(discard)Confirm("초안을 버릴까요?","기존에 저장한 기록과 사용 내역은 유지됩니다.",{discard=false}){vm.discard(current.id){discard=false;back()}}
}
@Composable private fun StageBadge(label:String,done:Boolean) {
    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp)){
        Box(contentAlignment=Alignment.Center){if(done)Icon(Icons.Outlined.Check,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onPrimary)else Text(label,color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun MeasurementSummary(value:String,label:String) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(value,style=MaterialTheme.typography.headlineSmall);Hint(label)
    }
}
