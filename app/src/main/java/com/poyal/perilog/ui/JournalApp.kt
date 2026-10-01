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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun JournalApp(vm:JournalViewModel=viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val date by vm.calendarDay.collectAsStateWithLifecycle()
    var stack by rememberSaveable{mutableStateOf(listOf("home"))}
    var tab by rememberSaveable{mutableStateOf("home")}
    var editingId by rememberSaveable{mutableStateOf<String?>(null)}
    val route=stack.last()
    val rootTabs=listOf("home","records","stats","stock")
    val management=route.contains('/')
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(Unit){vm.message.collect{snackbar.showSnackbar(it)}}
    LaunchedEffect(route){snackbar.currentSnackbarData?.dismiss()}
    fun navigate(to:String){if(to in rootTabs){stack=listOf(to);tab=to}else stack=stack+to}
    fun back(){if(stack.size>1)stack=stack.dropLast(1)}
    fun editor(id:String?=null,kind:String="MACHINE",day:String=com.poyal.perilog.data.today()){
        vm.edit(id,kind,day);editingId=vm.editor.value?.id;navigate("edit")
    }
    LaunchedEffect(ready,route){if(ready && route=="edit" && vm.editor.value==null){if(editingId!=null && (s.treatments.any{it.id==editingId} || s.drafts.any{it.id==editingId}))vm.edit(editingId)else back()}}
    BackHandler(stack.size>1 && !management){back()}
    CompositionLocalProvider(LocalInputErrors provides vm.inputErrors){PerilogTheme(s.preferences.darkMode){
        val colors=MaterialTheme.colorScheme
        Scaffold(containerColor=Color.Transparent,contentColor=colors.onBackground,snackbarHost={SnackbarHost(snackbar)},
            bottomBar={if(!management && !WindowInsets.isImeVisible)NavigationBar(containerColor=colors.surface.copy(alpha=.97f),tonalElevation=0.dp){
                listOf(Triple("home","홈",Icons.Outlined.Home),Triple("records","기록",Icons.Outlined.Description),Triple("stats","통계",Icons.Outlined.BarChart),Triple("stock","재고",Icons.Outlined.Inventory2)).forEach{(id,label,icon)->
                    NavigationBarItem(selected=tab==id,onClick={navigate(id)},icon={Icon(icon,label)},label={Text(label)},
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=colors.primary,selectedTextColor=colors.primary,indicatorColor=colors.primaryContainer,unselectedIconColor=colors.onSurfaceVariant,unselectedTextColor=colors.onSurfaceVariant))
                }
            }}){padding->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background,androidx.compose.ui.graphics.lerp(colors.background,colors.primaryContainer,.45f))))
                .padding(padding).consumeWindowInsets(padding).imePadding()) {
                if(!ready)CircularProgressIndicator(Modifier.align(Alignment.Center))else key(route){when {
                    route=="home"->HomeScreen(s,vm,date,{navigate("settings")},{id,kind,day->editor(id,kind,day)},{navigate("records")},{navigate("stock")})
                    route=="records"->RecordsScreen(s,vm,snackbar){id,kind,day->editor(id,kind,day)}
                    route=="stats"->StatsScreen(s,vm){editor(it)}
                    route=="stock"->StockScreen(s,vm,::navigate)
                    route=="products"->ProductsScreen(s,::navigate,::back)
                    route=="templates"->TemplatesScreen(s,vm,::navigate,::back)
                    route=="receipts"->ReceiptsScreen(s,vm,::navigate,::back)
                    route=="settings"->SettingsScreen(s,vm,::navigate,::back)
                    route=="edit"->TreatmentScreen(s,vm,::back)
                    route.startsWith("product/")->ProductEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("template/")->TemplateEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("receipt/")->ReceiptEditor(s,vm,route.substringAfter('/'),::back)
                    route.startsWith("stock/")->StockDetailScreen(s,vm,route.substringAfter('/'),::back)
                }}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }}
}
