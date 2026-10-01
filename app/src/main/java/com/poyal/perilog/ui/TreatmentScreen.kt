package com.poyal.perilog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate

@Composable fun TreatmentScreen(s: Snapshot,vm: JournalViewModel,back: ()->Unit) {
    val current by vm.editor.collectAsStateWithLifecycle()
    val t=current
    if(t==null) { Page("치료 기록",back=back) { Text("기록을 목록에서 다시 열어 주세요.") };return }
    var picker by remember(t.id){mutableStateOf(false)}
    var beforeExpanded by remember(t.id){mutableStateOf(!t.saved || t.weightGrams==null || t.systolic==null || t.diastolic==null)}
    var options by remember(t.id){mutableStateOf(false)}
    var calculation by remember(t.id){mutableStateOf(false)}
    var discard by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    fun update(next:Treatment)=vm.change(next)
    Page(if(t.kind=="MACHINE")"치료 기록"else"추가투석",if(t.saved)"저장한 기록 수정"else"입력 중인 내용은 초안으로 보관돼요",back) {
        DateControl(t.date,{if(it<=today())vm.changeDate(it) else error="미래 날짜에는 치료 기록을 등록할 수 없어요."})
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        if(t.kind=="MACHINE") Paper {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Section("시작 전 기록");Spacer(Modifier.weight(1f));TextButton(onClick={beforeExpanded=!beforeExpanded}){Text(if(beforeExpanded)"접기"else"수정")} }
            if(!beforeExpanded) Text("${t.weightGrams?.div(1000.0) ?: "—"} kg · ${t.systolic ?: "—"} / ${t.diastolic ?: "—"} mmHg")
            if(beforeExpanded) {
            t.sourceDate?.let { Hint("$it 측정값을 참고해 불러왔어요. 오늘 값으로 확인하고 저장해 주세요.") }
            NumberInput("몸무게",t.weightGrams,{update(t.copy(weightGrams=it))},"kg",1000,listOf(100,500,1000))
            NumberInput("수축기 혈압",t.systolic,{update(t.copy(systolic=it))},"mmHg",steps=listOf(1))
            NumberInput("이완기 혈압",t.diastolic,{update(t.copy(diastolic=it))},"mmHg",steps=listOf(1))
            }
        }
        Paper {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Section("사용 구성");Spacer(Modifier.weight(1f));TextButton(onClick={picker=true}){Text("구성 변경")} }
            if(t.items.isEmpty()) Hint("사용한 품목을 선택해 주세요.")
            t.items.forEach{item ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ColorDot(s.products.find{it.id==item.productId}?.color ?: 0xFF647789)
                    Text(item.name,Modifier.weight(1f));Text("${item.quantity} EA")
                }
            }
            if(t.items.isEmpty()) Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(t.usageConfirmed,{update(t.copy(usageConfirmed=it))});Text("이 기록에서는 사용한 물품이 없어요")
            } else Hint("기록 저장 시 사용량을 재고에 반영해요. 다시 저장해도 중복 차감하지 않아요.")
        }
        if(t.kind=="MACHINE") Paper {
            Section("종료 후 기록")
            NumberInput("초기배액량",t.initialDrain,{update(t.copy(initialDrain=it))},"mL")
            NumberInput("기계 제수량",t.machineUf,{update(t.copy(machineUf=it))},"mL",signed=true)
            TextButton(onClick={update(t.copy(machineUf=t.machineUf?.let{ -it }))}) { Text("제수량 + / − 바꾸기") }
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("총 제수량");Text(t.totalUf()?.let{"$it mL"} ?: "—",style=MaterialTheme.typography.headlineLarge)
                    TextButton(onClick={calculation=!calculation}){Text("계산 방법 ${if(calculation)"접기"else"보기"}")}
                    if(calculation) {
                        Text("초기배액량 − 이전 최종 주입 설정값 + 기계 제수량")
                        Hint("설정값 기준 계산 · 이전 기준 ${t.basisMl?.let{"$it mL"} ?: "미확인"}")
                        NumberInput("이 기록의 이전 주입 기준",t.basisMl,{update(t.copy(basisMl=it))},"mL")
                        Hint("이전 주입 기준을 모르는 경우 비워 두면 총 제수량은 계산하지 않아요.")
                    }
                }
            }
        } else Paper {
            Section("배액 기록 · 선택")
            Hint("물품만 저장해도 추가투석 기록이 완료돼요.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("g","kg","mL").forEach { unit -> FilterChip(t.drainUnit==unit,{update(t.copy(drainUnit=unit,manualDrain=if((t.drainUnit=="mL")!=(unit=="mL"))null else t.manualDrain,previousFill=if(unit=="mL")t.previousFill else null))},{Text(unit)}) }
            }
            NumberInput(if(t.drainUnit=="mL")"배액량"else"배액무게",t.manualDrain,{update(t.copy(manualDrain=it))},t.drainUnit,if(t.drainUnit=="kg")1000 else 1)
            if(t.drainUnit=="mL") {
                NumberInput("이 배액에 해당하는 이전 주입량 · 선택",t.previousFill,{update(t.copy(previousFill=it))},"mL")
                t.totalUf()?.let { Text("제수량 $it mL",style=MaterialTheme.typography.titleLarge) }
            } else Hint("입력한 무게 그대로 보관해요. 부피로 환산하거나 용기 무게를 자동으로 빼지 않아요.")
        }
        Paper {
            TextButton(onClick={options=!options}){Text(if(t.kind=="MACHINE")"평균저류시간 · 시간 · 메모 ${if(options)"접기"else"추가"}"else"시간 · 메모 ${if(options)"접기"else"추가"}")}
            if(options) {
                if(t.kind=="MACHINE") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { NumberInput("평균저류 시간",t.dwellMinutes?.div(60),{update(t.copy(dwellMinutes=it?.let{h -> h*60+(t.dwellMinutes?.rem(60)?:0)}))},"시") }
                        Column(Modifier.weight(1f)) { NumberInput("분",t.dwellMinutes?.rem(60),{if(it==null || it<60)update(t.copy(dwellMinutes=it?.let{m -> (t.dwellMinutes?.div(60)?:0)*60+m}))},"분") }
                    }
                }
                OutlinedTextField(t.startTime,{update(t.copy(startTime=it))},label={Text("시작 시각 · 예: 22:00")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(t.endTime,{update(t.copy(endTime=it))},label={Text("종료 시각 · 예: 07:00")},modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(t.interrupted,{update(t.copy(interrupted=it))});Text("중단·재시작 등 특이사항이 있었어요") }
                OutlinedTextField(t.memo,{update(t.copy(memo=it))},label={Text("메모")},modifier=Modifier.fillMaxWidth(),minLines=2)
            }
        }
        Action("기록 저장",{vm.save(t.items.isNotEmpty() || t.usageConfirmed,back)})
        Hint("아직 입력하지 않은 항목이 있어도 저장하고 나중에 이어 쓸 수 있어요.")
        TextButton(onClick={discard=true}){Text("이 초안 버리기")}
    }
    if(picker) UsagePicker(s,t.items,{picker=false}) { update(t.copy(items=it));picker=false }
    if(discard) Confirm("초안을 버릴까요?","기존에 저장한 기록과 사용 내역은 유지됩니다.",{discard=false}) { vm.discard(t.id) {discard=false;back()} }
}

