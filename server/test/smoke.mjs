// End-to-end smoke test for the TwoChat server: REST auth + pairing + messages,
// then the WebSocket channel (delivery, read receipts, typing, presence).
//
//   node test/smoke.mjs            # against http://127.0.0.1:3000
//   BASE=http://host:port node test/smoke.mjs
import assert from "node:assert/strict";
import WebSocket from "ws";

const BASE = process.env.BASE || "http://127.0.0.1:3000";
const WS_BASE = BASE.replace(/^http/, "ws");

async function api(path, { method = "GET", body, token } = {}) {
  const headers = {};
  if (body !== undefined) headers["content-type"] = "application/json";
  if (token) headers.authorization = `Bearer ${token}`;
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json().catch(() => null);
  return { status: response.status, data };
}

const suffix = Math.random().toString(36).slice(2, 7);
const steps = [];
const step = (name, extra = "") => steps.push(extra ? `${name} — ${extra}` : name);

const health = await api("/api/health");
assert.equal(health.status, 200);
step("health");

const register = (who) =>
  api("/api/auth/register", {
    method: "POST",
    body: { username: `${who}_${suffix}`, password: "secret123", displayName: who === "a" ? "Minh" : "An" },
  });

const alice = await register("a");
const bob = await register("b");
assert.equal(alice.status, 201, JSON.stringify(alice.data));
assert.equal(bob.status, 201, JSON.stringify(bob.data));
assert.ok(alice.data.user.inviteCode, "invite code");
step("register two accounts");

const badLogin = await api("/api/auth/login", { method: "POST", body: { username: alice.data.user.username, password: "wrong-pass" } });
assert.equal(badLogin.status, 401);
step("reject wrong password");

const login = await api("/api/auth/login", { method: "POST", body: { username: alice.data.user.username, password: "secret123" } });
assert.equal(login.status, 200);
assert.ok(login.data.token);
step("login");

const aToken = login.data.token;
const bToken = bob.data.token;

// WebSocket connections for both sides.
function openSocket(token) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(`${WS_BASE}/ws?token=${token}`);
    const events = [];
    socket.on("message", (raw) => {
      try {
        events.push(JSON.parse(raw.toString()));
      } catch {
        /* ignore */
      }
    });
    socket.once("open", () => resolve({ socket, events }));
    socket.once("error", reject);
    setTimeout(() => reject(new Error("websocket open timeout")), 4000);
  });
}

const waitFor = async (events, predicate, label, timeout = 4000) => {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    const found = events.find(predicate);
    if (found) return found;
    await new Promise((resolve) => setTimeout(resolve, 40));
  }
  throw new Error(`timeout waiting for ${label}`);
};

const aSocket = await openSocket(aToken);
const bSocket = await openSocket(bToken);
assert.equal((await waitFor(aSocket.events, (e) => e.type === "hello", "hello")).type, "hello");
step("websocket handshake");

const pair = await api("/api/pair", { method: "POST", body: { code: bob.data.user.inviteCode }, token: aToken });
assert.equal(pair.status, 200, JSON.stringify(pair.data));
assert.equal(pair.data.partner.id, bob.data.user.id);
step("pair with invite code");

await waitFor(bSocket.events, (e) => e.type === "paired", "paired event");
step("partner notified of pairing");

const first = await api("/api/messages", { method: "POST", body: { body: "Chào em 👋", clientId: "c1" }, token: aToken });
assert.equal(first.status, 201, JSON.stringify(first.data));
step("send message");

const delivered = await waitFor(bSocket.events, (e) => e.type === "message" && e.message.clientId === "c1", "message delivery");
assert.equal(delivered.message.body, "Chào em 👋");
step("realtime delivery");

// Idempotency: the same clientId must not create a second row.
const duplicate = await api("/api/messages", { method: "POST", body: { body: "Chào em 👋", clientId: "c1" }, token: aToken });
assert.equal(duplicate.status, 200);
assert.equal(duplicate.data.message.id, first.data.message.id);
step("idempotent send");

const reply = await api("/api/messages", { method: "POST", body: { body: "Dạ anh", clientId: "c2" }, token: bToken });
assert.equal(reply.status, 201);
step("reply from partner");

const read = await api("/api/read", { method: "POST", body: { lastMessageId: first.data.message.id }, token: bToken });
assert.equal(read.status, 200);
const receipt = await waitFor(aSocket.events, (e) => e.type === "read" && e.userId === bob.data.user.id, "read receipt");
assert.ok(receipt.readAt > 0);
step("read receipt");

bSocket.socket.send(JSON.stringify({ type: "typing" }));
await waitFor(aSocket.events, (e) => e.type === "typing" && e.userId === bob.data.user.id, "typing");
step("typing indicator");

const conversation = await api("/api/conversation", { token: aToken });
assert.equal(conversation.status, 200);
assert.equal(conversation.data.messages.length, 2);
assert.equal(conversation.data.messages[0].body, "Chào em 👋");
const firstMessage = conversation.data.messages[0];
assert.ok(firstMessage.readAt, "first message is marked read");
step("history with read state");

// Presence: closing Bob's socket notifies Alice.
bSocket.socket.close();
await waitFor(aSocket.events, (e) => e.type === "presence" && e.userId === bob.data.user.id && e.online === false, "presence offline");
step("presence offline");

const unpair = await api("/api/pair/reset", { method: "POST", token: aToken });
assert.equal(unpair.status, 200);
const empty = await api("/api/conversation", { token: aToken });
assert.equal(empty.data.messages.length, 0);
step("unpair clears history");

aSocket.socket.close();

console.log(`✅ TwoChat smoke test passed (${steps.length} checks)\n`);
for (const line of steps) console.log(`   · ${line}`);
