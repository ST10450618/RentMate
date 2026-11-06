// Run with: node --test supabase/functions/send-push/push.test.ts
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  buildFcmMessage,
  CHANNELS,
  shouldRemoveToken,
  toggleColumn,
  validatePushRequest,
} from "./push.ts";

const A = "00000000-0000-0000-0000-00000000000a";
const B = "00000000-0000-0000-0000-00000000000b";
const good = { user_ids: [A, B], channel: "bills", title: "New bill: Electricity", body: "Thandi added R1 200,00.", id: A };

test("accepts a good payload", () => {
  const r = validatePushRequest(good);
  assert.equal(r.ok, true);
  if (r.ok) assert.deepEqual(r.value, good);
});

test("treats a missing or empty id as null and removes duplicate users", () => {
  const r = validatePushRequest({ ...good, user_ids: [A, A.toUpperCase()], id: undefined });
  assert.equal(r.ok, true);
  if (r.ok) {
    assert.equal(r.value.id, null);
    assert.deepEqual(r.value.user_ids, [A]);
  }
});

test("allows an empty body and a 120 character title", () => {
  assert.equal(validatePushRequest({ ...good, body: "" }).ok, true);
  assert.equal(validatePushRequest({ ...good, title: "t".repeat(120) }).ok, true);
});

test("rejects bad payloads", () => {
  const bad: unknown[] = [
    null,
    "text",
    [],
    { ...good, user_ids: [] },
    { ...good, user_ids: "x" },
    { ...good, user_ids: ["not-a-uuid"] },
    { ...good, user_ids: Array.from({ length: 51 }, (_, i) => `00000000-0000-0000-0000-${String(i).padStart(12, "0")}`) },
    { ...good, channel: "billing" },
    { ...good, channel: undefined },
    { ...good, title: "" },
    { ...good, title: "t".repeat(121) },
    { ...good, title: 5 },
    { ...good, body: "b".repeat(1001) },
    { ...good, body: null },
    { ...good, id: "i".repeat(65) },
    { ...good, id: 7 },
  ];
  for (const b of bad) assert.equal(validatePushRequest(b).ok, false, JSON.stringify(b)?.slice(0, 80));
});

test("accepts 50 users", () => {
  const ids = Array.from({ length: 50 }, (_, i) => `00000000-0000-0000-0000-${String(i).padStart(12, "0")}`);
  assert.equal(validatePushRequest({ ...good, user_ids: ids }).ok, true);
});

test("message is data-only, all strings, high priority", () => {
  const r = validatePushRequest(good);
  assert.ok(r.ok);
  const m = buildFcmMessage("tok", r.value).message;
  assert.equal(m.token, "tok");
  assert.deepEqual(m.data, { channel: "bills", title: good.title, body: good.body, id: A });
  assert.equal("notification" in m, false);
  assert.equal(m.android.priority, "high");
  for (const v of Object.values(m.data)) assert.equal(typeof v, "string");
});

test("message omits id when it is null", () => {
  const r = validatePushRequest({ ...good, id: null });
  assert.ok(r.ok);
  assert.equal("id" in buildFcmMessage("tok", r.value).message.data, false);
});

test("channel maps to the profile toggle", () => {
  assert.equal(toggleColumn("bills"), "notify_bills");
  assert.equal(toggleColumn("chores"), "notify_chores");
  assert.equal(toggleColumn("shopping"), "notify_shopping_list");
  assert.equal(toggleColumn("maintenance"), "notify_maintenance");
  assert.equal(CHANNELS.length, 4);
});

test("removes tokens FCM reports as gone", () => {
  assert.equal(shouldRemoveToken(404, null), true);
  assert.equal(shouldRemoveToken(404, { error: { status: "NOT_FOUND" } }), true);
  assert.equal(shouldRemoveToken(400, { error: { status: "UNREGISTERED" } }), true);
  assert.equal(shouldRemoveToken(400, { error: { status: "INVALID_ARGUMENT", details: [{ errorCode: "UNREGISTERED" }] } }), true);
});

test("keeps tokens on success and on other errors", () => {
  assert.equal(shouldRemoveToken(200, { name: "projects/p/messages/1" }), false);
  assert.equal(shouldRemoveToken(400, { error: { status: "INVALID_ARGUMENT" } }), false);
  assert.equal(shouldRemoveToken(401, { error: { status: "UNAUTHENTICATED" } }), false);
  assert.equal(shouldRemoveToken(429, { error: { status: "RESOURCE_EXHAUSTED" } }), false);
  assert.equal(shouldRemoveToken(500, null), false);
});
