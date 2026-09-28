# The Great Conversion

**Goal, in Dylan's words:** *"You're going to convert (or make like with the
space mod) and pack every other mod from V+ and Tech into the Elysium system…
This is deeper than reskinning, this is full deep systemic integration… The end
project will not retain the original create or etc jars, and instead will only
be Elysium-namehere jars."*

This file is the contract for that work. It exists because the work is far
longer than any one conversation: **every step is written down here and in
MANIFEST.md, and the manifest is the source of truth for what is done.** A
session that has lost its history reads these two files and knows exactly where
it is.

---

## 0. How to resume (read this first)

1. `docs/MANIFEST.md` — every jar in the pack, what we decided about it, which
   Elysium module absorbs it, and its status. **Update the row the moment a
   module lands.** Nothing is "done" until its row says so.
2. `docs/CONVERSION.md` — this file: the architecture, the tiers, the order.
3. `CLAUDE-NOTES.md` on the user's machine — the running log of shipped work.
4. Working mode is unchanged and non-negotiable: *architect one feature,
   implement, validate (validator + pure-rules harness + negative tests that
   break every rule), build the jar, deliver it, commit it to
   `Instances/Claude-dev-build/mods/`, append to CLAUDE-NOTES.md.*

---

## 1. What "conversion" means here

It is **reimplementation, not repackaging**. For each mod we keep the *idea*
and write the code and the art ourselves, exactly as elysium-ascendancy
replaced Apotheosis and elysium-space replaced Stellaris:

* mechanics are reimplemented from the published behaviour (wikis, in-game
  observation, and where the licence allows it, source);
* every number lives in a pure rules class a harness executes;
* art is generated or drawn by us — **no third-party assets are copied**;
* names, lore and flavour are re-themed into Elysium's own world, which
  `check_names.py` enforces and `docs/NAMING.md` explains — it was prose
  for months and 134 creature ids kept the names of the mods they came
  from the whole time;
* the original jar is then unnecessary and comes out of the pack.

Licensing is the user's to settle with authors where it ever needs settling
(his standing instruction). What this repository does is keep the line clean:
our code, our art, our names, credit where a design came from.

---

## 2. The tech spine

The three big tech mods are not three mods here. They are **three ages of one
technology**, and the whole point of the conversion is that they become a
progression rather than three parallel ones.

| Age | Elysium module | Modelled on | The fiction | The feel |
|---|---|---|---|---|
| I — **Kinetics** | `elysium-kinetics` | Create (+ its addons) | Salvage. Pre-collapse machinery restarted with wood, brass and water. | Rotation, stress, contraptions. Things you can watch turn. |
| II — **Industry** | `elysium-industry` | Immersive Engineering, TFMG | The Reconstruction. Heavy plant, wires on poles, diesel and steam. | Multiblocks, high voltage, ugly and enormous. |
| III — **Atomics** | `elysium-atomics` | Mekanism (+Generators/Tools), Powah, Extreme Reactors, ZeroCore | The Ascendancy's own century. Chemical processing, fission, fusion. | Factories, ore multiplication, gases, matrices. |
| IV — **Foundry** | `elysium-foundry` | GregTech (our equivalent) | What the Empire actually runs on. | Deep material science: every metal has a real chain. |

**The rules that make it one progression rather than four:**

1. **One energy unit.** Forge Energy, as elysium-space already uses. Age I
   produces rotation and converts to FE badly; Age II produces FE properly;
   Age III produces it in quantity; Age IV assumes it.
2. **One rotation unit.** Stress units, from Age I, and Age II machines can be
   driven by Age I shafts at a penalty — so the ages physically connect.
3. **One material ladder.** Age I gives you raw metal. Age II doubles it. Age
   III triples and separates it into chemicals. Age IV is the full chain.
   Every tier's *output* is the next tier's *input*, so nothing is skippable
   and nothing is obsolete.
4. **Gated by world tier, not by a research tree.** The Ascension/world-tier
   system already exists and already means something; the ages hang off it.
5. **Each age keeps a reason to exist.** Age I contraptions stay the only way
   to do moving structures; Age II multiblocks stay the cheapest bulk power;
   Age III is the only chemistry; Age IV is the only full recovery.

---

## 3. The module map

New modules, each a jar, each merged into `elysium-all` unless it is an add-on
to a mod we do not own (the `STANDALONE` rule in `compile.sh`).

| Module | Absorbs |
|---|---|
| `elysium-kinetics` | create, createaddition, createdeco, create_connected, create-stuff-additions, createbigcannons, createdieselgenerators, create-new-age, create-aeronautics, create-central-kitchen, create-enchantment-industry, create-integrated-farming, create_aquatic_ambitions, create_ultimate_factory, create_power_loader, CreateDragonsPlus, copycats, sliceanddice, interiors, steam/rail content |
| `elysium-industry` | ImmersiveEngineering, tfmg, powergrid, oritech |
| `elysium-atomics` | Mekanism, MekanismGenerators, MekanismTools, Applied-Mekanistics, Powah, ExtremeReactors2, ZeroCore2, FluxNetworks |
| `elysium-foundry` | our GregTech equivalent (new design, informed by all of the above) |
| `elysium-logistics` | appliedenergistics2, ExtendedAE, refinedstorage, StorageDrawers, functionalstorage, dankstorage, sophisticatedcore/storage/backpacks, enderchests, modular_backpacks, integrateddynamics, allthecompressed |
| `elysium-agronomy` | our space Pam's (crops + trees), MysticalAgriculture, MysticalAgradditions, botanypots, productivebees, FarmersDelight, Aquaculture, solcarrot, tide, cobblegengalore |
| `elysium-transit` | our Automobility equivalent, waystones, create trains where they belong |
| `elysium-arsenal` | simplyswords, mythsandlegends, Reforged, SimplyBows, simplest_hammers/excavators/paxels/scythes, betterarcheology |
| `elysium-bestiary` | born_in_chaos, fangs_n_claws, mobs_of_mythology, naturalist, friendsandfoes, knightquest, mob_grinding_utils |
| `elysium-atelier` | the eleven macaw mods, chipped, handcrafted, amendments, supplementaries, beautify, bellsandwhistles, DnDesires, Design-n-Decor, DustyDecorations, cluttered, bf_blockpack, Clothing_Of_The_Lowlands, valhelsia_furniture, woodworks, fusion, everycomp, diagonal fences/walls/windows, chimes |
| `elysium-wilds` | wwoo, ctov, Quark's worldgen half, NaturesCompass |
| `elysium-comforts` | comforts, gravestone, lootr, easy-villagers, towntalk, a Patchouli-style guide, envelope, polymorph-like QoL |
| `elysium-arcana` | ProjectE, projectexpansion, relics, GeOre |

Existing modules stay as they are and absorb what belongs to them
(elysium-ores took the ore unification, elysium-colonies the MineColonies
layer, and so on).

---

## 4. Order of work

Highest leverage first, and always a thing that can be finished and shipped.

1. ~~**The spine** — `elysium-kinetics` rotation/stress engine~~ — **done,
   2026-09-24.** 13 blocks, four machines, three connection rules, the windmill.
2. ~~`elysium-industry` on top of it~~ — **the frame is done, 2026-09-24.**
   Twelve blocks, five parts, four machines, both directions of the joint with
   Age I, and the free-energy loop closed in arithmetic (0.6 return, proved in
   closed form and swept). *Not* yet the catalogue: Immersive Engineering,
   TFMG and Powah still have content this module has to absorb before their
   rows can move, and the multiblocks are not built. See MANIFEST.md.
3. ~~`elysium-atomics`~~ — **the frame is done, 2026-09-24.** Ten blocks,
   three parts, the grid taught to lend and borrow, a reactor whose heat
   settles rather than running away, and the uranium chain the pack was
   missing. *Not* yet the catalogue: Mekanism, Powah and the rest of the
   fission mods still have content this module has to absorb.
4. ~~`elysium-logistics`~~ — **done, 2026-09-24.** Three drawers, a bus and a
   controller that makes a wall of drawers one inventory a hopper can reach;
   then an assembly core, assemblers and a terminal on top of it. The planner
   now *orders* what it costs as well as costing it, and the harness follows
   four thousand random plans to the end without an ingredient ever going
   short. **No pattern items:** the core reads the game's own crafting recipes,
   so the gate on autocrafting is machinery and power rather than paperwork.
   **No screen:** the terminal is asked by holding up what you want. A screen
   is a strict addition to this and changes nothing behind it.
5. ~~`elysium-agronomy`~~ — **the frame is done, 2026-09-24.** A pot, an
   accelerator, a hive, three rungs of rock generator, five essence tiers and
   eight seeds — with the one rule that matters executed on every build: a farm
   loses to a mine by a stated margin, and overtaking one has to be a
   deliberate industrial project. The space crops are declared in
   `AgronomyRules.FRONTIERS` and each is tied to a condition of its world; the
   blocks for them are not built. Farmer's Delight's cooking and Aquaculture's
   fishing are not started.
