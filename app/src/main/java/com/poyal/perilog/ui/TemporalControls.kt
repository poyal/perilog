@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.poyal.perilog.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.poyal.perilog.domain.validAppointmentTime
import java.time.LocalTime
import java.util.Locale

/** The value stays in the button; editing belongs to the picker, not the form. */
@Composable fun SelectionFieldRow(label:String,valueText:String,icon:ImageVector,onClick:()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,description:String="$label 선택",
    onClear:(()->Unit)?=null,errorText:String?=null,stacked:Boolean=false) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth=maxWidth
            val vertical=stacked || availableWidth<300.dp || LocalDensity.current.fontScale>1.25f
            val buttons:@Composable ()->Unit={
                ButtonRow {
                    SmallButton(onClick,enabled=enabled,modifier=Modifier
                        .then(if(stacked)Modifier.fillMaxWidth()else Modifier.widthIn(max=availableWidth))
                        .semantics{contentDescription=description;stateDescription=valueText}) {
                        Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))
                        Text(valueText,Modifier.weight(1f,fill=false))
                        Spacer(Modifier.width(6.dp));Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp))
                    }
                    if(onClear!=null)IconButton(onClick=onClear,enabled=enabled) {
                        Icon(Icons.Outlined.Close,"$label 해제",tint=MaterialTheme.colorScheme.error)
                    }
                }
            }
            if(vertical)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label,style=MaterialTheme.typography.titleSmall);buttons()
            } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                // Reserve room for the label even when the value or optional clear action is long.
                Box(Modifier.widthIn(max=availableWidth*.72f)){buttons()}
            }
        }
        if(errorText!=null)Text(errorText,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
    }
}

/** Scroll the body while keeping confirmation reachable above the keyboard. */
@Composable internal fun SelectionDialog(title:String,onDismiss:()->Unit,onConfirm:(()->Unit)?=null,
    confirmEnabled:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    val configuration=LocalConfiguration.current
    val context=LocalContext.current
    val density=LocalDensity.current
    val korean=remember(configuration){Configuration(configuration).apply{setLocale(Locale.KOREA)}}
    val localized=remember(context,korean){context.createConfigurationContext(korean)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        CompositionLocalProvider(LocalConfiguration provides korean,LocalContext provides localized,LocalResources provides localized.resources,LocalDensity provides density) {
            BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(12.dp),contentAlignment=Alignment.Center) {
                Surface(Modifier.widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight),shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(title,style=MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
                        ButtonRow(Modifier.align(Alignment.End)) {
                            SmallButton(onClick=onDismiss){Text("취소")}
                            if(onConfirm!=null)SmallButton(onClick=onConfirm,enabled=confirmEnabled,emphasized=true){Text("확인")}
                        }
                    }
                }
            }
        }
    }
}

@Composable fun TimeControl(label:String,value:String?,onChange:(String?)->Unit,
    initialTime:String="09:00",clearable:Boolean=false) {
    var open by rememberSaveable{mutableStateOf(false)}
    val invalid=!value.isNullOrBlank() && !validAppointmentTime(value)
    SelectionFieldRow(label,value?.takeIf{it.isNotBlank()} ?: "시간 선택",Icons.Outlined.Schedule,{open=true},
        description="$label 선택",onClear=if(clearable && value!=null){{onChange(null)}}else null,
        errorText=if(invalid)"00:00~23:59의 시각을 다시 선택해 주세요."else null)
    if(open) {
        val initial=runCatching{LocalTime.parse(value?.takeIf{validAppointmentTime(it)} ?: initialTime)}.getOrDefault(LocalTime.of(9,0))
        val clock=rememberTimePickerState(initialHour=initial.hour,initialMinute=initial.minute,is24Hour=true)
        SelectionDialog(label,{open=false},{
            onChange(String.format(Locale.US,"%02d:%02d",clock.hour,clock.minute));open=false
        }) {Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){TimeInput(clock)}}
    }
}

@Composable fun DurationControl(value:Int?,onChange:(Int?)->Unit) {
    var open by rememberSaveable{mutableStateOf(false)}
    SelectionFieldRow("평균저류시간",value?.let{"${it/60}시간 ${it%60}분"} ?: "시간 선택",Icons.Outlined.Timer,{open=true},
        onClear=if(value!=null){{onChange(null)}}else null,
        errorText=if(value!=null && value !in 0..1439)"0~23시간 59분으로 수정하거나 해제해 주세요."else null)
    if(open) {
        // Keep raw text above the dialog's composition so blanks and leading zeroes restore too.
        var hours by rememberSaveable{mutableStateOf(value?.div(60)?.toString().orEmpty())}
        var minutes by rememberSaveable{mutableStateOf(value?.rem(60)?.toString().orEmpty())}
        val valid=validDwellPart(hours,23) && validDwellPart(minutes,59)
        SelectionDialog("평균저류시간",{open=false},{
            onChange(if(hours.isEmpty() && minutes.isEmpty())null else (hours.toIntOrNull() ?: 0)*60+(minutes.toIntOrNull() ?: 0))
            open=false
        },valid) {DwellTimeFields(hours,minutes,{hours=it},{minutes=it})}
    }
}
