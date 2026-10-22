// The one way an event gets into the database.
//
// Why a function rather than letting the app insert directly:
//
// **The salted hash is computed by the database, not here.** The standard says `user_id` is
// stored only as a salted hash. If the app hashed it, the app would hold the salt, and a salt the
// client holds protects nothing. So the app sends the device code, and a trigger fills the hash
// from a salt in a table with no API policies at all — which also means a client that forgets to
// send a hash still gets one.
//
// **The redaction is repeated here.** The client redacts; this does not trust that it did. A
// client is a thing you ship once and cannot fix; this is a thing you can.
//
// Deno.serve is the whole surface: one POST, one shape, no reads.

import { createClient } from "jsr:@supabase/supabase-js@2";

/** The most events one request may carry. A batch is a batch, not a backup. */
const MAX_EVENTS = 100;

/** The most requests one address may make in a minute. */
const MAX_REQUESTS_PER_MINUTE = 30;

/** The envelope, exactly. Anything else in an event object is dropped, not stored. */
const ALLOWED_FIELDS = new Set([
  "event_name", "schema_version", "timestamp", "session_id", "attempt_id", "user_code",
  "platform", "app_version", "os", "device_model", "network_type", "screen_or_route",
  "log_level", "release_channel", "details",
]);

const EVENT_NAMES = new Set([
  "session_start", "screen_view", "permission_state_resolved",
  "access_fix_requested", "access_fix_result", "media_query_result",
  "api_request", "crash", "anr", "performance_marker", "error_state_shown",
  "rage_tap", "dead_click", "permission_loop", "back_button_spam",
  "scroll_depth", "drop_off", "time_to_first_value", "consent_changed",
]);

const LEVELS = new Set(["debug", "info", "warn", "error"]);
const PLATFORMS = new Set(["android", "web"]);
const RELEASES = new Set(["production", "beta"]);
const DEVICES = new Set(["mobile", "tablet", "desktop"]);

/** A detail key that names something we promised never to collect. */
const FORBIDDEN_KEYS = new Set([
  "email", "phone", "name", "first_name", "last_name", "contact",
  "path", "file", "filename", "file_name", "filepath", "uri", "url",
  "query", "search", "search_query", "token", "access_token", "refresh_token",
  "password", "secret", "key", "authorization", "cookie",
  "location", "latitude", "longitude", "lat", "lon", "address", "gps",
]);

/** A detail value that *is* personal, whatever it is called. */
const PERSONAL_VALUE = [
  /(^|\/)(storage|sdcard|Android|data|Download|DCIM)\//,
  /^content:\/\//,
  /^file:\/\//,
  /^https?:\/\/[^ ]*\?/,
  /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/,
  /\+?[0-9][0-9 ()-]{8,}[0-9]/,
  /\.(jpg|jpeg|png|gif|webp|heic|mp4|mov|mkv|mp3|opus|pdf|docx|xlsx|zip)$/i,
];

const CODE_SHAPE = /^[A-Z]{2}-[0-9A-Z]{3}-[0-9A-Z]{3}$/;
const SESSION_SHAPE = /^sess_[0-9a-f]{16}$/;

/** A crude in-instance limiter. Not a security boundary — a courtesy to the database. */
const hits = new Map<string, { count: number; windowStart: number }>();

function overLimit(address: string): boolean {
  const now = Date.now();
  const entry = hits.get(address);
  if (entry === undefined || now - entry.windowStart > 60_000) {
    hits.set(address, { count: 1, windowStart: now });
    return false;
  }
  entry.count += 1;
  return entry.count > MAX_REQUESTS_PER_MINUTE;
}

function looksPersonal(value: string): boolean {
  return PERSONAL_VALUE.some((pattern) => pattern.test(value));
}

/**
 * Keeps only what the standard allows.
 *
 * Returns null when the event cannot be kept at all — an unknown name, a missing required field,
 * a detail that is personal. A rejected event is **dropped, not repaired**: guessing what a
 * client meant is how something personal ends up stored.
 */
