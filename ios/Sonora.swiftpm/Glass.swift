import SwiftUI

/// iOS 26+: Liquid Glass vero. Prima (o con un SDK più vecchio): materiale sfocato standard.
/// `canImport(FoundationModels)` è vero solo con l'SDK di iOS 26, dove esiste glassEffect.
extension View {
    @ViewBuilder
    func sonoraGlass<S: Shape>(_ shape: S) -> some View {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular.interactive(), in: shape)
        } else {
            self.background(.ultraThinMaterial, in: shape)
        }
        #else
        self.background(.ultraThinMaterial, in: shape)
        #endif
    }
}
