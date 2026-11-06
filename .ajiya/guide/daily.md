# Working with Ajiya every day

Use this guide for every task in a project that has a plan (`ajiya.toml` and an
`ajiya/` folder). To plan a project or organise imported tickets, read
`setup.md` instead.

The plan lives in `ajiya/`. Never edit it by hand: change it only with `ajiya`
commands. The same goes for `ajiya.toml`, except its `[test]` command. Trust the
plan over your memory of earlier sessions.

## The loop

### 1. Pick a ticket

At the start of every session, see where the project stands, then pick:

```
ajiya status
ajiya next
```

`ajiya status` is a short summary: progress to each milestone, tickets already
in progress (and for how long), what can start, tickets that need a human, the
check results and the latest activity. `ajiya status --brief` is three lines.
Finish what is in progress before you start something new.

It lists the tickets that can start now: not started, not blocked, every
dependency done. The earliest milestone comes first, then what unblocks the most work. Take
the first one unless the user asked for something else. Skip tickets marked
`Needs a human` unless the user asks for them: tell the user about them instead.

If the user asks for work that is not in the plan, find the ticket
(`ajiya ticket show <ID>` also finds old IDs) or add one first (step 6).

If the ticket the user wants is not in `ajiya next`, run `ajiya ticket show <ID>`
to see what it waits on, and **tell the user before you start it**. Starting
blocked work usually means building on something that is not there yet.

### 2. Start it

```
ajiya ticket start <ID>
```

Read its "done when". That is the check you must pass, not just the title.

### 3. Write the code and the tests

Keep to the ticket. Tests prove the "done when" where they can.

### 4. Commit with the trailer

Every commit ends with a trailer that names 1 to 3 tickets:

```
Add booking model and migration

Ajiya: CB-0142
```

- More than 3 tickets means the commit is too big: split it.
- `Ajiya: chore` is for upkeep with no ticket: dependency updates, formatting,
  config, plan-only changes.
- Merge commits and reverts need no trailer.
- Other trailers may sit in the same last paragraph. Git reads them all:

  ```
  Ajiya: CB-0142
  Co-Authored-By: Name <name@example.com>
  ```

The git hooks from `ajiya hook install` check the trailer and pre-fill it from the
tickets in progress. If a hook refuses a commit, fix the message; never skip the
hook.

### 5. Mark it done

```
ajiya ticket done <ID> --test
```

`done` refuses unless a commit names the ticket, and records the newest such
commit as evidence. Only commits made after `ticket start` count, so commit the
work itself with the trailer before you close the ticket. `--test` runs the
project's test command and refuses if it fails. Never mark a ticket done any
other way, and never before its "done when" is met.

`done` changes one file: the ticket's phase file in `ajiya/`. It does not run
`ajiya build`. `ajiya build` writes `ajiya/PROGRESS.md`, `ajiya/data.js` and
`ajiya/index.html`. Commit the files that changed with the same ticket's trailer
(`Ajiya: <ID>`) so the tree is clean.

If `done --test` says there is no `[test]` command, the project has not set one
yet. Set it in `ajiya.toml` as `setup.md` section 2 describes (ask the user if you
cannot tell the command), then run `done --test` again. Leave out `--test` only
when the project has no tests at all, and say so in your summary.

### 6. Check

```
ajiya check
```

Fix every error before you finish. Then tell the user what you did, and which
ticket is next.

## On the way

**Found extra work?** Add a ticket; do not widen the current one.

```
ajiya ticket add --phase <slug> --app <app> "<title>" \
  --done-when "<check>" --depends <IDs> [--tech <slugs>] [--decisions <IDs>]
```

Add tags only as the Tags section below says.

If a milestone should wait for it, make a ticket that milestone needs depend on it.

**Stuck on something outside your control?** Block the ticket and say what it
waits on. `ajiya ticket start` clears the block later.

```
ajiya ticket block <ID> "waiting for the API key from the payment provider"
```

