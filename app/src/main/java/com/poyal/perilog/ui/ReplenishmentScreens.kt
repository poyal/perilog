@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.LocalDate

@Composable fun ReplenishmentListScreen(s: Snapshot, navigate: (String)->Unit, back: ()->Unit) {
    Page("입고 요청 계산기", "필요한 양을 계산하고, 받은 만큼 확인해요", back) {
        Action("새 입고 요청 계산", { navigate("request/new") }, icon=Icons.Outlined.Calculate)
        if(s.replenishmentPlans.isEmpty()) Paper {
            Section("다음 병원 방문을 준비해 보세요")
            Hint("기록이 있으면 사용량으로 계산하고, 기록이 없어도 평소 사용을 직접 입력할 수 있어요.")
            Hint("계산하거나 요청을 저장하는 것만으로 재고가 늘지는 않아요.")
        }
        s.replenishmentPlans.sortedByDescending { it.updatedAt }.forEach { p ->
            val progress=requestProgress(s,p)
            Paper(Modifier.clickable { navigate("requestDetail/${p.id}") }.testTag("request-${p.id}")) {
                Section("${p.input.visitDate} 입고 요청")
                Hint("다음 방문 ${p.input.nextVisitDate} · ${p.calculation.days}일분")
                Text(if(progress.all { it.remaining==0 }) "받을 물품이 없어요" else "아직 받을 품목 ${progress.count { it.remaining>0 }}개", fontWeight=FontWeight.SemiBold)
                MemoBlock(p.memo)
                TextButton(onClick={navigate("request/copy/${p.id}/${newId()}")}) { Text("다음 요청으로 복사") }
            }
        }
    }
}

@Serializable private data class RequestDraft(val id: String, val input: ReplenishmentInput,
    val memo: String="", val basis: ReplenishmentBasis?=null, val createdAt: Long=System.currentTimeMillis())

