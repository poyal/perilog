package com.poyal.perilog.domain

import com.poyal.perilog.data.*

enum class StockHistoryType(val label:String) {
    RECEIPT("입고"),USAGE("사용"),ADD("추가"),LOSS("손실"),COUNT("수량 맞추기")
}
data class StockHistoryLine(val productId:String,val name:String,val quantity:Int)
data class StockHistoryEntry(val sourceId:String,val type:StockHistoryType,val date:String,val createdAt:Long,
    val lines:List<StockHistoryLine>,val memo:String="",val cancelled:Boolean=false,val treatmentId:String?=null,
    val description:String="") {
    val id:String get()="${type.name}:$sourceId"
    fun quantityLabel(line:StockHistoryLine):String=when(type) {
        StockHistoryType.COUNT->"기준 ${line.quantity} EA"
        StockHistoryType.USAGE,StockHistoryType.LOSS->"−${line.quantity} EA"
        else->"+${line.quantity} EA"
    }
}

fun stockHistory(s:Snapshot):List<StockHistoryEntry> {
    val products=s.products.associateBy{it.id}
    fun line(id:String,q:Int,name:String="품목")=StockHistoryLine(id,products[id]?.name ?: name,q)
    return buildList {
        s.receipts.forEach{r->add(StockHistoryEntry(r.id,StockHistoryType.RECEIPT,r.date,r.createdAt,
            r.lines.groupBy{it.productId}.map{(id,lines)->line(id,lines.sumOf{it.quantity})},r.memo,r.cancelled))}
        s.usages.filter{it.items.isNotEmpty()}.forEach{u->
            val t=s.treatments.find{it.id==u.id}
            val description=listOfNotNull(if(u.kind=="MACHINE")"기계투석"else"추가투석",t?.compositionName(s),
                if(t==null)"연결된 투석 기록이 없어요."else null).joinToString(" · ")
            add(StockHistoryEntry(u.id,StockHistoryType.USAGE,u.date,u.createdAt,
                u.items.map{line(it.productId,it.quantity,it.name)},t?.memo.orEmpty(),u.cancelled,t?.id,description))
        }
        s.adjustments.forEach{a->add(StockHistoryEntry(a.id,if(a.delta>0)StockHistoryType.ADD else StockHistoryType.LOSS,
            a.date,a.createdAt,listOf(line(a.productId,kotlin.math.abs(a.delta))),a.memo,a.cancelled))}
        s.counts.forEach{c->add(StockHistoryEntry(c.id,StockHistoryType.COUNT,c.date,c.createdAt,listOf(line(c.productId,c.quantity)),c.memo))}
    }.sortedWith(compareByDescending<StockHistoryEntry>{it.date}.thenByDescending{it.createdAt}.thenBy{it.id})
}

fun List<StockHistoryEntry>.filterStockHistory(type:StockHistoryType?=null,productId:String?=null,
    from:String?=null,to:String?=null,cancelled:Boolean?=null):List<StockHistoryEntry> =
    filter{e->(type==null || e.type==type) && (cancelled==null || e.cancelled==cancelled) &&
        (from==null || e.date>=from) && (to==null || e.date<=to) && (productId==null || e.lines.any{it.productId==productId})}
        .map{e->if(productId==null)e else e.copy(lines=e.lines.filter{it.productId==productId})}