**Needs a person?** Some work only a person can do: choose a host, sign a
contract, approve a design. Such tickets are marked `Needs a human`. Do not do
them yourself. When the person has done it, they (or you, on their word) close it:

```
ajiya ticket done <ID> --by "<name>" --note "<what was done>"
```

**Not needed any more?** Tell the user. Only a person drops a ticket:
`ajiya ticket drop <ID> --reason "<why>" --by "<name>"`.

**Created a new app folder?** Register it in the same task:
`ajiya app add <name> --path <dir>` (`--library` for a shared package). Create
the folder first: `app add` only warns when the path is not a folder yet. For
work outside app folders (hosting, CI, DNS), use the app `infra` instead.

**Ticket has no "done when"?** Imported tickets show `-`. Set one before you start:
`ajiya ticket edit <ID> --done-when "<check>"`.

**Plan wrong?** Fix it with commands: `ajiya ticket edit <ID>` changes the title,
"done when", dependencies, app or phase. `--depends` replaces the whole list, so
include the ones already there.

## Tags: optional context on a ticket

Tags are optional. Most tickets have none, and `ajiya check` never warns about a
missing tag. Tags exist so that a later change to the plan can find the tickets
it affects. There are three keys:

| Tag | Use it for | Add it when |
|---|---|---|
| `tech:<slug>` | A technology or service, such as `tech:aws-rds` | The ticket depends on that specific technology or service |
| `dec:<ID>` | A decision, such as `dec:DEC-0003` | The ticket exists because of that decision (set with `--decisions`) |
| `path:<glob>` | Files, such as `path:infra/rds/**` | The ticket has no commits yet and you already know where its files will be |

Examples:

- "Configure RDS backups" depends on a service: add `--tech aws-rds`.
- "Add a booking form" depends on no specific technology or service: add no tag.

Rules:

1. Run `ajiya tags` first. It lists every tag in use with its ticket count.
   Reuse a value that is there. Do not make `postgresql` when `postgres` exists.
2. Add `tech` only for a specific technology or service. The App column already
   says which part of the project a ticket belongs to, so there is no tag for it.
3. Use `path` only while the ticket has no commits. After that,
   `ajiya ticket files <ID>` reads the files from git. Do not copy them into the plan.
4. Never store what the CLI can work out: files, commits, readiness or
   milestone membership.

```
ajiya tags
ajiya ticket add --phase core --app api "Configure RDS backups" \
  --done-when "Daily snapshot, 7-day retention" --tech aws-rds
ajiya ticket edit <ID> --tech aws-rds,postgres
ajiya ticket files <ID>
```

`--tech`, `--decisions` and `--paths` take comma-separated values. On
`ticket edit`, a tag flag replaces all values of that key; `-` clears them.
`ajiya ticket files <ID>` lists the files changed by the commits that name the
ticket.

## Decisions

A decision records a choice the plan rests on, such as "Use PostgreSQL on AWS
RDS". Record one when other tickets would change if the choice changed. Do not
record small choices inside one ticket.

```
ajiya decision add "Use PostgreSQL on AWS RDS" \
  --decision "Run PostgreSQL on AWS RDS" \
  --context "We need a managed database" \
  --consequences "Backups use the RDS snapshot API" \
  --tech aws-rds
```

1. `decision add` records the decision as `Proposed`. `--context` and
   `--consequences` are optional; `--by` names who proposed it.
2. A person decides. When they say yes, run
   `ajiya decision accept <ID> --by "<name>"`. Do not accept a decision on your
   own.
3. Link the tickets that depend on it: `ajiya ticket add ... --decisions DEC-0001`
   or `ajiya ticket edit <ID> --decisions DEC-0001`.
4. `ajiya ticket show <ID>` lists the decisions a ticket links, with their
   status. Read only those. `ajiya decision show <ID>` also lists the tickets
   that link a decision. `ajiya decision list [--tag tech:<slug>]` lists them all.

A decision has one of three statuses: `Proposed`, `Accepted`, or
`Superseded by DEC-xxxx (CHG-xxxx)`. A superseded decision stays on file. An open
ticket that still links one gets a warning from `ajiya check`.