6. ~~`elysium-foundry`~~ — **the frame is done, 2026-09-24.** Thirty-one
   material families, a chain proved acyclic and leak-free by execution, four
   casing tiers, and the ore debt every earlier module had been quietly
   running up: every ore `elysium-ores` generates now has a supplier, so the
   day the old jars come out nothing stops dropping.
7. ~~`elysium-almanac`~~ — **done, 2026-09-24.** The quest book: 45 steps over
   8 chapters, each with a reason rather than a checklist entry, proved three
   separate ways to contain no cycle and strand nothing. It depends on no
   module it describes, which is what lets it describe all of them.
8. ~~`elysium-transit`~~ — **the waystone half is done, 2026-09-24.** A stone
   you bind, a price the grid pays, and — the part that took the work — a
   price that **cannot be arbitraged**. The first draft had a free allowance
   under 256 blocks, which lets a player chain short hops across a world for
   nothing; the second priced distance linearly, which made bouncing off the
   moon and back a flat 44,000 for *any* distance at home. Both were found by
   executing a million random triples, neither by reading. The price now
   saturates, which makes it concave, which makes it subadditive.
   The other half landed the same day: **three rungs of one vehicle** — cart,
   truck, hauler — that run on anything that burns, read the block under the
   wheels as one of four surfaces, and lose to walking over rough ground while
   a full load is aboard, which is the entire argument for laying a road. A
   fourth rung is four numbers and a sprite, not a file.
9. ~~`elysium-arcana`~~ — **the transmutation half is done, 2026-09-24.** One
   block. A price table derived from the pack's own loaded recipes rather than
   shipped, so another mod or a datapack is priced without this module knowing
   it exists — and **proved to contain no arrangement that makes value out of
   nothing**, which every mod that has ever done this has shipped at least
   once. Relics and GeOre are not started.
10. ~~`elysium-comforts`~~ — **the grave is done, 2026-09-24.** One block, and
   the only claim worth making about it: **nothing a player owned is ever
   lost.** That is a counting problem rather than a sentiment — every
   gravestone mod that has eaten somebody's backpack did it by deciding in
   advance how many slots a grave has, so this one is built around what it is
   handed, and it is filled from *what would have dropped* rather than from
   the inventory, so a slot added by a mod this module has never heard of is
   buried like anything else.

   **Per-player loot landed the same day.** A dungeon chest on a server is a
   race; whoever arrives first takes the lot. Every answer is "give each player
   their own copy", and what differs is the bookkeeping. Here the roll is a
   *function* of the container and the player, so nothing has to be remembered
   about who has opened what — the same player always gets the same chest, for
   ever, with no record of them anywhere. Only what they have **taken** is
   stored, and that is let go of the moment they have taken all of it. The
   original container is never unpacked, so the hundredth player gets the same
   chance as the first. Comforts' own sleeping bags, easy-villagers, towntalk
   and envelope are not started.
11. ~~`elysium-atelier`~~ — **the furniture half and the joining half are done,
   2026-09-24.** Twelve
   shapes the game does not have, in sixteen materials it already has: **192
   blocks, no new textures and no new dependencies**, because every shape is
   cut from a vanilla material and wears that material's own face. The whole
   catalogue is registered from a loop, so a thirteenth shape is one line in
   `AtelierRules.SHAPES` and nothing else anywhere.

   **Nothing is retired for it yet, on purpose.** Twelve shapes covers what
   `handcrafted` and Macaw's Furniture are *for* and not their catalogues —
   beds, couches, counters, sinks, cupboards, drawers — and doors, windows,
   roofs, bridges, paths and lights are shapes this does not have at all. A
   frame is not a catalogue; those rows stay PLANNED until it is one.
   **The joining half, added the same day: 36 more blocks.** Doors, trapdoors,
   gates, fences and panes — but only in the five materials the game has none
   of, plus panes in everything. The first draft added all eighty and eleven
   materials' worth of them were blocks Minecraft has shipped since 2011,
   which this module's own shape list says in so many words is adding nothing.
   The principle is a check now rather than a comment: `JoinRules` carries a
   written list of what the game ships, the catalogue subtracts it, and the
   harness fails on anything you could already craft.

   What goes wrong with a fence is never inside the fence. Joining is written
   in **tags** — the game's fence asks whether its neighbour is in `#fences`
   and whether the two agree about `#wooden_fences`, a villager asks whether a
   door is in `#wooden_doors` before walking through it — so a block left out
   of one tag works perfectly and simply does not join. The tags are derived,
   joining is proved symmetric over all 1,296 ordered pairs, and the validator
   diffs the **shipped tag files against the rules** rather than against the
   generator that wrote them.

   The blockstates are transcribed out of the game's own jar rather than
   derived. Three of the five were right when I worked them out by hand; the
   trapdoor turned its top half the wrong way and the pane put its blank sides
   on the wrong sides. The validator re-reads the jar every run. It then found
   a third: our trapdoor models were built on the *orientable* template, which
   turns its texture with the facing.

12. ~~`elysium-arsenal`~~ — **the broad tools are done, 2026-09-24.** Hammer,
   excavator, scythe and paxel in six vanilla tiers. Adding one of these
   without making the pickaxe pointless is the whole difficulty, so every one
   is **slower per swing** than the tool it replaces and **every block it takes
   costs a point of durability** exactly as if it had been mined singly. That
   leaves one band of speed where a broad tool is worth carrying, and the
   harness sweeps a thousand speeds to check the band is a band rather than an
   opinion.

   **And thirty-six melee weapons, the same day.** Six kinds in six tiers. The
   problem five weapon mods create between them is not that any one of them is
   badly made — it is that a hundred swords are never compared *to each
   other*, so three of them answer every question and ninety-seven fill loot
   tables. So the rule is **domination**: at least as good on every axis and
   better on one makes the other dead content, and the harness proves no
   weapon of a tier dominates another and that each is the best at something.
   Four faults on the first run: two kinds best at nothing, a longsword that
   beat the vanilla diamond sword on every axis, a ladder that dipped because
   gold sits between iron and diamond in the *registry* and below both in
   damage, and two weapons sharing a crafting grid. Bows and archaeology are
   not started, and Reforged turns out — on opening the jar rather than
   guessing from the name — to be three smithing hammers.
13. ~~`elysium-bestiary`~~ — **the floor is done, 2026-09-24.** Nine hostile
   beasts in three families of three, one entity class, three model shapes.
   The point of it is not the mobs: seven jars in this pack put things in the
   dark and not one of them states what any of them is worth against any other,
   which is how a pack ends up with a mob nobody fights twice and another that
   every grinder in the pack is built for. So **the stats are chosen and the
   rewards are not** — experience and drops do not appear in the table at all
   and are derived from the threat the stats come to, in order of danger, each
   forced past the one below it. The harness runs that derivation over a
   hundred thousand random bestiaries, so a tenth beast inherits the property
   instead of needing it re-checked; it also proves the whole set sits inside a
   1.5x band of reward-per-danger and that **the game's own zombie sits inside
   that band**, which is the check nobody writes because writing it means
   measuring your own work against somebody else's game. The model is a table
   of boxes the renderer squashes onto the declared hitbox, so nothing is ever
   a different size on screen than the box you swing at. *Not* the catalogues:
   born\_in\_chaos, fangs\_n\_claws, mobs\_of\_mythology, naturalist,
   friendsandfoes, knightquest and mob\_grinding\_utils stay PLANNED. Nine
   beasts is a floor, not sixty creatures, bosses, passive fauna or spawner
   tooling.
14. `elysium-wilds` — **the rules and the check are done, 2026-09-24; the
   content is not.** The game picks a biome by which of their claims on a
   six-dimensional climate space is nearest, so a claim sitting inside an
   earlier claim ties everywhere it could win, loses every tie, and never
   generates. **This pack shipped one** — `emerald_bastion_river` — and nothing
   reported it, because nothing was wrong: the file was valid and the id
   resolved. So the module is the game's own nearest-box rule against the JDK
   alone, and `check_worldgen.py` runs it over every dimension file in the
   repository. Two things it corrected that I had written wrong: a thin box is
   not a small territory (a sliver still wins everything nearer to it than to
   anything else), and there is no universal floor on a biome's share — a river
   failed the one I wrote, for being a river. How much of a world one biome may
   be is now a per-world claim in a table, and a world with no row is an error.
   *Not* the content: wwoo, ctov, Quark's worldgen and NaturesCompass all stay
   PLANNED. There are no biomes, structures or compass in it yet — what exists
   is the part that says whether any of them would have worked.

Interleaved, because they are small and were asked for directly:

