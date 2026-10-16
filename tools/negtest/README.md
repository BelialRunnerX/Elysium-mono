# The negative tests

213 scripts. Each one takes a validator that passes, breaks the thing it
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

`dryrun.py` neuters every mutation it recognises — `edit()`, every python
heredoc whatever it is tagged, `restore`, the generators, and every bare `rm`,
`mv`, `cp`, `sed -i` and `tar` — and then, since 2 October, runs the result
**inside a throwaway copy of the working tree**: a detached `git worktree` of
HEAD with every uncommitted change and untracked file carried over, built one
directory below `%TEMP%`, every `/tmp/Elysium-mono` in the script pointed at
it (tar's `-C /tmp Elysium-mono/...` spelling too), and removed afterwards
whatever happened. Any validator the dry script starts answers with the ok
phrase at once — a `sitecustomize` on `PYTHONPATH`, so the validator files
themselves are untouched and an anchor into `validate.py` still means
something. The dry run reports nothing but stale anchors, which is the only
thing worth knowing before a real run: an anchor that no longer matches means
the script is testing a line that has since been rewritten, and it will
silently test nothing.

A `Traceback`, a `FileNotFoundError` or a `No such file` in the dry output
counts as stale too, named by the case it came before, since 15 October: a
script whose every edit target was missing used to print one traceback per
edit and still end "no stale anchors" with status 0 (`negtest_fangs_effects.sh`,
48 of them). And a script whose `ROOT=`, or any absolute path naming an
`Elysium-*` tree, does not resolve into the copy after the rewrite is refused
with exit 6 before it runs, because `ROOT=/tmp/Elysium-int` is a dry run of
nothing, which reads exactly like a clean one.

After the script's output it says three more things: what the dry script
wrote *inside the copy* (the patterns missed those; the copy absorbed them),
whether the **real tree is byte-identical** before and after the script ran —
`git status`, the content of every modified file and every untracked file —
and what the copy cost. That is the proof: a dry run that ends with "the real
tree is unchanged" has, by construction, written nothing you keep. A tree
somebody else is editing meanwhile shows up there too, and the report says
whether the script even names the file that moved. Edits the patterns miss
still happen, in the copy, and are not undone between cases, so a later
anchor in the same file can fail as a consequence of an earlier edit — read
the write list before believing a second `STALE` in the same file.

`--in-place` is the old behaviour — write `negtest_<name>_dry.sh` beside the
script and stop, nothing protecting the tree — and is only safe for a script
whose dry run in the copy wrote nothing.

What the copy costs on this machine: 6–7 s to build on a quiet machine (44k
files; 20 s before parallel checkout), 11–19 s while another agent was
building copies alongside, and 7–25 s to remove; plus the dry script itself,
1–36 s depending on how many python processes it starts. The thirteen
scripts measured on 2 October took 30–57 s each, wall clock, where an
in-place dry run took seconds and a real validator run twenty minutes.

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

**The approach was the bug, and the worktree is the answer.** Every hole ever
found in this tool was something that was not at the start of a line: a
continuation, a second command after a semicolon, a helper that acted first,
a heredoc with an unexpected tag. Patching the next pattern bought a week.
Since 2 October the dry copy runs inside a throwaway `git worktree` that is
removed afterwards, so the whole family is closed by construction: a mutation
the patterns miss lands in a copy nobody keeps, and the tool proves it by
fingerprinting the real tree before and after. The pattern neutering stays
because it is what makes a dry run take seconds rather than minutes. The
first runs through the net showed what the patterns had been missing all
along — `negtest_court.sh` regenerates 24 textures through a `gen_village.py`
the `tools/gen_` pattern did not know, and the nine scripts of the
`keep "$1"; python3 -c` family make their edits for real — and none of it
reaches the tree. The nine `docs/handoff/STALE-ANCHORS.md` names can be
dry-run again.

A fifth hole, and the first found in a script written after `dryrun.py`
existed: a helper whose body is an inline `python3 -c` that writes. Not a
heredoc, not a bare shell mutation, and not called `edit`, so every pattern
walked past it — and since `restore()` is neutered for a dry run, what it
wrote stayed written. `negtest_names.sh`'s dry run put nine creature names
into a language file and took a word out of a data file, and the tree stayed
broken until the next real run refused to start. Helpers are now neutered by
what their body does rather than by what they are called, because the name is
the part that keeps changing. If you add a helper that writes, the dry run's
own report tells you whether the patterns knew about it — the list of what
the script wrote inside the copy. Teach `dryrun.py` the shape for speed; the
tree is safe either way.

Two more holes have been found the hard way, both by it letting a
write through: a bare `rm` in `negtest_atelier.sh` deleted a generated file
that then went into the next snapshot, so every restore came back broken; and
a heredoc tagged `PYZ` rather than `PYX` went straight through the neutering.
Both are closed. If you add a new kind of mutation to a script, check
`dryrun.py` knows about it.

A seventh hole (28 Sep): a heredoc with an argument between the dash and the
tag, `python3 - "$M/x.java" <<'PYX'`, walked past the heredoc pattern, which
wanted `python3 - <<`. Two cases in `negtest_bestiary.sh` that thin or resize
a shape class ran for real on every dry run; the diff of the tree showed it,
which is the check the paragraphs above keep asking for. Closed in `dryrun.py`.

Some scripts snapshot more than their module: `negtest_wilds.sh` keeps a copy
of the root `validate.py` (its `V=`) and puts it back at the end. An edit made
to the root validator while that script runs is undone when it finishes, and
nothing says so: a cross-check wired in during a wilds run vanished this way
(28 Sep). Do not edit anything a running script snapshots; read its `snap`
and `V=`/`M=` lines first.

A miss that is not a stale anchor: `edit()` replaces the **first** occurrence
only, and a rule that looks for a phrase anywhere in a file is not broken by
taking one of four copies of it out. `negtest_bees.sh`'s lockstep case edited
one wing of four and reported MISSED for a rule that worked. When the phrase
a rule wants recurs, break every copy (an inline python `replace` with no
count) and say so in the case's comment.

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
