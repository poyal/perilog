package com.poyal.perilog

import android.os.Bundle
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

class MainActivity: FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var authMessage by mutableStateOf("")
    private var authenticating=false
    private var backgrounded=false
    private val app get()=application as PerilogApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PerilogTheme("SYSTEM") { Box(Modifier.fillMaxSize()) {
            JournalApp()
            if(!unlocked) Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("나의 하루",style=MaterialTheme.typography.headlineLarge)
                    Text("나의 투석 기록",style=MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(32.dp)); Text(authMessage)
                    Button(onClick={ authenticate() }) { Text("기록 열기") }
                }
            }
        } } }
        lifecycleScope.launch { app.repository.snapshots.collect {
            lockEnabled=it.preferences.lock
            if(lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if(!lockEnabled) unlocked=true
        } }
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
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("나의 투석 기록 열기").setAllowedAuthenticators(authenticators).build())
    }
}
