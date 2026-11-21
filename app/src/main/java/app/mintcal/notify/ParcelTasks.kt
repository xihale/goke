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
        // 快递不占日历/课表时间块：截止保留驿站给的时刻用于提醒，顺路取件时段仅作扩展元数据存进 extra
        val st = ctx.mintApp.settings.value
        val window = parcelWindow(LocalDateTime.now(), st.parcelWindowStart, st.parcelWindowEnd)
        val extra =
            buildMap {
                put("code", info.code)
                put("place", info.place)
                put("company", info.company)
                if (window != null) {
                    put("windowStart", "%02d:%02d".format(st.parcelWindowStart / 60, st.parcelWindowStart % 60))
                    put("windowEnd", "%02d:%02d".format(st.parcelWindowEnd / 60, st.parcelWindowEnd % 60))
                }
            }
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
