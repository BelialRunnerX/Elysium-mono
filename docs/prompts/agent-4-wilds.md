# Agent 4 — Wilds — biomes, structures and the Vale

**Modules:** elysium-wilds

---

I'm continuing work on Elysium, a private Minecraft 1.21.1 / NeoForge modpack
conversion: about 130 mods being replaced by 29 modules of our own. It is for
my own use, not commercial and not public, so licensing is my problem to settle
with mod authors and not a reason to stop.

Several agents are working on this at the same time, each in their own modules.
Follow the order below exactly.

== 1. Get the source. It is not in this container. ==

The tree is on my machine at

    C:\Users\weaks\curseforge\Elysium-mono

Plain files, 18,349 of them, each verified against MIRROR-MANIFEST.json at the
top of that folder. Copy it into the container at /tmp/Elysium-mono and check
it arrived: `ls -d elysium-*` counts 29 and `ls tools/negtest/*.sh` counts 140.

== 2. Read docs/AGENTS.md before you touch anything. ==

It is about a hundred lines and it is the whole working agreement: how the
repository is laid out, what "finished" means, and the dozen mistakes this
project has actually made and now checks for. Do not skip it.

== 3. Claim your unit. ==

    python3 tools/board.py list
    python3 tools/board.py claim MODULE --as AGENTNAME

The claim is a file created with O_EXCL, so two agents cannot hold one unit.
While you hold it you may edit that module's directory, add a new file under
tools/negtest/, and change your own rows in docs/MANIFEST.md and
docs/SPLITS.md. Nothing else. Before you hand anything back:

    python3 tools/ownership.py --as AGENTNAME

It asks git what you changed and names anything outside what you hold.

== 4. How a module is built here ==

Every module has the same four parts, and the order they are written in is not
negotiable:

  src/main/java/.../rules/   pure Java arithmetic. No net.minecraft, no
                             net.neoforged, no com.mojang. This is where every
                             number a player can feel lives.
  tools/*Harness.java        a main() that runs that arithmetic hundreds of
                             thousands of times and exits non-zero when it
                             disagrees with itself.
  tools/gen_*.py             writes every sprite, model, blockstate, recipe,
                             loot table and language key. Nothing under
                             src/main/resources is kept by hand.
  validate.py                compiles and runs the harnesses, then checks that
                             what is in the game matches what the rules say.

Copy the shape from a finished module. elysium-arsenal is the newest and most
complete: five harnesses, a validator with named sections, and three negative-
test scripts. elysium-agronomy is the largest.

The property worth proving is almost always about a whole set at once — that no
weapon of a tier is beaten on every axis by another, that every bee's material
is something the pack actually makes — not about one entry. A number typed into
a registration is a number the arithmetic has never seen.

Walk the roster; never list it. A loop over RULES.EVERYTHING plus a count check
at the end catches the thirty-sixth entry somebody forgot. A hand-written list
of thirty-five does not, and the symptom is one item that quietly does not exist.

A domination rule ("nothing may be at least as good on every axis and better on
one") needs a categorical axis or it will demand that a bow and a pickaxe be
compared. And it needs positive controls — something built to be caught — or it
will one day quietly lose the ability to say yes and go on passing.

== 5. Negative tests ==

For every check you write, break the thing it checks, one way at a time, and
prove it reports. tools/negtest/ has 140 examples of the shape. Before running
one for real:

    python3 tools/dryrun.py tools/negtest/negtest_YOURS.sh "OK PHRASE"

That builds a variant which writes nothing, so you can prove every anchor still
matches the file. A stale anchor is a test that has silently stopped testing.
Your script must restore the tree between cases, or every failure after the
first is a consequence of the first.

== 6. Finished means all five, every time ==

    ./compile.sh
    python3 validate.py
    python3 check_rules.py
    bash tools/negtest/negtest_YOURS.sh
    python3 tools/ownership.py --as AGENTNAME

A feature that is done except for the validator is not done.

== 7. Ship ==

    ./compile.sh --jars --bundle

Send me dist/elysium-all-1.0.0.jar and dist/elysiumcolonies-1.0.0.jar, and
write both into Instances/Claude/mods and Instances/Claude-dev-build/mods on my
machine. Update your rows in docs/MANIFEST.md with a sentence saying what was
actually built — not "done", but what a player would notice. Then:

    python3 tools/board.py done MODULE --as AGENTNAME

== 8. Standing preferences ==

One feature all the way through before the next: architected, ruled, harnessed,
generated, validated, negative-tested, built. Half of six features is nothing.

Uncap everything, and scale percentile effects on stat/(stat+b). The player
should scale hard.

Do not change the GUI. There is no retexture generator. Never write anything
under Instances/"V+ and Tech"* — that is my working copy. Retire a jar by
moving it to mods/_superseded/, never by deleting it, and only after the three
checks in docs/CONVERSION.md section 4b.

If you get blocked on something another agent owns, say so and stop rather than
reaching into their module.

== Your unit ==

    python3 tools/board.py claim elysium-wilds --as AGENTNAME

4 units, content weight 8,825.

    6,557  [Neoforge]ctov-3.6.3.jar          (structures, mostly NBT)
    2,268  Quark-4.1-482.jar
        0  wwoo-neoforge-2.6.7.jar
        -   "Valoria: the Vale"  (1 dimension, 15 biomes, 22 structures)

== Read this before you decide anything ==

elysium-wilds already works. It has a multi-noise approach with 4 worlds and 10
biomes, and there is a root check — check_worldgen.py — that proves every biome
actually generates somewhere, reports the rarest as a share of its world and
the furthest in blocks, and fails the build when a biome can never appear. Read
elysium-wilds/validate.py's worldgen section and that root check first. You are
extending a working system, not choosing one from scratch.

The decision that is still open is structures. ctov is 6,557 of your 8,825 and
it is almost entirely NBT templates. The options are: ship the templates as
data, generate them from a room grammar, or hand-place a much smaller set well.
Write which you chose and what it costs into docs/CONVERSION.md before coding.

== What to prove ==

- Every biome generates somewhere. A biome that never appears is the single
  most common worldgen bug and it is invisible: the world looks fine.
- The rarest biome is rare and not absent. check_worldgen.py already reports
  the share; keep a floor on it.
- Every structure has a placement that can succeed — a structure whose
  conditions can never all hold at once is a folder of files and nothing else.
- No two biomes are the same biome under two names, and no biome is a strictly
  worse version of another to be in (same escape as everywhere else: give the
  comparison a categorical axis, and give it positive controls).

== The Vale ==

Valoria ships a whole dimension. Do the biomes and the dimension before the
structures; a structure in a dimension that does not generate is untestable.
The creatures that live there belong to another agent (elysium-bestiary) — do
not build them, and do not reach into that module. Coordinate through the
board.
