@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.poyal.perilog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CareIcon(val key:String,val label:String,val image:ImageVector)
private val careIcons=listOf(
    CareIcon("medical","진료",Icons.Outlined.MedicalServices),
    CareIcon("blood","피검사",Icons.Outlined.Bloodtype),
    CareIcon("lab","검사",Icons.Outlined.Science),
    CareIcon("injection","주사",Icons.Outlined.Vaccines),
    CareIcon("medicine","약",Icons.Outlined.Medication),
    CareIcon("dialysis","투석실",Icons.Outlined.LocalHospital),
    CareIcon("consult","상담",Icons.Outlined.HealthAndSafety),
    CareIcon("heart","심장",Icons.Outlined.MonitorHeart),
    CareIcon("healing","처치",Icons.Outlined.Healing),
    CareIcon("rehab","재활",Icons.Outlined.AccessibilityNew))

private fun careIcon(key:String)=careIcons.find{it.key==key} ?: careIcons.first()

@Composable fun DepartmentTags(departments:List<Department>) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        departments.forEach { DepartmentTag(it) }
    }
}

@Composable fun CareTasks(tasks:List<CareTask>) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        tasks.forEach { CareTaskTag(it) }
    }
}

@Composable private fun DepartmentTag(d:Department,time:String?=null) {
    Surface(modifier=if(time==null)Modifier else Modifier.semantics(mergeDescendants=true){contentDescription="${d.name} $time 진료 예약"},
        shape=MaterialTheme.shapes.small,color=Color(d.color).copy(alpha=.12f),
        border=BorderStroke(1.dp,Color(d.color).copy(alpha=.65f))) {
        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            ColorDot(d.color,14,d.name)
            Text(d.name,if(time==null)Modifier else Modifier.weight(1f,fill=false),style=MaterialTheme.typography.bodyMedium,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            if(time!=null)Text(time,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable private fun CareTaskTag(task:CareTask) {
    Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Icon(careIcon(task.iconKey).image,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
            Text(task.displayLabel(),style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun AppointmentDetails(a:Appointment) {
    Text(LocalDate.parse(a.date).format(DateTimeFormatter.ofPattern("yyyy. MM. dd (E)",Locale.KOREAN))+
        if(a.departments.isEmpty())" · "+a.time else "",
        style=MaterialTheme.typography.titleMedium)
    if(a.departments.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        a.departments.sortedBy{a.departmentTime(it)}.forEach { DepartmentTag(it,a.departmentTime(it)) }
    }
    if(a.selectedCareItems().isNotEmpty())CareTasks(a.selectedCareItems())
    MemoBlock(a.memo)
}

@Composable fun HomeAppointment(s:Snapshot,now:LocalDateTime,navigate:(String)->Unit) {
    val next=nextAppointment(s.appointments,now)
    val day=now.toLocalDate().toString()
    val shortageCount=remember(s,next?.date,day) {
        next?.let { appointment ->
            runCatching {forecastStock(s,appointment.date,day)}.getOrNull()
                ?.lines?.count {(it.balance?.numerator ?: 0)<0}
        } ?: 0
    }
    Paper(Modifier.testTag("home-appointment").then(if(next==null)Modifier else Modifier.clickable(
        onClickLabel="병원 일정 상세 보기",role=Role.Button,onClick={navigate("appointmentDetail/${next.id}")}))) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Event,null,tint=MaterialTheme.colorScheme.primary)
            Text("병원 일정",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if(next!=null) {
                Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick={navigate("appointment/new")}) {
                    Icon(Icons.Outlined.Add,"병원 일정 추가",tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(next==null) {
            Hint("예정된 병원 일정이 없어요.")
            Action("병원 일정 등록",{navigate("appointment/new")},icon=Icons.Outlined.Add)
        } else {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(next.dayLabel(now),Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
                    Column(Modifier.align(Alignment.CenterVertically),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(LocalDate.parse(next.date).format(DateTimeFormatter.ofPattern("yyyy. MM. dd (E)",Locale.KOREAN)),
                            style=MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("다음 일정 ${next.nextAt(now)?.toLocalTime()}",
                                modifier=Modifier.weight(1f,fill=false).testTag("home-next-appointment-time"),
                                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(shortageCount>0) IconButton(onClick={navigate("appointmentStock/${next.id}")},
                                modifier=Modifier.testTag("home-stock-shortage")) {
                                Icon(Icons.Outlined.WarningAmber,"방문 전 재고 부족 예상 ${shortageCount}품목 · 예상 잔량 보기",
                                    Modifier.size(20.dp),tint=MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if(next.departments.isNotEmpty() || next.selectedCareItems().isNotEmpty()) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        next.departments.sortedBy{next.departmentTime(it)}.forEach { DepartmentTag(it,next.departmentTime(it)) }
                        next.selectedCareItems().forEach { CareTaskTag(it) }
                    }
                }
                MemoBlock(next.memo)
            }
        }
    }
}

@Composable fun AppointmentDetailScreen(s:Snapshot,id:String,now:LocalDateTime,navigate:(String)->Unit,back:()->Unit) {
    val appointment=s.appointments.find {it.id==id}
    Page("병원 일정 상세",back=back) {
        if(appointment==null) Paper {Hint("삭제된 병원 일정이에요.")}
        else {
            Paper {
                Text(appointment.dayLabel(now),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                AppointmentDetails(appointment)
                if(!appointment.endsAt().isBefore(now)) AppointmentStockSummary(s,appointment,now,navigate)
            }
            Action("일정 수정",{navigate("appointment/${appointment.id}")},icon=Icons.Outlined.Edit)
            SecondaryButton(onClick={navigate("appointment/next/${appointment.id}")}) {Text("같은 구성으로 다음 예약")}
        }
    }
}

@Composable fun AppointmentsScreen(s:Snapshot,vm:JournalViewModel,now:LocalDateTime,navigate:(String)->Unit,back:()->Unit) {
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    val ordered=s.appointments.sortedWith(compareBy<Appointment>{it.at()}.thenBy{it.createdAt}.thenBy{it.id})
    Page("병원 일정 관리","마지막 진료시간이 지나면 다음 일정을 표시해요",back) {
        Action("+ 병원 일정 등록",{navigate("appointment/new")})
        if(ordered.isEmpty())Paper{Hint("예약 날짜와 시간을 등록해 주세요. 진료과와 검사·치료 항목은 설정에서 관리할 수 있어요.")}
        listOf(false,true).forEach { past ->
            val entries=ordered.filter{it.endsAt().isBefore(now)==past}.let{if(past)it.reversed()else it}
            if(entries.isNotEmpty())Section(if(past)"지난 일정"else"예정 일정")
            entries.forEach { a -> Paper {
                Text(a.dayLabel(now),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                AppointmentDetails(a)
                if(!past) AppointmentStockSummary(s,a,now,navigate)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={navigate("appointment/${a.id}")},modifier=Modifier.semantics{contentDescription="${a.id} 일정 수정"}){Text("수정")}
                    TextButton(onClick={navigate("appointment/next/${a.id}")}){Text("같은 구성으로 다음 예약")}
                    TextButton(onClick={deleting=a.id},modifier=Modifier.semantics{contentDescription="${a.id} 일정 삭제"}){Text("삭제")}
                }
            }}
        }
    }
    deleting?.let{id->Confirm("일정을 삭제할까요?","선택한 예약만 삭제해요. 등록한 진료과와 검사·치료 항목은 유지돼요.",{deleting=null}) {
        vm.act("일정을 삭제했어요"){vm.repository.deleteAppointment(id);deleting=null}
    }}
}

@Composable fun AppointmentEditor(s:Snapshot,vm:JournalViewModel,id:String,repeat:Boolean,navigate:(String)->Unit,back:()->Unit) {
    var a by rememberJsonState("appointment:$id:$repeat") {
        val existing=s.appointments.find{it.id==id}
        val initial=if(repeat)existing?.nextBooking() ?: Appointment() else existing ?: Appointment()
        initial.copy(care=null,careItems=initial.selectedCareItems(),
            departmentTimes=initial.departments.associate{it.id to initial.departmentTime(it)})
    }
    val original=rememberSaveable{codec.encodeToString(a)}
    val busy by vm.busy.collectAsState()
    val validDate=runCatching{LocalDate.parse(a.date)}.isSuccess
    val validTime=a.validTimes()
    var timeTarget by rememberSaveable{mutableStateOf<String?>(null)}
    var careTimeTarget by rememberSaveable{mutableStateOf<String?>(null)}
    EditorPage(if(id=="new" || repeat)"병원 일정 등록"else"병원 일정 수정","진료과는 색상, 치료 항목은 아이콘으로 보여요",
        codec.encodeToString(a)!=original,back,{
            vm.act("병원 일정을 저장했어요"){vm.repository.appointment(a.copy(memo=a.memo.trim(),care=null,careItems=a.selectedCareItems(),
                time=if(a.departments.isEmpty())a.time else a.departments.minOf{a.departmentTime(it)}));back()}
        },validDate && validTime,busy=busy) {
        Paper {
            DateControl(a.date,{a=a.copy(date=it)},"예약일")
            if(a.departments.isEmpty())AppointmentTimeInput("예약시간 · HH:mm",a.time,{a=a.copy(time=it)},"시간 선택"){timeTarget=""}
        }
        Paper {
            Section("진료과 · 여러 개 선택")
            Hint("선택한 진료과를 다시 누르면 해제해요. 수정한 색상·이름은 다시 선택하면 적용돼요.")
            val choices=(s.departments+a.departments).distinctBy{it.id}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                choices.forEach { d ->
                    val selected=a.departments.any{it.id==d.id}
                    SelectionChip(selected,{a=if(selected)a.copy(departments=a.departments.filterNot{it.id==d.id},departmentTimes=a.departmentTimes-d.id)
                        else a.copy(departments=a.departments+d,departmentTimes=a.departmentTimes+(d.id to a.time))},
                        label={Text(d.name)},leadingIcon={ColorDot(d.color,16,d.name)})
                }
            }
            if(a.departments.isNotEmpty()) {
                Hint("선택한 진료과마다 예약시간을 입력해 주세요.")
                a.departments.forEach { d ->
                    AppointmentTimeInput("${d.name} 예약시간 · HH:mm",a.departmentTime(d),
                        {value->a=a.copy(departmentTimes=a.departmentTimes+(d.id to value))},"${d.name} 시간 선택"){timeTarget=d.id}
                }
            }
            TextButton(onClick={navigate("departments")}){Text("진료과 등록·관리")}
        }
        Paper {
            Section("치료 항목 · 여러 개 선택")
            val selectedItems=a.selectedCareItems()
            val choices=(s.careTemplates.map{it.asCareTask()}+selectedItems).distinctBy{it.id}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                choices.forEach { item ->
                    val selected=selectedItems.any{it.id==item.id}
                    SelectionChip(selected,{a=a.copy(care=null,careItems=if(selected)selectedItems.filterNot{it.id==item.id}else selectedItems+item)},
                        label={Text(item.name)},leadingIcon={Icon(careIcon(item.iconKey).image,null,Modifier.size(22.dp))},
                        modifier=Modifier.semantics{contentDescription="${item.name} 치료 항목 선택"})
                }
            }
            selectedItems.forEach { item ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(item.name,Modifier.weight(1f))
                    TextButton(onClick={careTimeTarget=item.id}){Text(item.time ?: "시간 지정")}
                    if(item.time!=null)IconButton(onClick={a=a.copy(careItems=selectedItems.map{if(it.id==item.id)it.copy(time=null)else it})}) {
                        Icon(Icons.Outlined.Close,"${item.name} 시간 해제")
                    }
                }
            }
            Hint("시간이 필요한 항목만 지정해 주세요. 진료 전후 시각을 자유롭게 정할 수 있어요.")
            TextButton(onClick={navigate("careTemplates")}){Text("검사·치료 항목 등록·관리")}
            Hint("할 일을 하나씩 등록하고 필요한 항목을 여러 개 고르세요. 선택한 항목을 다시 누르면 해제해요.")
        }
        Paper {OutlinedTextField(a.memo,{a=a.copy(memo=it)},label={Text("메모")},modifier=Modifier.fillMaxWidth())}
    }
    timeTarget?.let { target ->
        val value=if(target.isEmpty())a.time else a.departmentTimes[target] ?: a.time
        val initial=runCatching{LocalTime.parse(value)}.getOrDefault(LocalTime.of(9,0))
        val clock=rememberTimePickerState(initialHour=initial.hour,initialMinute=initial.minute,is24Hour=true)
        AlertDialog(onDismissRequest={timeTarget=null},title={Text(if(target.isEmpty())"예약시간"else"${a.departments.find{it.id==target}?.name} 예약시간")},text={TimeInput(clock)},
            confirmButton={TextButton(onClick={
                val value=String.format(Locale.US,"%02d:%02d",clock.hour,clock.minute)
                a=if(target.isEmpty())a.copy(time=value) else a.copy(departmentTimes=a.departmentTimes+(target to value));timeTarget=null
            }){Text("확인")}},dismissButton={TextButton(onClick={timeTarget=null}){Text("취소")}})
    }
    careTimeTarget?.let { target ->
        val item=a.selectedCareItems().firstOrNull{it.id==target}
        if(item!=null)key(target) {
            val initial=runCatching{LocalTime.parse(item.time ?: a.time)}.getOrDefault(LocalTime.of(9,0))
            val clock=rememberTimePickerState(initialHour=initial.hour,initialMinute=initial.minute,is24Hour=true)
            AlertDialog(onDismissRequest={careTimeTarget=null},title={Text("${item.name} 시간")},text={TimeInput(clock)},
                confirmButton={TextButton(onClick={
                    val value=String.format(Locale.US,"%02d:%02d",clock.hour,clock.minute)
                    a=a.copy(careItems=a.selectedCareItems().map{if(it.id==target)it.copy(time=value)else it});careTimeTarget=null
                }){Text("확인")}},dismissButton={TextButton(onClick={careTimeTarget=null}){Text("취소")}})
        }
    }
}

@Composable private fun AppointmentTimeInput(label:String,value:String,onChange:(String)->Unit,clockLabel:String,openClock:()->Unit) {
    val valid=validAppointmentTime(value)
    OutlinedTextField(value,onChange,label={Text(label)},placeholder={Text("예: 09:30")},
        modifier=Modifier.fillMaxWidth(),singleLine=true,isError=value.isNotBlank() && !valid,
        supportingText={if(value.isNotBlank() && !valid)Text("00:00~23:59 형식으로 입력해 주세요.")})
    TextButton(onClick=openClock){Icon(Icons.Outlined.Schedule,null);Spacer(Modifier.width(8.dp));Text(clockLabel)}
}

@Composable fun DepartmentsScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit,back:()->Unit) {
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    Page("진료과 관리","이름과 색상을 등록하고 예약에서 여러 개 선택해요",back) {
        Action("+ 진료과 등록",{navigate("department/new")})
        if(s.departments.isEmpty())Paper{Hint("자주 방문하는 진료과를 등록해 주세요.")}
        s.departments.sortedBy{it.name}.forEach { d -> Paper {
            DepartmentTags(listOf(d))
            Row {
                TextButton(onClick={navigate("department/${d.id}")},modifier=Modifier.semantics{contentDescription="${d.name} 수정"}){Text("수정")}
                TextButton(onClick={deleting=d.id},modifier=Modifier.semantics{contentDescription="${d.name} 삭제"}){Text("삭제")}
            }
        }}
    }
    deleting?.let{id->Confirm("진료과를 삭제할까요?","이미 저장한 예약의 진료과 이름과 색상은 유지돼요.",{deleting=null}){
        vm.act("진료과를 삭제했어요"){vm.repository.deleteDepartment(id);deleting=null}
    }}
}

@Composable fun DepartmentEditor(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    var d by rememberJsonState("department:$id"){s.departments.find{it.id==id} ?: Department(name="")}
    val original=rememberSaveable{codec.encodeToString(d)}
    var picker by rememberSaveable{mutableStateOf(false)}
    EditorPage(if(id=="new")"진료과 등록"else"진료과 수정","색상 태그로 구분해요",codec.encodeToString(d)!=original,back,{
        vm.act("진료과를 저장했어요"){vm.repository.department(d.copy(name=d.name.trim()));back()}
    },d.name.isNotBlank(),busy=vm.busy.collectAsState().value) {
        Paper {OutlinedTextField(d.name,{d=d.copy(name=it)},label={Text("진료과 이름")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        Paper {
            Section("진료과 색상");DepartmentTags(listOf(d.copy(name=d.name.ifBlank{"진료과"})))
            ColorPalette(s.preferences.palette,d.color){d=d.copy(color=it)}
            TextButton(onClick={picker=!picker}){Text(if(picker)"컬러 피커 접기"else"컬러 피커 열기")}
            if(picker)InlineColorPicker(d.color){d=d.copy(color=it)}
        }
    }
}

@Composable fun CareTemplatesScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit,back:()->Unit) {
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    Page("검사·치료 항목","자주 하는 검사·치료를 한 항목씩 등록해요",back) {
        Action("+ 치료 항목 등록",{navigate("care/new")})
        if(s.careTemplates.isEmpty())Paper{Hint("피검사·투석실 방문·주사 등을 각각 등록해 주세요. 예약에서 여러 항목을 함께 선택할 수 있어요.")}
        s.careTemplates.sortedBy{it.name}.forEach { c -> Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(careIcon(c.iconKey).image,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary);Section(c.name)
            }
            Row {
                TextButton(onClick={navigate("care/${c.id}")},modifier=Modifier.semantics{contentDescription="${c.name} 수정"}){Text("수정")}
                TextButton(onClick={deleting=c.id},modifier=Modifier.semantics{contentDescription="${c.name} 삭제"}){Text("삭제")}
            }
        }}
    }
    deleting?.let{id->Confirm("치료 항목을 삭제할까요?","이미 저장한 예약의 치료 항목과 아이콘은 유지돼요.",{deleting=null}){
        vm.act("치료 항목을 삭제했어요"){vm.repository.deleteCareTemplate(id);deleting=null}
    }}
}

@Composable fun CareTemplateEditor(s:Snapshot,vm:JournalViewModel,id:String,back:()->Unit) {
    var c by rememberJsonState("care:$id"){s.careTemplates.find{it.id==id} ?: CareTemplate(name="")}
    val original=rememberSaveable{codec.encodeToString(c)}
    var icons by rememberSaveable{mutableStateOf(false)}
    EditorPage(if(id=="new")"치료 항목 등록"else"치료 항목 수정","이름과 아이콘을 지정하고 예약에서 여러 항목을 골라요",codec.encodeToString(c)!=original,back,{
        vm.act("치료 항목을 저장했어요"){vm.repository.careTemplate(c.copy(name=c.name.trim(),tasks=emptyList()));back()}
    },c.name.isNotBlank(),busy=vm.busy.collectAsState().value) {
        Paper {
            OutlinedTextField(c.name,{c=c.copy(name=it)},label={Text("치료 항목 이름")},placeholder={Text("예: 피검사")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            TextButton(onClick={icons=!icons}) {
                Icon(careIcon(c.iconKey).image,null);Spacer(Modifier.width(8.dp));Text("아이콘 · ${careIcon(c.iconKey).label}")
            }
            if(icons)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                careIcons.forEach { option ->
                    SelectionChip(c.iconKey==option.key,{c=c.copy(iconKey=option.key);icons=false},
                        label={Text(option.label)},leadingIcon={Icon(option.image,null,Modifier.size(22.dp))},
                        modifier=Modifier.semantics{contentDescription="${option.label} 아이콘"})
                }
            }
        }
    }
}
