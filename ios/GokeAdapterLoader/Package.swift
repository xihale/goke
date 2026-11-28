// swift-tools-version: 6.0
import PackageDescription
import Foundation

let adapterManifest = URL(fileURLWithPath: Context.packageDirectory)
    .deletingLastPathComponent() // ios/
    .deletingLastPathComponent() // repo root
    .appendingPathComponent("adapters/ios/GokeAdapters/Package.swift")
    .path

let hasPrivateAdapters = FileManager.default.fileExists(atPath: adapterManifest)

let package = Package(
    name: "GokeAdapterLoader",
    platforms: [
        .iOS(.v17),
        .macOS(.v15),
    ],
    products: [
        .library(
            name: "GokeAdapterLoader",
            targets: ["GokeAdapterLoader"]
        ),
    ],
    dependencies: [
        .package(path: "../GokeCore"),
    ] + (hasPrivateAdapters ? [.package(path: "../../adapters/ios/GokeAdapters")] : []),
    targets: [
        .target(
            name: "GokeAdapterLoader",
            dependencies: [
                .product(name: "GokeCore", package: "GokeCore"),
            ] + (hasPrivateAdapters ? [.product(name: "GokeAdapters", package: "GokeAdapters")] : []),
            swiftSettings: [
                .swiftLanguageMode(.v6),
            ] + (hasPrivateAdapters ? [.define("GOKE_HAS_PRIVATE_ADAPTERS")] : [])
        ),
        .testTarget(
            name: "GokeAdapterLoaderTests",
            dependencies: [
                "GokeAdapterLoader",
                .product(name: "GokeCore", package: "GokeCore"),
            ],
            swiftSettings: [
                .swiftLanguageMode(.v6),
            ]
        ),
    ]
)
