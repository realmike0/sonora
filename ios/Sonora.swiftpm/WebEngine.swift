import SwiftUI
import WebKit

/// Lettore ufficiale di YouTube (IFrame API) dentro una WebView nascosta.
/// Serve quando YouTube blocca l'audio diretto ("conferma di non essere un bot"): il lettore incorporato funziona sempre,
/// tranne per i brani che i proprietari non permettono di incorporare.
@MainActor
final class WebEngine: NSObject, WKScriptMessageHandler {
    let view: WKWebView
    var onState: ((Int) -> Void)?
    var onTime: ((Double, Double) -> Void)?
    var onError: ((Int) -> Void)?

    /// true = si usa il sito di YouTube Music (vero player) invece del lettore incorporato.
    private var site = false
    private var currentId = ""
    private var gotTime = false

    /// Gira dentro music.youtube.com: trova il <video> e ne riferisce stato e tempo.
    private static let siteScript = """
    function post(o) { window.webkit.messageHandlers.yt.postMessage(o); }
    function vid() { return document.querySelector('video'); }
    function spl() { var v = vid(); if (v) v.play(); }
    function spa() { var v = vid(); if (v) v.pause(); }
    function ssk(t) { var v = vid(); if (v) v.currentTime = t; }
    setInterval(function () {
      var v = vid(); if (!v) return;
      if (!v._sonora) {
        v._sonora = 1;
        v.addEventListener('playing', function () { post({ e: 'state', v: 1 }); });
        v.addEventListener('pause', function () { post({ e: 'state', v: 2 }); });
        v.addEventListener('ended', function () { post({ e: 'state', v: 0 }); });
        v.muted = false; v.play();
      }
      if (v.duration && isFinite(v.duration)) post({ e: 'time', c: v.currentTime, d: v.duration });
    }, 500);
    """

    private static let page = """
    <html><body style="margin:0;background:#000"><div id="p"></div><script>
    var p, ready = false, pend = null;
    function post(o) { window.webkit.messageHandlers.yt.postMessage(o); }
    var s = document.createElement('script'); s.src = 'https://www.youtube.com/iframe_api'; document.head.appendChild(s);
    function onYouTubeIframeAPIReady() {
      p = new YT.Player('p', { height: '200', width: '200', playerVars: { playsinline: 1, controls: 0, origin: 'https://www.youtube.com' }, events: {
        onReady: function () { ready = true; if (pend) { p.loadVideoById(pend); pend = null; } },
        onStateChange: function (x) { post({ e: 'state', v: x.data }); },
        onError: function (x) { post({ e: 'error', v: x.data }); } } });
    }
    function loadId(id) { if (ready) p.loadVideoById(id); else pend = id; }
    function pl() { if (p) p.playVideo(); }
    function pa() { if (p) p.pauseVideo(); }
    function sk(t) { if (p) p.seekTo(t, true); }
    setInterval(function () { if (ready && p.getDuration) post({ e: 'time', c: p.getCurrentTime(), d: p.getDuration() }); }, 500);
    </script></body></html>
    """

    override init() {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        view = WKWebView(frame: CGRect(x: 0, y: 0, width: 200, height: 200), configuration: cfg)
        super.init()
        cfg.userContentController.add(self, name: "yt")
        cfg.userContentController.addUserScript(WKUserScript(source: Self.siteScript, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        view.customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        // Evita la schermata di consenso dei cookie di YouTube.
        if let c = HTTPCookie(properties: [.name: "SOCS", .value: "CAI", .domain: ".youtube.com", .path: "/", .secure: "TRUE"]) {
            cfg.websiteDataStore.httpCookieStore.setCookie(c)
        }
        view.loadHTMLString(Self.page, baseURL: URL(string: "https://www.youtube.com"))
    }

    nonisolated func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        guard let d = m.body as? [String: Any], let e = d["e"] as? String else { return }
        Task { @MainActor in
            switch e {
            case "state": self.onState?(d["v"] as? Int ?? -1)
            case "error":
                let code = d["v"] as? Int ?? 0
                if !self.site && [101, 150, 152, 153].contains(code) {
                    // Incorporamento vietato: si passa al sito di YouTube Music.
                    self.openSite()
                } else {
                    self.onError?(code)
                }
            case "time":
                if (d["d"] as? Double ?? 0) > 0 { self.gotTime = true }
                self.onTime?((d["c"] as? Double) ?? 0, (d["d"] as? Double) ?? 0)
            default: break
            }
        }
    }

    func load(_ id: String) {
        currentId = id
        if site { openSite() } else { view.evaluateJavaScript("loadId('\(id)')") }
    }

    private func openSite() {
        site = true
        gotTime = false
        let id = currentId
        if let url = URL(string: "https://music.youtube.com/watch?v=\(id)") { view.load(URLRequest(url: url)) }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            if self.currentId == id && !self.gotTime { self.onError?(152) }
        }
    }

    func play() { view.evaluateJavaScript(site ? "spl()" : "pl()") }
    func pause() { view.evaluateJavaScript(site ? "spa()" : "pa()") }
    func seek(_ t: Double) { view.evaluateJavaScript(site ? "ssk(\(t))" : "sk(\(t))") }
}

/// Aggancia la WebView alla gerarchia (1 punto, quasi trasparente): senza, iOS sospende l'audio.
struct WebHost: UIViewRepresentable {
    let view: WKWebView
    func makeUIView(context: Context) -> WKWebView { view }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
