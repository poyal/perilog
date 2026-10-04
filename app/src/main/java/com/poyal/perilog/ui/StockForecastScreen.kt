@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.poyal.perilog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.LocalDateTime

private fun forecastHeading(pattern: UsagePattern) =
    if(pattern.mode=="HISTORY") "지금과 같은 사용 추세라면" else "지정한 사용 구성대로라면"

private fun SupplyAmount.forecastLabel(): String =
    if(numerator>0 && label()=="0") "0.1 미만" else label()

private fun StockForecastLine.balanceLabel(): String = when {
    currentStock==null -> "현재 재고를 등록하면 계산할 수 있어요"
    balance==null -> "사용량을 설정하면 계산할 수 있어요"
    balance.numerator<0 -> "방문 전 약 ${shortage!!.forecastLabel()} EA 부족 예상"
    balance.numerator==0L -> "방문일까지 사용하면 남는 수량이 없을 예상"
    else -> "방문일에 약 ${balance.forecastLabel()} EA 남을 예상"
}

@Composable private fun ForecastStatusChip(label: String, icon: ImageVector,
    background: Color, foreground: Color) {
    Surface(shape=RoundedCornerShape(50),color=background,contentColor=foreground) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,contentDescription=null,modifier=Modifier.size(16.dp))
            Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable internal fun AppointmentStockSummary(s: Snapshot, appointment: Appointment,
    now: LocalDateTime, navigate: (String)->Unit) {
    val day=now.toLocalDate().toString()
    val attempt=remember(s,appointment.date,day) { runCatching {forecastStock(s,appointment.date,day)} }
    val result=attempt.getOrNull()
    val colors=MaterialTheme.colorScheme
    HorizontalDivider()
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Outlined.Inventory2,contentDescription=null,modifier=Modifier.size(20.dp),tint=colors.primary)
        Text("예약일 기준 재고",style=MaterialTheme.typography.titleMedium)
    }
    if(result==null) Hint(attempt.exceptionOrNull()?.message ?: "예상 재고를 계산할 수 없어요.")
    else {
        val shortage=result.lines.filter { (it.balance?.numerator ?: 0)<0 }
        val unknown=result.lines.count {it.balance==null}
        val empty=result.lines.count {it.balance?.numerator==0L}
        val remaining=result.lines.count {(it.balance?.numerator ?: 0)>0}
        if(result.lines.isEmpty()) Hint("품목을 등록하면 예상 재고를 볼 수 있어요")
        else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(shortage.isNotEmpty()) ForecastStatusChip("부족 예상 ${shortage.size}품목",Icons.Outlined.WarningAmber,
                colors.errorContainer,colors.onErrorContainer)
            if(empty>0) ForecastStatusChip("잔량 0 · ${empty}품목",Icons.Outlined.RemoveCircleOutline,
                colors.secondaryContainer,colors.onSecondaryContainer)
            if(remaining>0) ForecastStatusChip("잔량 있음 ${remaining}품목",Icons.Outlined.CheckCircle,
                colors.tertiaryContainer,colors.onTertiaryContainer)
            if(unknown>0) ForecastStatusChip("미확인 ${unknown}품목",Icons.Outlined.HelpOutline,
                colors.surfaceVariant,colors.onSurfaceVariant)
        }
        val visibleLines=result.lines.filter {it.balance!=null}.sortedBy {
            when {it.balance!!.numerator<0 -> 0;it.balance.numerator==0L -> 1;else -> 2}
        }.take(2)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            visibleLines.forEach {line ->
                val balance=line.balance!!
                val (background,foreground)=when {
                    balance.numerator<0 -> colors.errorContainer to colors.onErrorContainer
                    balance.numerator==0L -> colors.secondaryContainer to colors.onSecondaryContainer
                    else -> colors.tertiaryContainer to colors.onTertiaryContainer
                }
                Surface(shape=RoundedCornerShape(16.dp),color=background.copy(alpha=0.5f),contentColor=foreground,
                    border=BorderStroke(1.dp,foreground.copy(alpha=0.18f))) {
                    Column(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(line.name,style=MaterialTheme.typography.labelLarge)
                        Text(when {
                            balance.numerator<0 -> "약 ${line.shortage!!.forecastLabel()} EA 부족"
                            balance.numerator==0L -> "남는 수량 없음"
                            else -> "약 ${balance.forecastLabel()} EA 남음"
                        },style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        if(unknown==result.lines.size && unknown>0) Hint("재고와 사용량을 확인하면 예상할 수 있어요")
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(forecastHeading(s.preferences.stockForecastPattern),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            if(result.pattern.mode=="HISTORY") Text("최근 28일 중 ${result.evidence.days}일 확정 기록 · 예약일 사용 전 예상",
                style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            else Text("예약일 사용 전 예상",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
    }
    TextButton(onClick={navigate("appointmentStock/${appointment.id}")},modifier=Modifier.testTag("forecast-${appointment.id}")) {Text("예상 잔량 계산 보기")}
}

@Composable fun StockForecastScreen(s: Snapshot, vm: JournalViewModel, id: String,
    now: LocalDateTime, navigate: (String)->Unit, back: ()->Unit) {
    val appointment=s.appointments.find {it.id==id}
    var editing by rememberSaveable {mutableStateOf(false)}
    val day=now.toLocalDate()
    val attempt=remember(s,appointment?.date,day) {runCatching {
        requireNotNull(appointment) {"삭제된 병원 일정이에요."}
        forecastStock(s,appointment.date,day.toString())
    }}
    val result=attempt.getOrNull()
    if(editing) {
        CompositionLocalProvider(LocalHelpAction provides {navigate("guide/request-patterns")}) {
            UsagePatternEditor(s,s.preferences.stockForecastPattern,
                result?.evidence?.days?.let {it>0} ?: false,{editing=false}) {pattern ->
                vm.act("병원 일정의 예상 사용량을 저장했어요") {
                    vm.repository.stockForecastPattern(pattern);editing=false
                }
            }
        }
        return
    }
    Page("방문일 예상 잔량",appointment?.date ?: "병원 일정",back) {
        if(appointment==null || appointment.endsAt().isBefore(now)) {
            Paper {Hint(if(appointment==null) "삭제된 병원 일정이에요." else "지난 일정에는 현재 사용 추세로 잔량을 예상하지 않아요.")}
        } else {
            Paper {
                Section(forecastHeading(s.preferences.stockForecastPattern))
                Hint("${appointment.date} 방문일 사용 전 기준 · ${day} 현재 재고에서 예상해요.")
                if(s.preferences.stockForecastPattern.mode=="HISTORY") {
                    Hint("최근 28일 중 ${result?.evidence?.days ?: 0}일의 확정 사용 기록으로 계산해요. 기록 없는 날은 평균에서 제외해요.")
                    result?.let {Hint("참고 기간 ${it.historyFrom} ~ ${it.historyTo}")}
                } else Hint("병원 일정 전체에 공통으로 정한 사용 구성을 적용해요.")
                SecondaryButton(onClick={editing=true}) {Text("평소 사용 바꾸기")}
                Hint("아직 받지 않은 요청 물품은 더하지 않아요. 실제 사용이나 입고가 달라지면 예상도 바뀌어요.")
            }
            if(result==null) Paper {Hint(attempt.exceptionOrNull()?.message ?: "예상 재고를 계산할 수 없어요.")}
            else {
                if(result.lines.isEmpty()) Paper {Hint("사용하는 품목을 먼저 등록해 주세요.");Action("품목 등록하기",{navigate("products")})}
                result.lines.forEach { line -> key(line.productId) {
                    Paper(Modifier.testTag("forecast-line-${line.productId}")) {
                        Section(line.name)
                        QuantitySummary("현재 재고",line.currentStock?.let {"$it EA"} ?: "미등록")
                        QuantitySummary("하루 평균 사용량",line.dailyUse?.let {"약 ${it.forecastLabel()} EA"} ?: "사용량 미확인")
                        QuantitySummary("방문 전까지 계산 기간","${result.days}일")
                        QuantitySummary("앞으로 사용할 예상량",line.expectedUse?.let {"약 ${it.forecastLabel()} EA"} ?: "사용량 미확인")
                        Text(line.balanceLabel(),color=if((line.balance?.numerator ?: 0)<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if(result.days==0) Hint("오늘 방문이므로 현재 재고를 표시해요.")
                        else Hint("오늘 이미 기록한 사용은 다시 빼지 않아요. 방문 전날까지의 남은 사용을 예상해요.")
                        if(line.currentStock==null) TextButton(onClick={navigate("count/${line.productId}")}) {Text("현재 재고 등록")}
                    }
                } }
            }
        }
    }
}
