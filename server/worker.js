// Server Wrapped di Sonora (Cloudflare Worker + D1). Segreti: BREVO_KEY, SENDER_EMAIL, TELEGRAM_BOT_TOKEN, AUDD_TOKEN.
const J = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { "content-type": "application/json" } });
const err = (m, s = 400) => J({ error: m }, s);

async function sha(s) {
  const b = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
}
const rnd = (n) => [...crypto.getRandomValues(new Uint8Array(n))].map((x) => x.toString(16).padStart(2, "0")).join("");
const validEmail = (e) => typeof e === "string" && e.length < 200 && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(e);


const tgApi = (env, method, body) =>
  fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
  });

async function newToken(env, uid) {
  await env.DB.prepare("INSERT OR IGNORE INTO users(id,created) VALUES(?,?)").bind(uid, Date.now()).run();
  const token = rnd(32);
  await env.DB.prepare("INSERT INTO tokens(token_hash,user_id) VALUES(?,?)").bind(await sha(token), uid).run();
  return token;
}

async function telegramUpdate(env, u) {
  const m = u.message;
  if (!m || !m.chat || m.chat.type !== "private") return;
  const chat = m.chat.id;
  const say = (text, extra = {}) => tgApi(env, "sendMessage", { chat_id: chat, text, ...extra });
  const start = (m.text || "").match(/^\/start\s+([A-Za-z0-9_-]{16,64})$/);
  if (start) {
    const sh = await sha(start[1]);
    const row = await env.DB.prepare("SELECT exp FROM tg_sessions WHERE sid_hash=?").bind(sh).first();
    if (!row || row.exp < Date.now()) return say("Richiesta scaduta. Torna su Sonora e riprova.");
    await env.DB.prepare("UPDATE tg_sessions SET user_id=? WHERE sid_hash=?").bind("pending:" + chat, sh).run();
    return say("Per verificare che sei tu, condividi il tuo numero di telefono con il pulsante qui sotto. Viene salvato solo come impronta (hash), mai in chiaro.", {
      reply_markup: { keyboard: [[{ text: "Condividi il mio numero", request_contact: true }]], resize_keyboard: true, one_time_keyboard: true },
    });
  }
  if (m.contact) {
    if (m.contact.user_id !== m.from.id) return say("Condividi il TUO numero con il pulsante, non un altro contatto.");
    const sess = await env.DB.prepare("SELECT sid_hash FROM tg_sessions WHERE user_id=? AND exp>?").bind("pending:" + chat, Date.now()).first();
    if (!sess) return say("Nessuna richiesta attiva. Apri Sonora e scegli \"Verifica con Telegram\".");
    const uid = await sha("tel:" + String(m.contact.phone_number).replace(/\D/g, ""));
    const token = await newToken(env, uid);
    await env.DB.prepare("UPDATE tg_sessions SET user_id=?, token=? WHERE sid_hash=?").bind(uid, token, sess.sid_hash).run();
    return say("Fatto! Torna su Sonora.", { reply_markup: { remove_keyboard: true } });
  }
  return say("Ciao! Per attivare il Wrapped apri Sonora → Impostazioni → Wrapped e tracciamento.");
}

