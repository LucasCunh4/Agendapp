package br.com.agendapp.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*
import java.util.UUID

fun uid(): String = UUID.randomUUID().toString()
fun stable(vararg parts: String): String = UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray()).toString()
val codec = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable data class Reminder(val id: String = uid(), val beforeMinutes: Long? = 15, val absolute: String? = null)
@Serializable data class Task(
    val id: String = uid(), val parentId: String? = null, val position: Int = 0,
    val name: String = "", val notes: String = "", val time: String? = null,
    val done: Boolean = false, val reminders: List<Reminder> = emptyList()
)
@Serializable data class Event(
    val id: String = uid(), val date: String, val name: String = "", val start: String? = null,
    val end: String? = null, val place: String = "", val notes: String = "",
    val tasks: List<Task> = emptyList(), val reminders: List<Reminder> = emptyList(),
    val done: Boolean = false, val manualDone: Boolean = false,
    val seriesId: String? = null, val occurrenceDate: String? = null, val updatedAt: Long = System.currentTimeMillis()
) { val title: String get() = name.ifBlank { "Sem título" } }
@Serializable enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
@Serializable data class Rule(
    val frequency: Frequency = Frequency.DAILY, val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(), val ordinal: Int? = null, val weekday: Int? = null,
    val until: String? = null, val count: Int? = null
)
@Serializable data class Segment(val id: String = uid(), val from: String, val endExclusive: String? = null, val notBefore: String? = null, val template: Event, val rule: Rule)
@Serializable data class Series(val id: String = uid(), val segments: List<Segment>, val excluded: Set<String> = emptySet(), val updatedAt: Long = System.currentTimeMillis())
@Serializable data class Trash(
    val id: String = uid(), val deletedAt: Long = System.currentTimeMillis(),
    val event: Event? = null, val series: Series? = null, val occurrences: List<Event> = emptyList(),
    val eventId: String? = null, val tasks: List<Task> = emptyList()
) { val title: String get() = event?.title ?: series?.segments?.firstOrNull()?.template?.title ?: occurrences.firstOrNull()?.title ?: tasks.firstOrNull()?.name?.ifBlank { "Etapa" } ?: "Item" }
@Serializable data class Settings(
    val dark: Boolean = false, val morning: Boolean = true, val morningTime: String = "07:00",
    val evening: Boolean = true, val eveningTime: String = "20:00"
)
@Serializable data class State(val events: List<Event> = emptyList(), val series: List<Series> = emptyList(), val trash: List<Trash> = emptyList())
@Serializable data class Backup(val version: Int = 1, val state: State = State(), val settings: Settings = Settings())
enum class Scope { ONLY, FUTURE, ALL }
