package com.poyal.perilog.ui

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.poyal.perilog.calendar.*
import com.poyal.perilog.data.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun CalendarSettingsScreen(vm: JournalViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val runtime = vm.app.calendar
    val store = runtime.store
    val connections by store.dao.observeConnections().collectAsState(initial = emptyList())
    val jobs by store.dao.observeJobs().collectAsState(initial = emptyList())
    val snapshot by vm.state.collectAsState()
    val connection = connections.firstOrNull {it.state != CalendarConnection.OFF}
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<CalendarTarget>>(emptyList()) }
    var selected by remember { mutableStateOf<CalendarTarget?>(null) }
    var includeMemo by rememberSaveable { mutableStateOf(false) }
    var googleAccount by rememberSaveable { mutableStateOf("") }
    var resumeId by rememberSaveable { mutableStateOf<String?>(null) }
    var disconnecting by rememberSaveable { mutableStateOf(false) }
    var deleteEvents by rememberSaveable { mutableStateOf(false) }
    var fullApply by rememberSaveable { mutableStateOf(false) }
    var freshLink by rememberSaveable { mutableStateOf<String?>(null) }
    var acknowledgeRestore by rememberSaveable { mutableStateOf(false) }
    var restoreCheck by remember { mutableStateOf(false) }

    fun perform(block: suspend () -> Unit) {
        if(working) return
        working = true; error = ""
        scope.launch {
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error = when(e) {
                is CalendarAccessException, is CalendarConflictException, is CalendarRetryException, is IllegalArgumentException -> e.message ?: "연결을 확인해 주세요."
                is SecurityException -> "캘린더 권한을 허용해 주세요."
                else -> "캘린더 연결을 완료하지 못했어요. 구글 연결은 계정 권한과 앱의 연결 설정을 확인해 주세요."
            } } finally { working = false }
        }
    }
    suspend fun authorized(provider: String, account: String = "") {
        val id = resumeId
        if(id != null) {
            val c = requireNotNull(store.dao.connection(id))
            runtime.gatewayFactory(c).verify(c)
            store.resume(id); runtime.request(c.provider); resumeId = null
        } else {
            targets = runtime.gatewayFactory(CalendarConnection(provider = provider, account = account,
                accountType = if(provider == CalendarConnection.GOOGLE) "com.google" else "", calendarId = "", calendarName = "")).targets()
            if(targets.isEmpty()) error = "쓰기 가능한 캘린더가 없어요. 휴대폰 또는 구글 캘린더에서 캘린더를 준비해 주세요."
            restoreCheck = store.dao.deviceState()?.restored == true
        }
    }
    suspend fun googleResult(result: AuthorizationResult) {
        runtime.auth.token(result)
        authorized(CalendarConnection.GOOGLE, googleAccount)
    }
    val googleResolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode != Activity.RESULT_OK) {error = "구글 연결을 취소했어요."; resumeId = null}
        else perform {googleResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data))}
    }
    fun authorizeGoogle() = perform {
        val result = runtime.auth.authorize(googleAccount)
        if(result.hasResolution()) googleResolution.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent)).build())
        else googleResult(result)
    }
    val chooseGoogle = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if(result.resultCode == Activity.RESULT_OK && !account.isNullOrBlank()) {googleAccount = account; authorizeGoogle()}
        else error = "구글 계정 선택을 취소했어요."
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if(granted.size == 2 && granted.values.all {it}) perform {authorized(CalendarConnection.DEVICE)}
        else {error = "캘린더 읽기·쓰기 권한이 필요해요. 거절해도 병원 예약은 앱에 그대로 저장됩니다."; resumeId = null}
    }
    fun devicePermission() = permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))

    Page("캘린더 연동", "페리로그에서 변경한 예약을 캘린더에 반영해요", back) {
        Paper {Section("페리로그 → 캘린더"); Hint("병원 방문 하나를 일정 하나로 보내요. 캘린더에서 수정·삭제한 내용은 페리로그에 가져오지 않아요.")}
        if(working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotEmpty()) Paper {Text(error, color = MaterialTheme.colorScheme.error)}
        if(connection == null) {
            Paper {
                Section("연결 방식 선택")
                Hint("구글 직접 연결 또는 휴대폰 캘린더 중 하나를 선택해요.")
                Action("휴대폰 캘린더 연결", {resumeId = null; targets = emptyList(); devicePermission()}, enabled = !working)
                SecondaryButton(onClick = {
                    resumeId = null; targets = emptyList()
                    try {chooseGoogle.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))}
                    catch(_: android.content.ActivityNotFoundException) {error = "이 기기에서는 구글 계정을 선택할 수 없어요. 휴대폰 캘린더 연결을 이용해 주세요."}
                }, enabled = !working, modifier = Modifier.fillMaxWidth()) {Text("구글 계정으로 연결", textAlign = TextAlign.Center)}
                Hint("구글 직접 연결은 인터넷과 Google Play 서비스가 필요해요.")
            }
            if(connections.isNotEmpty()) Paper {Hint("이전에 남긴 캘린더 일정이 있을 수 있어요. 다른 방식이나 캘린더로 연결하기 전에 기존 일정을 확인해 주세요.")}
            if(targets.isNotEmpty()) Paper {
                Section("캘린더 선택")
                Hint("일정을 보낼 캘린더를 눌러 주세요.")
                targets.forEach { target -> SecondaryButton(onClick = {selected = target; acknowledgeRestore = false}, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(24.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(target.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        if(target.account != target.name) Text(target.account.ifBlank {"휴대폰 전용"},
                            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(24.dp))
                } }
            }
        } else {
            val c = connection
            val pending = jobs.filter {it.connectionId == c.id}
            Paper {
                Section(c.calendarName)
                Hint("${if(c.provider == CalendarConnection.GOOGLE) "구글 직접 연결" else "휴대폰 캘린더"} · ${c.account.ifBlank {"휴대폰 전용"}}")
                Text(when(c.state) {
                    CalendarConnection.PAUSED -> "복원·초기화 후 일시 정지"
                    CalendarConnection.AUTH -> "다시 연결 필요"
                    CalendarConnection.CLEANING -> "보낸 일정 정리 중"
                    else -> if(pending.isEmpty()) "반영 완료" else "반영 대기"
                })
                Hint("대기 ${pending.count {it.error.isEmpty()}}건 · 확인 필요 ${pending.count {it.error.isNotEmpty()}}건")
                c.lastSuccess?.let {Hint("마지막 성공 ${DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))}")}
                if(c.provider == CalendarConnection.DEVICE) Hint("완료는 휴대폰 캘린더 저장 기준이에요. 구글 웹 등에는 계정 동기화 후 표시돼요.")
                if(c.error.isNotEmpty()) Text(c.error, color = MaterialTheme.colorScheme.error)
                Hint("예약 시간대: ${c.zoneId}")
                if(c.state == CalendarConnection.ACTIVE) {
                    CalendarCheck("예약 메모 포함", c.includeMemo, !working) {value -> perform {store.reapply(c.id, value); runtime.request(c.provider)}}
                    Action("지금 동기화", {perform {store.retry(c.id); runtime.request(c.provider)}}, enabled = !working)
                    SecondaryButton(onClick = {fullApply = true}, enabled = !working) {Text("전체 다시 반영")}
                } else Action("연결 다시 확인", {
                    resumeId = c.id
                    if(c.provider == CalendarConnection.DEVICE) devicePermission()
                    else {googleAccount = c.account; authorizeGoogle()}
                }, enabled = !working)
                SecondaryButton(onClick = {deleteEvents = true; disconnecting = true}, enabled = !working) {Text("캘린더 변경")}
                TextButton(onClick = {deleteEvents = false; disconnecting = true}, enabled = !working) {Text("연결 해제")}
            }
            pending.filter {it.error.isNotEmpty()}.forEach {job -> Paper {
                val a = snapshot.appointments.find {"${c.id}:${it.id}" == job.linkId}
                Section(a?.let {"${it.date} 병원 방문"} ?: "삭제할 캘린더 일정"); Text(job.error)
                if(job.blocked && a != null && c.state == CalendarConnection.ACTIVE) TextButton(onClick = {freshLink = job.linkId}, enabled = !working) {Text("중복 확인 후 새 전송")}
            } }
        }
    }
    selected?.let {target -> AlertDialog(onDismissRequest = {if(!working) selected = null},
        containerColor = MaterialTheme.colorScheme.surface,
        title = {Text("캘린더에 연동할까요?", style = MaterialTheme.typography.titleLarge)}, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(target.name, style = MaterialTheme.typography.titleMedium)
                    if(target.account != target.name) Text(target.account.ifBlank {"휴대폰 전용"},
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("오늘 이후 예약 ${snapshot.appointments.count {it.date >= today()}}건",
                        modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("전송할 내용", style = MaterialTheme.typography.titleSmall)
                Text("진료과·검사·치료 항목을 보내요.\n종료 시간은 마지막 예약의 30분 뒤로 표시해요.", style = MaterialTheme.typography.bodyMedium)
                CalendarCheck("예약 메모도 포함", includeMemo, !working) {includeMemo = it}
            }
            if(restoreCheck && target.provider == CalendarConnection.DEVICE) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("기존 일정 확인", style = MaterialTheme.typography.titleSmall)
                    Text("이전에 보낸 일정이 남아 있으면 중복될 수 있어요. 캘린더를 먼저 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
                    CalendarCheck("기존 일정을 확인했고, 새로 전송할게요", acknowledgeRestore, !working) {acknowledgeRestore = it}
                }
            }
        }
    }, confirmButton = {Button(modifier = Modifier.heightIn(min = 48.dp),
        enabled = !working && (!restoreCheck || target.provider != CalendarConnection.DEVICE || acknowledgeRestore), onClick = {perform {
        store.connect(target, includeMemo, acknowledgeRestore); runtime.request(target.provider); selected = null; targets = emptyList()
    }}) {Text("연동 시작")}}, dismissButton = {TextButton(onClick = {selected = null}, enabled = !working,
        modifier = Modifier.heightIn(min = 48.dp)) {Text("취소")}}) }
    if(disconnecting && connection != null) AlertDialog(onDismissRequest = {if(!working) disconnecting = false},
        containerColor = MaterialTheme.colorScheme.surface, title = {Text("캘린더 연결을 해제할까요?", style = MaterialTheme.typography.titleLarge)}, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("페리로그의 병원 예약은 유지해요. 기존 일정을 남기면 이후에는 자동으로 수정·삭제되지 않아요.")
            CalendarCheck("페리로그가 보낸 일정도 삭제", deleteEvents, !working) {deleteEvents = it}
            if(connection.state == CalendarConnection.CLEANING || connection.error.isNotBlank()) Text("정리가 불가능하면 위 항목을 끄고 기존 일정을 남긴 채 해제할 수 있어요.")
        }
    }, confirmButton = {TextButton(onClick = {perform {
        store.disconnect(connection.id, deleteEvents); runtime.request(connection.provider); disconnecting = false
    }}, enabled = !working) {Text("연결 해제")}}, dismissButton = {TextButton(onClick = {disconnecting = false}) {Text("취소")}})
    if(fullApply && connection != null) Confirm("전체 일정을 다시 반영할까요?", "캘린더에서 바꾼 날짜·시간·내용을 페리로그 내용으로 덮어써요. 캘린더에서 삭제한 일정도 다시 생성해요.", {fullApply = false}) {
        perform {store.reapply(connection.id); runtime.request(connection.provider); fullApply = false}
    }
    freshLink?.let {id -> Confirm("새 전송을 허용할까요?", "캘린더에서 기존·중복 일정을 먼저 정리해 주세요. 기존 일정을 지우지 않고 새 일정이 만들어질 수 있어요.", {freshLink = null}) {
        perform {store.allowFreshCreation(id); connection?.let {runtime.request(it.provider)}; freshLink = null}
    } }
}

