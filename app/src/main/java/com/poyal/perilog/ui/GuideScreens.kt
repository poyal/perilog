package com.poyal.perilog.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalHelpAction = staticCompositionLocalOf<(() -> Unit)?> { null }

data class GuideStep(val title: String, val text: String, val image: String? = null)
data class GuideTopic(val id: String, val title: String, val keywords: String, val steps: List<GuideStep>)

fun guideForRoute(route: String): String = when {
    route=="home" -> "start"
    route=="edit" -> "daily"
    route=="records" || route=="recordTable" -> "records"
    route=="stats" -> "statistics"
    route.startsWith("requestReceive/") -> "request-receive"
    route=="requests" || route.startsWith("request/") || route.startsWith("requestDetail/") -> "request"
    route.startsWith("template") -> "patterns"
    route.startsWith("product") -> "setup"
    route.startsWith("appointment") || route=="departments" || route=="careTemplates" || route.startsWith("department/") || route.startsWith("care/") -> "appointments"
    route.startsWith("contact") -> "contacts"
    route=="about" -> "updates"
    route=="settings" -> "settings"
    route=="widgets" -> "widgets"
    else -> "stock"
}

@Composable fun GuideScreen(topicId: String?, navigate: (String)->Unit, back: ()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val topic=guideTopics.find { it.id==topicId }
    CompositionLocalProvider(LocalHelpAction provides null) {
        Page(topic?.title ?: "사용 안내",if(topic==null) "인터넷 없이 앱 사용법을 확인해요" else "실제 화면을 보며 차근차근 따라 해 보세요",back) {
            if(topic==null) {
                Paper {OutlinedTextField(query,{query=it},label={Text("사용법 검색")},placeholder={Text("예: 입고, 백업, 기록 수정")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                val words=query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val found=guideTopics.filter { t->val text=t.title+" "+t.keywords+" "+t.steps.joinToString { it.title+" "+it.text };words.all { text.contains(it,ignoreCase=true) } }
                if(found.isEmpty()) Paper {Hint("검색한 사용법을 찾지 못했어요. 다른 단어로 찾아보세요.")}
                found.forEach { t->Paper {MenuRow(t.title,icon=Icons.Outlined.MenuBook) {navigate("guide/${t.id}")}} }
            } else {
                TextButton(onClick={navigate("guide")}) {Icon(Icons.Outlined.MenuBook,null);Spacer(Modifier.width(8.dp));Text("전체 사용 안내")}
                topic.steps.forEachIndexed { index,step->Paper(Modifier.testTag("guide-${topic.id}-$index")) {
                    Section("${index+1}. ${step.title}")
                    Text(step.text)
                    step.image?.let { GuideScreenshot(it,step.title) }
                } }
                Hint("화면의 날짜와 수량은 사용법을 설명하기 위한 예시예요.")
            }
        }
    }
}

@Composable private fun GuideScreenshot(name: String, description: String) {
    val context=LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null,name) {
        value=withContext(Dispatchers.IO) {runCatching {context.assets.open("guide/$name.png").use {BitmapFactory.decodeStream(it)?.asImageBitmap()} }.getOrNull()}
    }
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    bitmap?.let { img->
        Image(img,"$description 실제 화면 · 눌러서 확대",modifier=Modifier.fillMaxWidth().heightIn(max=480.dp).clickable {expanded=true},contentScale=ContentScale.Fit)
        TextButton(onClick={expanded=true}) {Text("화면 크게 보기")}
        if(expanded) Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Surface(Modifier.fillMaxSize()) {Column {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={scale=1f;offset=Offset.Zero}) {Text("크기 초기화")}
                    IconButton(onClick={expanded=false}) {Icon(Icons.Outlined.Close,"확대 화면 닫기")}
                }
                Text("두 손가락으로 확대하고 움직여 보세요",Modifier.padding(horizontal=20.dp))
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(Unit) {detectTransformGestures { _,pan,zoom,_->
                    scale=(scale*zoom).coerceIn(1f,5f)
                    offset=if(scale==1f)Offset.Zero else (offset+pan).let {Offset(it.x.coerceIn(-size.width*(scale-1),size.width*(scale-1)),it.y.coerceIn(-size.height*(scale-1),size.height*(scale-1)))}
                }},contentAlignment=Alignment.Center) {
                    Image(img,description,Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)
                }
            } }
        }
    }
}
