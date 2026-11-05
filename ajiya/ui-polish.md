<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# UI polish and code quality

Goal: The app looks finished, consistent, handles every invalid input and logs its lifecycle

Owner: Ali. Part 2 UI scored 4/10: login was one line, screens were bare cards, raw IDs and errors showed.

RM-0034: theme tokens (type scale, spacing, shapes) and shared components (RentMateCard, SectionLabel, PrimaryButton, SecondaryButton, StatusPill, MemberAvatar, BrandMark, formatRand); branded Login; migrate every screen to them. RM-0035: EmptyState(illustration, title, body, action) and ErrorState(retry) on every list. RM-0036: validate every form inline (required, max length, positive amounts, max R1,000,000, dates not in the past where relevant); disable submit until valid. RM-0037: adaptive + monochrome launcher icon, core-splashscreen with the brand mark, empty-state illustrations as vector drawables. RM-0038: Log.d/i/w/e with a TAG per class in lifecycle callbacks, ViewModel init/onCleared, repository calls and sync; KDoc on every public class and function, with links to the docs used.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0034 | android | Design system: typography, spacing, components, branded Login screen | All screens use shared components; Login has logo, tagline and styled SSO button | - | - | 🟥 Pending |
| RM-0035 | android | Empty, loading and error states for every list screen | Each list screen shows an illustrated empty state and a retry error state | RM-0004, RM-0034 | - | 🟥 Pending |
| RM-0036 | android | Input validation on every form with inline messages | Blank, negative, oversized and malformed inputs are rejected inline; no crash with fuzzed input | RM-0034 | - | 🟥 Pending |
| RM-0037 | android | Production assets: adaptive icon, splash screen, final images | Custom adaptive icon and splash on device; no default Android assets remain | - | - | 🟥 Pending |
| RM-0038 | android | Structured Log calls across lifecycle and KDoc comments with references | Logcat shows tagged lifecycle/state logs; every class has KDoc; sources cited where used | - | - | 🟥 Pending |
