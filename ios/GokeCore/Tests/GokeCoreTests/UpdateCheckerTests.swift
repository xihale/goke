import Foundation
import Testing
@testable import GokeCore

@Suite("iOS UpdateChecker & SemVer")
struct UpdateCheckerTests {
    @Test("SemVer parses prefixes, prereleases, and build suffixes")
    func semVerParsingAndOrdering() {
        #expect(SemVer.parse("0.0.1-beta.1") == SemVer(major: 0, minor: 0, patch: 1, preRelease: ["beta", "1"]))
        #expect(SemVer.parse("ios-v0.0.1-beta.10") == SemVer(major: 0, minor: 0, patch: 1, preRelease: ["beta", "10"]))
        #expect(SemVer.parse("v1.2.3-dirty") == SemVer(major: 1, minor: 2, patch: 3))
        #expect(SemVer.parse("0.0.1-beta.9-3-gabcdef1-debug") == SemVer(major: 0, minor: 0, patch: 1, preRelease: ["beta", "9"]))
        #expect(SemVer.parse("invalid-version") == nil)
        #expect(SemVer.parse("") == nil)

        #expect(SemVer.isNewer("0.0.1-beta.10", than: "0.0.1-beta.2"))
        #expect(SemVer.isNewer("0.0.1", than: "0.0.1-beta.10"))
        #expect(SemVer.isNewer("ios-v0.0.2-beta.1", than: "0.0.1"))
        #expect(!SemVer.isNewer("0.0.1-beta.9", than: "0.0.1-beta.9"))
        #expect(!SemVer.isNewer("0.0.1-beta.8", than: "0.0.1-beta.9"))
        #expect(!SemVer.isNewer("0.0.1-beta.9", than: "0.0.1"))
        #expect(!SemVer.isNewer("bad", than: "0.0.1"))
    }

    @Test("Filters ios-v* releases, ignores Android v* tags, drafts, and releases without .ipa")
    func filtersIOSReleasesAndSelectsIPA() throws {
        let json = """
        [
          {
            "tag_name": "v9.9.9",
            "html_url": "https://github.com/xihale/goke/releases/tag/v9.9.9",
            "prerelease": false,
            "draft": false,
            "body": "Android release",
            "assets": [
              { "name": "goke-android.apk", "browser_download_url": "https://github.com/xihale/goke/releases/download/v9.9.9/goke.apk", "size": 10000 }
            ]
          },
          {
            "tag_name": "ios-v0.0.2",
            "html_url": "https://github.com/xihale/goke/releases/tag/ios-v0.0.2",
            "prerelease": false,
            "draft": true,
            "body": "Draft release",
            "assets": [
              { "name": "Goke.ipa", "browser_download_url": "https://github.com/xihale/goke/releases/download/ios-v0.0.2/Goke.ipa", "size": 5000 }
            ]
          },
          {
            "tag_name": "ios-v0.0.1-beta.12",
            "html_url": "https://github.com/xihale/goke/releases/tag/ios-v0.0.1-beta.12",
            "prerelease": true,
            "draft": false,
            "body": "No IPA attached yet",
            "assets": [
              { "name": "checksums.txt", "browser_download_url": "https://github.com/xihale/goke/releases/download/ios-v0.0.1-beta.12/checksums.txt", "size": 128 }
            ]
          },
          {
            "tag_name": "ios-v0.0.1-beta.10",
            "html_url": "https://github.com/xihale/goke/releases/tag/ios-v0.0.1-beta.10",
            "prerelease": true,
            "draft": false,
            "body": "Older beta",
            "assets": [
              { "name": "Goke-0.0.1-beta.10.ipa", "browser_download_url": "https://github.com/xihale/goke/releases/download/ios-v0.0.1-beta.10/Goke-0.0.1-beta.10.ipa", "size": 12000000 }
            ]
          },
          {
            "tag_name": "ios-v0.0.1-beta.11",
            "html_url": "https://github.com/xihale/goke/releases/tag/ios-v0.0.1-beta.11",
            "prerelease": true,
            "draft": false,
            "published_at": "2026-11-28T10:00:00Z",
            "body": "- 修复课表刷新\\n- 新增应用内更新检查",
            "assets": [
              { "name": "Goke.ipa.sha256", "browser_download_url": "https://github.com/xihale/goke/releases/download/ios-v0.0.1-beta.11/Goke.ipa.sha256", "size": 64 },
              { "name": "Goke-iOS-0.0.1-beta.11.ipa", "browser_download_url": "https://github.com/xihale/goke/releases/download/ios-v0.0.1-beta.11/Goke-iOS-0.0.1-beta.11.ipa", "size": 14500000 }
            ]
          }
        ]
        """
        let data = Data(json.utf8)

        let update = try UpdateChecker.selectLatestIOSRelease(from: data, currentVersion: "0.0.1-beta.9")
        #expect(update != nil)
        #expect(update?.version == "0.0.1-beta.11")
        #expect(update?.tagName == "ios-v0.0.1-beta.11")
        #expect(update?.ipaFileName == "Goke-iOS-0.0.1-beta.11.ipa")
        #expect(update?.ipaSizeBytes == 14_500_000)
        #expect(update?.isPrerelease == true)
        #expect(update?.notes == "- 修复课表刷新\n- 新增应用内更新检查")

        // Stable build should ignore prerelease ios-v0.0.1-beta.11
        let stableCheck = try UpdateChecker.selectLatestIOSRelease(from: data, currentVersion: "0.0.1")
        #expect(stableCheck == nil)

        // Already on latest beta
        let upToDate = try UpdateChecker.selectLatestIOSRelease(from: data, currentVersion: "0.0.1-beta.11")
        #expect(upToDate == nil)
    }