* ~~unified GUI theming across every Elysium screen (the `ElysiumUI` kit)~~ —
  **done, 2026-09-24**. `ElysiumButton` and `ElysiumChooser` added so a plain
  `Screen` can use the kit at all, the three space screens ported onto them,
  and `check_ui.py` wired into root validation so no module the conversion adds
  can drift: every screen must reach `ElysiumUI`, no vanilla widgets, no blits,
  no colour written by hand, no English typed into a screen. **Every new module
  below inherits this — build screens on `ElysiumChooser` or a container screen
  drawn with `ElysiumUI`, and put every colour in `ui/palette.py`.**
* ~~natural, **working** portals to the other worlds at ruined-portal
  frequency~~ — **done**, the waygate structure set.
* ~~the Apotheosis/Silent Gear coverage audit against the wikis~~ — **done**,
  `SPEC_CLONE.md` §P3b.

### The slowest loop in this project, and the check that closed it

Every check here reads a file or runs arithmetic, and `check_loadtest.py` runs
the game's *data* loaders. Nothing ran our **Java**. A block's constructor is
where a surprising amount of a mod lives, and every mistake in one throws when
FML fires the registry event — four seconds into loading, before any screen. The
only report is a crash log, and getting one meant asking Dylan to launch the
game.

`check_construct.py` builds them all here instead. A `DeferredRegister` keeps
its suppliers in a private map until FML asks; this asks. It thaws the
registries `Bootstrap` froze, builds each entry, registers what it built, and
goes round until a pass builds nothing new. **2,138 blocks, items and types
across 28 modules**, and on its first successful run it found that the stone
doors added that morning were built on `BlockSetType.STONE` — which *opens by
hand*, because it is what stone buttons are built on and the game has no stone
door for it to have been wrong about. That was a crash on world load, caught by
the guard written for exactly that disagreement.

What it cannot see is anything behind a lazy supplier: a spawn egg holds a
`Supplier<EntityType>`, so an egg pointing at a mob nothing registers builds
perfectly and fails the first time somebody holds one. Written down rather than
assumed.

### The mistake this project keeps making, and the check that now catches it

A validator proves things about the source by looking for text in it. A rule
that looks for a **call** — `"ComfortRules.seedFor("` — goes on passing the day
somebody writes `long seed = 0L; if (false) seed = ComfortRules.seedFor(...);`.
The call is still there, the rule still matches, and the behaviour it was
written to guarantee is gone.

**This has happened fourteen times**, every single one found by a negative test
rather than by reading. The fix is always the same: name the *whole statement*.

So `check_rules.py` now finds rules that compare a truncated string — one
ending in `(` or `,` — against a **method body**, and fails the build on a new
one. Rules that ask whether a declaration exists are compared against a whole
file and are left alone; that distinction is why the check is signal rather
than noise. There are **94 already here**, recorded in
`docs/RULES_BASELINE.txt` as a ratchet: a new one fails, and the recorded ones
come off the list as the modules they live in are next touched.

**And three rules the ages below hand upwards.** Every later age inherits
these from Age II, because each of them was a bug before it was a rule:

* **A block on an Age I line must say what it is made of.** The default is
  timber, so a node that stays silent caps the line under it at timber's shear
  rating, and the line comes apart at a speed the player was told was safe.
  Age II answers it once in `SteelNode`; Age III and IV each need their own.
* **What a machine draws from a grid must not depend on what the grid can
  give.** That is a loop with no fixed point — it oscillates and never settles.
  A starved machine draws its full rating and runs more weakly.
* **Any two ages that convert energy both ways must be proved unable to pay.**
  Not tested on one arrangement — proved in closed form over every count and
  gearing, and swept to confirm the form. Both ages return exactly 0.6, and
  Age III's harness also checks the case neither age's own proof covers: both
  ages' motors on one line, settled by Age I's solver rather than by a formula.
* **An age that adds a new kind of grid member extends Age II's model rather
  than running a second grid.** Age III's cells describe themselves in two
  floats (`IndustryRules.Reserve`) and Age II settles them. Two grids would be
  two models that disagree, and the disagreement would be found by a player.
* **A module is not finished until the integrations it inherited are written.**
  Age III ships raw uranium because `elysium-ores` generates the ore and every
  mod that could have filled its tag is being retired.

**And one rule the conversion inherits from an incident.** Every listener must
be on the bus its event is actually fired on. `check_buses.py` asks the NeoForge
jars, because nothing in the type system does and we shipped a jar that crashed
before the title screen over one line. The tech modules will add hundreds of
listeners; this is checked from now on, not remembered.

---

## 4b. Retiring a jar, and the integrations that go with it

*Dylan: "Make sure to remove old mods once we have converted them so there
isn't redundancy."*

`python3 tools/prune.py` lists what is removable, read out of `MANIFEST.md`
rather than from memory: only rows marked **DONE**. A PLANNED jar still holds
content nothing replaces; a DROP jar is a library something still depends on.
Removal is a **move** into `mods/_superseded/`, never a delete — a jar that
turns out to have been holding something up is one you want back in half a
minute.

**Before removing, check two things on the device**, both of which are a
scripted scan of the remaining jars and not a judgement call:

1. **Required dependencies.** No remaining jar may declare the removed mod
   `type="required"`. (At the first prune: none did.)
2. **Tag entries.** A tag entry naming a missing item breaks *the whole tag*
   unless it is marked `required: false`. (At the first prune: every one was
   optional.)

### The second prune, 2026-09-24

Five more jars moved to `mods/_superseded/` in both the dev instance and the
test copy: **ProjectE**, **projectexpansion**, **StorageDrawers**,
**dankstorage** and **functionalstorage**. Both scripted pre-checks were run
against the 189 remaining jars on the device and both came back clean:

* **Required dependencies** — nothing remaining declares any of the five
  `type="required"`.
* **Tag entries** — 7,514 tag files scanned across every remaining jar; every
  reference to the five is optional, so no tag is broken.

`tools/prune.py` had a bug worth recording: it listed **our own jars** and the
`(new) …` design-note rows as removable, because both are marked DONE. A prune
list that tells you to delete the thing doing the replacing is worse than no
list, so it now excludes both.

### The third prune, 2026-09-26

**envelope** moved to `mods/_superseded/` in both instances, now that
`elysium-comforts` carries the post. Both scripted pre-checks were run against
the 174 remaining jars on the device and both came back clean:

* **Required dependencies** — nothing remaining declares `envelope`
  `type="required"`.
* **Tag entries** — no tag file in any remaining jar names an `envelope:` id at
  all, required or optional.

Two copies went across: `0.7.5`, which was the live one, and `0.7.3`, which was
already in `_superseded` from an earlier version bump. Both are kept.

### The fourth prune, 2026-09-26

**mobs_of_mythology** moved to `mods/_superseded/` in both instances, now that
`elysium-bestiary` carries its eight creatures. Both scripted pre-checks were
run against the 172 remaining jars on the device and both came back clean:

* **Required dependencies** — nothing remaining declares it `type="required"`.
* **Tag entries** — no tag file in any remaining jar names a
  `mobs_of_mythology:` id at all, required or optional.

### The fifth prune that did not happen, 2026-09-26

**friendsandfoes stays**, and it is worth writing down why, because the jar
looks ready to go: `elysium-bestiary` now carries every one of its creatures.

It carries nothing else, and the jar is four mods in a trench coat. Reading
its own language file rather than a memory of it: ten creatures, **29 blocks**
(eleven wooden beehives, fifteen of copper button and lightning rod across the
oxidation states, the buttercup and its pot, the crab egg), **six items** that
are not spawn eggs (two totems, the wildfire crown and its fragment, the crab
claw, a disc), and a **villager profession** with five tiers of trades, a
job-site point of interest, three village house variants and a hero-of-the-
village gift.

So the row became **SPLIT** rather than DONE, with six pieces in
`docs/SPLITS.md` and one of them finished. `tools/prune.py` will not offer the
jar until every piece is, which is the behaviour it already had and the reason
the status column is what prune reads rather than a judgement made at the time.

Both pre-checks were run anyway, against the 176 remaining jars, so that
whoever finishes the split does not have to: **nothing declares
`friendsandfoes` a required dependency**, and **no tag file in any remaining
jar names a `friendsandfoes:` id at all**. 7,210 tag files scanned. When the
last piece lands the jar can go without a second look.

### The sixth prune that did not happen, 2026-09-26

**fangs_n_claws stays**, for the reason friendsandfoes stays, and the pattern is
worth naming now that it has happened twice: *a creature mod is rarely only a
creature mod*. Read this one's language file and the twenty-seven creatures come
with four armour sets, ten weapons, six effects, five enchantments, three blocks
and a shelf of materials and food. Converting the creatures and calling the jar
done would drop all of that without anybody deciding to.

So **SPLIT** again, six pieces in `docs/SPLITS.md`, one of them finished. The
four that remain in `MANIFEST.md` are now genuinely four whole jars rather than
four jars with a creature roster sitting on top of them.

