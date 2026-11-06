<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Activity feed (NoSQL)

Goal: Household activity feed is written to and read from Firestore (NoSQL)

Owners: Seth (RM-0032 writes and rules), Ali (RM-0033 screen). Covers the 'use a NoSQL database to store and read data' learning outcomes.

Cloud Firestore collection households/{householdId}/events/{eventId} with fields type, actorName, summary, refId, createdAt (server timestamp). Written from the app after successful bill, chore, shopping and maintenance actions (or from the notify Edge Function). Security rules: only authenticated members can read/write their household path; membership mirrored to households/{id}/members/{uid} or Firebase custom auth. Screen uses a snapshot listener ordered by createdAt desc, limit 50, with empty and offline states.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0032 | android | Write household events to Firestore with security rules | Firestore console shows events for bill/chore/maintenance actions; rules deny other households | RM-0020 | - | 🟩 Done · 72e80e2 · 2026-11-06 · tests passed · Note: Events for bills, chores, shopping and maintenance go to households/{id}/events after the Supabase write succeeds. firebase-token Edge Function (deployed 2026-11-06) turns a Supabase session into a Firebase custom token with a households claim; it rejects calls without a user session. firestore.rules published 2026-11-06; 18 emulator tests prove members read only their own household and other households are denied. The phone check is on RM-0047. |
| RM-0033 | android | Activity feed screen reading Firestore in real time | Action on phone A appears on phone B's feed live | RM-0032 | - | 🟨 In progress |
