@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.inventory

@Composable fun StockDetailScreen(s:Snapshot,id:String,navigate:(String)->Unit,back:()->Unit) {
    val p=s.products.find{it.id==id} ?: return
    val stock=inventory(s).products.getValue(id)
    Page("재고 상세",p.name,back) {
        Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ColorDot(p.color,28,p.name);Section(p.name)
            }
            Text(if(stock.registered)"${stock.balance} EA"else"아직 재고를 등록하지 않았어요",style=MaterialTheme.typography.headlineMedium)
            stock.lots.filter{it.remaining>0}.forEach{lot->
                Hint("${lot.date} 재고 · ${lot.remaining}EA")
            }
        }
        Action("수량 추가·차감",{navigate("adjustment/$id")},icon=Icons.Outlined.SwapVert)
        Action("수량 맞추기",{navigate("count/$id")},icon=Icons.Outlined.Balance)
        SecondaryButton(onClick={navigate("stockHistory/$id")},modifier=Modifier.fillMaxWidth()){Text("이 품목 이력")}
    }
}

@Composable fun StockAdjustmentScreen(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    val p=s.products.find{it.id==id} ?: return
    val stock=inventory(s).products.getValue(id)
    var adding by rememberSaveable(id){mutableStateOf(false)}
    var quantity by rememberSaveable(id){mutableStateOf<Int?>(null)}
    var date by rememberSaveable(id){mutableStateOf(today())}
    val initialDate=rememberSaveable(id){date}
    var memo by rememberSaveable(id){mutableStateOf("")}
    val adjustmentId=rememberSaveable(id){newId()}
    var confirm by rememberSaveable{mutableStateOf(false)}
    val valid=quantity?.let{it in 1..100000}==true && memo.isNotBlank() && date<=today()
    val draft=quantity?.takeIf{it in 1..100000}?.let{
        StockAdjustment(id=adjustmentId,productId=id,date=date,delta=if(adding)it else -it,memo=memo.trim())
    }
    EditorPage("수량 추가·차감",p.name,adding || quantity!=null || date!=initialDate || memo.isNotBlank(),back,{confirm=true},
        valid,"조정 저장",vm.busy.collectAsState().value) {
        Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ColorDot(p.color,28,p.name);Section(p.name)
            }
            Text(if(stock.registered)"현재 재고 ${stock.balance} EA"else"아직 재고를 등록하지 않았어요",style=MaterialTheme.typography.titleLarge)
            draft?.let{a->
                val after=inventory(s.copy(adjustments=s.adjustments+a)).products.getValue(id).balance
                Text("적용 후 현재 재고 $after EA",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
            }
        }
        Paper {
            Section("어떻게 변경할까요?")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SelectionChip(!adding,{adding=false},{Text("차감 · 손실")})
                SelectionChip(adding,{adding=true},{Text("추가")})
            }
            DateControl(date,{date=it})
            if(date>today())Hint("오늘 또는 과거 날짜를 선택해 주세요.")
            NumberInput("변경 수량",quantity,{quantity=it},"EA")
            if(quantity!=null && quantity!! !in 1..100000)Hint("1~100,000EA를 입력해 주세요.")
            OutlinedTextField(memo,{memo=it},label={Text("변경 사유 · 필수")},placeholder={Text("예: 포장 손상, 누락 수량 추가")},modifier=Modifier.fillMaxWidth())
            Hint(if(adding)"입력한 개수만 추가해요. 새로 받은 물품은 입고 등록을 이용해 주세요."
                else "입력한 개수만 빼요. 먼저 등록한 재고부터 차감합니다.")
            Hint("기존 입고와 사용 기록은 유지해요. 변경일 이후에 수량을 맞춘 이력이 있으면 그 기준 수량을 따릅니다.")
        }
    }
    if(confirm && draft!=null)Confirm("재고를 ${if(adding)"추가"else"차감"}할까요?","${p.name} · ${quantity}EA ${if(adding)"추가"else"차감"}\n사유: ${memo.trim()}",{confirm=false}) {
        confirm=false;vm.act("재고 조정을 저장했어요"){vm.repository.adjustment(draft);back()}
    }
}
