import Foundation
import GokeCoreModel

// MARK: - Campus Adapter Error Types

/// Shared localized message when open-source builds invoke unavailable private campus adapters.
public enum CampusAdapterErrorMessages {
    public static let unsupported = "开源构建未包含校园私有协议适配器"
}

/// Categorized errors for academic portal (`Jxfw` / `Jwcwx` / notices) adapters.
public enum JxfwErrorKind: String, Sendable, Hashable, Codable {
    case network
    case credentials
    case captcha
    case sms
    case session
    case protocolError
    case unsupported
    case other
}

public struct JxfwError: Error, Sendable, Hashable, LocalizedError {
    public let message: String
    public let kind: JxfwErrorKind

    public init(_ message: String, kind: JxfwErrorKind = .other) {
        self.message = message
        self.kind = kind
    }

    public var errorDescription: String? { message }
}

// MARK: - Academic Portal (`Jxfw`) Adapter Contract

/// Academic portal client contract (timetable, exams, grades, and course selection).
public protocol JxfwAdapterClient: Sendable {
    var isSupported: Bool { get }
    var portalHost: String { get }
    var isAuthenticated: Bool { get async }

    func login(username: String, password: String) async throws
    func resetSession() async

    func fetchAllLessons(term: String) async throws -> [JxfwLesson]
    func fetchWeek(term: String, week: Int) async throws -> (instances: [JxfwInstance], days: [JxfwDay])
    func fetchExams(term: String) async throws -> [JxfwExam]
    func fetchGrades(termCode: String) async throws -> [JxfwGrade]

    func fetchXkWindowStatus() async throws -> XkWindowStatus
    func fetchXkOpenPlans(term: String) async throws -> [XkOpenPlan]
    func fetchXkAvailableCourses(
        page: Int,
        rows: Int,
        searchKey: String,
        searchValue: String
    ) async throws -> (total: Int, items: [XkCourseItem])
    func fetchXkEnrolledCourses(page: Int, rows: Int) async throws -> (total: Int, items: [XkCourseItem])
    func fetchXkCreditWallet() async throws -> XkCreditWallet?
    func fetchXkBatchSchedule(kcrwdms: [String]) async throws -> [XkSessionSlot]
    func fetchXkStudentSchedule(term: String) async throws -> [XkOccupiedSlot]
    func fetchXkSyllabus(kcptdm: String) async throws -> (zh: String, en: String)
    func fetchXkTeachingCalendar(kcrwdm: String) async throws -> [XkSessionSlot]
    func fetchXkTaskMetadata(term: String, courseName: String) async throws -> [String: XkExtraMeta]
    func xkEnroll(kcrwdm: String, kcmc: String) async throws -> XkActionResult
    func xkDrop(kcrwdm: String, kcmc: String) async throws -> XkActionResult
}

public extension JxfwAdapterClient {
    func fetchGrades(termCode: String = "") async throws -> [JxfwGrade] {
        try await fetchGrades(termCode: termCode)
    }

    func fetchXkAvailableCourses(
        page: Int = 1,
        rows: Int = 100,
        searchKey: String = "",
        searchValue: String = ""
    ) async throws -> (total: Int, items: [XkCourseItem]) {
        try await fetchXkAvailableCourses(
            page: page,
            rows: rows,
            searchKey: searchKey,
            searchValue: searchValue
        )
    }

    func fetchXkEnrolledCourses(page: Int = 1, rows: Int = 100) async throws -> (total: Int, items: [XkCourseItem]) {
        try await fetchXkEnrolledCourses(page: page, rows: rows)
    }

    func xkDrop(kcrwdm: String, kcmc: String = "") async throws -> XkActionResult {
        try await xkDrop(kcrwdm: kcrwdm, kcmc: kcmc)
    }
}

// MARK: - Academic Notices Adapter Contract

/// Dual-source academic notices client contract (public bulletin + authenticated inbox).
public protocol JxfwNoticesAdapterClient: Sendable {
    var isSupported: Bool { get }

    func login(username: String, password: String) async throws
    func fetchPublicNotices(etag: String?, lastModified: String?) async throws -> PublicNoticesFetch
    func fetchInternalNotices() async throws -> [JxfwNotice]
}

public extension JxfwNoticesAdapterClient {
    func fetchPublicNotices(
        etag: String? = nil,
        lastModified: String? = nil
    ) async throws -> PublicNoticesFetch {
        try await fetchPublicNotices(etag: etag, lastModified: lastModified)
    }
}

// MARK: - Empty Classroom (`Jwcwx`) Adapter Contract

/// Campus classroom occupancy client contract.
public protocol JwcwxAdapterClient: Sendable {
    var isSupported: Bool { get }
    var isAuthenticated: Bool { get async }

    func login(username: String, password: String) async throws
    func resetSession() async

    func fetchBuildings() async throws -> [ClassroomBuilding]
    func fetchRooms(buildingCode: String) async throws -> [ClassroomRoom]
    func fetchOccupancy(buildingCode: String, date: LocalDate) async throws -> [ClassroomOccupancy]
    func fetchWeekOccupancy(buildingCode: String, weekStart: LocalDate) async throws -> [LocalDate: [ClassroomOccupancy]]
}

// MARK: - Physical Fitness (`Tzcs`) Adapter Contract

/// Physical fitness test portal client contract.
public protocol TzcsAdapterClient: Sendable {
    var isSupported: Bool { get }
    var isAuthenticated: Bool { get async }

