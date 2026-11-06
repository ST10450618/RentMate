# send-push

Sends a data-only Firebase Cloud Messaging (FCM) push to the phones of the people you name. The database calls it. The app never does.

## What triggers it

Migration `0016_push_triggers.sql` calls this function through `pg_net` when:

- someone adds a bill (each housemate except the issuer gets their own share in the text),
- a repair request changes status (the person who raised it and the housemates, except whoever made the change),
- the daily job `notify-chores-due` runs at 05:00 UTC (07:00 in South Africa) and a chore has not been done this cycle (the current assignee gets it).

## Request

`POST` with header `x-push-secret: <PUSH_WEBHOOK_SECRET>`. Any other secret gets 401.

```json
{ "user_ids": ["<uuid>"], "channel": "bills", "title": "New bill: Electricity", "body": "...", "id": "<uuid or null>" }
```

`channel` is `bills`, `chores`, `shopping` or `maintenance`. Title is 1 to 120 characters, body up to 1000, id up to 64, and `user_ids` holds 1 to 50 uuids. The function skips people whose matching `profiles.notify_*` switch is off. It deletes tokens that FCM reports as unregistered. It answers `{ "sent": n, "failed": n, "removed": n }`.

The FCM message carries only `data` (channel, title, body, id), as the app expects in `RentMateMessagingService`.

## Secrets

Already on the hosted project: `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_PROJECT_ID`. New: `PUSH_WEBHOOK_SECRET`, a long random string. `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` come from the Edge runtime.

## One-time setup (do not run until the team agrees)

1. Enable the `pg_net` extension (Dashboard, Database, Extensions). Also enable `pg_cron` if it is not on yet.
2. Pick a secret, for example `openssl rand -hex 32`, and set it:
   `supabase secrets set PUSH_WEBHOOK_SECRET=<secret> --project-ref <ref>`
3. Deploy the function (`verify_jwt` is off in `supabase/config.toml`):
   `supabase functions deploy send-push --project-ref <ref>`
4. In the SQL editor, store the same secret and the function URL in Vault:
   ```sql
   select vault.create_secret('<secret>', 'push_webhook_secret');
   select vault.create_secret('https://<ref>.supabase.co/functions/v1/send-push', 'push_function_url');
   ```
5. Apply migration 0016 (`supabase db push`) if it is not applied yet.

Until step 4 is done, the database skips pushes and only logs a notice. Bills still save.

## Tests

`node --test supabase/functions/send-push/push.test.ts` covers the pure parts in `push.ts`. `supabase/tests/push_triggers_test.sql` covers the triggers.
