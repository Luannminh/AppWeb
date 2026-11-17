import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
import { Store } from "./store.js";
import { hashPassword, newInviteCode, newToken, verifyPassword } from "./auth.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, "..", "public");
const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || "0.0.0.0";
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, "..", "data", "twochat.json");
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_BODY_BYTES = 32 * 1024;
const HISTORY_LIMIT = 400;

const ACCENT_COLORS = [0xffe0567b, 0xffe4794f, 0xff8a6bd8, 0xff3a82c4, 0xff2f9e86, 0xffd99a1f];

const store = await new Store(DATA_FILE).load();

/** userId -> Set<WebSocket> (a user can be connected from several devices). */
const sockets = new Map();
const lastTypingAt = new Map();
const authAttempts = new Map();

// --------------------------------------------------------------------- helpers

const json = (res, status, body) => {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload),
    "access-control-allow-origin": "*",
  });
  res.end(payload);
};

const badRequest = (res, message) => json(res, 400, { error: message });

function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(Object.assign(new Error("Dữ liệu gửi lên quá lớn"), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (chunks.length === 0) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(Object.assign(new Error("JSON không hợp lệ"), { status: 400 }));
      }
    });
    req.on("error", reject);
  });
}

const isOnline = (userId) => (sockets.get(userId)?.size ?? 0) > 0;

function findUserById(id) {
  return store.data.users.find((user) => user.id === id) ?? null;
}

function partnerOf(user) {
  if (!user?.pairId) return null;
  const pair = store.data.pairs.find((row) => row.id === user.pairId);
  if (!pair) return null;
  const partnerId = pair.userIds.find((id) => id !== user.id);
  return partnerId ? findUserById(partnerId) : null;
}

function publicUser(user, { self = false } = {}) {
  if (!user) return null;
  const view = {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    color: user.color,
    pairId: user.pairId,
    lastSeen: user.lastSeen,
    online: isOnline(user.id),
  };
  if (self) view.inviteCode = user.inviteCode;
  return view;
}

function publicMessage(message) {
  return {
    id: message.id,
    clientId: message.clientId,
    senderId: message.senderId,
    body: message.body,
    createdAt: message.createdAt,
    readAt: message.readAt,
  };
}

function broadcast(userId, payload) {
  const bucket = sockets.get(userId);
  if (!bucket) return;
  const data = JSON.stringify(payload);
  for (const socket of bucket) {
    if (socket.readyState === socket.OPEN) socket.send(data);
  }
}

async function uniqueInviteCode() {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const code = newInviteCode();
    if (!store.data.users.some((user) => user.inviteCode === code)) return code;
  }
  throw new Error("Không tạo được mã mời");
}

function createSession(userId) {
  const token = newToken();
  const now = Date.now();
  store.data.sessions.push({ token, userId, createdAt: now, expiresAt: now + SESSION_TTL_MS });
  // Keep the session table small.
  if (store.data.sessions.length > 500) {
    store.data.sessions = store.data.sessions
      .filter((session) => session.expiresAt > now)
      .slice(-200);
  }
  return token;
}

function authenticate(req) {
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) return null;
  const session = store.data.sessions.find((row) => row.token === token);
  if (!session || session.expiresAt < Date.now()) return null;
  const user = findUserById(session.userId);
  if (!user) return null;
  return { token, user };
}

function clientIp(req) {
  const forwarded = req.headers["x-forwarded-for"];
  if (typeof forwarded === "string" && forwarded.length > 0) return forwarded.split(",")[0].trim();
  return req.socket.remoteAddress ?? "unknown";
}

function authThrottled(req) {
  const ip = clientIp(req);
  const now = Date.now();
  const entry = authAttempts.get(ip);
  if (!entry || entry.resetAt < now) {
    authAttempts.set(ip, { count: 1, resetAt: now + 10 * 60 * 1000 });
    return false;
  }
  entry.count += 1;
  return entry.count > 40;
}

// ------------------------------------------------------------------- messaging

function markRead(user, lastMessageId) {
  const partner = partnerOf(user);
  if (!partner) return null;
  const readAt = Date.now();
  let changed = false;
  for (const message of store.data.messages) {
    if (
      message.pairId === user.pairId &&
      message.senderId === partner.id &&
      message.id <= lastMessageId &&
      !message.readAt
    ) {
      message.readAt = readAt;
      changed = true;
    }
  }
  if (!changed) return null;
  store.scheduleSave();
  const payload = { type: "read", userId: user.id, lastMessageId, readAt };
  broadcast(user.id, payload);
  broadcast(partner.id, payload);
  return readAt;
}

