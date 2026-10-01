package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*

@Composable fun StockScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit) {
    var filter by remember{mutableStateOf("전체")}
    var selected by remember{mutableStateOf<Product?>(null)}
    val stock=remember(s){inventory(s)}
    Page("재고 관리","내가 사용하는 물품을 한눈에") {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={navigate("receipts")},Modifier.weight(1f).heightIn(min=52.dp)){Text("입고 등록 · 이력")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("전체","투석액","소모품").forEach{FilterChip(filter==it,{filter=it},{Text(it)})} }
        if(s.products.isEmpty())Paper{Text("사용하는 투석액과 소모품을 등록해 주세요.");Action("첫 품목 등록",{navigate("products")})}
        s.products.filter{it.active && (filter=="전체" || it.kind==filter)}.forEach{p->
            val b=stock.products.getValue(p.id)
            Paper(Modifier.clickable{selected=p}) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ColorDot(p.color);Section(p.name);Spacer(Modifier.weight(1f));Text(if(b.registered)"${b.balance} EA"else"미등록",style=MaterialTheme.typography.titleLarge)
                }
                val dated=b.lots.filter{it.remaining>0 && it.expiry!=null}
                if(dated.isEmpty())Hint("사용기한 무관")else dated.forEach{Hint("${it.expiry}까지 · ${it.remaining}EA")}
                if(b.unallocated>0)Text("확인 필요 · 미배정 ${b.unallocated}EA",color=MaterialTheme.colorScheme.secondary)
                Hint("눌러서 재고 상세와 현재 수량 확인")
            }
        }
        Paper {
            TextButton(onClick={navigate("products")}){Text("품목 관리 · 색상")}
            TextButton(onClick={navigate("templates")}){Text("사용 구성 관리")}
        }
    }
    selected?.let{p->
        val b=stock.products.getValue(p.id)
        var qty by remember(p.id){mutableStateOf<Int?>(null)}
        var date by remember(p.id){mutableStateOf(today())}
        var memo by remember(p.id){mutableStateOf("")}
        var confirm by remember{mutableStateOf(false)}
        AlertDialog(onDismissRequest={selected=null},title={Text(p.name)},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("현재 ${if(b.registered)"${b.balance} EA"else"미등록"}")
            b.lots.filter{it.remaining>0}.forEach{Text("${it.date} · ${it.expiry ?: "기한 무관"} · ${it.remaining}EA")}
            if(b.unallocated>0)Hint("사용 내역 중 ${b.unallocated}EA의 입고·재고를 확인해 주세요.")
            HorizontalDivider();Section("현재 수량 맞추기")
            Hint("처음 시작하거나 실제 재고와 다를 때 사용해요. 이 날짜 이전의 사용은 새 수량에서 다시 차감하지 않아요.")
            DateControl(date,{date=it})
            NumberInput("직접 확인한 수량",qty,{qty=it},"EA")
            OutlinedTextField(memo,{memo=it},label={Text("사유 · 메모")},modifier=Modifier.fillMaxWidth())
            Hint("새 기준의 재고는 ‘사용기한 무관’으로 시작해요. 기한별 수량을 유지하려면 입고 또는 사용 내역을 수정해 주세요.")
            s.counts.filter{it.productId==p.id}.sortedByDescending{it.createdAt}.take(10).forEach{Hint("실사 ${it.date} · ${it.quantity}EA · ${it.memo}")}
        }},confirmButton={TextButton(onClick={confirm=true},enabled=qty!=null && date<=today()){Text("현재 수량 저장")}},dismissButton={TextButton(onClick={selected=null}){Text("닫기")}})
        if(confirm)Confirm("현재 재고 기준을 바꿀까요?","$date 기준 ${qty}EA로 맞춥니다. 기존 기록은 이력으로 남습니다.",{confirm=false}){vm.act("현재 수량을 저장했어요"){vm.repository.count(StockCount(productId=p.id,date=date,quantity=qty!!,memo=memo.ifBlank{"현재 재고 확인"}));selected=null}}
    }
}

