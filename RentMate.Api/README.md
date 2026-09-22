# RentMate API

REST API backend for **RentMate**, a shared-household Android app (bill-splitting,
chore rotation, a shared shopping list and landlord-facing maintenance logging)
built for PROG7314 / OPSC7312, Part 2.

This repository is **Seth's portion of the group project only**: the ASP.NET
Core Web API, its database, its automated tests and its CI pipeline. It
contains no Android/Kotlin code by design - the Android app (built by the
rest of the team) is a separate repository that consumes this API over HTTP.

## 1. Purpose and scope

The API exposes six controllers, matching the six user-facing feature areas
from the Part 1 Research Report and Planning document:

| Controller | Responsibility | User stories covered |
|---|---|---|
| `AuthController` | Google Sign-In verification, RentMate JWT issuance, refresh tokens, user settings | US-1, US-2, US-3 |
| `HouseholdsController` | Creating/joining households, invite codes, landlord contact, offline-sync pull endpoint | US-5, US-7 |
| `BillsController` | Bills, per-member shares, paying a share, settle-up (debt simplification) | US-6, US-7, US-11 |
| `ChoresController` | Rotating chores, completions, points leaderboard | US-9, US-12 |
| `ShoppingListController` | Shared shopping list, converting a purchase into a bill | US-13 |
| `MaintenanceController` | Logging faults, status tracking, batching into one landlord letter | US-10 |

**Not included here, by design:** `SyncQueueItem` from the Part 1 data model
is device-local only (a Room table on the Android side) - it never touches
this API. What this API *does* provide for offline sync is a pull endpoint,
`GET /api/households/{id}/sync?since=...`, and last-write-wins conflict
detection on every "mark as done" endpoint (paying a bill share, completing
a chore, ticking off a shopping item). See the code comments in
`HouseholdsController.Sync` and `BillsController.PayShare` for exactly how
that works - you should be able to explain this design decision to your
lecturer or your team.

## 2. Architecture

```
RentMate.Api/
  Controllers/     6 controllers, one file each, matching the table above
  Models/          EF Core entities (User, Household, Bill, Chore, ...)
  DTOs/            Request/response records - controllers never return entities directly
  Data/            RentMateDbContext (EF Core)
  Services/        Business logic, kept out of controllers so it is unit-testable:
                     - JwtTokenService        issues RentMate's own JWTs
                     - GoogleAuthValidator     verifies Google ID tokens
                     - DebtSimplificationService  the settle-up algorithm (US-11)
                     - ChoreRotationService   rotation forecasting/advancing (US-9)
                     - InviteCodeGenerator    6-character household codes (US-5)
                     - FcmNotificationService push notifications (US-8)
  Program.cs       Wires everything together (DI, EF Core, JWT auth, Swagger)

RentMate.Api.Tests/
  *ServiceTests.cs           Unit tests for the business logic above
  *FlowTests.cs              Integration tests through the real HTTP pipeline
  TestAuthHandler.cs         Fake auth scheme so tests don't need real Google/JWT tokens
  RentMateApiFactory.cs      Boots the API in-memory for integration tests
```

**Authentication flow:** the Android app signs the user in with Google
(Credential Manager / Google Sign-In SDK), gets a Google ID token, and sends
it to `POST /api/auth/google-signin`. This API verifies that token really
came from Google and was issued for this app (never trusts it blindly),
creates a `User` row on first sign-in, and returns its own signed JWT plus a
refresh token. Every other endpoint requires that JWT in the
`Authorization: Bearer <token>` header. Biometric unlock (US-2) never talks
to this API directly - it just gates the app's local access to the stored
refresh token, which is then exchanged at `POST /api/auth/refresh`.

## 3. Prerequisites

- [.NET 8 SDK](https://dotnet.microsoft.com/download) (the project also
  targets fine on .NET 10 if that's what your lab machines have - see the
  comment at the top of `RentMate.Api.csproj`)
- A code editor: Visual Studio 2022, VS Code with the C# Dev Kit extension,
  or JetBrains Rider
