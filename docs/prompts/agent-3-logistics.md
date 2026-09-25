# Agent 3 — Logistics — storage, compression and networks

**Modules:** elysium-logistics

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

    python3 tools/board.py claim elysium-logistics --as AGENTNAME

14 jars, content weight 10,406. Three of them are most of it.

    4,489  allthecompressed-1.21.1-4.4.0.jar
    3,594  modular_backpacks-0.2.7.jar
      884  refinedstorage-neoforge-2.0.9.jar
      631  appliedenergistics2-19.2.17.jar
      293  sophisticatedstorage / 244 integrateddynamics / 144 ExtendedAE
      106  sophisticatedbackpacks / 12 enderchests / 5 cyclopscore
        3  commoncapabilities / 1 sophisticatedcore
        0  the two sophisticated*createintegration jars

== The decision you have to make first ==

refinedstorage, appliedenergistics2 and ExtendedAE are three storage networks
with three different models: RS is a flat network with a disk drive, AE2 is a
channel-limited network with a spatial component, ExtendedAE extends AE2.
Building all three is building three things that do not talk to each other,
and the player ends up with one of them plus two sets of dead blocks.

Pick one model, build it properly, and fold the other two jars' *content* onto
it. Write the decision and what it costs into docs/CONVERSION.md before you
write code. Say plainly which of the three you are effectively deleting.

== allthecompressed is a generator, not 4,489 blocks ==

It is N materials crossed with 9 compression tiers. Put the materials and the
tier count in rules/ and generate the rest. The one property worth proving,
and it is worth proving hard, is the round trip: compressing and then
decompressing must return exactly what went in, at every tier, for every
material, with no rounding and no loss. Sweep it — that is a few hundred
thousand checks and it runs in a second.

The second property: a compressed block must be worth exactly 9 of the tier
below. If any tier is worth 8 or 10 of the one below, a player can compress and
decompress in a loop to make matter, and nothing else in the pack will survive
that.

== modular_backpacks and the sophisticated* family ==

These are one idea — a container whose capacity and behaviour come from parts
fitted to it — appearing in four jars. Build the part system once. What to
prove: capacity is the sum of what is fitted and never more; a part cannot be
fitted twice; removing a part never destroys what is inside; and a backpack
inside a backpack is refused, or the capacity accounting is a recursion.

elysium-comforts already has a grave system whose whole discipline is "a
container must never lose what is in it" — read its validator before you start.