@Composable fun ProductsScreen(s:Snapshot,vm:JournalViewModel,back:()->Unit) {
    var editing by remember{mutableStateOf<Product?>(null)}
    Page("품목 관리","단위는 모두 EA예요",back) {
        Action("+ 품목 추가",{editing=Product(name="")})
        s.products.forEach{p->Paper(Modifier.clickable{editing=p}) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){ColorDot(p.color);Section(p.name);Text(if(p.active)"사용 중"else"보관")}
            Hint("${p.kind} · ${p.vendor}");if(p.memo.isNotEmpty())Text(p.memo)
        }}
    }
    editing?.let{initial->
        var p by remember(initial.id){mutableStateOf(initial)}
        var colorPicker by remember{mutableStateOf(false)}
        AlertDialog(onDismissRequest={editing=null},title={Text(if(initial.name.isEmpty())"품목 추가"else"품목 수정")},text={Column(Modifier.heightIn(max=540.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(p.name,{p=p.copy(name=it)},label={Text("제품명 · 농도 · 규격")},placeholder={Text("예: 투석액 1.5% 5L")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("투석액","소모품").forEach{FilterChip(p.kind==it,{p=p.copy(kind=it)},{Text(it)})}}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)){s.preferences.palette.forEach{color->Box(Modifier.size(44.dp).background(Color(color),MaterialTheme.shapes.small).border(if(p.color==color)3.dp else 0.dp,MaterialTheme.colorScheme.onSurface,MaterialTheme.shapes.small).clickable{p=p.copy(color=color)})}}
            TextButton(onClick={colorPicker=true}){Text("컬러 피커 · 색 추가")}
            OutlinedTextField(p.vendor,{p=p.copy(vendor=it)},label={Text("공급처")},modifier=Modifier.fillMaxWidth())
            NumberInput("부족 안내 기준 · 선택",p.lowStock,{p=p.copy(lowStock=it)},"EA")
            Hint("비워 두면 수량 부족 알림을 사용하지 않아요.")
            OutlinedTextField(p.memo,{p=p.copy(memo=it)},label={Text("메모")},modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically){Switch(p.active,{p=p.copy(active=it)});Text("현재 사용하는 품목")}
            Hint("품목을 보관해도 이전 기록과 재고 이력은 유지돼요.")
        }},confirmButton={TextButton(onClick={vm.act("품목을 저장했어요"){vm.repository.product(p.copy(name=p.name.trim()));editing=null}},enabled=p.name.isNotBlank()){Text("저장")}},dismissButton={TextButton(onClick={editing=null}){Text("취소")}})
        if(colorPicker)ColorPicker(p.color,{colorPicker=false}){color->p=p.copy(color=color);if(color !in s.preferences.palette)vm.preferences(s.preferences.copy(palette=s.preferences.palette+color));colorPicker=false}
    }
}

@Composable fun ColorPicker(initial:Long,dismiss:()->Unit,done:(Long)->Unit) {
    var red by remember{mutableFloatStateOf(((initial shr 16) and 255).toFloat())}
    var green by remember{mutableFloatStateOf(((initial shr 8) and 255).toFloat())}
    var blue by remember{mutableFloatStateOf((initial and 255).toFloat())}
    val color=0xFF000000L or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()
    var hex by remember(color){mutableStateOf(String.format("#%06X",color and 0xFFFFFF))}
    AlertDialog(onDismissRequest=dismiss,title={Text("나만의 색")},text={Column {
        Box(Modifier.fillMaxWidth().height(70.dp).background(Color(color),MaterialTheme.shapes.medium))
        Text("빨강");Slider(red,{red=it},valueRange=0f..255f)
        Text("초록");Slider(green,{green=it},valueRange=0f..255f)
        Text("파랑");Slider(blue,{blue=it},valueRange=0f..255f)
        OutlinedTextField(hex,{hex=it;val rgb=it.removePrefix("#");if(rgb.length==6)rgb.toLongOrNull(16)?.let{v->red=((v shr 16)and 255).toFloat();green=((v shr 8)and 255).toFloat();blue=(v and 255).toFloat()}},label={Text("HEX 색상")},modifier=Modifier.fillMaxWidth())
    }},confirmButton={TextButton(onClick={done(color)}){Text("색 추가")}},dismissButton={TextButton(onClick=dismiss){Text("취소")}})
}

@Composable fun TemplatesScreen(s:Snapshot,vm:JournalViewModel,back:()->Unit) {
    var editing by remember{mutableStateOf<UsageTemplate?>(null)}
    var deleting by remember{mutableStateOf<UsageTemplate?>(null)}
    Page("사용 구성 관리","자주 쓰는 품목 조합을 저장해요",back) {
        Action("+ 구성 만들기",{editing=UsageTemplate(name="",items=emptyList())})
        s.templates.forEach{t->Paper {Section(t.name);t.items.forEach{Text("${it.name} × ${it.quantity}EA")};Row{TextButton(onClick={editing=t}){Text("수정")};TextButton(onClick={deleting=t}){Text("삭제")}}}}
    }
    editing?.let{initial->
        var name by remember(initial.id){mutableStateOf(initial.name)}
        var items by remember(initial.id){mutableStateOf(initial.items)}
        var picker by remember{mutableStateOf(false)}
        AlertDialog(onDismissRequest={editing=null},title={Text("사용 구성")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name,{name=it},label={Text("구성 이름")});items.forEach{Text("${it.name} × ${it.quantity}EA")}
            TextButton(onClick={picker=true}){Text("품목 · 수량 선택")}
        }},confirmButton={TextButton(onClick={vm.act("구성을 저장했어요"){vm.repository.template(initial.copy(name=name.trim(),items=items.map{it.copy(batchId=null)}));editing=null}},enabled=name.isNotBlank() && items.isNotEmpty()){Text("저장")}},dismissButton={TextButton(onClick={editing=null}){Text("취소")}})
        if(picker)UsagePicker(s.copy(templates=emptyList()),items,{picker=false}){items=it;picker=false}
    }
    deleting?.let{t->Confirm("구성을 삭제할까요?","이전 기록에 저장한 품목과 수량은 유지돼요.",{deleting=null}){vm.act{vm.repository.deleteTemplate(t.id);deleting=null}}}
}

