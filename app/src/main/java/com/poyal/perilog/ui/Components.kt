@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.poyal.perilog.R
import java.time.*
import java.math.BigDecimal
val LocalInputErrors=staticCompositionLocalOf<MutableMap<String,String>>{mutableMapOf()}

private val light=lightColorScheme(primary=Color(0xFF2167B8),onPrimary=Color.White,primaryContainer=Color(0xFFD7E9FB),onPrimaryContainer=Color(0xFF23466D),background=Color(0xFFE7F3FC),
    surface=Color(0xFFFFFCF5),onSurface=Color(0xFF23466D),onBackground=Color(0xFF23466D),
    secondary=Color(0xFF9D4637),secondaryContainer=Color(0xFFFFDDD2),surfaceVariant=Color(0xFFDCEAF5),outline=Color(0xFF647789))
private val dark=darkColorScheme(primary=Color(0xFFA1CAFF),background=Color(0xFF142332),surface=Color(0xFF203142),
    onSurface=Color(0xFFF3EFE6),onBackground=Color(0xFFF3EFE6),secondary=Color(0xFFFFB6A5),surfaceVariant=Color(0xFF34495B))
@Composable fun PerilogTheme(mode: String,content: @Composable ()->Unit) {
    val night=mode=="DARK" || mode=="SYSTEM" && isSystemInDarkTheme()
    MaterialTheme(colorScheme=if(night)dark else light,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
@Composable fun Page(title: String,subtitle: String="",back: (() -> Unit)?=null,actions: @Composable RowScope.()->Unit={},content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=18.dp),verticalAlignment=Alignment.CenterVertically) {
            if(back!=null) IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"뒤로") }
            Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); if(subtitle.isNotEmpty()) Text(subtitle,style=MaterialTheme.typography.bodyMedium) }
            actions()
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable fun Paper(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun Section(text: String) { Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
@Composable fun Hint(text: String) { Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Action(text: String,onClick: () -> Unit,enabled: Boolean=true) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled) { Text(text) } }
@Composable fun Bow(size: Int=54) { Image(painterResource(R.drawable.journal_bow),"나비 리본 기록장",Modifier.size(size.dp).clip(RoundedCornerShape(16.dp))) }
@Composable fun ColorDot(color: Long) { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(color)).border(1.dp,MaterialTheme.colorScheme.outline,CircleShape)) }
@Composable fun DateControl(date: String,onChange: (String)->Unit,label: String="날짜") {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        OutlinedButton(onClick={open=true},Modifier.weight(1f)) { Text("$label $date") }
        TextButton(onClick={onChange(LocalDate.now().toString())}) { Text("오늘") }
        TextButton(onClick={onChange(LocalDate.now().minusDays(1).toString())}) { Text("어제") }
    }
    if(open) {
        val state=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) };open=false}){Text("선택")}},dismissButton={TextButton(onClick={open=false}){Text("취소")}}) { DatePicker(state) }
    }
}
@Composable fun NumberInput(label: String,value: Int?,onChange: (Int?)->Unit,unit: String,scale: Int=1,steps: List<Int> = emptyList(),signed: Boolean=false) {
    var text by remember(value) { mutableStateOf(value?.let { BigDecimal(it).divide(BigDecimal(scale)).stripTrailingZeros().toPlainString() } ?: "") }
    var invalid by remember { mutableStateOf(false) }
    val errors=LocalInputErrors.current
    val errorId=remember{java.util.UUID.randomUUID().toString()}
    DisposableEffect(errorId) { onDispose { errors.remove(errorId) } }
    OutlinedTextField(value=text,onValueChange={ entered ->
        if(entered.matches(Regex(if(signed) "-?[0-9]*([.,][0-9]*)?" else "[0-9]*([.,][0-9]*)?"))) {
            text=entered
            val decimal=entered.replace(',','.').toBigDecimalOrNull()
            val converted=try { decimal?.multiply(BigDecimal(scale))?.intValueExact() } catch(_: ArithmeticException){ null }
            invalid=entered.isNotBlank() && converted==null
            if(invalid)errors[errorId]=label else errors.remove(errorId)
            onChange(converted)
        }
    },label={Text(label)},suffix={Text(unit)},isError=invalid,supportingText=if(invalid){{Text("숫자와 소수 자릿수를 확인해 주세요")}}else null,
        keyboardOptions=KeyboardOptions(keyboardType=if(scale>1) KeyboardType.Decimal else KeyboardType.Number),
        trailingIcon={IconButton(onClick={text="";invalid=false;errors.remove(errorId);onChange(null)}){Icon(Icons.Outlined.Close,"$label 전체 지우기")}},
        modifier=Modifier.fillMaxWidth(),singleLine=true)
    if(steps.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        (steps.reversed().map{-it}+steps).forEach { step ->
            OutlinedButton(onClick={invalid=false;errors.remove(errorId);onChange(((value?:0)+step).coerceAtLeast(if(signed)Int.MIN_VALUE else 0))},contentPadding=PaddingValues(horizontal=12.dp)) {
                Text((if(step>0)"+"else "")+BigDecimal(step).divide(BigDecimal(scale)).stripTrailingZeros().toPlainString())
            }
        }
    }
}
@Composable fun Confirm(title: String,text: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=onConfirm){Text("확인")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
