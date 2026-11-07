// swift-tools-version: 6.0
import PackageDescription
import AppleProductTypes

let package = Package(
    name: "Sonora",
    platforms: [.iOS("16.0")],
    products: [
        .iOSApplication(
            name: "Sonora",
            targets: ["AppModule"],
            bundleIdentifier: "com.sonora.music",
            teamIdentifier: "",
            displayVersion: "0.80",
            bundleVersion: "1",
            appIcon: .placeholder(icon: .cloud),
            accentColor: .presetColor(.purple),
            supportedDeviceFamilies: [.phone, .pad],
            supportedInterfaceOrientations: [.portrait]
        )
    ],
    targets: [
        .executableTarget(
            name: "AppModule",
            path: ".",
            swiftSettings: [.swiftLanguageMode(.v5)]
        )
    ]
)
