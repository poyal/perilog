@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable fun StockScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit) {
    var filter by rememberSaveable{mutableStateOf("전체")}
    val stock=remember(s){inventory(s)}
    Page("재고 관리","내가 사용하는 물품을 한눈에",brand=true,actions={IconButton(onClick={navigate("settings")}){Icon(Icons.Outlined.Settings,"설정")}}) {
        AdaptivePair(first={Action("입고 등록",{navigate("receipt/new")},icon=Icons.Outlined.Add)},second={
            OutlinedButton(onClick={navigate("receipts")},Modifier.fillMaxWidth().heightIn(min=54.dp)){Icon(Icons.Outlined.ReceiptLong,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("입고 이력")}
        })
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("전체","투석액","소모품").forEach{FilterChip(filter==it,{filter=it},{Text(it)})}}
        if(s.products.isEmpty())Paper{Section("내 물품을 등록해 보세요");Hint("투석액·카세트·라인을 나만의 색상으로 구분해요.");Action("첫 품목 등록",{navigate("products")})}
        s.products.filter{it.active && (filter=="전체" || it.kind==filter)}.forEach{p->
            val b=stock.products.getValue(p.id)
            Paper(Modifier.clickable{navigate("stock/${p.id}")}) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ColorDot(p.color,28,p.name)
                    Text(p.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Column(horizontalAlignment=Alignment.End){Text(if(b.registered)b.balance.toString()else"—",style=MaterialTheme.typography.headlineMedium);Hint(if(b.registered)"EA"else"미등록")}
                    Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))
                }
                val near=b.lots.filter{expiryState(it.expiry,it.remaining,today(),s.preferences.expiryDays)!=null}
                if(near.isNotEmpty())near.forEach{lot->
                    val days=ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(lot.expiry))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        Icon(Icons.Outlined.Schedule,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.secondary)
                        Text(if(days<0)"기한 지남 ${lot.remaining}EA"else"임박 ${lot.remaining}EA · ${if(days==0L)"오늘까지"else"${days}일 남음"}",color=MaterialTheme.colorScheme.secondary)
                    }
                }else Hint(if(b.lots.any{it.remaining>0 && it.expiry!=null})"사용기한별 재고 보기"else"사용기한 무관")
                if(b.unallocated>0)Text("확인 필요 · 미배정 ${b.unallocated}EA",color=MaterialTheme.colorScheme.secondary)
            }
        }
        Paper {
            MenuRow("품목 관리 · 색상",icon=Icons.Outlined.Inventory2){navigate("products")}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow("사용 구성 관리",icon=Icons.Outlined.ViewList){navigate("templates")}
        }
    }
}

@Composable fun StockDetailScreen(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    val p=s.products.find{it.id==id} ?: return
    val b=inventory(s).products.getValue(p.id)
    var qty by rememberSaveable(id){mutableStateOf<Int?>(null)}
    var date by rememberSaveable(id){mutableStateOf(today())}
    val initialDate=rememberSaveable(id){date}
    var memo by rememberSaveable(id){mutableStateOf("")}
    var confirm by rememberSaveable{mutableStateOf(false)}
    EditorPage("재고 상세","${p.name} · EA",qty!=null || date!=initialDate || memo.isNotBlank(),back,{confirm=true},qty!=null && date<=today(),"현재 수량 저장",vm.busy.collectAsState().value) {
        Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){ColorDot(p.color,28,p.name);Section(p.name)}
            Text(if(b.registered)"${b.balance} EA"else"아직 재고를 등록하지 않았어요",style=MaterialTheme.typography.headlineMedium)
            b.lots.filter{it.remaining>0}.forEach{Hint("${it.date} 입고 · ${it.expiry ?: "기한 무관"} · ${it.remaining}EA")}
            if(b.unallocated>0)Hint("사용 내역 중 ${b.unallocated}EA의 입고·재고를 확인해 주세요.")
        }
        Paper {
            Section("현재 수량 맞추기")
            Hint("실제 보유한 수량으로 새 기준을 만들어요. 기준 이전의 사용은 다시 차감하지 않아요.")
            DateControl(date,{date=it})
            NumberInput("직접 확인한 수량",qty,{qty=it},"EA")
            OutlinedTextField(memo,{memo=it},label={Text("사유 · 메모")},modifier=Modifier.fillMaxWidth())
            Hint("새 기준의 재고는 ‘사용기한 무관’이에요. 기한별 수량을 유지하려면 입고 또는 사용 내역을 수정해 주세요.")
        }
        val history=s.counts.filter{it.productId==id}.sortedByDescending{it.createdAt}
        if(history.isNotEmpty())Paper{Section("수량 확인 이력");history.forEach{Hint("${it.date} · ${it.quantity}EA · ${it.memo}")}}
    }
    if(confirm)Confirm("현재 재고 기준을 바꿀까요?","$date 기준 ${qty}EA로 맞춥니다. 이전 기록은 이력으로 남습니다.",{confirm=false}){
        confirm=false;vm.act("현재 수량을 저장했어요"){vm.repository.count(StockCount(productId=id,date=date,quantity=qty!!,memo=memo.ifBlank{"현재 재고 확인"}));back()}
    }
}

