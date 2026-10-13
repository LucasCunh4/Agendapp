package br.com.agendapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import br.com.agendapp.domain.*
import br.com.agendapp.domain.State
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun BackupScreen(backup: Backup,vm: AgendaViewModel) {
    var exported by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var incoming by remember { mutableStateOf<Backup?>(null) }
    var mode by remember { mutableStateOf<String?>(null) }
    var conflicts by remember { mutableIntStateOf(0) }
    var prefer by remember { mutableStateOf(false) }
    val clipboard=LocalClipboardManager.current
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("Backup por texto",style=MaterialTheme.typography.titleLarge);Text("Guarde o JSON em um lugar seguro. Ele contém seus dados pessoais e permite restaurar a agenda sem arquivos ou internet.")}
        item{Button(onClick={exported=BackupCodec.encode(backup)}){Text("Gerar JSON completo")}}
        if(exported.isNotEmpty()) {
            item{OutlinedButton(onClick={clipboard.setText(AnnotatedString(exported));vm.message.value="Backup copiado."}){Text("Copiar JSON")}}
            item{SelectionContainer{Text(exported,style=MaterialTheme.typography.bodySmall)}}
        }
        item{HorizontalDivider();Text("Restaurar",style=MaterialTheme.typography.titleLarge)}
        item{Field(input,"Cole o JSON do backup",{input=it},single=false)}
        item{TextButton(onClick={input=clipboard.getText()?.text ?: ""}){Text("Colar da área de transferência")}}
        item{Row {
            Button(onClick={runCatching{BackupCodec.decode(input)}.onSuccess{incoming=it;mode="replace"}.onFailure{vm.message.value="Backup inválido: ${it.message}"}}){Text("Substituir")}
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick={runCatching{BackupCodec.decode(input)}.onSuccess{incoming=it;conflicts=BackupCodec.conflicts(backup,it);prefer=false;mode="merge"}.onFailure{vm.message.value="Backup inválido: ${it.message}"}}){Text("Mesclar")}
        }}
    }
    if(mode=="replace")Confirm("Substituir todos os dados?","A agenda atual, configurações e lixeira serão substituídas pelo backup. Exporte os dados atuais antes de continuar.",{mode=null}){val parsed=incoming!!;vm.change({parsed}){vm.message.value="Backup restaurado."};mode=null}
    if(mode=="merge")AlertDialog(onDismissRequest={mode=null},title={Text("Mesclar backup")},text={Column {
        Text("$conflicts conflito(s) de identificadores ou configurações. Itens sem conflito serão combinados sem duplicação. Escolha explicitamente qual versão manter nos conflitos; a outra versão permanece no texto do backup.")
        Row{RadioButton(!prefer,onClick={prefer=false});TextButton(onClick={prefer=false}){Text("Manter dados atuais")}}
        Row{RadioButton(prefer,onClick={prefer=true});TextButton(onClick={prefer=true}){Text("Usar dados importados")}}
        Text("Relações inválidas ou sobreposição de séries interrompem toda a importação.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={val parsed=incoming!!;val choice=prefer;vm.change({BackupCodec.merge(it,parsed,choice)}){vm.message.value="Backup mesclado."};mode=null}){Text("Confirmar mesclagem")}},dismissButton={TextButton(onClick={mode=null}){Text("Cancelar")}})
}
@Composable fun TrashScreen(state: State,vm: AgendaViewModel) {
    var deleting by remember { mutableStateOf<Trash?>(null) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("Itens ficam aqui por 30 dias. A restauração preserva os dados e estados anteriores.")}
        if(state.trash.isEmpty())item{Text("A lixeira está vazia.")}
        items(state.trash.sortedByDescending{it.deletedAt},key={it.id}) { item ->
            OutlinedCard(modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)) {
                Text(item.title,style=MaterialTheme.typography.titleMedium)
                Text("Excluído em ${Instant.ofEpochMilli(item.deletedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}",style=MaterialTheme.typography.bodySmall)
                Text(if(item.series!=null)"Série recorrente" else if(item.tasks.isNotEmpty())"Etapa e descendentes" else "Evento",style=MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick={vm.restore(item)}){Text("Restaurar")};TextButton(onClick={deleting=item}){Text("Excluir definitivamente")} }
            }}
        }
    }
    deleting?.let { item -> Confirm("Excluir definitivamente?","Não será possível recuperar este item.",{deleting=null}){vm.change({it.copy(state=it.state.copy(trash=it.state.trash.filterNot { t->t.id==item.id }))});deleting=null} }
}
