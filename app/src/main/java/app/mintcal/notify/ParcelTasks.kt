package app.mintcal.notify

import android.content.Context
import app.mintcal.R
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.parcel.ParcelInfo
import app.mintcal.core.parcel.ParcelParser
import app.mintcal.core.parcel.parcelWindow
import app.mintcal.core.util.Ids
import app.mintcal.mintApp
import java.time.LocalDateTime

object ParcelTasks {
    fun build(
        ctx: Context,
        info: ParcelInfo,
    ): Task {
        // 取件一般下午/晚上下班后再去，日程排进设置的取件时段（截止仍是驿站给的时刻，管提醒）
        val st = ctx.mintApp.settings.value
        val window = parcelWindow(LocalDateTime.now(), st.parcelWindowStart, st.parcelWindowEnd)
        return Task(
            id = Ids.next(),
            title = ctx.getString(R.string.parcel_task_title, info.code, info.place.ifBlank { info.company }),
            note = ParcelParser.redact(info.raw),
            due = info.deadline,
            dueHasTime = info.deadline != null,
            priority = 2,
            remindMinutes = if (info.deadline != null) 60 else null,
            blockStart = window?.first,
            blockMinutes = window?.second,
            source = Source.PARCEL,
            extra = mapOf("code" to info.code, "place" to info.place, "company" to info.company),
        )
    }
}
