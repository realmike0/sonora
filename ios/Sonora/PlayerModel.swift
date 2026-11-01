import AVFoundation
import MediaPlayer
import SwiftUI

@MainActor
final class PlayerModel: ObservableObject {
    @Published var queue: [Track] = []
    @Published var index = 0
    @Published var isPlaying = false
    @Published var loading = false
    @Published var error: String?
    @Published var progress: Double = 0
    @Published var duration: Double = 0
    @Published var liked: [Track] = []

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?

    var current: Track? { queue.indices.contains(index) ? queue[index] : nil }

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] t in
            let now = t.seconds
            Task { @MainActor in
                guard let self else { return }
                let d = self.player.currentItem?.duration.seconds ?? 0
                self.duration = d.isFinite ? d : 0
                self.progress = self.duration > 0 ? now / self.duration : 0
                self.isPlaying = self.player.timeControlStatus == .playing
            }
        }
        let rc = MPRemoteCommandCenter.shared()
        rc.playCommand.addTarget { [weak self] _ in self?.player.play(); return .success }
        rc.pauseCommand.addTarget { [weak self] _ in self?.player.pause(); return .success }
        rc.nextTrackCommand.addTarget { [weak self] _ in self?.next(); return .success }
        rc.previousTrackCommand.addTarget { [weak self] _ in self?.previous(); return .success }
        liked = Self.loadLiked()
    }

    func play(_ tracks: [Track], at i: Int) {
        queue = tracks
        index = i
        Task { await load() }
    }

    func toggle() { isPlaying ? player.pause() : player.play() }
    func next() { guard index + 1 < queue.count else { return }; index += 1; Task { await load() } }
    func previous() { guard index > 0 else { return }; index -= 1; Task { await load() } }
    func seek(_ f: Double) { player.seek(to: CMTime(seconds: f * duration, preferredTimescale: 600)) }

    private func load() async {
        guard let t = current else { return }
        loading = true; error = nil
        do {
            let url = try await YouTubeMusic.streamURL(for: t.id)
            let item = AVPlayerItem(url: url)
            if let o = endObserver { NotificationCenter.default.removeObserver(o) }
            endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.next() }
            }
            player.replaceCurrentItem(with: item)
            player.play()
            MPNowPlayingInfoCenter.default().nowPlayingInfo = [
                MPMediaItemPropertyTitle: t.title, MPMediaItemPropertyArtist: t.artist
            ]
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    // Preferiti (salvati sul dispositivo)
    func isLiked(_ t: Track) -> Bool { liked.contains { $0.id == t.id } }
    func toggleLike(_ t: Track) {
        if let i = liked.firstIndex(where: { $0.id == t.id }) { liked.remove(at: i) } else { liked.insert(t, at: 0) }
        let arr = liked.map { ["id": $0.id, "title": $0.title, "artist": $0.artist, "thumb": $0.thumb?.absoluteString ?? ""] }
        UserDefaults.standard.set(arr, forKey: "liked")
    }
    private static func loadLiked() -> [Track] {
        (UserDefaults.standard.array(forKey: "liked") as? [[String: String]] ?? []).compactMap {
            guard let id = $0["id"], let title = $0["title"] else { return nil }
            return Track(id: id, title: title, artist: $0["artist"] ?? "", thumb: URL(string: $0["thumb"] ?? ""))
        }
    }
}
