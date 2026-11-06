# UX observations and requirements

This file collects UX problems we find while testing RentMate, and the rule each one should follow. Add to it as you test. Each entry has an observation (what happens now), a requirement (what should happen) and an owner.

Status: 🔍 observed · 📋 agreed · ✅ done

---

## UX-01: Standardise how screens look and behave

**Status:** 🔍 observed · **Owner:** Ali (design system, RM-0034/0035) · **Found by:** Seth, 2026-11-06

### Observation

Screens build the same things in different ways, so the app feels like four people wrote it.

- Only 4 of 14 screens use the shared state views in `ui/components/StateViews.kt` (`LoadingState`, `EmptyState`, `ErrorState`, `InlineError`): Dashboard, Bills, Chores and Shopping List.
- Add Bill, Add Chore, Add Shopping Item, Household Setup and Maintenance each draw their own red error `Text`, with different spacing and styles.
- Activity, Settings and More have no shared loading, empty or error state at all.
- The same failure has four different messages: `error_generic` ("Something went wrong. Please try again."), `state_error_generic` ("Something went wrong. Check your connection and try again."), `activity_error` ("Could not load activity. Please try again later.") and `lock_error_other`.
- Wording style differs. Some messages are full sentences ending in a full stop ("Give the chore a title."), and others are fragments ("Too short", "This field is required").

### Requirement

1. Every list screen uses `LoadingState`, `EmptyState` and `ErrorState` from `StateViews.kt`. No screen draws its own spinner or error block.
2. Every form uses the shared field components in `FormFields.kt`, and shows field errors under the field with the same style.
3. Primary actions use `PrimaryButton`, secondary actions use `SecondaryButton`, and cards use `RentMateCard`.
4. One message per kind of failure. Keep the `AppError` set in `ui/error/ErrorMapper.kt` as the only source of failure messages, and delete the duplicates.
5. Every message is a full sentence that ends with a full stop.

### How to check

- `grep -rn "colorScheme.error" app/src/main/java/com/rentmate/app/ui/screens` finds nothing outside the shared components.
- Each screen shows the same loading, empty and error layout in airplane mode.

---

## UX-02: Show errors as a short message with clear wording

**Status:** 🔍 observed · **Owner:** Ali, with each screen's owner · **Found by:** Seth, 2026-11-06

### Observation

The app never uses a toast or snackbar. When an action fails, the user often gets no feedback, or the message lands somewhere they can't see it.

- **Silent failures.** These are only logged with `Log.w`. The user sees nothing:
  - Settings: saving any toggle (`SettingsViewModel` lines 101, 111 and 120). If the save fails, the switch looks changed, but the server value is not.
  - Settings: sign-out failing (line 85).
  - Chores: the forecast failing to load (`ChoresViewModel` line 123).
- **Hidden errors.** On Bills, a failed "Confirm received" writes its error behind the open dialog, so the user can't see it.
- **Vague errors.** "Something went wrong. Please try again." does not say what failed or what to do. A failed bill save and a failed photo upload show the same text.
- **Wrong numbers.** `error_amount_too_large` says "Amount can't be more than R999,999.99", but `FormValidation` allows exactly R1,000,000.

### Requirement

1. **Every user action gets feedback.** If a tap starts a network call, the user sees either the result or an error message. Nothing fails silently.
2. **Use a snackbar for action errors.** A Material 3 `Snackbar` at the bottom of the screen, shown through one shared `SnackbarHostState` in the app scaffold:
   - Errors from a tap (save, delete, mark paid, toggle) show as a snackbar.
   - Errors that stop a whole screen loading keep using `ErrorState` with a Retry button.
   - Field validation stays under the field (UX-01).

   We prefer a snackbar over an Android `Toast`. It stays inside the app's design, can carry a **Retry** action, and TalkBack reads it out. Users still see it as a short pop-up message, which is what a "toast" means here.
3. **Say what failed and what to do next.** Each message names the action and gives a next step:

   | Instead of | Write |
   |---|---|
   | Something went wrong. Please try again. | Couldn't save the bill. Check your connection and tap Retry. |
   | You don't have permission to do that. | Only the person who added this bill can mark it paid. |
   | That already exists. | This chore is already done for this week. |
   | Could not load activity. Please try again later. | Couldn't load recent activity. Pull down to try again. |

4. **Undo a toggle that fails.** If a settings change fails to save, the switch goes back to its old position, and a snackbar says "Couldn't save this setting. Try again."
5. **Messages close themselves.** Use `SnackbarDuration.Short`, or `Long` when there is a Retry action. Never stack more than one at a time.
6. **Every message is translated.** New strings go into `values`, `values-af` and `values-xh`. `TranslationCompletenessTest` fails the build if one is missing.

### How to check

- With airplane mode on, every button that calls the server shows a snackbar within a few seconds.
- Toggle a setting offline. The switch flips back, and a snackbar explains why.
- No message on any screen says only "Something went wrong."

---

## Adding an entry

Copy this template, give it the next number, and keep the sentences short.

```markdown
## UX-NN: <short name>

**Status:** 🔍 observed · **Owner:** <name> · **Found by:** <name>, <date>

### Observation
What happens now, on which screen, with file names or steps to reproduce.

### Requirement
What should happen instead, as numbered rules.

### How to check
A test someone can run on a phone in under a minute.
```
