package br.com.agendapp.domain

object Checklist {
    fun descendants(tasks: List<Task>, id: String): Set<String> {
        val found = mutableSetOf(id)
        var changed: Boolean
        do { changed = false; tasks.forEach { if (it.parentId in found && found.add(it.id)) changed = true } } while(changed)
        return found
    }
    fun normalized(tasks: List<Task>): List<Task> {
        val result = tasks.associateBy { it.id }.toMutableMap()
        val parents = tasks.mapNotNull { it.parentId }.toSet()
        val resolved = tasks.filter { it.id !in parents }.map { it.id }.toMutableSet()
        while (resolved.size < tasks.size) {
            var changed = false
            tasks.filter { it.id !in resolved }.forEach { t ->
                val children = tasks.filter { it.parentId == t.id }
                if (children.all { it.id in resolved }) {
                    result[t.id] = t.copy(done = children.all { result.getValue(it.id).done })
                    resolved += t.id; changed = true
                }
            }
            require(changed) { "A hierarquia contém um ciclo." }
        }
        return tasks.map { result.getValue(it.id) }
    }
    fun progress(tasks: List<Task>): Pair<Int,Int> {
        val parents = tasks.mapNotNull { it.parentId }.toSet()
        val leaves = tasks.filter { it.id !in parents }
        return leaves.count { it.done } to leaves.size
    }
    fun toggle(event: Event, taskId: String, done: Boolean): Event {
        val ids = descendants(event.tasks,taskId)
        val tasks = normalized(event.tasks.map { if (it.id in ids) it.copy(done = done) else it })
        val (completed,total) = progress(tasks)
        return event.copy(tasks = tasks, done = total > 0 && completed == total, manualDone = false, updatedAt = System.currentTimeMillis())
    }
    fun complete(event: Event, completeTasks: Boolean): Event = event.copy(done = true, manualDone = true,
        tasks = if (completeTasks) event.tasks.map { it.copy(done = true) } else event.tasks, updatedAt = System.currentTimeMillis())
    fun reopen(event: Event): Event = event.copy(done = false, manualDone = false,
        tasks = event.tasks, updatedAt = System.currentTimeMillis())
    fun move(tasks: List<Task>, id: String, parent: String?, before: String?): List<Task> {
        require(parent == null || tasks.any { it.id == parent }) { "Etapa de destino inexistente." }
        require(parent !in descendants(tasks,id)) { "Uma etapa não pode ser sua própria descendente." }
        val moving = tasks.first { it.id == id }.copy(parentId = parent)
        val siblings = tasks.filter { it.parentId == parent && it.id != id }.sortedBy { it.position }.toMutableList()
        val index = siblings.indexOfFirst { it.id == before }.takeIf { it >= 0 } ?: siblings.size
        siblings.add(index,moving)
        val positions = siblings.mapIndexed { i,t -> t.id to t.copy(position=i) }.toMap()
        return normalized(tasks.map { positions[it.id] ?: it })
    }
    fun ordered(tasks: List<Task>, expanded: Set<String>? = null): List<Pair<Task,Int>> {
        val out = mutableListOf<Pair<Task,Int>>()
        val stack = java.util.ArrayDeque<Pair<Task,Int>>()
        tasks.filter { it.parentId == null }.sortedByDescending { it.position }.forEach { stack.push(it to 0) }
        while(stack.isNotEmpty()) {
            val (t,d) = stack.pop(); out += t to d
            if (expanded == null || t.id in expanded) tasks.filter { it.parentId == t.id }.sortedByDescending { it.position }.forEach { stack.push(it to d+1) }
        }
        return out
    }
}
