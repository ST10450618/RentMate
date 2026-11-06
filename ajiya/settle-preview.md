<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# UD5: Offline settle-up preview

Goal: Settle-up preview runs offline on the cached ledger and reconciles with the server

Owners: Seth (RM-0030 algorithm), Michael (RM-0031 screen). User-defined feature 5, from the Part 2 planning document.

Port the greedy debt simplification in settle_up (largest creditor matched with largest debtor until all balances are zero, amounts in cents to avoid floating point) to a pure Kotlin DebtSimplifier with JUnit tests using the same fixtures as a SQL test. Offline, compute from Room's cached bills and shares and label the result 'Provisional, will update when you're back online'. After sync, re-fetch settle_up and replace the preview.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0030 | android | Port debt-simplification to Kotlin with unit tests matching server settle_up | Kotlin tests produce identical results to settle_up for shared fixtures | RM-0010 | - | 🟩 Done · 538db26 · 2026-11-06 · tests passed · Note: 0012 makes settle_up deterministic (cents, amount desc then user_id). Kotlin DebtSimplifier mirrors it; both checked against supabase/tests/fixtures/settle_up_fixtures.json (7 cases). settle_up_test 8 PASS, fails without 0012. Not yet applied to live DB. |
| RM-0031 | android | Offline settle-up preview from Room ledger, marked provisional, reconciled on sync | In airplane mode Settle-up shows provisional payments; after reconnect it matches server | RM-0016, RM-0030 | - | 🟩 Done · a959973 · 2026-11-06 · tests passed · Note: Offline, or while a bill still waits in the outbox, Settle-up shows a preview computed on the phone with DebtSimplifier. It is labelled and Confirm received is blocked. After a sync, a refresh or a 20-second retry, the server plan replaces it. SettleUpCoordinatorTest covers the rules. The phone check is on RM-0047. |
