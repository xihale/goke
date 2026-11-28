import Foundation
import GokeCoreModel

// MARK: - Open-Source Fallback Factory & Stubs (mirrors `UnavailableCampusAdapters.kt`)

/// Open-source fallback factory returned when private campus protocol adapters are not linked.
public struct UnavailableCampusAdapterFactory: CampusAdapterFactory, Sendable {
    public let isSupported: Bool = false

    public init() {}

    public func makeJxfwClient() -> any JxfwAdapterClient {
        UnavailableJxfwAdapterClient()
    }

    public func makeJxfwNoticesClient() -> any JxfwNoticesAdapterClient {
        UnavailableJxfwNoticesAdapterClient()
    }

    public func makeJwcwxClient() -> any JwcwxAdapterClient {
        UnavailableJwcwxAdapterClient()
    }

    public func makeTzcsClient() -> any TzcsAdapterClient {
        UnavailableTzcsAdapterClient()
    }

    public func makeHotWaterBackend() -> any HotWaterAdapterBackend {
        UnavailableHotWaterAdapterBackend()
    }

    public func dropRelayDefaults() -> CampusDropRelayDefaults {
        CampusDropRelayDefaults()
    }
}

public struct UnavailableJxfwAdapterClient: JxfwAdapterClient, Sendable {
    public let isSupported: Bool = false
    public let portalHost: String = ""

    public var isAuthenticated: Bool {
        get async { false }
    }

    public init() {}

    private func unsupported() -> JxfwError {
        JxfwError(CampusAdapterErrorMessages.unsupported, kind: .unsupported)
    }

    public func login(username: String, password: String) async throws {
        throw unsupported()
    }

    public func resetSession() async {}

    public func fetchAllLessons(term: String) async throws -> [JxfwLesson] {
        throw unsupported()
    }

    public func fetchWeek(term: String, week: Int) async throws -> (instances: [JxfwInstance], days: [JxfwDay]) {
        throw unsupported()
    }

    public func fetchExams(term: String) async throws -> [JxfwExam] {
        throw unsupported()
    }

    public func fetchGrades(termCode: String) async throws -> [JxfwGrade] {
        throw unsupported()
    }

    public func fetchXkWindowStatus() async throws -> XkWindowStatus {
        throw unsupported()
    }

    public func fetchXkOpenPlans(term: String) async throws -> [XkOpenPlan] {
        throw unsupported()
    }

    public func fetchXkAvailableCourses(
        page: Int,
        rows: Int,
        searchKey: String,
        searchValue: String
    ) async throws -> (total: Int, items: [XkCourseItem]) {
        throw unsupported()
    }

    public func fetchXkEnrolledCourses(page: Int, rows: Int) async throws -> (total: Int, items: [XkCourseItem]) {
        throw unsupported()
    }

    public func fetchXkCreditWallet() async throws -> XkCreditWallet? {
        throw unsupported()
    }

    public func fetchXkBatchSchedule(kcrwdms: [String]) async throws -> [XkSessionSlot] {
        throw unsupported()
    }

    public func fetchXkStudentSchedule(term: String) async throws -> [XkOccupiedSlot] {
        throw unsupported()
    }

    public func fetchXkSyllabus(kcptdm: String) async throws -> (zh: String, en: String) {
        throw unsupported()
    }

    public func fetchXkTeachingCalendar(kcrwdm: String) async throws -> [XkSessionSlot] {
        throw unsupported()
    }

    public func fetchXkTaskMetadata(term: String, courseName: String) async throws -> [String: XkExtraMeta] {
        throw unsupported()
    }

    public func xkEnroll(kcrwdm: String, kcmc: String) async throws -> XkActionResult {
        .failure(reason: CampusAdapterErrorMessages.unsupported, kind: .OTHER)
    }

    public func xkDrop(kcrwdm: String, kcmc: String) async throws -> XkActionResult {
        .failure(reason: CampusAdapterErrorMessages.unsupported, kind: .OTHER)
    }
}

public struct UnavailableJxfwNoticesAdapterClient: JxfwNoticesAdapterClient, Sendable {
    public let isSupported: Bool = false

    public init() {}

    private func unsupported() -> JxfwError {
        JxfwError(CampusAdapterErrorMessages.unsupported, kind: .unsupported)
    }

    public func login(username: String, password: String) async throws {
        throw unsupported()
    }

