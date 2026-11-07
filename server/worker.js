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



// ───────── Chiavi del server (segreto SERVER_KEY): email cifrate, impronte con chiave, IP in hash ─────────
const enc = new TextEncoder();
const hex = (buf) => [...new Uint8Array(buf)].map((x) => x.toString(16).padStart(2, "0")).join("");
const unhex = (h) => Uint8Array.from(h.match(/../g).map((x) => parseInt(x, 16)));
const keyReady = (env) => typeof env.SERVER_KEY === "string" && env.SERVER_KEY.length >= 16;
async function hmac(env, s) {
  const k = await crypto.subtle.importKey("raw", enc.encode("hmac:" + env.SERVER_KEY), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", k, enc.encode(s)));
}
async function aesKey(env) {
  const d = await crypto.subtle.digest("SHA-256", enc.encode("enc:" + env.SERVER_KEY));
  return crypto.subtle.importKey("raw", d, "AES-GCM", false, ["encrypt", "decrypt"]);
}
async function seal(env, text) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await aesKey(env), enc.encode(text));
  return hex(iv) + ":" + hex(ct);
}
async function unseal(env, s) {
  const [i, c] = String(s).split(":");
  const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv: unhex(i) }, await aesKey(env), unhex(c));
  return new TextDecoder().decode(pt);
}
async function sendMail(env, to, subject, text) {
  const r = await fetch("https://api.brevo.com/v3/smtp/email", {
    method: "POST",
    headers: { "api-key": env.BREVO_KEY, "content-type": "application/json" },
    body: JSON.stringify({ sender: { name: "Sonora", email: env.SENDER_EMAIL }, to: [{ email: to }], subject, textContent: text }),
  });
  return r.ok;
}
// Genera e invia il codice di verifica email di 6 cifre (valido 10 minuti).
async function issueCode(env, uid, email) {
  const code = String(100000 + (crypto.getRandomValues(new Uint32Array(1))[0] % 900000));
  const now = Date.now();
  await env.DB.prepare("UPDATE sid_mail SET code_hash=?, exp=?, attempts=0, last_sent=? WHERE user_id=?")
    .bind(await sha(uid + code), now + 10 * 60000, now, uid).run();
  return sendMail(env, email, "Verifica il tuo Sonora ID", `Il tuo codice di verifica Sonora è ${code}. Vale 10 minuti. Se non l'hai richiesto, ignora questa email.`);
}
const isBanned = async (env, k) => !!(k && (await env.DB.prepare("SELECT 1 FROM sid_ban WHERE k=?").bind(k).first()));
const isVerified = async (env, uid) => {
  const m = await env.DB.prepare("SELECT verified FROM sid_mail WHERE user_id=?").bind(uid).first();
  return !!(m && m.verified);
};

