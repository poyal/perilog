package com.poyal.perilog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun DwellTimeFields(hours:String,minutes:String,onHours:(String)->Unit,onMinutes:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        AdaptivePair(first={
            DwellTimeField("평균저류 시간","평균저류 시간",hours,23,"시",onHours)
        },second={
            DwellTimeField("분","평균저류 분",minutes,59,"분",onMinutes)
        })
        Hint("시간은 0~23, 분은 0~59로 입력해 주세요.")
    }
}

internal fun validDwellPart(text:String,max:Int)=text.isEmpty() ||
    (text.length<=2 && text.all{it in '0'..'9'} && text.toIntOrNull()?.let{it in 0..max}==true)

@Composable private fun DwellTimeField(label:String,description:String,value:String,max:Int,unit:String,onChange:(String)->Unit) {
    val invalid=!validDwellPart(value,max)
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value=value,onValueChange={if(validDwellPart(it,max))onChange(it)},
            placeholder={Text("—")},suffix={Text(unit,style=MaterialTheme.typography.bodyMedium)},isError=invalid,
            supportingText=if(invalid){{Text("0~${max}의 숫자로 수정하거나 지워 주세요.")}}else null,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
            trailingIcon={IconButton(onClick={onChange("")}){Icon(Icons.Outlined.Close,"$description 전체 지우기",Modifier.size(20.dp))}},
            textStyle=MaterialTheme.typography.titleLarge.copy(fontSize=22.sp),shape=RoundedCornerShape(14.dp),
            modifier=Modifier.fillMaxWidth().semantics{contentDescription=description},singleLine=true,
            colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outline,focusedBorderColor=MaterialTheme.colorScheme.primary,
                unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
    }
}
