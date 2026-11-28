import Foundation
import GokeCoreModel

/// Full JSON backup envelope matching Android `ExportBundle`.
public struct ExportBundle: Sendable, Hashable, Codable {
    public var app: String
    public var version: Int
    public var exportedAt: Int64
    public var snapshot: AppSnapshot

    public init(
        app: String = "goke",
        version: Int = 1,
        exportedAt: Int64,
        snapshot: AppSnapshot
    ) {
        self.app = app
        self.version = version
        self.exportedAt = exportedAt
        self.snapshot = snapshot
    }

    private enum CodingKeys: String, CodingKey {
        case app, version, exportedAt, snapshot
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.app = try c.decodeIfPresent(String.self, forKey: .app) ?? "goke"
        self.version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        self.exportedAt = try c.decodeIfPresent(Int64.self, forKey: .exportedAt) ?? 0
        self.snapshot = try c.decode(AppSnapshot.self, forKey: .snapshot)
    }
}

/// JSON backup encoder/decoder supporting both Goke v1 `ExportBundle` and legacy `mintcal-web` record arrays.
public enum BackupCodec {
    public static func encode(_ bundle: ExportBundle, pretty: Bool = true) throws -> String {
        try MintJSON.encodeToString(bundle, pretty: pretty)
    }

    public static func decode(_ text: String) throws -> ExportBundle {
        let data = Data(text.utf8)
        let decoder = MintJSON.makeDecoder()
        let root = try decoder.decode(JSONValue.self, from: data)
        if let obj = root.objectValue {
            if obj["snapshot"] != nil {
                return try decoder.decode(ExportBundle.self, from: data)
            }
            if obj["app"]?.stringValue == "mintcal-web", let recordsArray = obj["records"]?.arrayValue {
                return decodeLegacyWeb(rootObject: obj, recordsArray: recordsArray)
            }
        }
        return try decoder.decode(ExportBundle.self, from: data)
    }

    private static func decodeLegacyWeb(
        rootObject: [String: JSONValue],
        recordsArray: [JSONValue]
    ) -> ExportBundle {
        let exportedAt: Int64
        if let rawInt = rootObject["exportedAt"]?.int64Value {
            exportedAt = rawInt
        } else if let iso = rootObject["exportedAt"]?.stringValue,
                  let parsedDate = parseISO8601(iso) {
            exportedAt = Int64(parsedDate.timeIntervalSince1970 * 1000)
        } else {
            exportedAt = 0
        }

        let encoder = MintJSON.makeEncoder()
        let decoder = MintJSON.makeDecoder()
        var semesters: [Semester] = []
        var courses: [Course] = []
        var exceptions: [CourseException] = []
        var events: [Event] = []
        var tasks: [GokeTask] = []
        var lists: [TaskList] = []

        for el in recordsArray {
            guard let elData = try? encoder.encode(el),
                  let rec = try? decoder.decode(Record.self, from: elData)
            else { continue }
            switch rec {
            case .semester(let v): semesters.append(v)
            case .course(let v): courses.append(v)
            case .courseException(let v): exceptions.append(v)
            case .event(let v): events.append(v)
            case .task(let v): tasks.append(v)
            case .taskList(let v): lists.append(v)
            }
        }

        return ExportBundle(
            app: "mintcal-web",
            version: 1,
            exportedAt: exportedAt,
            snapshot: AppSnapshot(
                semesters: semesters,
                courses: courses,
                exceptions: exceptions,
                events: events,
                tasks: tasks,
                lists: lists
            )
        )
    }

    private static let isoLock = NSLock()
    private nonisolated(unsafe) static let isoWithFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    private nonisolated(unsafe) static let isoStandard: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private static func parseISO8601(_ text: String) -> Date? {
        isoLock.withLock {
            isoWithFraction.date(from: text) ?? isoStandard.date(from: text)
        }
    }
}
