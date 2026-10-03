@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.poyal.perilog.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.serialization.encodeToString

fun contactIntent(phone:String,sms:Boolean):Intent = Intent(if(sms)Intent.ACTION_SENDTO else Intent.ACTION_DIAL,
    Uri.fromParts(if(sms)"smsto"else"tel",dialNumber(phone),null))

private fun openContact(context:Context,c:Contact,sms:Boolean,vm:JournalViewModel) {
    if(if(sms)!c.allowSms else !c.allowCall)return
    try { context.startActivity(contactIntent(c.phone,sms)) }
    catch(_:ActivityNotFoundException) {vm.act{vm.message.emit(if(sms)"문자를 작성할 수 있는 앱이 없어요."else"전화 앱을 찾을 수 없어요.")}}
    catch(_:SecurityException) {vm.act{vm.message.emit("연결할 앱을 열 수 없어요.")}}
}

private val contactEmojis=listOf(
    "👤" to "기본", "🏥" to "병원", "🧑‍⚕️" to "의료진", "👩‍⚕️" to "여성 의료진", "👨‍⚕️" to "남성 의료진",
    "🩺" to "청진기", "💉" to "주사", "💊" to "약", "☎️" to "전화기", "📞" to "전화", "👩" to "여성", "👨" to "남성",
    "🙂" to "미소", "😊" to "웃는 얼굴", "💙" to "파란 하트", "💚" to "초록 하트", "❤️" to "빨간 하트",
    "🌷" to "튤립", "🌼" to "꽃", "🌻" to "해바라기", "🐶" to "강아지", "🐱" to "고양이", "🐻" to "곰", "🐰" to "토끼")

@Composable private fun ContactAvatar(c:Contact) {
    Surface(modifier=Modifier.size(64.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment=Alignment.Center) {
            Text(c.emoji.ifBlank{"👤"},fontSize=30.sp,lineHeight=36.sp,modifier=Modifier.clearAndSetSemantics{})
        }
    }
}

@Composable private fun ContactActions(c:Contact,vm:JournalViewModel) {
    val context=LocalContext.current
    if(!c.allowCall && !c.allowSms)Hint("이 연락처의 전화·문자 연결이 꺼져 있어요.")
    else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(c.allowCall) {
            OutlinedButton(onClick={openContact(context,c,false,vm)},modifier=Modifier.semantics{contentDescription="${c.name} 전화"}) {
                Icon(Icons.Outlined.Call,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("전화")
            }
        }
        if(c.allowSms) {
            OutlinedButton(onClick={openContact(context,c,true,vm)},modifier=Modifier.semantics{contentDescription="${c.name} 문자"}) {
                Icon(Icons.Outlined.Sms,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("문자")
            }
        }
    }
}

@Composable private fun ContactRow(c:Contact,vm:JournalViewModel) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        ContactAvatar(c)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Section(c.name);Text(c.phone,style=MaterialTheme.typography.bodyLarge);ContactActions(c,vm)
        }
    }
}

@Composable fun HomeContacts(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit) {
    var connecting by rememberSaveable{mutableStateOf<String?>(null)}
    val contacts=s.contacts.sortedWith(compareBy<Contact>{it.createdAt}.thenBy{it.id})
    Paper {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ContactPhone,null,tint=MaterialTheme.colorScheme.primary);Section("연락처")
        }
        if(contacts.isEmpty()) {
            Hint("자주 연락하는 곳의 이름과 번호를 등록해 주세요.")
            Action("연락처 등록",{navigate("contact/new")},icon=Icons.Outlined.Add)
        } else contacts.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    Column(Modifier.weight(1f).clickable(role=Role.Button){connecting=c.id}
                        .semantics{contentDescription="${c.name} 연락처"}.padding(vertical=6.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        ContactAvatar(c)
                        Text(c.name,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
                repeat(3-row.size){Spacer(Modifier.weight(1f))}
            }
        }
    }
    contacts.find{it.id==connecting}?.let { c ->
        ModalBottomSheet(onDismissRequest={connecting=null}) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Section("연결 방법 선택")
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    ContactAvatar(c)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Section(c.name);Text(c.phone)}
                }
                ContactActions(c,vm)
                TextButton(onClick={connecting=null}){Text("닫기")}
            }
        }
    }
}

