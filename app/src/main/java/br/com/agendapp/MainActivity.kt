package br.com.agendapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import br.com.agendapp.ui.*
import kotlinx.coroutines.launch

class MainActivity: ComponentActivity() {
    private lateinit var model: AgendaViewModel
    override fun attachBaseContext(base: android.content.Context) {
        val configuration=android.content.res.Configuration(base.resources.configuration)
        configuration.setLocales(android.os.LocaleList(java.util.Locale.forLanguageTag("pt-BR")))
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        model=ViewModelProvider(this)[AgendaViewModel::class.java]
        handle(intent)
        setContent { Agendapp(model) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    override fun onResume() { super.onResume(); if(::model.isInitialized) model.app.scope.let { scope ->
        scope.launchSafely { model.app.repository.initialize(); model.app.alarms.rebuild() }
    } }
    private fun handle(intent: Intent?) {
        val date=intent?.getStringExtra("date")
        if(date!=null) model.go(date,intent.getStringExtra("event"),intent.getStringExtra("task"))
        else if(intent?.action==Intent.ACTION_MAIN) model.go(java.time.LocalDate.now().toString())
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchSafely(block: suspend ()->Unit) = launch(block = { try { block() } catch(e: Exception) { android.util.Log.e("Agendapp","Falha na manutenção",e) } })
