# The negative tests

214 scripts. Each one takes a validator that passes, breaks the thing it
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

## Running many at once

    python3 tools/negtest/parallel.py -j 4 negtest_quick_move.sh negtest_spawners.sh ...
    python3 tools/negtest/parallel.py -j 4 --list names.txt     # one name per line
    python3 tools/negtest/parallel.py --all --plan              # rewrite all, run nothing
    python3 tools/negtest/parallel.py --resume <logs>/summary.json   # run again what was not ok

One at a time, in the live tree, the corpus takes more than a day: every
script works on `/tmp/Elysium-mono` itself and keeps its scratch at fixed
names (`/tmp/h.bak`, `/tmp/<module>_snap.tar`, lock dirs), so two at once
would spoil each other. `parallel.py` gives each of `-j` workers a world of its
own under `C:/Users/weaks/Elysium-env/negwork/w<N>`: a detached git worktree of
the base commit (`tree`), its own `/tmp` (`tmp`, emptied before every script),
its own build outputs (`classes`, `loadtest`), and a junction
`%TEMP%/elysium-negw<N>` to all of it. Each script is rewritten into the
worker's scratch first. Every spelling of the tree becomes the worktree, tar's
`-C /tmp Elysium-mono/x` becomes `-C /tmp/elysium-negw<N> tree/x`, every other
`/tmp/<name>` becomes the scratch, `/tmp/mc`, `mcwin`, `mods` and `ref` are left
alone, and `"$0"` becomes the script's place in the worktree. It runs with
`ELYSIUM_ROOT`, `ELYSIUM_MONO`, `ELYSIUM_OUT`, `TMP`, `TEMP` and `TMPDIR`
pointed at the worker and the validator exports from `tools/setup_env.py`. The
paths stay under `/tmp` on purpose: Git Bash, Windows Python and tar all
resolve them, where tar reads `C:/x.tar` as a file on a host called C. Python
files that hard-code a scratch path (`check_loadtest.py`'s build folder, which
the root validator runs) are rewritten by the same rules as they load, through
a `sitecustomize` on the worker's `PYTHONPATH`, so nothing tracked is edited.
`--plan` prints what every script would be rewritten to, its gitignored
inputs, and refuses any that would still name the tree, another worker, a temp
path in a spelling it does not know, or a product file nobody has built.

The workers take the lowest numbers whose `%TEMP%/elysium-negw<N>` junction is
free or already theirs. A junction that leads into another run's folder is
skipped and never taken down, so a second run can go alongside a first one
with a `--negwork` of its own (`Elysium-env/negwork-<name>`); one negwork holds
one run at a time (`parallel.lock`). A finished `--compile` cache in the
default negwork is read by the others too.

What it guarantees, and checks rather than assumes:

- **The paths a running script names stay as they were in the live tree.**
  Every script's names are worked out before it runs: its words, with each
  `VAR=value` it assigns expanded, that start with a top-level entry of the
  tree or follow `Elysium-mono/`. A module named bare (a `cd` into it, an
  `M=elysium-agronomy`) stands for the whole module. After every script, under
  one lock, the live tree is compared with the start: `git status`, and the
  bytes of every file it lists or listed. A file that changed under a path
  that script names, or that a script still running names, is a leak or
  somebody editing what it tests. Every script is killed, the ones that name
  the file are marked `leaked`, and the run stops with exit 3. A change
  anywhere else is somebody else's work: it is noted, in the log and in
  `summary.json`, and the run carries on. A commit or a `git add` changes
  status but no file, and is noted too. Nothing a script names may appear in
  the real `%TEMP%` or in another worker's scratch either (`leaked`, exit 3).
  `--live-ignore GLOB` exempts a path outright.
- **A gitignored input is the live tree's, frozen.** `dist/` is not in git, so
  no worktree has `dist/elysium-all-1.0.0.jar`, which `negtest_bundle.sh`
  reads. A gitignored file a script names that the live tree has is copied
  once at the start, recorded by its sha256 (in the log and in
  `summary.json`), put into the worktree before that script and taken out
  after it. A script that changes it is `dirty`; the live copy changing while
  the script runs is a leak. A file under `dist/` or `repo/` that the live
  tree lacks as well fails the script before it runs, with the reason, rather
  than letting it test nothing.