    func fetchCaptchaChallenge() async throws -> Data
    func login(username: String, password: String, vercode: String) async throws
    func fetchAllReports() async throws -> [TzcsYearReport]
    func exportCookies(nowMs: Int64) async -> String
    @discardableResult
    func importCookies(_ raw: String, nowMs: Int64) async -> Bool
    func resetSession() async
}

public extension TzcsAdapterClient {
    func exportCookies(nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) async -> String {
        await exportCookies(nowMs: nowMs)
    }

    @discardableResult
    func importCookies(_ raw: String, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) async -> Bool {
        await importCookies(raw, nowMs: nowMs)
    }
}

// MARK: - Hot Water Adapter Backend Contract

/// Authenticated hot water account credentials returned after login.
public struct HotWaterAccountCredentials: Sendable, Hashable, Codable {
    public var userId: String
    public var phone: String

    public init(userId: String, phone: String) {
        self.userId = userId
        self.phone = phone
    }
}

/// Aggregated account, meter, and balance status returned by the hot water backend.
public struct HotWaterStatusSnapshot: Sendable, Hashable, Codable {
    public var machineSn: String
    public var bluetoothName: String
    public var schoolName: String
    public var houseName: String
    public var dormitoryNo: String
    public var isArrearage: Bool
    public var canUseAmount: Int

    public init(
        machineSn: String = "",
        bluetoothName: String = "",
        schoolName: String = "",
        houseName: String = "",
        dormitoryNo: String = "",
        isArrearage: Bool = false,
        canUseAmount: Int = 0
    ) {
        self.machineSn = machineSn
        self.bluetoothName = bluetoothName
        self.schoolName = schoolName
        self.houseName = houseName
        self.dormitoryNo = dormitoryNo
        self.isArrearage = isArrearage
        self.canUseAmount = canUseAmount
    }
}

/// Protocol-neutral summary of a completed hot water valve session.
public struct HotWaterConsumeSummary: Sendable, Hashable, Codable {
    public var consumeId: Int64
    public var startTime: String
    public var endTime: String
    public var useAmount: Int

    public init(
        consumeId: Int64,
        startTime: String,
        endTime: String,
        useAmount: Int
    ) {
        self.consumeId = consumeId
        self.startTime = startTime
        self.endTime = endTime
        self.useAmount = useAmount
    }
}

/// Combined valve toggle outcome plus optional shower consume summary.
public struct HotWaterToggleResult: Sendable, Hashable, Codable {
    public var opResult: HotWaterOpResult
    public var consumeSummary: HotWaterConsumeSummary?

    public init(
        opResult: HotWaterOpResult,
        consumeSummary: HotWaterConsumeSummary? = nil
    ) {
        self.opResult = opResult
        self.consumeSummary = consumeSummary
    }
}

/// Protocol-neutral hot water HTTPS + BLE backend contract consumed by `HotWaterClient`.
public protocol HotWaterAdapterBackend: Sendable {
    var isSupported: Bool { get }

    func currentBleAuthorizationStatus() -> HotWaterBleAuthStatus
    func sendSmsCode(phone: String) async -> HotWaterApiResult<Void>
    func loginWithPassword(
        phone: String,
        password: String,
        uuid: String
    ) async -> HotWaterApiResult<HotWaterAccountCredentials>
    func loginWithSms(
        phone: String,
        code: String,
        uuid: String
    ) async -> HotWaterApiResult<HotWaterAccountCredentials>
    func fetchAccountStatus(
        userId: String,
        uuid: String,
        currentConfig: HotWaterConfig
    ) async -> HotWaterApiResult<HotWaterStatusSnapshot>
    func fetchOrders(
        userId: String,
        all: Bool,
        pageIndex: Int
    ) async -> HotWaterApiResult<HotWaterOrderPage>
    func queryValveOpen(
        config: HotWaterConfig,
        onPeripheralUUIDResolved: @escaping @Sendable (String) -> Void
    ) async -> Bool?
    func toggleValve(
        userId: String,
        config: HotWaterConfig,
        onPeripheralUUIDResolved: @escaping @Sendable (String) -> Void
    ) async -> HotWaterToggleResult
}

public extension HotWaterAdapterBackend {
    func fetchOrders(
        userId: String,
        all: Bool,
        pageIndex: Int = 1
    ) async -> HotWaterApiResult<HotWaterOrderPage> {
        await fetchOrders(userId: userId, all: all, pageIndex: pageIndex)
    }
}

// MARK: - Campus Adapter Factory Contract

/// Root factory contract: implemented by private `GokeAdapters` when present,
/// or falls back to `UnavailableCampusAdapterFactory` in open-source builds.
public protocol CampusAdapterFactory: Sendable {
    var isSupported: Bool { get }

    func makeJxfwClient() -> any JxfwAdapterClient
    func makeJxfwNoticesClient() -> any JxfwNoticesAdapterClient
    func makeJwcwxClient() -> any JwcwxAdapterClient
    func makeTzcsClient() -> any TzcsAdapterClient
    func makeHotWaterBackend() -> any HotWaterAdapterBackend
    func dropRelayDefaults() -> CampusDropRelayDefaults
}

public extension CampusAdapterFactory {
    func dropRelayDefaults() -> CampusDropRelayDefaults {
        CampusDropRelayDefaults()
    }
}
