package com.poyal.perilog.ui

import android.net.Uri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkManager
import com.poyal.perilog.BuildConfig
import com.poyal.perilog.backup.*
import com.poyal.perilog.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SettingsEntry(val title:String,val summary:String,val icon:ImageVector,val route:String)

/** Only navigation lives here; input and file lists belong to their detail screens. */
@Composable fun SettingsScreen(s:Snapshot,navigate:(String)->Unit,back:()->Unit) {
    val context=LocalContext.current
    val device by context.deviceStore.data.collectAsState(initial=androidx.datastore.preferences.core.emptyPreferences())
    val allowed=notificationAllowed()
    val p=s.preferences
    val theme=when(p.darkMode){"LIGHT"->"밝게";"DARK"->"어둡게";else->"시스템 설정"}
    val groups=listOf(
        "일반" to listOf(
            SettingsEntry("화면·표시", theme,Icons.Outlined.Palette,"settings/display"),
            SettingsEntry("알림", "${if(allowed)"허용됨"else"꺼짐"} · ${String.format(Locale.US,"%02d:%02d",p.reminderHour,p.reminderMinute)}",Icons.Outlined.Notifications,"settings/notifications"),
            SettingsEntry("앱 잠금",if(p.lock)"사용 중"else"사용 안 함",Icons.Outlined.Lock,"settings/lock"),
            SettingsEntry("홈 화면 위젯","어제·오늘 기록 · 병원 일정",Icons.Outlined.Widgets,"widgets")),
        "투석 기록·물품" to listOf(
            SettingsEntry("사용 구성","등록한 구성 ${s.templates.size}개",Icons.Outlined.ViewList,"templates"),
            SettingsEntry("품목 관리","등록한 품목 ${s.products.size}개",Icons.Outlined.Inventory2,"products"),
            SettingsEntry("투석 계산 기준","이전 최종 주입 설정 · 적용일",Icons.Outlined.Calculate,"settings/basis"),
            SettingsEntry("사용자 색상","저장한 색상 ${p.palette.size}개",Icons.Outlined.Palette,"settings/palette")),
        "병원·연락처" to listOf(
            SettingsEntry("병원 일정","예약 날짜·시간·메모",Icons.Outlined.Event,"appointments"),
            SettingsEntry("진료과","등록한 진료과 ${s.departments.size}개",Icons.Outlined.LocalHospital,"departments"),
            SettingsEntry("검사·치료 항목","등록한 항목 ${s.careTemplates.size}개",Icons.Outlined.MedicalServices,"careTemplates"),
            SettingsEntry("연락처","등록한 연락처 ${s.contacts.size}개",Icons.Outlined.ContactPhone,"contacts")),
        "백업·데이터" to listOf(
            SettingsEntry("자동 백업",if(device[DeviceKeys.folder]==null)"폴더 연결 필요"else "${if(p.backupDays==1)"매일"else"매주"} · ${p.keepBackups}개 보관",Icons.Outlined.Backup,"settings/backup"),
            SettingsEntry("데이터 내보내기·가져오기","기록·재고·설정을 파일로 보관·복원",Icons.Outlined.ImportExport,"settings/transfer"),
            SettingsEntry("보호 백업","복원·초기화 전 보관한 자료",Icons.Outlined.Restore,"settings/protection"),
            SettingsEntry("데이터 초기화","기록·재고·설정 전체 초기화",Icons.Outlined.DeleteOutline,"settings/reset")),
        "도움말·앱 정보" to listOf(
            SettingsEntry("사용 안내","전체 사용법 · 검색 · 실제 화면",Icons.Outlined.MenuBook,"guide"),
            SettingsEntry("업데이트","현재 버전 ${BuildConfig.VERSION_NAME}",Icons.Outlined.SystemUpdate,"updates"),
            SettingsEntry("앱 정보·문의","제작자 · 문의 · 변경 내역",Icons.Outlined.Info,"about"))
    )
    Page("설정",back=back) {
        groups.forEach { (title,entries)->
            Paper(contentPadding=16.dp) {
                Section(title)
                Column {
                    entries.forEachIndexed {i,entry->
                        if(i>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        MenuRow(entry.title,entry.summary,entry.icon){navigate(entry.route)}
                    }
                }
            }
        }
    }
}

@Composable fun PreferenceSettingsScreen(s:Snapshot,vm:JournalViewModel,section:String,back:()->Unit) {
    val context=LocalContext.current
    val busy by vm.busy.collectAsState()
    var p by rememberJsonState("preferences"){s.preferences}
    var original by rememberJsonState("original"){s.preferences}
    var basis by rememberSaveable{mutableStateOf<Int?>(s.preferences.basis.last().ml)}
    var basisFrom by rememberSaveable{mutableStateOf(today())}
    var originalBasis by rememberSaveable{mutableStateOf(basis)}
    var originalFrom by rememberSaveable{mutableStateOf(basisFrom)}
    val allowed=notificationAllowed()
    val title=when(section){"display"->"화면·표시";"lock"->"앱 잠금";"notifications"->"알림";"backup"->"자동 백업";else->"투석 계산 기준"}
    val saveLabel=when(section){"display"->"화면 설정 저장";"lock"->"앱 잠금 설정 저장";"notifications"->"알림 시각 저장";"backup"->"백업 설정 저장";else->"이 날짜부터 기준 저장"}
    val success=when(section){"display"->"화면 설정을 저장했어요";"lock"->"앱 잠금 설정을 저장했어요";"notifications"->"알림 시각을 저장했어요";"backup"->"백업 설정을 저장했어요";else->"계산 기준을 저장했어요"}
    val dirty=when(section) {
        "display"->p.darkMode!=original.darkMode
        "lock"->p.lock!=original.lock
        "notifications"->p.reminderHour!=original.reminderHour || p.reminderMinute!=original.reminderMinute
        "backup"->p.backupDays!=original.backupDays || p.keepBackups!=original.keepBackups
        else->basis!=originalBasis || basisFrom!=originalFrom
    }
    val valid=when(section) {
        "notifications"->p.reminderHour in 0..23 && p.reminderMinute in 0..59
        "backup"->p.keepBackups in 1..365
        "basis"->basis!=null && basis!! in 0..100000 && runCatching{LocalDate.parse(basisFrom)}.isSuccess
        else->true
    }
    EditorPage(title,"",dirty,back,{
        val draft=p;val ml=basis;val from=basisFrom
        vm.preferences(success,onSaved={original=draft;originalBasis=ml;originalFrom=from}) {saved->
            when(section) {
                "display"->saved.copy(darkMode=draft.darkMode)
                "lock"->saved.copy(lock=draft.lock)
                "notifications"->saved.copy(reminderHour=draft.reminderHour,reminderMinute=draft.reminderMinute)
                "backup"->saved.copy(backupDays=draft.backupDays,keepBackups=draft.keepBackups)
                else->saved.copy(basis=(saved.basis.filterNot{it.from==from}+Basis(from,requireNotNull(ml))).sortedBy{it.from})
            }
        }
    },valid,saveLabel,busy) {
        when(section) {
            "display"->Paper {
                Section("테마")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("SYSTEM" to "시스템","LIGHT" to "밝게","DARK" to "어둡게").forEach{(id,label)->SelectionChip(p.darkMode==id,{p=p.copy(darkMode=id)},{Text(label)})}
                }
                Hint("글씨 크기는 휴대폰의 글씨 설정을 따라요.")
            }
            "lock"->Paper {
                SettingsSwitch("앱 잠금",p.lock,{enabled->
                    val authenticators=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    if(!enabled || BiometricManager.from(context).canAuthenticate(authenticators)==BiometricManager.BIOMETRIC_SUCCESS)p=p.copy(lock=enabled)
                    else vm.act{vm.message.emit("먼저 휴대폰 설정에서 화면 잠금을 설정해 주세요.")}
                },description="생체 인증 또는 기기 잠금 사용")
                Hint("앱 잠금을 사용해도 홈 화면 위젯 내용은 표시돼요. 위젯을 눌러 앱으로 들어올 때 인증해요.")
            }
            "notifications"->Paper {
                Text(if(allowed)"시스템 알림이 켜져 있어요"else"시스템 알림이 꺼져 있어요")
                Hint("알림 허용·소리·진동은 휴대폰 설정에서 관리해요.")
                SecondaryButton(onClick={context.startActivity(Reminders.settingsIntent(context))}){Text("시스템 알림 설정 열기")}
                AdaptivePair(first={NumberInput("알림 시각",p.reminderHour,{p=p.copy(reminderHour=it?:0)},"시")},second={NumberInput("알림 분",p.reminderMinute,{p=p.copy(reminderMinute=it?:0)},"분")})
                if(!valid)Hint("시는 0~23, 분은 0~59 사이로 입력해 주세요.")
                Hint("완료된 날에는 알리지 않아요. 휴대폰 절전 상태에 따라 알림이 늦어질 수 있어요.")
            }
            "backup"->AutomaticBackupContent(p,vm){p=it}
            "basis"->Paper {
                Hint("새 기록에 적용할 이전 최종 주입 설정값이에요. 저장한 과거 기록의 계산은 바뀌지 않아요.")
                NumberInput("이전 최종 주입 설정",basis,{basis=it},"mL")
                DateControl(basisFrom,{basisFrom=it},"적용일")
                Section("저장한 기준")
                s.preferences.basis.sortedByDescending{it.from}.take(5).forEach{Hint("${it.from}부터 ${it.ml}mL")}
            }
        }
    }
}