@Composable fun ProductsScreen(s:Snapshot,navigate:(String)->Unit,back:()->Unit) {
    Page("품목 관리","단위는 모두 EA예요",back) {
        Action("+ 품목 추가",{navigate("product/new")})
        s.products.forEach{p->Paper(Modifier.clickable{navigate("product/${p.id}")}) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                ColorDot(p.color,24,p.name);Text(p.name,Modifier.weight(1f),fontWeight=FontWeight.Bold);Hint(if(p.active)"사용 중"else"보관");Icon(Icons.Outlined.ChevronRight,null)
            }
            Hint("${p.kind} · ${p.vendor}");if(p.memo.isNotEmpty())Hint(p.memo)
        }}
    }
}
@Composable fun ProductEditor(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    var p by rememberJsonState("product:$id"){s.products.find{it.id==id} ?: Product(name="")}
    val original=rememberSaveable(id){codec.encodeToString(p)}
    var colorPicker by rememberSaveable{mutableStateOf(false)}
    EditorPage(if(id=="new")"품목 추가"else"품목 수정","이름·색상·재고 안내를 설정해요",codec.encodeToString(p)!=original,back,{
        vm.act("품목을 저장했어요"){vm.repository.product(p.copy(name=p.name.trim()),listOf(p.color));back()}
    },p.name.isNotBlank(),busy=vm.busy.collectAsState().value) {
        Paper {
            OutlinedTextField(p.name,{p=p.copy(name=it)},label={Text("제품명 · 농도 · 규격")},placeholder={Text("예: 투석액 1.5% 5L")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("투석액","소모품").forEach{FilterChip(p.kind==it,{p=p.copy(kind=it)},{Text(it)})}}
        }
        Paper {
            Section("품목 색상")
            ColorPalette(s.preferences.palette,p.color){p=p.copy(color=it)}
            TextButton(onClick={colorPicker=!colorPicker}){Text(if(colorPicker)"컬러 피커 접기"else"컬러 피커 · 색 추가")}
            if(colorPicker)InlineColorPicker(p.color){p=p.copy(color=it)}
            Hint("이 색상은 재고와 모든 사용 구성에 함께 표시돼요.")
        }
        Paper {
            OutlinedTextField(p.vendor,{p=p.copy(vendor=it)},label={Text("공급처")},modifier=Modifier.fillMaxWidth())
            NumberInput("부족 안내 기준 · 선택",p.lowStock,{p=p.copy(lowStock=it)},"EA")
            Hint("비워 두면 수량 부족 알림을 사용하지 않아요.")
            OutlinedTextField(p.memo,{p=p.copy(memo=it)},label={Text("메모")},modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically){Switch(p.active,{p=p.copy(active=it)});Spacer(Modifier.width(8.dp));Text("현재 사용하는 품목",Modifier.weight(1f))}
            Hint("품목을 보관해도 이전 기록과 재고 이력은 유지돼요.")
        }
    }
}
@Composable private fun ColorPalette(palette:List<Long>,selectedColor:Long,onColor:(Long)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        (palette+selectedColor).distinct().forEach{color->
            val hex=String.format(Locale.US,"#%06X",color and 0xFFFFFF)
            Box(Modifier.size(48.dp).background(Color(color),MaterialTheme.shapes.small)
                .border(if(selectedColor==color)3.dp else 1.dp,if(selectedColor==color)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,MaterialTheme.shapes.small)
                .clickable{onColor(color)}.semantics{contentDescription="색상 $hex";selected=selectedColor==color},contentAlignment=Alignment.Center){
                if(selectedColor==color)Icon(Icons.Outlined.Check,null,tint=if(androidx.core.graphics.ColorUtils.calculateLuminance(color.toInt())>.4)Color.Black else Color.White)
            }
        }
    }
}
@Composable fun InlineColorPicker(color:Long,onColor:(Long)->Unit) {
    var hex by rememberSaveable(color){mutableStateOf(String.format(Locale.US,"#%06X",color and 0xFFFFFF))}
    val errorId=rememberSaveable{newId()};val errors=LocalInputErrors.current
    val valid=hex.removePrefix("#").let{it.length==6 && it.toLongOrNull(16)!=null}
    DisposableEffect(errorId){onDispose{errors.remove(errorId)}}
    LaunchedEffect(valid){if(valid)errors.remove(errorId)else errors[errorId]="HEX 색상"}
    Box(Modifier.fillMaxWidth().height(52.dp).background(Color(color),MaterialTheme.shapes.medium).border(1.dp,MaterialTheme.colorScheme.outline,MaterialTheme.shapes.medium))
    listOf("빨강" to 16,"초록" to 8,"파랑" to 0).forEach{(name,shift)->
        Text(name,style=MaterialTheme.typography.bodyMedium)
        Slider(((color shr shift)and 255).toFloat(),{v->onColor((color and (255L shl shift).inv()) or (v.toLong() shl shift))},valueRange=0f..255f,modifier=Modifier.semantics{contentDescription="$name 색상 조절"})
    }
    OutlinedTextField(hex,{hex=it;val value=it.removePrefix("#");if(value.length==6)value.toLongOrNull(16)?.let{rgb->onColor(0xFF000000L or rgb)}},label={Text("HEX 색상")},isError=!valid,modifier=Modifier.fillMaxWidth(),singleLine=true)
}

