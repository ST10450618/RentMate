<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# UD4: Photo attachments (blob storage)

Goal: Tenants attach photos to bills and maintenance faults, stored in blob storage

Owner: James. User-defined feature 4, covers the 'use blob storage' learning outcome.

Supabase Storage bucket 'attachments', private. Path convention households/<household_id>/<maintenance|bills>/<uuid>.jpg. Storage policies on storage.objects check is_household_member of the folder's household id. Client: PickVisualMedia and TakePicture contracts, downscale to 1600px and JPEG quality 80 before upload, progress indicator, retry on failure. Store the object path on maintenance_requests.photo_path / bills.receipt_path; load with signed URLs (Coil AsyncImage). Landlord email includes 7-day signed links.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0027 | supabase | Supabase Storage bucket with household-scoped access policies | Member can upload/read own household's photos; non-member gets 403 | RM-0006 | - | 🟨 In progress |
| RM-0028 | android | Camera/gallery picker with compression and upload progress | Photo picked on phone uploads under 1 MB with progress shown; failure shows retry | RM-0027 | - | 🟥 Pending |
| RM-0029 | android | Show attachments on maintenance requests and bills, included in landlord email | Maintenance and bill detail show thumbnails; landlord email contains photo links | RM-0009, RM-0028 | - | 🟥 Pending |
