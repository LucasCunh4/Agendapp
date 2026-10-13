package br.com.agendapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import br.com.agendapp.domain.*
import br.com.agendapp.domain.State
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun AgendaScreen(state: State,date: LocalDate,expanded: String?,focusedTask: String?,pendingOpen: Boolean,
    onDate: (LocalDate)->Unit,onCalendar: ()->Unit,onCreate: ()->Unit,onExpand: (String)->Unit,
    onPending: ()->Unit,onEdit: (Event)->Unit,vm: AgendaViewModel) {
    val events=remember(state,date) { Recurrence.onDay(state,date) }
    val pending=remember(state,LocalDate.now()) { Recurrence.pending(state,LocalDate.now()) }
    var drag by remember { mutableFloatStateOf(0f) }
    LazyColumn(modifier=Modifier.fillMaxSize().pointerInput(date) { detectHorizontalDragGestures(onDragStart={drag=0f},onDragEnd={if(drag>100)onDate(date.minusDays(1)) else if(drag< -100)onDate(date.plusDays(1))},onHorizontalDrag={_,amount->drag+=amount}) },contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically) {
            Text(date.dayOfMonth.toString(),style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start=12.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy",brazil)),style=MaterialTheme.typography.titleLarge)
                Text(date.format(DateTimeFormatter.ofPattern("EEEE",brazil)),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=onCalendar){Icon(Icons.Outlined.CalendarMonth,"Escolher data")}
            IconButton(onClick=onCreate){Icon(Icons.Outlined.Add,"Criar evento",tint=MaterialTheme.colorScheme.primary)}
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={onDate(date.minusDays(1))}){Icon(Icons.Outlined.ChevronLeft,null);Text("Anterior")}
            TextButton(onClick={onDate(LocalDate.now())}){Text("Hoje")}
            TextButton(onClick={onDate(date.plusDays(1))}){Text("Próximo");Icon(Icons.Outlined.ChevronRight,null)}
        } }
        if(pending.isNotEmpty()) {
            item { OutlinedCard(onClick=onPending,modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.History,null);Text("${pending.size} ${if(pending.size==1) "pendência anterior" else "pendências anteriores"}",Modifier.weight(1f).padding(start=8.dp));Icon(if(pendingOpen)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null) } } }
            if(pendingOpen) items(pending,key={"pending-${it.id}"}) { e -> EventCard(e,e.id==expanded,focusedTask,onExpand,onEdit,vm,true) }
        }
        if(events.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.EventAvailable,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(40.dp))
            Text("Um dia com espaço para você.",Modifier.padding(top=16.dp),style=MaterialTheme.typography.titleMedium)
            Text("Toque em + para adicionar um compromisso.",style=MaterialTheme.typography.bodySmall)
        } }
        val groups=listOf("Sem horário" to events.filter { !it.done && it.start==null && it.end==null },"Compromissos" to events.filter { !it.done && (it.start!=null || it.end!=null) },"Concluídos" to events.filter { it.done })
        groups.forEach { (title,list) -> if(list.isNotEmpty()) {
            item { Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=4.dp)) }
            items(list,key={it.id}) { e -> EventCard(e,e.id==expanded,focusedTask,onExpand,onEdit,vm,false) }
        } }
    }
}

