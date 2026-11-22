package app.goke.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.goke.R
import app.goke.core.change.ChangeSet
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.todo.Bucket
import app.goke.core.util.Ids

internal fun csvToSet(s: String): Set<String> = s.split(',').filter { it.isNotEmpty() }.toSet()

internal fun Set<String>.toCsv(): String = joinToString(",")

internal fun bucketRes(b: Bucket): Int =
    when (b) {
        Bucket.OVERDUE -> R.string.bucket_overdue
        Bucket.TODAY -> R.string.bucket_today
        Bucket.TOMORROW -> R.string.bucket_tomorrow
        Bucket.THIS_WEEK -> R.string.bucket_this_week
        Bucket.LATER -> R.string.bucket_later
        Bucket.INBOX -> R.string.bucket_inbox
        Bucket.DONE -> R.string.bucket_done
    }

/** 把若干变更集合成一个（一次撤销）。 */
internal fun mergeChangeSets(
    description: String,
    sets: List<ChangeSet>,
): ChangeSet = ChangeSet(Ids.next(), description, Source.MANUAL, System.currentTimeMillis(), sets.flatMap { it.changes })