- **The output decides, not only the exit status.** A `SETUP FAILED`,
  `STALE ANCHOR` or `GENERATOR BROKE` line makes the script an `error`
  whatever it exits with, and so does a setup's own Python dying: an
  `anchor not found`, a `pattern missing`, an `AssertionError`, or a
  traceback from a heredoc (`File "<stdin>"`) or a `python3 -c`
  (`File "<string>"`). So does printing no caught line at all (no `caught`,
  `CAUGHT` or `ok :` case and no summary with anything caught). A `MISSED` or
  `MISS` line anywhere is a miss. `RESTORE FAILED`, `TREE LEFT BROKEN` and
  `TREE DIRTY` are `restore-failed`. Only after all of that does a non-zero
  exit make an `error`. The scripts hold up their end too since 16 October:
  one that prints any of those three counts it as a miss and exits non-zero.
  Before that, 29 of them returned from a `SETUP FAILED` with nothing counted
  and ended on an `echo`, so a script whose every case failed to set up
  exited 0; `negtest_grids.sh` counted its `STALE ANCHOR` and still exited 0;
  and four ran a python heredoc that printed `STALE ANCHOR` without the shell
  looking at its status. A second review the same day found 72 more whose
  edit helper reported a stale anchor only as the `AssertionError` above,
  which nothing read: an expect case after it usually missed, but a
  must-not-flag case passed, and so did a case whose phrase the tree already
  printed. Each of those helpers now ends `|| { echo "STALE ANCHOR: ..."; fail=...; }`
  (73 helpers; the `t()` heredocs of the p-style scripts also put the file back
  and skip the case), and `negtest_datapack.sh` and `negtest_playtest.sh` say
  `SETUP FAILED` when a heredoc setup dies.
- **A check killed by the script's own timeout is a check that never
  finished.** The scripts run their validators as
  `out=$(timeout 1800 python3 validate.py 2>&1)`, which throws a 124 away, so
  a must-not-flag case ("nothing says brass_shaft") passed off a
  `check_models.py` that was killed before it printed a byte. Every worker's
  bash sources a `BASH_ENV` whose `timeout()` writes each kill down
  (`events.log`, proved by a self-test when the worker is built), and a
  Python check killed that way makes the script an `error`. Anything else
  killed by its timeout (the java harness in `negtest_wilds_portal.sh`, whose
  flood is meant to run out of time) is noted and decides nothing. The two
  scripts with must-not-flag cases that read only the absence of a phrase,
  `negtest_machines2.sh`'s `reject()` and `negtest_playtest.sh`'s shaft case,
  now also want the validator's own pass line and exit 0, as
  `negtest_datapack.sh`, `negtest_recursion.sh` and `negtest_buses.sh`
  already did, and count a kill, a crash or silence as a miss.
- **The base passes before anything runs.** A case is caught just as well
  off a break the base already carries. Before the first script, every
  module validator a queued script names is run once on the base, in the
  workers' worktrees (`base-check-<module>.log`), and a script that names a
  module whose validator fails there is `refused` with the validator's last
  lines. A pass is remembered in `negwork/base-checks.json` by base tree,
  module, validator jars and compiled classes, so a second run of the same
  base starts at once. The p-style scripts (`negtest_p1.sh`, `p2`, `p3`,
  `p3b`, `skies`, `mobs`, `s8`, `foundry_recovery`, `valoria_*`), which cd
  into one module and ended on an `echo`, now end by running that module's
  validator on the tree they leave (`RESTORE FAILED` if it does not pass) and
  exit non-zero on any miss.
- **Each script starts from the base.** The worktree is checked with
  `git status --porcelain` after every script; one that leaves it dirty is
  reported as `dirty` and the worktree is put back with `git checkout -- .`
  and `git clean -fdq`. A script that recompiles (`negtest_convergence.sh`)
  has the worker's classes restored after it.
