<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Release and submission

Goal: A signed, store-ready build is tagged Final POE with full documentation and video

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0039 | android | Release build: signing config from secrets, R8 minify, versionCode bump | assembleRelease produces a signed APK/AAB that installs and runs on a physical phone | RM-0037 | - | 🟥 Pending |
| RM-0040 | infra | CI builds signed release artefact on tag | Pushing a tag uploads the signed APK as a workflow artefact | RM-0012, RM-0039 | - | 🟥 Pending |
| RM-0041 | infra | Play Store listing pack: screenshots, feature graphic, description, privacy policy | docs/store/ holds phone screenshots, 1024x500 graphic, listing text and hosted privacy policy URL | RM-0035, RM-0037 | - | 🟥 Pending |
| RM-0042 | infra | README as technical report with images, DevOps section and release notes | README has purpose, design, architecture diagram, CI, screenshots, release notes since prototype | RM-0041 | - | 🟥 Pending |
| RM-0043 | infra | AI-use write-up (max 500 words) with citations | AI_USAGE.md is filled in, under 500 words, cites tools and prompts | - | - | 🟥 Pending |
| RM-0044 | infra | Record demo video on a physical phone with voice-over, link in README | Unlisted YouTube link in README covers every point in the brief's video checklist | RM-0039, RM-0042 | - | 🟥 Pending · Needs a human: a team member must record the video on a physical phone |
| RM-0045 | infra | Reach 75+ well-structured commits with all four members contributing | git shortlog shows 75+ commits and commits from every member | - | - | 🟥 Pending · Needs a human: each teammate must commit their own work |
| RM-0046 | infra | Tag final deployment commit as Final POE and submit before deadline | Tag final-poe exists on origin pointing at the submitted commit | RM-0003, RM-0005, RM-0007, RM-0008, RM-0011, RM-0014, RM-0019, RM-0023, RM-0025, RM-0026, RM-0029, RM-0031, RM-0033, RM-0036, RM-0038, RM-0040, RM-0042, RM-0043, RM-0044, RM-0045 | - | 🟥 Pending · Needs a human: a person confirms the submission |
