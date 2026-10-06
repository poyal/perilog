package com.poyal.perilog.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.poyal.perilog.backup.*
import kotlinx.coroutines.flow.first

/** One OS request per installation; subsequent choices belong to system settings. */
@Composable fun FirstNotificationPermission(enabled:Boolean,vm:JournalViewModel) {
    val context=LocalContext.current
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Reminders.schedule(context,vm.state.value.preferences)
    }
    LaunchedEffect(enabled) {
        if(!enabled || context.deviceStore.data.first()[DeviceKeys.notificationRequested]==true)return@LaunchedEffect
        context.deviceStore.edit{it[DeviceKeys.notificationRequested]=true}
        if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if(!vm.state.value.preferences.reminder && Reminders.allowed(context))
            vm.message.emit("알림은 휴대폰의 알림 설정을 따라요. 앱 설정에서 시각을 변경할 수 있어요.")
    }
}

@Composable fun notificationAllowed():Boolean {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var allowed by remember {mutableStateOf(Reminders.allowed(context))}
    DisposableEffect(lifecycle,context) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)allowed=Reminders.allowed(context)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    return allowed
}
