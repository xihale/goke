import Foundation
import Testing
@testable import GokeCore

@Suite("Academic Notices Diff & Merge")
struct JxfwNoticesTests {
    @Test("JxfwNoticeDiff handles initial baseline, incremental diff, and internal-first merge")
    func noticeDiffAndMerge() {
        let p1 = JxfwNotice(id: "pub/1", title: "P1", source: .public)
        let p2 = JxfwNotice(id: "pub/2", title: "P2", source: .public)
        let p3 = JxfwNotice(id: "pub/3", title: "P3", source: .public)
        let i1 = JxfwNotice(id: "jxfw:1", title: "I1", source: .internal)

        let baseline = JxfwNoticeDiff.diffNew(incoming: [p1, p2], seenIds: [], isInitialBaseline: true)
        #expect(baseline.newNotices.isEmpty)
        #expect(baseline.updatedSeenIds == ["pub/1", "pub/2"])

        let next = JxfwNoticeDiff.diffNew(incoming: [p3, p1], seenIds: baseline.updatedSeenIds, isInitialBaseline: false)
        #expect(next.newNotices == [p3])
        #expect(next.updatedSeenIds == ["pub/3", "pub/1", "pub/2"])

        let merged = JxfwNoticeDiff.mergeSources(publicNotices: [p1, p2], internalNotices: [i1, p1], maxItems: 3)
        #expect(merged.map(\.id) == ["jxfw:1", "pub/1", "pub/2"])
    }

    @Test("UnavailableJxfwNoticesAdapterClient reports unsupported")
    func unavailableNoticesAdapter() async {
        let client = UnavailableCampusAdapterFactory().makeJxfwNoticesClient()
        #expect(!client.isSupported)
        do {
            _ = try await client.fetchPublicNotices()
            Issue.record("Expected JxfwError.unsupported")
        } catch let err as JxfwError {
            #expect(err.kind == .unsupported)
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }
}
