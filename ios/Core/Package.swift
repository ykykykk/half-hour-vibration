// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "HalfHourCore", platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "HalfHourCore", targets: ["HalfHourCore"])],
    targets: [.target(name: "HalfHourCore"), .testTarget(name: "HalfHourCoreTests", dependencies: ["HalfHourCore"])])
