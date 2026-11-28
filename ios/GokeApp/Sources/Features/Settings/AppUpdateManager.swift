import Foundation
import GokeCore
import Observation
#if canImport(UIKit)
import UIKit
#endif

/// Manages automatic (24h cooldown) and manual iOS update checks against GitHub Releases,
/// persisting ignored versions and presenting the sideload-friendly update sheet.
@MainActor
@Observable
final class AppUpdateManager {
    enum CheckStatus: Equatable, Sendable {
        case idle
        case checking
        case upToDate
        case available(AppUpdateInfo)
        case failed(String)
    }

    static let shared = AppUpdateManager()
    static let autoCheckCooldown: TimeInterval = 24 * 60 * 60

    private static let lastCheckAtKey = "app.goke.ios.update.lastCheckAt.v1"
    private static let ignoredVersionKey = "app.goke.ios.update.ignoredVersion.v1"

    private(set) var status: CheckStatus = .idle
    private(set) var availableUpdate: AppUpdateInfo?
    var isSheetPresented: Bool = false

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let currentVersionProvider: @Sendable () -> String
    @ObservationIgnored private let fetchReleasesData: @Sendable (String) async throws -> Data

    var isChecking: Bool {
        status == .checking
    }

    var currentVersion: String {
        currentVersionProvider()
    }

    var ignoredVersion: String {
        get { defaults.string(forKey: Self.ignoredVersionKey) ?? "" }
        set {
            if newValue.isEmpty {
                defaults.removeObject(forKey: Self.ignoredVersionKey)
            } else {
                defaults.set(newValue, forKey: Self.ignoredVersionKey)
            }
        }
    }

    var hasUnreadUpdate: Bool {
        guard let update = availableUpdate else { return false }
        return update.version != ignoredVersion
    }

    var isSideStoreInstalled: Bool {
        #if canImport(UIKit)
        guard let url = URL(string: "sidestore://") else { return false }
        return UIApplication.shared.canOpenURL(url)
        #else
        return false
        #endif
    }

    var isAltStoreInstalled: Bool {
        #if canImport(UIKit)
        guard let url = URL(string: "altstore://") else { return false }
        return UIApplication.shared.canOpenURL(url)
        #else
        return false
        #endif
    }

    var sourceJSONURLString: String {
        UpdateChecker.defaultSourceJSONURL
    }

    var sideStoreAddSourceURL: URL? {
        UpdateChecker.sideStoreAddSourceURL(sourceURL: sourceJSONURLString)
    }

    var altStoreAddSourceURL: URL? {
        UpdateChecker.altStoreAddSourceURL(sourceURL: sourceJSONURLString)
    }

    func copySourceURLToPasteboard() {
        #if canImport(UIKit)
        UIPasteboard.general.string = sourceJSONURLString
        #endif
    }

    init(
        defaults: UserDefaults = .standard,
        currentVersionProvider: @escaping @Sendable () -> String = { GokeAppVersion.displayVersion },
        fetchReleasesData: @escaping @Sendable (String) async throws -> Data = AppUpdateManager.defaultFetchReleasesData
    ) {
        self.defaults = defaults
        self.currentVersionProvider = currentVersionProvider
        self.fetchReleasesData = fetchReleasesData
    }

    /// Runs a silent background check on launch when at least 24 hours have passed since the last check.
    func checkOnLaunchIfNeeded(now: Date = Date()) async {
        clearStaleIgnoredVersion()
        guard !isChecking else { return }

        let lastCheckAt = defaults.double(forKey: Self.lastCheckAtKey)
        if lastCheckAt > 0, now.timeIntervalSince1970 - lastCheckAt < Self.autoCheckCooldown {
            return
        }

        await performCheck(now: now, isManual: false)
    }

    /// Triggers an immediate user-initiated check and opens the update sheet if a newer version is available.
    @discardableResult
    func checkManually(now: Date = Date()) async -> CheckStatus {
        clearStaleIgnoredVersion()
        guard !isChecking else { return status }
        await performCheck(now: now, isManual: true)
        return status
    }

    func presentUpdateSheet() {
        guard availableUpdate != nil else { return }
        isSheetPresented = true
    }

    func ignoreCurrentUpdate() {
        if let version = availableUpdate?.version, !version.isEmpty {
            ignoredVersion = version
        }
        isSheetPresented = false
    }

    func dismissSheet() {
        isSheetPresented = false
    }

    private func clearStaleIgnoredVersion() {
        let ignored = ignoredVersion
        if !ignored.isEmpty, !SemVer.isNewer(ignored, than: currentVersion) {
            ignoredVersion = ""
        }
    }

    private func performCheck(now: Date, isManual: Bool) async {
        let previousUpdate = availableUpdate
        status = .checking

        let version = currentVersion
        do {
            let data = try await fetchReleasesData(version)
            let update = try UpdateChecker.selectLatestIOSRelease(
                from: data,
                currentVersion: version
            )
            defaults.set(now.timeIntervalSince1970, forKey: Self.lastCheckAtKey)

            if let update {
                availableUpdate = update
                status = .available(update)
                if isManual || update.version != ignoredVersion {
                    isSheetPresented = true
                }
            } else {
                availableUpdate = nil
                status = .upToDate
            }
        } catch {
            if isManual {
                status = .failed("检查更新失败，请稍后重试")
            } else {
                availableUpdate = previousUpdate
                status = previousUpdate.map(CheckStatus.available) ?? .idle
            }
        }
    }

    private static func defaultFetchReleasesData(currentVersion: String) async throws -> Data {
        guard let url = URL(string: UpdateChecker.githubReleasesAPIURLString) else {
            throw URLError(.badURL)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 15
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("goke-ios/\(currentVersion)", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }
}
