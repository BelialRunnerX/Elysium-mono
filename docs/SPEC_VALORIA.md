# Elysium-valoria: porting Valoria, then making it the Cordoned Planet

Requested 24 Sep 2026. Take Valoria (Dylan's own NeoForge 1.21.1 port of
IriDark's Forge mod), give it a module of its own, and redesign every part of it
from high fantasy into Elysium's science fiction: sci-fi and space instead of
magic and crypts. Reference: `docs/reference/valoria_inventory.md`.

## Rulings

- **Base: Valoria 1.21.1-1.0.4.2**, the NeoForge port at
  `C:\ClaudeValoria\Valoria` (github.com/BelialRunnerX/Valoria-NeoForge, made
  with the original author's permission). Code GPL-3.0, art CC BY-NC 4.0, for
  personal, non-commercial use (Dylan). Credit is in
  `elysium-valoria/CREDITS.md`; the module's licence line says so, the way
  elysium-space's does for Stellaris.
- **One module, not four.** This overturns the manifest's earlier ruling that
  Valoria is split by kind across arsenal, bestiary, trinkets and wilds
  (Dylan, 24 Sep 2026). Mod id `elysiumvaloria`, separate jar, bundled into
  elysium-all. Depends on elysiumlib, and on Curios only for the accessories,
  exactly as elysium-trinkets does. **No Tridot, no GeckoLib**: everything
  Valoria took from either is replaced (table below). The validator enforces
  that no class imports `pro.komaru` or `software.bernie`.
- **Reimplementation on the library, not repackaging.** Every mechanic is
  written against elysiumlib's systems where one exists — elements, hazards,
  gear and sets, sockets, standing, bestiary, research, rooms, the UI kit —
  and added to the library once where none does. The rule from
  `docs/CONVERSION.md` §1 holds: where Elysium already has an equivalent, the
  plan merges rather than duplicates.
- **Full redesign, not a reskin.** Names, lore, art, biomes, structures and the
  fiction of every mechanic change. Mechanics are kept where they are good and
  reworked where the fiction demands it, never neutered to pass a check.
- **Ids are Elysium names from day one** (as elysium-space). Nothing carries a
  Valoria id, so the reskin is never a migration.
- **All art is ours, drawn fresh.** Every sprite, block face, entity texture,
  GUI element and structure is produced by this module's generators in the
  Voidforged style (`art/style.py`), following the shade-map and box-model
  tooling the other modules use. No Valoria pixel, model, NBT template or audio
  file is copied. Until an asset is drawn, the thing it belongs to does not
  ship (no placeholder borrowed art).
- **The world is the Cordoned Planet.** Valoria's dimension becomes a sixth
  Elysium Space destination, `elysiumvaloria:cordoned`, reached by rocket and
  waygate like the other five, not by a portal of its own. Its fiction is the
  lore's Cordoned Planet: the former site of the Empire's clone and virus
  projects, glassed to erase them, and now a Nullspire containment zone.
- **Magic is Nullspire anomaly science.** Souls are *Null residue* harvested
  from anomalies; Nihility is *Null saturation* on the Void element; spells are
  psionic and neural technology; necromancy is reanimation by a rogue
  containment intelligence. Every Valoria item, mob and block is reread through
  that lens in the mapping tables below.
- **GUI stays the Elysium kit.** Every screen is an `ElysiumUI` screen and
  every colour comes from `ui/palette.py` (`check_ui.py`). No new HUD chrome:
  Null saturation is shown through the existing hazard telemetry line.

## What Valoria took from its libraries, and what replaces it

| Valoria needs | Used for | elysium-valoria uses instead |
|---|---|---|
| Tridot screen shake | boss attacks, abilities | a `telegraph` in the lib (the E7 wind-up already announces heavy blows); no camera shake |
| Tridot particle types, screen particles, post-processing | 16 particle types, ability trails, portal shader | vanilla particle types with our own textures; no post-processing; no custom shader (the portal is gone) |
| Tridot `DotStyle` text, custom font | animated tooltips, GUI text | `ElysiumUI` text, the lib's tooltip lines |
| Tridot boss bars | four bosses | vanilla `ServerBossEvent`, coloured by element |
| Tridot animation states, models | entity and armour models | box models from `art/boxmodel.py`, the `RiggedModel` elysium-space drew its mobs with |
| Tridot item skins | 12 cosmetic skins | dropped: one Elysium look per item |
| Tridot music modifiers | biome and dungeon music | the dimension's and biomes' own `music` JSON; structure music later |
| Tridot percentage armour, set bonuses | armour sets | `ElysiumGear` element sets and the lib's `set` package |
| Tridot builders (armour, curio, katana, scythe, spear, talisman) | registration | our own registration loops over rules tables |
| Tridot `BookHandler` codex | the Codex | the Imperial Codex's Research page (discoveries) and an Almanac chapter |
| Tridot `AttackSystemMob`, `AbstractBoss`, goals | boss AI | our own goals on vanilla `Mob`, on the lib's telegraph and elite hooks |
| Tridot `BaseSpawner`, trades, commands | structure spawners, merchant | vanilla spawner data, vanilla trades, the lib's command patterns |
| GeckoLib | the fire boss | a box model like every other Elysium mob |
| Curios | accessories | still Curios, through the lib's trinket hooks (as elysium-trinkets) |

## Elements, hazards and the two meters

Valoria's four elements and its Nihility map onto Elysium's five-element ring
and hazard pipeline. Nothing is special-cased: a Cordoned item is an Elysium
item of that element.

| Valoria | Elysium element | Why |
|---|---|---|
| infernal / magma | **Plasma** | heat and fire are Plasma's hazard (thermal) |
| aquarius / depth | **Dimensional** | pressure is Dimensional's hazard |
| nature | **Neural** | growth, venom and life-shaping are the neural/biotech side |
| void / nihility | **Void** | the Null field is Void, as vacuum and radiation are |
| jade, cobalt, crimtane, pyratite (physical metals) | **Kinetic** or inert | struck metal; pyratite (explosive) is Kinetic |

**Null saturation** replaces Nihility. It is a new hazard, `elysiumvaloria:null`
on the Void element, sourced by the Cordoned Planet's field through the same
`ElysiumHazardField.registerSource` hook elysium-space uses for vacuum, so
wards, protection arithmetic, rooms and claims all answer it without a line of
special code. On top of the hazard sits a *saturation* gauge, a player
attachment that integrates unblocked exposure:

| Rule | Value (from Valoria, kept unless noted) |
|---|---|
| accrual | every 5 s of exposure, `(1 − protection) × difficulty × 0.5` points; paused on Peaceful and under a Null ward |
| decay | 1 point per 6 s off-world (×2 on Easy) |
| ≥ 50 % | periodic Void damage, `1 + (ratio − 0.5)/0.1 × multiplier`, telegraphed per E7 |
| ≥ 75 % | the field speaks: the anomaly's heartbeat sound; Blindness if configured |
| ≥ 95 % | **collapse**: the player is reclaimed by the field (death type `null_collapse`), or, if the Nullspire failsafe is configured, extracted to their respawn point with the gauge cleared — Valoria's KILL / TELEPORT, as a server config |
| readout | the hazard telemetry line and the Research page; the *Null Monitor* trinket warns aloud |
| counterplay | Void-element gear (set seals), the Null ward ration, sealed rooms, the *Residue Vessel* trinket, the *Nullward* slate trinket, purifying consumables; eating raw Cordoned food adds saturation |

**Null residue** replaces souls: a resource carried in the *Residue Canister*
(soul collector), harvested when anomalies die near the carrier, spent at the
*Residue Infuser* and by the *Nullblade* (whose overload state is Valoria's
rage mode). Same counts (50 base, 50 max), same offhand collection rule.

**Plasma charge** replaces magma charge: a gauge on Plasma weapons that
refills over time and drains in lava or while burning, spent by the *Plasma
Eruption* ability of the plasma greatsword. The immunity charms keep their
timing under new names.

## The Cordoned Planet

Sixth row of the `SpaceWorld` table (S1 in `SPEC_SPACE.md`): 0.92 g, thin
breathable air (no suit needed on the surface), permanent dusk (fixed time
22000, as Valoria), hazards **null 1.0** everywhere and **radiological 0.4**
in the glassed wastes. Reached by tier-2 rocket range and by a waygate. Sky:
our own `DimensionSpecialEffects`, a low violet sun behind ash.

| Valoria biome | Cordoned biome | Character |
|---|---|---|
| shade_forest | **Ashveil Canopy** | engineered forest that kept growing after the glassing; ashveil trees, spiders and pickers |
| shaded_ecotone, shaded_barrens, ecotone, barren_ecotone | **Cordon Verge** (two variants) | the boundary where forest gives way to glass; scavenger drones, brute strains |
| void_barren, void_desert, void_hills, void_peaks, void_shore | **Glassed Flats / Wastes / Ridges / Spires / Shore** | vitrified ground, nullstone, dormant lattice crystals; scorpions and brutes |
| coast, ocean_wastes, river | **Still Shore / Dead Sea / Dead Channel** | lifeless water; no hostiles |
| meaty_lands, meaty_caves | **Flesh Blooms / Bloom Hollows** | the virus project's overgrowth; strains and cyst wardens |

Three woods (shadewood, eldritch, dreadwood → **ashveil**, **nullgrove**,
**marrowwood**), sixteen stone families renamed to planetary and Imperial
stone (void stone → **nullstone**, umbral → **cordon plate**, unbreakable
containment alloy), the flesh set, the plants, the pots, the traps and the
graves all come across under new names in `valoria_inventory.md`'s mapping
column. Ores keep Elysium's rule: one ore per material in every stone, through
`elysium-ores`'s tables, so jade, pearlium, cobalt, amber, sapphire, ruby,
pyratite and wicked amethyst (→ **null amethyst**) become ore rows there, not
private ores here.

## Creatures

Every creature is an anomaly, a failed project, or the machinery left to guard
them. Each registers into the lib's bestiary so it scales to level, Favor and
Suspicion, and each is drawn as a box model. Stats and rewards go through
`elysium-bestiary`'s reward ladder (chosen stats, derived rewards), extended to
bosses.

| Valoria | Cordoned | Notes |
|---|---|---|
| undead, draugr | **Reclaimed**, **Hollow Sentry** | clone subjects reanimated by the Reanimator; the sentry keeps its shield |
| scourge, swamp wanderer, maggot | **Blight Husk**, **Mire Strain**, **Larva** | virus strains |
| corrupted, flesh sentinel, flesh cyst | **Flesh Strain**, **Cyst Warden**, **Bloom Cyst** | the overgrowth |
| goblin | **Picker** | small salvage vermin; collects, hides, flees the light |
| troll, corrupted troll | **Brute Strain**, **Glassed Brute** | |
| shadewood spider | **Loom Spider** | |
| wicked scorpion | **Null Scorpion** | acid spit becomes a null spit |
| scavenger | **Carrion Drone** | a creature-class salvage drone |
| sorcerer | **Null Acolyte** | Unsworn cultist psion; casts and heals |
| devil (Nether) | **Ashborn** | keeps its Nether spawn |
| ent, nature golem, river golem (Overworld) | **Rootwalker**, **Terraformer Frame**, **Sluice Frame** | feral Imperial terraforming constructs |
| king crab | **Shell Carrier** | |
| pixie, wicked shield, crystal | **Mote**, **Lattice Shard**, **Lattice Node** | boss minions |
| haunted merchant | **Quarantine Broker** | an Unsworn smuggler; vanilla trades |
| mannequin | **Training Frame** | |
| **Necromancer** | **the Reanimator** | rogue containment intelligence in a walking chassis: drone bolts, EMP pulse (knockback + Weakness), self-repair, reanimation (summons Reclaimed, Hollow Sentries, Acolytes), conversion; summoned at the **Containment Altar** with a **Reanimator Core** |
| **Dryador** | **the Terraformer** | a feral world-engine: seed-pod barrage (acorns), stomp, radial pods that poison, motes in phase two; summoned in the Overworld with a **Terraforming Key** |
| **Firron** | **the Forgeborn** | a plasma war-frame: rush, sweep, piercing dash, triple sweep; phase two at half; summoned in the Nether with a **Slag Core** |
| **Wicked Crystal** | **the Null Lattice** | a crystalline anomaly: pull, shard ring, lattice nodes, radial burst, lattice storm in phase two; summoned at the **Lattice Altar** in the **Containment Spire** |

## Gear

- **Armour (14 sets)** become `ElysiumGear` sets with an element and a tier,
  so ascension, sockets, affixes and the set rules already apply. Set bonuses
  are kept where they are rules (explosion immunity, water breathing, night
  vision, the on-hit debuffs) and dropped where they were only Nihility
  resistance, which the element now provides.
- **Weapons** keep their kinds — greatswords, katanas (dash), scythes
  (radial), spears (throw and return), rapiers, daggers and throwables, bows
  and crossbows, shields — and every kind is placed on elysium-arsenal's
  domination axes so that no Cordoned weapon dominates an arsenal weapon of its
  tier or is dominated by one. The harness proves it over the union of both
  catalogues.
- **Abilities** are the reason this module exists before the arsenal's uniques
  do: Valoria brings a proven set — dash, radial strike, throw-and-return,
  charged strike, summon, launch-and-stun, overcharge, hook, mark — and
  `docs/TODO.md` §6.4 asks for the ability system to be built **once, in the
  library**. Phase V1 builds `elysiumlib`'s `ability` package from these, with
  cooldowns, charges, a data component for ability state, a cast packet, an
  overlay through the existing HUD, and a pure rules class the harness sweeps.
  elysium-arsenal's uniques and elysium-arcana's relics then use the same
  system.
- **Accessories (≈110)** become Elysium trinkets under the found/crafted rule:
  the reactive ones (eye, sun, moon, celestial necklaces; the residue vessel;
  the monitor; the bloodsight lens; the hook) are *found* and change a rule;
  the tiered stat necklaces, rings, gloves, belts and talismans are *crafted*,
  lose their iron/gold/netherite tiers, and **ascend** instead, which is what
  those tiers were.
- **Enchantments**: bleeding is already the library's; the module ships
  *accuracy* and *explosive charge* as data enchantments on its own items.

## Stations and recipes

| Valoria | Cordoned | Rule |
|---|---|---|
| kiln | **Ceramic Kiln** | kept; the pots, bricks and cooked food |
| stone crusher | merged into `elysium-kinetics`'s **Crusher** | gem dust recipes are added there; no second crusher |
| jeweler table | **Lattice Bench** | the crafted trinkets and their ascension |
| keg | **Distillery** | the drinks; alcohol keeps the *tipsy* trade-off |
| soul infuser | **Residue Infuser** | spends Null residue |
| elemental manipulator | **Element Lattice** | cores of each element make that element's gear; the 84 recipes become one rules table × five elements |
| alchemy station (4 tiers) | folded into the **Residue Infuser** (runes → slate trinkets) and vanilla brewing (consumables) | no fifth station |
| heavy workbench | **Fabrication Bench** | multi-part crafting for large items |
| cryptic / wicked altar | **Containment Altar**, **Lattice Altar** | boss summons |
| elegant pedestal | **Display Plinth** | kept |

Recipes are generated from tables, not hand-written: the 989 JSONs become a
few hundred rows the generator expands, and `check_datapack.py` proves every
reference resolves.

## Structures

Valoria's NBT templates are its art, so they are not copied. The crypts become
**Containment Vaults** assembled from generated rooms the way elysium-dungeons
builds rifts; the fortress becomes the **Containment Spire**; the monstrosity
bones become **Project Remains**; the small ruins become **Cordon Outposts**;
the magmatic halls become the **Slag Works** in the Nether. Spacing goes
through `WildRules.Spacing`, so nothing is met never or constantly.

## Consumables, drinks, food, rot

Healing and cleansing vials become **stims** and **purgatives** (the purgative
also drains saturation); the bandages stay; the nineteen drinks and the
distillery stay; the rot rule stays as **contamination**: unsealed food on the
Cordoned Planet degrades in three stages.

## Codex and progression

The codex's fifty unlockables become Research discoveries and Almanac steps:
mobs and bosses are catalogued by the Field Scanner (B1), materials by
Geology, the planet by Rift Science, and the Almanac gains a chapter **The
Cordon** with the steps that lead a player from the Overworld to the Spire.

## Phases

Each phase follows the working mode: architect, pure rules, harness, game code,
generator, validator section, negative tests, root validation, build, ship.

| Phase | Content | Status |
|---|---|---|
| V0 | module scaffold, credits, inventory, this plan, the registration in every module list, a jar that loads with the pack | DONE 24 Sep 2026 — `elysium-valoria` compiles, merges as the 29th mod, its validator and `CordonHarness` (300,146 checks: the ring covered, the field cannot touch a sealed player, hurts only past half, collapses nobody in under a minute) pass, and the pack boots into a world with "Elysium Valoria ready" in the log. Built on Windows with the PowerShell ports of compile.sh/packjar/mergejar (`D:\testingbuilds\Elysium\tools`). |
| V1 | the library's ability system (rules, harness, component, packet, overlay), proved on one weapon of each kind | NEXT |
| V2 | the Cordoned Planet: world table row, hazard source, Null saturation, biomes, stone and wood sets, plants, ores through elysium-ores, sky, rocket and waygate reach | |
| V3 | gear: fourteen element sets, the weapon catalogue on the domination harness, ranged, shields, throwables, plasma charge | |
| V4 | stations and recipes: kiln, lattice bench, distillery, residue infuser, element lattice, fabrication bench | |
| V5 | trinkets: found and crafted, ascension in place of tiers, the monitor and vessel | |
| V6 | creatures: the twenty-two, the minions, the broker; then the four bosses on the reward ladder | |
| V7 | structures: vaults, spire, remains, outposts, slag works; altars and summons; locators | |
| V8 | consumables, drinks, food, contamination | |
| V9 | research, almanac chapter, discoveries; JEI categories through the pack's JEI | |
| V10 | art complete for everything above, sounds, polish; Valoria and Tridot rows to DONE and the jars moved to `mods/_superseded/` | |