Both pre-checks were run anyway, against the 176 remaining jars: **nothing
declares `fangs_n_claws` a required dependency**, and **no tag file in any
remaining jar names a `fangs_n_claws:` id at all**. 7,228 tag files scanned.

**The rule this suggests for the two that are left.** `born_in_chaos` has 86
entity keys, 362 items and 133 blocks, and `mob_grinding_utils` has 82 blocks
and no creatures at all. Neither is going to be one module's work either.
Whoever takes them should read the jar's own language file first and split
before starting, rather than converting a roster and discovering the rest
afterwards — which is the order this session did it in twice.

### The fifth prune, 2026-09-26

**GeOre** moved to `mods/_superseded/` in both instances. It has been marked
DONE for some time and stayed in the pack anyway, and the reason is the one
this section is about: `oritech` ships thirty-two compat recipes that assemble
a `geore:budding_x` out of two `geore:x_shard`. Take the jar out and those
recipes name items that do not exist, the game drops them at load, and a
feature is gone with nothing saying so.

Both scripted pre-checks were clean and had been for weeks — **nothing declares
`GeOre` a required dependency** and **no tag file in any remaining jar names a
`geore:` id** (6,961 tag files scanned across 176 jars). It was always the
third thing, the one that is not automatic, that held it.

What unblocked it was noticing what those thirty-two recipes actually *are*:
the only way in the whole pack to get a budding block other than finding one.
`elysium-arcana` ships thirty materials with a full growth chain each and never
had a way to start one. It does now — `BuddingRules`, thirty seedings, paid in
the material's own blocks — and the capability is ours rather than a third
party's, which is the whole programme.

The one thing genuinely lost: oritech's recipes ran in its *assembler*, and the
replacement is a crafting table. A player who had automated budding blocks now
crafts them. `oritech: the machines` is still PLANNED for `elysium-industry`;
when that lands, the assembler is ours and can have the recipe too.

### The fourth thing: the licensing ledger

*Dylan: "During that step, note of licensing needs to be taken, that way if I
ever decide to go from a private to public project I can deal with licensing
appropriately first."*

`tools/retire.py` does the move and, in the same step, writes one row per jar
into `docs/LICENSING.md`: mod id, version, licence and authors as the jar's own
`neoforge.mods.toml` declares them, the licence file it carries, what replaced
it, and the manifest's sentence on what was built (which is where a port or a
redraw of somebody's art is recorded). It also runs the two pre-checks above
itself and refuses a jar that fails either. `--record-superseded` writes rows
for jars retired before the tool existed; `--list` and `--dry-run` change
nothing. The pack is private today; the day it is not, that table is the list
of authors whose terms apply to whatever was derived from their work.

### The third thing, which is not automatic

Other mods ship **integration recipes** against the jar being removed, and
those quietly stop loading. They are not errors and nothing breaks; a feature
simply is not there any more. At the first prune this orphaned:

| Mod | What it lost |
|---|---|
| `productivebees` | azure silver and crimson iron bees, and their centrifuge recipes — they named Silent Gear's ores |
| `create-enchantment-industry` | the whole Apotheosis chain: gem cutter, affix augmentor, dissolving materials by rarity, apotheotic assembly |
| `FarmersDelight` | netherwood cutting |
| `botanypots` | flax and fluffy seed crops |

At the second prune this orphaned exactly one thing: **`ExtendedAE`**'s three
EMC bus recipes (`ex_emc_export_bus`, `ex_emc_import_bus`, `ex_emc_interface`),
which named ProjectE. ExtendedAE is itself PLANNED into `elysium-logistics`,
so the same rule applies — they come back natively when that conversion lands,
written against our own motes, rather than as a shim for a jar that is no
longer in the pack.

Config files left behind by the five (`config/projecte/`,
`config/functionalstorage/`, Ex Deorum's compost colour lists, EveryCompat's
wood-type entries) are read by nothing and were left alone. Apotheosis'
`name_generation.cfg` still names ProjectE gear; Apotheosis went at the first
prune, so that file is read by nothing either.

**These are not patched twice.** Every mod in that table is itself PLANNED —
productivebees into `elysium-agronomy`, create-enchantment-industry into
`elysium-kinetics`, FarmersDelight into `elysium-agronomy`, botanypots the
same. When each is converted its recipes are written against *our* items, and
the integration comes back natively rather than as a compatibility shim for a
jar that is no longer in the pack.

**So the rule is:** when a module converts a mod, it inherits that mod's
integrations with anything already replaced, and the conversion is not finished
until they are written. A row in this table that is still orphaned when its
module is marked DONE is a bug in that module, not a leftover.

## 5. Things explicitly not being done

* **The tech retexture generator** — cancelled by the user. `tools/retexture`
  and `check_retexture.py` stay in the tree as a working tool, because the
  pack it already built is in the user's instance, but no further work goes
  into it and it is not part of this programme.
* **Performance and rendering mods** — sodium, iris, modernfix, ferritecore,
  entityculling, immediatelyfast, ETF/EMF, lithium. They are not content and
  reimplementing them would be building worse versions of them.
* **Pure libraries** — architectury, balm, bookshelf and the rest. They exist
  to be depended on; when the mods depending on them are gone, so are they.
  Anything of theirs we actually need is already in `elysium-lib`.


## 9. The multipliers, and the one that cannot be converted

*Written while converting elysium-atelier, 2026-09-25.*

Four of the atelier's thirty jars do not add blocks of their own. They take
blocks that already exist and make variants:

| jar | weight | what it multiplies |
|---|---|---|
| `chipped` | 13,979 | ~250 cosmetic **texture** variants of each of about 60 base blocks |
| `fusion` | 0 | connected textures |
| `everycomp` | 48 | compatibility blocks between other mods' materials and shapes |
| `DiagonalFences` / `DiagonalWalls` / `DiagonalWindows` | 0 | diagonal **geometry** for fences, walls and panes |

Three of those four have an answer. The fourth does not.

**The Diagonals are geometry, so they are ours to build.** A diagonal fence is
a shape, not a texture, and it drops out of the same shape-family engine
everything else in this module uses. *Built, 2026-09-25:* three kinds in
thirty-three materials, each an unbroken run of overlapping boxes from one
corner of its block to the other — flood-filled and proved to be one piece,
because two boxes meeting at a corner do not meet and a mob walks straight
through the join while it looks like a continuous rail from every angle.

**`everycomp` becomes moot.** It exists to make, say, another mod's stairs out
of a third mod's wood. Once both of those mods are ours, the crossing is a
loop over two of our own rosters, which is what this module already is.

**`fusion` is a resource pack.** Connected textures are a client-side
rendering feature; nothing in it is content a player obtains.

**`chipped` cannot be converted, and this is the reason.** Its whole value is
two hundred and fifty *textures* per block. Replacing it means generating
textures, and there is a standing instruction on this project not to build a
retexture generator. So the options are:

1. **Keep `chipped`.** One foreign jar stays in the pack. The conversion is
   then 130-of-131 rather than 131-of-131, and the thing kept is the one thing
   in the pack that is purely cosmetic.
2. **Drop it.** The pack loses about fifteen thousand decorative variants and
   nobody's existing builds survive.
3. **Reverse the standing instruction** and build a retexture generator.

### The same decision, and three more jars came to it

*Added 2026-09-25, after the shape work was finished.*

`chipped` is not alone. Once every **shape** in the block packs was built —
and it now is: vertical slabs, five ways up, balconies, railings, platforms,
panelling, blinds, board walls, frames, trellises, quoins, banisters,
catwalks, girders, cushions, curtains, rope, and sixteen colours of upholstery
out of the game's own wool — what was left in three more jars was the same
thing `chipped` is, and nothing else:

| jar | what is left after the shapes | weight left |
|---|---|---|
| `bf_blockpack` | ~800 blocks of bricks, tiles, shingles, wallpaper and candlesticks: retexture | ~1,800 |
| `cluttered` | new stone and wood *types* (marble, chalcedony, willow, poplar, sycamore) and sixteen colours of wallpaper: retexture | ~1,500 |
| `Clothing_Of_The_Lowlands` | 112 garments, every one of them a texture | 169 |

They are marked KEEP for the same reason and they belong in the same decision.
Answering it once answers all four, and the four together are about 24% of
everything left to convert.

This is not a decision an agent should take alone: it is 19% of everything
left to convert by content weight, and option 3 contradicts an instruction
given deliberately. **It is the user's call**, and until it is made `chipped`
is marked KEEP with this section as the reason.

Whatever is decided, the other three are ours and are being built.

## 10. Three storage networks, and which two are being deleted

*Written while converting elysium-logistics, 2026-09-25, before any code.*

Three of this unit's jars are whole storage networks:

