# Google Play Store Listing Pack

Everything needed to fill in the Play Console for RentMate (RM-0041). Character limits are Google Play's; counts are given so nothing is cut off.

## Store listing

| Field | Value | Limit |
|---|---|---|
| App name | RentMate: Shared Home Manager | 29 / 30 |
| Short description | Split bills, rotate chores and log repairs with the people you live with. | 73 / 80 |
| Category | House & Home | — |
| Tags | Home management, Finance, Productivity | — |
| Contact email | royalalite@gmail.com | — |
| Privacy policy URL | https://github.com/ST10450618/RentMate/blob/main/PRIVACY.md | — |
| Default language | English (South Africa), en-ZA | — |

### Full description (about 1,500 / 4,000 characters)

```
RentMate keeps a shared home running without the group-chat arguments.

Built for South African students and young professionals sharing a house or flat, RentMate puts bills, chores, the shopping list and repairs in one place that every housemate can see.

SPLIT BILLS, SETTLE UP IN FEWER PAYMENTS
• Add a bill once and RentMate splits it across the household.
• See who has paid and who still owes, in rand.
• Settle-up works out the smallest set of payments that clears everyone's balances, so six transfers become three.
• Preview who owes whom even when you're offline.

FAIR CHORES, PLANNED AHEAD
• Chores rotate automatically between housemates.
• See the next four weeks of the rotation, so nobody argues about whose turn it is.
• Earn points for completed chores, or hide the leaderboard for the whole house.

REPAIRS YOUR LANDLORD CAN'T IGNORE
• Log a fault with a photo, a category and an urgency.
• Send every open fault to your landlord in one dated, itemised email.
• Track each request from open to sent, acknowledged and resolved.

SHARED SHOPPING LIST
• Add items, tick them off when they're bought, and see who added what.

BUILT FOR REAL LIFE
• Works offline: add bills and shopping items with no signal, and they sync when you're back online.
• Real-time notifications when a bill is added, a chore is due or a repair is updated.
• Unlock with your fingerprint or face.
• Available in English, Afrikaans and isiXhosa.
• Sign in securely with your Google account.

Your household's data is visible only to the members of that household.
```

## Graphics

| Asset | Requirement | File |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit | `icon-512.png` |
| Feature graphic | 1024 × 500 PNG or JPG | `feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, 16:9 or 9:16, 320–3840 px per side | `screenshots/` (see the shot list below) |
| 7-inch and 10-inch tablet screenshots | Optional, recommended because RentMate is tested on a tablet | `screenshots/tablet/` |

### Screenshot shot list

Capture these on the physical device with demo data in the household, light theme, status bar clean (full battery, no notifications). Name the files in this order:

1. `01-dashboard.png`: Dashboard with balance, next chore and household avatars
2. `02-bills.png`: Bills list with paid and outstanding pills
3. `03-settle-up.png`: the settle-up dialog showing the simplified payments
4. `04-chores.png`: Chores with the four-week forecast and leaderboard
5. `05-maintenance.png`: Maintenance request with photo and status timeline
6. `06-offline.png`: offline banner with a pending item
7. `07-afrikaans.png`: any main screen in Afrikaans or isiXhosa
8. `08-login.png`: branded Login screen

## App content (Play Console declarations)

| Section | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | No ads |
| App access | Restricted: Google sign-in is required. Give reviewers a test Google account and an invite code for a demo household. |
| Content rating (IARC questionnaire) | Utility/productivity app. No violence, sexual content, profanity, drugs, gambling or user-to-user chat with strangers. Users share data only inside their own household. Expected rating: Everyone / 3+. |
| Target audience | 18 and over; not designed for children. |
| News app | No |
| Government app | No |
| Financial features | None. RentMate records who owes whom; it does not move money or provide financial services. |
| Health | None |

### Data safety form

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Name | Yes | No | Account management, app functionality | No |
| Email address | Yes | No | Account management | No |
| Photos | Yes | No | App functionality (maintenance and bill photos) | Yes |
| Other user-generated content (bills, chores, shopping items, requests) | Yes | No | App functionality | No |
| Device or other IDs (push notification token) | Yes | No | App functionality (notifications) | Yes |

- Data is encrypted in transit: **Yes** (HTTPS to Supabase and Firebase).
- Users can request deletion: **Yes**, by email (see `PRIVACY.md`).
- Fingerprint and face data: **not collected.** Biometric checks run on the device through Android's BiometricPrompt and nothing leaves the phone.

## Release

| Item | Value |
|---|---|
| Package name | `com.rentmate.app` |
| Version | 1.0.0 (versionCode 2) |
| Upload format | Android App Bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Signing | Upload key in `keystore/rentmate-release.jks` (not in git). Enrol in Play App Signing on first upload. |
| Minimum / target SDK | 26 (Android 8.0) / 36 |
| Release notes (en-ZA, max 500) | First public release: bill splitting with settle-up, chore rotation with a four-week forecast and points, maintenance requests with photos, shared shopping list, offline mode, push notifications, biometric unlock, and English, Afrikaans and isiXhosa. |

After Play App Signing is enabled, Google re-signs the app with its own key. Add Play's **app signing key SHA-1** (Play Console → Setup → App signing) to the Google Cloud OAuth Android client and to Firebase, or Google sign-in will fail for users who install from the Play Store.
