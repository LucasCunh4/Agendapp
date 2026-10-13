package br.com.agendapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime

object BackupCodec {
    fun encode(backup: Backup): String = codec.encodeToString(backup)
    fun decode(text: String): Backup {
        val root=codec.parseToJsonElement(text).jsonObject
        require(root.keys.containsAll(listOf("version","state","settings"))) { "O backup deve conter versão, dados e configurações." }
        fun objectKeys(value: JsonElement,required: List<String>): JsonObject = value.jsonObject.also {
            require(it.keys.containsAll(required)) { "Estrutura incompleta: ${required.joinToString()}." }
        }
        fun tasks(value: JsonElement) {
            value.jsonArray.forEach { element ->
                val task=objectKeys(element,listOf("id","parentId"))
                task["reminders"]?.jsonArray?.forEach { objectKeys(it,listOf("id")) }
            }
        }
        fun event(value: JsonElement) {
            val e=objectKeys(value,listOf("id","date"))
            e["tasks"]?.let(::tasks)
            e["reminders"]?.jsonArray?.forEach { objectKeys(it,listOf("id")) }
        }
        fun series(value: JsonElement) {
            val s=objectKeys(value,listOf("id","segments"))
            s.getValue("segments").jsonArray.forEach { element ->
                val segment=objectKeys(element,listOf("id","from","template","rule"))
                event(segment.getValue("template"))
                objectKeys(segment.getValue("rule"),listOf("frequency","interval"))
            }
        }
        val state=objectKeys(root.getValue("state"),listOf("events","series","trash"))
        state.getValue("events").jsonArray.forEach(::event)
        state.getValue("series").jsonArray.forEach(::series)
        state.getValue("trash").jsonArray.forEach { element ->
            val item=objectKeys(element,listOf("id","deletedAt"))
            item["event"]?.takeUnless { it==JsonNull }?.let(::event)
            item["series"]?.takeUnless { it==JsonNull }?.let(::series)
            item["occurrences"]?.jsonArray?.forEach(::event)
            item["tasks"]?.let(::tasks)
        }
        return codec.decodeFromJsonElement<Backup>(root).also(::validate)
    }
    fun validate(backup: Backup) {
        require(backup.version==1) { "Versão de backup não suportada." }
        LocalTime.parse(backup.settings.morningTime); LocalTime.parse(backup.settings.eveningTime)
        val state=backup.state
        require(state.events.map { it.id }.distinct().size==state.events.size) { "Eventos duplicados." }
        require(state.series.map { it.id }.distinct().size==state.series.size) { "Séries duplicadas." }
        require(state.trash.map { it.id }.distinct().size==state.trash.size) { "Itens da lixeira duplicados." }
        fun validateSeries(s: Series) {
            Engine.UUIDCheck(s.id)
            require(s.segments.map { it.id }.distinct().size==s.segments.size)
            s.excluded.forEach(LocalDate::parse)
            val sorted=s.segments.sortedBy { it.notBefore ?: it.from }
            sorted.forEach { seg ->
                Engine.UUIDCheck(seg.id); Engine.validateEvent(seg.template); Engine.validateRule(seg.rule)
                LocalDate.parse(seg.from); seg.notBefore?.let(LocalDate::parse); seg.endExclusive?.let(LocalDate::parse)
                require(seg.notBefore==null || seg.notBefore>=seg.from)
                require(seg.endExclusive==null || seg.endExclusive>(seg.notBefore ?: seg.from))
            }
            sorted.zipWithNext().forEach { (a,b) -> require(a.endExclusive!=null && a.endExclusive<=(b.notBefore ?: b.from)) { "Regras recorrentes sobrepostas." } }
        }
        state.series.forEach(::validateSeries)
        state.events.forEach { e ->
            Engine.validateEvent(e)
            require(e.seriesId==null || state.series.any { it.id==e.seriesId }) { "Referência de série inexistente." }
            require(e.seriesId==null || e.id==stable(e.seriesId,e.occurrenceDate!!)) { "Identificador de ocorrência inconsistente." }
        }
        state.trash.forEach { t ->
            Engine.UUIDCheck(t.id); require(t.deletedAt>=0)
            require(listOf(t.event!=null,t.series!=null,t.tasks.isNotEmpty()).count { it }==1) { "Item de lixeira inválido." }
            t.event?.let(Engine::validateEvent); t.series?.let(::validateSeries); t.occurrences.forEach(Engine::validateEvent)
            if(t.tasks.isNotEmpty()) {
                require(t.eventId!=null); Engine.UUIDCheck(t.eventId)
                val detached=t.tasks.map { task -> task.copy(parentId=task.parentId?.takeIf { p -> t.tasks.any { it.id==p } }) }
                Engine.validateEvent(Event(date="2000-01-01",tasks=detached))
            }
        }
    }
    fun conflicts(a: Backup,b: Backup): Int {
        fun <T> count(x: List<T>,y: List<T>,id: (T)->String) = y.count { entry -> x.any { id(it)==id(entry) && it!=entry } }
        return count(a.state.events,b.state.events) { it.id }+count(a.state.series,b.state.series) { it.id }+count(a.state.trash,b.state.trash) { it.id }+if(a.settings!=b.settings) 1 else 0
    }
    fun merge(local: Backup, incoming: Backup, preferImported: Boolean): Backup {
        validate(local); validate(incoming)
        fun <T> join(a: List<T>,b: List<T>,key: (T)->String): List<T> {
            val result=linkedMapOf<String,T>()
            (if(preferImported) a+b else b+a).forEach { result[key(it)]=it }
            return result.values.toList()
        }
        val result=Backup(state=State(
            events=join(local.state.events,incoming.state.events) { it.id },
            series=join(local.state.series,incoming.state.series) { it.id },
            trash=join(local.state.trash,incoming.state.trash) { it.id }),
            settings=if(preferImported) incoming.settings else local.settings)
        val trashedEvents=result.state.trash.mapNotNull { it.event?.id }.toSet()+result.state.trash.flatMap { it.occurrences }.map { it.id }
        require(result.state.events.none { it.id in trashedEvents }) { "Há um evento ativo e excluído com o mesmo ID; restaure ou exclua o item antes de mesclar." }
        result.state.trash.forEach { trash ->
            val active=result.state.events.find { it.id==trash.eventId }
            require(active==null || trash.tasks.none { removed -> active.tasks.any { it.id==removed.id } }) { "Uma etapa está ativa e excluída com o mesmo ID. Resolva o item antes de mesclar." }
        }
        validate(result)
        return result
    }
}