@Composable private fun AutomaticBackupContent(p:Preferences,vm:JournalViewModel,onChange:(Preferences)->Unit) {
    val context=LocalContext.current
    val busy by vm.busy.collectAsState()
    val device by context.deviceStore.data.collectAsState(initial=androidx.datastore.preferences.core.emptyPreferences())
    val folder=device[DeviceKeys.folder]
    val selectFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)vm.act("백업 폴더를 연결했어요"){vm.app.backup.chooseFolder(uri);vm.app.backup.automatic(true)}}
    Paper {
        Text(if(folder==null)"백업 폴더를 선택해 주세요"else"백업 폴더 연결됨")
        Hint(device[DeviceKeys.status] ?: "아직 자동 백업 내역이 없어요")
        device[DeviceKeys.lastBackup]?.takeIf{it>0}?.let{Hint("최근 성공: "+backupTime(it))}
        SecondaryButton(onClick={selectFolder.launch(null)},enabled=!busy){Text(if(folder==null)"백업 폴더 선택"else"백업 폴더 변경")}
        Hint("폴더를 선택하면 바로 연결하고 첫 백업을 만들어요.")
        Section("백업 주기")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(1 to "매일",7 to "매주").forEach{(days,label)->SelectionChip(p.backupDays==days,{onChange(p.copy(backupDays=days))},{Text(label)})}}
        NumberInput("자동 백업 보관 개수",p.keepBackups,{onChange(p.copy(keepBackups=it?:30))},"개")
        if(p.keepBackups !in 1..365)Hint("보관 개수는 1~365개로 입력해 주세요.")
        Action("지금 백업",{vm.act("백업을 완료했어요"){vm.app.backup.automatic(true)}},folder!=null && !busy)
        Hint("지금 백업은 저장된 주기·보관 개수를 사용해요. 자동 백업만 정리하며 직접 내보낸 파일은 그대로 남아요.")
    }
}

