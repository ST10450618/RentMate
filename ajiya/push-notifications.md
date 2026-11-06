<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Real-time notifications

Goal: Housemates get real-time FCM pushes for bills, chores and maintenance updates

Owners: Seth (RM-0020 Firebase project, RM-0022 Edge Function), Ali (RM-0021 client, RM-0023 deep links). Docs: https://firebase.google.com/docs/cloud-messaging.

Client: firebase-messaging, a FirebaseMessagingService that saves onNewToken to a device_tokens table (user_id, token, platform, updated_at; RLS: own rows), request POST_NOTIFICATIONS on Android 13+, channels per type (bills, chores, shopping, maintenance) with translated names. Server: Supabase Edge Function 'notify' called by database webhooks on bills insert, chores assignment and maintenance_requests status update; it reads recipients' tokens and their profile toggles, then calls FCM HTTP v1 with a service-account access token stored in Supabase secrets (never in git). Data payload includes 'screen' and 'id' for deep links.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0020 | infra | Create Firebase project, add google-services.json and service account secret | Firebase project exists; app builds with google-services plugin; secret stored outside git | - | - | 🟩 Done · by Seth · 2026-11-06 · tests passed · Note: Firebase project rentmate-b120f (company account) with Android app com.rentmate.app, shared debug SHA-1, FCM v1 and Firestore africa-south1. google-services.json committed; plugin builds. Service account stored as Supabase secrets FIREBASE_SERVICE_ACCOUNT + FIREBASE_PROJECT_ID, not in git. |
| RM-0021 | android | FCM client: token registration, POST_NOTIFICATIONS permission, channels | Token saved to device_tokens table on sign-in; test message from Firebase console arrives on phone | RM-0020 | - | 🟩 Done · 56f7678 · 2026-11-06 · tests passed |
| RM-0022 | supabase | Edge Function sends FCM on new bill, chore due, maintenance status change | Adding a bill on phone A pushes a notification to phone B within seconds | RM-0021 | - | 🟨 In progress |
| RM-0023 | android | Notification tap deep-links to the relevant screen; per-type toggles in Settings | Tapping a bill push opens Bills; disabling bill pushes stops them | RM-0022 | - | 🟥 Pending |
