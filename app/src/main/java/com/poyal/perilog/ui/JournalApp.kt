@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poyal.perilog.widget.WidgetOpenRequest
import com.poyal.perilog.domain.dailyProgress

@Composable fun JournalApp(vm:JournalViewModel=viewModel(), unlocked:Boolean=true,
    widgetRequest:WidgetOpenRequest?=null,onWidgetHandled:()->Unit={}) {
    val updates = vm.app.updates
    val updateState by updates.state.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val s by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val date by vm.calendarDay.collectAsStateWithLifecycle()
    val now by vm.localNow.collectAsStateWithLifecycle()
    val screenState=rememberSaveableStateHolder()
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var stack by rememberSaveable{mutableStateOf(listOf("home"))}
    var tab by rememberSaveable{mutableStateOf("home")}
    var editingId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(ready, unlocked) { if (ready && unlocked) updates.startSession() }
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)vm.refreshClock()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(lifecycle, unlocked) {
        if (unlocked) lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                updates.refreshDownload()
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    val route=stack.last()
    val rootTabs=listOf("home","records","stats","stock")
    val editing=route.contains('/')
    val management=editing || route in listOf("appointments","departments","careTemplates","contacts","stockHistory","receipts","recordTable","requests","guide","widgets","settings","about","updates")
    val snackbar=remember{SnackbarHostState()}
    var feedbackHeight by remember {mutableIntStateOf(0)}
    val feedbackInset=with(LocalDensity.current){feedbackHeight.toDp()}
    LaunchedEffect(Unit){vm.message.collectLatest{snackbar.showSnackbar(it)}}
    FirstNotificationPermission(ready && unlocked && route=="home" && !updateState.prompt,vm)
    fun navigate(to:String){
        snackbar.currentSnackbarData?.dismiss()
        focus.clearFocus();keyboard?.hide()
        // Each new contact owns its saved state, including across activity recreation.
        val destination=when(to) {
            "contact/new"->"contact/new/${com.poyal.perilog.data.newId()}"
            "request/new"->"request/new/${com.poyal.perilog.data.newId()}"
            else->to
        }
        if(destination in rootTabs){stack.filter{it!=destination}.forEach{screenState.removeState(it)};stack=listOf(destination);tab=destination}
        else if(destination!=route)stack=stack+destination
    }
    fun back(){if(stack.size>1){snackbar.currentSnackbarData?.dismiss();focus.clearFocus();keyboard?.hide();screenState.removeState(stack.last());stack=stack.dropLast(1)}}
    fun editor(id:String?=null,kind:String="MACHINE",day:String=com.poyal.perilog.data.today()){
        vm.edit(id,kind,day);editingId=vm.editor.value?.id;navigate("edit")
    }
    LaunchedEffect(widgetRequest?.id,ready,unlocked,busy) {
        val request=widgetRequest ?: return@LaunchedEffect
        if(!ready || !unlocked || busy)return@LaunchedEffect
        if(vm.inputErrors.isNotEmpty()) {
            onWidgetHandled()
            vm.message.emit("입력 중인 숫자 형식을 먼저 확인해 주세요. 작성한 내용은 유지했어요.")
            return@LaunchedEffect
        }
        val fresh=try {vm.flushDraft();vm.repository.snapshot()} catch(e:Exception) {
            if(e is kotlinx.coroutines.CancellationException)throw e
            onWidgetHandled();vm.message.emit("작성 중인 기록을 보관하지 못했어요. 다시 시도해 주세요.")
            return@LaunchedEffect
        }
        val target=request.target
        // Consume once before changing routes; activity recreation must not repeat an old tap.
        snackbar.currentSnackbarData?.dismiss()
        onWidgetHandled()
        when(target.screen) {
            "record" -> {
                if(target.valid()) {
                    val day=fresh.dailyProgress(target.value)
                    // Treatment input owns one ViewModel editor. Persist the previous draft and
                    // remove old editor entries while retaining other forms' SaveableStateProvider.
                    focus.clearFocus();keyboard?.hide()
                    if("edit" in stack)screenState.removeState("edit")
                    vm.edit(day.resume?.id,day.resume?.kind ?: "MACHINE",target.value,fresh)
                    editingId=vm.editor.value?.id
                    stack=stack.filterNot {it=="edit"}.ifEmpty {listOf("home")}+"edit"
                } else navigate("home")
            }
            "appointment" -> navigate(if(fresh.appointments.any {it.id==target.value})"appointment/${target.value}"else"appointments")
            "appointments" -> navigate("appointments")
            "newAppointment" -> navigate("appointment/new")
            else -> navigate("home")
        }
    }
    LaunchedEffect(ready,route){if(ready && route=="edit" && vm.editor.value==null){if(editingId!=null && (s.treatments.any{it.id==editingId} || s.drafts.any{it.id==editingId}))vm.edit(editingId)else back()}}
    val readOnly=route in listOf("settings/transfer","settings/protection","settings/reset","settings/palette") || route.startsWith("stock/") || route.startsWith("stockHistory/") || route.startsWith("guide/") || route.startsWith("requestDetail/") || route.startsWith("appointmentStock/") || route.startsWith("appointmentDetail/")
    BackHandler(stack.size>1 && (!editing || readOnly)){back()}
    CompositionLocalProvider(LocalInputErrors provides vm.inputErrors,LocalHelpAction provides {navigate("guide/${if(route=="edit" && vm.editor.value?.kind=="MANUAL")"manual"else guideForRoute(route)}")}){PerilogTheme(s.preferences.darkMode){
        val colors=MaterialTheme.colorScheme
        if (ready && unlocked) UpdatePrompt(updateState, updates::dismissPrompt) {
            updates.dismissPrompt(); navigate("updates"); updates.download()
        }
        Scaffold(containerColor=Color.Transparent,contentColor=colors.onBackground,snackbarHost={
            Box(Modifier.imePadding()){SnackbarHost(snackbar,Modifier.onSizeChanged{feedbackHeight=it.height})}
        },
            bottomBar={if(!management && !WindowInsets.isImeVisible)NavigationBar(containerColor=colors.surface.copy(alpha=.97f),tonalElevation=0.dp){
                listOf(Triple("home","홈",Icons.Outlined.Home),Triple("records","기록",Icons.Outlined.Description),Triple("stats","통계",Icons.Outlined.BarChart),Triple("stock","재고",Icons.Outlined.Inventory2)).forEach{(id,label,icon)->
                    NavigationBarItem(selected=tab==id,onClick={navigate(id)},icon={Icon(icon,label)},label={Text(label)},
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=colors.primary,selectedTextColor=colors.primary,indicatorColor=colors.primaryContainer,unselectedIconColor=colors.onSurfaceVariant,unselectedTextColor=colors.onSurfaceVariant))
                }
            }}){padding->
            Box(Modifier.fillMaxSize().background(colors.background)
                .padding(padding).consumeWindowInsets(padding).imePadding().padding(bottom=feedbackInset)) {
                if(!ready)CircularProgressIndicator(Modifier.align(Alignment.Center))else key(route){screenState.SaveableStateProvider(route){when {
                    route=="home"->HomeScreen(s,vm,date,{navigate("settings")},{id,kind,day->editor(id,kind,day)},::navigate,{navigate("stock")})
                    route=="records"->RecordsScreen(s,vm,snackbar,{navigate("recordTable")}){id,kind,day->editor(id,kind,day)}
                    route=="recordTable"->RecordTableScreen(s,vm,::back){t->editor(t.id,t.kind,t.date)}
                    route=="stats"->StatsScreen(s,vm){editor(it)}
                    route=="stock"->StockScreen(s,vm,::navigate)
                    route=="requests"->ReplenishmentListScreen(s,::navigate,::back)
                    route=="guide"->GuideScreen(null,::navigate,::back)
                    route.startsWith("guide/")->GuideScreen(route.substringAfter('/'),::navigate,::back)
                    route.startsWith("requestDetail/")->ReplenishmentDetailScreen(s,route.substringAfter('/'),::navigate,::back)
                    route.startsWith("requestReceive/")->RequestReceiptScreen(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("appointmentStock/")->StockForecastScreen(s,vm,route.substringAfterLast('/'),now,::navigate,::back)
                    route.startsWith("appointmentDetail/")->AppointmentDetailScreen(s,route.substringAfter('/'),now,::navigate,::back)
                    route.startsWith("request/")->ReplenishmentEditor(s,vm,route.substringAfterLast('/'),if(route.startsWith("request/copy/"))route.split('/')[2]else null,::navigate,::back)
                    route=="products"->ProductsScreen(s,::navigate,::back)
                    route=="templates"->TemplatesScreen(s,vm,::navigate,::back)
                    route=="stockHistory" || route=="receipts"->StockHistoryScreen(s,vm,null,::navigate,::back){editor(it)}
                    route=="about"->AboutScreen(updates,::back)
                    route=="updates"->UpdatesScreen(updates,::back)
                    route=="settings"->SettingsScreen(s,::navigate,::back)
                    route in listOf("settings/display","settings/notifications","settings/lock","settings/backup","settings/basis")->PreferenceSettingsScreen(s,vm,route.substringAfter('/'),::back)
                    route in listOf("settings/transfer","settings/protection","settings/reset")->DataSettingsScreen(s,vm,route.substringAfter('/'),::back)
                    route=="settings/palette"->PaletteSettingsScreen(s,vm,::back)
                    route=="widgets"->WidgetSettingsScreen(::back)
                    route=="edit"->key(editingId){TreatmentScreen(s,vm,::back)}
                    route=="appointments"->AppointmentsScreen(s,vm,now,::navigate,::back)
                    route=="departments"->DepartmentsScreen(s,vm,::navigate,::back)
                    route=="careTemplates"->CareTemplatesScreen(s,vm,::navigate,::back)
                    route=="contacts"->ContactsScreen(s,vm,::navigate,::back)
                    route=="contacts/order"->ContactOrderScreen(s,vm,::back)
                    route.startsWith("appointment/next/")->AppointmentEditor(s,vm,route.substringAfterLast('/'),true,::navigate,::back)
                    route.startsWith("appointment/")->AppointmentEditor(s,vm,route.substringAfter('/'),false,::navigate,::back)
                    route.startsWith("department/")->DepartmentEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("care/")->CareTemplateEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("contact/")->ContactEditor(s,vm,route.substringAfterLast('/'),route.startsWith("contact/new/"),::back)
                    route.startsWith("product/")->ProductEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("template/")->TemplateEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("receipt/")->ReceiptEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("stockHistory/")->StockHistoryScreen(s,vm,route.substringAfter('/'),::navigate,::back){editor(it)}
                    route.startsWith("stock/")->StockDetailScreen(s,route.substringAfter('/'),::navigate,::back)
                    route.startsWith("count/")->StockCountScreen(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("adjustment/")->StockAdjustmentScreen(s,vm,route.substringAfter('/'),::back)
                }}}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }}
}
