# Agent 8 — Bestiary, Comforts and Arcana — mobs and the relic summons

**Modules:** elysium-bestiary, elysium-comforts, elysium-arcana

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

    python3 tools/board.py claim elysium-bestiary --as AGENTNAME
    python3 tools/board.py claim elysium-comforts --as AGENTNAME
    python3 tools/board.py claim elysium-arcana --as AGENTNAME

14 units, content weight 2,721. The smallest unit by weight, and it contains
the one piece of work another module is already depending on.

== Do this first: the relic summons ==

elysium-arcana ships 30 relics across 8 slots, carrying the 30 abilities of the
shared ability system. Seventeen of those abilities are actives that throw
something — a summon, a projectile — and **those seventeen entities do not
exist yet**. The relics are registered, the abilities are priced, the harness
passes, and using an active does nothing.

That is the whole of relics-1.21.1-0.12.8.jar's remaining row, and it belongs
here rather than in arcana's own unit because it is mob work. Read
elysium-lib/src/main/java/com/elysium/lib/ability/ElysiumAbilityRules.java to
see which abilities need what, then build the entities in elysium-bestiary and
wire them from arcana.

== The mobs ==

    958  born_in_chaos_[Neoforge]_1.21.1_1.7.6.jar
    427  naturalist-2.0.2-neoforge-1.21.1.jar
    315  friendsandfoes-neoforge-4.0.27+mc1.21.1.jar
    280  knightquest-neoforge-1.21.1-1.9.3.jar
    196  fangs_n_claws-1.2.2.jar
     81  mob_grinding_utils-1.1.10+mc1.21.1.jar
     54  mobs_of_mythology-neoforge-1.21.1-3.0.0.jar
      -   "Valoria: creatures"  (50 entities)

elysium-bestiary and elysium-mobs both already exist and both have renderers
driven from a roster rather than registered by hand — read
elysium-agronomy/src/main/java/com/elysium/agronomy/client/AgronomyClient.java
for the pattern. A renderer registered for 74 of 75 mobs is how a pack ends up
with one creature rendering as a black-and-magenta cube, and it is only ever
found by the player who meets that one.

What to prove about a mob roster: every mob has a spawn that can actually
happen (a biome it is allowed in, a light level it tolerates, a height band
that exists); no mob is a strictly worse version of another to fight; every
mob's drops are things the pack makes; and the ones that spawn in groups have
a group size that fits where they spawn.

== Comforts ==

    163  envelope-neoforge-1.21.1-0.7.3.jar
     82  comforts-neoforge-9.0.5+1.21.1.jar
     18  easy-villagers-neoforge-1.21.1-1.1.42.jar
      0  towntalk-1.2.0.jar
      0  SimplyTooltips-neoforge-0.1.3.jar

elysium-comforts already has graves and pocket villagers, with 60 negative
tests behind them. The discipline there is worth copying before you add
anything: every one of those tests is a way a container could have lost what
was in it.
