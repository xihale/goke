package app.goke.hotwater

import kotlinx.serialization.Serializable

/** HTTPS 接口统一返回包装。 */
sealed interface HotWaterApiResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : HotWaterApiResult<T>

    data class Err(
        val code: HotWaterErrorCode,
        val detail: String = "",
    ) : HotWaterApiResult<Nothing>
}

/** 敏感凭据存储契约：真机由 SecretStore 实现，单测可用内存实现。 */
interface HotWaterVault {
    fun hotWaterUserId(): String?

    fun setHotWaterUserId(plain: String)

    fun hotWaterUuid(): String?

    fun setHotWaterUuid(plain: String)
}

/** 欠费自动待办文案（由宿主从 strings.xml 注入，保持适配器零硬编码中文与零 R.string 耦合）。 */
data class HotWaterTaskCopy(
    val title: String,
    val note: String,
    val opDesc: String,
)

/** 非敏感配置与设备元数据。 */
data class HotWaterConfig(
    val phone: String = "",
    val machineSn: String = "",
    val bluetoothName: String = "",
    val schoolName: String = "",
    val houseName: String = "",
    val dormitoryNo: String = "",
    val bleMac: String = "",
    val arrearsTodoEnabled: Boolean = false,
    val arrearsThresholdYuan: Int = DEFAULT_ARREARS_THRESHOLD_YUAN,
    val showerSummaryNotifyEnabled: Boolean = true,
) {
    val dormLabel: String
        get() = listOf(houseName, dormitoryNo).filter { it.isNotBlank() }.joinToString(" ")

    val hasMeter: Boolean
        get() = machineSn.isNotBlank() || bluetoothName.isNotBlank()

    companion object {
        const val DEFAULT_ARREARS_THRESHOLD_YUAN = 5
    }
}

/** 消费账单单条记录。 */
data class HotWaterOrder(
    val orderId: String,
    val usePrice: String,
    val status: Int,
    val createTime: String,
    val payTime: String,
) {
    val isPaid: Boolean get() = status == 1
}

/** 分页账单列表结果。 */
data class HotWaterOrderPage(
    val orders: List<HotWaterOrder>,
    val totalAmount: Double? = null,
    val hasMore: Boolean = false,
)

enum class HotWaterPhase {
    UNBOUND,
    IDLE,
    WORKING,
    VALVE_OPEN,
    ERROR,
}

enum class HotWaterErrorCode {
    NONE,
    NOT_BOUND,
    NO_METER_INFO,
    NO_BLE_PERMISSION,
    BLUETOOTH_DISABLED,
    DEVICE_NOT_FOUND,
    DEVICE_DISABLED,
    AUTH_FAILED,
    NETWORK_ERROR,
    BLE_ERROR,
    BUSY,
    UNSUPPORTED,
}

/** UI 与小组件统一订阅的不可变状态快照。 */
data class HotWaterState(
    val isBound: Boolean = false,
    val userIdMasked: String = "",
    val config: HotWaterConfig = HotWaterConfig(),
    val phase: HotWaterPhase = HotWaterPhase.UNBOUND,
    val errorCode: HotWaterErrorCode = HotWaterErrorCode.NONE,
    val errorDetail: String = "",
    val isArrearage: Boolean = false,
    val arrearsYuan: Double = 0.0,
    val canUseAmount: Int = 0,
    val lastCheckAt: Long = 0L,
    val lastActionAt: Long = 0L,
    val busy: Boolean = false,
)

data class HotWaterOpResult(
    val ok: Boolean,
    val phase: HotWaterPhase,
    val errorCode: HotWaterErrorCode = HotWaterErrorCode.NONE,
    val detail: String = "",
)

@Serializable
data class HotWaterShowerRecord(
    val consumeId: Long,
    val startTime: String,
    val endTime: String,
    val durationSec: Long,
    val useAmount: Int,
    val costYuan: Double,
    val liters: Double,
)

/** 账单单条记录的真实洗浴时长（仅本地有开/关水记录时非空）与预估出水量。 */
data class HotWaterOrderMetrics(
    val durationSec: Long?,
    val liters: Double,
)
