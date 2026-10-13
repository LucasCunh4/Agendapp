@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package br.com.agendapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import br.com.agendapp.domain.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable fun Agendapp(vm: AgendaViewModel) {
    val backup by vm.data.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val nav=rememberNavController()
    val route by nav.currentBackStackEntryAsState()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var focusedTask by remember { mutableStateOf<String?>(null) }
    var pendingOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Event?>(null) }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val coroutine=rememberCoroutineScope()
    val snack=remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.message.value=null } }
    LaunchedEffect(destination) { destination?.let { d ->
        date=LocalDate.parse(d.date); expanded=d.eventId; focusedTask=d.taskId
        nav.navigate("agenda") { popUpTo("agenda"){inclusive=true}; launchSingleTop=true }
        vm.destination.value=null
    } }
    fun edit(event: Event) {
        editing=event
        editingRule=backup.state.series.find { it.id==event.seriesId }?.segments?.lastOrNull { it.from<=(event.occurrenceDate ?: event.date) }?.rule
        nav.navigate("edit")
    }
    val activity=LocalActivity.current
    SideEffect { activity?.let { WindowInsetsControllerCompat(it.window,it.window.decorView).apply {
        isAppearanceLightStatusBars=!prefs.dark; isAppearanceLightNavigationBars=!prefs.dark
    } } }
    AgendaTheme(prefs.dark) {
        ModalNavigationDrawer(drawerState=drawer,drawerContent={ ModalDrawerSheet {
            Spacer(Modifier.height(28.dp)); Text("Agendapp",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(24.dp),color=MaterialTheme.colorScheme.primary)
            listOf("agenda" to "Agenda","calendar" to "Calendário","pending" to "Pendências","search" to "Pesquisa","settings" to "Configurações","backup" to "Backup","trash" to "Lixeira","about" to "Sobre o aplicativo").forEach { (key,label) ->
                NavigationDrawerItem(label={ Text(label) },selected=route?.destination?.route==key,onClick={
                    coroutine.launch { drawer.close() }
                    if(key=="pending") { pendingOpen=true; nav.navigate("agenda") { launchSingleTop=true } }
                    else nav.navigate(key) { launchSingleTop=true }
                },modifier=Modifier.padding(horizontal=12.dp))
            }
        } }) {
            Scaffold(topBar={ if(route?.destination?.route!="edit") TopAppBar(title={Text(if(route?.destination?.route=="agenda") "Agendapp" else when(route?.destination?.route){"calendar"->"Calendário";"search"->"Pesquisa";"settings"->"Configurações";"backup"->"Backup";"trash"->"Lixeira";else->"Sobre o aplicativo"})},navigationIcon={IconButton(onClick={coroutine.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,"Abrir menu")}}) },snackbarHost={SnackbarHost(snack)}) { padding ->
                NavHost(navController=nav,startDestination="agenda",modifier=Modifier.padding(padding)) {
                    composable("agenda") { AgendaScreen(backup.state,date,expanded,focusedTask,pendingOpen,
                        onDate={date=it;expanded=null;focusedTask=null},onCalendar={nav.navigate("calendar")},
                        onCreate={edit(Event(date=date.toString()))},onExpand={expanded=if(expanded==it)null else it;focusedTask=null},
                        onPending={pendingOpen=!pendingOpen},onEdit=::edit,vm=vm) }
                    composable("calendar") { CalendarScreen(backup.state,date) { vm.go(it.toString()) } }
                    composable("search") { SearchScreen(backup.state,vm) }
                    composable("edit") { editing?.let { EditorScreen(it,editingRule,vm) { nav.popBackStack() } } }
                    composable("settings") { SettingsScreen(prefs,vm) }
                    composable("backup") { BackupScreen(backup,vm) }
                    composable("trash") { TrashScreen(backup.state,vm) }
                    composable("about") { AboutScreen() }
                }
            }
        }
    }
}
