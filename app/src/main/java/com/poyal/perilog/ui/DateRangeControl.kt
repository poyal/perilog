@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

// Material date pickers represent calendar dates at midnight UTC, not local instants.
internal fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun pickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
internal fun rangeLabel(from: LocalDate, to: LocalDate): String =
    "${from.toString().replace('-', '.')} ~ ${to.toString().replace('-', '.')} · ${ChronoUnit.DAYS.between(from,to)+1}일"
internal fun inputDate(text:String):LocalDate? {
    if(!text.trim().matches(Regex("\\d{8}|\\d{4}[-./]\\d{2}[-./]\\d{2}")))return null
    val digits=text.filter{it.isDigit()}
    if(digits.length!=8)return null
    return runCatching{LocalDate.of(digits.take(4).toInt(),digits.substring(4,6).toInt(),digits.takeLast(2).toInt())}
        .getOrNull()?.takeIf{it.year in 1900..2100}
}

@Composable fun DateRangeControl(from: LocalDate, to: LocalDate, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    SecondaryButton(onClick, modifier.fillMaxWidth().semantics {
        contentDescription="조회 기간 변경";stateDescription=rangeLabel(from,to)
    }, enabled=enabled, disabledContentColor=MaterialTheme.colorScheme.onSurfaceVariant) {
        Icon(Icons.Outlined.CalendarMonth,null,Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(rangeLabel(from,to),Modifier.weight(1f))
    }
}

/** The draft range lives in the dialog; only Apply commits both endpoints together. */
@Composable fun DateRangeDialog(from: LocalDate, to: LocalDate, onDismiss: () -> Unit,
    onApply: (LocalDate, LocalDate) -> Unit) {
    val configuration=LocalConfiguration.current
    val context=LocalContext.current
    val korean=remember(configuration){Configuration(configuration).apply{setLocale(Locale.KOREA)}}
    val localizedContext=remember(context,korean){context.createConfigurationContext(korean)}
    // Match the rest of this Korean app even when the device language is different.
    CompositionLocalProvider(LocalConfiguration provides korean,LocalContext provides localizedContext,
        LocalResources provides localizedContext.resources) {
        LocalizedDateRangeDialog(from,to,onDismiss,onApply)
    }
}

@Composable private fun LocalizedDateRangeDialog(from:LocalDate,to:LocalDate,onDismiss:()->Unit,
    onApply:(LocalDate,LocalDate)->Unit) {
    val state=rememberDateRangePickerState(
        initialSelectedStartDateMillis=from.pickerMillis(),
        initialSelectedEndDateMillis=to.takeIf{it>=from}?.pickerMillis(),
        initialDisplayedMonthMillis=from.pickerMillis())
    var inputMode by rememberSaveable{mutableStateOf(false)}
    var startText by rememberSaveable{mutableStateOf(from.toString())}
    var endText by rememberSaveable{mutableStateOf(to.toString())}
    // Validate the two raw inputs together. Material 1.4's range input can discard the
    // start date when it temporarily follows the old end date, leaving valid text unselected.
    val start=if(inputMode)inputDate(startText)else state.selectedStartDateMillis?.let(::pickerDate)
    val end=if(inputMode)inputDate(endText)else state.selectedEndDateMillis?.let(::pickerDate)
    val valid=start!=null && end!=null && end>=start
    val configuration=LocalConfiguration.current
    val context=LocalContext.current
    val resources=LocalResources.current
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        // Dialog creates its own Android composition locals; reapply the picker locale inside it.
        CompositionLocalProvider(LocalConfiguration provides configuration,LocalContext provides context,LocalResources provides resources) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().testTag("date-range-dialog")) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onDismiss){Icon(Icons.Outlined.Close,"기간 선택 취소")}
                    Text("기간 선택",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick={
                        if(inputMode) {
                            if(valid)state.setSelection(start!!.pickerMillis(),end!!.pickerMillis())
                        } else {
                            startText=start?.toString().orEmpty();endText=end?.toString().orEmpty()
                        }
                        inputMode=!inputMode
                    }){Icon(if(inputMode)Icons.Outlined.CalendarMonth else Icons.Outlined.Edit,
                        if(inputMode)"달력으로 선택"else"날짜 직접 입력")}
                    SmallButton(onClick={if(valid)onApply(start!!,end!!)},enabled=valid,emphasized=true){Text("적용")}
                }
                if(inputMode)Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("연·월·일 순서로 입력해 주세요. 예: 2026-12-31")
                    OutlinedTextField(startText,{startText=it},Modifier.fillMaxWidth(),label={Text("시작일")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=startText.isNotBlank() && start==null,
                        supportingText={if(startText.isNotBlank() && start==null)Text("1900~2100년의 올바른 날짜를 입력해 주세요.")})
                    OutlinedTextField(endText,{endText=it},Modifier.fillMaxWidth(),label={Text("종료일")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                        isError=endText.isNotBlank() && (end==null || start!=null && end<start),
                        supportingText={
                            if(endText.isNotBlank() && end==null)Text("1900~2100년의 올바른 날짜를 입력해 주세요.")
                            else if(start!=null && end!=null && end<start)Text("종료일은 시작일과 같거나 이후여야 해요.")
                        })
                } else DateRangePicker(state,modifier=Modifier.weight(1f).fillMaxWidth(),title=null,
                    headline={
                        FlowRow(Modifier.padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(start?.toString()?.replace('-','.') ?: "시작일",style=MaterialTheme.typography.titleMedium)
                            Text("~",style=MaterialTheme.typography.titleMedium)
                            Text(end?.toString()?.replace('-','.') ?: "종료일",style=MaterialTheme.typography.titleMedium)
                        }
                    },showModeToggle=false)
            }
        }
        }
    }
}