@Composable fun ContactsScreen(s:Snapshot,vm:JournalViewModel,navigate:(String)->Unit,back:()->Unit) {
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    Page("연락처 관리","등록한 이름과 번호를 홈에서 바로 확인해요",back) {
        Action("+ 연락처 등록",{navigate("contact/new")})
        if(s.contacts.isEmpty())Paper{Hint("투석실·고객센터·간호사 등 자주 연락하는 곳을 직접 등록해 주세요.")}
        s.contacts.sortedWith(compareBy<Contact>{it.createdAt}.thenBy{it.id}).forEach { c -> Paper {
            ContactRow(c,vm)
            Row {
                TextButton(onClick={navigate("contact/${c.id}")},modifier=Modifier.semantics{contentDescription="${c.name} 수정"}){Text("수정")}
                TextButton(onClick={deleting=c.id},modifier=Modifier.semantics{contentDescription="${c.name} 삭제"}){Text("삭제")}
            }
        }}
    }
    deleting?.let{id->Confirm("연락처를 삭제할까요?","선택한 연락처가 홈에서도 사라져요.",{deleting=null}){
        vm.act("연락처를 삭제했어요"){vm.repository.deleteContact(id);deleting=null}
    }}
}

@Composable fun ContactEditor(s:Snapshot,vm:JournalViewModel,id:String,creating:Boolean,back:()->Unit) {
    val existing=s.contacts.find{it.id==id}
    if(!creating && existing==null) {
        Page("연락처 수정",back=back){Paper{Hint("연락처가 삭제되었어요. 목록에서 다시 선택해 주세요.")}}
        return
    }
    var c by rememberJsonState("contact:$id:$creating"){if(creating)Contact(id=id,name="")else requireNotNull(existing)}
    val original=rememberSaveable(id,creating){codec.encodeToString(c)}
    val valid=validPhone(c.phone)
    var emojis by rememberSaveable(id,creating){mutableStateOf(false)}
    EditorPage(if(creating)"연락처 등록"else"연락처 수정","아바타와 연결 방법을 설정해요",codec.encodeToString(c)!=original,back,{
        val contact=c.copy(id=id,name=c.name.trim(),phone=c.phone.trim())
        vm.act("연락처를 저장했어요"){
            if(creating)vm.repository.createContact(contact)else vm.repository.updateContact(contact)
            back()
        }
    },c.name.isNotBlank() && valid,busy=vm.busy.collectAsState().value) {
        Paper {
            Section("이모지 아바타")
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                ContactAvatar(c);TextButton(onClick={emojis=!emojis}){Text(if(emojis)"이모지 선택 접기"else"이모지 선택")}
            }
            if(emojis)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                contactEmojis.forEach { (emoji,label) ->
                    Surface(onClick={c=c.copy(emoji=emoji);emojis=false},shape=CircleShape,
                        color=if(c.emoji==emoji)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border=BorderStroke(if(c.emoji==emoji)2.dp else 1.dp,if(c.emoji==emoji)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier=Modifier.size(52.dp).semantics{contentDescription="$label 이모지";selected=c.emoji==emoji}) {
                        Box(contentAlignment=Alignment.Center){Text(emoji,fontSize=26.sp,lineHeight=32.sp,modifier=Modifier.clearAndSetSemantics{})}
                    }
                }
            }
        }
        Paper {
            OutlinedTextField(c.name,{c=c.copy(name=it)},label={Text("연락처 이름")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(c.phone,{c=c.copy(phone=it)},label={Text("전화번호")},modifier=Modifier.fillMaxWidth(),singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),isError=c.phone.isNotBlank() && !valid,
                supportingText={if(c.phone.isNotBlank() && !valid)Text("전화번호의 숫자와 구분 기호를 확인해 주세요.")})
        }
        Paper {
            Section("허용할 연결 방법")
            ContactPermission("전화 허용",c.allowCall){c=c.copy(allowCall=it)}
            ContactPermission("문자 허용",c.allowSms){c=c.copy(allowSms=it)}
            Hint("홈에서 아바타를 누르면 허용한 연결 방법만 표시해요.")
        }
    }
}

@Composable private fun ContactPermission(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(checked,role=Role.Switch,onValueChange=onChange),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.weight(1f));Switch(checked,onCheckedChange=null)
    }
}
