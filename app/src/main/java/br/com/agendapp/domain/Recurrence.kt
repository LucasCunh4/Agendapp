package br.com.agendapp.domain

import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Recurrence {
    fun dates(start: LocalDate, rule: Rule, through: LocalDate): Sequence<LocalDate> = sequence {
        require(rule.interval > 0)
        val end = minOf(through, rule.until?.let(LocalDate::parse) ?: through)
        var emitted = 0
        var period = 0L
        while (true) {
            val base = when(rule.frequency) {
                Frequency.DAILY -> start.plusDays(period * rule.interval)
                Frequency.WEEKLY -> start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(period * rule.interval)
                Frequency.MONTHLY -> start.withDayOfMonth(1).plusMonths(period * rule.interval)
                Frequency.YEARLY -> start.withDayOfYear(1).plusYears(period * rule.interval)
            }
            if (base > end) break
            val candidates = when(rule.frequency) {
                Frequency.DAILY -> listOf(base)
                Frequency.WEEKLY -> (rule.weekdays.ifEmpty { setOf(start.dayOfWeek.value) }).sorted().map { base.plusDays((it-1).toLong()) }
                Frequency.MONTHLY -> {
                    val day = rule.weekday
                    val ordinal = rule.ordinal
                    if (day != null && ordinal != null) {
                        val d = if (ordinal == -1) base.with(TemporalAdjusters.lastInMonth(DayOfWeek.of(day)))
                        else base.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, DayOfWeek.of(day)))
                        if (d.month == base.month) listOf(d) else emptyList()
                    } else listOf(base.withDayOfMonth(minOf(start.dayOfMonth, base.lengthOfMonth())))
                }
                Frequency.YEARLY -> {
                    val month = base.withMonth(start.monthValue)
                    listOf(month.withDayOfMonth(minOf(start.dayOfMonth, month.lengthOfMonth())))
                }
            }
            for (date in candidates) {
                if (date < start || date > end) continue
                if (rule.count != null && emitted >= rule.count) return@sequence
                emitted++
                yield(date)
            }
            period++
        }
    }
    fun occurrence(series: Series, segment: Segment, date: String): Event {
        val base = segment.template
        val eventId = stable(series.id, date)
        val mapping = base.tasks.associate { it.id to stable(eventId, it.id) }
        return base.copy(id = eventId, date = date, seriesId = series.id, occurrenceDate = date,
            done = false, manualDone = false,
            tasks = base.tasks.map { it.copy(id = mapping.getValue(it.id), parentId = it.parentId?.let(mapping::getValue), done = false,
                reminders = it.reminders.filter { r -> r.absolute == null || date == base.date }.map { r -> r.copy(id = stable(eventId, r.id)) }) },
            reminders = base.reminders.filter { it.absolute == null || date == base.date }.map { it.copy(id = stable(eventId,it.id)) })
    }
    fun generated(series: Series, through: LocalDate): Sequence<Event> = series.segments.asSequence().flatMap { s ->
        val last = minOf(through, s.endExclusive?.let { LocalDate.parse(it).minusDays(1) } ?: through)
        dates(LocalDate.parse(s.from), s.rule, last).filter { it.toString() !in series.excluded && (s.notBefore == null || it.toString() >= s.notBefore) }
            .map { occurrence(series, s, it.toString()) }
    }
    fun onDay(state: State, date: LocalDate): List<Event> {
        val overrides = state.events.associateBy { it.id }
        val generated = state.series.flatMap { series ->
            generated(series, date).filter { it.date == date.toString() && it.id !in overrides }.toList()
        }
        return (state.events.filter { it.date == date.toString() } + generated).sortedWith(
            compareBy<Event> { it.done }.thenBy { it.start ?: it.end ?: "" }.thenBy { it.title })
    }
    fun allThrough(state: State, date: LocalDate): List<Event> {
        val overrides = state.events.associateBy { it.id }
        return state.events.filter { LocalDate.parse(it.date) <= date } + state.series.flatMap { series ->
            generated(series,date).filter { it.id !in overrides }.toList()
        }
    }
    fun pending(state: State, today: LocalDate) = allThrough(state,today.minusDays(1)).filterNot { it.done }.sortedBy { it.date }
    fun find(state: State, id: String, date: String): Event? = state.events.find { it.id == id }
        ?: onDay(state,LocalDate.parse(date)).find { it.id == id }
}
