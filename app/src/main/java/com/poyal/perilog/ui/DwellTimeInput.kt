@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun DwellTimeInput(value:Int?,onChange:(Int?)->Unit) {
    var open by rememberSaveable{mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val invalid=value!=null && value !in 0..1439
    Text("평균저류시간",style=MaterialTheme.typography.bodyMedium)
    Text(value?.let{"${it/60}시간 ${(it%60).toString().padStart(2,'0')}분"} ?: "미입력",
        style=MaterialTheme.typography.titleLarge,color=if(invalid)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    if(invalid)Text("평균저류시간은 0시간 00분부터 23시간 59분까지 다시 선택하거나 지워 주세요.",color=MaterialTheme.colorScheme.error)
    ButtonRow {
        SmallButton(onClick={focus.clearFocus();keyboard?.hide();open=true}){Text(if(value==null)"시간 선택"else"시간 변경")}
        if(value!=null)SmallButton(onClick={onChange(null)},contentColor=MaterialTheme.colorScheme.error){Text("시간 지우기")}
    }
    if(open)DwellTimeDialog(value,{open=false}){onChange(it);open=false}
}

@Composable private fun DwellTimeDialog(value:Int?,dismiss:()->Unit,apply:(Int)->Unit) {
    // An invalid legacy value is left untouched until the user explicitly applies or clears it.
    val initial=value?.takeIf{it in 0..1439} ?: 0
    val clock=rememberTimePickerState(initialHour=initial/60,initialMinute=initial%60,is24Hour=true)
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp),contentAlignment=Alignment.Center) {
            val availableHeight=maxHeight
            val horizontal=maxWidth>=650.dp && maxHeight<500.dp
            Surface(Modifier.widthIn(max=if(horizontal)840.dp else 560.dp).fillMaxWidth().heightIn(max=availableHeight).testTag("dwell-time-dialog"),
                shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("평균저류시간 선택",style=MaterialTheme.typography.titleLarge)
                    if(horizontal)Row(Modifier.weight(1f,fill=false).fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        // Give the native horizontal dial bounded height so it chooses its compact size.
                        TimePicker(clock,modifier=Modifier.weight(1f),layoutType=TimePickerLayoutType.Horizontal)
                        Column(Modifier.width(140.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Hint("시각이 아닌 걸린 시간을 선택해 주세요.")
                            DwellSelection(clock)
                            DwellActions(dismiss){apply(clock.hour*60+clock.minute)}
                        }
                    } else {
                        Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Hint("시각이 아닌 걸린 시간을 선택해 주세요.")
                            TimePicker(clock,layoutType=TimePickerLayoutType.Vertical)
                            DwellSelection(clock)
                        }
                        DwellActions(dismiss){apply(clock.hour*60+clock.minute)}
                    }
                }
            }
        }
    }
}

@Composable private fun DwellSelection(clock:TimePickerState) {
    Text("${clock.hour}시간 ${clock.minute.toString().padStart(2,'0')}분",Modifier.testTag("dwell-time-selection"))
}

@Composable private fun DwellActions(dismiss:()->Unit,apply:()->Unit) {
    ButtonRow {
        SmallButton(onClick=dismiss){Text("취소")}
        SmallButton(onClick=apply,emphasized=true){Text("적용")}
    }
}
