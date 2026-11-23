package app.goke.notify

import android.content.Context
import app.goke.R
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.parcel.ParcelInfo
import app.goke.core.parcel.ParcelParser
import app.goke.core.util.Ids

object ParcelTasks {
    fun build(
        ctx: Context,
        info: ParcelInfo,
    ): Task {
        // 快递不占日历/课表时间块：截止保留驿站给的时刻用于提醒，列顶胶囊按截止日或当天汇总展示
        val extra =
            mapOf(
                "code" to info.code,
                "place" to info.place,
                "company" to info.company,
            )
        return Task(
            id = Ids.next(),
            title = ctx.getString(R.string.parcel_task_title, info.code, info.place.ifBlank { info.company }),
            note = ParcelParser.redact(info.raw),
            due = info.deadline,
            dueHasTime = info.deadline != null,
            priority = 2,
            remindMinutes = if (info.deadline != null) 60 else null,
            source = Source.PARCEL,
            extra = extra,
        )
    }
}
