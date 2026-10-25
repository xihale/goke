package app.mintcal.core.todo

import app.mintcal.core.model.Task

/** 待办搜索：`#清单 @标签 !高` 加普通文字，全部条件同时满足才命中。 */
data class TodoQuery(
    val words: List<String>,
    val lists: List<String>,
    val tags: List<String>,
    val priority: Int?,
) {
    val isEmpty: Boolean get() = words.isEmpty() && lists.isEmpty() && tags.isEmpty() && priority == null
}

object TodoSearch {
    fun parse(input: String): TodoQuery {
        val words = mutableListOf<String>()
        val lists = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var priority: Int? = null
        for (raw in input.trim().split(Regex("\\s+"))) {
            if (raw.isEmpty()) continue
            when {
                raw.startsWith("#") && raw.length > 1 -> lists += raw.substring(1).lowercase()
                raw.startsWith("@") && raw.length > 1 -> tags += raw.substring(1).lowercase()
                raw.startsWith("!") || raw.startsWith("！") -> {
                    priority =
                        when (raw.substring(1).lowercase()) {
                            "高", "high", "3" -> 3
                            "中", "mid", "2" -> 2
                            "低", "low", "1" -> 1
                            else -> priority
                        }
                }

                else -> words += raw.lowercase()
            }
        }
        return TodoQuery(words, lists, tags, priority)
    }

    /** [subs] 里有子任务命中也算（父任务连同展开显示）。 */
    fun matches(
        q: TodoQuery,
        task: Task,
        subs: List<Task>,
        listName: String?,
    ): Boolean {
        if (q.isEmpty) return true
        if (q.lists.isNotEmpty() && q.lists.any { listName?.lowercase()?.contains(it) != true }) return false
        if (q.tags.isNotEmpty() && q.tags.any { t -> task.tags.none { it.lowercase().contains(t) } }) return false
        if (q.priority != null && task.priority != q.priority) return false
        return q.words.all { w ->
            task.title.lowercase().contains(w) || task.note.lowercase().contains(w) ||
                subs.any { it.title.lowercase().contains(w) }
        }
    }
}
