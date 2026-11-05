# RentMate: Final POE Work Split

Team: James, Seth, Michael, Ali. Ticket IDs (RM-xxxx) come from the Ajiya plan in `ajiya/`.

Everyone keeps the areas they owned in Part 2, so nobody has to learn someone else's code.

## Rules for everyone

- Branch from `fix/post-part2-feedback` as `feature/RM-xxxx-short-name` and open a PR into it.
- Commit **your own work from your own GitHub account**, small and often. We need 75+ commits from all four of us (RM-0045).
- Conventional commit messages, with the ticket on the last line:

  ```
  fix(maintenance): catch network errors in MaintenanceViewModel

  Ajiya: RM-0001
  ```

- Every new screen and string: put the text in `strings.xml` (English, Afrikaans, isiXhosa), add KDoc comments, and add `Log.d` calls.
- A ticket counts as done only when its **Done when** check passes on a physical phone.
- If you are blocked by someone else's ticket, message them. Don't work around it.

## Work order

Stages are rough order. Start each ticket as soon as the tickets it depends on are merged.

| Stage | Michael | Seth | James | Ali |
|---|---|---|---|---|
| **1: Start now** | RM-0001, RM-0002, RM-0003, RM-0004 | RM-0006, RM-0012, RM-0008, RM-0007, RM-0020 | RM-0011, RM-0024 | RM-0034, RM-0037, RM-0043 |
| **2** | RM-0013, RM-0014, RM-0015 | RM-0010, RM-0030, RM-0032 | RM-0005, RM-0009, RM-0026, RM-0025, RM-0027 | RM-0021, RM-0036, RM-0035 |
| **3** | RM-0016, RM-0017, RM-0018, RM-0019, RM-0031 | RM-0022, RM-0040 | RM-0028, RM-0029 | RM-0023, RM-0033, RM-0038, RM-0039 |
| **4: Wrap-up** | Support demo video | Support demo video | Support demo video | RM-0041, RM-0042, RM-0044, RM-0046 |

## Michael: auth, stability, offline (12 tickets)

| # | Ticket | Done when | Waits on |
|---|---|---|---|
| 1 | RM-0001 Catch all repository errors; Maintenance never crashes offline | Airplane mode on every screen shows a friendly error, no crash; unit test for the failure path | none |
| 2 | RM-0002 Persist Supabase session, skip Login on relaunch | Kill and relaunch after sign-in lands on Dashboard | none |
| 3 | RM-0003 Fix back stack after sign-in | Back on Dashboard closes the app, no loop | RM-0002 |
| 4 | RM-0004 Friendly, translatable error messages | No raw exception text anywhere; ErrorMapper tests pass | RM-0001 |
| 5 | RM-0013 Biometric unlock (BiometricPrompt + PIN fallback) | Relaunch asks for fingerprint or face on a real phone | RM-0002 |
| 6 | RM-0014 Biometric toggle in Settings, unsupported-device handling | Toggle off skips the prompt; no crash on phones without biometrics | RM-0013 |
| 7 | RM-0015 Room database and DAOs | DAO tests pass | RM-0001 |
| 8 | RM-0016 Repositories read from Room, refresh from Supabase | Cached data shows in airplane mode | RM-0015 |
| 9 | RM-0017 Outbox queue for offline changes | Offline bill or shopping item shows a "pending" badge | RM-0016 |
| 10 | RM-0018 WorkManager sync worker | Reconnecting syncs pending items within a minute | RM-0017 |
| 11 | RM-0019 Offline banner and sync indicator | Banner shows offline and clears after sync | RM-0018 |
| 12 | RM-0031 Offline settle-up preview (User-defined 5) | Airplane mode shows provisional payments that match the server after reconnect | RM-0030 (Seth), RM-0016 |

## Seth: database, server, CI (10 tickets)

