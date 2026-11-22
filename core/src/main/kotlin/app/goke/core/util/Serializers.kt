package app.goke.core.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: LocalDate,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: LocalTime,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: LocalDateTime,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDateTime = parseDateTimeLenient(decoder.decodeString())
}

/** 宽松解析：`2026-09-01T08:30`、`2026-09-01 08:30`、`2026-09-01`（按 00:00）。 */
fun parseDateTimeLenient(text: String): LocalDateTime {
    val s = text.trim().replace(' ', 'T')
    return if (s.contains('T')) LocalDateTime.parse(s) else LocalDate.parse(s).atStartOfDay()
}

/** 全局 JSON 配置：容忍未知字段、始终写出默认值（便于变更集 / 导出稳定）。 */
val MintJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminator = "_t"
        prettyPrint = false
    }