@Composable fun ReceiptsScreen(s:Snapshot,vm:JournalViewModel,back:()->Unit) {
    var editing by remember{mutableStateOf<Receipt?>(null)}
    var cancelling by remember{mutableStateOf<Receipt?>(null)}
    Page("입고 등록 · 이력","한 번에 들어온 물품을 함께 기록해요",back) {
        Action("+ 일괄 입고 등록",{editing=Receipt(lines=s.products.filter{it.active}.map{ReceiptLine(productId=it.id,quantity=0)})},s.products.isNotEmpty())
        if(s.products.isEmpty())Text("품목을 먼저 등록해 주세요.")
        s.receipts.sortedWith(compareByDescending<Receipt>{it.date}.thenByDescending{it.createdAt}).forEach{r->Paper {
            Section("${r.date}${if(r.cancelled)" · 취소됨"else""}")
            r.lines.forEach{line->Text("${s.products.find{it.id==line.productId}?.name ?: "품목"} · ${line.quantity}EA");Hint(line.expiry?.let{"${it}까지"} ?: "사용기한 무관")}
            if(r.memo.isNotEmpty())Text(r.memo)
            if(!r.cancelled)Row{TextButton(onClick={editing=r}){Text("수정")};TextButton(onClick={cancelling=r}){Text("입고 취소")}}
        }}
    }
    editing?.let{initial->
        var r by remember(initial.id){mutableStateOf(initial)}
        var add by remember{mutableStateOf(false)}
        AlertDialog(onDismissRequest={editing=null},title={Text("품목별 입고 수량")},text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DateControl(r.date,{r=r.copy(date=it)},"입고일")
            r.lines.forEach{line->key(line.id){
                val p=s.products.find{it.id==line.productId}
                Section(p?.name ?: "품목")
                NumberInput("입고 수량",line.quantity.takeIf{it>0},{q->r=r.copy(lines=r.lines.map{if(it.id==line.id)it.copy(quantity=q?:0)else it})},"EA")
                Row(verticalAlignment=Alignment.CenterVertically){Switch(line.expiry!=null,{dated->r=r.copy(lines=r.lines.map{if(it.id==line.id)it.copy(expiry=if(dated)today()else null)else it})});Text(if(line.expiry==null)"사용기한 무관"else"사용기한 날짜 지정")}
                if(line.expiry!=null)DateControl(line.expiry,{date->r=r.copy(lines=r.lines.map{if(it.id==line.id)it.copy(expiry=date)else it})},"기한")
                TextButton(onClick={r=r.copy(lines=r.lines+ReceiptLine(productId=line.productId,quantity=0))}){Text("이 품목의 다른 사용기한 추가")}
                HorizontalDivider()
            }}
            TextButton(onClick={add=true}){Text("다른 품목 추가")}
            OutlinedTextField(r.memo,{r=r.copy(memo=it)},label={Text("입고 메모")},modifier=Modifier.fillMaxWidth())
            Hint("빈 수량과 0EA는 저장에서 제외해요.")
        }},confirmButton={TextButton(onClick={vm.act("입고 내역을 저장했어요"){vm.repository.receipt(r.copy(lines=r.lines.filter{it.quantity>0}));editing=null}},enabled=r.lines.any{it.quantity>0} && r.date<=today()){Text("함께 저장")}},dismissButton={TextButton(onClick={editing=null}){Text("취소")}})
        if(add)AlertDialog(onDismissRequest={add=false},title={Text("품목 선택")},text={Column(Modifier.verticalScroll(rememberScrollState())){s.products.forEach{p->TextButton(onClick={r=r.copy(lines=r.lines+ReceiptLine(productId=p.id,quantity=0));add=false}){Text(p.name)}}}},confirmButton={TextButton(onClick={add=false}){Text("닫기")}})
    }
    cancelling?.let{r->Confirm("입고를 취소할까요?","실제 사용한 물품의 이력은 유지돼요. 사용량보다 입고가 부족해지면 재고 확인 안내가 표시됩니다.",{cancelling=null}){vm.act("입고를 취소했어요"){vm.repository.receipt(r.copy(cancelled=true));cancelling=null}}}
}
