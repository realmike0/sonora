import SwiftUI

@main
struct SonoraApp: App {
    @StateObject private var player = PlayerModel()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(player)
        }
    }
}