function conversationFor(user, { limit = HISTORY_LIMIT } = {}) {
  if (!user.pairId) return [];
  const rows = store.data.messages
    .filter((message) => message.pairId === user.pairId)
    .sort((a, b) => a.id - b.id);
  return rows.slice(-limit).map(publicMessage);
}

// ------------------------------------------------------------------ http routes

async function handleApi(req, res, url) {
  const { pathname } = url;
  const method = req.method ?? "GET";

  if (method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "authorization,content-type",
      "access-control-max-age": "86400",
    });
    return res.end();
  }

  if (method === "GET" && pathname === "/api/health") {
    return json(res, 200, { ok: true, uptime: Math.round(process.uptime()) });
  }

  if (method === "POST" && (pathname === "/api/auth/register" || pathname === "/api/auth/login")) {
    if (authThrottled(req)) return json(res, 429, { error: "Quá nhiều lần thử, đợi vài phút nhé" });
    const body = await readJson(req);
    return pathname === "/api/auth/register" ? register(res, body) : login(res, body);
  }

  const session = authenticate(req);
  if (!session) return json(res, 401, { error: "Phiên đăng nhập hết hạn, đăng nhập lại nhé" });
  const { user } = session;

  if (method === "GET" && pathname === "/api/me") {
    return json(res, 200, {
      user: publicUser(user, { self: true }),
      partner: publicUser(partnerOf(user)),
    });
  }

  if (method === "GET" && pathname === "/api/pair/code") {
    return json(res, 200, { code: user.inviteCode });
  }

  if (method === "GET" && pathname === "/api/conversation") {
    return json(res, 200, {
      user: publicUser(user, { self: true }),
      partner: publicUser(partnerOf(user)),
      messages: conversationFor(user),
    });
  }

  if (method === "POST" && pathname === "/api/auth/logout") {
    store.data.sessions = store.data.sessions.filter((row) => row.token !== session.token);
    store.scheduleSave();
    return json(res, 200, { ok: true });
  }

  if (method === "POST" && pathname === "/api/pair") {
    const body = await readJson(req);
    return pair(res, user, body);
  }

  if (method === "POST" && pathname === "/api/pair/reset") {
    return unpair(res, user);
  }

  if (method === "POST" && pathname === "/api/messages") {
    const body = await readJson(req);
    return sendMessage(res, user, body);
  }

  if (method === "POST" && pathname === "/api/read") {
    const body = await readJson(req);
    const readAt = markRead(user, Number(body.lastMessageId) || 0);
    return json(res, 200, { ok: true, readAt });
  }

  if (method === "POST" && pathname === "/api/typing") {
    const partner = partnerOf(user);
    if (partner) broadcast(partner.id, { type: "typing", userId: user.id });
    return json(res, 200, { ok: true });
  }

  if (method === "POST" && pathname === "/api/profile") {
    const body = await readJson(req);
    return updateProfile(res, user, body);
  }

  return json(res, 404, { error: "Không có API này" });
}

