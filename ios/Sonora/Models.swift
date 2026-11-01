import Foundation

struct Track: Identifiable, Hashable {
    let id: String
    let title: String
    let artist: String
    let thumb: URL?
}
