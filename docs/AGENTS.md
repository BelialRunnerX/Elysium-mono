# Working on Elysium alongside other people

Read this before you touch anything. It is short because most of what it says
is checked by a tool, and the tools are named as we go.

## What this repository is

One hundred and thirty-odd mods are being replaced by twenty-nine modules of
our own. `docs/MANIFEST.md` is the ledger: one row per jar in the pack, with
the module that will absorb it and a note saying what was actually built. It is
the first thing a session with no memory should read, and the last thing a unit
of work should update.

Each module is a NeoForge mod in its own directory, and each has the same four
parts:

- `src/main/java/.../rules/` — the arithmetic, in plain Java, touching nothing
  from the game. This is where the decisions live.
- `tools/*Harness.java` — a `main` that runs that arithmetic a few hundred
  thousand times and exits non-zero when it disagrees with itself.
- `tools/gen_*.py` — writes every sprite, model, recipe, loot table and
  language key. Nothing under `src/main/resources` is kept by hand.
- `validate.py` — compiles and runs the harnesses, then checks that what is in
  the game matches what the rules say.

`./compile.sh` type-checks every module against NeoForge; `./compile.sh --jars
--bundle` builds them. Root `validate.py` runs all twenty-nine module
validators and sixteen cross-cutting checks.

## Your brief

`docs/prompts/` has one file per unit of work — what it contains, what order to
do it in, and the specific thing that will bite you. Read yours before the rest
of this document makes sense as instructions rather than as rules.

## Taking a piece of work

    python3 tools/board.py list
    python3 tools/board.py claim elysium-atelier --as yourname

The claim is a file created with `O_EXCL`, so if two of you claim the same unit
at the same moment the operating system decides and the loser is told. A unit
is one module and every jar still to be absorbed into it.

While you hold a unit you may edit that module's directory, add a new file
under `tools/negtest/`, and change your own rows in `docs/MANIFEST.md`.
Everything else — `elysium-lib`, the root validators, `compile.sh`, other
people's modules — needs the `shared` unit, which only one person holds at a
time. Before handing work back:

    python3 tools/ownership.py --as yourname

It asks git what you have changed and tells you what is outside what you hold.
The thing that trips this is never somebody rewriting another module on
purpose; it is a generator or a `sed -i` reaching one directory further than it
meant to.

When the module has nothing left to absorb:

    python3 tools/board.py done elysium-atelier --as yourname

which refuses if any of that module's jars are still un-`DONE` in the manifest.

## What finished means

All four of these, every time. A feature that is done except for the validator
is not done.

    ./compile.sh                       every module still type-checks
    python3 validate.py                all 29 module validators and the root checks,
                                       check_obtainable.py among them: what you added
                                       is reachable from a new world in survival
    python3 check_rules.py             no new entry on the rule-hygiene ratchet
    bash tools/negtest/negtest_<yours>.sh   every check you wrote proved effective

And when the change is behaviour a player meets, a live scenario in
`tools/livetest/scenarios.py` that asks the running server (run the suite on a
quiet machine; see docs/CONVERSION.md §17), and `/elysium audit harvest` clean
if you added a block.

Then build, install into both test instances (`bash tools/livetest/install.sh`:
the dedicated server `C:\testingbuilds\ElysiumServer` and the dev client
`C:\testingbuilds\Elysium`, both under `ELYSIUM_TEST_ROOT`; the E: drive that
held them was lost, and on 14 October 2026 the rigs were rebuilt on C:), and
update the manifest rows for the jars you absorbed with a sentence saying
what was actually built — not "done", but what a player would notice.

## The house rules

**One feature all the way through before the next.** Architect it, write the
pure rules, write the harness, write the game-side code, extend the generator,
extend the validator, write the negative tests, run the root validation, build.
Half of six features is nothing.

**A number a player can feel belongs in `rules/`, not in an item.** The
property worth proving is almost always about a whole set at once — that no
weapon of a tier is beaten on every axis by another, that every bee's material
is one something in the pack makes — and a number typed into a registration is
a number the arithmetic has never seen.

**Generate; never keep by hand.** If it is under `src/main/resources`, a
generator writes it, and the validator runs that generator into a temporary
directory and compares. A generator must write the same bytes every run:
`hash()` on a string is salted per process and will make every file look
edited.

**Walk the roster; never list it.** A loop over `RULES.EVERYTHING` plus a count
check at the end catches the thirty-sixth entry somebody forgot. A hand-written
list of thirty-five does not, and the symptom is one item that quietly does not
exist.

**Write a validator rule the way the ratchet reads it.** Name it as
`"<whole statement>" in <body>`, or in one of the other forms `check_rules.py`
lists in its docstring, so the ratchet can see it; a rule spelled some cleverer
way may be right and is still invisible to it, which is how half-statement
rules once read as zero.

## What has actually gone wrong here

Every one of these shipped at least once, and each is now checked:

A check guarded by the very thing it tests passes by never running. A rule that
compared a weapon's damage to *its own* damage could not report anything and
sat in the file looking like a check for weeks. A validator rule that names
half a statement survives the edit it was written to catch — hence
`check_rules.py`, which is a ratchet: the rules still grandfathered are all in
elysium-lib, and no new one may be added. A domination rule with no categorical axis demands
that a bow and a pickaxe be compared, so it either reports everything or is
turned off; give it an axis and then give it **positive controls**, or it will
one day quietly stop being able to say yes.

A registration guard checked file-wide rather than per method passes because
the statement exists *somewhere*. A generated file deleted by hand goes into
the next snapshot and comes back as a test failure rather than as the mistake
it was. Several generators writing one `en_us.json` clobber each other unless
each reads the committed file as its base and the validator runs all of them in
order into one directory.

## Negative tests

For each check you write, break the thing it checks — one way at a time — and
prove it reports. `tools/negtest/` has two hundred and twelve examples of the
shape. `python3 tools/dryrun.py <script> "<ok phrase>"` builds a variant that
writes nothing, so you can prove every anchor still matches the file before you
run the real thing; a stale anchor is a test that has silently stopped testing.

A script must restore the tree between cases, or every failure after the first
is a consequence of the first.

## Retiring a jar

Only a row already marked **DONE**, and only after three checks: nothing in the
pack declares it `type="required"`, no hard tag entry names its namespace, and
no recipe, loot table or advancement refers to it. Then **move** it to
`mods/_superseded/` — never delete. `docs/CONVERSION.md` §4b has the detail.
