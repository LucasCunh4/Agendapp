@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package br.com.agendapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.agendapp.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val brazil=Locale.forLanguageTag("pt-BR")
fun prettyDate(date: String)=LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",brazil))
@Composable fun Field(value: String,label: String,onChange: (String)->Unit,modifier: Modifier=Modifier,single: Boolean=true) {
    OutlinedTextField(value=value,onValueChange=onChange,label={ Text(label) },singleLine=single,modifier=modifier.fillMaxWidth())
}
@Composable fun DateDialog(initial: LocalDate,onDismiss: ()->Unit,onSelect: (LocalDate)->Unit) {
    val state=rememberDatePickerState(initialSelectedDateMillis=initial.toEpochDay()*86400000,yearRange=1..9999)
    DatePickerDialog(onDismissRequest=onDismiss,confirmButton={ TextButton(onClick={ state.selectedDateMillis?.let { onSelect(LocalDate.ofEpochDay(it/86400000)) } }) { Text("Escolher") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancelar") } }) { DatePicker(state) }
}
@Composable fun TimeDialog(initial: String,onDismiss: ()->Unit,onSelect: (String)->Unit) {
    val time=runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(7,0))
    val state=rememberTimePickerState(time.hour,time.minute,true)
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Escolha o horário") },text={ TimeInput(state) },confirmButton={ TextButton(onClick={ onSelect("%02d:%02d".format(state.hour,state.minute)) }) { Text("Confirmar") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancelar") } })
}
@Composable fun ScopeDialog(title: String,onDismiss: ()->Unit,onConfirm: (Scope)->Unit) {
    var scope by remember { mutableStateOf(Scope.FUTURE) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text(title) },text={ Column {
        listOf(Scope.ONLY to "Somente esta ocorrência",Scope.FUTURE to "Esta e as próximas",Scope.ALL to "Toda a série").forEach { (s,label) ->
            Row { RadioButton(scope==s,onClick={scope=s}); TextButton(onClick={scope=s}) { Text(label) } }
        }
        Text("Históricos anteriores e checklists já registrados são preservados nas edições.",style=MaterialTheme.typography.bodySmall)
    } },confirmButton={ TextButton(onClick={onConfirm(scope)}) { Text("Confirmar") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancelar") } })
}
@Composable fun Confirm(title: String,text: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=onConfirm){Text("Confirmar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