@Composable private fun CalendarCheck(label: String, value: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value, enabled = enabled, role = Role.Checkbox, onValueChange = change)
        .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value, null, enabled = enabled, modifier = Modifier.size(24.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun AppointmentCalendarStatus(vm: JournalViewModel, appointmentId: String, navigate: (String) -> Unit) {
    val dao = vm.app.calendar.store.dao
    val connections by dao.observeConnections().collectAsState(initial = emptyList())
    val links by dao.observeLinks().collectAsState(initial = emptyList())
    val jobs by dao.observeJobs().collectAsState(initial = emptyList())
    val c = connections.firstOrNull {it.state != CalendarConnection.OFF} ?: return
    val link = links.find {it.connectionId == c.id && it.appointmentId == appointmentId}
    val job = link?.let {l -> jobs.find {it.linkId == l.id}}
    Paper {
        Text("캘린더 · ${c.calendarName}")
        Hint(when {
            c.state != CalendarConnection.ACTIVE || job?.error?.isNotBlank() == true -> "연결 확인 필요"
            job != null -> "반영 대기"
            link?.lastSuccess != null && !link.deleted -> if(c.provider == CalendarConnection.DEVICE) "휴대폰 캘린더 반영 완료" else "반영 완료"
            else -> "전송 대상이 아닌 지난 일정이에요."
        })
        TextButton(onClick = {navigate("settings/calendar")}) {Text("캘린더 연동 설정")}
    }
}
