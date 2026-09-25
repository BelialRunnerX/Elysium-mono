# Agent 6 — Kinetics — Create and its nineteen addons

**Modules:** elysium-kinetics

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

    python3 tools/board.py claim elysium-kinetics --as AGENTNAME

19 jars, content weight 5,211. Seventeen of them are addons; Create itself is
2,129 of the total.

    2,129  create-1.21.1-6.0.10.jar
    1,221  createdeco-2.1.3.jar
      458  createbigcannons / 284 create_connected / 216 createdieselgenerators
      144  CreateDragonsPlus / 143 interiors / 126 create-stuff-additions
      111  create-new-age / 108 copycats / 107 createaddition
       66  create-enchantment-industry / 37 create_aquatic_ambitions
       35  create-integrated-farming / 9 sliceanddice
        9  create_ultimate_factory / 8 create_power_loader
        0  create-central-kitchen, create-aeronautics-bundled

== Do not write any code until you have written the decision down ==

Create is not a content mod. It is a physics system — rotational force, stress
budgets, contraptions that move as assemblies, belts that carry items as
entities — and everything above sits on it. Reimplementing that faithfully is
not a session's work or a week's. The content weight badly understates it,
because the weight counts blocks and the difficulty is in the simulation.

The three honest options:

 (a) Keep Create, convert only the addons. The pack keeps one foreign jar and
     the 17 addons become ours. Cheapest, and the conversion is then 18/19
     rather than 19/19.
 (b) Build a reduced kinetic system of our own — rotation and stress, no
     contraption assembly — and put the addons' content on it. Loses moving
     contraptions, which is the thing people install Create for.
 (c) Convert the addons' *content* onto machinery this pack already has
     (elysium-industry, elysium-atomics) and drop the kinetics entirely.

Write which you chose, why, and what it costs into docs/CONVERSION.md, and put
a one-line note in docs/MANIFEST.md against every jar it affects. A decision
this size that lives only in somebody's head is a decision that gets made again
differently in three months.

If you choose (a), say so on the board and release the unit rather than leaving
it held: the remaining work is then 17 small jars and somebody else can take it.

== If you build anything kinetic, prove this ==

Stress is conserved: what a source produces equals what the network consumes
plus what is idle. Sweep it across randomly built networks, including cycles —
a network that gains stress round a loop is a perpetual motion machine, and it
will be found by a player before it is found by reading.
