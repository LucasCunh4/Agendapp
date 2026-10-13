package br.com.agendapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.agendapp.domain.*
import java.time.*

fun frequencyName(f: Frequency)=when(f){Frequency.DAILY->"Diária";Frequency.WEEKLY->"Semanal";Frequency.MONTHLY->"Mensal";Frequency.YEARLY->"Anual"}
@Composable fun RuleDialog(initial: Rule?,onDismiss: ()->Unit,onSave: (Rule?)->Unit) {
    var rule by remember { mutableStateOf(initial ?: Rule()) }
    var interval by remember { mutableStateOf(rule.interval.toString()) }
    var count by remember { mutableStateOf(rule.count?.toString() ?: "") }
    var until by remember { mutableStateOf(rule.until ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Recorrência personalizada")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Frequency.entries.forEach { f -> Row { RadioButton(rule.frequency==f,onClick={rule=rule.copy(frequency=f,ordinal=null,weekday=null)});TextButton(onClick={rule=rule.copy(frequency=f,ordinal=null,weekday=null)}){Text(frequencyName(f))} } }
        Field(interval,"A cada (número de dias, semanas, meses ou anos)",{interval=it})
        if(rule.frequency==Frequency.WEEKLY) {
            Text("Dias da semana (vazio: dia original)")
            listOf("Segunda","Terça","Quarta","Quinta","Sexta","Sábado","Domingo").forEachIndexed { i,name -> Row { Checkbox(i+1 in rule.weekdays,onCheckedChange={checked->rule=rule.copy(weekdays=if(checked)rule.weekdays+(i+1) else rule.weekdays-(i+1))});TextButton(onClick={rule=rule.copy(weekdays=if(i+1 in rule.weekdays)rule.weekdays-(i+1) else rule.weekdays+(i+1))}){Text(name)} } }
        }
        if(rule.frequency==Frequency.MONTHLY) {
            Row { Checkbox(rule.ordinal!=null,onCheckedChange={rule=rule.copy(ordinal=if(it)2 else null,weekday=if(it)2 else null)});Text("Por posição do dia da semana") }
            if(rule.ordinal!=null) {
                Row { listOf(1,2,3,4,5,-1).forEach { n -> TextButton(onClick={rule=rule.copy(ordinal=n)}){Text(if(n==-1)"Últ." else "${n}ª",color=if(rule.ordinal==n)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)} } }
                listOf("Segunda","Terça","Quarta","Quinta","Sexta","Sábado","Domingo").forEachIndexed { i,name -> Row { RadioButton(rule.weekday==i+1,onClick={rule=rule.copy(weekday=i+1)});TextButton(onClick={rule=rule.copy(weekday=i+1)}){Text(name)} } }
            } else Text("Dias 29–31 usam o último dia dos meses mais curtos.",style=MaterialTheme.typography.bodySmall)
        }
        Field(count,"Encerrar após ocorrências (opcional)",{count=it})
        OutlinedButton(onClick={datePicker=true}){Text(if(until.isEmpty())"Data final (opcional)" else "Até ${prettyDate(until)}")}
        if(until.isNotEmpty())TextButton(onClick={until=""}){Text("Remover data final")}
        Text("Sem data ou quantidade, a série continua indefinidamente. Lembretes absolutos são usados apenas na primeira ocorrência.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={runCatching{rule.copy(interval=interval.toInt(),count=count.takeIf{it.isNotBlank()}?.toInt(),until=until.takeIf{it.isNotBlank()}).also(Engine::validateRule)}.onSuccess(onSave).onFailure{error="Confira o intervalo e a quantidade."}}){Text("Aplicar")}},dismissButton={Column{TextButton(onClick={onSave(null)}){Text("Sem recorrência")};TextButton(onClick=onDismiss){Text("Cancelar")}}})
    if(datePicker)DateDialog(until.takeIf{it.isNotEmpty()}?.let(LocalDate::parse) ?: LocalDate.now(),{datePicker=false}){until=it.toString();datePicker=false}
}

@Composable fun ReminderDialog(initial: List<Reminder>,onDismiss: ()->Unit,onSave: (List<Reminder>)->Unit) {
    var reminders by remember { mutableStateOf(initial) }
    var amount by remember { mutableStateOf("15") }
    var unit by remember { mutableIntStateOf(1) }
    var absolute by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf("09:00") }
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Lembretes")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        reminders.forEach { r -> Row {
            Text(if(r.absolute!=null)LocalDateTime.parse(r.absolute).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) else if(r.beforeMinutes==0L)"No horário" else "${r.beforeMinutes} min antes",Modifier.weight(1f))
            TextButton(onClick={reminders=reminders-r}){Text("Remover")}
        } }
        Row { listOf(15L,30L,60L,0L).forEach { n -> TextButton(onClick={reminders=reminders+Reminder(beforeMinutes=n)}){Text(if(n==0L)"Na hora" else "${n}m")} } }
        Row { Switch(absolute,onCheckedChange={absolute=it});Text(if(absolute)"Data e horário absolutos" else "Intervalo personalizado") }
        if(absolute) {
            OutlinedButton(onClick={datePicker=true}){Text(prettyDate(date.toString()))}
            OutlinedButton(onClick={timePicker=true}){Text(time)}
        } else {
            Field(amount,"Quanto tempo antes? (0 = no horário)",{amount=it})
            Row { listOf(1 to "Min",60 to "Horas",1440 to "Dias").forEach { (value,label) -> FilterChip(selected=unit==value,onClick={unit=value},label={Text(label)},modifier=Modifier.padding(end=4.dp)) } }
        }
        TextButton(onClick={runCatching{
            if(absolute)Reminder(beforeMinutes=null,absolute=date.atTime(LocalTime.parse(time)).toString())
            else { val minutes=Math.multiplyExact(amount.toLong(),unit.toLong());require(minutes>=0);Reminder(beforeMinutes=minutes) }
        }.onSuccess{reminders=reminders+it;error=null}.onFailure{error="Use um intervalo válido e não negativo."}}){Text("Adicionar lembrete")}
        Text("Instantes passados não são enviados. Sem permissão de alarme exato, o Android poderá atrasar a entrega.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={onSave(reminders)}){Text("Salvar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
    if(datePicker)DateDialog(date,{datePicker=false}){date=it;datePicker=false}
    if(timePicker)TimeDialog(time,{timePicker=false}){time=it;timePicker=false}
}
@Composable fun TaskDialog(initial: Task,onDismiss: ()->Unit,onSave: (Task)->Unit) {
    var task by remember { mutableStateOf(initial) }
    var clock by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Detalhes da etapa")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Field(task.name,"Nome",{task=task.copy(name=it)})
        Field(task.notes,"Observações",{task=task.copy(notes=it)},single=false)
        Text("A etapa utiliza a data do evento.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={clock=true}){Text(task.time ?: "Definir horário")}
        if(task.time!=null)TextButton(onClick={task=task.copy(time=null)}){Text("Remover horário")}
        OutlinedButton(onClick={reminders=true},enabled=task.time!=null){Text("Lembretes (${task.reminders.size})")}
    }},confirmButton={TextButton(onClick={onSave(task)}){Text("Salvar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
    if(clock)TimeDialog(task.time ?: "09:00",{clock=false}){task=task.copy(time=it);clock=false}
    if(reminders)ReminderDialog(task.reminders,{reminders=false}){task=task.copy(reminders=it);reminders=false}
}
