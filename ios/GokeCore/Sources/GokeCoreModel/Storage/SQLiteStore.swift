import Foundation

#if canImport(SQLite3)
import SQLite3
#else
import CSQLite
#endif

public enum SQLiteError: Error, Sendable, Equatable, CustomStringConvertible {
    case openFailed(code: Int32, message: String)
    case prepareFailed(code: Int32, message: String)
    case stepFailed(code: Int32, message: String)
    case execFailed(code: Int32, message: String)

    public var description: String {
        switch self {
        case .openFailed(let c, let m): return "SQLite open failed (\(c)): \(m)"
        case .prepareFailed(let c, let m): return "SQLite prepare failed (\(c)): \(m)"
        case .stepFailed(let c, let m): return "SQLite step failed (\(c)): \(m)"
        case .execFailed(let c, let m): return "SQLite exec failed (\(c)): \(m)"
        }
    }
}

private let sqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

/// Actor-isolated SQLite WAL store matching Android's `MintDb.kt` 3-table schema (`records`, `oplog`, `purged`).
public actor SQLiteStore {
    public let path: String
    public nonisolated let lowMemory: Bool
    private nonisolated(unsafe) var db: OpaquePointer?

    // Cached prepared statements (actor-isolated during lifetime; finalized in deinit before closing db).
    private nonisolated(unsafe) var upsertRecordStmt: OpaquePointer?
    private nonisolated(unsafe) var deleteRecordStmt: OpaquePointer?
    private nonisolated(unsafe) var insertOpStmt: OpaquePointer?
    private nonisolated(unsafe) var insertPurgedStmt: OpaquePointer?
    private nonisolated(unsafe) var markUndoneStmt: OpaquePointer?
    private nonisolated(unsafe) var trimOpsStmt: OpaquePointer?

    private let encoder: JSONEncoder = MintJSON.makeEncoder()
    private let decoder: JSONDecoder = MintJSON.makeDecoder()

    /// Creates a SQLite store at `path` (use `":memory:"` for in-memory tests).
    ///
    /// By default (`deferOpen == true`), SQLite is opened lazily on the first actor method call so
    /// constructing `SQLiteStore` during cold launch performs zero main-thread filesystem or WAL I/O.
    /// Pass `deferOpen: false` to open and migrate eagerly in `init`.
    /// Pass `lowMemory: true` in memory-constrained extensions (e.g. WidgetKit 30 MB Jetsam cap) to
    /// disable `mmap` (`PRAGMA mmap_size = 0`) and shrink SQLite's page cache (`PRAGMA cache_size = -1000`).
    public init(path: String, deferOpen: Bool = true, lowMemory: Bool = false) throws {
        self.path = path
        self.lowMemory = lowMemory
        if !deferOpen {
            self.db = try Self.openDatabase(at: path, lowMemory: lowMemory)
        }
    }

    /// Non-throwing convenience initializer that always defers opening SQLite until the first actor method call.
    public init(lazyPath path: String, lowMemory: Bool = false) {
        self.path = path
        self.lowMemory = lowMemory
    }

    /// Whether the underlying SQLite connection has already been opened.
    public var isOpen: Bool {
        db != nil
    }

    private func openIfNeeded() throws -> OpaquePointer {
        if let db {
            return db
        }
        let opened = try Self.openDatabase(at: path, lowMemory: lowMemory)
        self.db = opened
        return opened
    }

    private static func openDatabase(at path: String, lowMemory: Bool) throws -> OpaquePointer {
        if path != ":memory:" {
            let parentURL = URL(fileURLWithPath: path).deletingLastPathComponent()
            try FileManager.default.createDirectory(at: parentURL, withIntermediateDirectories: true)
        }
        var handle: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_NOMUTEX
        let rc = sqlite3_open_v2(path, &handle, flags, nil)
        guard rc == SQLITE_OK, let opened = handle else {
            let msg = handle.flatMap { String(cString: sqlite3_errmsg($0)) } ?? "unknown error"
            if let handle { sqlite3_close(handle) }
            throw SQLiteError.openFailed(code: rc, message: msg)
        }
        let cacheSizePragma = lowMemory ? -1000 : -4000
        let mmapSizePragma = lowMemory ? 0 : 16777216
        do {
            try Self.execSQL(
                opened,
                """
                PRAGMA journal_mode=WAL;
                PRAGMA synchronous=NORMAL;
                PRAGMA busy_timeout=5000;
                PRAGMA cache_size=\(cacheSizePragma);
                PRAGMA temp_store=MEMORY;
                PRAGMA mmap_size=\(mmapSizePragma);
                CREATE TABLE IF NOT EXISTS records (
                    id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER
                );
                CREATE TABLE IF NOT EXISTS oplog (
                    id TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    source TEXT NOT NULL,
                    json TEXT NOT NULL,
                    undone_at INTEGER
                );
                CREATE INDEX IF NOT EXISTS idx_oplog_created ON oplog(created_at);
                CREATE TABLE IF NOT EXISTS purged (
                    id TEXT PRIMARY KEY,
                    json TEXT NOT NULL
                );
                """
            )
        } catch {
            sqlite3_close(opened)
            throw error
        }
        return opened
    }

    deinit {
        if let s = upsertRecordStmt { sqlite3_finalize(s) }
        if let s = deleteRecordStmt { sqlite3_finalize(s) }
        if let s = insertOpStmt { sqlite3_finalize(s) }
        if let s = insertPurgedStmt { sqlite3_finalize(s) }
        if let s = markUndoneStmt { sqlite3_finalize(s) }
        if let s = trimOpsStmt { sqlite3_finalize(s) }
        if let db {
            sqlite3_close_v2(db)
        }
    }

    public func journalMode() throws -> String {
        let db = try openIfNeeded()
        var stmt: OpaquePointer?
        let rc = sqlite3_prepare_v2(db, "PRAGMA journal_mode;", -1, &stmt, nil)
        guard rc == SQLITE_OK, let stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        defer { sqlite3_finalize(stmt) }
        if sqlite3_step(stmt) == SQLITE_ROW, let ptr = sqlite3_column_text(stmt, 0) {
            return String(cString: ptr)
        }
        return ""
    }

    /// Deletes soft-deleted records whose `deleted_at < cutoff` directly in SQLite.
    @discardableResult
    public func purgeExpiredTrash(before cutoff: Int64) throws -> Int {
        let db = try openIfNeeded()
        return try purgeExpiredTrashImpl(db: db, cutoff: cutoff)
    }

    @discardableResult
    private func purgeExpiredTrashImpl(db: OpaquePointer, cutoff: Int64) throws -> Int {
        var stmt: OpaquePointer?
        let rc = sqlite3_prepare_v2(
            db,
            "DELETE FROM records WHERE deleted_at IS NOT NULL AND deleted_at < ?;",
            -1,
            &stmt,
            nil
        )
        guard rc == SQLITE_OK, let stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int64(stmt, 1, cutoff)
        let step = sqlite3_step(stmt)
        guard step == SQLITE_DONE else {
            throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
        }
        return Int(sqlite3_changes(db))
    }

    /// Loads all records partitioned directly into an `AppSnapshot` using the SQLite `kind` column
    /// (avoiding double JSON container parsing and `[Record]` wrapper allocation).
    /// When `purgeDeletedBefore` is supplied, expired trash rows are deleted in SQLite before scanning.
    public func loadSnapshot(purgeDeletedBefore cutoff: Int64? = nil) throws -> AppSnapshot {
        let db = try openIfNeeded()
        if let cutoff {
            _ = try purgeExpiredTrashImpl(db: db, cutoff: cutoff)
        }

        var stmt: OpaquePointer?
        let rc = sqlite3_prepare_v2(db, "SELECT kind, json FROM records;", -1, &stmt, nil)
        guard rc == SQLITE_OK, let stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        defer { sqlite3_finalize(stmt) }

        var semesters: [Semester] = []
        var courses: [Course] = []
        var exceptions: [CourseException] = []
        var events: [Event] = []
        var tasks: [GokeTask] = []
        var lists: [TaskList] = []
        courses.reserveCapacity(32)
        events.reserveCapacity(16)
        tasks.reserveCapacity(32)

        while true {
            let step = sqlite3_step(stmt)
            if step == SQLITE_ROW {
                guard let ptr = sqlite3_column_blob(stmt, 1) else { continue }
                let byteCount = Int(sqlite3_column_bytes(stmt, 1))
                guard byteCount > 0 else { continue }
                let data = Data(
                    bytesNoCopy: UnsafeMutableRawPointer(mutating: ptr),
                    count: byteCount,
                    deallocator: .none
                )
                let kindPtr = sqlite3_column_text(stmt, 0)
                let kindLen = Int(sqlite3_column_bytes(stmt, 0))
                if let kindPtr, kindLen > 0 {
                    let b0 = kindPtr[0]
                    if (b0 == 83 || b0 == 115) && kindLen == 8 && strncmp(UnsafeRawPointer(kindPtr).assumingMemoryBound(to: CChar.self) + 1, "emester", 7) == 0 {
                        if let v = try? decoder.decode(Semester.self, from: data) { semesters.append(v) }
                        continue
                    } else if (b0 == 67 || b0 == 99) && kindLen == 6 && strncmp(UnsafeRawPointer(kindPtr).assumingMemoryBound(to: CChar.self) + 1, "ourse", 5) == 0 {
                        if let v = try? decoder.decode(Course.self, from: data) { courses.append(v) }
                        continue
                    } else if (b0 == 67 || b0 == 99) && kindLen == 15 && strncmp(UnsafeRawPointer(kindPtr).assumingMemoryBound(to: CChar.self) + 1, "ourseException", 14) == 0 {
                        if let v = try? decoder.decode(CourseException.self, from: data) { exceptions.append(v) }
                        continue
                    } else if (b0 == 69 || b0 == 101) && kindLen == 5 && strncmp(UnsafeRawPointer(kindPtr).assumingMemoryBound(to: CChar.self) + 1, "vent", 4) == 0 {
                        if let v = try? decoder.decode(Event.self, from: data) { events.append(v) }
                        continue
                    } else if (b0 == 84 || b0 == 116) && kindLen == 8 && strncmp(UnsafeRawPointer(kindPtr).assumingMemoryBound(to: CChar.self) + 1, "askList", 7) == 0 {
                        if let v = try? decoder.decode(TaskList.self, from: data) { lists.append(v) }
                        continue
                    } else if (b0 == 84 || b0 == 116) && kindLen == 4 && strncmp(UnsafeRawPointer(kindPtr).assumingMemoryBound(to: CChar.self) + 1, "ask", 3) == 0 {
                        if let v = try? decoder.decode(GokeTask.self, from: data) { tasks.append(v) }
                        continue
                    }
                }
                if let record = try? decoder.decode(Record.self, from: data) {
                    switch record {
                    case .semester(let v): semesters.append(v)
                    case .course(let v): courses.append(v)
                    case .courseException(let v): exceptions.append(v)
                    case .event(let v): events.append(v)
                    case .taskList(let v): lists.append(v)
                    case .task(let v): tasks.append(v)
                    }
                }
            } else if step == SQLITE_DONE {
                break
            } else {
                throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
            }
        }

        return AppSnapshot(
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            events: events,
            tasks: tasks,
            lists: lists
        )
    }

    public func loadRecords() throws -> [Record] {
        let db = try openIfNeeded()
        var stmt: OpaquePointer?
        let rc = sqlite3_prepare_v2(db, "SELECT kind, json FROM records;", -1, &stmt, nil)
        guard rc == SQLITE_OK, let stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        defer { sqlite3_finalize(stmt) }
        var out: [Record] = []
        out.reserveCapacity(128)
        while true {
            let step = sqlite3_step(stmt)
            if step == SQLITE_ROW {
                if let ptr = sqlite3_column_blob(stmt, 1) {
                    let byteCount = Int(sqlite3_column_bytes(stmt, 1))
                    if byteCount > 0 {
                        let data = Data(
                            bytesNoCopy: UnsafeMutableRawPointer(mutating: ptr),
                            count: byteCount,
                            deallocator: .none
                        )
                        let kind = sqlite3_column_text(stmt, 0).map { String(cString: $0) } ?? ""
                        if let record = try? Record.decodeDirect(kind: kind, from: data, using: decoder) {
                            out.append(record)
                        }
                    }
                }
            } else if step == SQLITE_DONE {
                break
            } else {
                throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
            }
        }
        return out
    }

    /// Transactional batch write of record upserts, deletes, oplog row, undone marker, purged tombstones,
    /// and optional in-transaction `trimOpsKeep` pruning.
    public func write(
        upserts: [Record] = [],
        deletes: [String] = [],
        op: OpRow? = nil,
        markUndone: (id: String, undoneAt: Int64?)? = nil,
        purged: [Record] = [],
        trimOpsKeep: Int? = nil
    ) throws {
        let db = try openIfNeeded()
        try Self.execSQL(db, "BEGIN IMMEDIATE TRANSACTION;")
        do {
            if !upserts.isEmpty {
                let stmt = try cachedStatement(
                    db: db,
                    slot: &upsertRecordStmt,
                    sql: "INSERT OR REPLACE INTO records (id, kind, json, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?);"
                )
                defer {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                }
                for r in upserts {
                    let jsonData = try encoder.encode(r)
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                    Self.bindText(stmt, 1, r.id)
                    Self.bindText(stmt, 2, r.syncKind)
                    Self.bindDataAsText(stmt, 3, jsonData)
                    sqlite3_bind_int64(stmt, 4, r.updatedAt)
                    if let d = r.deletedAt {
                        sqlite3_bind_int64(stmt, 5, d)
                    } else {
                        sqlite3_bind_null(stmt, 5)
                    }
                    let step = sqlite3_step(stmt)
                    guard step == SQLITE_DONE else {
                        throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
                    }
                }
            }

            if !deletes.isEmpty {
                let stmt = try cachedStatement(
                    db: db,
                    slot: &deleteRecordStmt,
                    sql: "DELETE FROM records WHERE id = ?;"
                )
                defer {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                }
                for id in deletes {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                    Self.bindText(stmt, 1, id)
                    let step = sqlite3_step(stmt)
                    guard step == SQLITE_DONE else {
                        throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
                    }
                }
            }

            if let op {
                let stmt = try cachedStatement(
                    db: db,
                    slot: &insertOpStmt,
                    sql: "INSERT OR REPLACE INTO oplog (id, created_at, description, source, json, undone_at) VALUES (?, ?, ?, ?, ?, ?);"
                )
                defer {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                }
                sqlite3_reset(stmt)
                sqlite3_clear_bindings(stmt)
                Self.bindText(stmt, 1, op.id)
                sqlite3_bind_int64(stmt, 2, op.createdAt)
                Self.bindText(stmt, 3, op.description)
                Self.bindText(stmt, 4, op.source)
                Self.bindText(stmt, 5, op.json)
                if let u = op.undoneAt {
                    sqlite3_bind_int64(stmt, 6, u)
                } else {
                    sqlite3_bind_null(stmt, 6)
                }
                let step = sqlite3_step(stmt)
                guard step == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
                }
            }

            if !purged.isEmpty {
                let stmt = try cachedStatement(
                    db: db,
                    slot: &insertPurgedStmt,
                    sql: "INSERT OR REPLACE INTO purged (id, json) VALUES (?, ?);"
                )
                defer {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                }
                for r in purged {
                    let jsonData = try encoder.encode(r)
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                    Self.bindText(stmt, 1, r.id)
                    Self.bindDataAsText(stmt, 2, jsonData)
                    let step = sqlite3_step(stmt)
                    guard step == SQLITE_DONE else {
                        throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
                    }
                }
            }

            if let markUndone {
                let stmt = try cachedStatement(
                    db: db,
                    slot: &markUndoneStmt,
                    sql: "UPDATE oplog SET undone_at = ? WHERE id = ?;"
                )
                defer {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                }
                sqlite3_reset(stmt)
                sqlite3_clear_bindings(stmt)
                if let u = markUndone.undoneAt {
                    sqlite3_bind_int64(stmt, 1, u)
                } else {
                    sqlite3_bind_null(stmt, 1)
                }
                Self.bindText(stmt, 2, markUndone.id)
                let step = sqlite3_step(stmt)
                guard step == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
                }
            }

            if let trimOpsKeep {
                try trimOpsImpl(db: db, keep: trimOpsKeep)
            }

            try Self.execSQL(db, "COMMIT;")
        } catch {
            _ = try? Self.execSQL(db, "ROLLBACK;")
            throw error
        }
    }

    public func ops(limit: Int = 100) throws -> [OpRow] {
        let db = try openIfNeeded()
        var stmt: OpaquePointer?
        let sql = "SELECT id, created_at, description, source, json, undone_at FROM oplog ORDER BY created_at DESC LIMIT ?;"
        let rc = sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
        guard rc == SQLITE_OK, let stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int64(stmt, 1, Int64(limit))
        var out: [OpRow] = []
        while true {
            let step = sqlite3_step(stmt)
            if step == SQLITE_ROW {
                let id = sqlite3_column_text(stmt, 0).map { String(cString: $0) } ?? ""
                let createdAt = sqlite3_column_int64(stmt, 1)
                let desc = sqlite3_column_text(stmt, 2).map { String(cString: $0) } ?? ""
                let source = sqlite3_column_text(stmt, 3).map { String(cString: $0) } ?? ""
                let json = sqlite3_column_text(stmt, 4).map { String(cString: $0) } ?? ""
                let undoneAt: Int64? = sqlite3_column_type(stmt, 5) == SQLITE_NULL ? nil : sqlite3_column_int64(stmt, 5)
                out.append(OpRow(id: id, createdAt: createdAt, description: desc, source: source, json: json, undoneAt: undoneAt))
            } else if step == SQLITE_DONE {
                break
            } else {
                throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
            }
        }
        return out
    }

    public func loadPurged() throws -> [Record] {
        let db = try openIfNeeded()
        var stmt: OpaquePointer?
        let rc = sqlite3_prepare_v2(db, "SELECT json FROM purged;", -1, &stmt, nil)
        guard rc == SQLITE_OK, let stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        defer { sqlite3_finalize(stmt) }
        var out: [Record] = []
        while true {
            let step = sqlite3_step(stmt)
            if step == SQLITE_ROW {
                if let ptr = sqlite3_column_blob(stmt, 0) {
                    let byteCount = Int(sqlite3_column_bytes(stmt, 0))
                    if byteCount > 0 {
                        let data = Data(
                            bytesNoCopy: UnsafeMutableRawPointer(mutating: ptr),
                            count: byteCount,
                            deallocator: .none
                        )
                        if let record = try? decoder.decode(Record.self, from: data) {
                            out.append(record)
                        }
                    }
                }
            } else if step == SQLITE_DONE {
                break
            } else {
                throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
            }
        }
        return out
    }

    public func clearPurged(ids: [String]) throws {
        guard !ids.isEmpty else { return }
        let db = try openIfNeeded()
        try Self.execSQL(db, "BEGIN IMMEDIATE TRANSACTION;")
        do {
            var stmt: OpaquePointer?
            let rc = sqlite3_prepare_v2(db, "DELETE FROM purged WHERE id = ?;", -1, &stmt, nil)
            guard rc == SQLITE_OK, let stmt else {
                throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
            }
            defer { sqlite3_finalize(stmt) }
            for id in ids {
                sqlite3_reset(stmt)
                sqlite3_clear_bindings(stmt)
                Self.bindText(stmt, 1, id)
                let step = sqlite3_step(stmt)
                guard step == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
                }
            }
            try Self.execSQL(db, "COMMIT;")
        } catch {
            _ = try? Self.execSQL(db, "ROLLBACK;")
            throw error
        }
    }

    public func clearAll() throws {
        let db = try openIfNeeded()
        try Self.execSQL(db, "BEGIN IMMEDIATE TRANSACTION; DELETE FROM records; DELETE FROM oplog; COMMIT;")
    }

    public func trimOps(keep: Int = 200) throws {
        let db = try openIfNeeded()
        try trimOpsImpl(db: db, keep: keep)
    }

    private func trimOpsImpl(db: OpaquePointer, keep: Int) throws {
        let stmt = try cachedStatement(
            db: db,
            slot: &trimOpsStmt,
            sql: "DELETE FROM oplog WHERE id NOT IN (SELECT id FROM oplog ORDER BY created_at DESC LIMIT ?);"
        )
        defer {
            sqlite3_reset(stmt)
            sqlite3_clear_bindings(stmt)
        }
        sqlite3_reset(stmt)
        sqlite3_clear_bindings(stmt)
        sqlite3_bind_int64(stmt, 1, Int64(keep))
        let step = sqlite3_step(stmt)
        guard step == SQLITE_DONE else {
            throw SQLiteError.stepFailed(code: step, message: Self.errorMessage(db))
        }
    }

    private func cachedStatement(
        db: OpaquePointer,
        slot: inout OpaquePointer?,
        sql: String
    ) throws -> OpaquePointer {
        if let existing = slot {
            return existing
        }
        var stmt: OpaquePointer?
        let rc = sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
        guard rc == SQLITE_OK, let prepared = stmt else {
            throw SQLiteError.prepareFailed(code: rc, message: Self.errorMessage(db))
        }
        slot = prepared
        return prepared
    }

    private static func execSQL(_ db: OpaquePointer, _ sql: String) throws {
        var errMsg: UnsafeMutablePointer<CChar>?
        let rc = sqlite3_exec(db, sql, nil, nil, &errMsg)
        if rc != SQLITE_OK {
            let msg = errMsg.map { String(cString: $0) } ?? errorMessage(db)
            if let errMsg { sqlite3_free(errMsg) }
            throw SQLiteError.execFailed(code: rc, message: msg)
        }
    }

    private static func bindText(_ stmt: OpaquePointer, _ index: Int32, _ value: String) {
        value.withCString { cStr in
            _ = sqlite3_bind_text(stmt, index, cStr, -1, sqliteTransient)
        }
    }

    private static func bindDataAsText(_ stmt: OpaquePointer, _ index: Int32, _ data: Data) {
        data.withUnsafeBytes { rawBuf in
            let ptr = rawBuf.bindMemory(to: CChar.self).baseAddress
            _ = sqlite3_bind_text(stmt, index, ptr, Int32(data.count), sqliteTransient)
        }
    }

    private static func errorMessage(_ db: OpaquePointer) -> String {
        sqlite3_errmsg(db).map { String(cString: $0) } ?? "unknown sqlite error"
    }

    // MARK: - Non-Destructive Content Inspection (App Group Migration)

    public enum DatabaseContentState: Sendable, Equatable {
        case missingOrZeroBytes
        case emptySchemaOnly
        case hasData
        case unreadable
    }

    /// Synchronously inspects whether a SQLite database file on disk is missing/empty,
    /// initialized with empty Goke tables (0 rows in `records` and `oplog`), populated,
    /// or unreadable. Never creates a database file and never mutates table rows.
    public nonisolated static func inspectContentState(
        atPath path: String,
        fileManager: FileManager = .default
    ) -> DatabaseContentState {
        guard fileManager.fileExists(atPath: path),
              let attrs = try? fileManager.attributesOfItem(atPath: path),
              let size = (attrs[.size] as? NSNumber)?.int64Value,
              size > 0
        else {
            return .missingOrZeroBytes
        }

        var handle: OpaquePointer?
        var rc = sqlite3_open_v2(path, &handle, SQLITE_OPEN_READONLY, nil)
        if rc != SQLITE_OK || handle == nil {
            if let handle { sqlite3_close_v2(handle) }
            handle = nil
            // Fallback for WAL databases whose `-shm` index is absent: open RW (without CREATE)
            // and immediately lock to `PRAGMA query_only = ON` so rows are never mutated.
            rc = sqlite3_open_v2(path, &handle, SQLITE_OPEN_READWRITE, nil)
            guard rc == SQLITE_OK, let opened = handle else {
                if let handle { sqlite3_close_v2(handle) }
                return .unreadable
            }
            _ = sqlite3_exec(opened, "PRAGMA query_only = ON;", nil, nil, nil)
        }
        guard let db = handle else { return .unreadable }
        defer { sqlite3_close_v2(db) }
        sqlite3_busy_timeout(db, 1000)

        for table in ["records", "oplog"] {
            var stmt: OpaquePointer?
            let prep = sqlite3_prepare_v2(db, "SELECT 1 FROM \(table) LIMIT 1;", -1, &stmt, nil)
            if prep != SQLITE_OK {
                if let stmt { sqlite3_finalize(stmt) }
                let msg = errorMessage(db).lowercased()
                if msg.contains("no such table") {
                    continue
                }
                return .unreadable
            }
            defer { if let stmt { sqlite3_finalize(stmt) } }
            let step = sqlite3_step(stmt)
            if step == SQLITE_ROW {
                return .hasData
            } else if step != SQLITE_DONE {
                return .unreadable
            }
        }
        return .emptySchemaOnly
    }
}
