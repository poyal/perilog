package com.poyal.perilog.widget

data class WidgetViewport(val width:Float,val height:Float,val padding:Float,val header:Float,val gap:Float=4f)
fun widgetViewport(width:Float,height:Float,records:Boolean=false):WidgetViewport {
    if(records && height<110) {
        val header=minOf(20f,height*.25f)
        return WidgetViewport((width-8).coerceAtLeast(1f),(height-10-header).coerceAtLeast(1f),4f,header,2f)
    }
    val padding=if(width<150 || height<160) 6f else 8f
    val header=if(height<160) 26f else 30f
    return WidgetViewport((width-padding*2).coerceAtLeast(1f),(height-padding*2-header-4).coerceAtLeast(1f),padding,header)
}

enum class RecordWidgetMode { WIDE, WIDE_TALL, STRIP, MATRIX, STACKED }
data class RecordWidgetLayout(val mode:RecordWidgetMode,val cardWidth:Float,val cardHeight:Float,
    val inset:Float,val titleSize:Float,val stageSize:Float,val labelSize:Float,
    val dateWidth:Float=0f,val inlineDate:Boolean=false,val compact:Boolean=false)

/** Choose geometry from the host's actual content area, never from grid-cell aspect ratios. */
fun recordWidgetLayout(width:Float,height:Float):RecordWidgetLayout {
    val area=widgetViewport(width,height,records=true)
    val wide=width>=260
    val tall=area.height>=170
    if(height<110) {
        val cardWidth=if(wide) (area.width-6)/2 else area.width
        val cardHeight=if(wide) area.height else (area.height-2)/2
        val dateWidth=if(wide) 30f else if(cardWidth<135) 34f else 40f
        val label=if(wide) {if(cardWidth>=220) 9f else 8f} else if(cardWidth<135) 6f else 8f
        val stage=if(wide) minOf(24f,cardHeight-7-label*1.7f) else
            minOf(14f,(cardHeight-2)*.6f,(cardWidth-4-dateWidth)/3-label*2-2)
        return RecordWidgetLayout(if(wide) RecordWidgetMode.STRIP else RecordWidgetMode.MATRIX,cardWidth,cardHeight,
            2f,if(wide && cardHeight>=40) 10f else if(wide) 8f else 7f,stage.coerceAtLeast(4f),label,dateWidth,true,true)
    }
    val mode=if(wide) {if(tall) RecordWidgetMode.WIDE_TALL else RecordWidgetMode.WIDE}
        else if(tall) RecordWidgetMode.STACKED else RecordWidgetMode.MATRIX
    val cardWidth=if(wide) (area.width-6)/2 else area.width
    val cardHeight=when(mode) {
        RecordWidgetMode.STACKED -> (area.height-6)/2
        RecordWidgetMode.MATRIX -> (area.height-27)/2
        else -> area.height
    }
    val inset=if(cardWidth<145) 6f else 9f
    val inline=mode==RecordWidgetMode.MATRIX && cardHeight<32
    val dateWidth=if(inline) 43f else if(cardWidth<135) 32f else 38f
    val stage=when(mode) {
        RecordWidgetMode.WIDE_TALL -> minOf(34f,(cardHeight-inset*2-24)/3-12)
        RecordWidgetMode.MATRIX -> minOf(28f,(cardWidth-dateWidth-12)/3-3,cardHeight-8)
        else -> minOf(34f,(cardWidth-inset*2-2)/3-8,cardHeight-inset*2-46)
    }.coerceAtLeast(8f)
    return RecordWidgetLayout(mode,cardWidth,cardHeight,inset,if(cardWidth<145) 12f else 14f,
        stage,if(mode==RecordWidgetMode.WIDE_TALL) 12f else if(cardWidth<145) 9f else 10f,dateWidth,inline)
}
