package com.poyal.perilog.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.encodeToString
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import com.poyal.perilog.backup.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JournalViewModel(application: Application,private val savedState:SavedStateHandle): AndroidViewModel(application) {
    val app=application as PerilogApplication
    val repository=app.repository
    val state=repository.snapshots.stateIn(viewModelScope,SharingStarted.Eagerly,Snapshot())
    val ready=MutableStateFlow(false)
    val busy=MutableStateFlow(false)
    val message=MutableSharedFlow<String>(extraBufferCapacity=8)
    val editor=MutableStateFlow(savedState.get<String>("editor")?.let{codec.decodeFromString<Treatment>(it)})
    val calendarDay=MutableStateFlow(today())
    val localNow=MutableStateFlow(java.time.LocalDateTime.now())
    val inputErrors=mutableStateMapOf<String,String>()
    val recordFilters=RecordFilters(savedState["recordFilters"])
    val statsFilters=StatsFilters(savedState["statsFilters"])
    private val serial=Mutex()
    private var draftJob: Job?=null
    private var pendingDraft: Treatment?=null
    init {
        savedState.setSavedStateProvider("recordFilters"){recordFilters.save()}
        savedState.setSavedStateProvider("statsFilters"){statsFilters.save()}
        viewModelScope.launch { repository.snapshots.first(); ready.value=true }
        viewModelScope.launch { while(isActive) { refreshClock();delay(30000) } }
    }
    fun refreshClock() { localNow.value=java.time.LocalDateTime.now();calendarDay.value=localNow.value.toLocalDate().toString() }
    fun act(success: String?=null, block: suspend () -> Unit) {
        // Claim the operation synchronously so double taps cannot queue duplicate writes.
        if(busy.value)return
        busy.value=true
        viewModelScope.launch {
            try { serial.withLock {
                if(inputErrors.isNotEmpty()) { message.emit("입력 형식을 확인해 주세요: ${inputErrors.values.joinToString()}");return@withLock }
                block(); if(success!=null) message.emit(success)
            } }
            catch(e: Exception) { if(e is CancellationException) throw e; message.emit(e.message ?: "저장하지 못했습니다. 다시 시도해 주세요.") }
            finally { busy.value=false }
        }
    }
    fun edit(id: String?=null,kind: String="MACHINE",date: String=today(),source:Snapshot=state.value) {
        draftJob?.cancel()
        val s=source
        editor.value=(s.drafts.find{it.id==id}?.treatment ?: s.treatments.find{it.id==id} ?: newTreatment(s,kind,date)).withUsageTemplate(s)
        savedState["editor"]=codec.encodeToString(editor.value!!)
    }
    fun change(t: Treatment) {
        editor.value=t
        savedState["editor"]=codec.encodeToString(t)
        draftJob?.cancel()
        pendingDraft=t
        draftJob=viewModelScope.launch { delay(350); serial.withLock { repository.draft(t);if(pendingDraft==t)pendingDraft=null } }
    }
    suspend fun flushDraft() {
        draftJob?.cancelAndJoin()
        serial.withLock { pendingDraft?.let { repository.draft(it);pendingDraft=null } }
    }
    fun changeDate(date: String) {
        val t=editor.value ?: return
        if(t.saved) { change(t.copy(date=date)); return }
        change(t.copy(date=date,basisMl=if(t.kind=="MACHINE")state.value.preferences.basisOn(date)else null))
    }
    fun discard(id: String,done: ()->Unit) { draftJob?.cancel();pendingDraft=null; act { repository.discardDraft(id);editor.value=null;savedState.remove<String>("editor");done() } }
    fun save(confirm: Boolean,onSaved: () -> Unit) {
        if(busy.value)return
        val t=editor.value ?: return
        draftJob?.cancel()
        pendingDraft=null
        act("기록을 저장했어요") { repository.save(t,confirm); editor.value=null;savedState.remove<String>("editor"); onSaved() }
    }
    fun preferences(success:String="설정을 저장했어요",onSaved:()->Unit={},change:(Preferences)->Preferences) = act(success) {
        val next=change(repository.snapshot().preferences)
        repository.preferences(next); Reminders.schedule(app,next); onSaved()
    }
}


class RecordFilters(saved:Bundle?=null) {
    val mode=mutableStateOf(saved?.getString("mode") ?: "리스트")
    val range=mutableStateOf(saved?.getString("range") ?: "전체")
    val month=mutableStateOf(saved?.getString("month") ?: java.time.YearMonth.now().toString())
    val selected=mutableStateOf(saved?.getString("selected") ?: today())
    val type=mutableStateOf(saved?.getString("type") ?: "전체")
    val status=mutableStateOf(saved?.getString("status") ?: "전체 상태")
    val from=mutableStateOf(saved?.getString("from") ?: java.time.LocalDate.now().minusDays(29).toString())
    val to=mutableStateOf(saved?.getString("to") ?: today())
    val period=mutableStateOf(saved?.getBoolean("period") ?: false)
    val tableOpened=mutableStateOf(saved?.getBoolean("tableOpened") ?: false)
    fun save()=Bundle().apply {
        listOf("mode" to mode,"range" to range,"month" to month,"selected" to selected,"type" to type,
            "status" to status,"from" to from,"to" to to).forEach{(key,state)->putString(key,state.value)}
        putBoolean("period",period.value)
        putBoolean("tableOpened",tableOpened.value)
    }
}
enum class StatsChartMode(val label:String) { LINE("라인차트"), METRIC("항목별 도표") }
class StatsFilters(saved:Bundle?=null) {
    val range=mutableStateOf(saved?.getString("range") ?: "7D")
    val from=mutableStateOf(saved?.getString("from") ?: java.time.LocalDate.now().minusDays(6).toString())
    val to=mutableStateOf(saved?.getString("to") ?: today())
    val chartMode=mutableStateOf(StatsChartMode.entries.find{it.name==saved?.getString("chartMode")} ?: StatsChartMode.LINE)
    fun save()=Bundle().apply {
        putString("range",range.value);putString("from",from.value);putString("to",to.value);putString("chartMode",chartMode.value.name)
    }
}
