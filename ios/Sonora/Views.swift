import SwiftUI

struct RootView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var showPlayer = false

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView {
                SearchView().tabItem { Label("Ricerca", systemImage: "magnifyingglass") }
                LibraryView().tabItem { Label("Raccolta", systemImage: "music.note.list") }
            }
            if player.current != nil {
                MiniPlayer { showPlayer = true }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 58)
            }
        }
        .sheet(isPresented: $showPlayer) { FullPlayerView() }
    }
}

struct Cover: View {
    let url: URL?
    var body: some View {
        AsyncImage(url: url) { img in img.resizable().scaledToFill() } placeholder: { Color.gray.opacity(0.25) }
    }
}

struct TrackRow: View {
    let track: Track
    var body: some View {
        HStack(spacing: 12) {
            Cover(url: track.thumb).frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading) {
                Text(track.title).lineLimit(1)
                Text(track.artist).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
        }
    }
}

struct SearchView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var query = ""
    @State private var results: [Track] = []
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            List {
                if let error { Text(error).foregroundStyle(.secondary) }
                ForEach(Array(results.enumerated()), id: \.element.id) { i, t in
                    Button { player.play(results, at: i) } label: { TrackRow(track: t) }
                        .buttonStyle(.plain)
                }
                Color.clear.frame(height: 90).listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .overlay { if loading { ProgressView() } }
            .navigationTitle("Sonora")
            .searchableCompat(text: $query, onSubmit: run)
        }
        .navigationViewStyle(.stack)
    }

    private func run() {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        loading = true; error = nil
        Task {
            do { results = try await YouTubeMusic.search(q); if results.isEmpty { error = "Nessun risultato" } }
            catch { self.error = error.localizedDescription }
            loading = false
        }
    }
}

extension View {
    @ViewBuilder
    func searchableCompat(text: Binding<String>, onSubmit: @escaping () -> Void) -> some View {
        if #available(iOS 15.0, *) {
            self.searchable(text: text, prompt: "Brani e artisti").onSubmit(of: .search, onSubmit)
        } else { self }
    }
}

struct LibraryView: View {
    @EnvironmentObject var player: PlayerModel
    var body: some View {
        NavigationView {
            List {
                if player.liked.isEmpty { Text("I brani con il cuore compaiono qui").foregroundStyle(.secondary) }
                ForEach(Array(player.liked.enumerated()), id: \.element.id) { i, t in
                    Button { player.play(player.liked, at: i) } label: { TrackRow(track: t) }.buttonStyle(.plain)
                }
                Color.clear.frame(height: 90).listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .navigationTitle("Raccolta")
        }
        .navigationViewStyle(.stack)
    }
}

struct MiniPlayer: View {
    @EnvironmentObject var player: PlayerModel
    let open: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Cover(url: player.current?.thumb).frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading) {
                Text(player.current?.title ?? "").lineLimit(1).font(.subheadline.bold())
                Text(player.current?.artist ?? "").lineLimit(1).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button { player.toggle() } label: {
                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title3)
            }
        }
        .padding(10)
        .sonoraGlass(Capsule())
        .contentShape(Capsule())
        .onTapGesture(perform: open)
    }
}

struct FullPlayerView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var dragging: Double?

    var body: some View {
        ZStack {
            Cover(url: player.current?.thumb).blur(radius: 60).overlay(Color.black.opacity(0.45)).ignoresSafeArea()
            VStack(spacing: 22) {
                Cover(url: player.current?.thumb)
                    .aspectRatio(1, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 28))
                    .shadow(radius: 20)
                    .scaleEffect(player.isPlaying ? 1 : 0.9)
                    .animation(.spring(response: 0.5, dampingFraction: 0.6), value: player.isPlaying)
                VStack(spacing: 4) {
                    Text(player.current?.title ?? "").font(.title2.bold()).lineLimit(1)
                    Text(player.current?.artist ?? "").foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                }
                Slider(value: Binding(get: { dragging ?? player.progress }, set: { dragging = $0 }),
                       in: 0...1) { editing in
                    if !editing, let d = dragging { player.seek(d); dragging = nil }
                }
                if let e = player.error { Text(e).font(.footnote).foregroundStyle(.white.opacity(0.7)) }
                HStack(spacing: 28) {
                    ctl("backward.fill", 56) { player.previous() }
                    ctl(player.isPlaying ? "pause.fill" : "play.fill", 78) { player.toggle() }
                    ctl("forward.fill", 56) { player.next() }
                }
                if let t = player.current {
                    Button { player.toggleLike(t) } label: {
                        Image(systemName: player.isLiked(t) ? "heart.fill" : "heart").font(.title2)
                    }
                }
            }
            .foregroundStyle(.white)
            .padding(28)
        }
    }

    private func ctl(_ icon: String, _ size: CGFloat, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.title2).frame(width: size, height: size)
                .sonoraGlass(Circle())
        }
    }
}
