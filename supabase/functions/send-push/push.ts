// Pure helpers for the send-push function. No Deno globals, so Node can test them.

export const CHANNELS = ["bills", "chores", "shopping", "maintenance"] as const;
export type Channel = (typeof CHANNELS)[number];

export type PushRequest = {
  user_ids: string[];
  channel: Channel;
  title: string;
  body: string;
  id: string | null;
};

export type Validation =
  | { ok: true; value: PushRequest }
  | { ok: false; error: string };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Which profiles column switches each channel off.
const TOGGLE_COLUMNS: Record<Channel, string> = {
  bills: "notify_bills",
  chores: "notify_chores",
  shopping: "notify_shopping_list",
  maintenance: "notify_maintenance",
};

/** The profiles column that holds the user's on/off choice for a channel. */
export function toggleColumn(channel: Channel): string {
  return TOGGLE_COLUMNS[channel];
}

/** Checks the request body. Returns the cleaned value or the first problem found. */
export function validatePushRequest(input: unknown): Validation {
  if (typeof input !== "object" || input === null || Array.isArray(input)) {
    return { ok: false, error: "body must be a JSON object" };
  }
  const o = input as Record<string, unknown>;

  if (!Array.isArray(o.user_ids) || o.user_ids.length < 1 || o.user_ids.length > 50) {
    return { ok: false, error: "user_ids must hold 1 to 50 ids" };
  }
  if (!o.user_ids.every((u) => typeof u === "string" && UUID.test(u))) {
    return { ok: false, error: "user_ids must all be uuids" };
  }
  if (typeof o.channel !== "string" || !(CHANNELS as readonly string[]).includes(o.channel)) {
    return { ok: false, error: "channel must be bills, chores, shopping or maintenance" };
  }
  if (typeof o.title !== "string" || o.title.length < 1 || o.title.length > 120) {
    return { ok: false, error: "title must be 1 to 120 characters" };
  }
  if (typeof o.body !== "string" || o.body.length > 1000) {
    return { ok: false, error: "body must be a string of at most 1000 characters" };
  }
  if (o.id !== null && o.id !== undefined && (typeof o.id !== "string" || o.id.length > 64)) {
    return { ok: false, error: "id must be null or a string of at most 64 characters" };
  }

  return {
    ok: true,
    value: {
      user_ids: [...new Set((o.user_ids as string[]).map((u) => u.toLowerCase()))],
      channel: o.channel as Channel,
      title: o.title,
      body: o.body,
      id: typeof o.id === "string" && o.id !== "" ? o.id : null,
    },
  };
}

/** Builds the data-only FCM message the app expects (see migration 0015). */
export function buildFcmMessage(token: string, req: PushRequest) {
  const data: Record<string, string> = {
    channel: req.channel,
    title: req.title,
    body: req.body,
  };
  if (req.id !== null) data.id = req.id;
  return { message: { token, data, android: { priority: "high" } } };
}

/** True when FCM says the token is dead and the row should be deleted. */
export function shouldRemoveToken(status: number, responseBody: unknown): boolean {
  if (status === 404) return true;
  if (status < 400) return false;
  const err = (responseBody as { error?: { status?: string; details?: { errorCode?: string }[] } } | null)?.error;
  if (!err) return false;
  if (err.status === "NOT_FOUND" || err.status === "UNREGISTERED") return true;
  return (err.details ?? []).some((d) => d?.errorCode === "UNREGISTERED");
}
