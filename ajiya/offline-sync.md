<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Offline mode with sync

Goal: Bills, chores and shopping work offline via Room and sync automatically on reconnect

Owner: Michael. Scope: bills, bill shares, chores, shopping items (the brief allows specific features).

Room: entities mirror SupabaseRows plus syncState (SYNCED | PENDING_CREATE | PENDING_UPDATE | PENDING_DELETE) and updatedAt. Repositories expose Flow from Room; refresh() pulls from Supabase and upserts. Writes go to Room first and enqueue an OutboxEntry (op, table, payloadJson, createdAt). SyncWorker (CoroutineWorker, Constraints NetworkType.CONNECTED, unique work, exponential backoff) drains the outbox in order and marks rows SYNCED; conflicts: server wins for deletes, last-write-wins on updatedAt otherwise. Trigger sync on reconnect via ConnectivityManager.NetworkCallback and after each local write. Offline banner reads a NetworkMonitor StateFlow. Demo: airplane mode -> add shopping item and bill -> pending badges -> reconnect -> rows appear in Supabase table editor.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0015 | android | Room database and DAOs for bills, shares, chores, shopping items | Room migration/DAO instrumented or Robolectric tests pass | RM-0001 | - | 🟨 In progress |
| RM-0016 | android | Repositories read from Room as source of truth and refresh from Supabase | Screens render cached data in airplane mode after one online load | RM-0015 | - | 🟥 Pending |
| RM-0017 | android | Outbox queue: offline creates/edits stored as pending operations | Adding a shopping item and a bill offline shows them with a pending badge | RM-0016 | - | 🟥 Pending |
| RM-0018 | android | WorkManager sync worker with network constraint and conflict handling | Reconnecting syncs pending items to Supabase within a minute; unit tests for conflict rules pass | RM-0017 | - | 🟥 Pending |
| RM-0019 | android | Offline banner and sync status indicator | Banner appears when offline and clears after sync | RM-0018 | - | 🟥 Pending |
