import Foundation
import GokeCoreModel

/// Wire-level record exchanged with the self-hosted Go sync server (`server/openapi.yaml`).
public struct WireRecord: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var kind: String
    public var data: JSONValue
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var seq: Int64

    public init(
        id: String,
        kind: String,
        data: JSONValue,
        updatedAt: Int64,
        deletedAt: Int64? = nil,
        seq: Int64 = 0
    ) {
        self.id = id
        self.kind = kind
        self.data = data
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.seq = seq
    }

    private enum CodingKeys: String, CodingKey {
        case id, kind, data, updatedAt, deletedAt, seq
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.kind = try c.decode(String.self, forKey: .kind)
        self.data = try c.decode(JSONValue.self, forKey: .data)
        self.updatedAt = try c.decode(Int64.self, forKey: .updatedAt)
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.seq = try c.decodeIfPresent(Int64.self, forKey: .seq) ?? 0
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(kind, forKey: .kind)
        try c.encode(data, forKey: .data)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(seq, forKey: .seq)
    }

    /// Encodes a domain `Record` into a `WireRecord` ready for sync push.
    public static func encode(_ record: Record, seq: Int64 = 0) throws -> WireRecord {
        let rawData = try MintJSON.makeEncoder().encode(record)
        let jsonValue = try MintJSON.makeDecoder().decode(JSONValue.self, from: rawData)
        return WireRecord(
            id: record.id,
            kind: record.syncKind,
            data: jsonValue,
            updatedAt: record.updatedAt,
            deletedAt: record.deletedAt,
            seq: seq
        )
    }

    /// Decodes `data` into a domain `Record`; returns `nil` for unknown future types so sync can safely skip them.
    public func decodeRecord() -> Record? {
        guard let rawData = try? MintJSON.makeEncoder().encode(data) else { return nil }
        return try? MintJSON.makeDecoder().decode(Record.self, from: rawData)
    }
}
