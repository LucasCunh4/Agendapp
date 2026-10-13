package br.com.agendapp.domain

import java.time.*
import java.text.Normalizer

object Engine {
    fun put(state: State, event: Event): State = state.copy(events = state.events.filterNot { it.id == event.id } + event)
    fun save(state: State, event: Event, rule: Rule?, scope: Scope, today: LocalDate): State {
        validateEvent(event)
        val tasks = Checklist.normalized(event.tasks)
        val progress = Checklist.progress(tasks)
        val previous = state.events.find { it.id == event.id }
        val changedPending = previous?.done == true && previous.tasks != tasks && tasks.any { !it.done }
        val clean = event.copy(tasks = tasks, done = when {
            changedPending -> false
            tasks.isNotEmpty() && progress.first == progress.second -> true
            else -> event.done
        }, manualDone = if (changedPending) false else event.manualDone, updatedAt = System.currentTimeMillis())
        if (event.seriesId == null) {
            if (rule == null) return put(state,clean)
            validateRule(rule)
            require(Recurrence.dates(LocalDate.parse(event.date),rule,LocalDate.parse(event.date)).any()) { "A data inicial deve corresponder aos dias ou à posição mensal da recorrência. Ajuste a data ou a regra." }
            val series = Series(segments = listOf(Segment(from=event.date,template=clean.copy(done=false,manualDone=false),rule=rule)))
            // Store the first occurrence too: absolute reminders belong only to this date.
            val occurrence = Recurrence.occurrence(series,series.segments.first(),event.date)
            return put(state.copy(events=state.events.filterNot { it.id==event.id },series=state.series+series),occurrence)
        }
        if (scope == Scope.ONLY) return put(state,clean)
        val old = state.series.first { it.id == event.seriesId }
        val originalDate = event.occurrenceDate ?: event.date
        val cutoff = if (scope == Scope.ALL) minOf(today.toString(),originalDate) else originalDate
        // Materialized occurrences, including partial checklists, are immutable history.
        val historyEnd=if(scope==Scope.ALL) today.minusDays(1) else LocalDate.parse(cutoff).minusDays(1)
        val preserved = Recurrence.allThrough(state,historyEnd).filter { it.seriesId==old.id }
        var next = state
        preserved.forEach { next=put(next,it) }
        next=next.copy(events=next.events.map { existing ->
            if(existing.seriesId==old.id && !existing.done && (scope==Scope.ALL || (existing.occurrenceDate ?: existing.date)>=cutoff))
                existing.copy(name=clean.name,start=clean.start,end=clean.end,place=clean.place,notes=clean.notes,
                    reminders=clean.reminders.filter { it.absolute==null }.map { it.copy(id=stable(existing.id,it.id)) },updatedAt=System.currentTimeMillis())
            else existing
        })
        val retainedSegments = old.segments.filter { it.from < cutoff }.map {
            it.copy(endExclusive = minOf(it.endExclusive ?: cutoff,cutoff))
        }
        val oldRule = old.segments.lastOrNull { it.from <= originalDate }?.rule ?: old.segments.first().rule
        val newSegments = if (rule == null) retainedSegments else {
            validateRule(rule)
            val newStart=maxOf(event.date,cutoff)
            require(Recurrence.dates(LocalDate.parse(newStart),rule,LocalDate.parse(newStart)).any()) { "A data inicial deve corresponder à nova regra de recorrência." }
            val active=old.segments.lastOrNull { it.from<=originalDate }
            val remaining=if(rule==oldRule && rule.count!=null && active!=null)
                rule.copy(count=maxOf(1,rule.count-Recurrence.dates(LocalDate.parse(active.from),active.rule,LocalDate.parse(newStart).minusDays(1)).count()))
            else rule
            retainedSegments + Segment(from=newStart,template=clean.copy(seriesId=null,occurrenceDate=null,done=false,manualDone=false),rule=remaining)
        }
        val revised = old.copy(segments=newSegments,updatedAt=System.currentTimeMillis())
        next=next.copy(series=next.series.map { if(it.id==old.id) revised else it })
        // When recurrence is removed, the edited occurrence becomes a standalone event.
        return put(next, if(rule==null) clean.copy(seriesId=null,occurrenceDate=null) else clean)
    }
    fun delete(state: State, event: Event, scope: Scope): State {
        if(event.seriesId==null || scope==Scope.ONLY) {
            val series = state.series.map { if(it.id==event.seriesId) it.copy(excluded=it.excluded+(event.occurrenceDate ?: event.date),updatedAt=System.currentTimeMillis()) else it }
            return state.copy(events=state.events.filterNot { it.id==event.id },series=series,trash=state.trash+Trash(event=event))
        }
        val old=state.series.first { it.id==event.seriesId }
        val date=event.occurrenceDate ?: event.date
        val records=state.events.filter { it.seriesId==old.id && (scope==Scope.ALL || (it.occurrenceDate ?: it.date)>=date) }
        val kept=if(scope==Scope.ALL) emptyList() else old.segments.filter { it.from<date }.map { it.copy(endExclusive=minOf(it.endExclusive ?: date,date)) }
        val removed=if(scope==Scope.ALL) old.segments else old.segments.filter { it.endExclusive==null || it.endExclusive>date }.map { s ->
            s.copy(notBefore=maxOf(s.notBefore ?: s.from,date))
        }
        val archived=old.copy(segments=removed)
        return state.copy(events=state.events-records.toSet(),series=state.series.filterNot { it.id==old.id }+if(kept.isEmpty()) emptyList() else listOf(old.copy(segments=kept)),
            trash=state.trash+Trash(series=archived,occurrences=records))
    }
    fun deleteTask(state: State,event: Event,taskId: String): State {
        val ids=Checklist.descendants(event.tasks,taskId)
        val removed=event.tasks.filter { it.id in ids }
        val remaining=Checklist.normalized(event.tasks.filterNot { it.id in ids })
        val p=Checklist.progress(remaining)
        val changed=event.copy(tasks=remaining,done=if(remaining.isEmpty()) event.done else p.first==p.second,manualDone=false,updatedAt=System.currentTimeMillis())
        return put(state,changed).copy(trash=state.trash+Trash(eventId=event.id,tasks=removed))
    }
    fun restore(state: State,item: Trash): State {
        var next=state
        item.event?.let { e ->
            require(state.events.none { it.id==e.id }) { "Já existe um evento com este identificador." }
            require(e.seriesId==null || state.series.any { it.id==e.seriesId }) { "A série original não existe. Restaure a série primeiro." }
            next=put(next,e).copy(series=next.series.map { if(it.id==e.seriesId) it.copy(excluded=it.excluded-(e.occurrenceDate ?: e.date)) else it })
        }
        item.series?.let { s ->
            val active=next.series.find { it.id==s.id }
            val segments=active?.segments?.toMutableList() ?: mutableListOf()
            for (restored in s.segments) {
                val idx=segments.indexOfFirst { it.id==restored.id }
                if(idx>=0) {
                    val existing=segments[idx]
                    require(existing.template==restored.template && existing.rule==restored.rule && existing.from==restored.from && existing.endExclusive==restored.notBefore) { "A série foi alterada após a exclusão; restauração entraria em conflito. Exporte um backup antes de resolver a série." }
                    segments[idx]=existing.copy(endExclusive=restored.endExclusive)
                } else segments+=restored
            }
            val sorted=segments.sortedBy { it.notBefore ?: it.from }
            sorted.zipWithNext().forEach { (a,b) -> require(a.endExclusive!=null && a.endExclusive <= (b.notBefore ?: b.from)) { "A restauração sobreporia regras da série." } }
            next=next.copy(series=next.series.filterNot { it.id==s.id }+s.copy(segments=sorted,excluded=(active?.excluded ?: emptySet())+s.excluded))
            item.occurrences.forEach { e -> require(next.events.none { it.id==e.id }); next=put(next,e) }
        }
        if(item.tasks.isNotEmpty()) {
            val event=next.events.firstOrNull { it.id==item.eventId } ?: error("O evento original não existe. Restaure-o primeiro.")
            val ids=event.tasks.map { it.id }.toSet()+item.tasks.map { it.id }
            require(item.tasks.all { it.parentId==null || it.parentId in ids }) { "A etapa ancestral não existe. Restaure-a primeiro." }
            require(item.tasks.none { t -> event.tasks.any { it.id==t.id } }) { "Identificadores de etapas em conflito." }
            val tasks=Checklist.normalized(event.tasks+item.tasks)
            val p=Checklist.progress(tasks)
            next=put(next,event.copy(tasks=tasks,done=p.second>0 && p.first==p.second,manualDone=false,updatedAt=System.currentTimeMillis()))
        }
        return next.copy(trash=next.trash.filterNot { it.id==item.id })
    }
    fun expire(state: State,now: Long) = state.copy(trash=state.trash.filter { now-it.deletedAt < Duration.ofDays(30).toMillis() })
    fun fold(text: String): String = Normalizer.normalize(text,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT)
    fun summary(count: Int,tomorrow: Boolean) = "${if(tomorrow) "Amanhã" else "Hoje"} você " + when(count) { 0 -> "não tem compromissos."; 1 -> "tem 1 compromisso."; else -> "tem $count compromissos." }
    fun validateRule(r: Rule) {
        require(r.interval>0 && (r.count==null || r.count>0)) { "Intervalo e quantidade devem ser positivos." }
        require(r.weekdays.all { it in 1..7 })
        require((r.ordinal==null)==(r.weekday==null))
        require(r.ordinal==null || r.ordinal in 1..5 || r.ordinal==-1)
        require(r.weekday==null || r.weekday in 1..7)
        r.until?.let(LocalDate::parse)
    }
    fun validateEvent(e: Event) {
        UUIDCheck(e.id); LocalDate.parse(e.date)
        e.occurrenceDate?.let(LocalDate::parse)
        require((e.seriesId==null)==(e.occurrenceDate==null)) { "Ocorrência inválida." }
        e.seriesId?.let(::UUIDCheck)
        e.start?.let(LocalTime::parse); e.end?.let(LocalTime::parse)
        require(e.start==null || e.end==null || LocalTime.parse(e.end)>=LocalTime.parse(e.start)) { "O término deve ser igual ou posterior ao início." }
        require(e.tasks.map { it.id }.distinct().size==e.tasks.size) { "Etapas duplicadas." }
        val ids=e.tasks.map { it.id }.toSet()
        e.tasks.forEach { t -> UUIDCheck(t.id); require(t.parentId==null || t.parentId in ids); t.time?.let(LocalTime::parse); validateReminders(t.reminders) }
        Checklist.normalized(e.tasks)
        validateReminders(e.reminders)
        fun checkMoments(time: String?,reminders: List<Reminder>) {
            if(time!=null) reminders.forEach { ReminderPlanner.moment(e.date,time,it,ZoneId.systemDefault()) }
        }
        checkMoments(e.start ?: e.end,e.reminders)
        e.tasks.forEach { checkMoments(it.time,it.reminders) }
        require((e.reminders+e.tasks.flatMap { it.reminders }).map { it.id }.distinct().size==(e.reminders+e.tasks.flatMap { it.reminders }).size) { "Lembretes duplicados." }
    }
    private fun validateReminders(rs: List<Reminder>) { rs.forEach {
        UUIDCheck(it.id); require((it.beforeMinutes==null)!=(it.absolute==null)) { "Defina lembrete relativo ou absoluto." }
        require(it.beforeMinutes==null || it.beforeMinutes>=0)
        it.absolute?.let(LocalDateTime::parse)
    } }
    fun UUIDCheck(id: String) { require(java.util.UUID.fromString(id).toString()==id) { "Identificador inválido." } }
}