- **The compiled classes are the base's.** `/tmp/elysium-classes` is copied
  only if what javac read there was the base's Java, by content: since 16
  October `compile.sh` writes `<module>.sha`, the git blob id of every file
  it hands javac, hashed just before javac and again just after and kept
  only when the two agree, and every id must be the base's. Otherwise the run
  is refused. Until then this was read off mtimes, and 122 scripts restore
  with `tar x`, which puts the archived mtime back with the bytes: a module
  compiled while a negtest had it broken, then restored, passed as a compile
  of the base. Classes from a `compile.sh` older than that have no `.sha` and
  are refused. `--compile` builds them with `compile.sh` in a worktree
  instead (two minutes on a quiet machine), kept in
  `negwork/classes-<digest of the base's Java and compile.sh>` for every later
  run whose Java is the same. `--trust-classes` skips the check, which is
  right only when comparing with a run that used those same classes.
- **The workers come down.** Worktrees, scratch, junctions and the frozen
  inputs are removed at the end, on a timeout, a failure, Ctrl-C or
  Ctrl-Break.

What it cannot see. The guard above has blind spots, and a clean run says
nothing about them:

- **Gitignored paths in the live tree.** `git status` does not list them, so a
  write that escapes into `dist/`, `build/`, `logs/`, `__pycache__` or any
  other ignored path of the live tree is not noticed. Only the ignored inputs
  a script names are hashed.
- **The real `%TEMP%`.** Git Bash's `/tmp` is the real `%TEMP%` whatever `TEMP`
  says (its mount is fixed for the session), and it is shared with every
  program on the machine. The guard looks only at the top-level names a
  script spells under `/tmp`. A write to a name made at run time (`mktemp`, a
  name joined from parts) or spelt only inside a validator is not seen.
- **A write that is put back before the guard looks.** The live tree is
  compared when a script ends. A script that leaks into the live tree and
  then restores its own snapshot, as every script does at its end, leaves the
  bytes as they were and nothing is reported, although the live tree was
  broken for whoever read it in the meantime. A change somebody else makes
  and undoes between two script ends is not seen either.
- **A path reached without being named.** A script's names are what it
  spells. A file it reaches only through a validator (the root `validate.py`
  reads every module) is not one of them, so a change there while it runs is
  noted as somebody else's, not stopped.
- **What the product was built from.** A gitignored input is frozen and
  hashed, but nothing ties `dist/elysium-all-1.0.0.jar` to the base: it is
  whatever the last `gradlew collectJars` or `compile.sh --jars` built, from
  whatever tree that was.
- **A break made and undone inside one javac.** `compile.sh` hashes its
  sources before javac and after it. A negtest that broke a file and put it
  back with `tar x` entirely while javac ran would leave both hashes the
  base's and the classes not.

