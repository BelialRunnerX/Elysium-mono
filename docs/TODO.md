# What is left

A single place to pick this up from. Written 2026-09-24, at the end of a long
session, against a tree that no longer fits in anybody's head.

Read section 0 before anything else. The rest of this document is worthless if
the work is gone.

---

## 0. The environment is not durable. The source is.

**The tree lives in an ephemeral cloud container.** `/tmp/Elysium-mono` is
reclaimed when the session ends, and none of this work is on the GitHub remote:
`git remote -v` points at a repository whose last commit predates the whole
29-module conversion, and GitHub is gated for `curl` here, so nothing can
push from the container.

So the source lives on your own machine instead, as plain files:

    C:\Users\weaks\curseforge\Elysium-mono

Not an archive — the actual tree, 18058 files, every one verified against
`MIRROR-MANIFEST.json` (a SHA-256 per file, written from the container) after
it landed. Nothing has to be unpacked and nothing can half-unpack.

To check a copy arrived intact: `ls -d elysium-*` counts 29,
`ls tools/negtest/*.sh` counts 140, and

    python3 - <<'PY'
    import hashlib, json, os
    manifest = json.load(open("MIRROR-MANIFEST.json"))
    bad = [p for p, want in manifest.items()
           if not os.path.isfile(p)
           or hashlib.sha256(open(p, "rb").read()).hexdigest() != want]
    print(len(manifest), "files,", len(bad), "wrong")
    PY

reports zero wrong.

**Work is handed out through the board, not by reading this document.** See
`docs/AGENTS.md`; `python3 tools/board.py list` is the current state.

## 1. Where things stand

Twenty-nine modules. **1,687 named things** of our own — 936 items, 706 blocks,
45 mobs — plus the systems behind them: three ages of power, logistics and
autocrafting, agronomy, a foundry chain, transit, arcana, comforts, an arsenal,
a bestiary, a quest book and the beginnings of worldgen.

Forty-five validators guard it: twenty-nine module validators and sixteen
cross-cutting checks — `gear`, `ui`, `buses`, `recursion`, `datapack`,
`nullable`, `loadtest`, `rules`, `worldgen`, `construct`, `lang`, `lifecycle`,
`manifest`, `merge`, `retexture`, and `validators`, which checks the checkers. Between them they run somewhere north of forty million assertions, and
every one of them has been negative-tested — deliberately broken to prove it
can report what it claims to check. That discipline is not decoration; it has
caught real bugs roughly every other time it has been applied.

Twenty-four jars have been retired — that is, replaced by one of our modules
and taken out of the pack. Three of them this session, each after the same
three pre-checks, each **moved** to `mods/_superseded/` rather than deleted.

`_superseded/` holds 65 jars, which is the larger and easier-to-misread
number: the other forty-one are DROP rows, removed as redundant or unwanted
rather than replaced by anything of ours. 181 jars are still live in
`Claude-dev-build`. Nothing has ever been deleted.

---

## 2. The honest measure of "everything ported"

You asked to be sure nothing is being missed. This is the answer, and it is not
a comfortable one.

The manifest still lists **110 rows as PLANNED** against a module. Four of them
are not jars in the instance: `amendments` and `powergrid` are stale rows from
the drifted `pack-jars.txt` (see section 4), and two are things you asked for
that no jar provides at all — the GregTech equivalent and space HarvestCraft.
That leaves **106 jars**, and every per-module count in section 3 is one of
those 106. Scanning all of them in the live instance, they contain:

| | in the jars still to convert | ours so far |
|---|---|---|
| blocks | 23,473 | 706 |
| items | 6,653 | 936 |
| mobs | 1,067 | 45 |
| biomes | 408 | 0 |
| structures | 242 | 0 |
| structure templates (`.nbt`) | 2,870 | 0 |

By volume we are at roughly **five per cent**. That number is real and it should
be said plainly, but it is also the least interesting thing about the position,
because it counts a chipped-stone variant and a working reactor as one thing
each.

What is actually true is this: **the systems are largely built and the
catalogues are not.** Every module has a frame that is proved correct — an
energy model that cannot free-energy, a transmutation table closed against
profit, a reward ladder that cannot invert, a grave that cannot lose an item, a
biome checker that cannot miss a dead biome. What none of them has is the
several thousand blocks of *variety* the jars they replace ship.

