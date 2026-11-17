// TwoChat web client — same REST + WebSocket API the Android app uses.
const TOKEN_KEY = "twochat.token";

const state = {
  token: localStorage.getItem(TOKEN_KEY) || "",
  me: null,
  partner: null,
  messages: [],
  outbox: [],
  typingUntil: 0,
  authMode: "login",
  socket: null,
  socketAttempts: 0,
  pollTimer: null,
  lastReadSentAt: 0,
  reconnectTimer: null,
};

const $ = (id) => document.getElementById(id);

const els = {
  screens: ["screen-auth", "screen-pair", "screen-chat"].map((id) => $(id)),
  authForm: $("auth-form"),
  authUsername: $("auth-username"),
  authPassword: $("auth-password"),
  authDisplayName: $("auth-displayname"),
  authDisplayNameField: $("auth-displayname-field"),
  authSubmit: $("auth-submit"),
  authError: $("auth-error"),
  tabs: Array.from(document.querySelectorAll(".tab")),
  pairAvatar: $("pair-avatar"),
  pairName: $("pair-name"),
  pairCode: $("pair-code"),
  pairCopy: $("pair-copy"),
  pairForm: $("pair-form"),
  pairInput: $("pair-input"),
  pairError: $("pair-error"),
  pairLogout: $("pair-logout"),
  chatAvatar: $("chat-avatar"),
  chatName: $("chat-name"),
  chatStatus: $("chat-status"),
  chatMenu: $("chat-menu"),
  chatMenuPanel: $("chat-menu-panel"),
  menuUnpair: $("menu-unpair"),
  menuLogout: $("menu-logout"),
  thread: $("thread"),
  composer: $("composer"),
  composerInput: $("composer-input"),
  sendButton: $("send-btn"),
  toast: $("toast"),
};

// ------------------------------------------------------------------- utilities

