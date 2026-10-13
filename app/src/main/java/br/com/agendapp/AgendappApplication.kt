package br.com.agendapp

import android.app.Application
import androidx.work.*
import br.com.agendapp.data.Repository
import br.com.agendapp.notifications.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class AgendappApplication: Application() {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    lateinit var repository: Repository
    lateinit var alarms: AlarmScheduler
    override fun onCreate() {
        super.onCreate()
        repository=Repository(this)
        alarms=AlarmScheduler(this,repository)
        alarms.channels()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("maintenance",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(12,TimeUnit.HOURS).build())
        scope.launch { repository.initialize(); alarms.rebuild(true) }
    }
}