// ───────── Sonora ID (email + password): sincronizza i brani preferiti ─────────
async function pbkdf2(pw, saltHex) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(pw), "PBKDF2", false, ["deriveBits"]);
  const salt = Uint8Array.from(saltHex.match(/../g).map((h) => parseInt(h, 16)));
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: 100000 }, key, 256);
  return [...new Uint8Array(bits)].map((x) => x.toString(16).padStart(2, "0")).join("");
}
const sameStr = (a, b) => {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
};
// Conta i tentativi per ora; restituisce true se il limite è superato.
async function tooMany(env, key, max) {
  const k = key + ":" + Math.floor(Date.now() / 3600000);
  const row = await env.DB.prepare("SELECT n FROM rl WHERE k=?").bind(k).first();
  if (row && row.n >= max) return true;
  await env.DB.prepare("INSERT INTO rl(k,n) VALUES(?,1) ON CONFLICT(k) DO UPDATE SET n=n+1").bind(k).run();
  return false;
}
async function newSidToken(env, uid) {
  const token = rnd(32);
  await env.DB.prepare("INSERT INTO sid_tokens(token_hash,user_id) VALUES(?,?)").bind(await sha(token), uid).run();
  return token;
}
async function sidFrom(req, env) {
  const m = (req.headers.get("authorization") || "").match(/^Bearer (.+)$/);
  if (!m) return null;
  const r = await env.DB.prepare("SELECT user_id FROM sid_tokens WHERE token_hash=?").bind(await sha(m[1])).first();
  return r ? r.user_id : null;
}
async function sonoraId(req, env, path) {
  const ip = (await sha(req.headers.get("cf-connecting-ip") || "x")).slice(0, 24);
  if (req.method === "POST" && path === "/v1/id/register") {
    if (!keyReady(env)) return err("Server non configurato", 503);
    const { email: raw, password } = await req.json();
    if (!validEmail(raw)) return err("Email non valida");
    if (typeof password !== "string" || password.length < 8 || password.length > 200) return err("La password deve avere almeno 8 caratteri");
    if (await tooMany(env, "reg:" + ip, 10)) return err("Troppi tentativi, riprova più tardi", 429);
    const iph = await hmac(env, "ip:" + (req.headers.get("cf-connecting-ip") || "x"));
    if (await isBanned(env, "ip:" + iph)) return err("Registrazione non disponibile", 403);
    const email = raw.trim().toLowerCase();
    const eh = await hmac(env, "sid:" + email);
    const legacy = await sha("sid:" + email);
    if (await env.DB.prepare("SELECT 1 FROM sid_users WHERE email_hash IN (?,?)").bind(eh, legacy).first()) return err("Esiste già un Sonora ID con questa email", 409);
    const salt = rnd(16);
    const uid = "sid_" + rnd(12);
    await env.DB.prepare("INSERT INTO sid_users(email_hash,user_id,salt,pw_hash,created) VALUES(?,?,?,?,?)")
      .bind(eh, uid, salt, await pbkdf2(password, salt), Date.now()).run();
    await env.DB.prepare("INSERT INTO sid_mail(user_id,email_enc,verified,code_hash,exp,attempts,last_sent) VALUES(?,?,0,'',0,0,0)").bind(uid, await seal(env, email)).run();
    if (!(await issueCode(env, uid, email))) {
      await env.DB.batch([env.DB.prepare("DELETE FROM sid_mail WHERE user_id=?").bind(uid), env.DB.prepare("DELETE FROM sid_users WHERE user_id=?").bind(uid)]);
      return err("Invio email non riuscito: controlla l'indirizzo e riprova", 502);
    }
    return J({ token: await newSidToken(env, uid), verified: false });
  }
  if (req.method === "POST" && path === "/v1/id/login") {
    const { email: raw, password } = await req.json();
    if (!validEmail(raw) || typeof password !== "string" || password.length > 200) return err("Email o password non corretti", 401);
    const email = raw.trim().toLowerCase();
    const legacy = await sha("sid:" + email);
    if ((await tooMany(env, "login:" + legacy + ":" + ip, 10)) || (await tooMany(env, "loginip:" + ip, 40))) return err("Troppi tentativi, riprova più tardi", 429);
    let u = null, migrate = false, eh = null;
    if (keyReady(env)) {
      eh = await hmac(env, "sid:" + email);
      u = await env.DB.prepare("SELECT user_id,salt,pw_hash FROM sid_users WHERE email_hash=?").bind(eh).first();
    }
    if (!u) {
      u = await env.DB.prepare("SELECT user_id,salt,pw_hash FROM sid_users WHERE email_hash=?").bind(legacy).first();
      migrate = !!u;
    }
    if (!u || !sameStr(await pbkdf2(password, u.salt), u.pw_hash)) return err("Email o password non corretti", 401);
    if (await isBanned(env, "user:" + u.user_id)) return err("Account sospeso", 403);
    // Account creati prima della verifica: passano all'impronta con chiave e salvano l'email cifrata.
    if (migrate && keyReady(env)) {
      await env.DB.batch([
        env.DB.prepare("UPDATE sid_users SET email_hash=? WHERE user_id=?").bind(eh, u.user_id),
        env.DB.prepare("INSERT OR IGNORE INTO sid_mail(user_id,email_enc,verified,code_hash,exp,attempts,last_sent) VALUES(?,?,0,'',0,0,0)").bind(u.user_id, await seal(env, email)),
      ]);
      await issueCode(env, u.user_id, email).catch(() => false);
    }
    return J({ token: await newSidToken(env, u.user_id), verified: await isVerified(env, u.user_id) });
  }
  const uid = await sidFrom(req, env);
  if (!uid) return err("Non autorizzato", 401);
  if (req.method === "GET" && path === "/v1/id/me") {
    const m = await env.DB.prepare("SELECT email_enc,verified FROM sid_mail WHERE user_id=?").bind(uid).first();
    return J({ verified: !!(m && m.verified), email: m && keyReady(env) ? await unseal(env, m.email_enc).catch(() => null) : null });
  }
  if (req.method === "POST" && path === "/v1/id/verify") {
    const { code } = await req.json();
    if (!/^\d{6}$/.test(String(code))) return err("Il codice ha 6 cifre");
    const m = await env.DB.prepare("SELECT verified,code_hash,exp,attempts FROM sid_mail WHERE user_id=?").bind(uid).first();
    if (!m) return err("Nessuna verifica in corso", 404);
    if (m.verified) return J({ verified: true });
    if (!m.code_hash || m.exp < Date.now() || m.attempts >= 5) return err("Codice scaduto: chiedine uno nuovo");
    if (!sameStr(m.code_hash, await sha(uid + String(code)))) {
      await env.DB.prepare("UPDATE sid_mail SET attempts=attempts+1 WHERE user_id=?").bind(uid).run();
      return err("Codice errato");
    }
    await env.DB.prepare("UPDATE sid_mail SET verified=1, code_hash='' WHERE user_id=?").bind(uid).run();
    return J({ verified: true });
  }
  if (req.method === "POST" && path === "/v1/id/resend") {
    if (!keyReady(env)) return err("Server non configurato", 503);
    const m = await env.DB.prepare("SELECT email_enc,verified,last_sent FROM sid_mail WHERE user_id=?").bind(uid).first();
    if (!m) return err("Nessuna verifica in corso", 404);
    if (m.verified) return J({ verified: true });
    if (Date.now() - m.last_sent < 60000) return err("Aspetta un minuto prima di chiedere un altro codice", 429);
    if (await tooMany(env, "vr:" + uid, 5)) return err("Troppi codici richiesti, riprova più tardi", 429);
    if (!(await issueCode(env, uid, await unseal(env, m.email_enc)))) return err("Invio email non riuscito", 502);
    return J({ ok: true });
  }
  if (req.method === "POST" && path === "/v1/id/sync") {
    const { likes, removed } = await req.json();
    const L = Array.isArray(likes) ? likes.slice(0, 1000) : [];
    const R = Array.isArray(removed) ? removed.slice(0, 1000) : [];
    const now = Date.now();
    const up = env.DB.prepare("INSERT OR IGNORE INTO sid_likes(user_id,track_id,title,artist,thumb,dur,ts) VALUES(?,?,?,?,?,?,?)");
    const del = env.DB.prepare("DELETE FROM sid_likes WHERE user_id=? AND track_id=?");
    const batch = [
      ...L.filter((t) => t && typeof t.id === "string").map((t, i) =>
        up.bind(uid, t.id.slice(0, 40), String(t.title || "").slice(0, 200), String(t.artist || "").slice(0, 200), String(t.thumb || "").slice(0, 400), Math.max(0, +t.dur || 0), now - i)),
      ...R.filter((x) => typeof x === "string").map((x) => del.bind(uid, x.slice(0, 40))),
    ];
    if (batch.length) await env.DB.batch(batch);
    const rows = await env.DB.prepare("SELECT track_id,title,artist,thumb,dur FROM sid_likes WHERE user_id=? ORDER BY ts DESC LIMIT 5000").bind(uid).all();
    return J({ likes: rows.results.map((r) => ({ id: r.track_id, title: r.title, artist: r.artist, thumb: r.thumb, dur: r.dur })) });
  }
  if (req.method === "POST" && path === "/v1/id/logout") {
    const m = (req.headers.get("authorization") || "").match(/^Bearer (.+)$/);
    await env.DB.prepare("DELETE FROM sid_tokens WHERE token_hash=?").bind(await sha(m[1])).run();
    return J({ ok: true });
  }
  if (req.method === "DELETE" && path === "/v1/id") {
    await env.DB.batch([
      env.DB.prepare("DELETE FROM sid_likes WHERE user_id=?").bind(uid),
      env.DB.prepare("DELETE FROM sid_ded_meta WHERE user_id=?").bind(uid),
      env.DB.prepare("DELETE FROM sid_ded WHERE user_id=?").bind(uid),
      env.DB.prepare("DELETE FROM sid_mail WHERE user_id=?").bind(uid),
      env.DB.prepare("DELETE FROM sid_tokens WHERE user_id=?").bind(uid),
      env.DB.prepare("DELETE FROM sid_users WHERE user_id=?").bind(uid),
    ]);
    return J({ ok: true });
  }
  return err("Non trovato", 404);
}

