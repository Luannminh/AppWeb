import fs from "node:fs/promises";
import path from "node:path";

/**
 * Tiny JSON-file store. Everything (users, pairs, messages, sessions) is kept in
 * memory and flushed to disk after a short debounce, which keeps the chat loop
 * fast while staying durable enough for a self-hosted two-person app.
 */
export class Store {
  constructor(file) {
    this.file = file;
    this.data = emptyData();
    this.writeTimer = null;
    this.pending = null;
  }

  async load() {
    try {
      const raw = await fs.readFile(this.file, "utf8");
      const parsed = JSON.parse(raw);
      this.data = { ...emptyData(), ...parsed };
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    // Drop expired sessions on boot.
    const now = Date.now();
    this.data.sessions = this.data.sessions.filter((s) => s.expiresAt > now);
    return this;
  }

  scheduleSave() {
    if (this.writeTimer) return;
    this.writeTimer = setTimeout(() => {
      this.writeTimer = null;
      this.save().catch((error) => console.error("[store] save failed:", error));
    }, 120);
  }

  async save() {
    if (this.pending) return this.pending;
    this.pending = (async () => {
      await fs.mkdir(path.dirname(this.file), { recursive: true });
      const tmp = `${this.file}.tmp`;
      await fs.writeFile(tmp, JSON.stringify(this.data));
      await fs.rename(tmp, this.file);
    })().finally(() => {
      this.pending = null;
    });
    return this.pending;
  }

  async flush() {
    if (this.writeTimer) {
      clearTimeout(this.writeTimer);
      this.writeTimer = null;
    }
    await this.save();
  }

  nextId(collection) {
    const rows = this.data[collection];
    let max = 0;
    for (const row of rows) if (row.id > max) max = row.id;
    return max + 1;
  }
}

function emptyData() {
  return { users: [], pairs: [], messages: [], sessions: [] };
}
