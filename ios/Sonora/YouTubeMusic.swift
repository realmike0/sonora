import Foundation

/// Ricerca su YouTube Music e risoluzione dello stream audio (API web non ufficiali: possono cambiare).
enum YouTubeMusic {
    enum Err: LocalizedError {
        case http(Int), noStream
        var errorDescription: String? {
            switch self {
            case .http(let c): return "Errore di rete (\(c))"
            case .noStream: return "Nessuno stream audio disponibile"
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
        if let code = (resp as? HTTPURLResponse)?.statusCode, code >= 400 { throw Err.http(code) }
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    static func search(_ query: String) async throws -> [Track] {
        let version = "1." + { let f = DateFormatter(); f.dateFormat = "yyyyMMdd"; f.locale = Locale(identifier: "en_US_POSIX"); return f.string(from: Date()) }() + ".01.00"
        let body: [String: Any] = [
            "context": ["client": ["clientName": "WEB_REMIX", "clientVersion": version, "hl": "it", "gl": "IT"]],
            "query": query,
            "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D" // solo brani
        ]
        let json = try await post("https://music.youtube.com/youtubei/v1/search?alt=json", body: body, headers: [
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
    static func streamURL(for id: String) async throws -> URL {
        let ua = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)"
        let body: [String: Any] = [
            "videoId": id,
            "context": ["client": [
                "clientName": "IOS", "clientVersion": "19.45.4", "deviceMake": "Apple",
                "deviceModel": "iPhone16,2", "osName": "iPhone", "osVersion": "18.1.0.22B83",
                "hl": "it", "gl": "IT"
            ]],
            "contentCheckOk": true, "racyCheckOk": true
        ]
        let json = try await post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", body: body,
                                  headers: ["User-Agent": ua, "X-YouTube-Client-Name": "5", "X-YouTube-Client-Version": "19.45.4"])
        let formats = ((json["streamingData"] as? [String: Any])?["adaptiveFormats"] as? [[String: Any]]) ?? []
        let audio = formats
            .filter { ($0["mimeType"] as? String)?.hasPrefix("audio/mp4") == true && $0["url"] is String }
            .sorted { ($0["bitrate"] as? Int ?? 0) > ($1["bitrate"] as? Int ?? 0) }
        guard let s = audio.first?["url"] as? String, let url = URL(string: s) else { throw Err.noStream }
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