// ───────── AI (Cloudflare Workers AI): voti, scelte giornaliere, ordine dei brani ─────────
const AI_MODEL = "@cf/meta/llama-3.1-8b-instruct";

// ───────── Dediche: visibili a tutti gli utenti registrati ─────────
const BAD_MSG = /(https?:\/\/|www\.|\.com\b|\.it\b|t\.me\/|@\w{3,}|\+?\d[\d\s]{8,})/i;
const clean = (s, n) => String(s || "").replace(/[\u0000-\u001f<>]/g, " ").replace(/\s+/g, " ").trim().slice(0, n);
async function dedRoutes(req, env, path) {
  const uid = await sidFrom(req, env);
  if (!uid) return err("Accedi con Sonora ID", 401);
  if (req.method === "GET" && path === "/v1/id/ded") {
    const rows = await env.DB.prepare(
      "SELECT id,user_id,from_name,to_name,msg,track_id,title,artist,thumb,ts FROM sid_ded WHERE hidden=0 ORDER BY ts DESC LIMIT 60"
    ).all();
    return J({ items: rows.results.map((r) => ({
      id: r.id, from: r.from_name, to: r.to_name, msg: r.msg, track: r.track_id, title: r.title,
      artist: r.artist, thumb: r.thumb, ts: r.ts, mine: r.user_id === uid,
    })) });
  }
  if (req.method === "POST" && path === "/v1/id/ded") {
    if (!(await isVerified(env, uid))) return err("Verifica la tua email per scrivere dediche", 403);
    const iph = keyReady(env) ? await hmac(env, "ip:" + (req.headers.get("cf-connecting-ip") || "x")) : "";
    if ((await isBanned(env, "user:" + uid)) || (await isBanned(env, "ip:" + iph))) return err("Non puoi scrivere dediche", 403);
    if (await tooMany(env, "ded:" + uid, 5)) return err("Troppe dediche, riprova più tardi", 429);
    const b = await req.json();
    const to = clean(b.to, 30), from = clean(b.from, 24) || "Anonimo", msg = clean(b.msg, 160);
    const track = clean(b.track, 40);
    const title = clean(b.title, 200), artist = clean(b.artist, 200);
    if (!/^[\w-]{11}$/.test(track) || !to) return err("Scegli un brano e a chi dedicarlo");
    if (BAD_MSG.test([msg, to, from, title, artist].join(" ").normalize("NFKC"))) return err("Link e contatti non sono ammessi nelle dediche");
    const id = rnd(8);
    await env.DB.prepare("INSERT INTO sid_ded(id,user_id,from_name,to_name,msg,track_id,title,artist,thumb,ts) VALUES(?,?,?,?,?,?,?,?,?,?)")
      .bind(id, uid, from, to, msg, track, title, artist, "https://i.ytimg.com/vi/" + track + "/mqdefault.jpg", Date.now()).run();
    // L'impronta dell'IP serve solo a ripulire lo spam: si azzera dopo 30 giorni.
    await env.DB.batch([
      env.DB.prepare("INSERT INTO sid_ded_meta(ded_id,user_id,ip_hash,ts) VALUES(?,?,?,?)").bind(id, uid, iph, Date.now()),
      env.DB.prepare("UPDATE sid_ded_meta SET ip_hash='' WHERE ts<?").bind(Date.now() - 30 * 86400000),
    ]);
    return J({ ok: true, id });
  }
  const m = path.match(/^\/v1\/id\/ded\/([a-f0-9]{16})(\/report)?$/);
  if (m) {
    if (req.method === "DELETE" && !m[2]) {
      await env.DB.prepare("DELETE FROM sid_ded WHERE id=? AND user_id=?").bind(m[1], uid).run();
      return J({ ok: true });
    }
    if (req.method === "POST" && m[2]) {
      await env.DB.prepare("INSERT OR IGNORE INTO sid_ded_reports(ded_id,user_id) VALUES(?,?)").bind(m[1], uid).run();
      const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM sid_ded_reports WHERE ded_id=?").bind(m[1]).first();
      if (n && n.n >= 3) await env.DB.prepare("UPDATE sid_ded SET hidden=1 WHERE id=?").bind(m[1]).run();
      return J({ ok: true });
    }
  }
  return err("Non trovato", 404);
}