| jar | model |
|---|---|
| `refinedstorage` | one flat network: a controller, a grid, disk drives, and no limit on how many devices talk at once |
| `appliedenergistics2` | a **channel-budgeted** network: every device costs a channel, cables carry eight, and laying out a base is the puzzle |
| `ExtendedAE` | more devices for AE2's model |

Building all three is building three things that cannot see each other's
items. A player picks one and the other two are a creative-menu tab of dead
blocks.

**The decision: the flat network, and AE2's channels are deleted.**

`elysium-logistics` already is the flat model — `LogisticNetwork`, a
controller, a bus, drawers, a terminal, assemblers and a planner — so this is
as much a statement of what has been built as a choice about what to build.

**What it costs, plainly.** Deleted: channels, cable tiers and P2P tunnels;
the ME controller's face budget; spatial storage; and the CPU-cluster model of
autocrafting. Those are most of what makes AE2 *AE2*, and a player who came
here for the channel puzzle will not find it.

**What replaces the pressure channels create.** A network here is a constant
draw on the grid — `DEVICE_DRAW` FE per device per tick, `MOST_DEVICES` of
them — so a warehouse is a load you have to power rather than a layout you
have to solve. It is a different kind of constraint and it is a smaller one.
That is the honest version: this is a simplification, not a translation.

**What is folded on rather than lost.** AE2's and ExtendedAE's *content* —
terminals, pattern encoding, export and import behaviour, a crafting monitor,
storage tiers — is device content on a network we already have, and it goes on
the flat one. `refinedstorage` is the model we are keeping, so its jar is the
one whose behaviour the tests are written against.

## 11. Six thousand structures, and the grammar that replaces them

*Written while converting elysium-wilds, 2026-09-25, before any code.*

`[Neoforge]ctov-3.6.3.jar` is 6,557 of this unit's 8,825, and it is almost
entirely NBT: hundreds of hand-built templates for villages, towers, camps and
ruins. Three ways to replace it:

1. **Ship the templates as data.** Fastest, and it is not a conversion: it is
   the same files in our folder. Nothing in them can be checked, argued with,
   or changed without a structure editor, and every one of them is a thing we
   would be shipping rather than a thing we built.
2. **Hand-place a much smaller set well.** Honest and small. Twenty good
   structures instead of six hundred, and the pack's world gets quieter.
3. **Generate them from a room grammar.** Rooms, doors, and rules about how
   they may be joined; the generator walks the grammar and lays out a
   building.

**The decision: a room grammar.** It is the only one of the three where the
result can be *proved* rather than looked at, and this repository's whole
argument is that the interesting properties of generated content are the ones
nobody sees by looking:

- **Every room is reachable from the entrance.** A building with a sealed
  wing renders perfectly, generates perfectly, and is a room a player can
  only reach by mining. Flood-filled, the same way a diagonal fence is.
- **Nothing overlaps.** Two rooms in one space is a wall that is not there and
  a chest inside a staircase.
- **Every building fits the box it claimed**, so a structure cannot silently
  spill into the chunk next door.
- **Every grammar terminates.** A rule that can expand into itself forever is
  a chunk that never finishes generating, which is a server that stops.

**What it costs.** Our buildings will be *systematic* where ctov's are
hand-composed: a ctov village has a blacksmith somebody designed, and ours has
a blacksmith the grammar laid out. That is a real loss of character and it is
the price of every property above.

---

## 12. A hundred and forty biomes in somebody else's overworld

`wwoo` is 110 biomes and 367 features; Quark's worldgen half is another 23
biomes and 16 structures. Between them they are the largest single block of
content left, and they share one problem that none of the other jars have:
**the overworld is not ours.**

A dimension of our own — the Vale — is easy. We write the dimension file, we
write the biome files, and every climate in it belongs to us. The overworld's
biome source is vanilla's `minecraft:overworld` parameter list, built in Java
inside `OverworldBiomeBuilder`, and a datapack cannot append to it. The
`multi_noise_biome_source_parameter_list` registry looks like the place to do
it and is not: its codec is `{"preset": "..."}` and names one of the presets
the game already has.

### The three ways in

1. **A region-management library** of the sort TerraBlender provides. It
   works, and it is a third-party jar added to a project whose entire purpose
   is removing third-party jars.
2. **A biome source of our own**, registered in code, wrapping vanilla's list
   and prepending ours. Small file, no new dependency — and a codec we would
   be writing blind, where a mistake is a world that will not load at all.
3. **Override the world preset.** `minecraft:worldgen/world_preset/normal`
   names the overworld's biome source inline, and `minecraft:multi_noise`
   takes an explicit list of entries as readily as it takes a preset name. So
   we write the whole list: ours, then vanilla's.

**The decision: override the world preset.** It is plain data, so
`check_loadtest.py` puts it through the game's own codecs before the jar is
built, and a mistake is a file the loader names rather than a world that dies
on Create World.

### Where vanilla's half of the list comes from

Not typed. `tools/DumpOverworld.java` calls `OverworldBiomeBuilder.addBiomes`
— the game's own builder, from the jar on the classpath — and prints the
7,593 entries it is handed into `tools/data/overworld-vanilla.tsv`. The
generator copies them through verbatim. Nothing about vanilla's overworld is
restated in our words, so nothing about it can drift.

### Why ours can be declared first, and what that settles

Climate is six axes, every entry claims a box, and every column goes to the
nearest box with ties broken by declaration order. Put our entries at the
head of the list and the arithmetic is no longer an argument:

- **A climate inside one of our boxes is ours.** Our distance is the depth
  term and nothing else; vanilla's is the depth term or more; the tie goes to
  whoever was declared first, and that is us.
- **A climate outside all of them is vanilla's**, exactly as it was, because
  we changed nothing about vanilla's entries.

So the thing to prove is not "does our biome generate" — that is arithmetic —
but **what we took**, and the answer has to be a share rather than a hope.

### The share, and what is left of vanilla

Our biomes claim, at the surface:

    continentalness  inland only, from -0.11 up
    depth            0, the surface, as vanilla's own surface entries do
    weirdness        six of vanilla's twelve bands, every other one
    temperature      all five of vanilla's bands
    humidity         all five
    erosion          all five

Which is **47.5% of the inland surface**, 125 ways: five warmths by five
wetnesses by five reliefs, a partition of the region, every cell a biome.
Vanilla keeps the other half, the oceans, the shore, the rivers and the
caves, and **not one of its 53 overworld biomes loses all of its territory** —
checked entry by entry against the dump, not assumed.

Fifteen more sit at depth 1, five warmths by three wetnesses, which is Quark's
cave half.

### What it costs

`wwoo` replaces the overworld; we take half of the land and leave the rest
alone, so a player who knows vanilla still recognises where they are. Our 125
are *systematic* — a cell of a grid rather than a place somebody imagined —
and the price of that is the same price the room grammar pays. What is bought
is that every one of them provably generates, that none of them is a
duplicate of another under a different name, and that no vanilla biome was
quietly deleted to make room.

---

## 13. The apiary, and the ceiling it is not allowed to cross

`productivebees` is the largest jar left in agronomy and the module already
has the half of it that matters: 75 species, combs, comb blocks, nests, and a
breeding tree whose tier is derived from its parents rather than written down.
What is missing is the machinery — the apiaries, the upgrades that go in them,
and the centrifuge that turns a comb into the thing it is a comb of.

### The rule that makes this awkward, and is worth keeping anyway

`BeeRules.HIVE_AT_MOST` says a hive may be worth a quarter of a mine and no
more. It is the same rule crops are held to and it exists for the same reason:
the moment a farm out-earns a mine, the ore ladder three ages are built on
stops mattering. A hive wants no light, no water, no replanting and no
attention at all, so the only honest way to price it is to make it slow.

The trouble is that a bare hive *already sat exactly on that ceiling*. Twelve
bees, one comb every four minutes, sixty units an hour against a mine's two
hundred and forty. Which left no room at all: any upgrade worth fitting would
put a hive over the line, and an upgrade not worth fitting is a recipe nobody
makes twice.

### The decision: keep the ceiling, lower the floor

The ceiling does not move. What moves is where a *bare* hive sits under it. A
hive nobody has built anything for now runs at roughly a fifth of the ceiling,
and the ladder — apiary, advanced apiary, industrial apiary, and the upgrades
that go in their slots — earns the rest of it back. A fully fitted industrial
apiary lands exactly on the line and not a comb over it.

`FIRST_RESOURCE_TICKS` stops being a number somebody chose and becomes the
answer to "what makes the best possible apiary equal the ceiling" — solved on
every build, so retuning the mine moves the whole ladder rather than quietly
breaking the rule at the top of it.

### What the sweep has to cover

Not the best case. *Every* case: four housings, seven upgrades, every way of
filling the slots, every tier of bee. That is the point of an upgrade system
being expressed as arithmetic rather than as a handful of block entities —
"no combination of anything out-earns a mine" is a sentence a harness can
check, and "we think the top one is about right" is not.

