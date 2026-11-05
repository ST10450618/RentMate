<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Real-time notifications

Goal: Housemates get real-time FCM pushes for bills, chores and maintenance updates

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0020 | infra | Create Firebase project, add google-services.json and service account secret | Firebase project exists; app builds with google-services plugin; secret stored outside git | - | - | 🟥 Pending · Needs a human: needs a Google account to create the Firebase project |
| RM-0021 | android | FCM client: token registration, POST_NOTIFICATIONS permission, channels | Token saved to device_tokens table on sign-in; test message from Firebase console arrives on phone | RM-0020 | - | 🟥 Pending |
| RM-0022 | supabase | Edge Function sends FCM on new bill, chore due, maintenance status change | Adding a bill on phone A pushes a notification to phone B within seconds | RM-0021 | - | 🟥 Pending |
| RM-0023 | android | Notification tap deep-links to the relevant screen; per-type toggles in Settings | Tapping a bill push opens Bills; disabling bill pushes stops them | RM-0022 | - | 🟥 Pending |