## When the plan must change

Sometimes a choice is replaced after work is done: the project moves from AWS RDS
to Supabase. Do not edit or rewrite the tickets. Use a change request. It finds
the tickets that might be affected, you judge each one, and a person approves.
Nothing is deleted: replaced work stays on file, linked to what replaces it.

**Agents never run `ajiya change approve` or `ajiya change reject`.** Only a
person does, because one command changes many tickets. The MCP tools do not
offer them.

Follow these steps in order.

### 1. Open the change

The plan in `ajiya/` must be committed first.

```
ajiya change open "Move the database from AWS RDS to Supabase" \
  --replaces tech:aws-rds --with tech:supabase --decision DEC-0001
```

Give at least one of `--replaces` (tags that go away), `--decision` (decisions
the change would supersede) or `--paths` (file globs the change touches).
`--with` names the new tags. The command makes the snapshot `before-CHG-0001` and
writes `ajiya/changes/CHG-0001.md`. Commit that file with `Ajiya: chore`.

### 2. Find the candidates

```
ajiya change impact CHG-0001
```

This lists the tickets that might be affected, and why. It never judges. The
reasons are:

| Reason | Meaning |
|---|---|
| `tag` | The ticket has a replaced tag |
| `decision` | The ticket links a decision the change names |
| `path` | A `path` tag, or a file in the ticket's commits, matches a glob from `--paths` |
| `downstream` | The ticket depends directly on another candidate |

Tickets with no tags are still found, through decisions, dependencies and the
files in their commits.

### 3. Judge every candidate

For each candidate, read it with `ajiya ticket show <ID>` and record a verdict:

```
ajiya change assess CHG-0001 AJ-0107 --verdict replace \
  --reason "Uses the RDS backup API directly" \
  --title "Configure Supabase backups" --done-when "Daily backup, 7-day retention"
```

Every verdict needs `--reason`. The verdicts are:

| Verdict | Meaning | When a person approves |
|---|---|---|
| `unaffected` | Nothing to do | Nothing happens |
| `modify` | Still needed, but changes | Open ticket: edited in place. Done ticket: a follow-up ticket is added that depends on it |
| `replace` | A new ticket takes its place | New ticket added; the old one is superseded by it |
| `invalidate` | Not needed at all | Superseded, with no replacement |
| `investigate` | You cannot tell yet | Blocks approval until you change it |

`--title` and `--done-when` describe the new ticket:

- `replace` needs both.
- `modify` of a done ticket needs both (they describe the follow-up).
- `modify` of an open ticket needs at least one (they edit the ticket).
- The other verdicts take neither.

Assess a ticket again to change its verdict. To change tags, use
`ajiya ticket edit`; `assess` does not edit them.

### 4. Reach deeper tickets

`impact` goes one level of `downstream`. When you give `modify` or `replace`,
run `ajiya change impact CHG-0001` again. It adds the tickets that depend on
that ticket, and keeps the verdicts you have given. Repeat until `impact` shows
no candidate without a verdict.

### 5. Show the person, commit, then wait

```
ajiya change show CHG-0001
```

Show the person the candidate table and every verdict with its reason.
Commit the change file with `Ajiya: chore`. `approve` refuses while `ajiya/` has
uncommitted changes.

Then stop. Tell the person to run one of these:

```
ajiya change approve CHG-0001 --by "<name>" [--new-decision DEC-0002]
ajiya change reject CHG-0001 --by "<name>" --reason "<why>"
```

Do not run them, and do not look for another way to apply the verdicts.
`approve` is refused while a candidate has no verdict or has `investigate`. It
then applies everything in one write: new tickets, edits, superseded tickets,
moved dependencies and superseded decisions. `--new-decision` names the decision
that replaces the old one. `reject` closes the change and leaves the plan alone.

`ajiya change list` shows all changes. A change that stays open for more than 14
days gets a warning from `ajiya check`.