async function register(res, body) {
  const username = String(body.username ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");
  const displayName = String(body.displayName ?? "").trim();

  if (!/^[a-z0-9_]{3,20}$/.test(username)) {
    return badRequest(res, "Tên đăng nhập 3-20 ký tự: chữ thường, số, dấu _");
  }
  if (password.length < 6) return badRequest(res, "Mật khẩu cần ít nhất 6 ký tự");
  if (displayName.length > 30) return badRequest(res, "Tên hiển thị tối đa 30 ký tự");
  if (store.data.users.some((user) => user.username === username)) {
    return json(res, 409, { error: "Tên đăng nhập này đã có người dùng" });
  }

  const { salt, hash } = hashPassword(password);
  const user = {
    id: store.nextId("users"),
    username,
    salt,
    hash,
    displayName: displayName || username,
    color: ACCENT_COLORS[(store.data.users.length + 1) % ACCENT_COLORS.length],
    inviteCode: await uniqueInviteCode(),
    pairId: null,
    createdAt: Date.now(),
    lastSeen: Date.now(),
  };
  store.data.users.push(user);
  const token = createSession(user.id);
  store.scheduleSave();
  return json(res, 201, { token, user: publicUser(user, { self: true }), partner: null });
}

async function login(res, body) {
  const username = String(body.username ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");
  const user = store.data.users.find((row) => row.username === username);
  if (!user || !verifyPassword(password, user.salt, user.hash)) {
    return json(res, 401, { error: "Sai tên đăng nhập hoặc mật khẩu" });
  }
  const token = createSession(user.id);
  store.scheduleSave();
  return json(res, 200, {
    token,
    user: publicUser(user, { self: true }),
    partner: publicUser(partnerOf(user)),
  });
}

async function pair(res, user, body) {
  const code = String(body.code ?? "").trim().toUpperCase();
  if (user.pairId) {
    return json(res, 409, { error: "Bạn đã ghép đôi rồi. Huỷ ghép đôi trước đã nhé" });
  }
  if (!code) return badRequest(res, "Nhập mã mời của người ấy");
  const target = store.data.users.find((row) => row.inviteCode === code);
  if (!target || target.id === user.id) return json(res, 404, { error: "Không tìm thấy người dùng với mã này" });
  if (target.pairId) return json(res, 409, { error: "Người này đã ghép đôi với người khác" });

  const pairRow = { id: store.nextId("pairs"), userIds: [user.id, target.id], createdAt: Date.now() };
  store.data.pairs.push(pairRow);
  user.pairId = pairRow.id;
  target.pairId = pairRow.id;
  store.scheduleSave();

  broadcast(user.id, { type: "paired", partner: publicUser(target) });
  broadcast(target.id, { type: "paired", partner: publicUser(user) });
  broadcast(user.id, { type: "presence", userId: target.id, online: isOnline(target.id), lastSeen: target.lastSeen });
  broadcast(target.id, { type: "presence", userId: user.id, online: isOnline(user.id), lastSeen: user.lastSeen });

  return json(res, 200, { partner: publicUser(target) });
}

async function unpair(res, user) {
  const partner = partnerOf(user);
  if (!user.pairId) return json(res, 200, { ok: true });

  const pairId = user.pairId;
  const pairRow = store.data.pairs.find((row) => row.id === pairId);
  store.data.pairs = store.data.pairs.filter((row) => row.id !== pairId);
  store.data.messages = store.data.messages.filter((message) => message.pairId !== pairId);
  for (const userId of pairRow?.userIds ?? [user.id]) {
    const member = findUserById(userId);
    if (member) member.pairId = null;
  }
  store.scheduleSave();

  broadcast(user.id, { type: "unpaired" });
  if (partner) broadcast(partner.id, { type: "unpaired" });
  return json(res, 200, { ok: true });
}

async function sendMessage(res, user, body) {
  const partner = partnerOf(user);
  if (!partner) return json(res, 409, { error: "Bạn chưa ghép đôi với ai" });

  const text = String(body.body ?? "").trim();
  const clientId = String(body.clientId ?? "").slice(0, 64);
  if (!text) return badRequest(res, "Tin nhắn trống");
  if (text.length > 4000) return badRequest(res, "Tin nhắn dài quá 4000 ký tự");

  if (clientId) {
    const existing = store.data.messages.find(
      (message) => message.pairId === user.pairId && message.senderId === user.id && message.clientId === clientId,
    );
    if (existing) return json(res, 200, { message: publicMessage(existing) });
  }

  const message = {
    id: store.nextId("messages"),
    pairId: user.pairId,
    senderId: user.id,
    clientId,
    body: text,
    createdAt: Date.now(),
    readAt: null,
  };
  store.data.messages.push(message);
  store.scheduleSave();

  const payload = { type: "message", message: publicMessage(message) };
  broadcast(user.id, payload);
  broadcast(partner.id, payload);
  return json(res, 201, { message: publicMessage(message) });
}

async function updateProfile(res, user, body) {
  const displayName = String(body.displayName ?? "").trim();
  const color = Number(body.color);
  if (displayName) {
    if (displayName.length > 30) return badRequest(res, "Tên hiển thị tối đa 30 ký tự");
    user.displayName = displayName;
  }
  if (Number.isFinite(color) && ACCENT_COLORS.includes(color)) {
    user.color = color;
  }
  store.scheduleSave();
  const partner = partnerOf(user);
  if (partner) broadcast(partner.id, { type: "profile", user: publicUser(user) });
  return json(res, 200, { user: publicUser(user, { self: true }) });
}

// -------------------------------------------------------------------- websocket

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
  try {
    if (url.pathname.startsWith("/api/")) {
      await handleApi(req, res, url);
      return;
    }
    await serveStatic(res, url.pathname);
  } catch (error) {
    if (!res.headersSent) {
      const status = error?.status ?? 500;
      json(res, status, { error: error?.message ?? "Lỗi máy chủ" });
    }
  }
});

