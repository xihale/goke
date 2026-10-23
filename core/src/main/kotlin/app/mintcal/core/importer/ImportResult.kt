package app.mintcal.core.importer

/** 解析器统一返回类型：成功值 + 警告 + 错误（不直接抛给界面）。 */
data class ImportResult<out T>(
    val value: T?,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val ok: Boolean get() = value != null && errors.isEmpty()

    companion object {
        fun <T> failure(vararg errors: String): ImportResult<T> = ImportResult(null, emptyList(), errors.toList())
    }
}
