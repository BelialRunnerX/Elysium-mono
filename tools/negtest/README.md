# The negative tests

160 scripts. Each one takes a validator that passes, breaks the thing it
checks — one way at a time — and proves the validator says so.

They are here because they were not, for most of this project's life. They
lived in `/tmp` in an ephemeral container alongside the tree, and the tree was
archived without them. A protocol whose evidence does not survive the session
is a protocol nobody can audit afterwards, so the corpus moved into the
repository on 24 September and travels with the source from now on.

## Running one

    bash tools/negtest/negtest_comforts.sh

They assume the tree is at `/tmp/Elysium-mono` — that is the path they were
written against and the path `docs/TODO.md` §0 tells you to restore to. Each
script snapshots the files it is about to break with `tar`, breaks them,
re-runs the validator, checks the message, restores, and finishes by asserting
the tree is clean again. A script that ends `tree restored clean` has left
nothing behind. One that does not has, and you should `git status` before
doing anything else.

## Checking a script before running it

    python3 tools/dryrun.py tools/negtest/negtest_comforts.sh "all X checks passed"

`dryrun.py` writes a `_dry.sh` beside the script with every mutation neutered —
`edit()`, every python heredoc whatever it is tagged, `restore`, the
generators, and every bare `rm`, `mv`, `cp`, `sed -i` and `tar`. The dry run
reports nothing but stale anchors, which is the only thing worth knowing
before a real run: an anchor that no longer matches means the script is
testing a line that has since been rewritten, and it will silently test
nothing.

Two holes in `dryrun.py` have been found the hard way, both by it letting a
write through: a bare `rm` in `negtest_atelier.sh` deleted a generated file
that then went into the next snapshot, so every restore came back broken; and
a heredoc tagged `PYZ` rather than `PYX` went straight through the neutering.
Both are closed. If you add a new kind of mutation to a script, check
`dryrun.py` knows about it.

## What is covered, honestly

28 of the 29 modules have at least one script. `elysium-dungeons` has none.

11 of the 16 cross-cutting checks have one. `check_gear.py`,
`check_lifecycle.py`, `check_loadtest.py`, `check_manifest.py` and
`check_retexture.py` have never been deliberately broken, which means nobody
has proved they can report anything at all. `check_validators.py` proves they
are *structurally* able to — they have a report block and it is last — but
that is a weaker claim than having watched one fail.

Those six gaps are listed in `docs/TODO.md` §4 as well. Close them as you next
touch the thing concerned.
