import Foundation

struct Track: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let artist: String
    let thumb: URL?
}