### The Superseded status

After a change is approved, replaced work shows as:

```
🟩 Superseded by AJ-0201 · CHG-0014 · was: Done · 4f2a91c
```

It keeps its old status and evidence after `was:`. A done ticket stays recorded
as done. Superseded tickets do not count in progress totals or milestones, and
tickets that depended on one now depend on its replacement. Only an approved
change sets this status: there is no command for it.

### Snapshots

A snapshot is a git tag, `ajiya/plan/<name>`, on the committed plan.
`change open` makes one for you. To mark a point yourself:
`ajiya snapshot <name>` (refused with uncommitted changes in `ajiya/`), and
`ajiya snapshot list` to see them.

## Commands

| Command | Use it to |
|---|---|
| `ajiya status [--brief]` | See where the project stands at the start of a session |
| `ajiya next [--app <app>] [--milestone <name>]` | See what can start now |
| `ajiya ticket show <ID>` | See a ticket, what it waits on, what waits on it, and its commits |
| `ajiya ticket start <ID>` | Start a ticket |
| `ajiya ticket done <ID> --test` | Close a ticket with evidence |
| `ajiya ticket block <ID> "<reason>"` | Park a ticket that waits on something outside the plan |
| `ajiya ticket list [--phase <slug>] [--app <app>] [--state <state>] [--milestone <name>]` | List tickets in plan order (`--state` takes pending, in_progress, done, dropped, ready or human) |
| `ajiya ticket add ...` | Add work found on the way |
| `ajiya ticket edit <ID> ...` | Correct a ticket |
| `ajiya ticket files <ID>` | List the files changed by the commits that name a ticket |
| `ajiya tags` | List every tag in use, before you add one |
| `ajiya decision add "<title>" --decision "<text>"` | Record a choice the plan rests on (Proposed) |
| `ajiya decision accept <ID> --by "<name>"` | Accept a decision, when a person has decided |
| `ajiya decision show <ID>` / `ajiya decision list` | Read decisions |
| `ajiya snapshot <name>` / `ajiya snapshot list` | Tag the committed plan; list the tags |
| `ajiya change open "<title>" --replaces <tags>` | Start a change request |
| `ajiya change impact <CHG>` | List the tickets a change might affect |
| `ajiya change assess <CHG> <ID> --verdict <v> --reason "<why>"` | Judge one candidate |
| `ajiya change show <CHG>` / `ajiya change list` | Read changes |
| `ajiya change approve <CHG> --by "<name>"` | A person only: apply the verdicts |
| `ajiya change reject <CHG> --by "<name>" --reason "<why>"` | A person only: close the change |
| `ajiya milestone list` / `ajiya milestone show <name>` | See progress to each gate, and what blocks it |
| `ajiya launch show` | See what launch still needs, when the project uses a single launch target |
| `ajiya phase list` | See each phase's progress |
| `ajiya check` | Find problems in the plan and fix them |
| `ajiya build` | Write `ajiya/PROGRESS.md` and the dashboard data |
| `ajiya serve` | Open the dashboard on 127.0.0.1; it follows changes |
| `ajiya changelog [<range>]` | List the work in a range of commits, by ticket |

Every read command takes `--json`.

## MCP tools instead of shell commands

If your client has Ajiya's MCP tools (`ajiya mcp` runs the server), use them
instead of shell commands: `ajiya_status`, `ajiya_next`, `ajiya_ticket_start`
and so on. Each tool runs the same command with the same rules, and returns the
same JSON as `--json`. The tools cover reading the plan, adding and changing
tickets, phases, apps and milestones, and `ajiya_ticket_done`; it still refuses
without a commit that names the ticket, and the server never commits for you.
Decisions, snapshots and change requests up to `ajiya_change_assess` have tools
too. `change approve` and `change reject` do not: they are for a person only.
Dropping a ticket, removing an app or milestone, moving a milestone and
installing hooks are left out on purpose: a person decides those. So is
registering the server with an agent (`ajiya mcp install`, `ajiya mcp status`).
