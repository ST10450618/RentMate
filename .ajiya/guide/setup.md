# Setting up a plan with Ajiya

Use this guide when a project has no plan yet, or when tickets were just imported
and need to be organised. For everyday work on a planned project, read
`daily.md` instead.

The plan lives in `ajiya/`, one markdown file per phase. You decide what the work
is. The `ajiya` command does all the bookkeeping: IDs, files, statuses, evidence
and order. Never edit `ajiya/` by hand; `ajiya check` finds hand edits. Change
`ajiya.toml` with commands too; the one exception is its `[test]` command
(section 2).

## 1. Understand the goal

Read what the user asked for and what the repository already contains. Then:

- Ask **at most three questions**, and only when the answer changes the plan
  (for example "One clinic or many?" changes the data model; "Blue or green?"
  does not).
- For everything else, choose a sensible default and write it down as an
  assumption. You will list the assumptions in the hand-over (section 7).

## 2. Set up the project

If there is no `ajiya.toml`, run:

```
ajiya init
```

`init` suggests apps from workspace files and manifests (`package.json`, `go.mod`,
`pyproject.toml` and so on). Confirm the ones that are real apps. Use
`ajiya init --yes` to accept the suggestions without questions.

`init` also installs this guide, `daily.md`, the Claude Code skill, and a block
between `<!-- ajiya:start -->` and `<!-- ajiya:end -->` in `AGENTS.md` and
`CLAUDE.md`. Running `ajiya init` again in a project that is already set up only
brings those up to date: it keeps `ajiya.toml` and the plan as they are, and
never touches text outside the markers. Then:

- Register any app that `init` missed: create its folder first, then
  `ajiya app add <name> --path <dir>` (add `--library` for a shared package).
- Use the app `infra` for work outside app folders: hosting, CI, DNS.
- Register Ajiya's MCP server for the agents in use (Claude Code, Codex,
  Cursor), so they get Ajiya's tools instead of shell commands: `ajiya mcp status`
  shows which agents on this machine have it, and `ajiya mcp install` adds it (it
  shows the change and asks first; `--yes` skips the question, `--scope project`
  registers for this project only; it ends by printing the command that undoes it).
  `ajiya mcp uninstall` removes it, and `ajiya mcp status --claude --quiet` exits 0
  when Claude Code has it, 1 when not. This
  is a person's choice, so do it when the user asks or agrees; there is no MCP tool
  for it.
- Run `ajiya hook install`. The hooks check every commit message for the
  `Ajiya:` trailer and pre-fill it from the tickets in progress.
  When `ajiya.toml` is in a folder above several app repositories, run
  `ajiya hook install --apps`; evidence is then searched in every app repository.
- Set the command that runs the project's tests, so `ajiya ticket done --test`
  can run it. `init` sets it when it can tell (`go.mod`, a `package.json` test
  script, `pytest` in `pyproject.toml`) and says so; check that it is right. When
  it could not tell, no command sets it: edit the `[test]` section of `ajiya.toml` by
  hand, which is the only hand edit Ajiya expects. Use the command the project
  already uses (its CI config or `package.json` scripts show it); ask the user
  if there is none.

  ```toml
  [test]
  command = "npm test"
  ```

## 3. Phases: one per feature

A phase is one whole feature or function that a person would name: "Accounts",
"Bookings", "Admin", "Go-live". It is not a layer ("Backend") or a stage
("Testing").

```
ajiya phase add bookings "Patients book, move and cancel appointments"
```

The goal is one sentence that says what is true when the phase is done. A phase
can hold tickets for several apps. That is normal: a feature usually needs API,
UI and data work.

## 4. Tickets: one session each

A ticket is one piece of work an agent can finish **and prove** in one session.

```
ajiya ticket add --phase bookings --app api "Booking model and migration" \
  --done-when "Migration applies and rolls back; model tests pass" \
  --depends CB-0003
```

- **Title**: what gets built, in a few words.
- **Done when**: a check someone can run or see, not a restatement of the title.
  "Tests for overlapping bookings pass" is a check; "Bookings work" is not.
- **Depends**: the tickets that must be done first. Dependencies are the only
  source of order. If B needs A, say so, or `ajiya next` may offer B too early.
- **Tags** are optional. Add `--tech <slug>` only when a ticket depends on a
  specific technology or service, and run `ajiya tags` first to reuse a value.
  `daily.md` has the rules.
- Too big for one session? Split it. Too small to prove? Merge it into another.

### Work that is easy to forget

Before you finish, check that the plan covers each of these where the project
needs it. Add a ticket for each one that applies:

- production secrets and configuration
- database migrations, and how they run on deploy
- backups, and a tested restore
- monitoring, alerts and error tracking
- domain, DNS and TLS certificates
- error pages (404, 500) and empty states
- privacy notice and terms, and consent where the law requires it
- a rollback plan for a bad deploy

### Never invent facts

Do not invent hosts, providers, prices, dates, people or account names. When the
plan needs one, add a ticket that a person must finish:

```
ajiya ticket add --phase go-live --app infra "Choose a host" \
  --done-when "Host chosen and account created" \
  --human "needs a budget decision"
```

## 5. Milestones and launch

A milestone is a named gate, such as `staging` or `launch`. Each milestone
targets phases or tickets. It needs its targets **and everything they depend on**.
Milestones are checked in order, and a ticket belongs to the earliest milestone
that needs it.

