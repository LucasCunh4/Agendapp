package br.com.agendapp.notifications

import android.content.*
import android.util.Log
import br.com.agendapp.AgendappApplication
import kotlinx.coroutines.launch
import androidx.work.*

class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending=goAsync(); val app=context.applicationContext as AgendappApplication
        app.scope.launch { try { app.alarms.fire(intent) } catch(e: Exception) { Log.e("Agendapp","Falha no alarme",e) } finally { pending.finish() } }
    }
}
class SystemReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending=goAsync(); val app=context.applicationContext as AgendappApplication
        app.scope.launch { try { app.repository.initialize(); app.alarms.rebuild() } catch(e: Exception) { Log.e("Agendapp","Falha ao reprogramar",e) } finally { pending.finish() } }
    }
}
class MaintenanceWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app=applicationContext as AgendappApplication
        return try { app.repository.initialize(); app.alarms.rebuild(true); Result.success() } catch(_: Exception) { Result.retry() }
    }
}