### What an upgrade is allowed to be

Two of the seven touch throughput at all: one adds bees, one makes combs come
faster. The other five buy back *attention* — flowers that no longer have to
be planted, combs that no longer have to be collected by hand, bees that no
longer wander off, breeding that happens in the box rather than in a field.
That is what the jar being replaced is actually for, and none of it is
priced against the ore ladder because none of it produces anything.

---

## 14. Create, and the nineteen jars sitting on top of it

Nineteen jars, content weight 5,211, and the weight badly understates it.
Create is not a content mod. It is a physics system — rotational force, a
stress budget, contraptions that assemble a region of the world into a moving
thing, belts that carry items as entities — and the other eighteen jars are
content resting on that system. Counting blocks counts the easy half.

### The three honest options

1. **Keep Create and convert only the addons.** The pack keeps one foreign
   jar; eighteen of nineteen become ours. Cheapest, and it leaves the single
   largest mod in the pack unconverted forever.
2. **Build a reduced kinetic system of our own** — rotation and torque, no
   contraption assembly — and put the addons' content on it. Loses moving
   contraptions, which is a real part of why people install Create.
3. **Drop the kinetics and convert the addons' content onto machinery this
   pack already has** — `elysium-industry`'s grid and `elysium-atomics`.
   Loses the whole feel of the age, and turns eighteen jars of shafts and
   gearboxes into eighteen jars of boxes that take power.

### The decision: (2), and half of it already exists

`elysium-kinetics` already has the system. `KineticRules` is a line of rigidly
coupled shafts, a source described by a free speed and a stall torque, a load
that draws a constant torque, and a line that settles where push equals draw.
Sixteen blocks are built on it: shafts, cogwheels, gearboxes, a hand crank, a
water wheel, a windmill bearing, a millstone, a mixer, a press, a saw.

It is also, deliberately, **not Create's model**. Create gives a source a
fixed capacity and flips the whole network to OVERSTRESSED the moment demand
crosses supply — one comparison to implement, and it reads as a light switch:
everything works, then nothing does, with no warning in between. A linear
speed-torque curve is the same amount of code and behaves like a machine. The
line gets audibly slower as you hang things off it, and the first thing to
stop is whatever needed the most speed, which is a sentence a player can act
on.

So the decision is to finish what is there rather than to start again, and to
take the addons' content onto it.

### What it costs, stated plainly

**No contraptions.** No pistons that move a region, no gantries, no trains
built out of blocks, no flying machines. That is the single biggest thing in
Create and we are not building it: assembling an arbitrary region of the world
into a moving entity, with block entities, inventories, redstone and
collisions intact, is a mod's worth of work on its own and every one of its
failure modes is a corrupted save rather than a wrong number.

A player coming from Create will find the shafts, the gearboxes, the
processing and the stress, and will not find the moving machinery. The pack
keeps trains through `elysium-transit`, which is a different and simpler
promise: a train on rails rather than a train assembled out of blocks.

**Belts carry items, and do not carry players or blocks.** The same reason.

**`createbigcannons` and `create-aeronautics` do not fold in at all.** Both
are built on contraption assembly — a cannon is an assembled structure and an
airship is a flying one — so neither has a home here. They are DROP with a
reason rather than PLANNED against a module that will never take them.

### What each of the nineteen becomes

Written against every row in `docs/MANIFEST.md` rather than only here, because
a decision that lives in one document is a decision the next person re-makes.

| jar | becomes |
|---|---|
| `create` | the kinetic system itself: lines, sources, loads, and the processing blocks |
| `createdeco` | a decoration roster — brass and copper fittings — and it goes to `elysium-atelier` |
| `createaddition`, `create-new-age`, `createdieselgenerators` | the seam between rotation and the electrical grid `elysium-industry` already has: an alternator one way, a motor the other, and a diesel engine as a source |
| `create_connected`, `copycats`, `interiors`, `create-stuff-additions` | quality-of-life and decoration; the useful half is fittings for the atelier and the rest is a config |
| `create-integrated-farming`, `create-central-kitchen`, `sliceanddice` | processing recipes joining the kinetics to `elysium-agronomy`: eleven sawing recipes that are the kitchen's whole knife done by machine, and eight milling recipes that are the seed supply. The saw gives only the main yield — butter without the cream — and `validate.py` refuses a mechanical route that beats the hand one |
| `create_power_loader` | **anchors**: chunk loaders that are loads on a line, two of them, three widths, and the cost per chunk the same at every width |
| `create_ultimate_factory` | **not a machine at all.** Opened and read: two classes and 36 recipe files, no blocks. It is a recipe pack for machines Create already had, so it folds into `ProcessRules` — the nine rows our four machines can do, one thing in and one thing out. The rest was chance drops and multi-input compacting, and a millstone that rolled dice would be a different block |
| `create-enchantment-industry` | **DROP, covered.** The two things it exists for — taking enchantments off an item and moving them onto a book — are `elysium-enchanting`'s Tome of Scrapping and Tome of Transcription, which this pack already has. The third is doing them on a belt, which is contraption work this decision gives up |
| `CreateDragonsPlus`, `create_aquatic_ambitions` | content for `elysium-bestiary` and `elysium-wilds` |
| `createbigcannons`, `create-aeronautics-bundled` | **DROP**: both are contraption assembly, which is the one thing this decision gives up |

### The one thing anything kinetic has to prove

Stress is conserved. What the sources push equals what the loads draw plus
what is idle, on every network, including ones with cycles in them — a network
that gains torque going round a loop is a perpetual motion machine, and a
player finds that long before anybody finds it by reading.

## 15. Lighting a room, and the number that does not belong to the module

*Written while converting `supplementaries: the comforts`, 2026-09-26.*

A decoration module is where a bestiary quietly dies, and this is the one
conversion failure that leaves no trace at all.

The jar has thirty-odd blocks that light a room. Each one is fine. Each one is
prettier than a torch, most are cheaper, and several are brighter. Nobody
decides anything; each block is added on its own merits and each is defensible
on its own merits. What happens next is that light stops being a resource,
which would be a small loss — except that `elysium-bestiary`'s haunts each
name a band of light they spawn in, and almost all of those bands are dark.
A decoration roster in which every fitting is bright and cheap does not make
those creatures rarer. It makes them impossible anywhere a player has
decorated, which is everywhere a player spends time.

Nothing reports that. Every block works. Every room looks good. The pack
simply stops having a night in it, and the symptom reaches the author as "the
mobs feel a bit sparse lately".

**So the rule this row is really about is not in this module.**
`LightRules.STILL_DARK` is seven, and seven is not a taste: it is the
brightest light level at which a majority of `elysium-bestiary`'s own nine
monsters still spawn. At seven, five of nine appear; at eight, two of nine do.
`elysium-comforts/validate.py` works that number out again from
`HauntRules.HAUNTS` on every run rather than trusting the copy, and two
negative tests move it — one from this side and one from the bestiary's — so
that a band widened over there fails the build over here.

The general rule, for the decoration rows still to come:

> A decoration roster that touches a resource another module has built rules
> on is not a decoration roster. Find the module that owns the resource, take
> its number, and check it is still that module's number every run.

Light is the obvious one. Sound is the same shape of problem and
`elysium-comforts`' chimes already answer it. Storage, speed of travel and
anything that shortens a walk are the others.

### Two smaller things this row turned up

**A jar can be a different jar than the manifest thinks.** The pack carries
`supplementaries-1.21.1-3.9.5`; `tools/pack-jars.txt` named `3.8.8`, and
`3.8.8` was sitting in `mods/_superseded` — put there by a version bump rather
than by §4b. Read the jar that is actually in the instance, not the one the
list names, and fix the list. Retiring a jar and superseding a jar look
identical from the folder; only `docs/MANIFEST.md` distinguishes them.

**A hand edit in a generated file is not a decision, it is a countdown.**
This jar's `SPLIT` status had been typed into `docs/MANIFEST.md` directly.
`tools/manifest.py` knew nothing about it, so the next regeneration silently
turned it back into a catalogue row. The status now lives in the `DECISIONS`
table with everything else. If a row in a generated document says something
the generator cannot say, the generator is what needs changing.

## 16. Runs: one language for every pipe, cable, wire, conduit, bus and channel

Dylan, 26 September 2026: a pipe touching a block it feeds should show *a large
flat square on the face touching it, coloured to match the pipe*, as the cue
that the connection is live; and pipes should be dyeable, with *dyed pipes
joining only their own colour, undyed ones, or a block*.

Six modules had a run of their own, drawn six ways: two with arms in the
blockstate, four as a bare cube. All six now go through `elysium-lib`'s
`run` package and `art/runs.py`:

* **The state.** Six `RunLink` properties, one per face: `none`, `run`
  (an arm), `device` (an arm and the plate). `Runs.link` decides a face by
  one rule for everybody: another block of this kind joins if either is
  undyed or both wear the same dye (`RunRules.mayJoin`); anything else is a
  device if the block's own test says so - an energy handler for a cable and
  a conduit, a fluid handler for a pipe, a grid node that is not a wire for a
  wire, any logistics block entity for a bus, a core, a cell or a hall block
  for a coolant channel.
* **The plate.** `RunRules.PLATE` pixels square, `PLATE_DEPTH` deep, on a
  device face, tinted the run's own colour (`Runs.declare`) or its dye.
  `art/runs.py` reads the numbers off `RunRules.java`, so the models cannot
  disagree with the rule, and every module's validator calls its `check`.
* **The dye.** A dye used on a placed run dyes it (one dye in survival, free in
  creative); a run and a dye at the crafting table make the run wearing the
  `elysiumlib:run_dye` component; a picked run keeps its dye; a placed run
  takes its item's. The block entity remembers it (`RunDyed`; a bare
  `RunDyeBlockEntity` for the bus, the channel and the conduit, which had
  none). A dye change re-reads the neighbours, because their link to this
  block depends on a fact that is not in this block's state.
* **The tints.** Body faces carry tint 0 (white or the dye); the plate carries
  tint 1 (the run's own colour or the dye). One colour handler in the library
  serves every declared run.

What a player sees: a copper wire into a crusher ends in a copper square on
the crusher; a red cable and a blue cable cross without joining; a plain pipe
joins both. `RunHarness` executes the rule, `negtest_runs.sh` breaks it
fourteen ways.

### 16.1 The network: what a run carries, and to whom (27 September)

Drawing the links was half of it. The first live test (§17) put a coal
generator, three cables and a water separator in a row and read the
separator after two hundred ticks: nothing. Each cable handed its buffer to
its six neighbours in turn, and the second cable's first neighbour was the
first cable, so the energy sloshed between the two of them and never
arrived. Four of the six runs already walked their network (the solarpunk
grid, the industry grid line, the logistics network, the reactor hall) but
walked it by adjacency, so a red conduit and a blue one, drawn apart, still
powered each other.

* **`RunNetwork.walk`** reads the network off the six link properties: from
  a run, through every face whose link is `run` (checked from both sides),
  collecting every face whose link is `device`. Because the links already
  obey the dye rule, the walk is the picture: a red cable's network ends
  where the red ends. Bounded at 512 runs.
* **Space cables and pipes** carry along that walk: a run hands its buffer
  to the network's devices and never to another run, so only source-adjacent
  runs ever hold anything and the buffer arrives in one tick however long
  the line. A run remembers which faces poured into it this tick
  (`energyFrom`/`fluidsFrom` wrap the capability per side) and never pours
  back out of them, so a tank above a pipe does not get its water straight
  back.
* **The three walks** (`PowerGrid`, `LogisticNetwork`, `GridLine` through
  `WireBlockEntity.connects`) step from run to run only through
  `RunNetwork.joined`. The reactor hall counts channels geometrically and is
  unchanged.
* **`RunBlock` and `placedBlind`.** A run set by a command, a structure or a
  machine arrives with every link `none` (only a player's placement reads the
  world). `onPlace` calls `Runs.placedBlind`, which relinks such a run; and a
  dye that arrives in the block entity's data live (a `/data merge`, a
  structure's block entity tag) relinks through `RunBlock.runDevice`, so the
  library can re-read a run without the block's own methods in hand.

### 16.2 What else the first live run found

Three bugs no validator could see, because each one is a correct-looking
statement that the game answers differently:

* **A tank saved twice.** A space machine whose one tank is both input and
  output (the fluid tank, a pipe's buffer) listed it twice and saved it as
  `Tank0` and `Tank1`; a load that found only `Tank0` emptied the tank again
  reading `Tank1`. Saved once now.
* **Solarpunk power never moved.** Every solarpunk machine spent its own
  power through `extractEnergy`, which a consumer's extract limit of 0
  bounds; both generators filled themselves through `receiveEnergy`, which
  a source's receive limit of 0 bounds. Nothing in the module had ever made
  or spent a unit. The buffer (`SolarpunkMachineEntity.Store`) now has
  `produce`, `canSpend` and `spend` for the machine's own work; the outward
  limits stay what a neighbour meets. Validator §7b, three negative cases.
* **Twenty machines that never produced.** A machine's output slot refuses
  every insert (so a hopper cannot fill the room a product needs), and
  twenty machines asked that same slot for room with
  `insertItem(OUT, result, true)`: the crusher, rolling mill, arc furnace,
  separator, every atomics works, the five kinetics machines, the alloy
  smelter, the refinery, the centrifuge, the hives and the rock generators.
  The pot had the identical bug and agronomy's validator had caught it for
  the pot alone. `ElysiumSlots.roomFor` in the library reads the slot; root
  `check_slots.py` names any simulated insert into an output-named slot in
  any module (`negtest_slots.sh`). The harvester and tree harvester lost
  every harvest the same way, through their out-tray's own refusing
  `insertItem`; `SolarpunkOutput.store` places instead.

* **Water washed the runs away.** The game calls a block with a small
  collision box "not solid" and lets water flow into its place, so a bucket
  poured beside a cable, pipe, wire, conduit, bus or channel deleted the
  line. `Runs.properties` makes every run block `forceSolidOn` (and never
  occluding); `art/runs.py` asks for it, the plates scenario pours water at
  the end of every line.
* **Seven blocks the game refused to place.** The vein rock generators were
  not in their block entity type's block list, so placing one was "an
  unexpected error" to the player. The catalogue scenario (every block set
  once) found it; the type now lists every vein, and agronomy's validator
  checks that it does.
* **A hopper could not feed a press.** A processor asked the recipes about
  the one item in hand, and a recipe that wants four refused it, so no
  hopper could ever feed a mycelium press or a fibre loom. The filter now
  asks about a whole stack (validator §7d).

* **A grid that never re-solved.** An industry grid line re-solves when a
  block changes. A centrifuge wants nothing until a comb arrives, and a comb
  arriving changes no block, so the turbine never lit and the comb sat
  there. `GridBlockEntity.tick` now compares each node's want with the one
  the line was last solved for and unsettles the line when it differs.
* **A centrifuge spinning into nothing.** Its `put` filled the output slots
  through `insertItem`, which those slots refuse; `check_slots.py` now names
  a real insert as well as a simulated one.
* **127 creatures without an egg.** The denizens, the courts, the kindred
  and the warded shipped with an egg's model and name and no egg item;
  `BeastItems.registerRoster` gives each roster its eggs and the bestiary
  validator asks for every roster.

### 16.3 The mechanics pass (27 September, later)

`docs/MECHANICS.md` lists every runtime mechanic of every module with how a
server exercises it; `tools/livetest/scenarios_mechanics.py` runs the ones a
server can reach. Its first passes found, and this fixes:

* **Absorption clamped to nothing.** The game clamps absorption to the
  `max_absorption` attribute (0 unless a potion raised it), so the Praetor's
  field, the bulwark drones, overheal, the stat shield and the barrier rune
  all set 0. `ElysiumAbsorption` raises the ceiling first; root
  `check_absorption.py` names any other call.
* **Relics with passive abilities did nothing** while carried: only the
  active ran on use. `RelicPassives` keeps the passives up through the
  library's own `ElysiumAbilityUse.sustain`, fires periodic ones on their
  clock and the on-hit, on-hurt, on-kill and low-health ones from events.
* **No fauna bred**: a step put every grown animal back on its cooldown
  whenever it was not in love. **No pigeon bred**: there was no breed goal.
  **No pigeonhole fouled**: nothing called the roost; pigeons beside a hole
  now roost every 600 ticks.
* **A herald's court never ended**: copies were heralds with room to call;
  copies are born counted out.
* **Vehicles**: a cactus, a mob or a fire erased a truck and its cargo;
  every destroyer but a creative player now spills it. Nothing could reach a
  hold; sneaking with a stack puts it in, an empty hand takes one out.
* **The reactor hall's throttle** had no setter (sneak-use cycles it). **The
  tree harvester never replanted** because leaves were never cut: the canopy
  comes down with the trunk, so saplings reach the tray.
* **Eight crag biomes never generated.** Their climate boxes were the
  game's own peak boxes, a little wider on one axis; a point inside two boxes
  is at distance nought from both and the game settles the tie by the order
  its tree holds, not the list's, so the peaks won every one. The generator
  now drops a vanilla claim that lies wholly inside a crag's box (150 of
  7 593) and the validator counts and forbids them. A world made before this
  keeps its old list: the test server's world was remade.
* **Every wilds structure stood nowhere.** A room piece built without an
  orientation places its blocks at its own relative coordinates, beside the
  world's origin, so the keep, camp, hamlet, lodge, mine, ruin, shrine and
  watchtower all located and none of them existed. Pieces face north now;
  the wilds validator asks for it and the live test scans a placed keep.