```
ajiya milestone add launch --targets go-live
ajiya milestone add after-launch --targets reporting --after launch
```

For a single target, `ajiya launch set <phase|ticket>` is enough. Tickets that
no milestone needs are "unscheduled"; `ajiya check` warns about open ones. Either
make a milestone need them or add a later milestone for them.

## 6. Check the plan

```
ajiya phase order --suggest     # a reading order worked out from dependencies
ajiya phase order --apply       # write it to ajiya.toml (display only)
ajiya check --strict            # fix every finding
ajiya next                      # what can start first
ajiya build                     # ajiya/PROGRESS.md and the dashboard data
```

The phase order is for reading only. It never changes what can start: only
dependencies and milestones do that.

## 7. Hand over

Commit the plan (`Ajiya: chore` is fine for a plan-only commit), then give the
user a short summary:

- phases and ticket counts
- what the first milestone (or launch) needs, and what `ajiya next` offers first
- the assumptions you made (section 1)
- the `Needs a human` tickets, with the reason for each

Then stop and wait. Do not start building until the user says so.

## Changing the structure later

| Change | Command | Who decides |
|---|---|---|
| Rename a phase | `ajiya phase rename <old> <new> [--title "<title>"]` | you |
| Remove an empty phase | `ajiya phase remove <slug>` (refused while it holds tickets or a milestone or the launch target names it) | you |
| Describe a phase | `ajiya phase describe <slug> "<text>"` (or `phase add ... --description`; `-` clears it) | you |
| Show a phase earlier or later | `ajiya phase move <slug> --before <slug>` (or `--after`, `--first`, `--last`) | you |
| Move an app's folder | `ajiya app move <name> --path <dir>` | you |
| Remove an app | `ajiya app remove <name> --to <app>` (moves its open tickets) | a person |
| Reorder a milestone | `ajiya milestone move <name> --before <name>` (or `--after`) | a person |
| Remove a milestone | `ajiya milestone remove <name>` (tickets stay) | a person |

For the ones a person decides, propose the change and wait for a yes.

## Organising imported tickets

Imports bring existing work into Ajiya. The CLI imports; you organise afterwards.
Imports never reword items, and you should not either.

| Source | Command | Lands in |
|---|---|---|
| A markdown to-do list | `ajiya import todo <file> [--app <app>]` | phase `inbox`; ticked items are done |
| GitHub issues (needs `gh`) | `ajiya import github [--repo owner/name] [--app <app>]` | phase `inbox`; closed issues are done |
| A legacy WBS (the `collate.py` format) | `ajiya import legacy <config> [--app <app>]` | a phase per rollout work package (or per file); old IDs kept as aliases |

After an import:

1. Read what it printed: the app it used ("Using app X.") and how many items it
   imported into `inbox`. It does not list duplicates, so review the tickets
   yourself with `ajiya ticket list --phase inbox` (add `--json` to read them in a
   script); do not read the files in `ajiya/`. `import legacy` also prints a
   report with the links it left out and the old IDs.
2. Create the feature phases (section 3) and move each ticket out of `inbox`:
   `ajiya ticket edit <ID> --phase <slug>`. Set the right app with `--app`.
3. Imported tickets have no "done when" (it shows as `-`). Give each one a check
   someone can run or see: `ajiya ticket edit <ID> --done-when "<check>"`.
4. Add the dependencies the source did not have:
   `ajiya ticket edit <ID> --depends <IDs>`. This replaces the list, so include the
   ones already there (`ajiya ticket show <ID>` lists them).
5. Add tickets for gaps, including the forgotten work in section 4.
6. Duplicates and items that are no longer needed: list them for the user. Only a
   person decides to drop a ticket (`ajiya ticket drop <ID> --reason "..." --by "<name>"`).
7. When `inbox` is empty, remove it: `ajiya phase remove inbox`.
8. Set milestones (section 5), check (section 6), and hand over (section 7).

Old IDs stay as aliases, so `ajiya ticket show MI3-185` still finds the ticket.

### A legacy WBS with a rollout file

When the legacy config names a rollout WBS, the import already groups the tickets
by feature, so most of steps 2 and 3 are done:

- Each rollout work package (`RO-021`) becomes a phase, like
  `ro-021-production-host-and-deploy`, and the tickets its scope counts move into
  it. There is no `RO-021` ticket: the report (and `--json`) maps each RO ID to
  its phase.
- A ticket keeps its area as its app: give each source an `app = "<app>"` key in
  the legacy config (register the app first); a source without one uses `--app`.
- A package with no scope (acceptance, cutover) is a phase with one ticket; a
  pending one is marked `Needs a human`.
- When package A depends on package B, the first tickets of A wait for the last
  tickets of B. Links that would make a loop are left out and reported.
- A ticket named by several packages stays in the first; the report lists the rest.
- A cross-reference ("tracked as ...") is imported as dropped, so the work counts
  once. The report lists them: reopen one if it is really separate work.
- Open tickets in no package go to `inbox` (organise them as above); done ones to
  `archive`.
- Each `##` heading of the rollout file becomes a milestone (`gate-0`,
  `phase-a`) that targets the phases under it, and the phases are shown in the
  order of the file.

Without a rollout file, each source file becomes one phase.

After this import, read the report, then check the phases and milestones
(`ajiya phase list`, `ajiya milestone list`), organise `inbox` (`ajiya ticket list --phase inbox`), and run `ajiya check`.
