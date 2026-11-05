<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Offline mode with sync

Goal: Bills, chores and shopping work offline via Room and sync automatically on reconnect

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0015 | android | Room database and DAOs for bills, shares, chores, shopping items | Room migration/DAO instrumented or Robolectric tests pass | RM-0001 | - | 🟥 Pending |
| RM-0016 | android | Repositories read from Room as source of truth and refresh from Supabase | Screens render cached data in airplane mode after one online load | RM-0015 | - | 🟥 Pending |
| RM-0017 | android | Outbox queue: offline creates/edits stored as pending operations | Adding a shopping item and a bill offline shows them with a pending badge | RM-0016 | - | 🟥 Pending |
| RM-0018 | android | WorkManager sync worker with network constraint and conflict handling | Reconnecting syncs pending items to Supabase within a minute; unit tests for conflict rules pass | RM-0017 | - | 🟥 Pending |
| RM-0019 | android | Offline banner and sync status indicator | Banner appears when offline and clears after sync | RM-0018 | - | 🟥 Pending |