    @Test("SideStore and AltStore URL builders percent-encode IPA URL and reject invalid URLs")
    func sideloadURLBuilders() {
        let ipaURL = "https://github.com/xihale/goke/releases/download/ios-v0.0.1-beta.11/Goke iOS+signed.ipa?token=a&b=1"
        let sideStore = UpdateChecker.sideStoreInstallURL(ipaURL: ipaURL)
        let altStore = UpdateChecker.altStoreInstallURL(ipaURL: ipaURL)

        #expect(sideStore != nil)
        #expect(sideStore?.scheme == "sidestore")
        #expect(sideStore?.host == "install")
        #expect(sideStore?.absoluteString.hasPrefix("sidestore://install?url=https%3A%2F%2F") == true)

        let sideQuery = sideStore.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "url" })?.value }
        #expect(sideQuery == ipaURL)

        #expect(altStore != nil)
        #expect(altStore?.scheme == "altstore")
        #expect(altStore?.absoluteString.hasPrefix("altstore://install?url=https%3A%2F%2F") == true)
        let altQuery = altStore.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "url" })?.value }
        #expect(altQuery == ipaURL)

        #expect(UpdateChecker.sideStoreInstallURL(ipaURL: "") == nil)
        #expect(UpdateChecker.sideStoreInstallURL(ipaURL: "ftp://example.com/app.ipa") == nil)
        #expect(UpdateChecker.altStoreInstallURL(ipaURL: "not a url") == nil)
    }

    @Test("SideStore and AltStore add-source URL builders use canonical source URL and encode properly")
    func sideloadAddSourceURLBuilders() {
        #expect(UpdateChecker.defaultSourceJSONURL == "https://goke.xeed.ink/altstore.json")
        #expect(UpdateChecker.defaultSideStoreSourceJSONURL == UpdateChecker.defaultSourceJSONURL)

        let defaultSideStore = UpdateChecker.sideStoreAddSourceURL()
        #expect(defaultSideStore != nil)
        #expect(defaultSideStore?.scheme == "sidestore")
        #expect(defaultSideStore?.host == "source")
        #expect(defaultSideStore?.absoluteString == "sidestore://source?url=https%3A%2F%2Fgoke.xeed.ink%2Faltstore.json")
        let defaultSideQuery = defaultSideStore.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "url" })?.value }
        #expect(defaultSideQuery == UpdateChecker.defaultSourceJSONURL)

        let defaultAltStore = UpdateChecker.altStoreAddSourceURL()
        #expect(defaultAltStore != nil)
        #expect(defaultAltStore?.scheme == "altstore")
        #expect(defaultAltStore?.host == "source")
        #expect(defaultAltStore?.absoluteString == "altstore://source?url=https%3A%2F%2Fgoke.xeed.ink%2Faltstore.json")
        let defaultAltQuery = defaultAltStore.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "url" })?.value }
        #expect(defaultAltQuery == UpdateChecker.defaultSourceJSONURL)

        let customSource = "https://goke.xeed.ink/altstore.json?channel=beta&v=2"
        let customSide = UpdateChecker.sideStoreAddSourceURL(sourceURL: customSource)
        let customSideQuery = customSide.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "url" })?.value }
        #expect(customSideQuery == customSource)

        let customAlt = UpdateChecker.altStoreAddSourceURL(sourceURL: customSource)
        let customAltQuery = customAlt.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "url" })?.value }
        #expect(customAltQuery == customSource)

        #expect(UpdateChecker.sideStoreAddSourceURL(sourceURL: "") == nil)
        #expect(UpdateChecker.sideStoreAddSourceURL(sourceURL: "ftp://goke.xeed.ink/altstore.json") == nil)
        #expect(UpdateChecker.altStoreAddSourceURL(sourceURL: "not a url") == nil)
    }
}
