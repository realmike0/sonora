import SwiftUI

// MARK: - Base

struct RootView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var showPlayer = false

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView {
                HomeView().tabItem { Label("Home", systemImage: "house.fill") }
                SearchView().tabItem { Label("Ricerca", systemImage: "magnifyingglass") }
                LibraryView().tabItem { Label("Raccolta", systemImage: "music.note.list") }
                ProfileView().tabItem { Label("Profilo", systemImage: "person.crop.circle") }
            }
            if player.current != nil {
                MiniPlayer { showPlayer = true }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 58)
            }
        }
        .background(WebHost(view: player.web.view).frame(width: 2, height: 2).opacity(0.02), alignment: .topLeading)
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
            Cover(url: track.thumb).frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading) {
                Text(track.title).lineLimit(1)
                Text(track.artist).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
        }
        .contentShape(Rectangle())
    }
}

/// Fila orizzontale di copertine (Home).
struct Shelf: View {
    @EnvironmentObject var player: PlayerModel
    let title: String
    let tracks: [Track]

    var body: some View {
        if !tracks.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text(title).font(.title2.bold()).padding(.horizontal, 20)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(Array(tracks.enumerated()), id: \.element.id) { i, t in
                            Button { player.play(tracks, at: i) } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    Cover(url: t.thumb).frame(width: 150, height: 150).clipShape(RoundedRectangle(cornerRadius: 24))
                                    Text(t.title).font(.subheadline.bold()).lineLimit(1)
                                    Text(t.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                                .frame(width: 150, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }
}

// MARK: - Home

struct HomeView: View {
    @EnvironmentObject var store: Store

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    Shelf(title: "Scelti per te dall'AI", tracks: store.picks)
                    Shelf(title: "Ascoltati di recente", tracks: Array(store.recent.prefix(15)))
                    Shelf(title: "I tuoi preferiti", tracks: Array(store.liked.prefix(15)))
                    if store.recent.isEmpty && store.liked.isEmpty {
                        Text("Cerca un brano per iniziare: qui compariranno i tuoi ascolti.")
                            .foregroundStyle(.secondary).padding(.horizontal, 20)
                    }
                    Color.clear.frame(height: 110)
                }
                .padding(.top, 8)
            }
            .navigationTitle("Sonora")
            .task { await store.refreshPicks() }
        }
    }
}

// MARK: - Ricerca

struct SearchView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var query = ""
    @State private var results: [Track] = []
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
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
            .navigationTitle("Ricerca")
            .searchable(text: $query, prompt: "Brani e artisti")
            .task(id: query) { await run() }
        }
    }

    private func run() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { results = []; error = nil; return }
        try? await Task.sleep(nanoseconds: 450_000_000)
        if Task.isCancelled { return }
        loading = true; error = nil
        do {
            let r = try await YouTubeMusic.search(q)
            if Task.isCancelled { return }
            results = r
            if r.isEmpty { error = "Nessun risultato" }
        } catch {
            if !Task.isCancelled { self.error = error.localizedDescription }
        }
        loading = false
    }
}

// MARK: - Raccolta

struct LibraryView: View {
    @EnvironmentObject var player: PlayerModel
    @EnvironmentObject var store: Store

    var body: some View {
        NavigationStack {
            List {
                if store.liked.isEmpty { Text("I brani con il cuore compaiono qui").foregroundStyle(.secondary) }
                ForEach(Array(store.liked.enumerated()), id: \.element.id) { i, t in
                    Button { player.play(store.liked, at: i) } label: { TrackRow(track: t) }
                        .buttonStyle(.plain)
                        .swipeActions { Button("Rimuovi", role: .destructive) { store.toggleLike(t) } }
                }
                Color.clear.frame(height: 90).listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .navigationTitle("Raccolta")
        }
    }
}

// MARK: - Profilo / Sonora ID

struct ProfileView: View {
    @EnvironmentObject var store: Store
    @State private var mail = ""
    @State private var password = ""

