// Sends push notifications through Firebase Cloud Messaging (RM-0022).
//
// The database calls this function (see migration 0016) when a bill is added, a
// repair request changes status, or a chore is due. It is not called by the app.
// Callers prove who they are with the `x-push-secret` header, so verify_jwt is off.
//
// Secrets (set with `supabase secrets set`): PUSH_WEBHOOK_SECRET, FIREBASE_SERVICE_ACCOUNT,
// FIREBASE_PROJECT_ID. SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY come from the runtime.

import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";
import { buildFcmMessage, shouldRemoveToken, toggleColumn, validatePushRequest } from "./push.ts";

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const JWT_LIFETIME_SECONDS = 3600;
const REFRESH_MARGIN_SECONDS = 300;

const JSON_HEADERS = { "Content-Type": "application/json" };

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

/** Compares two strings in constant time by hashing them first. */
async function safeEqual(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [ha, hb] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b)),
  ]);
  const x = new Uint8Array(ha);
  const y = new Uint8Array(hb);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

// The access token lives as long as the warm function instance.
let cached: { token: string; expiresAt: number } | null = null;

async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.expiresAt - REFRESH_MARGIN_SECONDS > now) return cached.token;

  const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
  const key = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({ scope: SCOPE })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(TOKEN_URL)
    .setIssuedAt(now)
    .setExpirationTime(now + JWT_LIFETIME_SECONDS)
    .sign(key);

  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`token exchange failed with status ${res.status}`);
  const json = await res.json();
  cached = { token: json.access_token, expiresAt: now + (json.expires_in ?? JWT_LIFETIME_SECONDS) };
  return cached.token;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return reply({ error: "method not allowed" }, 405);

  const expected = Deno.env.get("PUSH_WEBHOOK_SECRET");
  const given = req.headers.get("x-push-secret");
  if (!expected || !given || !(await safeEqual(given, expected))) {
    return reply({ error: "unauthorized" }, 401);
  }

  let input: unknown;
  try {
    input = await req.json();
  } catch {
    return reply({ error: "body must be JSON" }, 400);
  }
  const parsed = validatePushRequest(input);
  if (!parsed.ok) return reply({ error: parsed.error }, 400);
  const request = parsed.value;

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Drop people who switched this kind of notification off.
  const { data: profiles, error: profileError } = await supabase
    .from("profiles")
    .select("id")
    .in("id", request.user_ids)
    .eq(toggleColumn(request.channel), true);
  if (profileError) {
    console.error("profile lookup failed", profileError.message);
    return reply({ error: "could not load profiles" }, 500);
  }
  const wanted = (profiles ?? []).map((p: { id: string }) => p.id);
  if (wanted.length === 0) return reply({ sent: 0, failed: 0, removed: 0 });

  const { data: rows, error: tokenError } = await supabase
    .from("device_tokens")
    .select("token")
    .in("user_id", wanted);
  if (tokenError) {
    console.error("token lookup failed", tokenError.message);
    return reply({ error: "could not load device tokens" }, 500);
  }
  const tokens = (rows ?? []).map((r: { token: string }) => r.token);
  if (tokens.length === 0) return reply({ sent: 0, failed: 0, removed: 0 });

  let accessToken: string;
  try {
    accessToken = await getAccessToken();
  } catch (e) {
    console.error("could not get a Google access token", (e as Error).message);
    return reply({ error: "could not authenticate with Firebase" }, 500);
  }

  const url = `https://fcm.googleapis.com/v1/projects/${Deno.env.get("FIREBASE_PROJECT_ID")}/messages:send`;
  const results = await Promise.all(tokens.map(async (token) => {
    try {
      const res = await fetch(url, {
        method: "POST",
        headers: { ...JSON_HEADERS, Authorization: `Bearer ${accessToken}` },
        body: JSON.stringify(buildFcmMessage(token, request)),
      });
      const body = res.ok ? null : await res.json().catch(() => null);
      if (!res.ok) console.error("FCM rejected a message", res.status);
      return { token, ok: res.ok, remove: shouldRemoveToken(res.status, body) };
    } catch (e) {
      console.error("FCM request failed", (e as Error).message);
      return { token, ok: false, remove: false };
    }
  }));

  const dead = results.filter((r) => r.remove).map((r) => r.token);
  if (dead.length > 0) {
    const { error } = await supabase.from("device_tokens").delete().in("token", dead);
    if (error) console.error("could not delete dead tokens", error.message);
  }

  return reply({
    sent: results.filter((r) => r.ok).length,
    failed: results.filter((r) => !r.ok).length,
    removed: dead.length,
  });
});
