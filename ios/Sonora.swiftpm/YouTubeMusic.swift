import Foundation

/// Ricerca su YouTube Music e risoluzione dello stream audio (API web non ufficiali: possono cambiare).
enum YouTubeMusic {
    enum Err: LocalizedError {
        case http(Int), noStream, custom(String)
        var errorDescription: String? {
            switch self {
            case .http(let c): return "Errore di rete (\(c))"
            case .noStream: return "Nessuno stream audio disponibile"
            case .custom(let m): return m
            }
        }
    }

    private static func post(_ url: String, body: [String: Any], headers: [String: String]) async throws -> [String: Any] {
        var req = URLRequest(url: URL(string: url)!)
        req.httpMethod = "POST"
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        headers.forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let (data, resp) = try await URLSession.shared.data(for: req)
        if let code = (resp as? HTTPURLResponse)?.statusCode, code >= 400 {
            let msg = (((try? JSONSerialization.jsonObject(with: data)) as? [String: Any])?["error"] as? [String: Any])?["message"] as? String
            throw msg.map { Err.custom("HTTP \(code): \($0)") } ?? Err.http(code)
        }
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    /// Prima la ricerca diretta; se YouTube risponde con un errore, si passa dal server di Sonora.
    static func search(_ query: String) async throws -> [Track] {
        do { return try await searchDirect(query) } catch {
            if let viaServer = try? await searchViaServer(query), !viaServer.isEmpty { return viaServer }
            throw error
        }
    }

    private static func searchViaServer(_ query: String) async throws -> [Track] {
        var c = URLComponents(string: Server.base + "/v1/web/search")!
        c.queryItems = [URLQueryItem(name: "q", value: query)]
        let (data, _) = try await URLSession.shared.data(from: c.url!)
        let items = ((try? JSONSerialization.jsonObject(with: data)) as? [String: Any])?["items"] as? [[String: Any]] ?? []
        return items.compactMap {
            guard let id = $0["id"] as? String, let title = $0["title"] as? String else { return nil }
            return Track(id: id, title: title, artist: $0["artist"] as? String ?? "", thumb: ($0["thumb"] as? String).flatMap(URL.init))
        }
    }

    private static func searchDirect(_ query: String) async throws -> [Track] {
        let version = "1.20250310.01.00"
        let body: [String: Any] = [
            "context": ["client": ["clientName": "WEB_REMIX", "clientVersion": version, "hl": "it", "gl": "IT"]],
            "query": query,
            "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D" // solo brani
        ]
        let json = try await post("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", body: body, headers: [
            "Origin": "https://music.youtube.com", "Cookie": "SOCS=CAI",
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"
        ])
        var items: [[String: Any]] = []
        collect(json, "musicResponsiveListItemRenderer", &items)
        var seen = Set<String>()
        return items.compactMap { item in
            guard let id = firstString(item, "videoId"), seen.insert(id).inserted else { return nil }
            let cols = item["flexColumns"] as? [[String: Any]] ?? []
            func runs(_ i: Int) -> [[String: Any]] {
                let r = cols.indices.contains(i) ? cols[i]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any] : nil
                return ((r?["text"] as? [String: Any])?["runs"] as? [[String: Any]]) ?? []
            }
            guard let title = runs(0).first?["text"] as? String else { return nil }
            let artist = (runs(1).first?["text"] as? String) ?? ""
            var thumbs: [[String: Any]] = []
            collect(item, "thumbnails", &thumbs)
            let url = (item["thumbnail"] as? [String: Any])
                .flatMap { ($0["musicThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any] }
                .flatMap { $0["thumbnails"] as? [[String: Any]] }?.last?["url"] as? String
            let big = url.map { $0.replacingOccurrences(of: #"=w\d+-h\d+"#, with: "=w512-h512", options: .regularExpression) }
            return Track(id: id, title: title, artist: artist, thumb: big.flatMap(URL.init))
        }
    }

    /// URL diretto del miglior stream audio (client iOS di YouTube).
    struct Source {
        let label: String
        let url: URL
    }

    /// Tutti gli indirizzi da provare, in ordine: diretti da YouTube, poi (nessun altro).
    /// `notes` spiega perché un metodo non ha dato nulla (utile per la diagnostica).
    static func streamCandidates(for id: String) async -> (sources: [Source], notes: [String]) {
        var list: [Source] = []
        var notes: [String] = []
        for c in clients {
            do { list.append(Source(label: c.name, url: try await streamDirect(id, client: c))) }
            catch { notes.append("\(c.name): \(error.localizedDescription)") }
        }
        return (list, notes)
    }

    static func streamURL(for id: String) async throws -> URL {
        guard let u = await streamCandidates(for: id).sources.first?.url else { throw Err.noStream }
        return u
    }

    private struct PlayerClient {
        let name: String, version: String, id: String, ua: String
        let extra: [String: Any]
    }

    /// Client "app" di YouTube: ognuno restituisce indirizzi audio diretti. Si provano uno dopo l'altro.
    private static let clients: [PlayerClient] = [
        PlayerClient(name: "ANDROID_VR", version: "1.65.10", id: "28",
                     ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                     extra: ["deviceMake": "Oculus", "deviceModel": "Quest 3", "osName": "Android", "osVersion": "12L", "androidSdkVersion": 32]),
        PlayerClient(name: "IOS", version: "20.10.4", id: "5",
                     ua: "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                     extra: ["deviceMake": "Apple", "deviceModel": "iPhone16,2", "osName": "iOS", "osVersion": "18.3.2.22D82"]),
    ]

    private static func streamDirect(_ id: String, client c: PlayerClient) async throws -> URL {
        var client: [String: Any] = ["clientName": c.name, "clientVersion": c.version, "hl": "en", "gl": "IT"]
        c.extra.forEach { client[$0] = $1 }
        let body: [String: Any] = ["videoId": id, "context": ["client": client], "contentCheckOk": true, "racyCheckOk": true]
        let json = try await post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", body: body,
                                  headers: ["User-Agent": c.ua, "X-YouTube-Client-Name": c.id, "X-YouTube-Client-Version": c.version])
        let formats = ((json["streamingData"] as? [String: Any])?["adaptiveFormats"] as? [[String: Any]]) ?? []
        let audio = formats
            .filter { ($0["mimeType"] as? String)?.hasPrefix("audio/mp4") == true && $0["url"] is String }
            .sorted { ($0["bitrate"] as? Int ?? 0) > ($1["bitrate"] as? Int ?? 0) }
        guard let s = audio.first?["url"] as? String, let url = URL(string: s) else {
            let ps = json["playabilityStatus"] as? [String: Any]
            let status = ps?["status"] as? String ?? "?"
            let reason = (ps?["reason"] as? String) ?? ""
            let cipher = formats.contains { $0["signatureCipher"] != nil }
            throw Err.custom("\(status) \(reason) \(formats.isEmpty ? "(nessun formato)" : (cipher ? "(formati protetti)" : "(senza audio)"))")
        }
        return url
    }

    private static func collect(_ node: Any?, _ key: String, _ out: inout [[String: Any]]) {
        if let d = node as? [String: Any] {
            for (k, v) in d {
                if k == key, let o = v as? [String: Any] { out.append(o) } else { collect(v, key, &out) }
            }
        } else if let a = node as? [Any] { a.forEach { collect($0, key, &out) } }
    }

    private static func firstString(_ node: Any?, _ key: String) -> String? {
        if let d = node as? [String: Any] {
            if let s = d[key] as? String, !s.isEmpty { return s }
            for v in d.values { if let s = firstString(v, key) { return s } }
        } else if let a = node as? [Any] {
            for v in a { if let s = firstString(v, key) { return s } }
        }
        return nil
    }
}
