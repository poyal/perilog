package com.poyal.perilog.ui

import android.app.Application
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
    val inputErrors=mutableStateMapOf<String,String>()
    val recordFilters=RecordFilters()
    val statsFilters=StatsFilters()
    private val serial=Mutex()
    private var draftJob: Job?=null
    init {
        viewModelScope.launch { repository.snapshots.first(); ready.value=true }
        viewModelScope.launch { while(isActive) { calendarDay.value=today();delay(30000) } }
    }
    fun act(success: String?=null, block: suspend () -> Unit) {
        viewModelScope.launch { serial.withLock {
            if(inputErrors.isNotEmpty()) { message.emit("입력 형식을 확인해 주세요: ${inputErrors.values.joinToString()}");return@withLock }
            busy.value=true
            try { block(); if(success!=null) message.emit(success) }
            catch(e: Exception) { if(e is CancellationException) throw e; message.emit(e.message ?: "저장하지 못했습니다. 다시 시도해 주세요.") }
            finally { busy.value=false }
        } }
    }
    fun edit(id: String?=null,kind: String="MACHINE",date: String=today()) {
        draftJob?.cancel()
        val s=state.value
        editor.value=(s.drafts.find{it.id==id}?.treatment ?: s.treatments.find{it.id==id} ?: newTreatment(s,kind,date)).withUsageTemplate(s)
        savedState["editor"]=codec.encodeToString(editor.value!!)
    }
    fun change(t: Treatment) {
        editor.value=t
        savedState["editor"]=codec.encodeToString(t)
        draftJob?.cancel()
        draftJob=viewModelScope.launch { delay(350); serial.withLock { repository.draft(t) } }
    }
    fun changeDate(date: String) {
        val t=editor.value ?: return
        if(t.saved) { change(t.copy(date=date)); return }
        val old=newTreatment(state.value,t.kind,t.date)
        val fresh=newTreatment(state.value,t.kind,date)
        val usingPrevious=t.items==old.items && t.usageTemplateId==old.usageTemplateId
        change(t.copy(date=date,basisMl=fresh.basisMl,
            weightGrams=if(t.weightGrams==old.weightGrams)fresh.weightGrams else t.weightGrams,
            systolic=if(t.systolic==old.systolic)fresh.systolic else t.systolic,
            diastolic=if(t.diastolic==old.diastolic)fresh.diastolic else t.diastolic,
            items=if(usingPrevious)fresh.items else t.items,
            usageTemplateId=if(usingPrevious)fresh.usageTemplateId else t.usageTemplateId,
            usageTemplateName=if(usingPrevious)fresh.usageTemplateName else t.usageTemplateName,
            usageTemplateColor=if(usingPrevious)fresh.usageTemplateColor else t.usageTemplateColor,sourceDate=fresh.sourceDate))
    }
    fun discard(id: String,done: ()->Unit) { draftJob?.cancel(); act { repository.discardDraft(id);editor.value=null;savedState.remove<String>("editor");done() } }
    fun save(confirm: Boolean,onSaved: () -> Unit) {
        val t=editor.value ?: return
        draftJob?.cancel()
        act("기록을 저장했어요") { repository.save(t,confirm); editor.value=null;savedState.remove<String>("editor"); onSaved() }
    }
    fun markCelebrated(date:String) = act { repository.markCelebrated(date) }
    fun preferences(p: Preferences) = act { repository.preferences(p); Reminders.schedule(app,p) }
}


class RecordFilters {
    val mode=mutableStateOf("리스트")
    val range=mutableStateOf("전체")
    val month=mutableStateOf(java.time.YearMonth.now().toString())
    val selected=mutableStateOf(today())
    val type=mutableStateOf("전체")
    val status=mutableStateOf("전체 상태")
    val from=mutableStateOf(java.time.LocalDate.now().minusDays(29).toString())
    val to=mutableStateOf(today())
    val period=mutableStateOf(false)
}
class StatsFilters {
    val range=mutableStateOf("7D")
    val from=mutableStateOf(java.time.LocalDate.now().minusDays(6).toString())
    val to=mutableStateOf(today())
    val table=mutableStateOf(false)
}
