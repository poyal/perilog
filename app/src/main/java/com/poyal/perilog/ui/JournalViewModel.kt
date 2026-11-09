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
    private var editorOriginal=savedState.get<String>("editorOriginal")?.let{codec.decodeFromString<Treatment>(it)} ?: editor.value
    val editorChanged get()=editor.value!=null && editor.value!=editorOriginal
    var editorHiddenErrors: Boolean
        get()=savedState["editorHiddenErrors"] ?: false
        set(value){savedState["editorHiddenErrors"]=value}
    val calendarDay=MutableStateFlow(today())
    val localNow=MutableStateFlow(java.time.LocalDateTime.now())
    val inputErrors=mutableStateMapOf<String,String>()
    val recordFilters=RecordFilters(savedState["recordFilters"])
    val statsFilters=StatsFilters(savedState["statsFilters"])
    private val serial=Mutex()
    init {
        savedState.setSavedStateProvider("recordFilters"){recordFilters.save()}
        savedState.setSavedStateProvider("statsFilters"){statsFilters.save()}
        viewModelScope.launch { repository.snapshots.first(); ready.value=true }
        viewModelScope.launch { while(isActive) { refreshClock();delay(30000) } }
    }
    fun refreshClock() { localNow.value=java.time.LocalDateTime.now();calendarDay.value=localNow.value.toLocalDate().toString() }
    fun act(success: String?=null, checkInputs:Boolean=true,onSuccess:()->Unit={}, block: suspend () -> Unit) {
        // Claim the operation synchronously so double taps cannot queue duplicate writes.
        if(busy.value)return
        busy.value=true
        viewModelScope.launch {
            var completed=false
            try { serial.withLock {
                if(checkInputs && inputErrors.isNotEmpty()) { message.emit("입력 형식을 확인해 주세요: ${inputErrors.values.joinToString()}");return@withLock }
                block();completed=true; if(success!=null) message.emit(success)
            } }
            catch(e: Exception) { if(e is CancellationException) throw e; message.emit(e.message ?: "저장하지 못했습니다. 다시 시도해 주세요.") }
            finally { busy.value=false }
            if(completed)onSuccess()
        }
    }
    fun edit(id: String?=null,kind: String="MACHINE",date: String=today(),source:Snapshot=state.value) {
        val s=source
        beginEditing((s.visibleRecords().find{it.id==id} ?: newTreatment(s,kind,date)).withUsageTemplate(s))
    }
    private fun beginEditing(t:Treatment) {
        editor.value=t
        editorOriginal=t
        editorHiddenErrors=false
        inputErrors.clear()
        savedState["editor"]=codec.encodeToString(editor.value!!)
        savedState["editorOriginal"]=codec.encodeToString(editorOriginal!!)
    }
    fun change(t: Treatment) {
        if(busy.value)return
        editor.value=t
        savedState["editor"]=codec.encodeToString(t)
    }
    // Only transient UI state is cleared. Existing records, usage and legacy drafts stay intact.
    fun endEditing() {
        editor.value=null
        editorOriginal=null
        editorHiddenErrors=false
        inputErrors.clear()
        savedState.remove<String>("editor")
        savedState.remove<String>("editorOriginal")
    }
    fun recordsForDate(date:String,onLoaded:(List<Treatment>)->Unit) {
        val current=editor.value ?: return
        if(date==current.date)return
        var records=emptyList<Treatment>()
        act(checkInputs=false,onSuccess={onLoaded(records)}) {
            require(date<=today()) { "미래 날짜에는 치료 기록을 등록할 수 없어요." }
            records=repository.snapshot().treatmentsOnDate(date,current.kind)
        }
    }
    fun openDate(date:String,id:String?,saveChanges:Boolean,onOpened:()->Unit) {
        val current=editor.value ?: return
        if(date==current.date)return
        act(if(saveChanges)"기록을 저장했어요"else null,checkInputs=saveChanges,onSuccess=onOpened) {
            require(date<=today()) { "미래 날짜에는 치료 기록을 등록할 수 없어요." }
            // Resolve before saving or replacing the editor, so failed reads retain all input.
            val source=repository.snapshot()
            val records=source.treatmentsOnDate(date,current.kind)
            val target=if(id==null) {
                require(records.isEmpty()) { "기록이 바뀌었어요. 날짜를 다시 선택해 주세요." }
                newTreatment(source,current.kind,date).let {
                    // Saving a manual record also updates the default unit for the next record.
                    if(saveChanges && current.kind=="MANUAL")it.copy(drainUnit=current.drainUnit)else it
                }
            } else requireNotNull(records.find{it.id==id}) { "기록이 바뀌었어요. 날짜를 다시 선택해 주세요." }
            val next=target.withUsageTemplate(source)
            if(saveChanges)repository.save(current,current.items.isNotEmpty() || current.usageConfirmed)
            beginEditing(next)
        }
    }
    fun discard(id: String,done: ()->Unit) = act(checkInputs=false) { repository.discardDraft(id);endEditing();done() }
    fun save(confirm: Boolean,onSaved: () -> Unit) {
        if(busy.value)return
        val t=editor.value ?: return
        act("기록을 저장했어요") { repository.save(t,confirm); endEditing(); onSaved() }
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
