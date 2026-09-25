# Agent 5 — Agronomy — bees, crops, fishing and cooking

**Modules:** elysium-agronomy

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

    python3 tools/board.py claim elysium-agronomy --as AGENTNAME

8 units, content weight 6,030.

    2,490  productivebees-1.21.1-13.13.5.jar
    1,180  MysticalAgriculture-1.21.1-8.0.27.jar
      768  tide-neoforge-1.21.1-2.1.1.jar
      700  botanypots-neoforge-1.21.1-21.1.44.jar
      458  FarmersDelight-1.21.1-1.3.2.jar
      282  Aquaculture-1.21.1-2.7.21.jar
      152  MysticalAgradditions-1.21.1-8.0.14.jar
        -   "(new) space harvestcraft"  (no jar: crops and trees for the
           frontier worlds, a design job first)

== Most of these systems already exist. Read before you write. ==

This module is further along than its jar count suggests, and your job is
mostly extending rosters that already have harnesses watching them:

  BeeRules.java      75 bees, tier *derived* from parents rather than declared,
                     with checks for orphans, breeding cycles, unreachable
                     species, two bees making the same thing, one pair of
                     parents breeding two different bees, inbreeding, and a
                     hive that out-earns a mine. productivebees is an extension
                     of this, not a new system.
  SeedRules.java     30 seeds and the mined-only rule: you may not grow what is
                     only made. MysticalAgriculture is an extension of this.
  FishRules.java     105 fish in 11 families, with a fishing table that
                     responds to rod, luck and bait, and checks that no fish is
                     uncatchable, no water is dead, and luck never makes the
                     rare fish rarer. tide and Aquaculture extend this.
  KitchenRules.java  29 meals, 4 stations, and a domination rule with effect as
                     its categorical axis. FarmersDelight extends this.
  PotRules.java      165 pots and 14 soils. botanypots extends this.

The good news: add an entry that breaks an invariant and the existing harness
tells you immediately, by name. The bad news: those invariants are real and
some of the mods you are absorbing do not respect them. When productive bees
wants a bee for a material nothing in the pack makes, that is the check
working, not the check being wrong.

== What is genuinely new ==

"(new) space harvestcraft" has no jar. It is crops and trees for the frontier
worlds in elysium-space, and it is a design job before it is a coding job.
Write the design into docs/ first. elysium-space is another agent's module —
read it, do not edit it.
