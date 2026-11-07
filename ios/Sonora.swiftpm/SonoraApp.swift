import SwiftUI

@main
struct SonoraApp: App {
    @StateObject private var store: Store
    @StateObject private var player: PlayerModel

    init() {
        let s = Store()
        _store = StateObject(wrappedValue: s)
        _player = StateObject(wrappedValue: PlayerModel(store: s))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(player)
                .tint(Color(red: 0.81, green: 0.74, blue: 1.0))
        }
    }
}
