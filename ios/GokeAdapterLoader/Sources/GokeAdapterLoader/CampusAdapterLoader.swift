import GokeCore
#if GOKE_HAS_PRIVATE_ADAPTERS
import GokeAdapters
#endif

/// Bridge loader selecting private campus adapters when `adapters/ios/GokeAdapters` is present,
/// or falling back to `UnavailableCampusAdapterFactory` in open-source builds.
public enum CampusAdapterLoader {
    public static var hasPrivateAdapters: Bool {
        #if GOKE_HAS_PRIVATE_ADAPTERS
        return true
        #else
        return false
        #endif
    }

    public static func makeFactory() -> any CampusAdapterFactory {
        #if GOKE_HAS_PRIVATE_ADAPTERS
        return PrivateCampusAdapterFactory()
        #else
        return UnavailableCampusAdapterFactory()
        #endif
    }
}

public typealias CampusAdapterRegistry = CampusAdapterLoader
