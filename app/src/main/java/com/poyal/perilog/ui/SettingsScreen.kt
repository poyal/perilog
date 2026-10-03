package com.poyal.perilog.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.res.stringResource
import com.poyal.perilog.R
import com.poyal.perilog.BuildConfig
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkManager
import com.poyal.perilog.backup.*
import com.poyal.perilog.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SettingsScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit,back:()->Unit) {
    val context=LocalContext.current
    var p by rememberJsonState("preferences"){s.preferences}
    var basis by rememberSaveable{mutableStateOf<Int?>(s.preferences.basis.last().ml)}
    var basisFrom by rememberSaveable{mutableStateOf(today())}
    var restoring by remember{mutableStateOf<Snapshot?>(null)}
    var reset by remember{mutableStateOf(false)}
    var files by remember{mutableStateOf<List<Pair<String,Uri>>>(emptyList())}
    var protection by remember{mutableStateOf<List<File>>(emptyList())}
    var exportProtection by remember{mutableStateOf<File?>(null)}
    val device by context.deviceStore.data.collectAsState(initial=androidx.datastore.preferences.core.emptyPreferences())
    val folder=device[DeviceKeys.folder]
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)vm.act("파일로 내보냈어요"){vm.app.backup.export(uri)}}
    val exportProtected=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)exportProtection?.let{file->vm.act("보호 백업을 내보냈어요"){withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(file.readBytes())}}}}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.act{restoring=vm.app.backup.read(uri)}}
    val selectFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)vm.act("백업 폴더를 연결했어요"){vm.app.backup.chooseFolder(uri);vm.app.backup.automatic(true)}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(!allowed){p=p.copy(reminder=false);vm.act{vm.message.emit("알림 권한이 꺼져 있어요. 홈의 미작성 안내는 계속 제공돼요.")}}}
    LaunchedEffect(folder,device[DeviceKeys.lastBackup],restoring) {
        withContext(Dispatchers.IO) {
            files=runCatching { folder?.let{DocumentFile.fromTreeUri(context,Uri.parse(it))?.listFiles()?.filter{f->f.name?.startsWith("perilog-auto-")==true}?.sortedByDescending{it.name}?.map{(it.name ?: "백업") to it.uri}}}.getOrNull() ?: emptyList()
            protection=File(context.filesDir,"protection").listFiles()?.sortedByDescending{it.name} ?: emptyList()
        }
    }
    Page("설정","내 기록과 사용 방식을 관리해요",back) {
        Paper {
            Section("병원 일정·연락처")
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow("병원 일정 관리","예약 날짜·시간·메모를 관리해요",Icons.Outlined.Event){navigate("appointments")}
            MenuRow("진료과 관리","여러 진료과를 색상으로 구분해요",Icons.Outlined.Palette){navigate("departments")}
            MenuRow("치료 구성 관리","검사·치료를 한 항목씩 등록해요",Icons.Outlined.MedicalServices){navigate("careTemplates")}
            MenuRow("연락처 관리","아바타와 전화·문자 허용을 설정해요",Icons.Outlined.ContactPhone){navigate("contacts")}
        }
        Paper {
            Section("투석 물품·사용 구성")
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow("사용 구성 관리","품목별 색상과 EA 수량을 함께 설정해요",Icons.Outlined.ViewList){navigate("templates")}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow("품목 관리 · 색상",icon=Icons.Outlined.Inventory2){navigate("products")}
        }
        Paper {
            Section("투석 계산 기준")
            Hint("새 기록에 적용할 이전 최종 주입 설정값이에요. 저장한 과거 기록의 계산은 바뀌지 않아요.")
            NumberInput("이전 최종 주입 설정",basis,{basis=it},"mL")
            DateControl(basisFrom,{basisFrom=it},"적용일")
            SecondaryButton(onClick={basis?.let{ml->val next=p.copy(basis=(p.basis.filterNot{it.from==basisFrom}+Basis(basisFrom,ml)).sortedBy{it.from});p=next;vm.preferences(next)}},enabled=basis!=null){Text("이 날짜부터 기준 저장")}
            p.basis.sortedByDescending{it.from}.take(5).forEach{Hint("${it.from}부터 ${it.ml}mL")}
        }
        Paper {
            Section("표시와 안내")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("SYSTEM" to "시스템","LIGHT" to "밝게","DARK" to "어둡게").forEach{(id,label)->SelectionChip(p.darkMode==id,{p=p.copy(darkMode=id)},{Text(label)})}}
            Column {
                SettingsSwitch("완료 축하 애니메이션",p.celebrate,{p=p.copy(celebrate=it)})
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitch("미작성 항목 기기 알림",p.reminder,{enabled->
                    p=p.copy(reminder=enabled)
                    if(enabled && Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
                if(p.reminder)Column(Modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    AdaptivePair(first={NumberInput("알림 시각",p.reminderHour,{p=p.copy(reminderHour=it?:0)},"시")},second={NumberInput("알림 분",p.reminderMinute,{p=p.copy(reminderMinute=it?:0)},"분")})
                    if(p.reminderHour !in 0..23 || p.reminderMinute !in 0..59)Hint("시는 0~23, 분은 0~59 사이로 입력해 주세요.")
                    Hint("완료된 날에는 알리지 않아요. 휴대폰 절전 상태에 따라 알림이 늦어질 수 있어요.")
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitch("앱 잠금",p.lock,{enabled->
                    val authenticators=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    if(!enabled || BiometricManager.from(context).canAuthenticate(authenticators)==BiometricManager.BIOMETRIC_SUCCESS)p=p.copy(lock=enabled)
                    else vm.act{vm.message.emit("먼저 휴대폰 설정에서 화면 잠금을 설정해 주세요.")}
                },description="생체 인증 또는 기기 잠금 사용")
            }
            Action("표시·알림·잠금 설정 저장",{vm.preferences(p)})
        }
        Paper {
            Section("데이터 내보내기 · 가져오기")
            Text("기록 ${s.treatments.size}건 · 품목 ${s.products.size}개 · 입고 ${s.receipts.count{!it.cancelled}}건")
            Hint("병원 일정 ${s.appointments.size}건 · 진료과 ${s.departments.size}개 · 치료 구성 ${s.careTemplates.size}개 · 연락처 ${s.contacts.size}개도 함께 보관해요.")
            Action("전체 데이터 내보내기",{export.launch("perilog-${today()}.json")})
            SecondaryButton(onClick={import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},Modifier.fillMaxWidth()){Text("백업 파일 가져오기")}
            Hint("가져오기는 전체 교체예요. 파일을 확인하고 현재 데이터를 보호 백업한 뒤 복원해요. 백업 JSON에는 암호가 없으므로 보관 위치를 확인해 주세요.")
        }
        Paper {
            Section("주기적 파일 백업")
            Text(if(folder==null)"백업 폴더를 선택해 주세요"else"백업 폴더 연결됨")
            Hint(device[DeviceKeys.status] ?: "아직 자동 백업 내역이 없어요")
            device[DeviceKeys.lastBackup]?.takeIf{it>0}?.let{at->Hint("최근 성공: "+Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))}
            SecondaryButton(onClick={selectFolder.launch(null)}){Text(if(folder==null)"백업 폴더 선택"else"백업 폴더 변경")}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(1 to "매일",7 to "매주").forEach{(days,label)->SelectionChip(p.backupDays==days,{p=p.copy(backupDays=days)},{Text(label)})}}
            NumberInput("자동 백업 보관 개수",p.keepBackups,{p=p.copy(keepBackups=it?:30)},"개")
            TextButton(onClick={vm.preferences(p)}){Text("백업 설정 저장")}
            Action("지금 백업",{vm.act("백업을 완료했어요"){vm.app.backup.automatic(true)}},folder!=null)
            Hint("자동 백업만 보관 개수에 따라 정리해요. 내보낸 파일은 그대로 남아요. 앱을 다시 설치하면 폴더를 다시 연결해 주세요.")
            files.take(10).forEach{(name,uri)->TextButton(onClick={vm.act{restoring=vm.app.backup.read(uri)}}){Text(name,style=MaterialTheme.typography.bodySmall)}}
        }
        if(protection.isNotEmpty())Paper {
            Section("복원·초기화 전 보호 백업")
            Hint("앱 안에 보관돼요. 앱 삭제 전에 필요한 파일을 내보내 주세요.")
            protection.forEach{f->Row(verticalAlignment=Alignment.CenterVertically){Text(f.name,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={exportProtection=f;exportProtected.launch(f.name)}){Text("내보내기")}}}
        }
        Paper {Section("사용자 색상");Hint("색상을 길게 누르면 팔레트에서 삭제해요. 품목에 지정된 색은 유지돼요.");Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            p.palette.forEach{color->Box(Modifier.size(44.dp).background(androidx.compose.ui.graphics.Color(color),MaterialTheme.shapes.small).combinedClickable(onClick={},onLongClick={val next=p.copy(palette=p.palette-color);p=next;vm.preferences(next)}))}
        }}
        Paper { MenuRow("페리로그 정보", "앱 정보 · 업데이트 · 문의", Icons.Outlined.Info) { navigate("about") } }
        TextButton(onClick={reset=true}){Text("모든 앱 데이터 초기화",color=MaterialTheme.colorScheme.error)}
        Hint("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} · 기기 내부 저장")
        Hint(stringResource(R.string.app_description))
    }
    restoring?.let{incoming->Confirm("백업으로 전체 복원할까요?","${Instant.ofEpochMilli(incoming.exportedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()}\n치료 ${incoming.treatments.size}건 · 품목 ${incoming.products.size}개 · 입고 ${incoming.receipts.size}건\n병원 일정 ${incoming.appointments.size}건 · 진료과 ${incoming.departments.size}개 · 치료 구성 ${incoming.careTemplates.size}개 · 연락처 ${incoming.contacts.size}개\n현재 데이터는 앱 내부에 보호 백업한 뒤 교체합니다.",{restoring=null}){
        vm.act("데이터를 복원했어요"){vm.app.backup.restore(incoming);Reminders.schedule(context,incoming.preferences);restoring=null;back()}
    }}
    if(reset)Confirm("모든 앱 데이터를 초기화할까요?","현재 데이터는 보호 백업으로 남깁니다. 기록·재고·설정을 초기화하고 자동 백업 폴더 연결을 해제합니다. 외부 파일은 삭제하지 않아요.",{reset=false}){
        vm.act("초기화했어요"){vm.app.backup.protect();vm.repository.restore(Snapshot());context.deviceStore.edit{it.clear()};WorkManager.getInstance(context).cancelUniqueWork("perilog-backup");Reminders.schedule(context,Preferences());reset=false;back()}
    }
}

@Composable private fun SettingsSwitch(
    title:String,
    checked:Boolean,
    onCheckedChange:(Boolean)->Unit,
    description:String=""
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min=64.dp).clip(MaterialTheme.shapes.small)
            .toggleable(value=checked,role=Role.Switch,onValueChange=onCheckedChange)
            .padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            if(description.isNotBlank())Hint(description)
        }
        Switch(checked=checked,onCheckedChange=null)
    }
}
