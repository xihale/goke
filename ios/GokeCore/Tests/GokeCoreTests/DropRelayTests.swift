import Foundation
import Testing
@testable import GokeCore

private actor MockDropRelayTransport: HTTPTransport {
    typealias Handler = @Sendable (HTTPRequest) async throws -> HTTPResponse

    private var recorded: [HTTPRequest] = []
    private let handler: Handler

    init(handler: @escaping Handler) {
        self.handler = handler
    }

    func send(_ request: HTTPRequest) async throws -> HTTPResponse {
        recorded.append(request)
        return try await handler(request)
    }

    func requests() -> [HTTPRequest] {
        recorded
    }
}

@Suite("DropRelay Campus Resources")
struct DropRelayTests {
    @Test("URL normalization, path encoding, and DropRelay URL builders")
    func urlHelpers() {
        #expect(DropRelayLogic.normalizeBaseUrl(" https://share.example.com/ ") == "https://share.example.com")
        #expect(DropRelayLogic.normalizeBaseUrl("http://10.0.0.2:8080///") == "http://10.0.0.2:8080")
        #expect(DropRelayLogic.normalizeBaseUrl("ftp://example.com") == nil)
        #expect(DropRelayLogic.normalizeBaseUrl("") == nil)

        #expect(
            DropRelayLogic.fileWebseedUrl(baseUrl: "https://share.example.com/", path: "/高数/第1章 笔记.pdf")
                == "https://share.example.com/%E9%AB%98%E6%95%B0/%E7%AC%AC1%E7%AB%A0%20%E7%AC%94%E8%AE%B0.pdf"
        )
        #expect(
            DropRelayLogic.fileBrowserUrl(baseUrl: "https://share.example.com", path: "docs/a.pdf")
                == "https://share.example.com/#/docs/a.pdf"
        )
        #expect(
            DropRelayLogic.dirZipUrl(baseUrl: "https://share.example.com", dirPath: "docs/math")
                == "https://share.example.com/docs/math?zip=1"
        )
        #expect(
            DropRelayLogic.dirZipUrl(baseUrl: "https://share.example.com", dirPath: "")
                == "https://share.example.com/?zip=1"
        )
        #expect(
            DropRelayLogic.dropRoomUrl(baseUrl: "https://drop.example.com/", code: "ab34cd")
                == "https://drop.example.com/AB34CD"
        )
    }

    @Test("Drop code validation and extraction from text and URLs")
    func dropCodeExtraction() {
        #expect(DropRelayLogic.isValidDropCode("AB34CD"))
        #expect(!DropRelayLogic.isValidDropCode("AB01CD")) // 0 and 1 excluded
        #expect(!DropRelayLogic.isValidDropCode("SHORT"))

        #expect(DropRelayLogic.extractDropCode(from: "取件码：k7m9p2 快来领") == "K7M9P2")
        #expect(
            DropRelayLogic.extractDropCodeFromUrl(
                from: "分享给你 https://drop.example.com/x3y4z5 有效期两小时",
                dropBaseUrl: "https://drop.example.com"
            ) == "X3Y4Z5"
        )
        #expect(
            DropRelayLogic.extractDropCodeFromUrl(
                from: "普通链接 https://other.example.com/X3Y4Z5",
                dropBaseUrl: "https://drop.example.com"
            ) == nil
        )
    }

    @Test("Index parsing, directory listing, search, course matching, and file size formatting")
    func indexAndBrowseLogic() throws {
        let arrayJson = """
        [
          {"path":"高数/微积分上.pdf","size":1048576,"mtime":1000},
          {"path":"高数/习题/第一章.pdf","size":2048,"mtime":2000},
          {"path":"readme.txt","size":512,"mtime":1500}
        ]
        """
        let index = try DropRelayLogic.parseIndexJson(arrayJson)
        #expect(index.files.count == 3)
        #expect(index.generatedAt == 2000)
        #expect(index.totalBytes == 1048576 + 2048 + 512)

        let rootEntries = DropRelayLogic.listDirectory(files: index.files, currentDir: "")
        #expect(rootEntries.count == 2)
        if case .dir(let path, let name, let count, let bytes) = rootEntries[0] {
            #expect(path == "高数")
            #expect(name == "高数")
            #expect(count == 2)
            #expect(bytes == 1048576 + 2048)
        } else {
            Issue.record("Expected directory first")
        }
        if case .file(let f) = rootEntries[1] {
            #expect(f.displayName == "readme.txt")
        } else {
            Issue.record("Expected file second")
        }

        let subEntries = DropRelayLogic.listDirectory(files: index.files, currentDir: "高数")
        #expect(subEntries.count == 2)

        let search = DropRelayLogic.searchFiles(files: index.files, query: "高数 第一章")
        #expect(search.map(\.normalizedPath) == ["高数/习题/第一章.pdf"])

        let matched = DropRelayLogic.matchCourseResources(files: index.files, courseName: "微积分(上)")
        #expect(matched.map(\.normalizedPath) == ["高数/微积分上.pdf"])

        #expect(DropRelayLogic.formatFileSize(512) == "512 B")
        #expect(DropRelayLogic.formatFileSize(2048) == "2.0 KB")
        #expect(DropRelayLogic.formatFileSize(1024 * 1024) == "1.0 MB")
    }

    @Test("DropRelayClient fetches index, falls back to /app/manifest.json on 404, and handles 304/errors")
    func clientFetchAndFallback() async throws {
        let transport = MockDropRelayTransport { req in
            let path = req.url.path
            if path == "/api/index.json" {
                return HTTPResponse(statusCode: 404, url: req.url)
            }
            if path == "/app/manifest.json" {
                if req.firstHeader("If-None-Match") == "\"m1\"" {
                    return HTTPResponse(statusCode: 304, url: req.url)
                }
                let body = #"{"version":1,"generatedAt":123,"totalBytes":100,"files":[{"path":"a/b.pdf","size":100}]}"#
                return HTTPResponse(
                    statusCode: 200,
                    url: req.url,
                    headers: [HTTPHeader(name: "ETag", value: "\"m1\"")],
                    body: Data(body.utf8)
                )
            }
            return HTTPResponse(statusCode: 503, url: req.url)
        }

        let client = DropRelayClient(transport: transport)
        let res = try await client.fetchIndex(shareBaseUrl: "https://share.example.com")
        guard case .updated(let index, _, let etag, _) = res else {
            Issue.record("Expected .updated")
            return
        }
        #expect(index.files.count == 1)
        #expect(index.files[0].displayName == "b.pdf")
        #expect(etag == "\"m1\"")

        let notMod = try await client.fetchIndex(shareBaseUrl: "https://share.example.com", etag: etag)
        #expect(notMod == .notModified)

        do {
            _ = try await client.fetchIndex(shareBaseUrl: "not-a-url")
            Issue.record("Expected invalidUrl error")
        } catch let err as DropRelayError {
            #expect(err.kind == .invalidUrl)
        }
    }
}
