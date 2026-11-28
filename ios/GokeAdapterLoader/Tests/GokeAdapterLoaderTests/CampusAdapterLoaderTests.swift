import Testing
import GokeCore
import GokeAdapterLoader

@Suite("CampusAdapterLoader")
struct CampusAdapterLoaderTests {
    @Test("makeFactory matches hasPrivateAdapters and returns consistent fallback in open-source mode")
    func factoryMatchesCompileMode() async {
        let factory = CampusAdapterLoader.makeFactory()
        #expect(factory.isSupported == CampusAdapterLoader.hasPrivateAdapters)

        let jxfw = factory.makeJxfwClient()
        let notices = factory.makeJxfwNoticesClient()
        let jwcwx = factory.makeJwcwxClient()
        let tzcs = factory.makeTzcsClient()
        let hotWater = factory.makeHotWaterBackend()

        #expect(jxfw.isSupported == CampusAdapterLoader.hasPrivateAdapters)
        #expect(notices.isSupported == CampusAdapterLoader.hasPrivateAdapters)
        #expect(jwcwx.isSupported == CampusAdapterLoader.hasPrivateAdapters)
        #expect(tzcs.isSupported == CampusAdapterLoader.hasPrivateAdapters)
        #expect(hotWater.isSupported == CampusAdapterLoader.hasPrivateAdapters)

        if !CampusAdapterLoader.hasPrivateAdapters {
            let defaults = factory.dropRelayDefaults()
            #expect(defaults.shareUrl.isEmpty)
            #expect(defaults.dropUrl.isEmpty)

            do {
                try await jxfw.login(username: "u", password: "p")
                Issue.record("Expected JxfwError.unsupported")
            } catch let err as JxfwError {
                #expect(err.kind == .unsupported)
            } catch {
                Issue.record("Unexpected error: \(error)")
            }

            do {
                _ = try await tzcs.fetchCaptchaChallenge()
                Issue.record("Expected TzcsError.unsupported")
            } catch let err as TzcsError {
                #expect(err.kind == .unsupported)
            } catch {
                Issue.record("Unexpected error: \(error)")
            }

            let sms = await hotWater.sendSmsCode(phone: "13800138000")
            if case .err(let code, _) = sms {
                #expect(code == .unsupported)
            } else {
                Issue.record("Expected .err(.unsupported)")
            }
        }
    }
}
