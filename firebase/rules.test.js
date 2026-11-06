// RM-0032: tests for ../firestore.rules. Run with the Firestore emulator:
//   npx firebase-tools emulators:exec --only firestore --project demo-rentmate "npm test"
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { doc, collection, getDoc, getDocs, addDoc, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } =
  require("firebase/firestore");

const H1 = "household-1";
const H2 = "household-2";
const EVENT_PATH = (h, id) => `households/${h}/events/${id}`;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-rentmate",
    firestore: { rules: fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8") },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), EVENT_PATH(H1, "e1")), validEvent({ createdAt: Timestamp.now() }));
    await setDoc(doc(ctx.firestore(), EVENT_PATH(H2, "e2")), validEvent({ createdAt: Timestamp.now() }));
  });
});

function validEvent(overrides = {}) {
  return {
    type: "bill_added",
    actorName: "Thandi",
    summary: "added a bill: Electricity R1 200",
    refId: "bill-1",
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

const memberOfH1 = () => env.authenticatedContext("user-1", { households: [H1] }).firestore();
const noClaim = () => env.authenticatedContext("user-3").firestore();
const anonymous = () => env.unauthenticatedContext().firestore();
const eventsOf = (db, h) => collection(db, `households/${h}/events`);

test("member reads own household events", async () => {
  await assertSucceeds(getDocs(eventsOf(memberOfH1(), H1)));
  await assertSucceeds(getDoc(doc(memberOfH1(), EVENT_PATH(H1, "e1"))));
});

test("member of two households reads both", async () => {
  const db = env.authenticatedContext("user-2", { households: [H1, H2] }).firestore();
  await assertSucceeds(getDocs(eventsOf(db, H2)));
});

test("non-member cannot read another household", async () => {
  await assertFails(getDocs(eventsOf(memberOfH1(), H2)));
  await assertFails(getDoc(doc(memberOfH1(), EVENT_PATH(H2, "e2"))));
});

test("signed-in user without the claim cannot read", async () => {
  await assertFails(getDocs(eventsOf(noClaim(), H1)));
});

test("unauthenticated user cannot read or write", async () => {
  await assertFails(getDocs(eventsOf(anonymous(), H1)));
  await assertFails(addDoc(eventsOf(anonymous(), H1), validEvent()));
});

test("member creates a valid event", async () => {
  await assertSucceeds(addDoc(eventsOf(memberOfH1(), H1), validEvent()));
});

test("member creates an event with a null refId", async () => {
  await assertSucceeds(addDoc(eventsOf(memberOfH1(), H1), validEvent({ refId: null })));
});

test("every allowed type is accepted", async () => {
  for (const type of [
    "bill_added", "bill_paid", "chore_completed", "shopping_added",
    "shopping_bought", "maintenance_logged", "maintenance_status",
  ]) {
    await assertSucceeds(addDoc(eventsOf(memberOfH1(), H1), validEvent({ type })));
  }
});

test("create in another household is denied", async () => {
  await assertFails(addDoc(eventsOf(memberOfH1(), H2), validEvent()));
});

test("unknown type is denied", async () => {
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), validEvent({ type: "hacked" })));
});

test("extra field is denied", async () => {
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), validEvent({ extra: "x" })));
});

test("missing field is denied", async () => {
  const { refId, ...withoutRefId } = validEvent();
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), withoutRefId));
});

test("client-chosen createdAt is denied", async () => {
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), validEvent({ createdAt: Timestamp.fromDate(new Date("2020-01-01")) })));
});

test("overlong summary is denied", async () => {
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), validEvent({ summary: "x".repeat(201) })));
  await assertSucceeds(addDoc(eventsOf(memberOfH1(), H1), validEvent({ summary: "x".repeat(200) })));
});

test("empty actorName and non-string summary are denied", async () => {
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), validEvent({ actorName: "" })));
  await assertFails(addDoc(eventsOf(memberOfH1(), H1), validEvent({ summary: 42 })));
});

test("update is denied", async () => {
  await assertFails(updateDoc(doc(memberOfH1(), EVENT_PATH(H1, "e1")), { summary: "changed" }));
});

test("delete is denied", async () => {
  await assertFails(deleteDoc(doc(memberOfH1(), EVENT_PATH(H1, "e1"))));
});

test("other paths are denied", async () => {
  await assertFails(getDoc(doc(memberOfH1(), `households/${H1}`)));
  await assertFails(setDoc(doc(memberOfH1(), "other/doc"), { a: 1 }));
});