@Composable fun ReplenishmentEditor(s: Snapshot, vm: JournalViewModel, id: String, copyFrom: String?, navigate: (String)->Unit, back: ()->Unit) {
    val existing=s.replenishmentPlans.find { it.id==id }
    var draft by rememberJsonState("request:$id") {
        val copied=s.replenishmentPlans.find { it.id==copyFrom }
        val input=existing?.input ?: copied?.input?.copy(visitDate=today(),nextVisitDate="",historyFrom=LocalDate.now().minusDays(28).toString(),
            historyTo=LocalDate.now().minusDays(1).toString(),changes=emptyList(),stockOverrides=emptyMap(),requestOverrides=emptyMap()) ?: ReplenishmentInput()
        RequestDraft(id,input,existing?.memo ?: copied?.memo ?: "",existing?.calculation?.basis,existing?.createdAt ?: System.currentTimeMillis())
    }
    val original=rememberSaveable(id) { codec.encodeToString(draft) }
    var patternTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf(false) }
    var datesExpanded by rememberSaveable { mutableStateOf(false) }
    var editingStock by rememberSaveable { mutableStateOf<String?>(null) }
    val target=patternTarget
    if(target!=null) {
        val selected=if(target=="base") draft.input.pattern else draft.input.changes.firstOrNull { it.id==target }?.pattern
        if(selected!=null) CompositionLocalProvider(LocalHelpAction provides {navigate("guide/request-patterns")}) {
            UsagePatternEditor(s,selected,runCatching {usageEvidence(s,draft.input.historyFrom,draft.input.historyTo).days>0}.getOrDefault(false),{patternTarget=null}) { value ->
                draft=draft.copy(input=if(target=="base") draft.input.copy(pattern=value) else draft.input.copy(changes=draft.input.changes.map { if(it.id==target)it.copy(pattern=value) else it }))
                patternTarget=null
            }
        }
        return
    }
    val attempt=remember(s,draft) { runCatching { calculateReplenishment(s,draft.input,today(),draft.basis) } }
    val calculated=attempt.getOrNull()
    var lastValidJson by rememberSaveable { mutableStateOf(existing?.calculation?.let {codec.encodeToString(it)}) }
    LaunchedEffect(calculated) { if(calculated!=null) lastValidJson=codec.encodeToString(calculated) }
    val result=calculated ?: lastValidJson?.let {codec.decodeFromString<ReplenishmentCalculation>(it)}
    val hasReceipts=s.receipts.any { it.requestPlanId==id }
    val latestInput=if(draft.basis!=null && draft.input.historyTo==LocalDate.parse(draft.basis!!.asOf).minusDays(1).toString()) {
        val length=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(draft.input.historyFrom),LocalDate.parse(draft.input.historyTo))+1
        draft.input.copy(historyFrom=LocalDate.now().minusDays(length).toString(),historyTo=LocalDate.now().minusDays(1).toString())
    } else draft.input
    val latest=remember(s,latestInput,hasReceipts) { if(existing!=null && !hasReceipts && today()<=draft.input.visitDate)
        runCatching { calculateReplenishment(s,latestInput,today()) }.getOrNull() else null }
    EditorPage(if(existing==null) "입고 요청 계산기" else "입고 요청 수정", "다음 방문까지 필요한 물품을 계산해요",
        codec.encodeToString(draft)!=original,back,{
            result?.let { calculation -> vm.act("입고 요청을 저장했어요") {
                vm.repository.replenishmentPlan(ReplenishmentPlan(draft.id,draft.input,calculation,draft.memo,draft.createdAt,System.currentTimeMillis()));back()
            } }
        },calculated!=null && calculated.lines.isNotEmpty(),"요청안 저장",vm.busy.collectAsState().value) {
        Paper {
            DateControl(draft.input.visitDate,{draft=draft.copy(input=draft.input.copy(visitDate=it))},"이번 방문일")
            HorizontalDivider()
            DateControl(draft.input.nextVisitDate,{draft=draft.copy(input=draft.input.copy(nextVisitDate=it))},"다음 방문일")
            calculated?.let { Hint("${it.days}일분 · ${draft.input.visitDate}부터 ${LocalDate.parse(draft.input.nextVisitDate).minusDays(1)}까지") }
            if(s.appointments.any { it.date>=today() }) {
                TextButton(onClick={datesExpanded=!datesExpanded}) { Text("병원 예약에서 선택") }
                if(datesExpanded) s.appointments.filter { it.date>=today() }.sortedBy { it.date }.distinctBy { it.date }.forEach { a ->
                    Text(a.date+" · "+a.departments.joinToString { it.name })
                    FlowRow { TextButton(onClick={draft=draft.copy(input=draft.input.copy(visitDate=a.date))}) {Text("이번 방문으로")}
                        TextButton(onClick={draft=draft.copy(input=draft.input.copy(nextVisitDate=a.date))}) {Text("다음 방문으로")} }
                }
            }
        }
        Paper {
            if(draft.input.pattern.mode=="HISTORY") {
                val evidence=calculated?.let {UsageEvidence(it.historyDays,it.historyTotalDays,emptyMap())} ?: runCatching { usageEvidence(s,draft.input.historyFrom,draft.input.historyTo) }.getOrNull()
                Hint(if(evidence!=null && evidence.days>0) "최근 ${evidence.totalDays}일 중 ${evidence.days}일의 기록으로 계산해요" else "기록이 없어도 평소 사용을 입력하면 계산할 수 있어요")
            } else Hint("직접 정한 평소 사용으로 계산해요")
            SecondaryButton(onClick={patternTarget="base"},modifier=Modifier.fillMaxWidth()) {Text("평소 사용 바꾸기")}
            TextButton(onClick={details=!details}) {Text(if(details) "계산 조건 접기" else "참고 기간 · 사용 변경 · 여유분")}
            if(details) {
                Hint("참고 기간은 계산 기준일 전날까지예요. 기록 없는 날짜는 평균에서 제외해요.")
                DateControl(draft.input.historyFrom,{draft=draft.copy(input=draft.input.copy(historyFrom=it))},"참고 시작일")
                DateControl(draft.input.historyTo,{draft=draft.copy(input=draft.input.copy(historyTo=it))},"참고 종료일")
                NumberInput("여유분 더하기",draft.input.bufferDays,{draft=draft.copy(input=draft.input.copy(bufferDays=it ?: 0))},"일")
                FlowRow { listOf(0,3,7).forEach { n->TextButton(onClick={draft=draft.copy(input=draft.input.copy(bufferDays=n))}) {Text(if(n==0) "없음" else "${n}일")} } }
                draft.input.changes.forEach { change->key(change.id) {
                    HorizontalDivider()
                    DateControl(change.from,{date->draft=draft.copy(input=draft.input.copy(changes=draft.input.changes.map { if(it.id==change.id)it.copy(from=date) else it }))},"사용 변경일")
                    FlowRow {
                        TextButton(onClick={patternTarget=change.id}) {Text("이 날짜부터 사용할 구성")}
                        TextButton(onClick={draft=draft.copy(input=draft.input.copy(changes=draft.input.changes.filterNot { it.id==change.id }))}) {Text("변경 삭제")}
                    }
                } }
                TextButton(onClick={val c=PatternChange(from=draft.input.visitDate,pattern=draft.input.pattern);draft=draft.copy(input=draft.input.copy(changes=draft.input.changes+c));patternTarget=c.id}) {Text("+ 방문일부터 사용이 달라져요")}
                TextButton(onClick={val c=PatternChange(from=runCatching { LocalDate.parse(draft.input.visitDate).plusDays(7).toString() }.getOrDefault(today()),pattern=draft.input.pattern);draft=draft.copy(input=draft.input.copy(changes=draft.input.changes+c));patternTarget=c.id}) {Text("+ 중간에 사용이 바뀌어요")}
            }
        }
        if(latest!=null && latest!=calculated) Paper {
            Section("최신 기록으로 다시 계산")
            Hint("저장한 기준 ${draft.basis?.asOf ?: today()} · 새 기준 ${latest.basis.asOf}")
            latest.lines.filter { it.requested>0 || it.visitStock!=null }.forEach { line ->
                Text("${line.name}: 방문일 ${line.visitStock?.let { "약 ${it.label()}EA" } ?: "미확인"} · 계산 요청 약 ${line.suggested}EA")
            }
            TextButton(onClick={draft=draft.copy(input=latestInput,basis=latest.basis)}) {Text("새 계산 적용")}
            Hint("직접 수정한 요청량은 유지해요.")
        }
        if(hasReceipts) Paper {Hint("이미 받은 물품이 있어 저장한 계산 기준을 유지해요. 요청량을 바꿔도 실제 입고는 바뀌지 않아요.")}
        if(calculated==null) Paper {Hint(if(draft.input.nextVisitDate.isBlank()) "다음 방문일을 선택해 주세요." else attempt.exceptionOrNull()?.message ?: "입력한 조건을 확인해 주세요.");if(result!=null)Hint("아래 수량은 마지막으로 계산한 값이에요. 입력을 고치면 다시 계산해요.")}
        if(s.products.isEmpty()) Paper {Hint("사용하는 품목을 먼저 등록해 주세요.");Action("품목 등록하기",{navigate("products")})}
        result?.lines?.forEach { line -> key(line.productId) {
            Paper(Modifier.testTag("request-line-${line.productId}")) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    s.products.find { it.id==line.productId }?.let { ColorDot(it.color,24,it.name) };Section(line.name)
                }
                QuantitySummary(if(result.basis.asOf==today())"현재 재고" else "${result.basis.asOf} 기준 재고",line.currentStock?.let { "$it EA" } ?: "미등록")
                QuantitySummary("방문일 예상 재고",line.visitStock?.let { "약 ${it.label()} EA" } ?: "미확인")
                if(line.currentStock!=null && line.currentStock<0) Hint("현재 재고가 음수예요. 실제 보유 수량을 확인해 주세요.")
                if(line.beforeShortage.numerator>0) Text("방문 전 약 ${line.beforeShortage.label()}EA 부족 예상",color=MaterialTheme.colorScheme.error)
                if(line.visitStock==null) Hint("보유 재고를 빼지 않은 필요량이에요.")
                TextButton(onClick={editingStock=if(editingStock==line.productId)null else line.productId}) {Text("방문일 잔량 직접 입력")}
                if(editingStock==line.productId) {
                    NumberInput("${line.name} 방문일 잔량",draft.input.stockOverrides[line.productId],{q->draft=draft.copy(input=draft.input.copy(stockOverrides=if(q==null)draft.input.stockOverrides-line.productId else draft.input.stockOverrides+(line.productId to q)))},"EA")
                    Hint("이번 계산에만 적용해요. 실제 재고는 수량 맞추기에서 바꿔 주세요.")
                    TextButton(onClick={navigate("count/${line.productId}")}) {Text("실제 재고 수량 맞추기")}
                }
                QuantitySummary("${result.days}일 동안 사용할 양","약 ${line.demand.label()} EA")
                if(line.buffer.numerator>0) QuantitySummary("여유분","약 ${line.buffer.label()} EA")
                HorizontalDivider()
                QuantitySummary("계산한 요청량","약 ${line.suggested} EA")
                NumberInput("${line.name} 실제 요청할 수량",draft.input.requestOverrides[line.productId] ?: line.requested,{q->draft=draft.copy(input=draft.input.copy(requestOverrides=draft.input.requestOverrides+(line.productId to (q ?: 0))))},"EA",large=true)
                if(line.productId in draft.input.requestOverrides || line.productId in draft.input.stockOverrides) {
                    Hint("직접 수정한 값이 있어요")
                    TextButton(onClick={draft=draft.copy(input=draft.input.copy(requestOverrides=draft.input.requestOverrides-line.productId,stockOverrides=draft.input.stockOverrides-line.productId))}) {Text("계산값으로 되돌리기")}
                }
            }
        } }
        Paper {OutlinedTextField(draft.memo,{draft=draft.copy(memo=it)},label={Text("요청 메모")},modifier=Modifier.fillMaxWidth());Hint("요청안을 저장해도 실제 재고는 늘지 않아요.")}
    }
}

