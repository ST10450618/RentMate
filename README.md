# RentMate

[![Android CI](https://github.com/ST10450618/RentMate/actions/workflows/android-ci.yml/badge.svg)](https://github.com/ST10450618/RentMate/actions/workflows/android-ci.yml)

**One household, one app.** RentMate puts bill splitting, chore rotation, a
shared shopping list and landlord maintenance requests in one place for people
who share a home. It also captures those actions offline, so a tenant on a
depleted data bundle can still record what they did and sync it later.

It's a native Android app (Kotlin, Jetpack Compose) backed by Supabase
(Postgres, PostgREST and Supabase Auth). Built for PROG7314 / OPSC7312,
Programming 3D, at The IIE.

---

## Contents

- [Purpose and scope](#purpose-and-scope)
- [Features](#features)
- [Screens](#screens)
- [Architecture](#architecture)
- [Why Supabase instead of Azure](#why-supabase-instead-of-azure)
- [Technology stack](#technology-stack)
- [Repository structure](#repository-structure)
- [Getting started](#getting-started)
- [Backend (Supabase)](#backend-supabase)
- [Testing](#testing)
- [Continuous integration](#continuous-integration)
- [Version control workflow](#version-control-workflow)
- [Demonstration video](#demonstration-video)
- [Team](#team)
- [Roadmap to final POE](#roadmap-to-final-poe)
- [AI usage](#ai-usage)
- [References](#references)

---

## Purpose and scope

People sharing a home usually split household admin across three or four
unrelated tools: a chat group for chores, a banking app for rent, a note for the
shopping list, and a message thread with the landlord that nobody can find again.
RentMate puts all four in one place.

The competitive analysis in Part 1 found that no reviewed app covers this scope.
Splitwise and Tricount handle money but not chores. Flatastic handles the
household but not the tenant's relationship with a landlord. The maintenance
request log is RentMate's clearest differentiator: none of the three reviewed
apps offers it.

**In scope for this prototype:** SSO sign-in, household creation and membership,
bills with configurable splits, chore rotation, shared shopping list, maintenance
request log, settings, and a cloud-hosted REST API backed by Postgres.

**Deferred to the final POE:** biometric unlock, offline mode with sync, push
notifications, multi-language support, blob storage for photos, and production
visual assets.

## Features

Mandatory requirements from the module brief:

| Feature | Story | Status |
| --- | --- | --- |
| SSO registration and sign-in | US-1 | Done |
| Settings menu | US-3 | Done |
| Custom REST API connected to a database | US-5, US-6, US-9 | Done |
| REST API integrated into the app | — | Done |
| Biometric authentication | US-2 | Final POE |
| Offline mode with sync | US-7 | Final POE |
| Real-time push notifications | US-8 | Final POE |
| Multi-language (English, Afrikaans, isiXhosa) | US-4 | Final POE |

Custom features defined during Part 1 design:

| # | Feature | Story | What it does |
| --- | --- | --- | --- |
| 1 | **Maintenance request log** | US-10 | Photograph a fault, tag it by category and urgency, then let RentMate group open items into one dated request to the landlord. Each request moves through open → sent → acknowledged → resolved. |
| 2 | **Chore rotation and points** | US-9, US-12 | Resolves the rotation four cycles ahead, so every housemate sees the same forward schedule instead of guessing whose turn it is. Points accrue quietly on completion, and the leaderboard can be hidden household-wide without affecting the points themselves. |
| 3 | **Settle-up** | US-11 | Runs debt simplification across outstanding balances to show the smallest set of payments that clears the household, instead of everyone paying everyone back individually. |

## Screens

Screen IDs match the navigation wireflow and UI mockups in the Part 1 design
document, so you can trace a design decision from the document straight to the
code that implements it.

| ID | Screen | Delivers |
| --- | --- | --- |
| S1 | Login / SSO | US-1, US-2, US-4 |
| S2 | Household setup / join | US-5 |
| S3 | Dashboard | US-3 |
| S4 | Bills | US-6, US-11 |
| S5 | Add Bill | US-6 |
| S6 | Chores | US-9, US-12 |
| S7 | Add Chore | US-9, US-12 |
| S8 | Shopping List | US-13 |
| S9 | Maintenance Request | US-10 |
| S10 | Settings | US-2, US-3, US-4 |

<!-- Embed screenshots here once screens are built. Suggested: a 3-wide table of
     images at docs/screenshots/, one row per feature area. -->

## Architecture

The client follows MVVM. ViewModels read and write directly against Supabase's
Postgrest API, which is a real REST API generated from the Postgres schema
(not the client talking to the database itself). Business logic that isn't
plain CRUD, such as debt simplification and chore rotation, lives in Postgres
functions and is called the same way, through `rpc()` calls over that same
REST interface.

**Row Level Security is the access-control layer.** Every table checks "is the
signed-in user a member of this household?" before returning or accepting a
row. That check happens inside Postgres itself, driven by the JWT Supabase
Auth issues on sign-in, so a client can't read or write another household's
data no matter what it sends.

A few structural decisions worth calling out:

- **No hand-rolled auth server.** Google Sign-In goes through Supabase Auth
  directly (`signInWith(IDToken)`), which issues and refreshes the session.
  Nothing in this app stores or validates a JWT itself.
- **Server-side rotation resolution.** A Postgres function computes chore
  rotation, rather than each device computing it independently. In plain
  English: if every phone worked out "whose turn it is" on its own, two
  housemates could easily end up disagreeing. Doing it once, server-side,
  settles that.
- **Four top-level destinations.** Dashboard, Bills, Chores and a More sheet.
  Material 3 recommends three to five, and this keeps everything in one thumb
  reach. Creation screens are modal and only reachable from their parent list,
  so the back stack stays shallow.

Offline-first reads (Room as the local source of truth, with a WorkManager
sync worker replaying queued writes) is designed but not yet built. It's
explicitly Final POE scope per the brief, not something skipped by accident.

## Why Supabase instead of Azure

Part 1's design specified ASP.NET Core on Azure App Service with Azure SQL.
That plan assumed Azure for Students credit, which turned out not to be
available to this team, so Part 2 pivoted to Supabase instead. The RentMate.Api
project (ASP.NET Core, Entity Framework Core, xUnit tests) still lives in this
repo under `RentMate.Api/` as the record of that original design: its
controllers, DTOs and two services (`DebtSimplificationService`,
`ChoreRotationService`) are what the Postgres functions in `supabase/functions/`
were ported from, so the algorithms specified in Part 1 are unchanged even
though the runtime is not.

What Supabase gives this project, mapped to the brief's mandatory
requirements:

- **A real REST API connected to a database.** Postgrest generates one
  automatically from the Postgres schema, and Postgres functions cover the
  logic Postgrest can't (`create_household`, `settle_up`, `complete_chore`,
  and so on).
- **No credit card required**, unlike Azure's free tier.
- **Auth included.** Supabase Auth handles the Google OAuth exchange, which
  replaces the JWT-issuing controller Part 1 designed.

## Technology stack

| Layer | Choice |
| --- | --- |
| Client | Kotlin, Jetpack Compose, Material 3 |
| Client architecture | MVVM, Navigation Compose, Hilt |
| Local persistence | DataStore (settings only; Room + offline sync is Final POE) |
| Networking | supabase-kt (Postgrest, Auth), Ktor |
| Backend | Supabase: Postgres, PostgREST, Postgres functions (RPC) |
| Database | Postgres (Supabase-managed) |
| Auth | Google SSO (OAuth 2.0) via Supabase Auth |
| Push | Firebase Cloud Messaging (Final POE) |
| CI | GitHub Actions |
| Testing | JUnit (client) |

## Repository structure

```
.
├── app/                      # Android application module
│   └── src/
│       ├── main/java/...     # UI, ViewModels, repositories, data sources
│       ├── main/res/         # Layouts, drawables, string resources
│       └── test/             # Unit tests
├── supabase/
│   ├── migrations/           # Postgres schema + Row Level Security policies
│   └── functions/            # Postgres functions (settle-up, rotation, etc.)
├── keystore/                 # Shared debug keystore (see Getting started)
├── RentMate.Api/             # Retired ASP.NET Core prototype - kept as the
│                              # source the Postgres functions were ported from
├── .github/workflows/        # CI pipelines
├── CONTRIBUTING.md           # Branch and commit conventions
└── README.md
```

## Getting started

**Prerequisites:** Android Studio, JDK 17, an Android device running 8.0+ with
USB debugging enabled, a Supabase project (free tier, no card needed).

```bash
git clone https://github.com/ST10450618/RentMate.git
cd RentMate
```

Create `local.properties` in the project root (it's gitignored) with:

```properties
sdk.dir=/path/to/your/Android/sdk
SUPABASE_URL=https://<your-project>.supabase.co
SUPABASE_ANON_KEY=<your-project's-anon-key>
WEB_CLIENT_ID=<your-google-oauth-web-client-id>.apps.googleusercontent.com
```

`SUPABASE_URL` and `SUPABASE_ANON_KEY` are on your Supabase project's
Settings → API page. `WEB_CLIENT_ID` is a **Web application** type OAuth
client from Google Cloud Console (not Android, not Desktop) - the same one
registered in Supabase's Google Auth provider.

Debug builds sign with the keystore committed at `keystore/debug.keystore`,
not the auto-generated per-machine one, so Google Sign-In's registered SHA-1
stays valid no matter who builds the app or where.

Then open the project in Android Studio, let Gradle sync, select your device
and run. From the command line:

```bash
./gradlew assembleDebug
```

## Backend (Supabase)

The schema and business logic live in `supabase/migrations/` as plain SQL.
Run them in order in your Supabase project's SQL Editor:

1. `0001_init.sql` - tables and Row Level Security policies
2. `0002_functions.sql` - `create_household`, `join_household`,
   `complete_chore`, `forecast_chore`, `settle_up`, `confirm_settlement`,
   `leaderboard`
3. `0003_send_to_landlord.sql` - batches open maintenance requests

Every function is called through Postgrest's `rpc()` endpoint, so from the
Android app's side it's still a normal REST call, just to a Postgres function
instead of a hand-written controller action.

## Testing

```bash
./gradlew testDebugUnitTest      # client unit tests
./gradlew lintDebug              # static analysis
```

Unit tests cover the logic worth protecting rather than chasing a coverage
number: chore rotation, and the maintenance request status/validation rules.
The debt-simplification and rotation *algorithms* are also covered by the
legacy `RentMate.Api.Tests` project (`cd RentMate.Api && dotnet test`), since
that's where they were first written and tested before being ported into
Postgres functions with the same logic.

## Continuous integration

**`android-ci.yml`** runs on every push to any branch and on every PR into
`main` or `develop`. It checks out the code, sets up JDK 17 with Gradle
dependency caching, runs Android Lint, runs the unit test suite, and assembles
a debug APK. Test and lint reports upload as artifacts even when the job
fails, so you can diagnose a failure from the Actions tab without reproducing
it locally.

**`api-ci.yml`** still builds and tests the retired `RentMate.Api` project.
It's kept green because that code is still real (the Postgres functions were
ported from it), just no longer what the app talks to at runtime.

Both use a concurrency group keyed on the branch, so pushing twice in quick
succession cancels the stale run instead of queuing it.

## Version control workflow

`main` always builds and only gets merged into from `develop`, at milestones.
Feature work happens on `feature/<area>-<name>` branches and reaches `develop`
through pull requests, reviewed by another team member. Commits follow
Conventional Commits, for example `feat(maintenance): add photo attachment to
request form`. Full conventions are in [CONTRIBUTING.md](CONTRIBUTING.md).

Milestone tags: `part-2` for the prototype submission, `final-poe` for the final
deployment commit.

## Demonstration video

<!-- Replace with the unlisted YouTube link before submission. -->
**Watch the demo:** _link pending_

Covers: SSO registration and login, changing and saving settings, live
round-trips against Supabase's REST API, visual confirmation of data changing
in Supabase Auth's Users tab, the Postgrest API, and the Table Editor, plus
each custom feature.

## Team

| Member | Part 2 responsibilities |
| --- | --- |
| James | Project setup, Compose navigation shell, Settings screen, maintenance log feature |
| Seth | Original REST API design: six controllers, data model and the debt-simplification/rotation algorithms, later ported into Supabase's Postgres functions |
| Michael | SSO sign-in, API integration into the app, settle-up feature |
| Ali | UI build-out across all screens, chore rotation and points, demo video, Supabase migration |

## Roadmap to final POE

- Biometric unlock (US-2)
- Offline mode with sync via Room + WorkManager (US-7)
- Push notifications via Firebase Cloud Messaging (US-8)
- Multi-language: English, Afrikaans, isiXhosa (US-4)
- Blob storage for maintenance photos
- Production icon and final visual assets
- Signed release APK and Play Store screenshots

## AI usage

See [`AI_USAGE.md`](AI_USAGE.md) for a transparent account of how generative AI
tooling was used during this phase, as required by the brief (maximum 500 words).

## References

[1] Microsoft, "Azure for Students." https://azure.microsoft.com/free/students/
[2] Supabase, "Supabase Docs." https://supabase.com/docs
[3] Google, "Biometric authentication." https://developer.android.com/identity/sign-in/biometric-auth
[4] Google, "Firebase Cloud Messaging." https://firebase.google.com/docs/cloud-messaging
[5] Google, "Material Design 3." https://m3.material.io/
[6] Google, "Build an offline-first app." https://developer.android.com/topic/architecture/data-layer/offline-first
[7] Google, "Schedule tasks with WorkManager." https://developer.android.com/topic/libraries/architecture/workmanager
[8] M. Jones, J. Bradley and N. Sakimura, "JSON Web Token (JWT)," RFC 7519, IETF, 2015. https://datatracker.ietf.org/doc/html/rfc7519
