// UI-level test for the web client: boots public/app.js inside jsdom against a
// live server, then drives the real screens (register → pair → send → read).
//
//   node test/web-client.mjs        (server must be running)
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { webcrypto } from "node:crypto";
import { JSDOM } from "jsdom";
import WebSocket from "ws";

const BASE = process.env.BASE || "http://127.0.0.1:3000";
const PUBLIC_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "public");

const html = await readFile(path.join(PUBLIC_DIR, "index.html"), "utf8");
const appCode = await readFile(path.join(PUBLIC_DIR, "app.js"), "utf8");

const dom = new JSDOM(html, { url: `${BASE}/`, runScripts: "dangerously", pretendToBeVisual: true });
const { window } = dom;
const { document } = window;

window.fetch = (input, init) => fetch(new URL(String(input), BASE), init);
window.WebSocket = WebSocket;
Object.defineProperty(window, "crypto", { value: webcrypto, configurable: true });
window.confirm = () => true;
window.eval(appCode);

const $ = (id) => document.getElementById(id);
const screenVisible = (id) => !$(id).hidden;
const visibleScreen = () => ["screen-auth", "screen-pair", "screen-chat"].find(screenVisible);

async function waitFor(predicate, label, timeout = 6000) {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    const value = predicate();
    if (value) return value;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  throw new Error(`timeout waiting for ${label}`);
}

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
  if (!response.ok) throw new Error(`${path} -> ${response.status} ${JSON.stringify(data)}`);
  return data;
}

const steps = [];
const step = (label) => steps.push(label);
const suffix = Math.random().toString(36).slice(2, 7);

// 1. The client boots on the auth screen.
assert.equal(visibleScreen(), "screen-auth");
step("boots on the login screen");

// 2. Switch to register and create the account through the UI.
const tabs = document.querySelectorAll(".tab");
assert.equal(tabs.length, 2);
tabs[1].dispatchEvent(new window.Event("click", { bubbles: true }));
assert.equal($("auth-displayname-field").hidden, false, "display name field shows for sign-up");

$("auth-username").value = `ui_${suffix}`;
$("auth-displayname").value = "Minh";
$("auth-password").value = "secret123";
$("auth-form").dispatchEvent(new window.Event("submit", { cancelable: true }));

await waitFor(() => visibleScreen() === "screen-pair", "pair screen after register");
assert.match($("pair-code").textContent, /^[A-Z2-9]{6}$/);
step("register via the form → pairing screen with invite code");

const myCode = $("pair-code").textContent;

// 3. The partner account is created over the API and pairs with that code.
const partner = await api("/api/auth/register", {
  method: "POST",
  body: { username: `mate_${suffix}`, password: "secret123", displayName: "An" },
});
const partnerToken = partner.token;
await api("/api/pair", { method: "POST", body: { code: myCode }, token: partnerToken });

await waitFor(() => visibleScreen() === "screen-chat", "chat screen after pairing");
assert.equal($("chat-name").textContent, "An");
step("partner pairs → chat opens, name shown");

// 4. A message from the partner arrives over the realtime channel.
await api("/api/messages", { method: "POST", body: { body: "Chào anh 👋", clientId: `m-${suffix}` }, token: partnerToken });

const theirBubble = await waitFor(
  () => Array.from(document.querySelectorAll(".row.theirs .bubble")).find((node) => node.textContent === "Chào anh 👋"),
  "incoming bubble",
);
assert.ok(theirBubble);
step("incoming message renders in the thread");

// 5. Sending from the UI goes through REST and shows up optimistically.
$("composer-input").value = "Chào em, anh đây";
$("composer").dispatchEvent(new window.Event("submit", { cancelable: true }));

await waitFor(
  () => Array.from(document.querySelectorAll(".row.mine .bubble")).some((node) => node.textContent === "Chào em, anh đây"),
  "outgoing bubble",
);
const conversation = await api("/api/conversation", { token: partnerToken });
assert.ok(
  conversation.messages.some((message) => message.body === "Chào em, anh đây"),
  "partner received the message",
);
step("outgoing message sent from the composer");

// 6. The partner reads it → “Đã xem” appears under my message.
const mine = conversation.messages.find((message) => message.body === "Chào em, anh đây");
await api("/api/read", { method: "POST", body: { lastMessageId: mine.id }, token: partnerToken });
await waitFor(() => document.querySelector(".seen"), "read receipt");
step("read receipt (Đã xem) shows up");

// 7. Presence: online while the partner's socket is open, last-seen afterwards.
const mateSocket = new WebSocket(`${BASE.replace(/^http/, "ws")}/ws?token=${partnerToken}`);
await new Promise((resolve, reject) => {
  mateSocket.once("open", resolve);
  mateSocket.once("error", reject);
});
await waitFor(() => $("chat-status").textContent === "Đang hoạt động", "online status");
step("presence shows the partner online");

mateSocket.close();
await waitFor(
  () => /Vừa mới truy cập|Hoạt động .* trước/.test($("chat-status").textContent),
  "offline status",
);
step("presence falls back to last seen when they disconnect");

window.close();
console.log(`✅ Web client UI test passed (${steps.length} checks)\n`);
for (const line of steps) console.log(`   · ${line}`);
process.exit(0);
