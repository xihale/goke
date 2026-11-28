#if DEBUG && canImport(UIKit)
import Darwin
import Foundation
import GokeCore
import GokeShared
import Network
import UIKit

/// `#if DEBUG`-only loopback HTTP/1.1 bridge (`127.0.0.1:18923`) for zero-popup route
/// navigation, live theme/performance-mode toggling, telemetry, real `UIWindow` PNG capture,
/// and runtime campus login/sync triggers (credentials are never hardcoded).
@MainActor
public final class DebugBridgeServer {
    public static let shared = DebugBridgeServer()
    public static let defaultPort: UInt16 = 18923

    private var listener: NWListener?
    private weak var router: AppRouter?
    private weak var container: AppContainer?
    private let queue = DispatchQueue(label: "app.goke.ios.debug-bridge", qos: .userInitiated)

    private init() {}

    /// Starts the loopback-only HTTP listener on `127.0.0.1:<port>`. Safe to call repeatedly.
    public func start(router: AppRouter, container: AppContainer? = nil, port: UInt16 = defaultPort) {
        self.router = router
        if let container {
            self.container = container
        }
        guard listener == nil else { return }

        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            return
        }

        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        parameters.includePeerToPeer = false
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: nwPort)

        do {
            let newListener = try NWListener(using: parameters)
            newListener.newConnectionHandler = { [queue] connection in
                Self.handleConnection(connection, on: queue)
            }
            newListener.stateUpdateHandler = { [weak self] state in
                if case .failed = state {
                    Task { @MainActor in
                        self?.listener?.cancel()
                        self?.listener = nil
                    }
                }
            }
            newListener.start(queue: queue)
            self.listener = newListener
        } catch {
            // Debug bridge is best-effort and must never crash the app.
            self.listener = nil
        }
    }

    public func stop() {
        listener?.cancel()
        listener = nil
    }

    // MARK: - Connection Handling

    private nonisolated static func handleConnection(_ connection: NWConnection, on queue: DispatchQueue) {
        connection.start(queue: queue)
        receiveRequest(on: connection, buffer: Data())
    }

    private nonisolated static func receiveRequest(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { data, _, isComplete, error in
            if error != nil {
                connection.cancel()
                return
            }

            var accumulated = buffer
            if let data, !data.isEmpty {
                accumulated.append(data)
            }

            let headerDelimiter = Data("\r\n\r\n".utf8)
            var isRequestComplete = isComplete || accumulated.count >= 65_536
            if !isRequestComplete, let range = accumulated.range(of: headerDelimiter) {
                let headerText = String(decoding: accumulated[..<range.lowerBound], as: UTF8.self)
                let contentLength = Self.parseContentLength(from: headerText)
                if accumulated.count >= range.upperBound + contentLength {
                    isRequestComplete = true
                }
            }

            if isRequestComplete {
                let payload = accumulated
                Task { @MainActor in
                    let response = await DebugBridgeServer.shared.routeHTTPRequest(payload)
                    Self.send(response: response, on: connection)
                }
            } else {
                receiveRequest(on: connection, buffer: accumulated)
            }
        }
    }

    private nonisolated static func parseContentLength(from headerText: String) -> Int {
        for line in headerText.split(separator: "\r\n") {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = line[..<colon].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if key == "content-length" {
                let val = line[line.index(after: colon)...].trimmingCharacters(in: .whitespacesAndNewlines)
                return max(0, Int(val) ?? 0)
            }
        }
        return 0
    }

    private nonisolated static func send(response: HTTPResponse, on connection: NWConnection) {
        let payload = response.serialized()
        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    // MARK: - HTTP Routing

    private func routeHTTPRequest(_ rawData: Data) async -> HTTPResponse {
        let headerDelimiter = Data("\r\n\r\n".utf8)
        let altDelimiter = Data("\n\n".utf8)
        let headerData: Data
        let bodyData: Data
        if let range = rawData.range(of: headerDelimiter) {
            headerData = rawData[..<range.lowerBound]
            bodyData = rawData[range.upperBound...]
        } else if let range = rawData.range(of: altDelimiter) {
            headerData = rawData[..<range.lowerBound]
            bodyData = rawData[range.upperBound...]
        } else {
            headerData = rawData
            bodyData = Data()
        }

        let headerText = String(decoding: headerData, as: UTF8.self)
        guard let firstLine = headerText.split(separator: "\r\n", maxSplits: 1).first
            ?? headerText.split(separator: "\n", maxSplits: 1).first else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "empty request"])
        }

        let parts = firstLine.split(separator: " ", omittingEmptySubsequences: true)
        guard parts.count >= 2 else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "malformed request line"])
        }

        let method = String(parts[0]).uppercased()
        let target = String(parts[1])

        guard let components = URLComponents(string: "http://127.0.0.1\(target)") else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "invalid target"])
        }

        let path = components.path
        let queryItems = components.queryItems ?? []

        func queryValue(_ name: String) -> String? {
            queryItems.first(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame })?.value
        }

        if method == "POST" {
            switch path {
            case "/debug/login-jxfw":
                return await handleLoginJxfw(bodyData: bodyData)
            case "/debug/login-tzcs":
                return await handleLoginTzcs(bodyData: bodyData)
            case "/debug/login-hotwater":
                return await handleLoginHotWater(bodyData: bodyData)
            default:
                return .json(status: 404, reason: "Not Found", object: ["ok": false, "error": "unknown POST endpoint", "path": path])
            }
        }

        guard method == "GET" else {
            return .json(status: 405, reason: "Method Not Allowed", object: ["ok": false, "error": "GET or POST only"])
        }

        switch path {
        case "/ping":
            return handlePing()
        case "/route":
            return handleRoute(urlString: queryValue("url"))
        case "/theme":
            return handleTheme(mode: queryValue("mode"))
        case "/perf":
            return handlePerf(mode: queryValue("mode"))
        case "/screenshot":
            return handleScreenshot()
        default:
            return .json(status: 404, reason: "Not Found", object: ["ok": false, "error": "unknown endpoint", "path": path])
        }
    }

    // MARK: - Endpoints

    private func handlePing() -> HTTPResponse {
        let mode = GokePerformanceMode.load()
        let policy = GokePerformancePolicy.current(
            mode: mode,
            reduceMotion: UIAccessibility.isReduceMotionEnabled,
            reduceTransparency: UIAccessibility.isReduceTransparencyEnabled
        )
        let memoryMB = (Self.currentMemoryFootprintMB() * 10.0).rounded() / 10.0
        let appState: String
        switch UIApplication.shared.applicationState {
        case .active: appState = "active"
        case .inactive: appState = "inactive"
        case .background: appState = "background"
        @unknown default: appState = "unknown"
        }
        let keyWindow = primaryWindow()
        return .json(object: [
            "ok": true,
            "performanceMode": mode.rawValue,
            "isDegraded": policy.isDegraded,
            "tab": router?.selectedTab.rawValue ?? "unknown",
            "memoryMB": memoryMB,
            "appState": appState,
            "windowCount": activeWindows().count,
            "keyWindow": keyWindow.map { "\(type(of: $0)) \($0.bounds.width)x\($0.bounds.height) hidden=\($0.isHidden) level=\($0.windowLevel.rawValue)" } ?? "none",
        ])
    }

    private func handleRoute(urlString: String?) -> HTTPResponse {
        guard let urlString, !urlString.isEmpty, let url = URL(string: urlString) else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "missing or invalid url"])
        }
        guard let router else {
            return .json(status: 503, reason: "Service Unavailable", object: ["ok": false, "error": "router not attached"])
        }

        // Dismiss any active sheet before deep-linking so transitions away from modal sheets are clean.
        router.dismissSheet()
        let handled = router.handleDeepLink(url)
        return .json(object: [
            "ok": handled,
            "url": url.absoluteString,
            "tab": router.selectedTab.rawValue,
        ])
    }

    private func handleTheme(mode: String?) -> HTTPResponse {
        let normalized = (mode ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let style: UIUserInterfaceStyle
        switch normalized {
        case "light":
            style = .light
        case "dark":
            style = .dark
        case "system", "unspecified":
            style = .unspecified
        default:
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "mode must be light|dark|system"])
        }

        let windows = activeWindows()
        for window in windows {
            window.overrideUserInterfaceStyle = style
        }
        return .json(object: [
            "ok": true,
            "mode": normalized,
            "windowCount": windows.count,
        ])
    }

    private func handlePerf(mode: String?) -> HTTPResponse {
        let normalized = (mode ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let parsed: GokePerformanceMode? = GokePerformanceMode(rawValue: normalized)
            ?? GokePerformanceMode.allCases.first(where: { $0.rawValue.caseInsensitiveCompare(normalized) == .orderedSame })

        guard let parsed else {
            return .json(
                status: 400,
                reason: "Bad Request",
                object: ["ok": false, "error": "mode must be auto|standard|powerSaver"]
            )
        }

        parsed.save()
        let policy = GokePerformancePolicy.current(
            mode: parsed,
            reduceMotion: UIAccessibility.isReduceMotionEnabled,
            reduceTransparency: UIAccessibility.isReduceTransparencyEnabled
        )
        return .json(object: [
            "ok": true,
            "performanceMode": parsed.rawValue,
            "isDegraded": policy.isDegraded,
        ])
    }

    private func handleScreenshot() -> HTTPResponse {
        guard let window = primaryWindow() else {
            return .json(status: 503, reason: "Service Unavailable", object: ["ok": false, "error": "no active window"])
        }

        window.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat()
        format.scale = window.screen.scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(bounds: window.bounds, format: format)
        var usedDrawHierarchy = false
        let image = renderer.image { context in
            usedDrawHierarchy = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            if !usedDrawHierarchy {
                window.layer.render(in: context.cgContext)
            }
        }

        guard let pngData = image.pngData() else {
            return .json(status: 500, reason: "Internal Server Error", object: ["ok": false, "error": "png encoding failed"])
        }

        return HTTPResponse(
            status: 200,
            reason: "OK",
            contentType: "image/png",
            body: pngData,
            extraHeaders: ["X-Capture-Method": usedDrawHierarchy ? "drawHierarchy" : "layerRender"]
        )
    }

    // MARK: - Campus Login & Sync Debug Triggers

    private func decodeJSONDictionary(_ data: Data) -> [String: Any]? {
        guard !data.isEmpty,
              let obj = try? JSONSerialization.jsonObject(with: data, options: []),
              let dict = obj as? [String: Any] else {
            return nil
        }
        return dict
    }

    private func handleLoginJxfw(bodyData: Data) async -> HTTPResponse {
        guard let container else {
            return .json(status: 503, reason: "Service Unavailable", object: ["ok": false, "error": "container not attached"])
        }
        guard let json = decodeJSONDictionary(bodyData) else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "invalid json body"])
        }
        let user = (json["username"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let password = json["password"] as? String ?? ""
        guard !user.isEmpty, !password.isEmpty else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "username and password required"])
        }

        if !container.isBootstrapped {
            await container.bootstrap()
        }
        guard let semester = container.liveSnapshot.currentSemester ?? container.snapshot.semesters.first else {
            return .json(status: 409, reason: "Conflict", object: ["ok": false, "error": "no active semester"])
        }

        let requestedTerm = (json["term"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let term = requestedTerm.isEmpty ? JxfwParse.currentTerm(today: .today()) : requestedTerm

        do {
            try? await container.keychain.set(user, for: .jxfwAccount)
            try? await container.keychain.set(password, for: .jxfwPassword)
            container.appGroup.userDefaults.set(term, forKey: "app.goke.ios.campus.lastJxfwTerm.v1")

            try await container.jxfwClient.login(username: user, password: password)

            let exams = try? await container.jxfwClient.fetchExams(term: term)
            let lessons = try await container.jxfwClient.fetchAllLessons(term: term)
            let week1 = try? await container.jxfwClient.fetchWeek(term: term, week: 1)
            let monday = week1.flatMap { JxfwParse.firstMonday(from: $0.days) }

            let ambiguous = JxfwSyncMapper.ambiguousWeeks(lessons).sorted()
            var weekInstances: [Int: [JxfwInstance]] = [:]
            if let w1 = week1, ambiguous.contains(1) {
                weekInstances[1] = w1.instances
            }
            for w in ambiguous where weekInstances[w] == nil {
                if let fetched = try? await container.jxfwClient.fetchWeek(term: term, week: w) {
                    weekInstances[w] = fetched.instances
                }
            }

            let plan = JxfwSyncMapper.fullImport(
                snapshot: container.snapshot,
                semester: semester,
                term: term,
                lessons: lessons,
                exams: exams,
                week1Monday: monday,
                weekInstances: weekInstances
            )
            let committed = await container.actions.commit(plan.changeSet, label: plan.changeSet.description)

            var gradesCount = 0
            var gradesError = ""
            do {
                let fetchedGrades = try await container.jxfwClient.fetchGrades(termCode: "")
                gradesCount = fetchedGrades.count
                let defaults = container.appGroup.userDefaults
                if let encoded = try? MintJSON.makeEncoder().encode(fetchedGrades) {
                    defaults.set(encoded, forKey: "app.goke.ios.campus.gradesCache.v1")
                }
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                defaults.set(now, forKey: "app.goke.ios.campus.gradesUpdatedAt.v1")
            } catch {
                gradesError = error.localizedDescription
            }

            var result: [String: Any] = [
                "ok": true,
                "term": term,
                "committed": committed,
                "lessonCount": lessons.count,
                "courseCount": container.liveSnapshot.courses.count,
                "examCount": exams?.count ?? 0,
                "gradesCount": gradesCount,
            ]
            if let monday {
                result["week1Monday"] = monday.description
            }
            if !gradesError.isEmpty {
                result["gradesError"] = gradesError
            }
            return .json(object: result)
        } catch {
            var errObj: [String: Any] = [
                "ok": false,
                "term": term,
                "error": error.localizedDescription,
            ]
            if let jxfwErr = error as? JxfwError {
                errObj["errorKind"] = jxfwErr.kind.rawValue
            }
            return .json(status: 200, reason: "OK", object: errObj)
        }
    }

    private func handleLoginTzcs(bodyData: Data) async -> HTTPResponse {
        guard let container else {
            return .json(status: 503, reason: "Service Unavailable", object: ["ok": false, "error": "container not attached"])
        }
        guard let json = decodeJSONDictionary(bodyData) else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "invalid json body"])
        }
        let user = (json["username"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let password = json["password"] as? String ?? ""
        let vercode = (json["vercode"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "username and password required"])
        }

        let cookiesKey: KeychainKey = .custom("campus.tzcs.cookies")

        func saveTzcsReports(_ incoming: [TzcsYearReport]) -> [TzcsYearReport] {
            let defaults = container.appGroup.userDefaults
            var existing: [TzcsYearReport] = []
            if let data = defaults.data(forKey: "app.goke.ios.campus.tzcsCache.v1"),
               let decoded = try? MintJSON.makeDecoder().decode([TzcsYearReport].self, from: data) {
                existing = decoded
            }
            let merged = TzcsParse.mergeReports(existing: existing, incoming: incoming)
            let filtered = TzcsParse.filterApplicableItems(merged)
            if let encoded = try? MintJSON.makeEncoder().encode(filtered) {
                defaults.set(encoded, forKey: "app.goke.ios.campus.tzcsCache.v1")
            }
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            defaults.set(now, forKey: "app.goke.ios.campus.tzcsUpdatedAt.v1")
            return filtered
        }

        // First try existing persisted session cookies if no vercode was provided.
        if vercode.isEmpty,
           let rawCookies = (try? await container.keychain.get(cookiesKey)) ?? nil,
           !rawCookies.isEmpty {
            _ = await container.tzcsClient.importCookies(rawCookies)
            if let fetched = try? await container.tzcsClient.fetchAllReports() {
                let filtered = saveTzcsReports(fetched)
                return .json(object: [
                    "ok": true,
                    "reusedSession": true,
                    "reportCount": filtered.count,
                    "years": filtered.map(\.year),
                ])
            }
        }

        do {
            try await container.tzcsClient.login(username: user, password: password, vercode: vercode)
            try? await container.keychain.set(user, for: .jxfwAccount)
            try? await container.keychain.set(password, for: .tzcsPassword)
            let exportedCookies = await container.tzcsClient.exportCookies()
            try? await container.keychain.set(exportedCookies, for: cookiesKey)

            let fetched = try await container.tzcsClient.fetchAllReports()
            let filtered = saveTzcsReports(fetched)
            return .json(object: [
                "ok": true,
                "reusedSession": false,
                "reportCount": filtered.count,
                "years": filtered.map(\.year),
            ])
        } catch {
            var errObj: [String: Any] = [
                "ok": false,
                "error": error.localizedDescription,
            ]
            if let tzErr = error as? TzcsError {
                errObj["errorKind"] = tzErr.kind.rawValue
                errObj["captchaRequired"] = (tzErr.kind == .captcha)
            }
            return .json(status: 200, reason: "OK", object: errObj)
        }
    }

    private func handleLoginHotWater(bodyData: Data) async -> HTTPResponse {
        guard let json = decodeJSONDictionary(bodyData) else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "invalid json body"])
        }
        let phone = (json["phone"] as? String ?? json["username"] as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let password = json["password"] as? String ?? ""
        guard !phone.isEmpty, !password.isEmpty else {
            return .json(status: 400, reason: "Bad Request", object: ["ok": false, "error": "phone and password required"])
        }

        let client = HotWaterClient.shared
        await client.ensureHydrated()
        let res = await client.loginWithPassword(
            phone: phone,
            password: password,
            repository: container?.repository
        )

        var orderCount = 0
        var ordersError = ""
        if res.ok {
            switch await client.fetchOrders(all: true, pageIndex: 1) {
            case .ok(let page):
                orderCount = page.orders.count
            case .err(let code, let detail):
                ordersError = "\(code.rawValue):\(detail)"
            }
        }

        let st = client.state
        var result: [String: Any] = [
            "ok": res.ok,
            "phase": res.phase.rawValue,
            "errorCode": res.errorCode.rawValue,
            "detail": res.detail,
            "isBound": st.isBound,
            "isArrearage": st.isArrearage,
            "canUseAmount": st.canUseAmount,
            "dormLabel": st.config.dormLabel,
            "machineSn": st.config.machineSn,
            "orderCount": orderCount,
        ]
        if !ordersError.isEmpty {
            result["ordersError"] = ordersError
        }
        return .json(object: result)
    }

    // MARK: - Helpers

    private func activeWindows() -> [UIWindow] {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
    }

    private func primaryWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let orderedScenes = scenes.sorted { lhs, rhs in
            let rank: (UIScene.ActivationState) -> Int = {
                switch $0 {
                case .foregroundActive: 0
                case .foregroundInactive: 1
                case .background: 2
                case .unattached: 3
                @unknown default: 4
                }
            }
            return rank(lhs.activationState) < rank(rhs.activationState)
        }
        let windows = orderedScenes.flatMap(\.windows)
        return windows.first(where: { !$0.isHidden && $0.windowLevel == .normal && $0.isKeyWindow })
            ?? windows.first(where: { !$0.isHidden && $0.windowLevel == .normal })
            ?? windows.first(where: \.isKeyWindow)
            ?? windows.first
    }

    private static func currentMemoryFootprintMB() -> Double {
        var vmInfo = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result: kern_return_t = withUnsafeMutablePointer(to: &vmInfo) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { rebound in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), rebound, &count)
            }
        }
        if result == KERN_SUCCESS {
            return Double(vmInfo.phys_footprint) / (1024.0 * 1024.0)
        }
        return 0.0
    }
}

private struct HTTPResponse: Sendable {
    let status: Int
    let reason: String
    let contentType: String
    let body: Data
    let extraHeaders: [String: String]

    init(
        status: Int,
        reason: String,
        contentType: String,
        body: Data,
        extraHeaders: [String: String] = [:]
    ) {
        self.status = status
        self.reason = reason
        self.contentType = contentType
        self.body = body
        self.extraHeaders = extraHeaders
    }

    static func json(status: Int = 200, reason: String = "OK", object: [String: Any]) -> HTTPResponse {
        let data = (try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]))
            ?? Data("{\"ok\":false}".utf8)
        return HTTPResponse(
            status: status,
            reason: reason,
            contentType: "application/json; charset=utf-8",
            body: data
        )
    }

    func serialized() -> Data {
        var header =
            "HTTP/1.1 \(status) \(reason)\r\n" +
            "Content-Type: \(contentType)\r\n" +
            "Content-Length: \(body.count)\r\n"
        for (key, value) in extraHeaders.sorted(by: { $0.key < $1.key }) {
            header += "\(key): \(value)\r\n"
        }
        header += "Connection: close\r\n\r\n"
        var data = Data(header.utf8)
        data.append(body)
        return data
    }
}
#endif