    public func fetchPublicNotices(etag: String?, lastModified: String?) async throws -> PublicNoticesFetch {
        throw unsupported()
    }

    public func fetchInternalNotices() async throws -> [JxfwNotice] {
        throw unsupported()
    }
}

public struct UnavailableJwcwxAdapterClient: JwcwxAdapterClient, Sendable {
    public let isSupported: Bool = false

    public var isAuthenticated: Bool {
        get async { false }
    }

    public init() {}

    private func unsupported() -> JxfwError {
        JxfwError(CampusAdapterErrorMessages.unsupported, kind: .unsupported)
    }

    public func login(username: String, password: String) async throws {
        throw unsupported()
    }

    public func resetSession() async {}

    public func fetchBuildings() async throws -> [ClassroomBuilding] {
        throw unsupported()
    }

    public func fetchRooms(buildingCode: String) async throws -> [ClassroomRoom] {
        throw unsupported()
    }

    public func fetchOccupancy(buildingCode: String, date: LocalDate) async throws -> [ClassroomOccupancy] {
        throw unsupported()
    }

    public func fetchWeekOccupancy(buildingCode: String, weekStart: LocalDate) async throws -> [LocalDate: [ClassroomOccupancy]] {
        throw unsupported()
    }
}

public struct UnavailableTzcsAdapterClient: TzcsAdapterClient, Sendable {
    public let isSupported: Bool = false

    public var isAuthenticated: Bool {
        get async { false }
    }

    public init() {}

    private func unsupported() -> TzcsError {
        TzcsError(CampusAdapterErrorMessages.unsupported, kind: .unsupported)
    }

    public func fetchCaptchaChallenge() async throws -> Data {
        throw unsupported()
    }

    public func login(username: String, password: String, vercode: String) async throws {
        throw unsupported()
    }

    public func fetchAllReports() async throws -> [TzcsYearReport] {
        throw unsupported()
    }

    public func exportCookies(nowMs: Int64) async -> String {
        "[]"
    }

    @discardableResult
    public func importCookies(_ raw: String, nowMs: Int64) async -> Bool {
        false
    }

    public func resetSession() async {}
}

public struct UnavailableHotWaterAdapterBackend: HotWaterAdapterBackend, Sendable {
    public let isSupported: Bool = false

    public init() {}

    public func currentBleAuthorizationStatus() -> HotWaterBleAuthStatus {
        .notDetermined
    }

    public func sendSmsCode(phone: String) async -> HotWaterApiResult<Void> {
        .err(code: .unsupported, detail: CampusAdapterErrorMessages.unsupported)
    }

    public func loginWithPassword(
        phone: String,
        password: String,
        uuid: String
    ) async -> HotWaterApiResult<HotWaterAccountCredentials> {
        .err(code: .unsupported, detail: CampusAdapterErrorMessages.unsupported)
    }

    public func loginWithSms(
        phone: String,
        code: String,
        uuid: String
    ) async -> HotWaterApiResult<HotWaterAccountCredentials> {
        .err(code: .unsupported, detail: CampusAdapterErrorMessages.unsupported)
    }

    public func fetchAccountStatus(
        userId: String,
        uuid: String,
        currentConfig: HotWaterConfig
    ) async -> HotWaterApiResult<HotWaterStatusSnapshot> {
        .err(code: .unsupported, detail: CampusAdapterErrorMessages.unsupported)
    }

    public func fetchOrders(
        userId: String,
        all: Bool,
        pageIndex: Int
    ) async -> HotWaterApiResult<HotWaterOrderPage> {
        .err(code: .unsupported, detail: CampusAdapterErrorMessages.unsupported)
    }

    public func queryValveOpen(
        config: HotWaterConfig,
        onPeripheralUUIDResolved: @escaping @Sendable (String) -> Void
    ) async -> Bool? {
        nil
    }

    public func toggleValve(
        userId: String,
        config: HotWaterConfig,
        onPeripheralUUIDResolved: @escaping @Sendable (String) -> Void
    ) async -> HotWaterToggleResult {
        HotWaterToggleResult(
            opResult: HotWaterOpResult(
                ok: false,
                phase: .error,
                errorCode: .unsupported,
                detail: CampusAdapterErrorMessages.unsupported
            ),
            consumeSummary: nil
        )
    }
}
