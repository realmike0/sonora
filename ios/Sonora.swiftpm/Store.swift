import Foundation

/// Preferiti, ascolti recenti, Sonora ID e scelte dell'AI. Tutto salvato sul dispositivo.
@MainActor
final class Store: ObservableObject {
    @Published var liked: [Track] = []
    @Published var recent: [Track] = []
    @Published var picks: [Track] = []
    @Published var email: String?
    @Published var message: String?
    @Published var busy = false

    private var token: String?
    private var removed: Set<String> = []
    private let d = UserDefaults.standard

    var signedIn: Bool { token != nil }

    init() {
        liked = load("liked")
        recent = load("recent")
        picks = load("picks")
        token = d.string(forKey: "sid_token")
        email = token == nil ? nil : d.string(forKey: "sid_email")
        removed = Set(d.stringArray(forKey: "sid_removed") ?? [])
    }

    private func load(_ key: String) -> [Track] {
        guard let data = d.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Track].self, from: data)) ?? []
    }

    private func save(_ key: String, _ list: [Track]) {
        if let data = try? JSONEncoder().encode(list) { d.set(data, forKey: key) }
    }

    // MARK: Preferiti e cronologia

    func isLiked(_ t: Track) -> Bool { liked.contains { $0.id == t.id } }

    func toggleLike(_ t: Track) {
        if let i = liked.firstIndex(where: { $0.id == t.id }) {
            liked.remove(at: i)
            if signedIn { removed.insert(t.id); d.set(Array(removed), forKey: "sid_removed") }
        } else {
            liked.insert(t, at: 0)
            removed.remove(t.id)
        }
        save("liked", liked)
    }

    func recordPlay(_ t: Track) {
        recent.removeAll { $0.id == t.id }
        recent.insert(t, at: 0)
        if recent.count > 60 { recent.removeLast(recent.count - 60) }
        save("recent", recent)
    }

    // MARK: Sonora ID

    private func accept(_ mail: String, _ json: [String: Any]) throws {
        guard let t = json["token"] as? String, !t.isEmpty else { throw Server.Failure(message: "Risposta non valida") }
        token = t
        email = mail
        d.set(t, forKey: "sid_token")
        d.set(mail, forKey: "sid_email")
        removed = []
        d.removeObject(forKey: "sid_removed")
    }

    func register(_ mail: String, _ password: String) async {
        await run {
            let m = mail.trimmingCharacters(in: .whitespaces)
            try self.accept(m, try await Server.call("POST", "/v1/id/register", body: ["email": m, "password": password]))
            await self.syncNow()
        }
    }

    func login(_ mail: String, _ password: String) async {
        await run {
            let m = mail.trimmingCharacters(in: .whitespaces)
            try self.accept(m, try await Server.call("POST", "/v1/id/login", body: ["email": m, "password": password]))
            await self.syncNow()
        }
    }

    func logout() async {
        _ = try? await Server.call("POST", "/v1/id/logout", body: [:], token: token)
        token = nil; email = nil
        d.removeObject(forKey: "sid_token"); d.removeObject(forKey: "sid_email"); d.removeObject(forKey: "sid_removed")
        removed = []
        message = nil
    }

    func syncNow() async {
        guard let token else { return }
        await run {
            let likes = self.liked.prefix(1000).map { ["id": $0.id, "title": $0.title, "artist": $0.artist, "thumb": $0.thumb?.absoluteString ?? "", "dur": 0] as [String: Any] }
            let json = try await Server.call("POST", "/v1/id/sync", body: ["likes": likes, "removed": Array(self.removed)], token: token)
            let rows = json["likes"] as? [[String: Any]] ?? []
            self.liked = rows.compactMap {
                guard let id = $0["id"] as? String, let title = $0["title"] as? String else { return nil }
                let thumb = ($0["thumb"] as? String).flatMap { $0.isEmpty ? nil : URL(string: $0) }
                return Track(id: id, title: title, artist: $0["artist"] as? String ?? "", thumb: thumb)
            }
            self.removed = []
            self.d.removeObject(forKey: "sid_removed")
            self.save("liked", self.liked)
            self.message = "Sincronizzato"
        }
    }

    private func run(_ work: @escaping () async throws -> Void) async {
        busy = true
        message = nil
        do { try await work() } catch { message = error.localizedDescription }
        busy = false
    }

    // MARK: Scelti per te dall'AI (una volta al giorno)

    func refreshPicks() async {
        guard recent.count >= 5 else { return }
        let today = Calendar.current.startOfDay(for: Date()).timeIntervalSince1970
        if d.double(forKey: "picks_day") == today, !picks.isEmpty { return }
        let history = recent.prefix(40).map { "\($0.artist) - \($0.title)" }
        guard let json = try? await Server.call("POST", "/v1/ai/picks", body: ["history": history]),
              let list = json["picks"] as? [[String: Any]] else { return }
        let known = Set(recent.map(\.id))
        var found: [Track] = []
        for p in list.prefix(10) {
            guard let a = p["artist"] as? String, let t = p["title"] as? String,
                  let hit = try? await YouTubeMusic.search("\(a) \(t)").first, !known.contains(hit.id) else { continue }
            found.append(hit)
        }
        guard !found.isEmpty else { return }
        picks = found
        save("picks", found)
        d.set(today, forKey: "picks_day")
    }
}