@Composable fun EventCard(event: Event,expanded: Boolean,focusedTask: String?,onExpand: (String)->Unit,onEdit: (Event)->Unit,vm: AgendaViewModel,showDate: Boolean) {
    var deleting by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    var taskExpanded by remember(event.id) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(focusedTask,event.tasks) { if(focusedTask!=null) {
        var id=focusedTask; val ancestors=mutableSetOf<String>()
        while(id!=null) { ancestors+=id; id=event.tasks.find{it.id==id}?.parentId }
        taskExpanded=taskExpanded+ancestors
    } }
    Card(modifier=Modifier.fillMaxWidth().animateContentSize(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable{onExpand(event.id)},verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.title,style=MaterialTheme.typography.titleMedium)
                    if(showDate) Text(prettyDate(event.date),style=MaterialTheme.typography.bodySmall)
                    (event.start ?: event.end?.let{"Até $it"})?.let { Text(it,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium) }
                }
                if(event.done) Icon(Icons.Outlined.CheckCircle,"Concluído",tint=MaterialTheme.colorScheme.secondary)
                if(event.seriesId!=null) Icon(Icons.Outlined.Repeat,"Recorrente",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=4.dp).size(18.dp))
                Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded)"Recolher" else "Expandir")
            }
            if(event.tasks.isNotEmpty()) {
                val (done,total)=Checklist.progress(event.tasks)
                Row(Modifier.padding(top=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    LinearProgressIndicator(progress={if(total==0)0f else done.toFloat()/total},modifier=Modifier.weight(1f).height(3.dp))
                    Text("$done/$total",Modifier.padding(start=12.dp),style=MaterialTheme.typography.labelSmall)
                }
            }
            if(expanded) {
                if(event.start!=null && event.end!=null) Text("${event.start} – ${event.end}",Modifier.padding(top=12.dp),color=MaterialTheme.colorScheme.primary)
                if(event.place.isNotBlank()) Text("Local: ${event.place}",Modifier.padding(top=12.dp))
                if(event.notes.isNotBlank()) Text(event.notes,Modifier.padding(top=12.dp))
                if(event.reminders.isNotEmpty()) Text("${event.reminders.size} lembrete(s) configurado(s)",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall)
                Checklist.ordered(event.tasks,taskExpanded).forEach { (task,depth) ->
                    val hasChildren=event.tasks.any { it.parentId==task.id }
                    Row(Modifier.fillMaxWidth().padding(start=(minOf(depth,4)*12).dp,top=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(hasChildren) IconButton(onClick={taskExpanded=if(task.id in taskExpanded)taskExpanded-task.id else taskExpanded+task.id},modifier=Modifier.size(30.dp)) { Icon(if(task.id in taskExpanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Mostrar subetapas") }
                        Checkbox(task.done,onCheckedChange={vm.toggle(event,task)})
                        Column(Modifier.weight(1f)) {
                            if(depth>4) Text("Nível ${depth+1}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            Text(task.name.ifBlank{"Etapa sem nome"},color=if(task.id==focusedTask)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            task.time?.let { Text(it,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall) }
                            if(task.notes.isNotBlank()) Text(task.notes,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={ if(event.done) vm.reopen(event) else if(event.tasks.any{!it.done}) completing=true else vm.complete(event,false) }) { Icon(if(event.done)Icons.Outlined.Undo else Icons.Outlined.Done,null);Text(if(event.done)"Reabrir" else "Concluir") }
                    IconButton(onClick={onEdit(event)}){Icon(Icons.Outlined.Edit,"Editar evento")}
                    IconButton(onClick={deleting=true}){Icon(Icons.Outlined.DeleteOutline,"Excluir evento")}
                }
            }
        }
    }
    if(completing) AlertDialog(onDismissRequest={completing=false},title={Text("Concluir o evento?")},text={Text("Há etapas pendentes. Você pode concluí-las ou manter seus estados atuais.")},confirmButton={TextButton(onClick={vm.complete(event,true);completing=false}){Text("Concluir etapas")}},dismissButton={Column {TextButton(onClick={vm.complete(event,false);completing=false}){Text("Manter etapas")};TextButton(onClick={completing=false}){Text("Cancelar")}}})
    if(deleting) {
        if(event.seriesId!=null) ScopeDialog("Enviar à lixeira",{deleting=false}) { vm.remove(event,it);deleting=false }
        else Confirm("Excluir evento?","O evento poderá ser recuperado na lixeira por 30 dias.",{deleting=false}){vm.remove(event,Scope.ONLY);deleting=false}
    }
}