// ───────── Amministrazione (chiave ADMIN_KEY): elenco dediche, pulizia spam per IP/utente, ban ─────────
async function adminRoutes(req, env, path, url) {
  const k = req.headers.get("x-admin-key") || "";
  if (!env.ADMIN_KEY || env.ADMIN_KEY.length < 16 || !sameStr(k, env.ADMIN_KEY)) return err("Non trovato", 404);
  if (req.method === "GET" && path === "/v1/admin/ded") {
    const lim = Math.min(200, parseInt(url.searchParams.get("limit")) || 50);
    const rows = await env.DB.prepare(
      "SELECT d.id,d.user_id,d.from_name,d.to_name,d.msg,d.title,d.ts,d.hidden,m.ip_hash FROM sid_ded d LEFT JOIN sid_ded_meta m ON m.ded_id=d.id ORDER BY d.ts DESC LIMIT ?"
    ).bind(lim).all();
    return J({ items: rows.results.map((r) => ({ id: r.id, user: r.user_id, ip: (r.ip_hash || "").slice(0, 10), from: r.from_name, to: r.to_name, msg: r.msg, title: r.title, ts: r.ts, hidden: r.hidden })) });
  }
  if (req.method === "POST" && path === "/v1/admin/purge") {
    const b = await req.json();
    const ids = new Set(), users = new Set(), ips = new Set();
    if (typeof b.ded === "string") {
      const r = await env.DB.prepare("SELECT d.id,d.user_id,m.ip_hash FROM sid_ded d LEFT JOIN sid_ded_meta m ON m.ded_id=d.id WHERE d.id=?").bind(b.ded).all();
      for (const x of r.results) { ids.add(x.id); users.add(x.user_id); if (x.ip_hash) ips.add(x.ip_hash); }
    }
    if (typeof b.user === "string") {
      users.add(b.user);
      const r = await env.DB.prepare("SELECT d.id,m.ip_hash FROM sid_ded d LEFT JOIN sid_ded_meta m ON m.ded_id=d.id WHERE d.user_id=?").bind(b.user).all();
      for (const x of r.results) { ids.add(x.id); if (x.ip_hash) ips.add(x.ip_hash); }
    }
    if (typeof b.ip === "string" && /^[a-f0-9]{6,64}$/.test(b.ip)) {
      const r = await env.DB.prepare("SELECT ded_id,user_id,ip_hash FROM sid_ded_meta WHERE ip_hash LIKE ?").bind(b.ip + "%").all();
      for (const x of r.results) { ids.add(x.ded_id); users.add(x.user_id); ips.add(x.ip_hash); }
    }
    const list = [...ids];
    for (let i = 0; i < list.length; i += 40) {
      const part = list.slice(i, i + 40);
      const q = part.map(() => "?").join(",");
      await env.DB.batch([
        env.DB.prepare(`DELETE FROM sid_ded WHERE id IN (${q})`).bind(...part),
        env.DB.prepare(`DELETE FROM sid_ded_meta WHERE ded_id IN (${q})`).bind(...part),
        env.DB.prepare(`DELETE FROM sid_ded_reports WHERE ded_id IN (${q})`).bind(...part),
      ]);
    }
    let banned = 0;
    if (b.ban === true) {
      const keys = [...[...users].map((u) => "user:" + u), ...[...ips].map((i) => "ip:" + i)];
      for (const key of keys) { await env.DB.prepare("INSERT OR IGNORE INTO sid_ban(k,ts) VALUES(?,?)").bind(key, Date.now()).run(); banned++; }
    }
    return J({ deleted: list.length, banned });
  }
  if (req.method === "POST" && path === "/v1/admin/unban") {
    const b = await req.json();
    if (typeof b.user === "string") await env.DB.prepare("DELETE FROM sid_ban WHERE k=?").bind("user:" + b.user).run();
    if (typeof b.ip === "string" && /^[a-f0-9]{6,64}$/.test(b.ip)) await env.DB.prepare("DELETE FROM sid_ban WHERE k LIKE ?").bind("ip:" + b.ip + "%").run();
    return J({ ok: true });
  }
  return err("Non trovato", 404);
}

