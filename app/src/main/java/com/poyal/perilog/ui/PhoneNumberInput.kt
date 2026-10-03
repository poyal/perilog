package com.poyal.perilog.ui

import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation

private fun phoneSeparator(c:Char)=c.isWhitespace() || c in "-()."
internal fun phoneInputDigits(phone:String)=phone.filterNot(::phoneSeparator)

/** Keep every digit and a leading country code; formatting must never change the recipient. */
internal fun formattedPhone(phone:String):String {
    val raw=phoneInputDigits(phone)
    if(!raw.matches(Regex("\\+?[0-9]+")))return raw
    if(raw.startsWith("050") && raw.length==12)
        return "${raw.take(4)}-${raw.substring(4,8)}-${raw.takeLast(4)}"
    val formatted=PhoneNumberUtils.formatNumber(raw,"KR")
    if(formatted!=null && phoneInputDigits(formatted)==raw && formatted.any(::phoneSeparator))
        return formatted.replace(' ','-')
    if(raw.startsWith('+'))return raw
    // The platform formatter can leave an unfinished number ungrouped.
    val first=when {
        raw.startsWith("02") -> 2
        raw.startsWith("050") && raw.length>=12 -> 4
        raw.startsWith('0') && raw.length<=11 -> 3
        raw.startsWith('1') && raw.length in 5..8 -> 4
        else -> return raw
    }
    val service=raw.startsWith('1')
    val middle=if(first==4 || raw.startsWith("010") || raw.startsWith("070") || raw.length>=first+8)4 else 3
    return buildString {
        raw.forEachIndexed { index,c->
            if(index==first || !service && index==first+middle)append('-')
            append(c)
        }
    }
}

internal val phoneInputTransformation=InputTransformation {
    // Remove pasted separators as individual edits so the cursor stays with the edited digits.
    for(index in length-1 downTo 0)if(phoneSeparator(asCharSequence()[index]))replace(index,index+1,"")
}

internal val phoneOutputTransformation=OutputTransformation {
    val raw=asCharSequence().toString()
    val display=formattedPhone(raw)
    // Output-only separators use Compose's cursor/selection mapping and are never stored in state.
    if(phoneInputDigits(display)==raw)display.forEachIndexed { index,c->
        if(phoneSeparator(c))replace(index,index,c.toString())
    }
}