@Composable private fun QuantitySummary(label: String, value: String) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label);Text(value,fontWeight=FontWeight.SemiBold)
    }
}

@Composable private fun UsagePatternEditor(s: Snapshot, initial: UsagePattern, hasHistory: Boolean, back: ()->Unit, apply: (UsagePattern)->Unit) {
    var p by rememberJsonState("pattern") { if(initial.mode=="HISTORY" && !hasHistory) initial.copy(mode=if(s.templates.isEmpty())"DIRECT"else"WEEKLY") else initial }
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    var editingComposition by rememberSaveable { mutableStateOf<String?>(null) }
    val original=rememberSaveable { codec.encodeToString(p) }
    val valid=runCatching { validatePattern(p,s.products.map { it.id }.toSet()) }.isSuccess
    EditorPage("평소 사용 바꾸기","일주일에 어떻게 사용하시나요?",codec.encodeToString(p)!=original,back,{apply(p)},valid,"이 사용으로 계산") {
        Paper {
            Hint("기록이 없어도 직접 계산할 수 있어요.")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("HISTORY" to "기록으로 계산","WEEKLY" to "사용 구성","DIRECT" to "품목별 직접 입력").forEach { (mode,label)->
                    SelectionChip(p.mode==mode,{p=p.copy(mode=mode)},{Text(label)})
                }
            }
        }
        if(p.mode=="HISTORY") Paper {Hint("확정한 사용 기록의 하루 평균을 사용해요. 기록 없는 날짜는 평균에서 제외해요.")}
        if(p.mode=="WEEKLY") {
            Paper {
                Section("기본으로 사용하는 구성")
                SecondaryButton(onClick={choosing="base"},modifier=Modifier.fillMaxWidth()) {Text(p.base?.name ?: "기본 구성 선택")}
                Text("나머지 ${7-p.alternatives.sumOf { it.count }}일 사용",fontWeight=FontWeight.Bold)
                Hint("다른 구성을 더하면 자동으로 맞춰요.")
                p.base?.let { base -> TextButton(onClick={editingComposition=base.id}) {Text("기본 구성 품목·수량 수정")} }
            }
            p.alternatives.forEach { row -> key(row.composition.id) {
                CompositionFrequency(row,"일",7-p.alternatives.filterNot { it.composition.id==row.composition.id }.sumOf { it.count },
                    {count->p=p.copy(alternatives=p.alternatives.map { if(it.composition.id==row.composition.id)it.copy(count=count) else it })},
                    {p=p.copy(alternatives=p.alternatives.filterNot { it.composition.id==row.composition.id })},{editingComposition=row.composition.id})
            } }
            SecondaryButton(onClick={choosing="alternative"},enabled=p.base!=null && p.alternatives.sumOf { it.count }<7,modifier=Modifier.fillMaxWidth()) {Text("+ 다른 구성도 사용해요")}
            if(p.base!=null) Paper {
                Section("일주일 사용 요약")
                Hint("${p.base!!.name} ${7-p.alternatives.sumOf { it.count }}일"+p.alternatives.joinToString(""){" · ${it.composition.name} ${it.count}일"})
                Hint("요일을 정하지 않고 같은 비율로 예상해요.")
            }
        }
        if(p.mode=="DIRECT") {
            Paper {
                Section("품목별 사용량 직접 입력")
                FlowRow {listOf(1 to "하루",7 to "일주일").forEach { (n,label)->SelectionChip(p.directPeriodDays==n,{p=p.copy(directPeriodDays=n)},{Text(label)})}}
            }
            ItemQuantityEditor(s,p.directItems,{p=p.copy(directItems=it)})
        }
        Paper {Section("추가 사용");Hint("기본 사용 외에 같은 날 더 사용하는 구성이 있으면 추가해 주세요.")}
        p.extras.forEach { row ->key(row.composition.id) {
            CompositionFrequency(row,"회",1000,{count->p=p.copy(extras=p.extras.map { if(it.composition.id==row.composition.id)it.copy(count=count) else it })},
                {p=p.copy(extras=p.extras.filterNot { it.composition.id==row.composition.id })},{editingComposition=row.composition.id})
        } }
        SecondaryButton(onClick={choosing="extra"},modifier=Modifier.fillMaxWidth()) {Text("+ 추가로 사용하는 구성")}
        if(s.templates.isEmpty()) Hint("저장한 구성이 없어요. 품목별 직접 입력을 사용할 수 있어요.")
    }
    if(choosing!=null) AlertDialog(onDismissRequest={choosing=null},title={Text("사용 구성 선택")},text={
        androidx.compose.foundation.lazy.LazyColumn {items(s.templates.size) {index->val t=s.templates[index]
            TextButton(onClick={val c=t.forPlan();p=when(choosing) {"base"->p.copy(base=c);"alternative"->p.copy(alternatives=p.alternatives+WeeklyComposition(c));else->p.copy(extras=p.extras+WeeklyComposition(c))};choosing=null},modifier=Modifier.fillMaxWidth()) {Text(t.name)}
        } }
    },confirmButton={TextButton(onClick={choosing=null}) {Text("닫기")}})
    val composition=(listOfNotNull(p.base)+p.alternatives.map { it.composition }+p.extras.map { it.composition }).find { it.id==editingComposition }
    if(composition!=null) {
        // Full-screen dialog keeps the pattern editor state, including its unsaved day counts.
        androidx.compose.ui.window.Dialog(onDismissRequest={editingComposition=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize()) {
                var items by rememberJsonState("composition:${composition.id}") { composition.items }
                EditorPage("이번 계산의 구성 수정",composition.name,items!=composition.items,{editingComposition=null},{
                    val changed=composition.copy(items=items)
                    p=p.copy(base=if(p.base?.id==changed.id)changed else p.base,
                        alternatives=p.alternatives.map { if(it.composition.id==changed.id)it.copy(composition=changed) else it },
                        extras=p.extras.map { if(it.composition.id==changed.id)it.copy(composition=changed) else it })
                    editingComposition=null
                },items.isNotEmpty(),"이번 계산에 적용") {Hint("저장된 원래 사용 구성은 바뀌지 않아요.");ItemQuantityEditor(s,items,{items=it})}
            }
        }
    }
}

