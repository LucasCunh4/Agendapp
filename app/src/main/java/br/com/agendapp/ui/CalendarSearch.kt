package br.com.agendapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.agendapp.domain.*
import br.com.agendapp.domain.State
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun CalendarScreen(state: State,selected: LocalDate,onChoose: (LocalDate)->Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var jump by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Outlined.ChevronLeft,"Mês anterior")}
            TextButton(onClick={jump=true},modifier=Modifier.weight(1f)){Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",brazil)),style=MaterialTheme.typography.titleLarge)}
            IconButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Outlined.ChevronRight,"Próximo mês")}
        }
        Row(Modifier.fillMaxWidth()) { listOf("S","T","Q","Q","S","S","D").forEach { Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.primary) } }
        val first=month.atDay(1); val offset=first.dayOfWeek.value-1
        val dots=remember(state,month){(1..month.lengthOfMonth()).filter{Recurrence.onDay(state,month.atDay(it)).isNotEmpty()}.toSet()}
        val cells=(0 until offset).map{null}+(1..month.lengthOfMonth()).map{it}
        cells.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) {
            (week+List(7-week.size){null}).forEach { day ->
                Box(Modifier.weight(1f).height(64.dp),contentAlignment=Alignment.Center) {
                    if(day!=null) {
                        val date=month.atDay(day)
                        Surface(onClick={onChoose(date)},color=if(date==selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium) {
                            Column(Modifier.width(42.dp).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text(day.toString(),style=MaterialTheme.typography.titleMedium)
                                Text(if(day in dots)"•" else " ",color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } }
        Text("Os pontos dourados indicam dias com compromissos.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={month=YearMonth.now()}){Text("Voltar ao mês atual")}
    }
    if(jump)DateDialog(month.atDay(1),{jump=false}){month=YearMonth.from(it);jump=false}
}
@Composable fun SearchScreen(state: State,vm: AgendaViewModel) {
    var query by remember { mutableStateOf("") }
    var seriesPicker by remember { mutableStateOf<Series?>(null) }
    val normalized=Engine.fold(query.trim())
    val events=remember(state,normalized){if(normalized.isEmpty())emptyList() else state.events.filter { Engine.fold(it.title).contains(normalized) }.sortedBy{it.date}}
    val series=remember(state,normalized){if(normalized.isEmpty())emptyList() else state.series.filter { it.segments.any { s -> Engine.fold(s.template.title).contains(normalized) } }}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Field(query,"Pesquisar pelo nome",{query=it})}
        if(normalized.isNotEmpty() && events.isEmpty() && series.isEmpty())item{Text("Nenhum evento encontrado.")}
        items(events,key={it.id}) { e -> OutlinedCard(onClick={vm.go(e.date,e.id)},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)){Text(e.title,style=MaterialTheme.typography.titleMedium);Text(prettyDate(e.date));if(e.done)Text("Concluído",color=MaterialTheme.colorScheme.secondary)} } }
        if(series.isNotEmpty())item{Text("Séries recorrentes • escolha qualquer data",style=MaterialTheme.typography.titleMedium)}
        items(series,key={it.id}) { s -> OutlinedCard(onClick={seriesPicker=s},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(s.segments.last().template.title,style=MaterialTheme.typography.titleMedium);Text("Consultar ocorrência em uma data",style=MaterialTheme.typography.bodySmall)}} }
    }
    seriesPicker?.let { s -> DateDialog(LocalDate.now(),{seriesPicker=null}){date->
        val result=Recurrence.onDay(state,date).firstOrNull{it.seriesId==s.id && Engine.fold(it.title).contains(normalized)}
        if(result==null)vm.message.value="Esta série não possui ocorrência com esse nome na data selecionada."
        else vm.go(result.date,result.id)
        seriesPicker=null
    } }
}