| # | Ticket | Done when | Waits on |
|---|---|---|---|
| 1 | RM-0006 Tighten database access rules (RLS) | SQL test: a non-member can't join without a code; a member can't edit someone else's bill | none |
| 2 | RM-0012 Fix CI so tests run and pass on every push | Green Android CI and API CI on GitHub | none |
| 3 | RM-0008 Chores: only the assignee completes, points once per cycle, leaderboard hidden for the whole household | RPC rejects cheating; hiding on one phone hides it for all | none |
| 4 | RM-0007 Use the server chore forecast in the app | Forecast comes from the RPC; local version removed | none |
| 5 | RM-0020 Create the Firebase project and add config (**needs a Google account**) | App builds with google-services; secrets not in git | none |
| 6 | RM-0010 Settle-up: creditor confirms; equal, % and exact splits | Only the creditor can confirm; bad split totals rejected | RM-0006 |
| 7 | RM-0030 Port debt simplification to Kotlin with tests | Kotlin results match `settle_up` on shared test data | RM-0010 |
| 8 | RM-0032 Write household events to Firestore with security rules | Events appear in the Firestore console; other households denied | RM-0020 |
| 9 | RM-0022 Edge Function sends FCM pushes | New bill on phone A notifies phone B within seconds | RM-0021 (Ali) |
| 10 | RM-0040 CI builds the signed release on tag | Pushing a tag uploads the signed APK | RM-0039 (Ali), RM-0012 |

## James: Settings, maintenance, languages, photos (9 tickets)

| # | Ticket | Done when | Waits on |
|---|---|---|---|
| 1 | RM-0011 Number keypad for amounts; hide raw user IDs | Amount fields open a decimal keypad; no IDs on screen | none |
| 2 | RM-0024 Move every hard-coded string into resources | Lint clean; no literal UI text in screens | none |
| 3 | RM-0005 Settings read back from the server on sign-in | A toggle set on phone A shows on phone B | RM-0002 (Michael) |
| 4 | RM-0009 Send to landlord: itemised email; only the sender records replies | Send opens a pre-filled email; other housemates can't change status | RM-0001 (Michael) |
| 5 | RM-0026 In-app language picker | Switching language changes every screen and survives restart | RM-0024 |
| 6 | RM-0025 Afrikaans + isiXhosa translations checked by a speaker | Every key translated; reviewer named in README | RM-0024 |
| 7 | RM-0027 Supabase Storage bucket + access rules (User-defined 4) | Members upload and read photos; non-members get 403 | RM-0006 (Seth) |
| 8 | RM-0028 Camera/gallery picker with compression and upload progress | Photo under 1 MB uploads with progress; failure offers retry | RM-0027 |
| 9 | RM-0029 Show photos on maintenance and bills, include them in the landlord email | Thumbnails show; email has photo links | RM-0028, RM-0009 |

## Ali: UI, notifications, feed, release (14 tickets)

| # | Ticket | Done when | Waits on |
|---|---|---|---|
| 1 | RM-0034 Design system + branded Login screen | Shared components everywhere; Login has logo and styled SSO button | none |
| 2 | RM-0037 Adaptive icon, splash, final images | No default Android assets left | none |
| 3 | RM-0043 AI-use write-up (max 500 words) | `AI_USAGE.md` filled in with citations | none |
| 4 | RM-0021 FCM client: token, permission, channels | Test push from the Firebase console arrives | RM-0020 (Seth) |
| 5 | RM-0036 Input validation on every form | Blank, negative and oversized input rejected inline, no crashes | RM-0034 |
| 6 | RM-0035 Empty, loading and error states | Every list has empty and retry states | RM-0034, RM-0004 (Michael) |
| 7 | RM-0023 Tapping a push opens the right screen; per-type toggles | A bill push opens Bills; turning bill pushes off works | RM-0022 (Seth) |
| 8 | RM-0033 Activity feed screen reading Firestore live | An action on phone A appears live on phone B | RM-0032 (Seth) |
| 9 | RM-0038 Logging + KDoc pass across the codebase | Logcat shows tagged lifecycle logs; every class has KDoc | none (do last) |
| 10 | RM-0039 Signed release build, R8, version bump | Signed APK installs and runs on a physical phone | RM-0037 |
| 11 | RM-0041 Play Store pack: screenshots, feature graphic, listing, privacy policy | All in `docs/store/` | RM-0035, RM-0037 |
| 12 | RM-0042 README as technical report + release notes | README covers purpose, design, architecture, CI, screenshots, release notes | RM-0041 |
| 13 | RM-0044 Record the demo video on a physical phone (everyone helps) | Unlisted YouTube link in README covers the whole video checklist | RM-0039, RM-0042 |
| 14 | RM-0046 Tag `final-poe` and submit **before the deadline** | Tag on origin points at the submitted commit | everything |

**RM-0045 (75+ commits, all four of us contributing)** is on everyone.