// ───────── Sonora Web: ricerca e audio per il player web separato (cartella web/) ─────────
function ytWalk(o, out) {
  if (!o || typeof o !== "object") return;
  if (o.musicResponsiveListItemRenderer) out.push(o.musicResponsiveListItemRenderer);
  for (const k in o) ytWalk(o[k], out);
}
async function webSearch(req, env, url) {
  const q = (url.searchParams.get("q") || "").trim().slice(0, 100);
  if (q.length < 2) return J({ items: [] });
  const ip = (await sha(req.headers.get("cf-connecting-ip") || "x")).slice(0, 24);
  if (await tooMany(env, "ws:" + ip, 120)) return err("Troppe ricerche", 429);
  const r = await fetch("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", {
    method: "POST",
    headers: { "content-type": "application/json", "user-agent": "Mozilla/5.0", origin: "https://music.youtube.com" },
    body: JSON.stringify({
      context: { client: { clientName: "WEB_REMIX", clientVersion: "1.20241111.01.00", hl: "it", gl: "IT" } },
      query: q, params: "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D",
    }),
  });
  if (!r.ok) return err("Ricerca non disponibile", 502);
  const found = [];
  ytWalk(await r.json(), found);
  const items = [], seen = new Set();
  for (const it of found) {
    const id = it.playlistItemData && it.playlistItemData.videoId;
    if (!id || seen.has(id)) continue;
    seen.add(id);
    const cols = it.flexColumns || [];
    const title = (((cols[0] || {}).musicResponsiveListItemFlexColumnRenderer || {}).text || {}).runs;
    const sub = (((cols[1] || {}).musicResponsiveListItemFlexColumnRenderer || {}).text || {}).runs || [];
    const parts = [[]];
    for (const run of sub) { if (run.text === " • ") parts.push([]); else parts[parts.length - 1].push(run.text); }
    const artist = (parts.length > 1 ? parts[1] : parts[0]).join("").slice(0, 120);
    const th = ((it.thumbnail || {}).musicThumbnailRenderer || {}).thumbnail;
    const thumb = th && th.thumbnails && th.thumbnails.length ? th.thumbnails[th.thumbnails.length - 1].url : "";
    items.push({ id, title: title && title[0] ? title[0].text : "", artist, thumb });
    if (items.length >= 20) break;
  }
  return J({ items });
}

