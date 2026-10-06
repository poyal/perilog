package com.poyal.perilog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay

/** An overlay host: its size never contributes to the screen's content insets. */
@Composable
fun TopSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = hostState.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(current) {
        if (current != null && current.visuals.duration != SnackbarDuration.Indefinite) {
            val timeout = when (current.visuals.duration) {
                SnackbarDuration.Short -> 4_000L
                SnackbarDuration.Long -> 10_000L
                SnackbarDuration.Indefinite -> error("Indefinite snackbars do not time out")
            }
            delay(accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = timeout,
                containsIcons = true,
                containsText = true,
                containsControls = current.visuals.actionLabel != null || current.visuals.withDismissAction
            ) ?: timeout)
            current.dismiss()
        }
    }
    AnimatedContent(
        targetState = current,
        modifier = modifier.fillMaxWidth().clipToBounds(),
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            ((slideInVertically(tween(200)) { -it } + fadeIn(tween(200))) togetherWith
                (slideOutVertically(tween(150)) { -it } + fadeOut(tween(150))))
                .using(null)
        },
        label = "Top snackbar"
    ) { data ->
        if (data != null) {
            // AnimatedContent retains the old card during exit. It must neither announce
            // itself again nor run an old action after cancellation or replacement.
            val active = data === current
            Box(if (active) Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                paneTitle = "알림"
                dismiss { data.dismiss(); true }
            } else Modifier.clearAndSetSemantics {}) {
                Snackbar(
                    action = data.visuals.actionLabel?.let { label ->
                        { TextButton(onClick = { if (active) data.performAction() }, enabled = active,
                            colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionContentColor)
                        ) { Text(label) } }
                    },
                    dismissAction = if (data.visuals.withDismissAction) {
                        { IconButton(onClick = { if (active) data.dismiss() }, enabled = active) {
                            Icon(Icons.Default.Close, contentDescription = "알림 닫기")
                        } }
                    } else null,
                    actionOnNewLine = data.visuals.actionLabel != null
                ) { Text(data.visuals.message) }
            }
        }
    }
}