@Composable private fun CompositionFrequency(row: WeeklyComposition, unit: String, max: Int, change: (Int)->Unit, remove: ()->Unit, edit: ()->Unit) {
    Paper {
        Section(row.composition.name)
        Text("일주일에")
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IconButton(onClick={change(row.count-1)},enabled=row.count>1) {Icon(Icons.Outlined.Remove,"${row.composition.name} 줄이기")}
            Text("${row.count}$unit",style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={change(row.count+1)},enabled=row.count<max) {Icon(Icons.Outlined.Add,"${row.composition.name} 늘리기")}
        }
        FlowRow {TextButton(onClick=edit) {Text("품목·수량 수정")};TextButton(onClick=remove) {Text("구성 제외")}}
    }
}

@Composable fun ReplenishmentDetailScreen(s: Snapshot, id: String, navigate: (String)->Unit, back: ()->Unit) {
    val p=s.replenishmentPlans.find { it.id==id } ?: return
    val context=LocalContext.current
    var shareError by rememberSaveable { mutableStateOf(false) }
    Page("저장한 입고 요청","${p.input.visitDate} ~ ${p.input.nextVisitDate} · ${p.calculation.days}일분",back) {
        Action("물품 받았어요",{navigate("requestReceive/$id")},icon=Icons.Outlined.Inventory2)
        Paper {
            FlowRow {
                TextButton(onClick={navigate("request/$id")}) {Text("요청 수정")}
                TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("입고 요청",p.shareText()))}) {Text("요청 내용 복사")}
                TextButton(onClick={shareError=runCatching {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,p.shareText()),"입고 요청 공유"))}.isFailure}) {Text("공유")}
            }
            if(shareError) Hint("공유할 앱을 열지 못했어요. 요청 내용 복사를 이용해 주세요.")
            MemoBlock(p.memo)
            Hint("계산 기준 ${p.calculation.basis.asOf} · 요청 수정에서 새 계산을 확인할 수 있어요.")
        }
        requestProgress(s,p).filter { it.requested>0 || it.received>0 }.forEach { line->Paper {
            Section(line.name)
            QuantitySummary("요청","${line.requested} EA");QuantitySummary("받은 양","${line.received} EA");QuantitySummary("남은 양","${line.remaining} EA")
            if(line.excess>0) Hint("요청보다 ${line.excess}EA 더 받았어요.")
            p.calculation.lines.find { it.productId==line.productId }?.let { original->Hint("저장 당시 방문일 예상 재고 ${original.visitStock?.let { "약 ${it.label()}EA" } ?: "미확인"}") }
        } }
        Paper {
            Section("이전에 받은 내역")
            val receipts=s.receipts.filter { it.requestPlanId==id }.sortedByDescending { it.date }
            if(receipts.isEmpty()) Hint("아직 받은 내역이 없어요.")
            receipts.forEach { r->
                Text("${r.date} · ${if(r.cancelled) "취소된 입고" else "${r.lines.sumOf { it.quantity }}EA 입고"}")
                if(!r.cancelled) TextButton(onClick={navigate("receipt/${r.id}")}) {Text("${r.date} 받은 수량 수정")}
            }
            TextButton(onClick={navigate("stockHistory")}) {Text("재고 이력에서 입고 확인·취소")}
        }
        SecondaryButton(onClick={navigate("request/copy/$id/${newId()}")}) {Text("다음 요청으로 복사")}
    }
}

