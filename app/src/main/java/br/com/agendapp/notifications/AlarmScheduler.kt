package br.com.agendapp.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.core.content.edit
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.agendapp.*
import br.com.agendapp.data.Repository
import br.com.agendapp.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class AlarmScheduler(private val context: Context,private val repository: Repository) {
    private val manager=context.getSystemService(AlarmManager::class.java)
    private val preferences=context.getSharedPreferences("scheduled_alarms",Context.MODE_PRIVATE)
    private val mutex=Mutex()
    fun channels() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel("events","Lembretes de compromissos",NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("summaries","Resumos diários",NotificationManager.IMPORTANCE_DEFAULT)))
    }
    fun exactAllowed() = Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms()
    fun notificationsAllowed() = (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    private fun pending(id: String,intent: Intent?=null): PendingIntent = PendingIntent.getBroadcast(context,0,
        (intent ?: Intent(context,AlarmReceiver::class.java)).setData("agendapp://alarm/$id".toUri()),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun rebuild(preserveDue: Boolean=false)=mutex.withLock {
        val now=System.currentTimeMillis()
        val existing=preferences.getStringSet("ids",emptySet())!!.toSet()
        val due=if(preserveDue) existing.filter { preferences.getLong("at:$it",Long.MAX_VALUE)<=now }.toSet() else emptySet()
        (existing-due).forEach { manager.cancel(pending(it)) }
        val backup=repository.current()
        val planned=ReminderPlanner.window(backup.state,backup.settings,Instant.now(),ZoneId.systemDefault())
            .sortedBy { it.instant }.take(maxOf(0,200-due.size))
        // Journal IDs before scheduling so interrupted rebuilds can still be cancelled safely.
        preferences.edit(commit=true) {
            putStringSet("ids",due+planned.map { it.id }.toSet())
            (existing-due).forEach { remove("at:$it") }
            planned.forEach { putLong("at:${it.id}",it.instant.toEpochMilli()) }
            preferences.all.keys.filter { it.startsWith("sent:") && preferences.getLong(it,0)<now-Duration.ofDays(30).toMillis() }.forEach { remove(it) }
        }
        planned.forEach { p ->
            val intent=Intent(context,AlarmReceiver::class.java).putExtra("id",p.id).putExtra("date",p.date)
                .putExtra("event",p.eventId).putExtra("task",p.taskId).putExtra("at",p.instant.toEpochMilli())
            p.summaryTomorrow?.let { intent.putExtra("summary",it) }
            val pi=pending(p.id,intent)
            try {
                if(exactAllowed()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.instant.toEpochMilli(),pi)
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.instant.toEpochMilli(),pi)
            } catch(_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.instant.toEpochMilli(),pi) }
        }
    }
    suspend fun fire(intent: Intent) {
        val id=intent.getStringExtra("id") ?: return
        if(id==stable("renewal")) { acknowledge(id); repository.initialize(); rebuild(true); return }
        val atMillis=intent.getLongExtra("at",0)
        if(atMillis>System.currentTimeMillis()) return
        if(preferences.getLong("sent:$id",-1)==atMillis) { acknowledge(id); rebuild(true); return }
        val backup=repository.current()
        if(intent.hasExtra("summary")) {
            val tomorrow=intent.getBooleanExtra("summary",false)
            val enabled=if(tomorrow) backup.settings.evening else backup.settings.morning
            val expected=if(tomorrow) backup.settings.eveningTime else backup.settings.morningTime
            val at=intent.getLongExtra("at",0).let(Instant::ofEpochMilli).atZone(ZoneId.systemDefault())
            if(enabled && at.toLocalTime()==LocalTime.parse(expected)) {
                val date=LocalDate.now().plusDays(if(tomorrow) 1 else 0)
                preferences.edit(commit=true) { putLong("sent:$id",atMillis) }
                notify(id,"Agendapp",Engine.summary(Recurrence.onDay(backup.state,date).size,tomorrow),date.toString(),null,null,"summaries")
            }
        } else {
            val eventId=intent.getStringExtra("event") ?: return
            val date=intent.getStringExtra("date") ?: return
            val event=Recurrence.find(backup.state,eventId,date)
            val eligible=event?.let { ReminderPlanner.event(it,Instant.EPOCH,ZoneId.systemDefault()).find { p -> p.id==id && p.instant.toEpochMilli()==intent.getLongExtra("at",0) } }
            if(event!=null && eligible!=null) {
                val task=event.tasks.find { it.id==eligible.taskId }
                preferences.edit(commit=true) { putLong("sent:$id",atMillis) }
                notify(id,event.title,task?.name?.ifBlank { "Etapa sem nome" } ?: "Seu compromisso está chegando.",event.date,event.id,task?.id,"events")
            }
        }
        acknowledge(id)
        rebuild(true)
    }
    private fun acknowledge(id: String) {
        preferences.edit(commit=true) {
            putStringSet("ids",preferences.getStringSet("ids",emptySet())!!-id)
            remove("at:$id")
        }
    }
    fun test() { notify(stable("test"),"Agendapp","Notificação de teste. Os lembretes funcionam offline.",LocalDate.now().toString(),null,null,"events") }
    private fun notify(id: String,title: String,text: String,date: String,event: String?,task: String?,channel: String) {
        if(!notificationsAllowed()) return
        val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData("agendapp://agenda/$id".toUri()).putExtra("date",date).putExtra("event",event).putExtra("task",task)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_agendapp).setContentTitle(title)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id,0,notification) } catch(_: SecurityException) { }
    }
}