const wss = new WebSocketServer({ server, path: "/ws" });

wss.on("connection", (socket, req) => {
  const url = new URL(req.url ?? "/ws", `http://${req.headers.host ?? "localhost"}`);
  const token = url.searchParams.get("token") ?? "";
  const session = store.data.sessions.find((row) => row.token === token && row.expiresAt > Date.now());
  const user = session ? findUserById(session.userId) : null;
  if (!user) {
    socket.close(4001, "unauthorized");
    return;
  }

  const bucket = sockets.get(user.id) ?? new Set();
  sockets.set(user.id, bucket);
  bucket.add(socket);
  user.lastSeen = Date.now();
  socket.isAlive = true;
  socket.on("pong", () => {
    socket.isAlive = true;
  });

  const partner = partnerOf(user);
  socket.send(
    JSON.stringify({
      type: "hello",
      user: publicUser(user, { self: true }),
      partner: publicUser(partner),
      messages: conversationFor(user),
    }),
  );
  if (partner) {
    broadcast(partner.id, {
      type: "presence",
      userId: user.id,
      online: true,
      lastSeen: user.lastSeen,
    });
  }

  socket.on("message", (raw) => {
    let payload;
    try {
      payload = JSON.parse(String(raw));
    } catch {
      return;
    }
    const partnerNow = partnerOf(user);
    if (!partnerNow) return;

    if (payload.type === "typing") {
      const now = Date.now();
      if ((lastTypingAt.get(user.id) ?? 0) + 700 > now) return;
      lastTypingAt.set(user.id, now);
      broadcast(partnerNow.id, { type: "typing", userId: user.id });
    } else if (payload.type === "read") {
      markRead(user, Number(payload.lastMessageId) || 0);
    } else if (payload.type === "ping") {
      socket.send(JSON.stringify({ type: "pong" }));
    }
  });

  socket.on("close", () => {
    bucket.delete(socket);
    if (bucket.size === 0) sockets.delete(user.id);
    user.lastSeen = Date.now();
    store.scheduleSave();
    const partnerNow = partnerOf(user);
    if (partnerNow) {
      broadcast(partnerNow.id, {
        type: "presence",
        userId: user.id,
        online: isOnline(user.id),
        lastSeen: user.lastSeen,
      });
    }
  });

  socket.on("error", () => socket.terminate());
});

const heartbeat = setInterval(() => {
  for (const socket of wss.clients) {
    if (socket.isAlive === false) {
      socket.terminate();
      continue;
    }
    socket.isAlive = false;
    socket.ping();
  }
}, 30_000);

// ----------------------------------------------------------------- static files

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".webmanifest": "application/manifest+json",
};

async function serveStatic(res, rawPath) {
  const pathname = decodeURIComponent(rawPath) === "/" ? "/index.html" : decodeURIComponent(rawPath);
  const filePath = path.join(PUBLIC_DIR, path.normalize(pathname).replace(/^(\.\.[/\\])+/, ""));
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403).end("Forbidden");
    return;
  }
  try {
    const data = await readFile(filePath);
    res.writeHead(200, {
      "content-type": MIME[path.extname(filePath)] ?? "application/octet-stream",
      "cache-control": "no-cache",
    });
    res.end(data);
  } catch {
    res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
    res.end("Không tìm thấy trang");
  }
}

// ------------------------------------------------------------------------ boot

server.listen(PORT, HOST, () => {
  console.log(`TwoChat server đang chạy tại http://${HOST}:${PORT}`);
  console.log(`Dữ liệu: ${DATA_FILE}`);
});

async function shutdown(signal) {
  console.log(`\n[${signal}] đang lưu dữ liệu…`);
  clearInterval(heartbeat);
  for (const socket of wss.clients) socket.close(1001, "server shutting down");
  await store.flush();
  wss.close();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 2000).unref();
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
