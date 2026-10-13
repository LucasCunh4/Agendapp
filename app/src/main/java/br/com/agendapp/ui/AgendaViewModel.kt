package br.com.agendapp.ui

import android.app.Application
import androidx.lifecycle.*
import br.com.agendapp.AgendappApplication
import br.com.agendapp.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class Destination(val date: String,val eventId: String?=null,val taskId: String?=null)
class AgendaViewModel(application: Application): AndroidViewModel(application) {
    val app=application as AgendappApplication
    val data=app.repository.backup.stateIn(viewModelScope,SharingStarted.Eagerly,Backup())
    val preferences=app.repository.settings.stateIn(viewModelScope,SharingStarted.Eagerly,Settings())
    val saving=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    val destination=MutableStateFlow<Destination?>(null)
    fun go(date: String,event: String?=null,task: String?=null) { destination.value=Destination(date,event,task) }
    fun change(transform: (Backup)->Backup,onFailure: ()->Unit = {},onSuccess: ()->Unit = {}) { viewModelScope.launch {
        try {
            app.repository.change(transform)
            runCatching { app.alarms.rebuild() }.onFailure { message.value="Dados salvos; não foi possível reprogramar alarmes. Abra o app novamente. ${it.message}" }
            onSuccess()
        }
        catch(e: Exception) { message.value=e.message ?: "Não foi possível salvar. Tente novamente."; onFailure() }
    } }
    fun save(event: Event,rule: Rule?,scope: Scope,trash: List<Trash> = emptyList()) {
        if(saving.value)return
        saving.value=true
        var expanded=event.id
        change({ old ->
            var state=Engine.save(old.state,event,rule,scope,LocalDate.now())
            if(event.seriesId==null && rule!=null) {
                val created=state.series.first { series -> old.state.series.none { it.id==series.id } }
                expanded=stable(created.id,event.date)
            }
            state=state.copy(trash=state.trash+trash)
            old.copy(state=state)
        },onFailure={saving.value=false}) { saving.value=false; go(event.date,expanded) }
    }
    private fun latest(backup: Backup,event: Event)=Recurrence.find(backup.state,event.id,event.date) ?: error("Este evento não está mais na agenda.")
    fun toggle(event: Event,task: Task) = change({ backup ->
        val current=latest(backup,event)
        val currentTask=current.tasks.first { it.id==task.id }
        backup.copy(state=Engine.put(backup.state,Checklist.toggle(current,task.id,!currentTask.done)))
    })
    fun complete(event: Event,all: Boolean) = change({ it.copy(state=Engine.put(it.state,Checklist.complete(latest(it,event),all))) })
    fun reopen(event: Event) = change({ it.copy(state=Engine.put(it.state,Checklist.reopen(latest(it,event)))) })
    fun remove(event: Event,scope: Scope) = change({ it.copy(state=Engine.delete(it.state,latest(it,event),scope)) })
    fun removeTask(event: Event,id: String) = change({ it.copy(state=Engine.deleteTask(it.state,latest(it,event),id)) })
    fun restore(item: Trash) = change({ it.copy(state=Engine.restore(it.state,item)) })
    fun settings(settings: Settings) = change({ it.copy(settings=settings) })
}