async function api(path, { method = "GET", body, auth = true } = {}) {
  const headers = {};
  if (body !== undefined) headers["content-type"] = "application/json";
  if (auth && state.token) headers.authorization = `Bearer ${state.token}`;
  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }
  if (!response.ok) {
    const error = new Error(data?.error || `Lỗi ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return data;
}

const initials = (name) => (name || "?").trim().slice(0, 1).toUpperCase();

// crypto.randomUUID needs a secure context; fall back for plain http on a LAN.
const newClientId = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `c_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
const colorOf = (argb) => `#${(Number(argb) & 0xffffff).toString(16).padStart(6, "0")}`;

function formatTime(epoch) {
  return new Date(epoch).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

function dayKey(epoch) {
  const date = new Date(epoch);
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
}

function formatDay(epoch) {
  const date = new Date(epoch);
  const today = new Date();
  const startOfDay = (value) => new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
  const diffDays = Math.round((startOfDay(today) - startOfDay(date)) / 86400000);
  if (diffDays === 0) return "Hôm nay";
  if (diffDays === 1) return "Hôm qua";
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  if (date.getFullYear() === today.getFullYear()) return `Ngày ${date.getDate()} tháng ${date.getMonth() + 1}`;
  return `${dd}/${mm}/${date.getFullYear()}`;
}

function relativeLastSeen(epoch) {
  if (!epoch) return "Ngoại tuyến";
  const minutes = Math.floor((Date.now() - epoch) / 60000);
  if (minutes < 1) return "Vừa mới truy cập";
  if (minutes < 60) return `Hoạt động ${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `Hoạt động ${hours} giờ trước`;
  return `Hoạt động ${Math.floor(hours / 24)} ngày trước`;
}

let toastTimer = null;
function toast(message) {
  els.toast.textContent = message;
  els.toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    els.toast.hidden = true;
  }, 2600);
}

function showScreen(id) {
  for (const screen of els.screens) screen.hidden = screen.id !== id;
}

// ------------------------------------------------------------------ auth flow

function setAuthMode(mode) {
  state.authMode = mode;
  for (const tab of els.tabs) tab.classList.toggle("is-active", tab.dataset.mode === mode);
  els.authDisplayNameField.hidden = mode !== "register";
  els.authSubmit.textContent = mode === "register" ? "Tạo tài khoản" : "Đăng nhập";
  els.authPassword.autocomplete = mode === "register" ? "new-password" : "current-password";
  els.authError.textContent = "";
}

async function handleAuthSubmit(event) {
  event.preventDefault();
  els.authError.textContent = "";
  els.authSubmit.disabled = true;
  try {
    const payload = {
      username: els.authUsername.value.trim(),
      password: els.authPassword.value,
    };
    if (state.authMode === "register") payload.displayName = els.authDisplayName.value.trim();
    const path = state.authMode === "register" ? "/api/auth/register" : "/api/auth/login";
    const result = await api(path, { method: "POST", body: payload, auth: false });
    state.token = result.token;
    state.me = result.user;
    state.partner = result.partner || null;
    localStorage.setItem(TOKEN_KEY, result.token);
    els.authPassword.value = "";
    connectSocket();
    if (state.partner) {
      await loadConversation();
      route();
    } else {
      await loadPairCode();
      route();
    }
  } catch (error) {
    els.authError.textContent = error.message;
  } finally {
    els.authSubmit.disabled = false;
  }
}

async function logout() {
  try {
    await api("/api/auth/logout", { method: "POST" });
  } catch {
    /* ignore */
  }
  logoutLocal();
  route();
}

function logoutLocal() {
  state.token = "";
  state.me = null;
  state.partner = null;
  state.messages = [];
  state.outbox = [];
  localStorage.removeItem(TOKEN_KEY);
  stopPolling();
  if (state.socket) {
    state.socket.onclose = null;
    state.socket.close();
    state.socket = null;
  }
  clearTimeout(state.reconnectTimer);
}

// ------------------------------------------------------------------ pair flow

async function loadPairCode() {
  try {
    const result = await api("/api/pair/code");
    els.pairCode.textContent = result.code;
  } catch (error) {
    els.pairError.textContent = error.message;
  }
}

async function handlePairSubmit(event) {
  event.preventDefault();
  els.pairError.textContent = "";
  const code = els.pairInput.value.trim().toUpperCase();
  if (!code) return;
  try {
    const result = await api("/api/pair", { method: "POST", body: { code } });
    state.partner = result.partner;
    state.messages = [];
    await loadConversation();
    els.pairInput.value = "";
    route();
    toast("Đã ghép đôi 🎉");
  } catch (error) {
    els.pairError.textContent = error.message;
  }
}

async function unpair() {
  if (!confirm("Huỷ ghép đôi? Toàn bộ tin nhắn giữa hai người sẽ bị xoá.")) return;
  try {
    await api("/api/pair/reset", { method: "POST" });
    state.partner = null;
    state.messages = [];
    route();
  } catch (error) {
    toast(error.message);
  }
}

// ------------------------------------------------------------------ messaging

async function loadConversation() {
  const result = await api("/api/conversation");
  state.partner = result.partner;
  state.messages = result.messages || [];
  renderThread(true);
}

function upsertMessage(message) {
  const index = state.messages.findIndex(
    (row) => row.id === message.id || (message.clientId && row.clientId === message.clientId),
  );
  if (index >= 0) state.messages[index] = { ...state.messages[index], ...message };
  else state.messages.push(message);
  state.messages.sort((a, b) => a.id - b.id);
}

async function sendMessage(text) {
  const body = text.trim();
  if (!body || !state.partner) return;
  const pending = {
    clientId: newClientId(),
    body,
    createdAt: Date.now(),
    status: "pending",
    senderId: state.me.id,
  };
  state.outbox.push(pending);
  renderThread(true);

  try {
    const result = await api("/api/messages", {
      method: "POST",
      body: { body, clientId: pending.clientId },
    });
    state.outbox = state.outbox.filter((item) => item.clientId !== pending.clientId);
    upsertMessage(result.message);
    renderThread(true);
  } catch (error) {
    pending.status = "failed";
    renderThread(true);
    toast(error.message);
  }
}

function retryMessage(clientId) {
  const pending = state.outbox.find((item) => item.clientId === clientId);
  if (!pending) return;
  state.outbox = state.outbox.filter((item) => item.clientId !== clientId);
  sendMessage(pending.body);
}

let typingSentAt = 0;
async function sendTyping() {
  const now = Date.now();
  if (now - typingSentAt < 1200) return;
  typingSentAt = now;
  try {
    state.socket?.readyState === WebSocket.OPEN
      ? state.socket.send(JSON.stringify({ type: "typing" }))
      : await api("/api/typing", { method: "POST" });
  } catch {
    /* ignore */
  }
}

async function markRead(force = false) {
  const lastFromPartner = [...state.messages].reverse().find((message) => message.senderId !== state.me?.id);
  if (!lastFromPartner) return;
  const now = Date.now();
  if (!force && now - state.lastReadSentAt < 1000) return;
  state.lastReadSentAt = now;
  try {
    if (state.socket?.readyState === WebSocket.OPEN) {
      state.socket.send(JSON.stringify({ type: "read", lastMessageId: lastFromPartner.id }));
    } else {
      await api("/api/read", { method: "POST", body: { lastMessageId: lastFromPartner.id } });
    }
  } catch {
    /* ignore */
  }
}

// --------------------------------------------------------------------- render

function route() {
  if (!state.token || !state.me) {
    showScreen("screen-auth");
    return;
  }
  if (!state.partner) {
    els.pairAvatar.textContent = initials(state.me.displayName);
    els.pairAvatar.style.background = colorOf(state.me.color);
    els.pairName.textContent = state.me.displayName;
    showScreen("screen-pair");
    loadPairCode();
    return;
  }
  showScreen("screen-chat");
  renderChrome();
  renderThread(true);
  markRead();
}

function renderChrome() {
  const partner = state.partner;
  els.chatName.textContent = partner.displayName;
  els.chatAvatar.textContent = initials(partner.displayName);
  els.chatAvatar.style.background = colorOf(partner.color);
  renderStatus();
}

function renderStatus() {
  const partner = state.partner;
  if (!partner) return;
  const typing = state.typingUntil > Date.now();
  els.chatStatus.className = "";
  if (typing) {
    els.chatStatus.classList.add("typing");
    els.chatStatus.innerHTML = 'Đang nhập <span class="typing-dots"><i></i><i></i><i></i></span>';
  } else if (partner.online) {
    els.chatStatus.classList.add("online");
    els.chatStatus.textContent = "Đang hoạt động";
  } else {
    els.chatStatus.classList.add("muted");
    els.chatStatus.textContent = relativeLastSeen(partner.lastSeen);
  }
}

function buildTimeline() {
  const mineId = state.me?.id;
  const rows = [
    ...state.messages.map((message) => ({ ...message, kind: "server" })),
    ...state.outbox.map((item) => ({ ...item, id: null, readAt: null, kind: "outbox" })),
  ].sort((a, b) => (a.createdAt ?? 0) - (b.createdAt ?? 0));

  const lastOwnServerId = [...rows]
    .reverse()
    .find((row) => row.kind === "server" && row.senderId === mineId)?.id;

  const timeline = [];
  let lastDay = null;
  for (const row of rows) {
    const day = dayKey(row.createdAt);
    if (day !== lastDay) {
      timeline.push({ type: "day", createdAt: row.createdAt });
      lastDay = day;
    }
    timeline.push({
      type: "message",
      row,
      showSeen: row.kind === "server" && row.senderId === mineId && row.readAt && row.id === lastOwnServerId,
    });
  }
  return timeline;
}

function renderThread(forceScroll = false) {
  const thread = els.thread;
  const distanceFromBottom = thread.scrollHeight - thread.scrollTop - thread.clientHeight;
  const timeline = buildTimeline();

  thread.replaceChildren();
  if (timeline.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty-thread";
    empty.textContent = "Chưa có tin nhắn nào. Gửi lời chào đầu tiên nhé 👋";
    thread.appendChild(empty);
    return;
  }

  for (const item of timeline) {
    if (item.type === "day") {
      const chip = document.createElement("div");
      chip.className = "day";
      chip.textContent = formatDay(item.createdAt);
      thread.appendChild(chip);
      continue;
    }

    const { row, showSeen } = item;
    const mine = row.senderId === state.me?.id;
    const wrapper = document.createElement("div");
    wrapper.className = `row ${mine ? "mine" : "theirs"}`;

    const bubble = document.createElement("div");
    bubble.className = "bubble" + (row.status ? ` ${row.status}` : "");
    bubble.textContent = row.body;
    wrapper.appendChild(bubble);

    const meta = document.createElement("div");
    meta.className = "meta";
    const time = document.createElement("span");
    time.textContent = formatTime(row.createdAt);
    meta.appendChild(time);

    if (row.status === "failed") {
      const retry = document.createElement("button");
      retry.className = "retry";
      retry.textContent = "Gửi lại";
      retry.addEventListener("click", () => retryMessage(row.clientId));
      meta.appendChild(retry);
    } else if (showSeen) {
      const seen = document.createElement("span");
      seen.className = "seen";
      seen.textContent = "Đã xem";
      meta.appendChild(seen);
    }
    wrapper.appendChild(meta);
    thread.appendChild(wrapper);
  }

  if (forceScroll || distanceFromBottom < 90) thread.scrollTop = thread.scrollHeight;
}

// ------------------------------------------------------- polling fallback

function startPolling() {
  if (state.pollTimer) return;
  state.pollTimer = setInterval(async () => {
    if (!state.token || state.socket) return;
    try {
      const result = await api("/api/conversation");
      if (result.partner) state.partner = result.partner;
      for (const message of result.messages || []) upsertMessage(message);
      if (state.partner) {
        renderChrome();
        renderThread();
      }
    } catch {
      /* keep trying */
    }
  }, 3000);
}

function stopPolling() {
  if (!state.pollTimer) return;
  clearInterval(state.pollTimer);
  state.pollTimer = null;
}

// ------------------------------------------------------------------- websocket

function connectSocket() {
  if (!state.token) return;
  clearTimeout(state.reconnectTimer);
  const protocol = location.protocol === "https:" ? "wss" : "ws";
  const socket = new WebSocket(`${protocol}://${location.host}/ws?token=${encodeURIComponent(state.token)}`);
  state.socket = socket;

  socket.addEventListener("open", () => {
    state.socketAttempts = 0;
    stopPolling();
  });

  socket.addEventListener("message", (event) => {
    let payload;
    try {
      payload = JSON.parse(event.data);
    } catch {
      return;
    }
    handleSocketEvent(payload);
  });

  socket.addEventListener("close", () => {
    state.socket = null;
    if (!state.token) return;
    // The preview proxies and some corporate networks drop WebSockets, so keep
    // the conversation fresh over REST until the socket is back.
    startPolling();
    const delay = Math.min(15000, 700 * 2 ** state.socketAttempts);
    state.socketAttempts += 1;
    state.reconnectTimer = setTimeout(connectSocket, delay);
  });
}

function handleSocketEvent(event) {
  switch (event.type) {
    case "hello": {
      stopPolling();
      state.me = event.user;
      state.partner = event.partner;
      state.messages = event.messages || [];
      route();
      break;
    }
    case "message": {
      const message = event.message;
      upsertMessage(message);
      state.outbox = state.outbox.filter((item) => item.clientId !== message.clientId);
      renderThread(true);
      if (message.senderId !== state.me?.id) markRead();
      break;
    }
    case "read": {
      if (event.userId === state.partner?.id) {
        for (const message of state.messages) {
          if (message.senderId === state.me?.id && message.id <= event.lastMessageId && !message.readAt) {
            message.readAt = event.readAt;
          }
        }
        renderThread();
      }
      break;
    }
    case "typing": {
      if (event.userId === state.partner?.id) {
        state.typingUntil = Date.now() + 3000;
        renderStatus();
        setTimeout(renderStatus, 3100);
      }
      break;
    }
    case "presence": {
      if (event.userId === state.partner?.id) {
        state.partner = { ...state.partner, online: event.online, lastSeen: event.lastSeen };
        renderStatus();
      }
      break;
    }
    case "paired": {
      state.partner = event.partner;
      state.messages = [];
      loadConversation().then(() => {
        route();
        toast("Đã ghép đôi 🎉");
      });
      break;
    }
    case "unpaired": {
      state.partner = null;
      state.messages = [];
      state.outbox = [];
      route();
      toast("Đã huỷ ghép đôi");
      break;
    }
    case "profile": {
      if (event.user?.id === state.partner?.id) {
        state.partner = { ...state.partner, ...event.user };
        renderChrome();
      }
      break;
    }
    default:
      break;
  }
}

// ---------------------------------------------------------------------- events

function bindEvents() {
  for (const tab of els.tabs) tab.addEventListener("click", () => setAuthMode(tab.dataset.mode));
  els.authForm.addEventListener("submit", handleAuthSubmit);
  els.pairForm.addEventListener("submit", handlePairSubmit);
  els.pairLogout.addEventListener("click", logout);
  els.menuLogout.addEventListener("click", logout);
  els.menuUnpair.addEventListener("click", () => {
    els.chatMenuPanel.hidden = true;
    unpair();
  });
  els.chatMenu.addEventListener("click", () => {
    els.chatMenuPanel.hidden = !els.chatMenuPanel.hidden;
  });
  document.addEventListener("click", (event) => {
    if (!els.chatMenuPanel.hidden && !els.chatMenu.contains(event.target) && !els.chatMenuPanel.contains(event.target)) {
      els.chatMenuPanel.hidden = true;
    }
  });

  els.pairCopy.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(els.pairCode.textContent);
      toast("Đã sao chép mã mời");
    } catch {
      toast(`Mã mời: ${els.pairCode.textContent}`);
    }
  });

  els.composer.addEventListener("submit", (event) => {
    event.preventDefault();
    const text = els.composerInput.value;
    els.composerInput.value = "";
    els.composerInput.style.height = "auto";
    sendMessage(text);
  });

  els.composerInput.addEventListener("input", () => {
    const input = els.composerInput;
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 140)}px`;
    if (input.value.trim()) sendTyping();
  });

  els.composerInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      els.composer.dispatchEvent(new Event("submit", { cancelable: true }));
    }
  });

  els.thread.addEventListener("scroll", () => {
    const thread = els.thread;
    if (thread.scrollHeight - thread.scrollTop - thread.clientHeight < 60) markRead();
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") markRead(true);
  });

  window.addEventListener("beforeunload", () => {
    if (state.socket) state.socket.close();
  });
}

async function boot() {
  bindEvents();
  setAuthMode("login");
  if (!state.token) {
    showScreen("screen-auth");
    return;
  }
  try {
    const me = await api("/api/me");
    state.me = me.user;
    state.partner = me.partner;
    connectSocket();
    if (state.partner) await loadConversation();
    route();
  } catch (error) {
    if (error.status === 401) logoutLocal();
    els.authError.textContent = error.status === 401 ? "" : error.message;
    route();
  }
}

boot();