@Composable fun RequestReceiptScreen(s: Snapshot, vm: JournalViewModel, planId: String, back: ()->Unit) {
    val plan=s.replenishmentPlans.find { it.id==planId } ?: return
    val progress=requestProgress(s,plan)
    var receipt by rememberJsonState("request-receive:$planId") { Receipt(lines=emptyList(),requestPlanId=planId) }
    val original=rememberSaveable { codec.encodeToString(receipt) }
    EditorPage("물품 받았어요","이번에 받은 품목과 수량을 확인해요",codec.encodeToString(receipt)!=original,back,{
        vm.act("받은 물품을 재고에 더했어요") {vm.repository.receipt(receipt);back()}
    },receipt.date<=today() && receipt.lines.isNotEmpty() && receipt.lines.all { it.quantity in 1..100000 },"받은 수량 저장",vm.busy.collectAsState().value) {
        Paper {Section("${plan.input.visitDate} 입고 요청");DateControl(receipt.date,{receipt=receipt.copy(date=it)},"입고일")}
        progress.forEach { line->key(line.productId) {
            val selected=receipt.lines.find { it.productId==line.productId }
            Paper {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(selected!=null,{checked->receipt=receipt.copy(lines=receipt.lines.filterNot { it.productId==line.productId }+if(checked)listOf(ReceiptLine(productId=line.productId,quantity=line.remaining.coerceAtMost(100000))) else emptyList())},modifier=Modifier.semantics {contentDescription="${line.name} 받음"})
                    Text(line.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                }
                QuantitySummary("요청","${line.requested} EA");QuantitySummary("받은 양","${line.received} EA");QuantitySummary("남은 양","${line.remaining} EA")
                if(selected!=null) {
                    NumberInput("${line.name} 이번에 받은 수량",selected.quantity,{q->receipt=receipt.copy(lines=receipt.lines.map { if(it.productId==line.productId)it.copy(quantity=q ?: 0) else it })},"EA",large=true)
                    Hint("저장하면 받은 양이 ${line.received+selected.quantity}EA가 돼요.")
                    if(line.received+selected.quantity>line.requested) Hint("요청량보다 더 받은 수량도 그대로 기록해요.")
                    if(selected.quantity !in 1..100000) Hint("이번에 받은 수량을 1~100,000EA로 입력해 주세요.")
                }
            }
        } }
        Paper {Hint("체크한 품목만 재고에 더해요. 나머지 물품은 다음에 받을 때 기록하세요.");OutlinedTextField(receipt.memo,{receipt=receipt.copy(memo=it)},label={Text("입고 메모")},modifier=Modifier.fillMaxWidth())}
    }
}