@Composable fun TemplatesScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit,back:()->Unit) {
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    Page("사용 구성 관리","자주 쓰는 품목과 EA 수량을 저장해요",back) {
        Action("+ 구성 만들기",{navigate("template/new")})
        if(s.templates.isEmpty())Paper{Hint("밤 투석·추가투석처럼 자주 쓰는 조합을 만들어 보세요.")}
        s.templates.forEach{t->Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ColorDot(t.color,22,"${t.name} 대표");Text(t.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            }
            t.items.forEach{ProductLine(s,it)}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                TextButton(onClick={navigate("template/${t.id}")}){Text("수정")}
                TextButton(onClick={deleting=t.id}){Text("삭제",color=MaterialTheme.colorScheme.secondary)}
            }
        }}
    }
    deleting?.let{id->Confirm("구성을 삭제할까요?","이전 기록에 저장한 품목과 수량은 유지돼요.",{deleting=null}){vm.act{vm.repository.deleteTemplate(id);deleting=null}}}
}
@Composable fun TemplateEditor(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    var t by rememberJsonState("template:$id"){s.templates.find{it.id==id} ?: UsageTemplate(name="",items=emptyList())}
    val original=rememberSaveable(id){codec.encodeToString(t)}
    var colorPicker by rememberSaveable(id){mutableStateOf(false)}
    EditorPage(if(id=="new")"사용 구성 만들기"else"사용 구성 수정","품목별 수량과 대표 색상을 설정해요",codec.encodeToString(t)!=original,back,{
        vm.act("구성을 저장했어요"){vm.repository.template(t.copy(name=t.name.trim(),items=t.items.map{it.copy(batchId=null)}));back()}
    },t.name.isNotBlank() && t.items.isNotEmpty(),busy=vm.busy.collectAsState().value) {
        Paper{OutlinedTextField(t.name,{t=t.copy(name=it)},label={Text("구성 이름")},placeholder={Text("예: 밤 투석 · 1.5 + 2.5")},modifier=Modifier.fillMaxWidth());Hint("기록에서 이 구성을 고르면 저장한 품목·수량이 함께 적용돼요.")}
        Paper {
            Section("대표 색상")
            ColorPalette(s.preferences.palette,t.color){t=t.copy(color=it)}
            TextButton(onClick={colorPicker=!colorPicker}){Text(if(colorPicker)"컬러 피커 접기"else"컬러 피커 · 색 추가")}
            if(colorPicker)InlineColorPicker(t.color){t=t.copy(color=it)}
            Hint("구성 선택과 기록의 구성 이름 옆에 함께 표시돼요.")
        }
        if(s.products.isEmpty())Paper{Hint("품목 관리에서 사용하는 물품을 먼저 등록해 주세요.")}
        ItemQuantityEditor(s,t.items,{t=t.copy(items=it)},batches=false)
    }
}

