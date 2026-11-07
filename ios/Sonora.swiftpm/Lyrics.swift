import Foundation

struct LyricLine: Identifiable {
    let id = UUID()
    let time: Double   // -1 = testo non sincronizzato
    let text: String
}

/// Testi da LRCLIB (sincronizzati quando disponibili).
enum Lyrics {
    static func fetch(title: String, artist: String) async -> [LyricLine] {
        let clean = title.replacingOccurrences(of: #"\s*[\(\[].*?[\)\]]"#, with: "", options: .regularExpression)
        var c = URLComponents(string: "https://lrclib.net/api/get")!
        c.queryItems = [URLQueryItem(name: "track_name", value: clean), URLQueryItem(name: "artist_name", value: artist)]
        guard let url = c.url,
              let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return [] }
        if let synced = json["syncedLyrics"] as? String, !synced.isEmpty {
            let lines = parse(synced)
            if !lines.isEmpty { return lines }
        }
        if let plain = json["plainLyrics"] as? String {
            return plain.split(separator: "\n", omittingEmptySubsequences: false).map { LyricLine(time: -1, text: String($0)) }
        }
        return []
    }

    private static func parse(_ s: String) -> [LyricLine] {
        var out: [LyricLine] = []
        for raw in s.split(separator: "\n") {
            let line = String(raw)
            guard line.hasPrefix("["), let close = line.firstIndex(of: "]") else { continue }
            let stamp = line[line.index(after: line.startIndex)..<close].split(separator: ":")
            guard stamp.count == 2, let m = Double(stamp[0]), let sec = Double(stamp[1]) else { continue }
            let text = String(line[line.index(after: close)...]).trimmingCharacters(in: .whitespaces)
            out.append(LyricLine(time: m * 60 + sec, text: text))
        }
        return out
    }
}
