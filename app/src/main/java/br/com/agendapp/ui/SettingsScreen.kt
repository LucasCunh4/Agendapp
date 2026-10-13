package br.com.agendapp.ui

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.agendapp.domain.Settings

@Composable fun SettingsScreen(settings: Settings,vm: AgendaViewModel) {
    val context=LocalContext.current
    var time by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){refresh++}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){refresh++}
    val allowed=remember(refresh){vm.app.alarms.notificationsAllowed()}
    val exact=remember(refresh){vm.app.alarms.exactAllowed()}
    fun open(intent: Intent) { runCatching{context.startActivity(intent)}.onFailure{vm.message.value="Esta configuração não está disponível no aparelho."} }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Resumos diários",style=MaterialTheme.typography.titleLarge)
        Row { Text("Resumo de hoje",Modifier.weight(1f));Switch(settings.morning,onCheckedChange={vm.settings(settings.copy(morning=it))}) }
        OutlinedButton(onClick={time="morning"}){Text("Horário da manhã: ${settings.morningTime}")}
        Row { Text("Resumo de amanhã",Modifier.weight(1f));Switch(settings.evening,onCheckedChange={vm.settings(settings.copy(evening=it))}) }
        OutlinedButton(onClick={time="evening"}){Text("Horário da noite: ${settings.eveningTime}")}
        Text("Os resumos contam eventos principais, inclusive concluídos. Etapas não entram na contagem. Mesmo sem compromissos, o resumo será enviado.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Row { Text("Modo escuro",Modifier.weight(1f));Switch(settings.dark,onCheckedChange={vm.settings(settings.copy(dark=it))}) }
        Text("Permissões",style=MaterialTheme.typography.titleLarge)
        Text("Notificações: ${if(allowed)"permitidas" else "desativadas"}")
        Button(onClick={if(Build.VERSION.SDK_INT>=33 && !allowed)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Permitir notificações")}
        TextButton(onClick={open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Abrir configurações de notificações")}
        Text("Alarmes exatos: ${if(exact)"permitidos" else "não permitidos — os lembretes poderão atrasar"}")
        OutlinedButton(onClick={if(Build.VERSION.SDK_INT>=31)open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,"package:${context.packageName}".toUri())) else vm.message.value="O Android desta versão já permite alarmes exatos."}){Text("Configurar alarmes exatos")}
        OutlinedButton(onClick={vm.app.alarms.test();if(!allowed)vm.message.value="Ative as notificações antes de enviar o teste." else vm.message.value="Notificação local de teste enviada."}){Text("Enviar notificação de teste")}
        Text("Todos os dados e lembretes funcionam sem internet. O Android pode atrasar alarmes inexatos e tarefas de manutenção durante economia de bateria. Após reiniciar, atualizar ou mudar o fuso, os alarmes são reconstruídos. Ao forçar parada nas configurações, abra o Agendapp novamente para reativá-los.",style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}){Text("Abrir informações do aplicativo")}
    }
    time?.let { key -> TimeDialog(if(key=="morning")settings.morningTime else settings.eveningTime,{time=null}){value->vm.settings(if(key=="morning")settings.copy(morningTime=value) else settings.copy(eveningTime=value));time=null} }
}
@Composable fun AboutScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Agendapp",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
        Text("Sua agenda, no seu ritmo.",style=MaterialTheme.typography.titleLarge)
        Text("Versão 1.0.0 • Android 8 ou superior")
        Text("Agenda pessoal inteiramente offline. Seus compromissos, etapas e preferências ficam neste aparelho. Não há login, anúncios, servidor ou sincronização.")
        Text("Exporte um backup JSON antes de desinstalar ou trocar de aparelho. Atualizações com a mesma assinatura preservam os dados. A lixeira mantém itens por 30 dias.")
        Text("Os lembretes respeitam as permissões, a economia de bateria e as limitações do Android. Não há garantia de entrega após Forçar parada.")
    }
}
