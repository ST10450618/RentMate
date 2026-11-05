<!-- ajiya:start -->
## Work tracking (Ajiya)

This repository tracks work with Ajiya. The plan is in `ajiya/`; never edit it by
hand.

1. Before a task: `ajiya next`, then `ajiya ticket start <ID>`.
2. Commit with a last paragraph `Ajiya: <ID>` (1 to 3 IDs, or `Ajiya: chore`).
   Other trailers may share that paragraph, for example
   `Ajiya: AJ-0001` then `Co-Authored-By: Name <name@example.com>`.
3. Finish with `ajiya ticket done <ID> --test`, then `ajiya check`.
4. Found extra work? `ajiya ticket add`, do not widen the current ticket.

| Command | Purpose |
|---|---|
| `ajiya next` | Tickets that can start now |
| `ajiya ticket show <ID>` | A ticket, its dependencies and commits |
| `ajiya check` | Problems in the plan |

Full guides: `.ajiya/guide/daily.md` and `.ajiya/guide/setup.md`.
<!-- ajiya:end -->
