<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Part 2 feedback fixes

Goal: Every issue in the Part 2 lecturer feedback is fixed and verifiable on device

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0001 | android | Catch all repository errors; Maintenance screen never crashes offline | Airplane mode on every screen shows a friendly error state, no crash; unit test covers MaintenanceViewModel failure path | - | - | 🟥 Pending |
| RM-0002 | android | Persist Supabase session and skip Login on relaunch | Kill and relaunch app after sign-in lands on Dashboard without signing in again | - | - | 🟥 Pending |
| RM-0003 | android | Fix back-stack: Login popped after sign-in, Back on Dashboard exits app | Back on Dashboard closes the app; never returns to Login or loops | RM-0002 | - | 🟥 Pending |
| RM-0004 | android | Map Supabase/network exceptions to friendly localisable messages | No screen shows raw exception text; ErrorMapper unit tests pass | RM-0001 | - | 🟥 Pending |
| RM-0005 | android | Read settings back from profiles on sign-in so they follow the user across devices | Change a toggle on device A, sign in on device B, toggle matches | RM-0002 | - | 🟥 Pending |
| RM-0006 | supabase | Tighten RLS: join only via invite-code RPC; bills edit/delete by creator only; shares paid only via RPC | SQL test script proves a non-member cannot join without a code and a member cannot edit another's bill | - | - | 🟥 Pending |
| RM-0007 | android | Use server get_chore_forecast RPC instead of on-device forecast | Chores screen forecast comes from the RPC (visible in network log); local forecast code removed | - | - | 🟥 Pending |
| RM-0008 | supabase | Chore completion only by assignee, points once per cycle; leaderboard hide stored household-wide | RPC rejects non-assignee and duplicate completions; hiding leaderboard on one phone hides it for all | - | - | 🟥 Pending |
| RM-0009 | android | Send to landlord builds an itemised email to the landlord; only the sender records landlord responses | Tapping Send opens a prefilled email with all open faults; other housemates cannot change status | RM-0001 | - | 🟥 Pending |
| RM-0010 | supabase | Settle-up: creditor confirms payment; bills support equal, percentage and exact splits | Only the creditor can confirm; split validation rejects totals that do not add up; Add Bill offers all three modes | RM-0006 | - | 🟥 Pending |
| RM-0011 | android | Number keypad for amounts; never show raw user IDs | Amount fields open decimal keypad; no UUIDs visible on any screen | - | - | 🟥 Pending |
| RM-0012 | infra | Fix CI so unit tests run and pass on every push | Green Android CI and API CI runs on GitHub for the latest push | - | - | 🟥 Pending |