Both halves matter and they are not the same job. The frames were the right
thing to do first, because a catalogue built on a broken frame is a catalogue
that has to be redone. But the pack a player installs is mostly catalogue, and
that work has barely started.

---

## 3. What is left, module by module

Ordered by how much content is waiting. Every number is from the scan.

### elysium-atelier — 29 jars, 14,749 blocks

By a very wide margin the largest body of work in the programme, and the most
mechanical. `chipped` alone is 6,973 blocks: every vanilla material in dozens of
carved, tiled and patterned variants. Then Macaw's ten jars (doors, windows,
roofs, bridges, paths, fences, stairs, trapdoors, lights, furniture),
`handcrafted`, `supplementaries`, `everycomp`, the Diagonal trio, and a dozen
decoration packs.

What exists: twelve furniture shapes in sixteen materials (192 blocks), and the
five joining kinds in the materials the game lacks them in (36 blocks).

What is missing and is *not* a catalogue problem: **roofs, bridges, paths and
lights are shapes the module does not have at all.** A roof in particular has a
real geometry property worth proving — that a straight piece, an inner corner
and an outer corner of the same pitch tile without leaving a hole — and that is
the kind of thing this project is for. Do the four missing shape families
before touching the variant explosion.

For `chipped` specifically: 6,973 hand-made variants is not a thing to
reimplement block by block. It wants a generator driven by a table of pattern
families, the way `elysium-ores` and the gear catalogue already work, and it
wants the mirror-distinctness check the twelve shapes already have, applied at
that scale.

### elysium-logistics — 14 jars, 2,091 blocks, 833 items

`allthecompressed` is 1,794 of those blocks and is purely generative: every
material compressed nine times over. A table and a loop. `sophisticatedstorage`
and `sophisticatedbackpacks` are the real design work — upgrades, filters,
pickup rules — and `refinedstorage` and `appliedenergistics2` are a second
complete storage network on top of the one this module already has.

The frame here is strong: drawers, a controller, an assembly core, terminals,
and a planner that orders what it costs. The autocrafting property — that no
plan ever runs an ingredient short — is proved over four thousand random plans.

### elysium-kinetics — 19 jars, 1,873 blocks, 808 items, 393 `.nbt`

Create and its fifteen add-ons. The rotation and stress engine is done and
proved; what is missing is contraptions — the assembly, the schematics, the
393 structure templates — and the add-on catalogues (deco, cannons, diesel
generators, aeronautics, the kitchen).

This is the module where "port the content" is most misleading. Create's value
is its *mechanics*, and reimplementing a contraption system is a project in its
own right, not a catalogue pass. Decide deliberately whether Age I is ours or
whether Create stays.

### elysium-agronomy — 9 jars, 916 items, 738 blocks, 610 mobs

Mystical Agriculture's essence tiers are already framed. The 610 mobs are
`productivebees` — every bee variant — and that is a catalogue with a real
systemic core: bee breeding, produce, habitats. The space-themed Pam's
HarvestCraft you asked for lives here too, and has not been started.

### elysium-arsenal — 6 jars, 1,478 items, 540 blocks, 105 mobs

`Valoria` is 997 of those, and by your own earlier instruction it is split by
kind across four modules rather than getting one of its own. The weapons frame
is done (36 melee weapons, none dominated, each best at something). Missing:
bows and anything ranged at all, archaeology, and the uniques that carry
abilities — which is what `simplyswords` and `mythsandlegends` actually are.

An ability system is the prerequisite for those two, and it does not exist yet.
That is a frame, not a catalogue, and it should come before any of their items.

### elysium-bestiary — 7 jars, 889 items, 287 blocks, 205 mobs

Nine beasts exist on a proved reward ladder. The 205 mobs waiting are
`born_in_chaos`, `fangs_n_claws`, `mobs_of_mythology`, `friendsandfoes`,
`knightquest` and `naturalist`. The ladder generalises: the derivation already
handles any number of beasts and the harness proves it over a hundred thousand
random bestiaries, so adding to it is mostly modelling and animation work
rather than balance work.

