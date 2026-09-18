# RentMate

[![Android CI](https://github.com/ST10450618/RentMate/actions/workflows/android-ci.yml/badge.svg)](https://github.com/ST10450618/RentMate/actions/workflows/android-ci.yml)
[![API CI](https://github.com/ST10450618/RentMate/actions/workflows/api-ci.yml/badge.svg)](https://github.com/ST10450618/RentMate/actions/workflows/api-ci.yml)

**One household, one app.** RentMate consolidates bill splitting, chore rotation,
a shared shopping list and landlord maintenance requests for people who share a
home — and captures those actions offline, so a tenant on a depleted data bundle
can still record what they did and have it reconcile later.

Native Android (Kotlin, Jetpack Compose) with a custom ASP.NET Core REST API
hosted on Azure. Built for PROG7314 / OPSC7312 — Programming 3D, The IIE.

---

## Contents

- [Purpose and scope](#purpose-and-scope)
- [Features](#features)
- [Screens](#screens)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Repository structure](#repository-structure)
- [Getting started](#getting-started)
- [REST API](#rest-api)
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

People sharing a home currently split household admin across three or four
unrelated tools: a chat group for chores, a banking app for rent, a note for the
shopping list, and a message thread with the landlord that nobody can find again.
RentMate puts all four in one place.

The competitive analysis in Part 1 established that no reviewed application covers
this scope. Splitwise and Tricount handle money but not chores; Flatastic handles
the household but not the tenant's relationship with a landlord. The maintenance
request log is RentMate's clearest differentiator — none of the three reviewed
apps offers it.

**In scope for this prototype:** SSO sign-in, household creation and membership,
bills with configurable splits, chore rotation, shared shopping list, maintenance
request log, settings, and a cloud-hosted REST API backed by Azure SQL.

**Deferred to the final POE:** biometric unlock, offline mode with sync, push
notifications, multi-language support, blob storage, NoSQL reads/writes, and
production visual assets.

## Features

Mandatory requirements from the module brief:

| Feature | Story | Status |
| --- | --- | --- |
| SSO registration and sign-in | US-1 | <!-- ☐ / ☑ --> |
| Settings menu | US-3 | |
| Custom REST API connected to a database | US-5, US-6, US-9 | |
| REST API integrated into the app | — | |
| Biometric authentication | US-2 | Final POE |
| Offline mode with sync | US-7 | Final POE |
| Real-time push notifications | US-8 | Final POE |
| Multi-language (English, Afrikaans, isiXhosa) | US-4 | Final POE |

Custom features defined during Part 1 design:

| # | Feature | Story | What it does |
| --- | --- | --- | --- |
| 1 | **Maintenance request log** | US-10 | Photograph a fault, tag it by category and urgency, compile open items into a dated request to the landlord, then track each through open → sent → acknowledged → resolved. |
| 2 | **Chore rotation and points** | US-9, US-12 | Resolves the rotation four cycles ahead so every housemate sees the same forward schedule. Points accrue silently on completion; the leaderboard can be hidden household-wide. |
| 3 | **Settle-up** | US-11 | Applies debt simplification across outstanding balances to show the minimum set of payments that clears the household. |

## Screens

Screen IDs match the navigation wireflow and UI mockups in the Part 1 design
document, so a design decision can be traced from document to code.

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

<!-- Export the UML component diagram to docs/architecture.png and embed it here:
     ![RentMate architecture](docs/architecture.png) -->

The client follows MVVM with a local database as the UI's single source of truth.
Screens observe ViewModel state; ViewModels read and write through repositories
that hit Room first and the network second. A WorkManager sync worker is the only
component permitted to replay queued offline writes.

The key constraint, visible in the component diagram: **no path runs from the
Android client to the database.** All shared state passes through the REST API,
which owns reconciliation and conflict resolution. Where the same record changes
on two devices, the server-recorded timestamp wins and the losing device shows a
non-blocking notice.

Structural decisions worth stating:

- **Local-first reads.** The UI never waits on the network to draw. This is what
  makes the offline requirement an architectural property rather than a feature
  bolted on at POE.
- **Server-side rotation resolution.** Chore rotation is computed once by the API
  rather than independently on each device, which is what prevents two housemates
  disagreeing about whose turn it is.
- **Four top-level destinations.** Dashboard, Bills, Chores and a More sheet — the
  Material 3 recommendation is three to five, and everything stays in one thumb
  reach. Creation screens are modal and reachable only from their parent list, so
  the back stack stays shallow.

## Technology stack

| Layer | Choice |
| --- | --- |
| Client | Kotlin, Jetpack Compose, Material 3 |
| Client architecture | MVVM, Navigation Compose, Hilt |
| Local persistence | Room |
| Background work | WorkManager |
| Networking | Retrofit + OkHttp, Kotlinx Serialization |
| API | ASP.NET Core 8 Web API, Entity Framework Core |
| Database | Azure SQL Database |
| Hosting | Azure App Service (Azure for Students) |
| Auth | Google SSO (OAuth 2.0), JWT bearer tokens |
| Push | Firebase Cloud Messaging |
| CI | GitHub Actions |
| Testing | JUnit, MockK, Turbine (client); xUnit (API) |

## Repository structure

```
.
├── app/                      # Android application module
│   └── src/
│       ├── main/java/...     # UI, ViewModels, repositories, data sources
│       ├── main/res/         # Layouts, drawables, string resources
│       └── test/             # Unit tests
├── api/                      # ASP.NET Core Web API
├── docs/                     # Diagrams, screenshots, design exports
├── .github/workflows/        # CI pipelines
├── CONTRIBUTING.md           # Branch and commit conventions
└── README.md
```

## Getting started

**Prerequisites:** Android Studio (Ladybug or newer), JDK 17, an Android device
running 8.0+ with USB debugging enabled, .NET 8 SDK if you're working on the API.

```bash
git clone https://github.com/ST10450618/RentMate.git
cd REPO
```

Create `local.properties` in the project root (it is gitignored) and add the API
base URL:

```properties
API_BASE_URL="https://<your-app-service>.azurewebsites.net/"
```

Then open the project in Android Studio, let Gradle sync, select your physical
device and run. To build from the command line:

```bash
./gradlew assembleDebug
```

To run the API locally:

```bash
cd api
dotnet run
```

## REST API

Base URL: `https://<your-app-service>.azurewebsites.net/api`
All endpoints except `/auth/*` require an `Authorization: Bearer <jwt>` header.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/auth/google` | Exchange a Google ID token for a RentMate JWT |
| POST | `/households` | Create a household, returns an invite code |
| POST | `/households/join` | Join using an invite code |
| GET | `/households/{id}/bills` | List bills for a household |
| POST | `/households/{id}/bills` | Create a bill and its splits |
| GET | `/households/{id}/chores` | List chores and the rotation forecast |
| POST | `/chores/{id}/complete` | Record a completion, advance the rotation |
| GET / POST | `/households/{id}/shopping` | Shared shopping list |
| GET / POST | `/households/{id}/maintenance` | Maintenance requests |
| POST | `/notifications/register-device` | Register an FCM device token |

<!-- Full payload schemas are in the Part 1 design document, Section 5. Consider
     linking a Swagger/OpenAPI page here once the API is deployed. -->

## Testing

```bash
./gradlew testDebugUnitTest      # client unit tests
./gradlew lintDebug              # static analysis
cd api && dotnet test            # API unit tests
```

Unit tests cover the logic worth protecting rather than chasing a coverage
number: split calculations, rotation resolution, debt simplification, and the
sync queue's conflict rules. Reports for every CI run are downloadable from the
workflow's artifacts.

## Continuous integration

Two GitHub Actions workflows, each scoped by path so a client push doesn't
trigger the back-end build:

**`android-ci.yml`** — runs on every push to any branch and on every PR into
`main` or `develop`. It checks out the code, sets up JDK 17 with Gradle
dependency caching, runs Android Lint, runs the unit test suite, and assembles a
debug APK. Test and lint reports upload as artifacts even when the job fails, so
a failure can be diagnosed from the Actions tab without reproducing it locally.
The APK is retained for 30 days, which means the current build can be installed
straight from GitHub.

**`api-ci.yml`** — the same idea for the ASP.NET Core API: restore, build in
Release, run xUnit tests, upload results.

Both use a concurrency group keyed on the branch, so pushing twice in quick
succession cancels the stale run instead of queueing it.

The point of this setup is verification outside the machine that wrote the code.
A build that only compiles on one laptop is not a build.

## Version control workflow

`main` always builds and is only merged into from `develop` at milestones.
Feature work happens on `feature/<area>-<name>` branches and reaches `develop`
through pull requests reviewed by another team member. Commits follow
Conventional Commits — `feat(maintenance): add photo attachment to request form`.
Full conventions are in [CONTRIBUTING.md](CONTRIBUTING.md).

Milestone tags: `part-2` for the prototype submission, `final-poe` for the final
deployment commit.

## Demonstration video

<!-- Replace with the unlisted YouTube link before submission. -->
**Watch the demo:** _link pending_

Covers: SSO registration and login, changing and saving settings, live data
round-trips against the hosted API, visual confirmation of data changing in the
auth service, API and database, and each custom feature.

## Team

| Member | Part 2 responsibilities |
| --- | --- |
| James | Project setup, Compose navigation shell, Settings screen, maintenance log feature |
| Seth | REST API — six controllers, Azure hosting, API tests and CI |
| Michael | SSO sign-in, API integration into the app, settle-up feature |
| Ali | UI build-out across all screens, chore rotation and points, demo video |

## Roadmap to final POE

- Biometric unlock (US-2)
- Offline mode with sync via Room + WorkManager (US-7)
- Push notifications via Firebase Cloud Messaging (US-8)
- Multi-language: English, Afrikaans, isiXhosa (US-4)
- Blob storage for maintenance photos
- NoSQL read/write path
- Production icon and final visual assets
- Signed release APK and Play Store screenshots

## AI usage

See [`AI_USAGE.md`](AI_USAGE.md) for a transparent account of how generative AI
tooling was used during this phase, as required by the brief (maximum 500 words).

## References

[1] Microsoft, "ASP.NET Core documentation." https://learn.microsoft.com/aspnet/core/
[2] Microsoft, "Azure for Students." https://azure.microsoft.com/free/students/
[3] Google, "Biometric authentication." https://developer.android.com/identity/sign-in/biometric-auth
[4] Google, "Firebase Cloud Messaging." https://firebase.google.com/docs/cloud-messaging
[5] Google, "Material Design 3." https://m3.material.io/
[6] Google, "Build an offline-first app." https://developer.android.com/topic/architecture/data-layer/offline-first
[7] Google, "Schedule tasks with WorkManager." https://developer.android.com/topic/libraries/architecture/workmanager
[8] M. Jones, J. Bradley and N. Sakimura, "JSON Web Token (JWT)," RFC 7519, IETF, 2015. https://datatracker.ietf.org/doc/html/rfc7519
