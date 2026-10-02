@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDate

@Composable fun StockHistoryScreen(s:Snapshot,vm:JournalViewModel,initialProductId:String?,navigate:(String)->Unit,
    back:()->Unit,openTreatment:(String)->Unit) {
    var type by rememberSaveable{mutableStateOf("전체")}
    var productId by rememberSaveable{mutableStateOf(initialProductId)}
    var period by rememberSaveable{mutableStateOf("전체 기간")}
    var status by rememberSaveable{mutableStateOf("전체 상태")}
    var from by rememberSaveable{mutableStateOf(LocalDate.now().minusDays(29).toString())}
    var to by rememberSaveable{mutableStateOf(today())}
    var choosingProduct by remember{mutableStateOf(false)}
    var cancellingId by rememberSaveable{mutableStateOf<String?>(null)}
    val history=remember(s){stockHistory(s)}
    val selected=s.products.find{it.id==productId}
    val dates=period!="전체 기간"
    val filtered=history.filterStockHistory(StockHistoryType.entries.find{it.label==type},productId,
        if(dates)from else null,if(dates)to else null,when(status){"유효한 내역"->false;"취소된 내역"->true;else->null})
    Page("재고 이력","입고·사용·추가·손실을 한곳에서 확인해요",back) {
        Paper {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SelectionBox("이력 종류",type,listOf("전체")+StockHistoryType.entries.map{it.label},{type=it},Modifier.weight(1f))
                SelectionBox("이력 상태",status,listOf("전체 상태","유효한 내역","취소된 내역"),{status=it},Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Surface(onClick={choosingProduct=true},shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
                        modifier=Modifier.fillMaxWidth().semantics{contentDescription="이력 품목";stateDescription=selected?.name ?: "전체 품목"}) {
                        Column(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text("이력 품목",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                selected?.let{ColorDot(it.color,14,it.name)}
                                Text(selected?.name ?: "전체 품목",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
                                    maxLines=1,overflow=TextOverflow.Ellipsis)
                                Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
                            }
                        }
                    }
                    DropdownMenu(choosingProduct,{choosingProduct=false},modifier=Modifier.heightIn(max=320.dp)) {
                        DropdownMenuItem(text={Text("전체 품목")},onClick={productId=null;choosingProduct=false})
                        s.products.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={productId=p.id;choosingProduct=false},
                            leadingIcon={ColorDot(p.color,18,p.name)})}
                    }
                }
                SelectionBox("이력 기간",period,listOf("전체 기간","7D","30D","기간 지정"),{value->
                    period=value
                    if(value=="7D" || value=="30D") {
                        from=LocalDate.now().minusDays(if(value=="7D")6 else 29).toString();to=today()
                    }
                },Modifier.weight(1f))
            }
            if(period=="기간 지정") {
                DateControl(from,{from=it},"시작");DateControl(to,{to=it},"종료")
            }
            if(dates)Hint(if(from>to)"종료일을 시작일 이후로 선택해 주세요."else"$from ~ $to")
            Hint("취소한 내역도 남아요. 수량 맞추기는 직접 확인한 새 기준 수량입니다.")
        }
        Action("+ 일괄 입고 등록",{navigate("receipt/new")},s.products.isNotEmpty())
        Section("${filtered.size}건")
        if(filtered.isEmpty())Paper{Hint("선택한 조건에 해당하는 재고 이력이 없어요.")}
        filtered.forEach{e->key(e.id){
            Paper(Modifier.testTag("stock-history-${e.id}")) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment=Alignment.CenterVertically) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(10.dp)) {
                        Text(e.type.label,Modifier.padding(horizontal=10.dp,vertical=5.dp),color=MaterialTheme.colorScheme.onPrimaryContainer,fontWeight=FontWeight.SemiBold)
                    }
                    Hint(e.date)
                    if(e.cancelled)Text("취소됨",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e.lines.forEach{line->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        s.products.find{it.id==line.productId}?.let{ColorDot(it.color,20,it.name)}
                        Text(line.name,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(e.quantityLabel(line),fontWeight=FontWeight.Bold,
                            color=if(e.cancelled)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if(e.memo.isNotBlank())Hint(e.memo)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(e.type==StockHistoryType.RECEIPT && !e.cancelled) {
                        TextButton(onClick={navigate("receipt/${e.sourceId}")}){Text("수정")}
                        TextButton(onClick={cancellingId=e.id}){Text("입고 취소")}
                    }
                    if(e.type==StockHistoryType.USAGE) {
                        e.treatmentId?.let{id->TextButton(onClick={openTreatment(id)}){Text("기록 보기")}}
                        if(!e.cancelled)TextButton(onClick={cancellingId=e.id}){Text("사용 취소")}
                    }
                    if(e.type in listOf(StockHistoryType.ADD,StockHistoryType.LOSS) && !e.cancelled)
                        TextButton(onClick={cancellingId=e.id}){Text("조정 취소")}
                }
            }
        }}
    }
    history.find{it.id==cancellingId}?.let{e->
        val title=when(e.type){StockHistoryType.RECEIPT->"입고를 취소할까요?";StockHistoryType.USAGE->"사용을 취소할까요?";else->"조정을 취소할까요?"}
        val quantities=e.lines.joinToString("\n"){"${it.name} · ${e.quantityLabel(it)}"}
        val explanation=when(e.type) {
            StockHistoryType.RECEIPT->"이번 입고의 모든 품목을 취소합니다. 실제 사용 내역은 유지해요."
            StockHistoryType.USAGE->"연결된 물품 사용을 취소해 재고에 반영합니다. 투석 기록은 유지해요."
            else->"이 수량 조정을 취소합니다. 사유와 취소 상태는 이력에 남아요."
        }
        Confirm(title,"$quantities\n\n$explanation",{cancellingId=null}) {
            cancellingId=null;vm.act("${e.type.label} 내역을 취소했어요") {
                when(e.type) {
                    StockHistoryType.RECEIPT->s.receipts.find{it.id==e.sourceId}?.let{vm.repository.receipt(it.copy(cancelled=true))}
                    StockHistoryType.USAGE->vm.repository.cancelUsage(e.sourceId)
                    StockHistoryType.ADD,StockHistoryType.LOSS->s.adjustments.find{it.id==e.sourceId}?.let{vm.repository.adjustment(it.copy(cancelled=true))}
                    StockHistoryType.COUNT->Unit
                }
            }
        }
    }
}