async function userFrom(req, env) {
  const m = (req.headers.get("authorization") || "").match(/^Bearer (.+)$/);
  if (!m) return null;
  const r = await env.DB.prepare("SELECT user_id FROM tokens WHERE token_hash=?").bind(await sha(m[1])).first();
  return r ? r.user_id : null;
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const path = url.pathname;
    try {
      if (req.method === "POST" && path === "/v1/code") {
        const { email: raw } = await req.json();
        if (!validEmail(raw)) return err("Email non valida");
        const email = raw.trim().toLowerCase();
        const eh = await sha(email);
        const now = Date.now();
        const old = await env.DB.prepare("SELECT last_sent FROM codes WHERE email_hash=?").bind(eh).first();
        if (old && now - old.last_sent < 60000) return err("Aspetta un minuto prima di chiedere un altro codice", 429);
        const code = String(100000 + (crypto.getRandomValues(new Uint32Array(1))[0] % 900000));
        await env.DB.prepare(
          "INSERT INTO codes(email_hash,code_hash,exp,attempts,last_sent) VALUES(?,?,?,0,?) ON CONFLICT(email_hash) DO UPDATE SET code_hash=excluded.code_hash,exp=excluded.exp,attempts=0,last_sent=excluded.last_sent"
        ).bind(eh, await sha(eh + code), now + 10 * 60000, now).run();
        const r = await fetch("https://api.brevo.com/v3/smtp/email", {
          method: "POST",
          headers: { "api-key": env.BREVO_KEY, "content-type": "application/json" },
          body: JSON.stringify({
            sender: { name: "Sonora", email: env.SENDER_EMAIL },
            to: [{ email }],
            subject: "Il tuo codice Sonora",
            textContent: `Il tuo codice di verifica Sonora è ${code}. Vale 10 minuti. Se non l'hai richiesto, ignora questa email.`,
          }),
        });
        if (!r.ok) return err("Invio email non riuscito", 502);
        return J({ ok: true });
      }

      if (req.method === "POST" && path === "/v1/verify") {
        const { email: raw, code } = await req.json();
        if (!validEmail(raw) || !/^\d{6}$/.test(String(code))) return err("Dati non validi");
        const eh = await sha(raw.trim().toLowerCase());
        const row = await env.DB.prepare("SELECT * FROM codes WHERE email_hash=?").bind(eh).first();
        if (!row || row.exp < Date.now() || row.attempts >= 5) return err("Codice scaduto: chiedine uno nuovo", 400);
        if (row.code_hash !== (await sha(eh + String(code)))) {
          await env.DB.prepare("UPDATE codes SET attempts=attempts+1 WHERE email_hash=?").bind(eh).run();
          return err("Codice errato");
        }
        await env.DB.prepare("DELETE FROM codes WHERE email_hash=?").bind(eh).run();
        await env.DB.prepare("INSERT OR IGNORE INTO users(id,created) VALUES(?,?)").bind(eh, Date.now()).run();
        const token = rnd(32);
        await env.DB.prepare("INSERT INTO tokens(token_hash,user_id) VALUES(?,?)").bind(await sha(token), eh).run();
        return J({ token });
      }

      if (req.method === "POST" && path === "/setup") {
        if (!env.TELEGRAM_BOT_TOKEN || req.headers.get("authorization") !== "Bearer " + env.TELEGRAM_BOT_TOKEN) return err("Non autorizzato", 401);
        const r = await tgApi(env, "setWebhook", { url: url.origin + "/tg", secret_token: (await sha(env.TELEGRAM_BOT_TOKEN)).slice(0, 32), allowed_updates: ["message"] });
        return J(await r.json());
      }

      if (req.method === "POST" && path === "/tg") {
        if (req.headers.get("x-telegram-bot-api-secret-token") !== (await sha(env.TELEGRAM_BOT_TOKEN || "")).slice(0, 32)) return err("Non autorizzato", 401);
        await telegramUpdate(env, await req.json());
        return J({ ok: true });
      }

      if (req.method === "POST" && path === "/v1/recognize") {
        if (!env.AUDD_TOKEN || env.AUDD_TOKEN === "none") return err("Riconoscimento non configurato", 503);
        const ip = req.headers.get("cf-connecting-ip") || "x";
        const day = Math.floor(Date.now() / 86400000);
        const key = (await sha(ip)).slice(0, 24) + ":" + day;
        const row = await env.DB.prepare("SELECT n FROM rl WHERE k=?").bind(key).first();
        if (row && row.n >= 15) return err("Limite giornaliero raggiunto, riprova domani", 429);
        await env.DB.prepare("INSERT INTO rl(k,n) VALUES(?,1) ON CONFLICT(k) DO UPDATE SET n=n+1").bind(key).run();
        const buf = await req.arrayBuffer();
        if (buf.byteLength < 1000 || buf.byteLength > 2000000) return err("Audio non valido");
        const fd = new FormData();
        fd.append("api_token", env.AUDD_TOKEN);
        fd.append("file", new Blob([buf], { type: "audio/wav" }), "a.wav");
        const r = await fetch("https://api.audd.io/", { method: "POST", body: fd });
        const j = await r.json().catch(() => ({}));
        if (j && j.result && j.result.title) return J({ found: true, title: j.result.title, artist: j.result.artist || "" });
        return J({ found: false });
      }

      if (req.method === "POST" && path === "/v1/tg/start") {
        const sid = rnd(16);
        await env.DB.prepare("DELETE FROM tg_sessions WHERE exp<?").bind(Date.now()).run();
        await env.DB.prepare("INSERT INTO tg_sessions(sid_hash,exp) VALUES(?,?)").bind(await sha(sid), Date.now() + 10 * 60000).run();
        return J({ sid });
      }

      if (req.method === "POST" && path === "/v1/tg/poll") {
        const { sid } = await req.json();
        if (typeof sid !== "string" || sid.length > 64) return err("Dati non validi");
        const sh = await sha(sid);
        const row = await env.DB.prepare("SELECT token,exp FROM tg_sessions WHERE sid_hash=?").bind(sh).first();
        if (!row || row.exp < Date.now()) return err("Richiesta scaduta", 410);
        if (!row.token) return J({ pending: true });
        await env.DB.prepare("DELETE FROM tg_sessions WHERE sid_hash=?").bind(sh).run();
        return J({ token: row.token });
      }

      const uid = await userFrom(req, env);
      if (!uid) return err("Non autorizzato", 401);

      if (req.method === "POST" && path === "/v1/events") {
        const { events } = await req.json();
        if (!Array.isArray(events) || events.length > 200) return err("Troppi eventi");
        const st = env.DB.prepare("INSERT OR IGNORE INTO events(user_id,ts,track_id,title,artist,thumb,ms) VALUES(?,?,?,?,?,?,?)");
        const batch = events
          .filter((e) => e && Number.isFinite(e.ts) && typeof e.id === "string")
          .map((e) => st.bind(uid, e.ts, e.id.slice(0, 40), String(e.title || "").slice(0, 200), String(e.artist || "").slice(0, 200), String(e.thumb || "").slice(0, 400), Math.max(0, Math.min(+e.ms || 0, 36000000))));
        if (batch.length) await env.DB.batch(batch);
        return J({ ok: true, saved: batch.length });
      }

      if (req.method === "GET" && path === "/v1/wrapped") {
        const year = parseInt(url.searchParams.get("year")) || new Date().getUTCFullYear();
        const from = Date.UTC(year, 0, 1), to = Date.UTC(year + 1, 0, 1);
        const rows = (await env.DB.prepare("SELECT ts,track_id,title,artist,thumb,ms FROM events WHERE user_id=? AND ts>=? AND ts<? LIMIT 100000").bind(uid, from, to).all()).results;
        const byTrack = new Map(), byArtist = new Map(), months = {}, hours = {};
        let ms = 0;
        for (const r of rows) {
          ms += r.ms;
          const t = byTrack.get(r.track_id) || { id: r.track_id, title: r.title, artist: r.artist, thumb: r.thumb, plays: 0 };
          t.plays++; byTrack.set(r.track_id, t);
          const an = (r.artist || "").split(/,| & /)[0].trim();
          if (an) { const a = byArtist.get(an) || { name: an, ms: 0, plays: 0 }; a.ms += r.ms; a.plays++; byArtist.set(an, a); }
          const d = new Date(r.ts);
          months[d.getUTCMonth() + 1] = (months[d.getUTCMonth() + 1] || 0) + 1;
          hours[d.getUTCHours()] = (hours[d.getUTCHours()] || 0) + 1;
        }
        const top = (o) => Object.entries(o).sort((a, b) => b[1] - a[1])[0];
        return J({
          year, minutes: Math.floor(ms / 60000), plays: rows.length, distinctTracks: byTrack.size,
          topTracks: [...byTrack.values()].sort((a, b) => b.plays - a.plays).slice(0, 5),
          topArtists: [...byArtist.values()].sort((a, b) => b.ms - a.ms).slice(0, 5).map((a) => ({ name: a.name, minutes: Math.floor(a.ms / 60000), plays: a.plays })),
          topMonth: top(months) ? +top(months)[0] : 0,
          topHour: top(hours) ? +top(hours)[0] : -1,
        });
      }

      if (req.method === "DELETE" && path === "/v1/me") {
        await env.DB.batch([
          env.DB.prepare("DELETE FROM events WHERE user_id=?").bind(uid),
          env.DB.prepare("DELETE FROM tokens WHERE user_id=?").bind(uid),
          env.DB.prepare("DELETE FROM users WHERE id=?").bind(uid),
        ]);
        return J({ ok: true });
      }
      return err("Non trovato", 404);
    } catch (e) {
      return err("Errore del server", 500);
    }
  },
};