function clean(raw: Record<string, unknown>): Record<string, unknown> | null {
  const event: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(raw)) {
    if (ALLOWED_FIELDS.has(key)) event[key] = value;
  }

  const name = String(event.event_name ?? "");
  const level = String(event.log_level ?? "");
  const code = String(event.user_code ?? "");
  const session = String(event.session_id ?? "");

  if (!EVENT_NAMES.has(name)) return null;
  if (!LEVELS.has(level)) return null;
  if (!CODE_SHAPE.test(code)) return null;
  if (!SESSION_SHAPE.test(session)) return null;

  const platform = String(event.platform ?? "android");
  const release = String(event.release_channel ?? "production");
  const device = String(event.device_model ?? "mobile");
  if (!PLATFORMS.has(platform) || !RELEASES.has(release) || !DEVICES.has(device)) return null;

  // The details, key by key and value by value.
  const details: Record<string, string> = {};
  const rawDetails = event.details;
  if (rawDetails !== null && typeof rawDetails === "object") {
    for (const [key, value] of Object.entries(rawDetails as Record<string, unknown>)) {
      if (FORBIDDEN_KEYS.has(key.toLowerCase())) return null;
      if (typeof value !== "string" && typeof value !== "number" && typeof value !== "boolean") {
        return null;
      }
      const text = String(value);
      if (looksPersonal(text)) return null;
      // Long free text is where something personal hides. A detail is a value, not a story.
      if (text.length > 120) return null;
      details[key] = text;
    }
  }

  const screen = event.screen_or_route === null || event.screen_or_route === undefined
    ? null
    : String(event.screen_or_route);
  if (screen !== null && (screen.includes("/") || screen.includes("\\") || screen.length > 64)) {
    return null;
  }

  const timestamp = String(event.timestamp ?? "");
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/.test(timestamp)) return null;

  return {
    timestamp,
    schema_version: String(event.schema_version ?? "1.0.0"),
    event_name: name,
    log_level: level,
    session_id: session,
    attempt_id: event.attempt_id === null || event.attempt_id === undefined
      ? null
      : String(event.attempt_id).slice(0, 32),
    user_code: code,
    platform,
    app_version: String(event.app_version ?? "unknown").slice(0, 32),
    os: String(event.os ?? "unknown").slice(0, 32),
    device_model: device,
    network_type: event.network_type === null || event.network_type === undefined
      ? null
      : String(event.network_type).slice(0, 16),
    screen_or_route: screen,
    release_channel: release,
    details,
  };
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST only" }), { status: 405 });
  }

  const address = request.headers.get("x-forwarded-for") ?? "unknown";
  if (overLimit(address)) {
    return new Response(JSON.stringify({ error: "too many requests" }), { status: 429 });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) {
    // Said plainly rather than pretending to accept and dropping the data.
    return new Response(JSON.stringify({ error: "the ingest function is not configured" }), {
      status: 500,
    });
  }

  let body: { events?: unknown };
  try {
    body = await request.json();
  } catch {
    return new Response(JSON.stringify({ error: "invalid JSON" }), { status: 400 });
  }

  const incoming = Array.isArray(body.events) ? body.events : [];
  if (incoming.length === 0) {
    return new Response(JSON.stringify({ accepted: 0, rejected: 0 }), { status: 200 });
  }
  if (incoming.length > MAX_EVENTS) {
    return new Response(JSON.stringify({ error: `at most ${MAX_EVENTS} events per request` }), {
      status: 413,
    });
  }

  const rows: Record<string, unknown>[] = [];
  let rejected = 0;
  for (const raw of incoming) {
    if (raw === null || typeof raw !== "object") {
      rejected += 1;
      continue;
    }
    const row = clean(raw as Record<string, unknown>);
    if (row === null) {
      rejected += 1;
      continue;
    }
    // `user_id_hash` is deliberately not set here: the database fills it from a trigger, because
    // the salt lives in a table this function cannot read. A hash computed here would need the
    // salt here, and a salt in one more place is one more place to leak it.
    rows.push(row);
  }

  if (rows.length === 0) {
    return new Response(JSON.stringify({ accepted: 0, rejected }), { status: 200 });
  }

  const client = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { error } = await client.from("log_events").insert(rows);
  if (error) {
    return new Response(JSON.stringify({ error: "the events could not be stored" }), { status: 500 });
  }

  return new Response(JSON.stringify({ accepted: rows.length, rejected }), { status: 200 });
});
