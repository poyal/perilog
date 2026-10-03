package com.poyal.perilog

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poyal.perilog.ui.*
import kotlinx.coroutines.launch
import com.poyal.perilog.widget.*
import com.poyal.perilog.data.codec
import kotlinx.serialization.encodeToString

class MainActivity: FragmentActivity() {
    private var themeMode by mutableStateOf("SYSTEM")
    private var unlocked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var authMessage by mutableStateOf("")
    private var authenticating=false
    private var backgrounded=false
    private var widgetRequest by mutableStateOf<WidgetOpenRequest?>(null)
    private val app get()=application as PerilogApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetRequest=if(savedInstanceState==null)WidgetNavigation.read(intent)
            else savedInstanceState.getString("widget_request")?.let { runCatching {codec.decodeFromString<WidgetOpenRequest>(it)}.getOrNull() }
        // Android 15 can retain the old content-insets applier when reusing a
        // decor view. Reset it before enableEdgeToEdge obtains that view;
        // afterwards edge-to-edge enforcement makes this setter a no-op.
        if(savedInstanceState!=null && Build.VERSION.SDK_INT==35) {
            WindowCompat.setDecorFitsSystemWindows(window,false)
        }
        enableEdgeToEdge()
        setContent { PerilogTheme(themeMode) { Box(Modifier.fillMaxSize()) {
            JournalApp(unlocked = unlocked,widgetRequest=widgetRequest,onWidgetHandled={widgetRequest=null})
            if(!unlocked) Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(getString(R.string.app_name),style=MaterialTheme.typography.headlineLarge)
                    Text(getString(R.string.app_description),style=MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(32.dp)); Text(authMessage)
                    Button(onClick={ authenticate() }) { Text("기록 열기") }
                }
            }
        } } }
        lifecycleScope.launch { app.repository.snapshots.collect {
            themeMode=it.preferences.darkMode
            lockEnabled=it.preferences.lock
            val dark=it.preferences.darkMode=="DARK" || (it.preferences.darkMode=="SYSTEM" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)
            WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark }
            if(lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if(!lockEnabled) unlocked=true
        } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetNavigation.read(intent)?.let {widgetRequest=it}
    }
    override fun onSaveInstanceState(outState: Bundle) {
        widgetRequest?.let {outState.putString("widget_request",codec.encodeToString(it))}
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            lockEnabled=app.repository.snapshot().preferences.lock
            if(!lockEnabled) unlocked=true else if(!unlocked) authenticate()
            runCatching{app.backup.automatic()}
        }
    }
    override fun onStop() { super.onStop(); if(lockEnabled && !isChangingConfigurations && !authenticating) { unlocked=false; backgrounded=true } }
    private fun authenticate() {
        if(!lockEnabled) { unlocked=true; return }
        if(authenticating) return
        val authenticators=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if(BiometricManager.from(this).canAuthenticate(authenticators)!=BiometricManager.BIOMETRIC_SUCCESS) {
            authMessage="휴대폰 설정에서 화면 잠금을 설정한 뒤 다시 열어 주세요."; return
        }
        authenticating=true
        val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authenticating=false; unlocked=true; authMessage="" }
            override fun onAuthenticationError(errorCode: Int,errString: CharSequence) { authenticating=false; authMessage=errString.toString() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_title)).setAllowedAuthenticators(authenticators).build())
    }
}
