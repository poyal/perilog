package com.poyal.perilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poyal.perilog.data.*

@Composable fun JournalApp(vm: JournalViewModel=viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val date by vm.calendarDay.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var tab by rememberSaveable { mutableStateOf("home") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    fun navigate(to:String) { route=to; if(to in listOf("home","records","stats","stock")) tab=to }
    fun editor(id:String?=null,kind:String="MACHINE",date:String=today()) { vm.edit(id,kind,date);editingId=vm.editor.value?.id;route="edit" }
    LaunchedEffect(ready,route) { if(ready && route=="edit" && vm.editor.value==null) { if(editingId!=null && (s.treatments.any{it.id==editingId} || s.drafts.any{it.id==editingId}))vm.edit(editingId)else route=tab } }
    BackHandler(route!=tab) { route=tab }
    CompositionLocalProvider(LocalInputErrors provides vm.inputErrors) { PerilogTheme(s.preferences.darkMode) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},
            bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                listOf(Triple("home","홈",Icons.Outlined.Home),Triple("records","기록",Icons.Outlined.MenuBook),Triple("stats","통계",Icons.Outlined.BarChart),Triple("stock","재고",Icons.Outlined.Inventory2)).forEach{(id,label,icon)->
                    NavigationBarItem(selected=tab==id,onClick={navigate(id)},icon={Icon(icon,label)},label={Text(label)},
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                }
            }}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if(!ready) CircularProgressIndicator(Modifier.align(Alignment.Center)) else when(route) {
                    "home" -> HomeScreen(s,vm,date,{navigate("settings")},{id,kind,date->editor(id,kind,date)},{navigate("records")},{navigate("stock")})
                    "records" -> RecordsScreen(s,vm,snackbar,{id,kind,date->editor(id,kind,date)})
                    "stats" -> StatsScreen(s,vm){editor(it)}
                    "stock" -> StockScreen(s,vm,{navigate(it)})
                    "products" -> ProductsScreen(s,vm){navigate("stock")}
                    "templates" -> TemplatesScreen(s,vm){navigate("stock")}
                    "receipts" -> ReceiptsScreen(s,vm){navigate("stock")}
                    "settings" -> SettingsScreen(s,vm){navigate(tab)}
                    "edit" -> TreatmentScreen(s,vm){navigate(tab)}
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    } }
}