private fun backupTime(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@Composable fun DataSettingsScreen(s:Snapshot,vm:JournalViewModel,section:String,back:()->Unit) {
    val context=LocalContext.current
    val busy by vm.busy.collectAsState()
    var restoring by remember{mutableStateOf<Snapshot?>(null)}
    var reset by rememberSaveable{mutableStateOf(false)}
    var files by remember{mutableStateOf<List<Pair<String,Uri>>>(emptyList())}
    var protection by remember{mutableStateOf<List<File>>(emptyList())}
    var exportProtectionPath by rememberSaveable{mutableStateOf<String?>(null)}
    val device by context.deviceStore.data.collectAsState(initial=androidx.datastore.preferences.core.emptyPreferences())
    val folder=device[DeviceKeys.folder]
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)vm.act("파일로 내보냈어요"){vm.app.backup.export(uri)}}
    val exportProtected=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)exportProtectionPath?.let{path->vm.act("보호 백업을 내보냈어요"){withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(File(path).readBytes())}}}}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.act{restoring=vm.app.backup.read(uri)}}
    LaunchedEffect(section,folder,device[DeviceKeys.lastBackup],restoring) {
        withContext(Dispatchers.IO) {
            if(section=="transfer")files=runCatching {folder?.let{DocumentFile.fromTreeUri(context,Uri.parse(it))?.listFiles()?.filter{f->f.name?.startsWith("perilog-auto-")==true}?.sortedByDescending{it.name}?.map{(it.name ?: "백업") to it.uri}}}.getOrNull() ?: emptyList()
            if(section=="protection")protection=File(context.filesDir,"protection").listFiles()?.sortedByDescending{it.name} ?: emptyList()
        }
    }
    val title=when(section){"transfer"->"내보내기·가져오기";"protection"->"보호 백업";else->"데이터 초기화"}
    Page(title,back=back) {
        when(section) {
            "transfer"->{
                Paper {
                    Text("기록 ${s.treatments.size}건 · 품목 ${s.products.size}개 · 입고 ${s.receipts.count{!it.cancelled}}건")
                    Hint("병원 일정·진료과·검사·치료 항목·연락처·입고 요청과 설정도 함께 보관해요.")
                    Action("전체 데이터 내보내기",{export.launch("perilog-${today()}.json")},!busy)
                    SecondaryButton(onClick={import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("백업 파일 가져오기")}
                    Hint("가져오기는 전체 교체예요. 파일을 확인하고 현재 데이터를 보호 백업한 뒤 복원해요. 백업 JSON에는 암호가 없으므로 보관 위치를 확인해 주세요.")
                }
                if(files.isNotEmpty())Paper {
                    Section("연결한 폴더의 최근 자동 백업")
                    files.take(10).forEach{(name,uri)->TextButton(onClick={vm.act{restoring=vm.app.backup.read(uri)}},enabled=!busy){Text(name,style=MaterialTheme.typography.bodySmall)}}
                }
            }
            "protection"->Paper {
                Hint("복원·초기화 전에 앱 안에 보관한 자료예요. 앱 삭제 전에 필요한 파일을 내보내 주세요.")
                if(protection.isEmpty())Text("보관된 보호 백업이 없어요")
                protection.forEach{f->Column {
                    Text(f.name,style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={exportProtectionPath=f.path;exportProtected.launch(f.name)},enabled=!busy){Text("내보내기")}
                }}
            }
            "reset"->Paper {
                Text("기록·재고·설정을 초기화하고 자동 백업 폴더 연결을 해제해요.")
                Hint("현재 자료는 앱 내부 보호 백업에 남겨요. 외부로 내보낸 파일은 삭제하지 않아요.")
                TextButton(onClick={reset=true},enabled=!busy){Text("모든 앱 데이터 초기화",color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    restoring?.let{incoming->Confirm("백업으로 전체 복원할까요?","${backupTime(incoming.exportedAt)}\n치료 ${incoming.treatments.size}건 · 품목 ${incoming.products.size}개 · 입고 ${incoming.receipts.size}건\n병원 일정 ${incoming.appointments.size}건 · 진료과 ${incoming.departments.size}개 · 검사·치료 항목 ${incoming.careTemplates.size}개 · 연락처 ${incoming.contacts.size}개\n현재 데이터는 앱 내부에 보호 백업한 뒤 교체합니다.",{restoring=null}){
        vm.act("데이터를 복원했어요"){vm.app.backup.restore(incoming);Reminders.schedule(context,incoming.preferences);restoring=null;back()}
    }}
    if(reset)Confirm("모든 앱 데이터를 초기화할까요?","현재 데이터는 보호 백업으로 남깁니다. 기록·재고·설정을 초기화하고 자동 백업 폴더 연결을 해제합니다. 외부 파일은 삭제하지 않아요.",{reset=false}){
        vm.act("초기화했어요"){vm.app.backup.protect();vm.repository.restore(Snapshot());context.deviceStore.edit{val requested=it[DeviceKeys.notificationRequested];it.clear();requested?.let{value->it[DeviceKeys.notificationRequested]=value}};WorkManager.getInstance(context).cancelUniqueWork("perilog-backup");Reminders.schedule(context,Preferences());reset=false;back()}
    }
}

@Composable fun PaletteSettingsScreen(s:Snapshot,vm:JournalViewModel,back:()->Unit) {
    val busy by vm.busy.collectAsState()
    Page("사용자 색상",back=back) {
        Paper {
            Hint("색상을 길게 누르면 팔레트에서 바로 삭제해요. 품목에 지정된 색은 유지돼요.")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                s.preferences.palette.forEach{color->Box(Modifier.size(48.dp).background(androidx.compose.ui.graphics.Color(color),MaterialTheme.shapes.small).combinedClickable(enabled=!busy,onClick={},onLongClick={vm.preferences("색상을 삭제했어요"){it.copy(palette=it.palette-color)}}))}
            }
            if(s.preferences.palette.isEmpty())Text("저장한 색상이 없어요")
            Hint("새 색상은 품목을 수정할 때 컬러 피커에서 추가할 수 있어요.")
        }
    }
}

@Composable private fun SettingsSwitch(title:String,checked:Boolean,onCheckedChange:(Boolean)->Unit,description:String="") {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clip(MaterialTheme.shapes.small)
        .toggleable(value=checked,role=Role.Switch,onValueChange=onCheckedChange).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            if(description.isNotBlank())Hint(description)
        }
        Switch(checked=checked,onCheckedChange=null)
    }
}
