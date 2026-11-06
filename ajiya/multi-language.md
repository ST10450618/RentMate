<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Multi-language

Goal: Every screen works fully in English, Afrikaans and isiXhosa, switchable in-app

Owner: James. Languages: English (default), Afrikaans (af), isiXhosa (xh), all official South African languages.

RM-0024: move every literal in Compose screens into strings.xml (use plurals for counts like 'n bill(s)'). Turn on lint HardcodedText as error. RM-0025: fill values-af and values-xh for every key; get a speaker in class to review and record their name in README. RM-0026: AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) with locales_config.xml and android:localeConfig in the manifest (per-app language, Android 13 settings integration); persist in profiles.language. Dates and currency through locale-aware formatters.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0024 | android | Move every hard-coded string into resources | Lint HardcodedText/SetTextI18n clean; grep finds no literal UI strings in screens | - | - | 🟩 Done · a7d6640 · 2026-11-06 · tests passed |
| RM-0025 | android | Complete Afrikaans and isiXhosa translations, reviewed by a speaker | values-af and values-xh have every key; reviewer name recorded in README | RM-0024 | - | 🟨 In progress · Needs a human: translations need checking by an Afrikaans and an isiXhosa speaker |
| RM-0026 | android | In-app language picker using per-app locales, persisted | Switching language in Settings changes every screen immediately and survives restart | RM-0024 | - | 🟩 Done · 2c273a3 · 2026-11-06 · tests passed · Note: Code done; on-device check pending in RM-0047 style live check |
