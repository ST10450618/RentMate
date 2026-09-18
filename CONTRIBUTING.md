# Contributing — RentMate team guide

Four people pushing to one repository over several weeks. These conventions exist
so the commit history reads as deliberate work rather than a dump, which is what
the GitHub criterion is actually marked on.

## Branches

| Branch | Purpose |
| --- | --- |
| `main` | Always builds. Only merged into from `develop` at milestones. Tagged `part-2` and later `final-poe`. |
| `develop` | Integration branch. Everyone merges here. |
| `feature/<area>-<short-name>` | One feature, one branch. Example: `feature/maintenance-log`, `feature/api-bills-controller`. |
| `fix/<short-name>` | Bug fixes found during integration testing. |

Never push directly to `main`. Open a pull request from your feature branch into
`develop`, and have one other person approve it — pull request reviews are the
clearest evidence of collaboration a marker can see.

## Commit messages

Use Conventional Commits. The format is `type(scope): imperative summary`.

```
feat(maintenance): add photo attachment to request form
fix(sync): stop queued chore completions duplicating on retry
test(bills): cover percentage split validation
docs(readme): add architecture diagram
chore(ci): cache Gradle dependencies between runs
refactor(nav): extract bottom bar into its own composable
```

Types in use: `feat`, `fix`, `test`, `docs`, `chore`, `refactor`, `style`.

Rules that matter:

- Imperative mood — "add", not "added" or "adding".
- Summary under 72 characters, no full stop at the end.
- One logical change per commit. Splitting work into real commits is how you
  reach 50+ honestly; padding with "update" and "fix stuff" is visible and
  counts against the same criterion it's meant to satisfy.
- Reference the user story where it helps: `feat(settings): add isiXhosa strings (US-4)`.

## Commit cadence

The rubric wants 50+ commits for Part 2 and 75+ by the final POE, from multiple
contributors. At four people over eight weeks that is roughly two commits each
per working day — which is what normal incremental work produces anyway, provided
nobody batches a week of changes into one push.

Push at least once per working day, even mid-feature. A half-finished feature on
your own branch harms nobody and proves the work was spread over time.

## Pull requests

Title uses the same convention as commits. In the description, state:

- What changed and which user story it delivers.
- How you tested it (unit test, physical device, Postman).
- Anything the reviewer should watch for.

CI must be green before merge. If lint or a unit test fails, fix it on the branch
rather than merging past it — a red run on `develop` blocks everyone else.

## Code style

- Kotlin: follow the official style guide; Android Studio's default formatter is
  configured for it. Format before committing.
- Comment intent, not mechanics. `// server timestamp wins so two devices can't
  both claim the chore` is useful; `// set the variable` is not.
- Where you adopt an external code block or pattern, cite the source in a comment
  above it. The brief requires this and it is quick to do at the time, slow to
  reconstruct later.
- Use `Log.d`/`Log.i`/`Log.e` with a consistent tag per class. Lifecycle and state
  transitions are explicitly marked, so log them as you build rather than
  retrofitting logging the night before submission.
