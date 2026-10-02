# Hand-off — 27 September 2026

Left here by the agent that held `elysium-arcana`, `elysium-bestiary`,
`elysium-comforts`, `elysium-almanac` (briefly) and `shared`. All five are
released; `python3 tools/board.py list` shows 58 jars across 18 units and
nothing held.

Everything in this folder is for **whoever picks the project up next** — the
other agents working in their own modules, or a person deciding what to do
about the findings.

## What is here

| file | what it is | who needs it |
|---|---|---|
| `WHAT-CHANGED.md` | the naming work, and the three things every other module should check in itself | every agent |
| `STALE-ANCHORS.md` | 24 negative-test scripts whose anchors no longer match the file — 52 anchors across 13 modules — with the owning module and the command that fixes each | the agent holding each named module |
| `DRYRUN.md` | the state of `tools/dryrun.py`: six holes closed on 27 Sep by pattern, the whole family closed on 2 Oct by running every dry run in a throwaway worktree | whoever wants the tooling to stop leaking |
| `elysium-playtest.pdf` | eight-page playtest guide for the build in both instances | Dylan |

## What is not here, because it is in the tree

Documentation belongs beside the thing it documents, so these were written
where they will be found rather than copied in here:

- `docs/NAMING.md` — the rule, the scheme, and what the rename itself got
  wrong. **Read this before renaming anything in your own module.**
- `docs/CONVERSION.md` §15 — lighting a room, and the general rule about a
  decoration roster that touches a resource another module has built on.
- `tools/negtest/README.md` — five holes found in `dryrun.py`, three of them
  this week, and what shape of script defeats it.
- `check_names.py` — the check. Run it; it takes a second.

## The one-line version

A principle stated in a document is not a principle, it is a hope. Three of
the four promises in `docs/CONVERSION.md` §1 were enforced by something that
fails a build. The fourth was prose, and it had been quietly false for months.
Go and look at what your own module claims in prose and nothing executes.
