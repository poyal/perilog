package com.poyal.perilog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

internal fun ComposeTestRule.openTime(label:String) {
    val button=onNodeWithContentDescription("$label 선택")
    runCatching{button.performScrollTo()}
    button.performClick()
}

internal fun ComposeTestRule.setClock(hour:String,minute:String) {
    val fields=onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
    fields[0].performTextReplacement(hour)
    fields[1].performTextReplacement(minute)
}

internal fun ComposeTestRule.chooseTime(label:String,value:String) {
    openTime(label)
    val parts=value.split(':')
    setClock(parts[0],parts[1])
    onNodeWithText("확인").performClick()
}
