package br.com.agendapp.domain

import java.time.*

data class PlannedAlarm(val id: String,val instant: Instant,val date: String,val eventId: String?=null,val taskId: String?=null,val summaryTomorrow: Boolean?=null)
object ReminderPlanner {
    fun moment(date: String,time: String,reminder: Reminder,zone: ZoneId): Instant =
        (reminder.absolute?.let(LocalDateTime::parse) ?: LocalDate.parse(date).atTime(LocalTime.parse(time)).minusMinutes(reminder.beforeMinutes!!)).atZone(zone).toInstant()
    fun event(event: Event,now: Instant,zone: ZoneId): List<PlannedAlarm> {
        if(event.done) return emptyList()
        val result=mutableListOf<PlannedAlarm>()
        fun add(time: String?,reminders: List<Reminder>,taskId: String?) {
            if(time==null) return
            reminders.forEach { r ->
                val at=moment(event.date,time,r,zone)
                if(at>now) result+=PlannedAlarm(stable(event.id,taskId ?: "event",r.id),at,event.date,event.id,taskId)
            }
        }
        add(event.start ?: event.end,event.reminders,null)
        event.tasks.filterNot { it.done }.forEach { add(it.time,it.reminders,it.id) }
        return result
    }
    fun window(state: State,settings: Settings,now: Instant,zone: ZoneId,days: Long=14): List<PlannedAlarm> {
        val today=now.atZone(zone).toLocalDate()
        val end=today.plusDays(days)
        val templates=state.series.flatMap { it.segments }.map { it.template }
        val relative=(state.events+templates).flatMap { it.reminders+it.tasks.flatMap { task -> task.reminders } }.mapNotNull { it.beforeMinutes }.maxOrNull() ?: 0
        val through=end.plusDays(relative/1440+2)
        val persisted=state.events.associateBy { it.id }
        val generated=state.series.flatMap { Recurrence.generated(it,through).filter { e -> e.id !in persisted }.toList() }
        val absoluteOrigins=state.series.flatMap { series -> series.segments.mapNotNull { segment ->
            if((segment.template.reminders+segment.template.tasks.flatMap { it.reminders }).any { it.absolute!=null } && segment.template.date !in series.excluded)
                Recurrence.generated(series,LocalDate.parse(segment.template.date)).find { it.date==segment.template.date && it.id !in persisted }
            else null
        } }
        val alarms=(state.events+generated+absoluteOrigins).distinctBy { it.id }.flatMap { event(it,now,zone) }.filter { it.instant <= end.plusDays(1).atStartOfDay(zone).toInstant() }.toMutableList()
        listOf(false to (settings.morning to settings.morningTime),true to (settings.evening to settings.eveningTime)).forEach { (tomorrow,config) ->
            if(config.first) {
                var time=today.atTime(LocalTime.parse(config.second)).atZone(zone)
                if(time.toInstant()<=now) time=today.plusDays(1).atTime(LocalTime.parse(config.second)).atZone(zone)
                alarms+=PlannedAlarm(stable("summary",tomorrow.toString()),time.toInstant(),time.toLocalDate().toString(),summaryTomorrow=tomorrow)
            }
        }
        // An AlarmManager renewal remains available even if periodic work is deferred.
        alarms+=PlannedAlarm(stable("renewal"),today.plusDays(1).atTime(0,5).atZone(zone).toInstant(),today.toString())
        return alarms.distinctBy { it.id }
    }
}
