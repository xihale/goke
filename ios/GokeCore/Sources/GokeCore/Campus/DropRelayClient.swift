import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

// MARK: - DropRelay HTTP Client (matches Android `droprelay/DropRelayClient.kt`)

public enum DropRelayErrorKind: String, Sendable, Hashable, Codable {
    case invalidUrl
    case unreachable
    case notFound
    case rateLimited
    case serverBusy
    case badResponse
}

public struct DropRelayError: Error, Sendable, Hashable, LocalizedError {
    public let kind: DropRelayErrorKind
    public let status: Int
    public let message: String

    public init(kind: DropRelayErrorKind, status: Int = 0, message: String? = nil) {
        self.kind = kind
        self.status = status
        self.message = message ?? kind.rawValue
    }

    public var errorDescription: String? { message }
}

public enum DropRelayIndexFetchResult: Sendable, Equatable {
    case notModified
    case updated(
        index: DropRelayIndex,
        rawJson: String,
        etag: String?,
        lastModified: String?
    )
}

/// Minimal DropRelay HTTP client supporting ETag / If-Modified-Since and `/app/manifest.json` fallback.
public actor DropRelayClient {
    private static let userAgent = "Goke-iOS/DropRelay"
    private static let timeoutInterval: TimeInterval = 15

    private let transport: any HTTPTransport

    public init(transport: any HTTPTransport = URLSessionHTTPTransport()) {
        self.transport = transport
    }

    public func fetchIndex(
        shareBaseUrl: String,
        etag: String? = nil,
        lastModified: String? = nil
    ) async throws -> DropRelayIndexFetchResult {
        guard let base = DropRelayLogic.normalizeBaseUrl(shareBaseUrl) else {
            throw DropRelayError(kind: .invalidUrl, message: "Invalid URL: \(shareBaseUrl)")
        }

        let primary = try await requestGet(
            urlString: "\(base)/api/index.json",
            etag: etag,
            lastModified: lastModified
        )
        let resp: HTTPResponse
        if primary.statusCode == 404 {
            resp = try await requestGet(
                urlString: "\(base)/app/manifest.json",
                etag: etag,
                lastModified: lastModified
            )
        } else {
            resp = primary
        }

        if resp.statusCode == 304 {
            return .notModified
        }
        guard (200...299).contains(resp.statusCode) else {
            throw mapHttpError(status: resp.statusCode)
        }

        let parsed: DropRelayIndex
        do {
            parsed = try DropRelayLogic.parseIndexJson(resp.bodyString)
        } catch {
            throw DropRelayError(
                kind: .badResponse,
                status: resp.statusCode,
                message: "Parse error: \(error.localizedDescription)"
            )
        }

        let normalizedJson: String
        if let encoded = try? JSONEncoder().encode(parsed),
           let str = String(data: encoded, encoding: .utf8)
        {
            normalizedJson = str
        } else {
            normalizedJson = resp.bodyString
        }

        let respEtag = resp.firstHeader("ETag")?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nonEmpty
        let respLastMod = resp.firstHeader("Last-Modified")?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nonEmpty

        return .updated(
            index: parsed,
            rawJson: normalizedJson,
            etag: respEtag,
            lastModified: respLastMod
        )
    }

    private func requestGet(
        urlString: String,
        etag: String?,
        lastModified: String?
    ) async throws -> HTTPResponse {
        guard let url = URL(string: urlString) else {
            throw DropRelayError(kind: .invalidUrl, message: "Invalid URL: \(urlString)")
        }
        var headers: [HTTPHeader] = [
            HTTPHeader(name: "Accept", value: "application/json"),
            HTTPHeader(name: "User-Agent", value: Self.userAgent),
        ]
        if let etag = etag?.trimmingCharacters(in: .whitespacesAndNewlines), !etag.isEmpty {
            headers.append(HTTPHeader(name: "If-None-Match", value: etag))
        }
        if let lastModified = lastModified?.trimmingCharacters(in: .whitespacesAndNewlines), !lastModified.isEmpty {
            headers.append(HTTPHeader(name: "If-Modified-Since", value: lastModified))
        }

        do {
            return try await transport.send(
                HTTPRequest(
                    method: "GET",
                    url: url,
                    headers: headers,
                    body: nil,
                    followRedirects: true,
                    timeoutInterval: Self.timeoutInterval
                )
            )
        } catch let err as DropRelayError {
            throw err
        } catch {
            throw DropRelayError(kind: .unreachable, message: describeError(error))
        }
    }

    private func mapHttpError(status: Int) -> DropRelayError {
        switch status {
        case 404:
            return DropRelayError(kind: .notFound, status: status, message: "HTTP 404")
        case 429:
            return DropRelayError(kind: .rateLimited, status: status, message: "HTTP 429")
        case 503:
            return DropRelayError(kind: .serverBusy, status: status, message: "HTTP 503")
        default:
            return DropRelayError(kind: .badResponse, status: status, message: "HTTP \(status)")
        }
    }

    private func describeError(_ error: Error) -> String {
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain {
            switch ns.code {
            case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed:
                return "DNS: \(error.localizedDescription)"
            case NSURLErrorTimedOut:
                return "Timeout: \(error.localizedDescription)"
            case NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateUntrusted,
                 NSURLErrorServerCertificateHasBadDate, NSURLErrorServerCertificateNotYetValid,
                 NSURLErrorServerCertificateHasUnknownRoot:
                return "TLS: \(error.localizedDescription)"
            case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost, NSURLErrorNotConnectedToInternet:
                return "Connect: \(error.localizedDescription)"
            default:
                break
            }
        }
        return error.localizedDescription
    }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}
