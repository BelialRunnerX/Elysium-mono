# Agent 1 — Foundation and the Valoria chain

**Modules:** elysium-lib, elysium-ores, elysium-foundry, then elysium-arsenal, elysium-armory, elysium-trinkets

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

Claim these, in this order, and hold `shared` for the first one:

    python3 tools/board.py claim shared --as AGENTNAME
    python3 tools/board.py claim elysium-lib --as AGENTNAME
    python3 tools/board.py claim elysium-ores --as AGENTNAME
    python3 tools/board.py claim elysium-foundry --as AGENTNAME

and later, once the materials exist:

    python3 tools/board.py claim elysium-arsenal --as AGENTNAME
    python3 tools/board.py claim elysium-armory --as AGENTNAME
    python3 tools/board.py claim elysium-trinkets --as AGENTNAME

== You go first, and other agents are waiting on you ==

elysium-lib is shared ground: every other module reads its attributes, effects
and ability roster. Anything you add there, everyone gets; anything you break
there, everyone gets too. So do the lib piece first, do it quickly rather than
thoroughly, and release `shared` the moment it lands. Then take your time on
the rest.

== The work, in order ==

1. "Valoria: effects" -> elysium-lib. 17 effects, 18 attributes and 6
   enchantments that the rest of the Valoria split reads. Read them out of
   Valoria-1.21.1-1.0.4.2.jar's own assets/valoria/lang/en_us.json rather than
   from a wiki; the jar is in Instances/Claude-dev-build/mods. Existing shape
   to copy: elysium-lib/src/main/java/com/elysium/lib/combat/ElysiumEffects.java
   already registers seven effects (SUNDERING, BLEEDING, GRIEVOUS, VITALITY,
   KNOWLEDGE, DETONATION, FLYING) with their numbers as named constants.

2. "Valoria: materials" -> elysium-ores. The ores, ingots and drops behind
   Valoria's 17 material sets. Read the sets out of the same lang file; at the
   time of writing they are: infernal, nature, crimtane, jade, aquarius,
   cobalt, ethereal, void, lunar, phantasm, pyratite, spider, black_gold,
   awakened_void, depth, marsh, samurai. Do not trust that list — re-derive it,
   because the check that matters is that the gear and the materials agree, and
   two lists typed twice is how they stop agreeing.

3. "(new) gregtech equivalent" -> elysium-foundry. This has no jar behind it.
   It is the deep material chain that is meant to tie the three ages together,
   and it is a design job before it is a coding job. Write the design into
   docs/ first and say what it costs, then build it. elysium-foundry already
   makes 34 materials; read FoundryRules first and extend rather than restart.

4. "Valoria: gear" -> elysium-arsenal, and "Valoria: armour" -> elysium-armory.
   Both are blocked until step 2 lands: a tier with no material behind it is a
   weapon nobody can craft.

5. "Valoria: charms" -> elysium-trinkets. The pet curio slot and what goes in
   it. Independent of everything above; do it whenever.

== The thing that will bite you ==

elysium-arsenal's ladder is ArsenalRules.TIERS — six vanilla tiers — and every
one of its 36 melee weapons, 36 bows and 24 broad tools is derived from it,
along with four separate proofs: that no weapon of a tier is beaten on every
axis by another, that each is the best at something, that the ladder climbs at
every rung, and that the game's own sword and bow are neither deleted nor left
better than all of ours.

Adding Valoria's materials as new rungs multiplies twelve shapes by however
many tiers you add, and every one of those proofs has to still pass. So do not
hard-code a seventh tier: make the ladder take its rungs from a roster, and let
the existing harnesses tell you the moment a new rung breaks something. They
will. That is what they are for.

Also: ForgeRules.smallestLadderStep() computes reforging's ceiling from the
weapon ladder itself. Add rungs and that ceiling moves. Check reforging still
passes after every tier you add — `bash tools/negtest/negtest_forge.sh` is the
proof that its checks still work at all.
