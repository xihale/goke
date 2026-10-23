package app.mintcal.notify

import android.content.Context
import app.mintcal.R
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.parcel.ParcelInfo
import app.mintcal.core.parcel.ParcelParser
import app.mintcal.core.util.Ids

object ParcelTasks {
    fun build(
        ctx: Context,
        info: ParcelInfo,
    ): Task =
        Task(
            id = Ids.next(),
            title = ctx.getString(R.string.parcel_task_title, info.code, info.place.ifBlank { info.company }),
            note = ParcelParser.redact(info.raw),
            due = info.deadline,
            dueHasTime = info.deadline != null,
            priority = 2,
            remindMinutes = if (info.deadline != null) 60 else null,
            source = Source.PARCEL,
            extra = mapOf("code" to info.code, "place" to info.place, "company" to info.company),
        )
}
