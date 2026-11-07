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
    @Published var position: Double = 0
    @Published var duration: Double = 0
    @Published var shuffleOn = false
    @Published var lyrics: [LyricLine] = []

    let store: Store
    private let player = AVPlayer()
    let web = WebEngine()
    private var usingWeb = false
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var lyricsTask: Task<Void, Never>?

    var current: Track? { queue.indices.contains(index) ? queue[index] : nil }
    var progress: Double { duration > 0 ? position / duration : 0 }

    /// Riga dei testi attualmente cantata (solo testi sincronizzati).
    var currentLine: Int? {
        guard lyrics.first?.time ?? -1 >= 0 else { return nil }
        return lyrics.lastIndex { $0.time <= position + 0.3 }
    }

    init(store: Store) {
        self.store = store
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.4, preferredTimescale: 600), queue: .main) { [weak self] t in
            let now = t.seconds
            Task { @MainActor in
                guard let self, !self.usingWeb else { return }
                let d = self.player.currentItem?.duration.seconds ?? 0
                self.duration = d.isFinite ? d : 0
                self.position = now.isFinite ? now : 0
                self.isPlaying = self.player.timeControlStatus == .playing
            }
        }
        let rc = MPRemoteCommandCenter.shared()
        rc.playCommand.addTarget { [weak self] _ in Task { @MainActor in if self?.isPlaying == false { self?.toggle() } }; return .success }
        rc.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in if self?.isPlaying == true { self?.toggle() } }; return .success }
        rc.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.next() }; return .success }
        rc.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.previous() }; return .success }
        rc.changePlaybackPositionCommand.addTarget { [weak self] e in
            if let e = e as? MPChangePlaybackPositionCommandEvent {
                Task { @MainActor in
                    guard let self, self.duration > 0 else { return }
                    self.seek(e.positionTime / self.duration)
                }
            }
            return .success
        }
        web.onState = { [weak self] s in
            guard let self, self.usingWeb else { return }
            if s == 1 { self.isPlaying = true; self.loading = false }
            else if s == 2 { self.isPlaying = false }
            else if s == 0 { self.next() }
        }
        web.onTime = { [weak self] c, d in
            guard let self, self.usingWeb else { return }
            self.position = c; self.duration = d
        }
        web.onError = { [weak self] code in
            guard let self, self.usingWeb else { return }
            self.loading = false
            self.error = code == 101 || code == 150 || code == 153
                ? "Questo brano non può essere riprodotto fuori da YouTube (blocco del proprietario)."
                : "Brano non riproducibile (errore \(code))."
        }
    }

    func play(_ tracks: [Track], at i: Int) {
        queue = tracks
        index = i
        Task { await load() }
    }

    func toggle() {
        if usingWeb { isPlaying ? web.pause() : web.play() }
        else { isPlaying ? player.pause() : player.play() }
    }

    func next() {
        guard !queue.isEmpty else { return }
        if shuffleOn && queue.count > 1 {
            var n = index
            while n == index { n = Int.random(in: 0..<queue.count) }
            index = n
        } else if index + 1 < queue.count {
            index += 1
        } else { return }
        Task { await load() }
    }

    func previous() {
        if position > 4 { seek(0); return }
        guard index > 0 else { return }
        index -= 1
        Task { await load() }
    }

    func seek(_ f: Double) {
        if usingWeb { web.seek(f * duration) }
        else { player.seek(to: CMTime(seconds: f * duration, preferredTimescale: 600)) }
    }

    private func load() async {
        guard let t = current else { return }
        loading = true; error = nil; position = 0; lyrics = []
        if usingWeb { web.pause() }
        usingWeb = false
        store.recordPlay(t)
        lyricsTask?.cancel()
        lyricsTask = Task { [weak self] in
            let l = await Lyrics.fetch(title: t.title, artist: t.artist)
            if !Task.isCancelled { self?.lyrics = l }
        }
        let found = await YouTubeMusic.streamCandidates(for: t.id)
        let candidates = found.sources
        if current?.id != t.id { return }
        var report = found.notes
        var started = false
        for src in candidates {
            let item = AVPlayerItem(url: src.url)
            if let o = endObserver { NotificationCenter.default.removeObserver(o) }
            endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.next() }
            }
            player.replaceCurrentItem(with: item)
            player.play()
            if await waitReady(item) {
                started = true
                break
            }
            var why = item.error?.localizedDescription ?? "non parte"
            if let log = item.errorLog()?.events.last { why += " (HTTP \(log.errorStatusCode))" }
            report.append("\(src.label): \(why)")
            if current?.id != t.id { return }
        }
        if started {
            var info: [String: Any] = [MPMediaItemPropertyTitle: t.title, MPMediaItemPropertyArtist: t.artist]
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            if let thumb = t.thumb, let (data, _) = try? await URLSession.shared.data(from: thumb), let img = UIImage(data: data) {
                info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
                MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            }
        } else {
            // YouTube blocca l'audio diretto: si usa il lettore incorporato.
            player.pause()
            usingWeb = true
            isPlaying = false
            web.load(t.id)
            MPNowPlayingInfoCenter.default().nowPlayingInfo = [MPMediaItemPropertyTitle: t.title, MPMediaItemPropertyArtist: t.artist]
            return
        }
        loading = false
    }

    /// Aspetta fino a 12 secondi che lo stream sia pronto (o fallito).
    private func waitReady(_ item: AVPlayerItem) async -> Bool {
        for _ in 0..<60 {
            switch item.status {
            case .readyToPlay: return true
            case .failed: return false
            default: break
            }
            try? await Task.sleep(nanoseconds: 200_000_000)
        }
        return false
    }
}