@Composable fun UsagePicker(s: Snapshot,initial: List<Item>,dismiss: ()->Unit,done: (List<Item>)->Unit) {
    var items by remember{mutableStateOf(initial)}
    val stock=remember(s){inventory(s)}
    AlertDialog(onDismissRequest=dismiss,title={Text("사용 구성 선택")},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            s.templates.forEach { template -> OutlinedButton(onClick={items=template.items},Modifier.fillMaxWidth()){Text(template.name)} }
            if(s.products.isEmpty()) Text("재고 → 품목 관리에서 사용하는 물품을 먼저 등록해 주세요.")
            s.products.filter{it.active || items.any { line -> line.productId==it.id }}.forEach{p->
                val item=items.find{it.productId==p.id}
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { ColorDot(p.color);Text(p.name,Modifier.weight(1f)) }
                NumberInput("수량",item?.quantity,{q->items=items.filterNot{it.productId==p.id}+(if(q!=null && q>0)listOf(Item(p.id,p.name,q,item?.batchId))else emptyList())},"EA",steps=listOf(1))
                if(item!=null) {
                    var showBatches by remember(p.id){mutableStateOf(false)}
                    TextButton(onClick={showBatches=!showBatches}){Text(if(item.batchId==null)"사용 재고 자동 배정"else"사용 재고 직접 선택됨")}
                    if(showBatches) {
                        TextButton(onClick={items=items.map{if(it.productId==p.id)it.copy(batchId=null)else it}}){Text("자동 배정")}
                        stock.products[p.id]?.lots?.forEach{lot ->
                            TextButton(onClick={items=items.map{if(it.productId==p.id)it.copy(batchId=lot.id)else it}}){Text("${lot.date} 입고 · ${lot.expiry ?: "기한 무관"} · ${lot.remaining}EA")}
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    },confirmButton={TextButton(onClick={done(items)}){Text("이 구성 사용")}},dismissButton={TextButton(onClick=dismiss){Text("취소")}})
}