/** Shared inline editor: no modal and no stock mutation until the owning record is saved. */
@Composable fun ItemQuantityEditor(s:Snapshot,items:List<Item>,onChange:(List<Item>)->Unit,batches:Boolean=false) {
    val stock=remember(s){if(batches)inventory(s)else null}
    s.products.filter{it.active || items.any{line->line.productId==it.id}}.forEach{p->key(p.id){
        val item=items.find{it.productId==p.id}
        var selected by rememberSaveable(p.id){mutableStateOf(item!=null)}
        Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ColorDot(p.color,22,p.name);Text(p.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                Checkbox(selected,{checked->selected=checked;onChange(items.filterNot{it.productId==p.id}+if(checked)listOf(Item(p.id,p.name,1))else emptyList())},Modifier.semantics{contentDescription="${p.name} 사용"})
            }
            if(selected) {
                NumberInput("${p.name} 수량",item?.quantity,{q->onChange(items.filterNot{it.productId==p.id}+if(q!=null && q>0)listOf((item ?: Item(p.id,p.name,1)).copy(quantity=q))else emptyList())},"EA")
                if(item==null)Hint("빈 수량과 0EA는 사용에서 제외해요.")
                if(batches && item!=null) {
                    var expanded by rememberSaveable{mutableStateOf(false)}
                    TextButton(onClick={expanded=!expanded}){Text(if(item.batchId==null)"사용 재고 자동 배정"else"사용 재고 직접 선택됨")}
                    if(expanded) {
                        TextButton(onClick={onChange(items.map{if(it.productId==p.id)it.copy(batchId=null)else it});expanded=false}){Text("자동 배정")}
                        stock?.products?.get(p.id)?.lots?.forEach{lot->TextButton(onClick={onChange(items.map{if(it.productId==p.id)it.copy(batchId=lot.id)else it});expanded=false}){Text("${lot.date} 입고 · ${lot.expiry ?: "기한 무관"} · ${lot.remaining}EA")}}
                    }
                }
            }
        }
    }}
}

