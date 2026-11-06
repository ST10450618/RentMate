// Turns a Supabase login into a Firebase custom token (RM-0032).
//
// The app signs in with Supabase Auth. Firestore only understands Firebase Auth, so the
// app calls this function with its Supabase access token and gets back a Firebase custom
// token. The token's uid is the Supabase user id. Its `households` claim lists the
// households the user belongs to, and firestore.rules reads that claim.
//
// Secrets (set with `supabase secrets set`): FIREBASE_SERVICE_ACCOUNT, FIREBASE_PROJECT_ID.
// SUPABASE_URL and SUPABASE_ANON_KEY are provided by the Supabase runtime.

import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

const AUDIENCE =
  "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";
const TOKEN_LIFETIME_SECONDS = 3600;

const JSON_HEADERS = { "Content-Type": "application/json" };

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

/** Signs a Firebase custom token (RS256) with the service account key. */
export async function mintCustomToken(
  serviceAccount: { client_email: string; private_key: string },
  uid: string,
  households: string[],
  nowSeconds = Math.floor(Date.now() / 1000),
): Promise<string> {
  const key = await importPKCS8(serviceAccount.private_key, "RS256");
  return await new SignJWT({ uid, claims: { households } })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(serviceAccount.client_email)
    .setSubject(serviceAccount.client_email)
    .setAudience(AUDIENCE)
    .setIssuedAt(nowSeconds)
    .setExpirationTime(nowSeconds + TOKEN_LIFETIME_SECONDS)
    .sign(key);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return reply({ error: "method not allowed" }, 405);

  const authorization = req.headers.get("Authorization");
  if (!authorization) return reply({ error: "missing Authorization header" }, 401);

  // A client that carries the caller's JWT, so row level security applies to every query.
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authorization } } },
  );

  const jwt = authorization.replace(/^Bearer\s+/i, "");
  const { data: userData, error: userError } = await supabase.auth.getUser(jwt);
  if (userError || !userData.user) return reply({ error: "invalid session" }, 401);
  const uid = userData.user.id;

  const { data: memberships, error: memberError } = await supabase
    .from("household_members")
    .select("household_id")
    .eq("user_id", uid);
  if (memberError) {
    console.error("household lookup failed", memberError.message);
    return reply({ error: "could not load households" }, 500);
  }
  const households = (memberships ?? []).map((m: { household_id: string }) => m.household_id);

  try {
    const serviceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
    const token = await mintCustomToken(serviceAccount, uid, households);
    return reply({ token });
  } catch (e) {
    console.error("could not mint Firebase token", (e as Error).message);
    return reply({ error: "could not create token" }, 500);
  }
});