- [Postman](https://www.postman.com/downloads/) for manual testing
- A GitHub account and an empty GitHub repository for this project
- An Azure for Students account (free tier is enough) for deployment
- A Google Cloud project (for the OAuth client ID) - you likely already have
  one if the Android team has set up Google Sign-In
- A Firebase project (for push notifications) - can be the same one the
  Android team uses for FCM

## 4. Step-by-step: getting it running locally

1. **Install the .NET SDK** if you don't have it, then confirm it worked:
   ```bash
   dotnet --version
   ```
2. **Extract this project** into its own folder and open a terminal there.
3. **Restore and build**, to make sure everything compiles on your machine
   before you change anything:
   ```bash
   dotnet restore
   dotnet build
   ```
4. **Run it.** By default (no connection string set) the API uses an
   in-memory database, so there is nothing else to configure for a first run:
   ```bash
   cd RentMate.Api
   dotnet run
   ```
   Open the URL it prints (e.g. `http://localhost:5080/swagger`) in a
   browser. You should see the Swagger UI listing all six controllers.
5. **Run the automated tests:**
   ```bash
   dotnet test
   ```
   All tests should pass. If you add new endpoints later, add a test for
   them in `RentMate.Api.Tests` following the existing examples.

## 5. Setting up Google Sign-In verification

The Android team's Google Sign-In setup gives you an **OAuth 2.0 Client ID**
(the "Android client ID" or "Web client ID", depending on how it's
configured in Google Cloud Console). Ask them for it, then:

```bash
cd RentMate.Api
dotnet user-secrets init
dotnet user-secrets set "Google:AndroidClientId" "your-client-id.apps.googleusercontent.com"
```

`dotnet user-secrets` keeps this out of `appsettings.json` and out of git,
which matters because this ID (together with your JWT signing key below) is
effectively the key to your API.

## 6. Setting up your own JWT signing key

Never use the placeholder key in `appsettings.Development.json` outside of
your own machine. Generate a real one and store it the same way:

```bash
dotnet user-secrets set "Jwt:Key" "a-random-string-at-least-32-characters-long"
```

## 7. Setting up Firebase push notifications (US-8)

1. In the [Firebase console](https://console.firebase.google.com/), open the
   same project the Android app uses (Project Settings > Service Accounts).
2. Click **Generate new private key** - this downloads a JSON file.
   **Do not commit this file to git** (it's already covered by `.gitignore`).
3. Save it somewhere outside the repo, e.g. `~/secrets/firebase-service-account.json`.
4. Point the API at it:
   ```bash
   dotnet user-secrets set "Firebase:ServiceAccountJsonPath" "/home/you/secrets/firebase-service-account.json"
   ```
   Until you do this, the API still works normally - it just logs "would
   have sent a push notification" instead of actually sending one, so you
   can build and test everything else first.

## 8. Setting up the database (Azure SQL)

You can develop entirely against the in-memory database. Once you're ready
for data to persist between runs (and definitely before your Azure
deployment):

1. In the [Azure Portal](https://portal.azure.com) (use your Azure for
   Students credits), create an **Azure SQL Database** (the free/Basic tier
   is enough for a student project).
2. Copy its connection string from the database's **Connection strings**
   page.
3. Set it locally:
   ```bash
   dotnet user-secrets set "ConnectionStrings:RentMate" "Server=tcp:...;Database=...;User ID=...;Password=...;Encrypt=True;"
   ```
4. Create the schema. This project uses `EnsureCreated` implicitly through
   normal EF Core migrations - add the initial migration once:
   ```bash
   dotnet tool install --global dotnet-ef
   dotnet ef migrations add InitialCreate --project RentMate.Api
   dotnet ef database update --project RentMate.Api
   ```
   Re-run `dotnet ef migrations add <Name>` any time you change a model in
   `Models/`, then `dotnet ef database update` to apply it.

## 9. Git and GitHub Actions workflow

1. Create an empty repository on GitHub (e.g. `RentMate-API`), then from
   this folder:
   ```bash
   git init
   git add .
   git commit -m "Initial RentMate API project structure"
   git branch -M main
   git remote add origin https://github.com/<your-org-or-username>/RentMate-API.git
   git push -u origin main
   ```
2. **Commit as you go, not all at once.** For each endpoint or feature you
   touch, make a small, separately-described commit
   (`git commit -m "Add settle-up debt simplification endpoint"`). The POE
   checklist expects an active, regular commit history as evidence of your
   own work, not one giant drop.
3. The workflow at `.github/workflows/build-and-test.yml` runs automatically
   on every push and pull request against `main`/`develop`: it restores,
   builds, and runs every test in `RentMate.Api.Tests`. Check the **Actions**
   tab on GitHub after your first push to confirm it goes green. A red X
   means a test failed or the build broke - fix it before merging.
4. The same workflow file has a second, **disabled** job (`deploy`) for
   automatically deploying to Azure on every push to `main`. See the next
   section for how to turn it on once you actually have an Azure Web App to
   deploy to.

## 10. Deploying to Azure App Service

1. In the Azure Portal, create an **App Service** (Runtime stack: .NET 8,
   Operating System: Linux is cheaper on the free/student tier).
2. Set its configuration (Settings > Environment variables, or
   Configuration > Application settings) with the same keys you set locally
   via `user-secrets`, but as environment variables using `__` instead of
   `:`:
   - `ConnectionStrings__RentMate`
   - `Jwt__Key`
   - `Jwt__Issuer`, `Jwt__Audience`
   - `Google__AndroidClientId`
   - `Firebase__ServiceAccountJsonPath` (or switch that service to read the
     JSON from an environment variable instead of a file path if you'd
     rather not upload the file to the App Service's filesystem)
3. From the App Service's **Overview** page, click **Get publish profile**
   and download it.
4. In your GitHub repository, go to **Settings > Secrets and variables >
   Actions** and add a new repository secret named
   `AZURE_WEBAPP_PUBLISH_PROFILE`, pasting the entire contents of that file.
5. In `.github/workflows/build-and-test.yml`, replace
   `<YOUR-AZURE-WEBAPP-NAME>` with your App Service's name, and change
   `if: false && ...` to `if: ...` (just remove `false &&`) to enable the
   deploy job.
6. Push to `main`. The Actions tab should now show both jobs running, and a
   few minutes later your API is live at
   `https://<your-app-name>.azurewebsites.net/swagger`.

## 11. Handing this off to the rest of the team (Android integration)

Give whoever is doing the Android networking layer:
- The deployed base URL (or `http://10.0.2.2:5080` if they're pointing an
  emulator at your machine's local `dotnet run` instance)
- `RentMate.postman_collection.json`, so they can see exactly what every
  endpoint expects and returns
- This README's section 2 (Architecture) for the authentication flow
- The fact that every timestamp in request/response bodies is UTC ISO-8601

## 12. Extending: emailing the landlord letter

`POST /api/households/{id}/maintenance/send-to-landlord` currently builds
the letter text and marks the requests `Sent`, but does not send an actual
email - that needs an email provider (e.g. SendGrid, which has a free tier
that works well with Azure). To wire it up: add the `SendGrid` NuGet
package, inject an `ISendGridClient` the same way `INotificationService` is
injected, and call it with `household.LandlordEmail` and the `letter` string
from `MaintenanceController.SendToLandlord` right after `SaveChangesAsync`.

## 13. Marking rubric cross-reference

For your own checklist against the POE marking rubric:
- **Creation of the REST API (10 marks):** all 6 controllers above, with
  validation (see `BillsController.Create`'s amount/percentage checks) and
  the settle-up business logic in `DebtSimplificationService`.
- **GitHub, README and automated testing (10 marks):** this file, the
  commit-as-you-go instructions in section 9, and 22 tests across
  `RentMate.Api.Tests` (run `dotnet test` to see the count).
- **Integration in the app (10 marks):** owned by your teammates once they
  point the Android networking layer at this API - section 11 above is
  what to give them.