async function askAi(env, system, user) {
  const r = await env.AI.run(AI_MODEL, {
    messages: [{ role: "system", content: system }, { role: "user", content: user }],
    max_tokens: 700,
    temperature: 0.4,
  });
  const text = String(r.response || "");
  const m = text.match(/\{[\s\S]*\}/);
  if (!m) throw new Error("risposta AI non valida");
  return JSON.parse(m[0]);
}
const cleanList = (a, n, len) => (Array.isArray(a) ? a : []).slice(0, n).map((x) => String(x).slice(0, len));
async function aiRoutes(req, env, path) {
  if (!env.AI) return err("AI non disponibile", 503);
  const ip = (await sha(req.headers.get("cf-connecting-ip") || "x")).slice(0, 24);
  if (await tooMany(env, "ai:" + ip, 60)) return err("Troppe richieste, riprova più tardi", 429);
  if (req.method !== "POST") return err("Non trovato", 404);
  const b = await req.json();
  const lang = { it: "italiano", en: "inglese", es: "spagnolo", de: "tedesco", tr: "turco", sq: "albanese", fr: "francese" }[b.lang] || "italiano";
  try {
    if (path === "/v1/ai/rate") {
      const what = b.kind === "album" ? `l'album "${String(b.name).slice(0, 120)}" di ${String(b.artist || "").slice(0, 120)}` : `l'artista ${String(b.name).slice(0, 120)}`;
      const tracks = cleanList(b.tracks, 25, 100).join("; ");
      const o = await askAi(env,
        "Sei un critico musicale esperto e onesto. Giudichi il significato dei testi, la melodia e il flow. Se non conosci bene l'opera, dai un voto prudente. Rispondi SOLO con un oggetto JSON.",
        `Valuta ${what}. Brani: ${tracks || "non disponibili"}.\nRispondi in ${lang} con JSON: {"voto": numero da 1 a 10 con un decimale, "sintesi": frase di massimo 18 parole}`);
      const voto = Math.min(10, Math.max(1, Number(o.voto) || 0));
      return J({ score: Math.round(voto * 10) / 10, verdict: String(o.sintesi || "").slice(0, 200) });
    }
    if (path === "/v1/ai/picks") {
      const hist = cleanList(b.history, 40, 120).join("\n");
      const o = await askAi(env,
        "Sei un consulente musicale. Proponi brani reali e famosi che esistono davvero. Rispondi SOLO con un oggetto JSON.",
        `Ascolti recenti dell'utente:\n${hist}\n\nSuggerisci 10 brani che potrebbero piacergli, per lo più diversi da quelli elencati, ma coerenti con i suoi gusti.\nJSON: {"brani":[{"artista":"...","titolo":"..."}]}`);
      const picks = (Array.isArray(o.brani) ? o.brani : []).slice(0, 10)
        .map((x) => ({ artist: String(x.artista || "").slice(0, 100), title: String(x.titolo || "").slice(0, 100) }))
        .filter((x) => x.artist && x.title);
      return J({ picks });
    }
    if (path === "/v1/ai/mix") {
      const list = cleanList(b.tracks, 60, 120);
      const n = list.length;
      const o = await askAi(env,
        "Sei un DJ. Ordini i brani per un flusso fluido: energia e tempo che salgono e scendono gradualmente, tonalità e mood vicini tra un brano e il successivo. Rispondi SOLO con un oggetto JSON.",
        `Brani numerati da 0 a ${n - 1}:\n${list.map((t, i) => `${i}. ${t}`).join("\n")}\n\nRispondi con JSON: {"ordine":[tutti i numeri da 0 a ${n - 1}, ciascuno una sola volta]}`);
      let ord = (Array.isArray(o.ordine) ? o.ordine : []).map(Number).filter((x, i, a) => Number.isInteger(x) && x >= 0 && x < n && a.indexOf(x) === i);
      for (let i = 0; i < n; i++) if (!ord.includes(i)) ord.push(i);
      return J({ order: ord });
    }
  } catch (e) {
    return err("L'AI non ha risposto, riprova", 502);
  }
  return err("Non trovato", 404);
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
  // CORS aperto: l'API è usata anche dal player web separato (cartella web/). L'autenticazione è nel token, non nei cookie.
  async fetch(req, env) {
    const cors = { "access-control-allow-origin": "*", "access-control-allow-headers": "authorization,content-type,range,x-admin-key", "access-control-expose-headers": "content-range,content-length,accept-ranges", "access-control-allow-methods": "GET,POST,DELETE,OPTIONS" };
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const r = await this.handle(req, env);
    const h = new Headers(r.headers);
    for (const k in cors) h.set(k, cors[k]);
    return new Response(r.body, { status: r.status, headers: h });
  },
  async handle(req, env) {
    const url = new URL(req.url);
    const path = url.pathname;
    try {
      if (req.method === "POST" && path === "/v1/code") {
        const { email: raw } = await req.json();
        if (!validEmail(raw)) return err("Email non valida");
        const email = raw.trim().toLowerCase();
        const eh = await sha(email);
        const now = Date.now();
        const cip = (await sha(req.headers.get("cf-connecting-ip") || "x")).slice(0, 24);
        if (await tooMany(env, "code:" + cip, 8)) return err("Troppe richieste, riprova più tardi", 429);
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

      if (req.method === "GET" && path === "/v1/web/search") return await webSearch(req, env, url);
      if (path.startsWith("/v1/admin/")) return await adminRoutes(req, env, path, url);
      if (path.startsWith("/v1/id/ded")) return await dedRoutes(req, env, path);
      if (path.startsWith("/v1/id")) return await sonoraId(req, env, path);
      if (path.startsWith("/v1/ai/")) return await aiRoutes(req, env, path);

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