* **Cinder boars turned into zoglins.** The constructor made them immune;
  the hoglin's own load read the saved flag back over it, false for any boar
  summoned or loaded from a chunk. They never convert now, whatever loads.
* **Kitchen crops stood only on farmland**, so the wild patches the world
  grows failed to place on grass; they stand on dirt too.
* **Emberwood logs smelted into the game's charcoal**, not ours: they leave
  `logs_that_burn`. **An axe never stripped emberwood**: the table existed
  and nothing read it. **Trail-ruins relic shards** targeted chest tables
  that do not exist in 1.21.1 (`archaeology/trail_ruins_*` now). **Atelier's
  waterlogged shapes never spread their water** (no fluid tick).

### 16.4 The client stage (27 September, evening)

`stage_mechanics_client.ps1` (now in `tools/livetest/stages/`, with the
`mc.ps1` driver) walks the test client through the mechanics only a player
reaches: a relic's passive, an axe on emberwood, a heavy grade, the reactor
throttle, a cart's hold, capturing, budding and crop growth under a player's
random ticks, a rift key and its portal, and a grave. Screenshots `mech_*.png`.
Its first honest run (the first run was against a client eleven hours old,
which had none of the day's fixes: a stage is only as new as the jar the
client booted with, so the launcher is rerun after every install) found:

* **Every grade did nothing.** The roster names attributes plainly
  (`attack_damage`, `block_break_speed`); the game of 1.21.1 files them under
  `generic.` and the player's own under `player.`, so every lookup returned no
  attribute and a heavy iron sword hit for 6.0, exactly a plain one.
  `ForgeEvents.attributeNamed` tries the plain name, `generic.` and `player.`;
  the arsenal validator now checks every name the roster uses against the
  game's own attribute list (from the vanilla jar's lang file when `MC_JAR` is
  set) and refuses a plain-name-only lookup. Two negative cases.
* **The hoar hill never generated** for the same reason as the crags: a
  wide box of ours holds a dozen of the game's cold claims whole, every point
  inside is a tie, and the game's tree gave every tie to the game. The shadow
  rule now runs for every biome of ours, not the crags alone: 2 230 of
  vanilla's 7 593 claims lie wholly inside one of our boxes and give way. A
  box of ours is where that biome is; the validator's own model had assumed
  as much all along. Worlds made before this keep their old list.
* Verified as built: the wanderer's charm keeps `minecraft:speed` up while
  held and it lapses within 45 ticks of putting it down; an axe strips
  emberwood (and oak, as the control); a three-cube hall forms and its port's
  throttle steps 512 → 1024 → 1536 FE/t on sneaking uses; a cart takes 32
  cobblestone from a sneaking hand and hands it back to an empty one; a
  capturing III sword dropped a cow spawn egg from eight cows; a rift key
  lights a 2×3 frame and a player standing in the portal for a few seconds
  arrives in a dungeon room (a walk overshoots a one-block portal); a grave
  holds five gold ingots and twelve levels of experience with nothing on the
  floor; budding iron grew the whole chain to 25 clusters under fast random
  ticks; 49 onions all left age 0. (A corner crop that vanished on placement
  vanished for vanilla wheat too, so it is the rig, not the pack.)

`stage_firstjoin_client.ps1` then remade the client's world and joined it as a
new player (POLISH.md P3). The kit and the declaration screen are as designed;
the stage found:

* **A grave in deep water washed away.** The headstone is a 12×14×8 box, the
  game lets water flow into any block that is not solid, and a grave made
  where a player drowned stood for a few seconds and then spilled everything
  to the sea floor, its "your things are at" message pointing at open water.
  The grave is declared solid (`forceSolidOn`, as the runs already were); the
  comforts validator requires it and a negative case removes it. The other
  partial-shape inventory blocks in the pack are all runs, which are solid.
  Rerun on the fix: three gold ingots lost to the same water read back out of
  the grave's `Held`, nothing floating.
* The test rig's reference world spawns at an ocean floor under the wilds'
  biome list, so the new player drowned reading the screen; that is P4's
  stale reference world, not the pack. A Graftguard then killed the new
  survival player twice within seconds of standing on land.

`stage_screens_client.ps1` opened every menu block once (POLISH.md P14). It
photographed five screens drawing over themselves and five boxes on the
game's grey chest screen:

* **Screens too short for what they held.** The arcane table's four stat
  lines ran over its two slots and its three offers over the inventory rule;
  the machine screen's energy line ran under the fuel slot; the scriptorium's
  buy line lay across the inventory rule; the smithing table's part list
  covered its input slot and the inventory heading; the salvager's button sat
  on the rule. Each has sixteen more pixels above the inventory now (182
  tall, `INVENTORY_Y` 100), the machine's slot is at the right edge, the
  smithing input is right of its list, and the scriptorium's line is cut to
  its button.
* **Boxes outside the theme.** Cabinets, the tackle box, the freight
  terminal, packs and the per-player loot chests opened
  `ChestMenu.threeRows` or `MenuType.GENERIC_9xN`: the game's menu types and
  so the game's grey screen, which `check_ui.py` could not see because its
  rule is about screens we write. The library now declares a chest menu type
  per row count (`ElysiumChests`, one to six) under the kit's
  `ElysiumChestScreen`; every box goes through it, the module validators
  that had pinned the game's menu pin the library's, `check_ui.py` refuses
  the game's, and negative cases cover both.

### 16.5 Creature body plans, the first slice (27 September, night)

P10 begins. The eight Elysium mobs keep their models; everything else gets a
plan of its own at their density. `art/bodyplans.py` builds a plan from a row
of proportions with the organic primitives the eight use (chains, ovoids,
plates, limbs, digits) laid along a spine turned a quarter turn, so a torso
lies down instead of standing up: a quadruped, a knuckle-walking hulk, a
stilt-legged insectile walker. `art/creature.py` emits the Java model with a
real part tree, measures the bind pose through the hierarchy for the
renderer's hitbox scaling, and builds the skin (materials by part role,
accents on the visor, the leading shells and the claw tips). The bestiary
generator dresses its nine beasts in the three plans and paints every beast's
sheet from the same rows; its validator wants a class per family, a hundred
boxes and a part tree in it, published bounds, a registry entry, a delegating
BeastModel and a sheet of the packed size; three negative cases. Each plan
was rendered headless through the mobs' PoseCapture and preview.py before
it shipped, and the first quadruped's head floated a body-length away until
the torso was laid along a turned spine — which is the whole reason the
render loop exists.

The same night the rest followed: the twelve animals (two quadruped builds,
a winged plan, a fish), the kin, court, denizens, wards, myths and companions
(by the form each rules class already names per row, with the roster's
colours as base and its mark as accent), the pigeons (a small winged plan
whose wings beat off the ground and whose legs tuck), the ninety-six bee
species (an insect: six legs, two pairs of wings beating off each bee's own
age) and the hundred and five fish (thirty boxes, scaled to their length),
and the shade (a hooded floater, translucent, reaching when it hunts). Seven
plans, eleven generated model classes, some four hundred sheets, every one
from the rows it is drawn with; each module's validator asks for the
generated class, its density, its published sheet size and the sheets to
match. The space mobs are Stellaris's own ported models and keep them.

## 17. Live tests: the pack running, asked questions

Dylan, 27 September 2026: *validation should include testing pipes and
cables, testing power generation, making sure crop farm machines actually
farm crops and generate items, testing I/O of all block inventories.*

`tools/livetest/livetest.py` runs against the dedicated test server over
RCON. Each scenario in `tools/livetest/scenarios.py` builds a rig with
commands on its own plot of a force-loaded stage far from spawn (9000, 200,
9000; plots 24 apart), sprints the tick clock (`tick sprint`), and reads
the result back with `/data get` and `/execute if block`. A scenario passes
or says what it saw, in the words a player would use.

    python3 tools/livetest/livetest.py [--only cable] [--verbose]

Twenty-one scenarios: every run's plates and arms (and water poured at the end of each line); the dye rule; energy across a
cable, water across a pipe, a red cable refusing a blue one; the industry
grid; the solarpunk condenser, conduit, harvester and tree harvester; the
logistics bus; each producing machine of industry, kinetics, foundry,
agronomy and solarpunk making its product from a hopper's input; and every
hopper-fed inventory in the pack taking its input; and the catalogue, which
reads `docs/features.json` (`tools/catalogue.py`, the feature catalogue of
`docs/FEATURES.md`) and sets every block once, gives every item once and
summons every creature once, so a block the game refuses is a named
failure rather than a player's surprise. Random ticks do not
happen in a force-loaded chunk without a player, so crop growth and pots
stay in the client stages (PLAYTEST.md).

What it cannot see: the client. Plates, tints and dyed items are checked by
eye in `stage_runs_client.ps1`. Atomics machines need a high-voltage source
and are fed by the inventory scenario only.
