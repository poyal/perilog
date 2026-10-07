package com.poyal.perilog.ui

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poyal.perilog.BuildConfig
import com.poyal.perilog.update.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun AboutScreen(updates: UpdateController, back: () -> Unit) {
    val state by updates.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var message by remember {mutableStateOf("")}
    fun link(url:String) {
        if(!openExternal(context,url))message="연결할 앱을 찾지 못했어요."
    }
    Page("앱 정보·문의",back=back) {
        Paper {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bow(48)
                Column { Section("페리로그"); Hint("버전 ${BuildConfig.VERSION_NAME}") }
            }
            Text("제작자  Poyal")
            TextButton(onClick = { link("mailto:poyal.work@gmail.com") }) { Text("poyal.work@gmail.com") }
            HorizontalDivider()
            MenuRow("GitHub", "소스와 프로젝트 안내", Icons.Outlined.Code) { link("https://github.com/poyal/perilog") }
            MenuRow("버그 신고 · 기능 제안", icon = Icons.Outlined.BugReport) { link("https://github.com/poyal/perilog/issues") }
            MenuRow("릴리즈 · 변경 내역", icon = Icons.Outlined.NewReleases) { link(state.release?.pageUrl ?: "$RELEASES_URL/latest") }
            MenuRow("이메일 문의", icon = Icons.Outlined.Email) { link("mailto:poyal.work@gmail.com") }
        }
        if(message.isNotBlank())Paper {Hint(message)}
        Paper {Hint("기록은 기기 내부에 저장돼요. 업데이트 확인·다운로드에만 GitHub를 사용하며 기록이나 백업은 전송하지 않아요.")}
    }
}

@Composable fun UpdatesScreen(updates: UpdateController, back: () -> Unit) {
    val state by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var folderMessage by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.installationMessage(installationId, if (context.packageManager.canRequestPackageInstalls())
            "설치가 허용됐어요. 설치 버튼을 눌러 계속해 주세요." else "설치 허용이 꺼져 있어요. Download 폴더의 APK는 보관돼요.")
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.installationMessage(installationId, "설치를 취소했거나 완료되지 않았다면 다시 시도할 수 있어요. Download 폴더의 APK는 그대로 보관돼요.")
    }
    fun install() { scope.launch {
        val prepared = updates.installationCopy() ?: return@launch
        if (!updates.isInstallationCurrent(prepared)) return@launch
        installationId = prepared.download.id
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                updates.installationMessage(installationId, "페리로그의 ‘이 출처의 앱 설치 허용’을 켜 주세요. 다운로드한 파일은 유지돼요.")
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            } else {
                installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(prepared.uri.toUri(), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        } catch (_: Exception) { updates.installationMessage(installationId, "설치 화면을 열지 못했어요. 다운로드 폴더에서 APK를 직접 열어 주세요.") }
    } }
    Page("업데이트", back=back) {
        Paper {
            Section("업데이트")
            Text(state.message, color = if (state.check == CheckStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Hint("현재 버전 ${updates.installedVersion}")
            state.release?.let { Hint("최신 확인 버전 ${it.version}") }
            if (state.checkedAt > 0) Hint("마지막 성공 확인: " + Instant.ofEpochMilli(state.checkedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy. M. d HH:mm")))
            if (state.check == CheckStatus.ERROR && state.release != null) Hint("이전 확인 결과예요. 새 정보를 받지 못했어요.")
            SecondaryButton(onClick = updates::checkNow, enabled = state.check != CheckStatus.CHECKING) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (state.check == CheckStatus.CHECKING) "확인 중…" else "업데이트 확인")
            }
            val newer = state.release?.let { compareVersions(it.version, updates.installedVersion) > 0 } == true
            val matching = newer && state.transferRelease == state.release
            val transfer = if (matching) state.transfer else TransferStatus.NONE
            if (newer) {
                val running = transfer in setOf(TransferStatus.DOWNLOADING, TransferStatus.VERIFYING)
                val version = state.release!!.version
                if (transfer != TransferStatus.READY) Action("$version " + if (transfer in setOf(TransferStatus.FAILED, TransferStatus.CANCELLED)) "다시 다운로드" else "APK 다운로드", updates::download, !running, Icons.Outlined.Download)
                if (transfer == TransferStatus.READY) Action("$version 업데이트 설치", ::install, icon = Icons.Outlined.InstallMobile)
            }
            if (transfer == TransferStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { if (state.total > 0) (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Hint("${state.downloaded / 1024} / ${state.total / 1024} KB")
                TextButton(onClick = updates::cancelDownload) { Text("다운로드 취소") }
            }
            if (transfer == TransferStatus.VERIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (matching && state.transferMessage.isNotBlank()) Text(state.transferMessage, color = if (transfer == TransferStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (matching) state.fileName?.let { Hint("${if (transfer == TransferStatus.READY) "저장 파일" else "다운로드 파일"}: $it") }
            SecondaryButton(onClick = {
                try { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                catch (_: Exception) { folderMessage = "파일 앱의 Download 또는 다운로드 폴더에서 APK를 찾아 주세요." }
            }) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("다운로드 폴더 열기") }
            if (folderMessage.isNotBlank()) Hint(folderMessage)
            Hint("앱을 새로 실행하면 새 버전을 알려드려요. 자동 조회는 하루 한 번, 직접 확인은 언제든 가능해요.")
        }
        Paper {
            Section("업데이트와 데이터")
            Text("기존 앱을 삭제하지 않고 같은 서명의 APK로 업데이트하면 기록·재고·설정이 유지돼요.")
            Hint("업데이트 전 설정 → 데이터 내보내기·가져오기에서 전체 데이터를 내보내 두세요. 앱을 삭제하거나 데이터를 초기화하면 기기 안의 기록이 지워져요.")
            Text("설치에 실패하거나 보안 설정으로 차단돼도 Download 폴더의 APK는 남아 있어요. 파일 앱에서 직접 열어 설치할 수 있어요.")
            Hint("설치가 끝나도 APK를 자동 삭제하지 않아요. 필요 없어진 파일은 직접 정리해 주세요.")
            HorizontalDivider()
            Hint("기록은 기기 내부에 저장돼요. 업데이트 확인·다운로드에만 GitHub를 사용하며, 기록이나 백업은 전송하지 않아요.")
        }
    }
}

@Composable fun UpdatePrompt(state: UpdateState, dismiss: () -> Unit, download: () -> Unit) {
    if (state.prompt) AlertDialog(onDismissRequest = dismiss,
        title = { Text("새 버전이 있어요") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("페리로그 ${state.release?.version} 업데이트를 다운로드할 수 있어요.")
            if (state.check == CheckStatus.ERROR) Hint("이전 확인 결과예요. 지금은 GitHub에 연결하지 못했어요.")
            Hint("APK는 Download 폴더에 저장돼요. 설치는 확인 후 직접 진행해 주세요.")
        } },
        confirmButton = { TextButton(onClick = download) { Text("다운로드") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("나중에") } })
}
