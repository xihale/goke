// swift-tools-version: 6.0
import PackageDescription

// Root SwiftPM manifest for local Linux `xtool` builds (`ios/xtool.yml`).
// Authoritative macOS Xcode / GitHub CI `.ipa` + WidgetKit builds use `ios/project.yml` (XcodeGen).
let package = Package(
    name: "Goke",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(
            name: "GokeApp",
            targets: ["GokeApp"]
        ),
        .library(
            name: "GokeWidgetExtension",
            targets: ["GokeWidgetExtension"]
        ),
    ],
    dependencies: [
        .package(path: "GokeCore"),
        .package(path: "GokeShared"),
        .package(path: "GokeAdapterLoader"),
    ],
    targets: [
        .target(
            name: "GokeApp",
            dependencies: [
                .product(name: "GokeCore", package: "GokeCore"),
                .product(name: "GokeShared", package: "GokeShared"),
                .product(name: "GokeAdapterLoader", package: "GokeAdapterLoader"),
            ],
            path: "GokeApp/Sources",
            swiftSettings: [
                .swiftLanguageMode(.v6),
                .unsafeFlags([
                    "-Osize",
                    "-whole-module-optimization",
                    "-lto=llvm-full",
                    "-Xfrontend", "-disable-reflection-names",
                    "-Xllvm", "-enable-machine-outliner",
                ], .when(configuration: .release)),
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-Xlinker", "-framework", "-Xlinker", "CoreFoundation",
                    "-Xlinker", "-framework", "-Xlinker", "Foundation",
                    "-Xlinker", "-framework", "-Xlinker", "UIKit",
                    "-Xlinker", "-framework", "-Xlinker", "CoreTransferable",
                    "-Xlinker", "-framework", "-Xlinker", "SwiftUI",
                    "-Xlinker", "-no_implicit_dylibs",
                ]),
                .unsafeFlags([
                    "-Xlinker", "-dead_strip",
                    "-Xlinker", "-dead_strip_dylibs",
                    "-Xlinker", "--icf=all",
                    "-Xlinker", "--deduplicate-strings",
                    "-Xlinker", "-mllvm", "-Xlinker", "-enable-machine-outliner",
                    "-Xlinker", "-S",
                    "-Xlinker", "-x",
                ], .when(configuration: .release)),
            ]
        ),
        .target(
            name: "GokeWidgetExtension",
            dependencies: [
                .product(name: "GokeCoreModel", package: "GokeCore"),
                .product(name: "GokeShared", package: "GokeShared"),
            ],
            path: "GokeWidgetExtension/Sources",
            swiftSettings: [
                .swiftLanguageMode(.v6),
                .unsafeFlags([
                    "-Osize",
                    "-whole-module-optimization",
                    "-lto=llvm-full",
                    "-Xfrontend", "-disable-reflection-names",
                    "-Xllvm", "-enable-machine-outliner",
                ], .when(configuration: .release)),
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-Xlinker", "-u", "-Xlinker", "_main",
                    "-Xlinker", "-framework", "-Xlinker", "CoreFoundation",
                    "-Xlinker", "-framework", "-Xlinker", "Foundation",
                    "-Xlinker", "-framework", "-Xlinker", "UIKit",
                    "-Xlinker", "-framework", "-Xlinker", "CoreTransferable",
                    "-Xlinker", "-framework", "-Xlinker", "SwiftUI",
                    "-Xlinker", "-no_implicit_dylibs",
                ]),
                .unsafeFlags([
                    "-Xlinker", "-dead_strip",
                    "-Xlinker", "-dead_strip_dylibs",
                    "-Xlinker", "--icf=all",
                    "-Xlinker", "--deduplicate-strings",
                    "-Xlinker", "-mllvm", "-Xlinker", "-enable-machine-outliner",
                    "-Xlinker", "-S",
                    "-Xlinker", "-x",
                ], .when(configuration: .release)),
            ]
        ),
    ]
)