    var body: some View {
        NavigationStack {
            Form {
                if store.signedIn {
                    Section("Sonora ID") {
                        Text(store.email ?? "")
                        Button("Sincronizza ora") { Task { await store.syncNow() } }.disabled(store.busy)
                        Button("Esci", role: .destructive) { Task { await store.logout() } }
                    }
                } else {
                    Section("Sonora ID") {
                        TextField("Email", text: $mail)
                            .textInputAutocapitalization(.never).keyboardType(.emailAddress).autocorrectionDisabled()
                        SecureField("Password (almeno 8 caratteri)", text: $password)
                        Button("Accedi") { Task { await store.login(mail, password) } }.disabled(store.busy || mail.isEmpty || password.isEmpty)
                        Button("Crea Sonora ID") { Task { await store.register(mail, password) } }.disabled(store.busy || mail.isEmpty || password.count < 8)
                    }
                }
                if let m = store.message { Section { Text(m).foregroundStyle(.secondary) } }
                Section {
                    Text("I preferiti si sincronizzano con l'app Android. Le dediche sono disponibili solo su Android.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Profilo")
        }
    }
}

// MARK: - Player

struct MiniPlayer: View {
    @EnvironmentObject var player: PlayerModel
    let open: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Cover(url: player.current?.thumb).frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading) {
                Text(player.current?.title ?? "").lineLimit(1).font(.subheadline.bold())
                Text(player.loading ? "Caricamento…" : (player.error != nil ? "Non riproducibile" : (player.current?.artist ?? ""))).lineLimit(1).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button { player.toggle() } label: {
                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title3)
            }
            Button { player.next() } label: { Image(systemName: "forward.fill").font(.title3) }
        }
        .padding(10)
        .sonoraGlass(Capsule())
        .contentShape(Capsule())
        .onTapGesture(perform: open)
    }
}

struct FullPlayerView: View {
    @EnvironmentObject var player: PlayerModel
    @EnvironmentObject var store: Store
    @State private var dragging: Double?
    @State private var showLyrics = false

    var body: some View {
        ZStack {
            Cover(url: player.current?.thumb).blur(radius: 60).overlay(Color.black.opacity(0.5)).ignoresSafeArea()
            VStack(spacing: 18) {
                if showLyrics {
                    LyricsView()
                } else {
                    Cover(url: player.current?.thumb)
                        .aspectRatio(1, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 34))
                        .shadow(radius: 20)
                        .scaleEffect(player.isPlaying ? 1 : 0.9)
                        .animation(.spring(response: 0.5, dampingFraction: 0.6), value: player.isPlaying)
                    Spacer(minLength: 0)
                }
                VStack(spacing: 4) {
                    Text(player.current?.title ?? "").font(.title2.bold()).lineLimit(1)
                    Text(player.current?.artist ?? "").foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                }
                Slider(value: Binding(get: { dragging ?? player.progress }, set: { dragging = $0 }), in: 0...1) { editing in
                    if !editing, let d = dragging { player.seek(d); dragging = nil }
                }
                HStack {
                    Text(time(player.position)); Spacer(); Text(time(player.duration))
                }
                .font(.caption).foregroundStyle(.white.opacity(0.7))
                if let e = player.error { Text(e).font(.caption2).foregroundStyle(.white.opacity(0.8)).multilineTextAlignment(.center).lineLimit(8) }
                HStack(spacing: 22) {
                    small(player.shuffleOn ? "shuffle.circle.fill" : "shuffle") { player.shuffleOn.toggle() }
                    ctl("backward.fill", 56) { player.previous() }
                    ctl(player.isPlaying ? "pause.fill" : "play.fill", 78) { player.toggle() }
                    ctl("forward.fill", 56) { player.next() }
                    if let t = player.current {
                        small(store.isLiked(t) ? "heart.fill" : "heart") { store.toggleLike(t) }
                    }
                }
                Button { withAnimation { showLyrics.toggle() } } label: {
                    Label(showLyrics ? "Copertina" : "Testi", systemImage: showLyrics ? "photo" : "quote.bubble")
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .sonoraGlass(Capsule())
                }
            }
            .foregroundStyle(.white)
            .padding(28)
        }
    }

    private func time(_ s: Double) -> String {
        let t = Int(max(0, s))
        return "\(t / 60):" + String(format: "%02d", t % 60)
    }

    private func small(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.title2).frame(width: 36, height: 36) }
    }

    private func ctl(_ icon: String, _ size: CGFloat, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.title2).frame(width: size, height: size)
                .sonoraGlass(Circle())
        }
    }
}

struct LyricsView: View {
    @EnvironmentObject var player: PlayerModel

    var body: some View {
        if player.lyrics.isEmpty {
            Text("Testo non disponibile").foregroundStyle(.white.opacity(0.7)).frame(maxHeight: .infinity)
        } else {
            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(Array(player.lyrics.enumerated()), id: \.element.id) { i, l in
                            Text(l.text.isEmpty ? "♪" : l.text)
                                .font(.title3.bold())
                                .opacity(player.currentLine == nil || player.currentLine == i ? 1 : 0.35)
                                .id(i)
                                .onTapGesture { if l.time >= 0, player.duration > 0 { player.seek(l.time / player.duration) } }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 40)
                }
                .onChange(of: player.currentLine) { line in
                    if let line { withAnimation { proxy.scrollTo(line, anchor: .center) } }
                }
            }
        }
    }
}
