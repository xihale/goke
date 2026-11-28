// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "GokeShared",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(
            name: "GokeShared",
            targets: ["GokeShared"]
        ),
    ],
    dependencies: [
        .package(path: "../GokeCore"),
    ],
    targets: [
        .target(
            name: "GokeShared",
            dependencies: [
                .product(name: "GokeCoreModel", package: "GokeCore"),
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
    ]
)
