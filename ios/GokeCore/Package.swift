// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "GokeCore",
    platforms: [
        .iOS(.v15),
        .macOS(.v15),
    ],
    products: [
        .library(
            name: "GokeCoreModel",
            targets: ["GokeCoreModel"]
        ),
        .library(
            name: "GokeCore",
            targets: ["GokeCore"]
        ),
    ],
    targets: [
        .systemLibrary(
            name: "CSQLite",
            pkgConfig: "sqlite3",
            providers: [
                .apt(["libsqlite3-dev"]),
            ]
        ),
        .target(
            name: "GokeCoreModel",
            dependencies: [
                .target(name: "CSQLite", condition: .when(platforms: [.linux])),
            ],
            swiftSettings: [
                .swiftLanguageMode(.v6),
                .unsafeFlags([
                    "-Osize",
                    "-whole-module-optimization",
                    "-Xfrontend", "-disable-reflection-names",
                    "-Xllvm", "-enable-machine-outliner",
                ], .when(configuration: .release)),
            ]
        ),
        .target(
            name: "GokeCore",
            dependencies: [
                "GokeCoreModel",
            ],
            swiftSettings: [
                .swiftLanguageMode(.v6),
                .unsafeFlags([
                    "-Osize",
                    "-whole-module-optimization",
                    "-Xfrontend", "-disable-reflection-names",
                    "-Xllvm", "-enable-machine-outliner",
                ], .when(configuration: .release)),
            ]
        ),
        .testTarget(
            name: "GokeCoreTests",
            dependencies: ["GokeCore", "GokeCoreModel"],
            swiftSettings: [
                .swiftLanguageMode(.v6),
            ]
        ),
    ]
)
