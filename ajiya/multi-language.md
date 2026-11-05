<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Multi-language

Goal: Every screen works fully in English, Afrikaans and isiXhosa, switchable in-app

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0024 | android | Move every hard-coded string into resources | Lint HardcodedText/SetTextI18n clean; grep finds no literal UI strings in screens | - | - | 🟥 Pending |
| RM-0025 | android | Complete Afrikaans and isiXhosa translations, reviewed by a speaker | values-af and values-xh have every key; reviewer name recorded in README | RM-0024 | - | 🟥 Pending · Needs a human: translations need checking by an Afrikaans and an isiXhosa speaker |
| RM-0026 | android | In-app language picker using per-app locales, persisted | Switching language in Settings changes every screen immediately and survives restart | RM-0024 | - | 🟥 Pending |