`--base live` tests the working tree as it is, uncommitted and untracked files
included (a commit made through a private index, nobody's branch). It refuses
without `--force` when the live tree may be half way through a negtest run the
ordinary way, because a snapshot then could hold a module half broken: a
running bash whose command line names `tools/negtest` (other than a
`parallel.py` run or a `dryrun.py` working in a copy), or anything a
live-tree script keeps in `/tmp` (any `/tmp/<name>` it spells: a snapshot, a
backup, a lock dir, and `/tmp/negtest_*_snapshot.tgz`) beside modified tracked
files of a module that script names. A tar is asked what it holds; anything
else stands for every module the scripts that spell it name. Until 16 October
only names with `snap`, `.tar` or `.tgz` in them counted, and 79 of the 214
scripts keep their backups under other names (`/tmp/nb_p2`, `/tmp/sm.bak`,
`/tmp/_abs_backup.java`, `/tmp/gates_bak`, `/tmp/negtest_<x>.lockdir`), so a
tree snapshotted half way through `negtest_p2.sh` was taken as whole and p2
passed off its own leftover break. A script started as `bash negtest_x.sh`
from inside `tools/negtest` does not name the folder and is not seen; what it
keeps in `/tmp` is. Old backups left behind by a run that died trip this too:
look at the date it prints, and at the base check, which runs either way.

Timeouts: three times a script's last recorded time and never under 30
minutes (`negtest_agronomy_fishbees.sh` took 75 minutes at `-j 3`), four hours
for a script never timed (`--timeout-unknown MIN`), or `--timeout MIN` for all
of them. Default base `HEAD`, default `-j 4`.

`--resume SUMMARY` reads an earlier run's `summary.json` and does not run
again the scripts it found ok, as long as the base tree, the compiled classes,
the script's own text and the sha256 of every gitignored input it was given
are the same, and the run read its scripts by the same rules against the same
validator jars (`rules_digest`, a digest of the parser and status rules and
the timeout shim; `exports_digest`, the content of every file the validator
exports name, the jars a `cp.txt` lists included). A script whose input
changed runs again; a summary written by other rules or against other jars
carries nothing. Before 16 October only the first three were compared, so an
ok decided against an old `dist/` jar, or by a parser since fixed, was
carried as it was. Its base is
the default, a `--base live` snapshot included, so an aborted day-long run
picks up where it stopped: `parallel.py --resume <logs>/summary.json` runs the
rest of the list it had, or name the scripts to run. The new `summary.json`
holds the carried results too (`resumed_from`), so it can be resumed in its
turn. A script given as a path outside the tree (a throwaway copy) is read
from there and is never added to the timing hints.

Logs go to `Elysium-env/logs/negtests-<timestamp>/<script>.log` with
`summary.json` beside them (status, caught and missed, from the script's
summary line and its own `caught :` / `MISSED :` lines, the larger miss count
winning, exit, time, timeout, inputs, the ordered case lines, the base tree
and classes digest); timings go to `Elysium-env/logs/negtest-timings.json`
and the next run starts the longest first. Exit 0 only if every script caught
everything and left its tree clean; 1 a script missed, errored, timed out,
was refused or left its tree dirty; 2 the run was refused before it started;
3 a leak, and everything was stopped.

Proved on 15 October against three scripts run the ordinary way first: the
output of `negtest_quick_move.sh`, `negtest_harvest_audit.sh` and
`negtest_spawners.sh` (3, 7 and 84 caught) was byte-identical, case for case,
and the live tree was byte-identical before and after.

Proved again on 16 October, after a review found the gaps closed above (logs
in `Elysium-env/logs/negtest-proof-20261016`). `--all --plan --base live`
rewrote all 214 scripts and refused none. A real run at `-j 3` of
`negtest_bundle.sh` (its jar frozen from the live tree), `negtest_quick_move.sh`,
`negtest_harvest_audit.sh`, `negtest_set_bonus.sh`, `negtest_absorption.sh` and
`negtest_slots.sh` was ok throughout, 37 caught, with the live tree
byte-identical. Two throwaway copies of `negtest_absorption.sh` with a stale
anchor, kept outside the repository, were both errors: the fixed one exits 1,
the one from before the fix exits 0. A run stopped by a throwaway script that
writes into the real `/tmp` (`leaked`, exit 3, `negtest_harvest_audit.sh`
aborted) was resumed: the resume ran `negtest_harvest_audit.sh` alone and
carried the two scripts that were already ok.

Proved a third time on 16 October, after a second review, each fix with the
reviewer's own probe (logs in `Elysium-env/logs/negtest-fix-20261016`). The
stale-anchor copy of `negtest_machines2.sh`, helpers verbatim, is now an
`error` on its `AssertionError`; the same copy with the fixed helpers prints
`STALE ANCHOR` and exits 1. The shaft case cut to `timeout 1` is an `error`
(its own timeout killed `check_models.py`), and the fixed case is a miss as
well. A `--resume` across a changed gitignored input ran the script again,
which missed; one from a summary written by the old rules carried nothing.
`--base live` on a tree left the way `negtest_p2.sh` leaves it mid-case was
refused for `/tmp/nb_p2` beside the modified `iron.json`, and with `--force`
the base check failed `elysium-armory` and refused p2. The provenance probe is
refused at every step, and with `.sha` records written the way `compile.sh`
now writes them it accepts the clean compile and refuses the one made while
the file was broken, after the `tar` restore too. On a live snapshot at `-j 4`
the six scripts above were ok again (37 caught), the two stale absorption
copies were errors, and `negtest_p2.sh`, `negtest_datapack.sh` and
`negtest_playtest.sh` were ok with their new checks; a copy of p2 whose last
restore does not happen is `restore-failed`. The first playtest run found a
real stale anchor that had only ever shown as a MISS: its generator-drift case
edited `emerald_bastion_lake.json`, gone since 1360fd0e, and now edits
`oil_lake_space.json`. Still open: `negtest_machines2.sh` leaves 83
generated files changed (a case runs `gen_solarpunk.py` with a rule taken out
and puts back only the generator and the tag), at HEAD as well.

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
