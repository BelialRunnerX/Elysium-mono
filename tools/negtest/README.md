# The negative tests

198 scripts. Each one takes a validator that passes, breaks the thing it
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

A dry run that will not parse prints nothing, and nothing is exactly what a
clean dry run prints. `dryrun.py` now runs `bash -n` over what it wrote and
refuses to hand it over if it is not a script, because three scripts —
`negtest_gates.sh`, `negtest_icons.sh` and `negtest_uncap.sh` — had a dry run
that died on a syntax error for as long as they had existed, and every author
who ran one read the silence as a pass. Two stale anchors reached a real run
that way. The three shapes that broke it were a command split over several
lines with a trailing backslash, a multi-line `restore()` with a brace group
inside it, and a function whose last line was its cleanup.

Three more were found on 27 September while sweeping the whole corpus, all of
the same family and all worth knowing before you trust a dry run:

- **A helper that does something before its inline python.**
  `negtest_weather.sh` writes `edit() { keep "$1"; python3 -c "` — the `keep`
  snapshots the file it is about to break. Both rules below required
  `python3 -c "` to be the first thing inside the brace, so neither matched,
  the real `edit()` survived into the dry script, and a dry run rewrote 47
  files in `elysium-lib`.
- **A bare multi-line `python3 -c "..."` at the top level.** Not a heredoc,
  not inside a function. `negtest_freight.sh` opens a language file, pops a
  key and writes it back that way.
- **A mutation that is not the first thing on its line.**
  `keep $S/x; mv $S/x /tmp/bak` slips past a pattern anchored at the start of
  a line on the strength of the `keep`.

**The approach is the bug.** Every hole ever found in this tool has been
something that was not at the start of a line: a continuation, a second
command after a semicolon, a helper that acted first, a heredoc with an
unexpected tag. Patching the next pattern buys a week. Parsing the script, or
running the dry copy inside a throwaway `git worktree` that is thrown away
afterwards, would end the whole family at once — and the second of those is an
afternoon's work. **One shape is still open**: the first of the three above is
fixed, but nine scripts written in that family still leak on a dry run.
`docs/handoff/STALE-ANCHORS.md` names them.

A fifth hole, and the first found in a script written after `dryrun.py`
existed: a helper whose body is an inline `python3 -c` that writes. Not a
heredoc, not a bare shell mutation, and not called `edit`, so every pattern
walked past it — and since `restore()` is neutered for a dry run, what it
wrote stayed written. `negtest_names.sh`'s dry run put nine creature names
into a language file and took a word out of a data file, and the tree stayed
broken until the next real run refused to start. Helpers are now neutered by
what their body does rather than by what they are called, because the name is
the part that keeps changing. If you add a helper that writes, check
`dryrun.py` knows about it, and check by running the dry variant and diffing
the tree rather than by reading the patterns.

Two more holes have been found the hard way, both by it letting a
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
