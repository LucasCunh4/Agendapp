@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package br.com.agendapp.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import br.com.agendapp.domain.*
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*

@Composable fun EditorScreen(initial: Event,initialRule: Rule?,vm: AgendaViewModel,onBack: ()->Unit) {
    val backup by vm.data.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var event by remember(initial.id) { mutableStateOf(initial) }
    var rule by remember(initial.id) { mutableStateOf(initialRule) }
    var dateDialog by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf<String?>(null) }
    var ruleDialog by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Task?>(null) }
    var scopeDialog by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var quick by remember { mutableStateOf("") }
    var defaultUsed by remember { mutableStateOf(initial.start!=null) }
    var deleted by remember { mutableStateOf(listOf<Trash>()) }
    val visible=Checklist.ordered(event.tasks,expanded).map { it.first.id }.toSet()
    val bounds=remember { mutableMapOf<String,Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    fun replace(task: Task) { event=event.copy(tasks=event.tasks.map{if(it.id==task.id)task else it}) }
    fun save(scope: Scope) {
        runCatching { Engine.validateEvent(event); rule?.let(Engine::validateRule); require(rule?.until==null || rule!!.until!!>=event.date){"O fim da recorrência deve ser igual ou posterior à data do evento."} }
            .onFailure { vm.message.value=it.message }
            .onSuccess { vm.save(event,rule,scope,deleted) }
    }
    BackHandler { if(event!=initial || rule!=initialRule)discard=true else onBack() }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title={Text(if(initial.seriesId!=null || backup.state.events.any { it.id==initial.id })"Editar evento" else "Novo evento")},navigationIcon={IconButton(onClick={if(event!=initial || rule!=initialRule)discard=true else onBack()}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Voltar")}},actions={TextButton(enabled=!saving,onClick={if(event.seriesId!=null)scopeDialog=true else save(Scope.ONLY)}){Text("Salvar")}})
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Field(event.name,"Nome (opcional)",{event=event.copy(name=it)})
            OutlinedButton(onClick={dateDialog=true},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CalendarMonth,null);Text(prettyDate(event.date),Modifier.padding(start=8.dp)) }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={clock="start"},modifier=Modifier.weight(1f)){Text(event.start?.let{"Início: $it"} ?: "Horário inicial")}
                if(event.start!=null)IconButton(onClick={event=event.copy(start=null)}){Icon(Icons.Outlined.Close,"Remover início")}
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={clock="end"},modifier=Modifier.weight(1f)){Text(event.end?.let{"Término: $it"} ?: "Horário final")}
                if(event.end!=null)IconButton(onClick={event=event.copy(end=null)}){Icon(Icons.Outlined.Close,"Remover término")}
            }
            Field(event.place,"Local (opcional)",{event=event.copy(place=it)})
            Field(event.notes,"Observações (opcional)",{event=event.copy(notes=it)},single=false)
            OutlinedButton(onClick={reminderDialog=true},modifier=Modifier.fillMaxWidth(),enabled=event.start!=null || event.end!=null){Icon(Icons.Outlined.NotificationsNone,null);Text("Lembretes (${event.reminders.size})",Modifier.padding(start=8.dp))}
            if(event.start==null && event.end==null)Text("Sem horário, o evento participa dos resumos diários e não envia lembretes individuais.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={ruleDialog=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Repeat,null);Text(if(rule==null)"Sem recorrência" else "Recorrência: ${frequencyName(rule!!.frequency)}",Modifier.padding(start=8.dp))}
            HorizontalDivider()
            Text("Etapas",style=MaterialTheme.typography.titleLarge)
            Text("Segure o ícone de arraste. Solte sobre uma etapa: para a direita cria uma subetapa; para a esquerda sobe um nível; ao centro reordena.",style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Field(quick,"Nome da nova etapa",{quick=it},Modifier.weight(1f))
                IconButton(onClick={event=event.copy(tasks=event.tasks+Task(name=quick,position=event.tasks.count{it.parentId==null}));quick=""}){Icon(Icons.Outlined.Add,"Adicionar etapa")}
            }
            Checklist.ordered(event.tasks,expanded).forEach { (task,depth) ->
                key(task.id) {
                    Surface(color=if(task.id==target)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().padding(start=(minOf(depth,4)*12).dp).onGloballyPositioned{bounds[task.id]=it.boundsInRoot()}) {
                        Column(Modifier.padding(6.dp)) {
                            if(depth>4)Text("Nível ${depth+1}",style=MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Outlined.DragIndicator,"Segurar para arrastar",Modifier.padding(8.dp).pointerInput(task.id,event.tasks,visible) {
                                    detectDragGesturesAfterLongPress(onDragStart={dragId=task.id;dx=0f;dy=0f},onDragCancel={dragId=null;target=null},onDragEnd={
                                        val destination=event.tasks.find{it.id==target}
                                        if(destination!=null && destination.id!=task.id) {
                                            val parent=when { dx>44 -> destination.id; dx< -44 -> event.tasks.find{it.id==destination.parentId}?.parentId; else -> destination.parentId }
                                            runCatching{Checklist.move(event.tasks,task.id,parent,if(dx>44)null else destination.id)}
                                                .onSuccess{event=event.copy(tasks=it);if(parent!=null)expanded=expanded+parent}
                                                .onFailure{vm.message.value=it.message}
                                        }
                                        dragId=null;target=null
                                    },onDrag={change,amount->
                                        change.consume();dx+=amount.x;dy+=amount.y
                                        val y=(bounds[task.id]?.center?.y ?: 0f)+dy
                                        target=bounds.filterKeys { id -> id in visible && id!=task.id }.minByOrNull{ (_,r)->kotlin.math.abs(r.center.y-y) }?.key
                                    })
                                })
                                Field(task.name,"Etapa",{replace(task.copy(name=it))},Modifier.weight(1f))
                                IconButton(onClick={detail=task}){Icon(Icons.Outlined.Tune,"Detalhes da etapa")}
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                if(event.tasks.any{it.parentId==task.id})TextButton(onClick={expanded=if(task.id in expanded)expanded-task.id else expanded+task.id}){Text(if(task.id in expanded)"Recolher" else "Subetapas")}
                                TextButton(onClick={event=event.copy(tasks=event.tasks+Task(parentId=task.id,position=event.tasks.count{it.parentId==task.id}));expanded=expanded+task.id}){Text("+ Subetapa")}
                                IconButton(onClick={
                                    val ids=Checklist.descendants(event.tasks,task.id)
                                    val removed=event.tasks.filter{it.id in ids}
                                    if(initial.tasks.any{it.id==task.id})deleted=deleted+Trash(eventId=event.id,tasks=removed)
                                    event=event.copy(tasks=event.tasks.filterNot{it.id in ids})
                                }){Icon(Icons.Outlined.DeleteOutline,"Excluir etapa")}
                            }
                        }
                    }
                }
            }
            if(dragId!=null)Text("Destino: ${event.tasks.find{it.id==target}?.name ?: "escolha uma etapa"} • ${if(dx>44)"como subetapa" else if(dx< -44)"subir um nível" else "antes desta etapa"}",color=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
        }
    }
    if(dateDialog)DateDialog(LocalDate.parse(event.date),{dateDialog=false}){event=event.copy(date=it.toString());dateDialog=false}
    clock?.let { field -> TimeDialog((if(field=="start")event.start else event.end) ?: "09:00",{clock=null}) { time ->
        if(field=="start") {
            event=event.copy(start=time,reminders=if(!defaultUsed)event.reminders+Reminder() else event.reminders);defaultUsed=true
        } else event=event.copy(end=time)
        clock=null
    } }
    if(reminderDialog)ReminderDialog(event.reminders,{reminderDialog=false}){event=event.copy(reminders=it);reminderDialog=false}
    if(ruleDialog)RuleDialog(rule,{ruleDialog=false}){rule=it;ruleDialog=false}
    detail?.let { task -> TaskDialog(task,{detail=null}){replace(it);detail=null} }
    if(scopeDialog)ScopeDialog("Aplicar alterações",{scopeDialog=false}){scopeDialog=false;save(it)}
    if(discard)Confirm("Descartar alterações?","As alterações que ainda não foram salvas serão descartadas.",{discard=false}){onBack()}
}
