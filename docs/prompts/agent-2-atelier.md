# Agent 2 — Atelier — the decorative blocks

**Modules:** elysium-atelier

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

    python3 tools/board.py claim elysium-atelier --as AGENTNAME

30 jars, content weight 33,028. That is 44 per cent of everything left in the
whole conversion, and you are the long pole. It is also, almost all of it,
bulk rather than design: the same block in many shapes and many materials.
Treat it as a generator problem and it is tractable. Treat it as 14,000
individual blocks and it is not.

== The jars, heaviest first ==

    13,979  chipped-neoforge-1.21.1-4.0.2.jar
     2,699  bf_blockpack-neoforge-1.21.1-1.0.11.jar
     2,263  cluttered-[NeoForge]-3.0.3-1.21.1.jar
     1,969  Design-n-Decor-1.21.1-2.2b.jar
     1,663  valhelsia_furniture-neoforge-1.21.1-1.1.1.jar
     1,469  mcw-furniture-3.4.1-mc1.21.1neoforge.jar
     1,363  mcw-roofs-2.3.2-mc1.21.1neoforge.jar
     1,124  DustyDecorations_1.21.1Neoforge_V1.13.jar
       791  supplementaries-1.21.1-3.8.8-neoforge.jar
       733  mcw-mcwwindows / 708 mcw-mcwpaths / 637 handcrafted
       570  mcw-doors / 504 mcw-mcwstairs / 417 mcw-trapdoors
       415  mcw-mcwfences / 354 mcw-bridges / 315 mcw-lights
       258  DnDesires / 243 woodworks / 181 beautify
       169  Clothing_Of_The_Lowlands / 89 bellsandwhistles / 54 amendments
        48  everycomp / 13 Chimes
         0  fusion, DiagonalWindows, DiagonalWalls, DiagonalFences

== Build it in this order ==

1. The shape-family engine, and nothing else, first. A family is one material
   crossed with a set of shapes: stair, slab, wall, fence, gate, door,
   trapdoor, pressure plate, button, and then this module's own — roof, bridge,
   path, light, window. Put the families and the shapes in rules/ as two lists
   and the crossing as arithmetic, so a family that claims a stair and has no
   slab is a build failure rather than a gap somebody notices in a year.

2. The mcw-* set (roofs, bridges, paths, lights, windows, doors, stairs,
   fences, trapdoors, furniture) is nine jars and about 6,700 weight, and it is
   exactly what the engine in step 1 is for. Ship it as one wave.

3. The block packs (bf_blockpack, cluttered, Design-n-Decor, DustyDecorations,
   valhelsia_furniture, handcrafted, woodworks, beautify, DnDesires) are more
   families for the same engine.

4. The multipliers last: chipped, fusion, everycomp and the three Diagonal*
   jars do not add blocks of their own — they take blocks that already exist
   and make variants. chipped alone is 13,979 of your 33,028 and it is one
   generator, driven by a list of base blocks and a list of variant styles.
   Write that generator once and it covers four jars.

Ship after every wave. Do not hold 30 jars back for one build.

== What to prove ==

- Every family has every shape it claims, and every shape has a blockstate, a
  model, an item model, a loot table and a name. Missing any one of those looks
  different in game and all of them look like a bug the player cannot report.
- No two families are the same material under two names.
- Every block is reachable: a recipe, or a drop from something that has one.
- The variant generator is deterministic. Run it twice into two temporary
  directories and compare bytes. Python's hash() on a string is salted per
  process and will make your generator write different files every run; the
  arsenal's texture generator shipped with exactly that bug and the validator
  caught it as "33 generated files differ from what the generator writes".
