<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# UD4: Photo attachments (blob storage)

Goal: Tenants attach photos to bills and maintenance faults, stored in blob storage

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0027 | supabase | Supabase Storage bucket with household-scoped access policies | Member can upload/read own household's photos; non-member gets 403 | RM-0006 | - | 🟥 Pending |
| RM-0028 | android | Camera/gallery picker with compression and upload progress | Photo picked on phone uploads under 1 MB with progress shown; failure shows retry | RM-0027 | - | 🟥 Pending |
| RM-0029 | android | Show attachments on maintenance requests and bills, included in landlord email | Maintenance and bill detail show thumbnails; landlord email contains photo links | RM-0009, RM-0028 | - | 🟥 Pending |
