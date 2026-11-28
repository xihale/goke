# Goke (工课) for iOS

Native SwiftUI + Swift 6 iOS client for Goke (minimum **iOS 17.0**, built with **Xcode 26 SDK**; iOS 18+ ControlWidget and iOS 26 Liquid Glass are `#available`-gated with graceful degradation on older/low-RAM devices).

## Layout

- `GokeCore/` — Pure Swift 6 SwiftPM package (models, wire JSON `_t` codec, SQLite WAL storage, recurrence/agenda, sync, campus contracts & fallback stubs, ICS/JSON export). Compiles and tests on both **Linux** and **iOS**.
- `GokeShared/` — Shared SwiftPM package (`GokeTheme`, `AppGroupContainer`, `GokeClassActivityAttributes`) consumed by both `GokeApp` and `GokeWidgetExtension`.
- `GokeAdapterLoader/` — Optional private campus adapter bridge package. Automatically links `../../adapters/ios/GokeAdapters` when present and falls back to `UnavailableCampusAdapterFactory` in open-source builds.
- `GokeApp/` — SwiftUI application target (`app.goke.ios`).
- `GokeWidgetExtension/` — WidgetKit + Live Activity + ControlWidget + AppIntents extension (`app.goke.ios.widget`).
- `project.yml` — [XcodeGen](https://github.com/yonaskolb/XcodeGen) specification generating `Goke.xcodeproj`.
- `Package.swift` + `xtool.yml` — Root SwiftPM manifest and [xtool](https://github.com/xtool-org/xtool) config for building/sideloading directly from Linux.

## 1. Local Linux Tests (`GokeCore`)

`GokeCore` has zero UIKit/SwiftUI dependencies, so you can run the full domain & storage test suite directly on Linux:

```bash
cd ios/GokeCore && swift test
cd ../GokeAdapterLoader && swift test
```

> **Optional Private Adapters Cache Note**: SwiftPM caches evaluated `Package.swift` manifests by content hash. If you build in open-source mode first and later clone `adapters/` into the same checkout, clear SwiftPM caches (`swift package purge-cache` / `rm -rf .build` / Xcode **Reset Package Caches**) so `GokeAdapterLoader` re-evaluates `hasPrivateAdapters`.

## 2. Local Linux `.ipa` Build (`xtool`, no GitHub CI)

To compile and package/deploy directly from Linux without GitHub Actions, install Swift 6.4, `usbmuxd`, and [xtool](https://github.com/xtool-org/xtool), plus a user-supplied `Xcode.xip` downloaded from Apple Developer:

```bash
xtool setup                             # log in with Apple ID + pass path to Xcode.xip
python3 scripts/sync_ios_version.py     # sync CFBundleShortVersionString / CFBundleVersion / GokeDisplayVersion from Git tag
cd ios
xtool dev                               # build, sign with your Apple ID, and install over USB
xtool dev build -c release --ipa        # export unsigned .ipa for AltStore/SideStore/LiveContainer/TrollStore
```

### Versioning & App Icon

- **Version sync**: `python3 scripts/sync_ios_version.py` mirrors `app/build.gradle.kts` (`git describe --tags --match 'v[0-9]*'`), updating `ios/GokeApp/Resources/Info.plist`, `ios/GokeWidgetExtension/Resources/Info.plist`, and `ios/project.yml`:
  - `CFBundleShortVersionString` / `MARKETING_VERSION`: `MAJOR.MINOR.PATCH` (Apple numeric requirement, e.g. `0.0.1`).
  - `CFBundleVersion` / `CURRENT_PROJECT_VERSION`: monotonic integer matching Android `computeVersionCode` (e.g. `12099`).
  - `GokeDisplayVersion`: full SemVer string without `-dirty` (e.g. `0.0.1-beta.9` on a tag, `0.0.1-beta.9+24` after tag).
  - Run `python3 scripts/sync_ios_version.py --check` to verify sync without writing.
- **App Icon**: `ios/GokeApp/Resources/AppIcon.png` (1024×1024 opaque full-bleed PNG, referenced by `xtool.yml` `iconPath`) and `ios/GokeApp/Resources/Assets.xcassets/AppIcon.appiconset/` (light, dark, tinted 1024×1024 opaque PNGs for Xcode / XcodeGen). `GokeMark.imageset` provides the rounded in-app brand mark.

`GokeCore` intentionally uses SQLite WAL instead of SwiftData `@Model` macros so it stays compatible with Linux toolchains. Both `GokeApp` and `GokeWidgetExtension` share `GokeShared/` cleanly across both `Package.swift` (`xtool`) and `project.yml` (`XcodeGen`).

## 3. GitHub Actions CI & Sideloading (`Goke-unsigned.ipa`)

The workflow [`.github/workflows/ios.yml`](../.github/workflows/ios.yml) runs on **`macos-26`** (triggered on `workflow_dispatch` and on pushes/PRs touching `ios/**`):

1. Runs `swift test` in `ios/GokeCore`.
2. Generates `ios/Goke.xcodeproj` with `xcodegen generate`.
3. Runs iOS Simulator tests and builds `GokeApp`.
4. Archives an unsigned Release build (`CODE_SIGNING_ALLOWED=NO`) and uploads **`goke-ios-unsigned-ipa`** (`Goke-unsigned.ipa`).

### Sideloading `Goke-unsigned.ipa`

Download `Goke-unsigned.ipa` from the GitHub Actions run artifacts and install using your preferred sideloading tool:

- **AltStore / SideStore**: Share `Goke-unsigned.ipa` to AltStore or SideStore; it re-signs with your Apple ID and refreshes automatically.
- **LiveContainer**: Import `Goke-unsigned.ipa` directly into LiveContainer (no 3-app free-ID slot consumed).
- **TrollStore** (supported iOS versions): Open `Goke-unsigned.ipa` in TrollStore for permanent installation with full entitlements.

> **App Group note**: Free Apple ID sideloading rewrites bundle IDs and may strip `group.app.goke.shared`. Goke detects when the App Group container is unavailable and falls back cleanly to `Application Support/` so the app launches reliably under any sideloader.
