import Foundation

/// Server di Sonora (Cloudflare Worker): Sonora ID e suggerimenti AI.
enum Server {
    static let base = "https://sonora-wrapped.sonorawrapped.workers.dev"

    struct Failure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    static func call(_ method: String, _ path: String, body: [String: Any]? = nil, token: String? = nil) async throws -> [String: Any] {
        var req = URLRequest(url: URL(string: base + path)!)
        req.httpMethod = method
        req.timeoutInterval = 30
        if let body {
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code >= 400 { throw Failure(message: (json["error"] as? String) ?? "Errore del server (\(code))") }
        return json
    }
}