`naturalist` is different in kind — passive fauna, ambience, a field guide —
and the module has nothing that does not attack you. That is a frame gap.

`mob_grinding_utils` is filed here and is not creatures at all; it is farming
infrastructure and probably belongs with logistics.

### elysium-atomics — 9 jars, 539 blocks, 490 items

Mekanism, its generators and tools, Powah, Extreme Reactors, Flux Networks,
Industrial Foregoing, Oritech. The reactor and the grid's lending model are
done. The catalogue — chemical processing chains, the five-stage ore doubling,
the machine roster — is not.

### elysium-wilds — 3 jars, 815 blocks, 133 biomes, 122 structures, 2,118 `.nbt`

**The largest frame gap in the programme.** What exists is the biome-placement
checker and a compass. There are no biomes, no structures and no worldgen
features of our own at all.

`wwoo` is 133 biomes and `ctov` is 122 structures with 2,118 templates. Neither
is a catalogue pass:

- Adding biomes to the *overworld* is not a datapack edit. The overworld's
  multi-noise parameter list is vanilla's, and injecting into it needs either a
  full preset override or a region-management layer of the sort Terrablender
  provides. **Decide that approach before writing a single biome.**
- Structures need jigsaw pools and NBT templates, which is authoring work with
  no arithmetic shortcut. `WildRules.Spacing` already proves a structure is met
  neither never nor constantly, so the balance half is ready and waiting.

### elysium-industry — 3 jars, 1,569 blocks, 742 items

Immersive Engineering is 1,192 of them. The Age II frame and its joint with
Age I are done and the free-energy loop is closed in arithmetic. Multiblocks
are not built, and IE is mostly multiblocks.

### elysium-arcana — 2 jars, 204 blocks, 83 items

`GeOre` (261 things: gem ores in every material) and `relics`. GeOre is
generative and should be a table. Relics needs the same ability system the
arsenal's uniques need — build it once.

### elysium-comforts — 5 jars, 68 blocks, 32 items

The smallest remaining. Sleeping bags, `towntalk`, `envelope`, `SimplyTooltips`,
and the six easy-villagers blocks the crate does not replace (trading station,
breeder, converter, incubator, farmer, iron farm). A good session's work, and
the only module within reach of actually being finished.

### Not assigned to any module

Two rows in the manifest are things you asked for that no jar provides: **the
GregTech equivalent** (filed to `elysium-foundry`) and **space HarvestCraft**
(filed to `elysium-agronomy`). Neither is started. The foundry's material chain
is the beginning of the first.

---

## 4. Known gaps that are not content

These are defects and limits in what already exists. Fix them when you next
touch the module concerned.

**Nothing has ever booted the game.** `check_construct.py` builds all 2,138
registry objects the way FML does, and `check_loadtest.py` runs the game's data
loaders, and between them they catch a great deal — including a crash in the
stone doors on the first run. But your CurseForge install is a client install
with no server jar, so a real end-to-end boot needs you to launch the pack
once. **Do that before trusting any of this in a world you care about.**

**`check_construct.py` cannot see past a lazy supplier.** A spawn egg holds a
`Supplier<EntityType>`, so an egg pointing at a mob nothing registers builds
perfectly and fails the first time somebody holds one. Anything of that shape
is unchecked.

**Five cross-cutting checks have never been negative-tested.**
`check_gear.py`, `check_lifecycle.py`, `check_loadtest.py`,
`check_manifest.py` and `check_retexture.py` have a report block and it is
last — `check_validators.py` proves that much — but nobody has ever broken
them and watched one fail, which is the only evidence that actually counts.
`elysium-dungeons` likewise has no negative-test script of its own. Six gaps;
`tools/negtest/README.md` lists them.

**94 validator rules are on the ratchet.** `docs/RULES_BASELINE.txt` lists rules
that name a *call* rather than a whole statement, and so would survive
`if (false) theCall();`. `check_rules.py` stops new ones being added but the 94
are still there. Burn them down as you touch each module.