@Composable fun ReceiptsScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit,back:()->Unit) {
    var cancelling by rememberSaveable{mutableStateOf<String?>(null)}
    Page("입고 이력","한 번에 들어온 물품을 함께 확인해요",back) {
        Action("+ 일괄 입고 등록",{navigate("receipt/new")},s.products.isNotEmpty())
        if(s.products.isEmpty())Paper{Hint("품목을 먼저 등록해 주세요.")}
        s.receipts.sortedWith(compareByDescending<Receipt>{it.date}.thenByDescending{it.createdAt}).forEach{r->Paper {
            Section("${r.date}${if(r.cancelled)" · 취소됨"else""}")
            r.lines.forEach{line->val p=s.products.find{it.id==line.productId};ProductLine(s,Item(line.productId,p?.name ?: "품목",line.quantity));Hint(line.expiry?.let{"${it}까지"} ?: "사용기한 무관")}
            if(r.memo.isNotEmpty())Hint(r.memo)
            if(!r.cancelled)Row{TextButton(onClick={navigate("receipt/${r.id}")}){Text("수정")};TextButton(onClick={cancelling=r.id}){Text("입고 취소")}}
        }}
    }
    cancelling?.let{id->s.receipts.find{it.id==id}?.let{r->Confirm("입고를 취소할까요?","실제 사용 이력은 유지돼요. 재고가 부족해지면 확인 안내를 표시합니다.",{cancelling=null}){vm.act("입고를 취소했어요"){vm.repository.receipt(r.copy(cancelled=true));cancelling=null}}}}
}
@Composable fun ReceiptEditor(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    var r by rememberJsonState("receipt:$id"){s.receipts.find{it.id==id} ?: Receipt(lines=s.products.filter{it.active}.map{ReceiptLine(productId=it.id,quantity=0)})}
    val original=rememberSaveable(id){codec.encodeToString(r)}
    var add by rememberSaveable{mutableStateOf(false)}
    EditorPage(if(id=="new")"입고 등록"else"입고 수정","받은 날짜와 품목별 EA 수량을 입력해요",codec.encodeToString(r)!=original,back,{
        vm.act("입고 내역을 저장했어요"){vm.repository.receipt(r.copy(lines=r.lines.filter{it.quantity>0}));back()}
    },r.lines.any{it.quantity>0} && r.date<=today(),"함께 저장",vm.busy.collectAsState().value) {
        Paper{DateControl(r.date,{r=r.copy(date=it)},"입고일")}
        if(s.products.isEmpty())Paper{Hint("품목 관리에서 사용하는 물품을 먼저 등록해 주세요.")}
        r.lines.forEach{line->key(line.id){val p=s.products.find{it.id==line.productId}
            Paper {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){ColorDot(p?.color ?: 0xFF647789,22,p?.name);Text(p?.name ?: "품목",Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton(onClick={r=r.copy(lines=r.lines.filterNot{it.id==line.id})}){Icon(Icons.Outlined.Close,"${p?.name} 입고 줄 삭제")}}
                NumberInput("입고 수량",line.quantity.takeIf{it>0},{q->r=r.copy(lines=r.lines.map{if(it.id==line.id)it.copy(quantity=q?:0)else it})},"EA")
                Row(verticalAlignment=Alignment.CenterVertically){Switch(line.expiry!=null,{dated->r=r.copy(lines=r.lines.map{if(it.id==line.id)it.copy(expiry=if(dated)today()else null)else it})});Spacer(Modifier.width(8.dp));Text(if(line.expiry==null)"사용기한 무관"else"사용기한 날짜 지정",Modifier.weight(1f))}
                if(line.expiry!=null)DateControl(line.expiry,{date->r=r.copy(lines=r.lines.map{if(it.id==line.id)it.copy(expiry=date)else it})},"기한")
                TextButton(onClick={r=r.copy(lines=r.lines+ReceiptLine(productId=line.productId,quantity=0))}){Text("이 품목의 다른 사용기한 추가")}
            }
        }}
        Paper {
            TextButton(onClick={add=!add}){Text(if(add)"품목 선택 접기"else"다른 품목 추가")}
            if(add)s.products.forEach{p->MenuRow(p.name){r=r.copy(lines=r.lines+ReceiptLine(productId=p.id,quantity=0));add=false}}
            OutlinedTextField(r.memo,{r=r.copy(memo=it)},label={Text("입고 메모")},modifier=Modifier.fillMaxWidth())
            Hint("빈 수량과 0EA는 저장에서 제외해요.")
        }
    }
}
