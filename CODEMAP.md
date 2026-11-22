# Goke（`app.goke`）Codemap

> **Token Guard**: Discovery first (`csearch` MCP `search_code` or `rg -n`), then targeted `read_file` slices (`<= 120-180` lines around hit lines). Android strings: two-step `rg -l "<text>" app/src/main/res` -> `rg -n "<key>"` (do not query Chinese UI copy in `csearch`; `strings.xml` & `DESIGN.md` are excluded from `.csearch`).

## 1. Module & Directory Index

| Module | Key Paths | Responsibility |
|---|---|---|
| `:core` | `core/src/main/kotlin/app/goke/core/{model,agenda,change,recurrence,todo,time,importer,export,sync,jxfw,parcel,util}/` | Pure Kotlin domain logic (JVM-testable; `ChangeSet`, `EventExpander`, `Sync`, `JxfwSync`, `ParcelRuleEngine`) |
| `:script` | `script/src/main/kotlin/app/goke/script/ScriptRunner.kt` · `resources/goke/{sdk.js,goke.d.ts}` | QuickJS sandbox for batch mutations + dry-run preview (no IO) |
| `:app` core | `app/src/main/java/app/goke/{GokeApp,MainActivity,DeepLink,BenchSeed}.kt` | App container, Activity entry, deep links, benchmark seeding |
| `:app` UI | `app/src/main/java/app/goke/ui/{GokeRoot,Nav,Common,Format}.kt` · `ui/theme/Theme.kt` · `ui/components/` · `ui/screens/` | Compose M3 Expressive UI, `Route` table, CompositionLocal state |
| `:app` data/sys | `app/src/main/java/app/goke/{data,calendar,jxfw,lanweb,mcp,notify,sync,widget,advanced}/` | Handwritten SQLite (`MintDb.kt`), Reminders, 5 Widgets, `jxfw`, LAN Web, System Calendar, Local MCP |
| `server` (Go) | `server/cmd/goke-server/main.go` · `server/internal/{server,store,mcp}/` · `server/openapi.yaml` | Go + SQLite self-hosted sync server (`auth`, `sync`, `admin`, `POST /mcp`) |
| `server` (Web) | `server/internal/server/web/app/{index.html,app.css,js/*.js,js/views/*.js}` · `web/admin/` | Zero-build Vanilla ESM web client (`go:embed`, CSP `self`) + admin console |
| `:benchmark` | `benchmark/src/main/java/app/goke/benchmark/{Startup,TabSwitch,Navigation,Today,Timetable,Calendar,Todo}Benchmark.kt` | Macrobenchmark suites (`app.goke.bench`, physical device only) |

## 2. Key Entry Points & UI Mapping

- **Android App**: `GokeApp.kt` (wires `Repository`, `SettingsStore`, `ParcelRuleStore`, `SecretStore`, `ReminderScheduler`, `AdvancedController`, `LocalMcpManager`, `SyncManager`, `SystemCalendarSync`, `JxfwManager`) -> `MainActivity.kt` -> `ui/GokeRoot.kt` (`Tabs`: `TODAY`, `TIMETABLE`, `CALENDAR`, `TODO`, `ME`; `gokeDestinations` stack) & `ui/Nav.kt` (`Route`, `Actions.commit`).
- **Screens (`app/src/main/java/app/goke/ui/screens/`)**: `TodayScreen.kt`, `TimetableScreen.kt`, `CalendarScreen.kt`, `TodoScreen.kt` (`TodoRows.kt`, `TodoModel.kt`, `QuickAddBar.kt`, `TodoSheets.kt`), `TodoEditScreen.kt`, `EventTimeSheet.kt`, `CourseEditScreen.kt`, `EditTimetableScreen.kt`, `SettingsScreens.kt`, `WidgetsScreen.kt`, `SyncScreen.kt`, `JxfwScreen.kt`, `GradesScreen.kt`, `McpScreen.kt`, `LanWebScreen.kt`, `DataScreens.kt`, `ManageScreens.kt`, `ParcelRules.kt`, `ReliabilityScreen.kt`.
- **Server & Web**: `server/cmd/goke-server/main.go` -> `internal/server/server.go` (`sync.go`, `auth.go`, `admin.go`, `mcp.go`); Web entry `server/internal/server/web/app/js/main.js` -> `views/{today,timetable,calendar,todo,settings,shared}.js`.

## 3. Platform & Architecture Rules (Critical)

- **No ViewModel**: Android uses `LocalApp` / `LocalActions` / `LocalNav`; all writes go through `Actions.commit(ChangeSet)` to preserve undo history.
- **Android vs Web parity**: Android owns local SQLite, `jxfw` CAS import, QuickJS batch scripts, parcel SMS rule engine, 5 desktop widgets, system calendar two-way sync, and Local MCP (`LocalMcpServer.kt`, `LocalMcpTools.kt`). Web (`go:embed`, zero build) omits file import, QuickJS, parcel parser, and system calendar sync; obeys strict CSP (`script-src 'self'; style-src 'self'`, no inline styles/HTML string building).
- **Local MCP vs Cloud MCP**: Local MCP (`app/.../mcp/`) runs on device with QuickJS tool support; Cloud MCP (`server/internal/mcp/`) serves `POST /mcp` (11 tools, LWW sync path).
- **Theme & Strings**: Follow `DESIGN.md` (`mintClickable` / `mintPressable`, never bare `.clickable`; no hardcoded `Color(0xFF...)`); sync theme tokens across `Theme.kt` <-> `web/app/js/theme.js` <-> widget `colors.xml`; keep visible copy in `strings.xml`.
- **Hot large files (>30KB — never read whole)**: `TimetableScreen.kt` (79KB), `LocalMcpTools.kt` (50KB), `TodoEditScreen.kt`, `EventTimeSheet.kt`, `CourseEditScreen.kt`, `CalendarScreen.kt`, `SettingsScreens.kt`, `Kit.kt`, `QuickAddBar.kt`, `TodoScreen.kt`, `CalendarBridge.kt`.

## 4. Build, Lint & Test Commands (1-Line Cheatsheet)

- **JVM & Robolectric**: `./gradlew :core:test :script:test` · `./gradlew :app:testDebugUnitTest` (outputs `app/build/shots/`).
- **APK & Format / Strings**: `./gradlew :app:assembleDebug` · `./gradlew spotlessCheck` (`spotlessApply` to fix) · `python3 scripts/check_strings.py --unused`.
- **Go Server & Web/E2E**: `cd server && go vet ./... && go test ./...` · `cd server && node --test webtest/*.test.mjs` · `bash scripts/sync-e2e.sh` · `bash scripts/web-e2e.sh`.