**`tools/pack-jars.txt` has drifted from the live instance.** The manifest is
generated from that listing, which is the original V+ and Tech pack; about
twenty-nine of its rows name jar versions the instance no longer has. Nothing
is *missing* a decision, but the version strings are stale. Regenerate the
listing from the instance and re-run `tools/manifest.py` when convenient.

**The almanac describes six modules and points at thirteen.** Its independence
message still says six. Cosmetic, but it is a count that should be computed.

**`elysium-colonies` is deliberately outside the merged jar** and needs
MineColonies to compile. It is excluded from `check_construct.py` for that
reason.

---

## 5. The protocols. Do not skip these.

These are not style preferences. Every one of them exists because something
went wrong without it, usually more than once.

**One feature at a time, all the way through.** Architect it, write the pure
rules, write the harness, make the harness pass, write the game-side code,
write the generator, write the validator section, write the negative tests, run
the root validation, build, ship. A feature that is "done except for the
validator" is a feature that is not done.

**Every rule lives in a pure JDK class under `rules/`.** No `net.minecraft`, no
`net.neoforged`. That is what lets a harness sweep millions of cases in
seconds without a game, and it is checked.

**Every validator rule names a whole statement, never a call.** A rule that
looks for `Foo.bar(` still matches `if (false) x = Foo.bar(...)`. This has been
got wrong fifteen times. `check_rules.py` now catches the class.

**Every check must be negative-tested.** Break it deliberately, one way at a
time, and prove it reports. The corpus is `tools/negtest/` — 127 scripts, one
per feature — and `tools/dryrun.py` verifies a script's anchors without
touching the tree. Both used to live in `/tmp` and would have died with the
container; they are in the repository now. `dryrun.py` neuters *every*
mutation, not only the ones that go through `edit()`, because an earlier
hand-rolled version left a bare `rm` in place and deleted a real file, and a
later one let a heredoc through because it was tagged `PYZ` rather than `PYX`.
`tools/negtest/README.md` has the rest.

**Prefer copying the game to deriving it.** Three of five blockstates I derived
by hand were right; two were wrong in ways that look almost correct. They are
transcribed from the jar now and compared against it on every run. If the game
already decides something, read what it decided.

**Check what a jar contains before claiming to replace it.** `Reforged` sounds
like a weapons mod and is three smithing hammers. Open the jar.

**Never mark a row DONE for a frame.** Twelve furniture shapes is not
`handcrafted`. Nine beasts is not sixty. Marking a row DONE takes real content
out of the pack and puts a subset in its place, and it has been caught and
reverted twice. The manifest note should say exactly what exists and exactly
what does not.

**Retiring a jar:** only DONE rows, and then three pre-checks — nothing else
declares it `type="required"`, no hard tag entry names its namespace, and no
recipe, loot table or advancement in any other jar references it. Then **move**
it to `mods/_superseded/`. Never delete. `tools/prune.py` lists candidates;
`docs/CONVERSION.md` §4b has the full protocol.

**Every milestone ends the same way:** validate, negative-test, build the merged
jar, send it, commit it to both `Claude-dev-build/mods/` and `Claude/mods/`,
and append to `~/curseforge/CLAUDE-NOTES.md`. Never write to the `V+ and Tech`
instance.

---

## 6. The order I would do it in

1. **Push `elysium-conversion` to GitHub.** It is committed but only ever on
   disks that can vanish. Everything else is at risk until this is done.
2. **Launch the pack once** and send back the log. Nothing here has met the
   real game.
3. **Finish `elysium-comforts`** — five small jars, and the only module within
   reach of being genuinely complete. It will also prove the protocols still
   hold end to end.
4. **Build the ability system** once, in the library, and use it for both the
   arsenal's uniques and arcana's relics. Two modules are blocked on it.
5. **Decide the worldgen approach** for `elysium-wilds` before writing a biome.
   It is the largest frame gap and the decision governs all of it.
6. **The four missing atelier shape families** — roofs, bridges, paths, lights
   — with the roof-tiling geometry proof.
7. **Then the generative catalogues**, which are table-and-loop work and can be
   done in any order: `allthecompressed`, `chipped`, `GeOre`.
8. **Decide deliberately about Create.** Age I's mechanics are ours; Create's
   contraptions are not, and reimplementing them is a programme rather than a
   task.
