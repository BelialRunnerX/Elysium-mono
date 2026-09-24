# Elysium roadmap (set 22 Sep 2026, reordered the same day)

Dylan's order, top to bottom (reordered 22 Sep 2026: finish Elysium Space, then the
whole Apotheosis and Silent Gear conversion, then the rest in the order first given). Each block lists what the work theoretically needs,
the decisions already made, and what stays open. Each phase gets its own SPEC_*.md
when it starts, the way SPEC_CLONE and SPEC_SPACE did. Working mode is unchanged:
architect one feature, implement, validate (validators, harnesses, negative tests),
then the next. Everything stays modular (separate jars merged into elysium-all). Dev
jars go to `Instances/Claude-dev-build` only; the `V+ and Tech*` instances are never
written.

## Licenses (checked 22 Sep 2026; "the git page is the most accurate")

| Project | License per its GitHub | Consequence |
|---|---|---|
| Stellaris 1.21 (github.com/st0x0ef/Stellaris, branch 1.21), the version ported | CC BY-NC-SA 4.0 | elysium-space stays CC BY-NC-SA, credited, non-commercial. (github.com/st0x0ef/stellaris-2.0 is MIT, but that is a separate rewrite for Minecraft 26.1; nothing was taken from it.) |
| MineColonies (ldtteam) | GPL-3.0 | An add-on that calls its API is the practical route; a rewrite into Elysium would be a GPL derivative of a very large codebase |
| Curios (TheIllusiveC4) | LGPL-3.0-or-later | Can be repackaged unmodified inside elysium-all (jar-in-jar), with its license text and a pointer to its source |
| Create | code MIT, **assets All Rights Reserved** | We may not ship recoloured copies of Create's art; retextures must be generated locally from the installed jar, or drawn fresh |
| Other tech/storage mods | per mod, mostly ARR assets | Same rule as Create unless a mod's assets are openly licensed |

## 1. Finish Elysium Space (docs/SPEC_SPACE.md, S4 to S8)

- S4 travel: rocket entity (tiers), launch pad, fuel, launch sequence, planet
  selection screen, landing. The `/elysiumspace travel` command stays for testing.
- S5 suits: suit set + oxygen tank (a carried oxygen supply clears vacuum), tank HUD.
- S6 machines, fuel, ores, structures, mobs (4 Stellaris mobs), space stations. The
  distributor starts using oxygen and energy (NeoForge capabilities, no Potentials).
- S7 skies: our own `DimensionSpecialEffects` in place of Sky Aesthetics.
- S8 reskin: all ported art redrawn in the Elysium palette, names and lore.
- Carried into colonies (below): pressurised colony buildings and corridors.

## 2. The full Apotheosis and Silent Gear conversion (docs/SPEC_CLONE.md, P1 to P12)

Moved up by Dylan: all of it, not just P0. That includes every block and item the
two mods add, rebuilt in `elysium-armory` (Silent Gear) and `elysium-ascendancy`
(Apotheosis) with no dependency on either:

- P1 combat model (the 20 attributes doing their jobs),
- P2 materials and parts, including **harvest tiers 0 to 4 and the metal ladder**
  (bronze and blaze gold at iron level; crimson iron and azure silver at diamond;
  crimson steel, azure electrum and tyrian steel at netherite),
- P3 part-built gear and the Gear Smithing Table,
- P4 the item-effect engine,
- P5 rarities and world tiers,
- P6 crafting loops: Salvaging Table, Reforging Tables, Salvager,
- P7 gems, purities, sockets, Gem Cutting and Augmenting Tables,
- P8 materials science: Alloy Forge, Recrystallizer, Material Grader,
- P9 mobs, structures and world content, **including the crimson iron and azure
  silver ores**,
- P10 enchanting, P11 spawners, P12 polish and compat.

Where Elysium already has an equivalent (runes, Ascension Forge, reforging, its own
metals), the plan marks it partial and merges rather than duplicates. The seven open
decisions in SPEC_CLONE are asked when their phase starts (world tiers before P5).

## 3. Living colonies: `elysium-colonies` (MineColonies add-on)

Goal: worlds feel lived in and grow by themselves.

**Route: an add-on**, not a rewrite. MineColonies + Structurize + BlockUI + Domum
Ornamentum are thousands of classes and GPL. An add-on module requires `minecolonies`
and uses its API; everything Elysium-specific lives in our jar. Revisit only if the
API cannot do something below.

What it theoretically needs:
1. **Natural spawning in worldgen.** Custom `Structure`s (jigsaw or a placer) that
   drop a MineColonies town hall plus buildings from a style pack, per dimension.
   - *Abandoned*: blueprints placed as ruins (decay processor, loot, no colony data).
   - *Inhabited*: after generation, register a real colony (`IColonyManager`) owned
     by no player (an NPC "steward"), spawn citizens, and assign the placed buildings.
2. **Growth without players.** A server-side colony "director" on a slow tick:
   - picks the next building or upgrade from a per-theme growth plan,
   - completes the work order by placing the blueprint in stages (Structurize's
     placer), rather than waiting for a builder with resources,
   - adds citizens as housing allows, and raises happiness and the colony level.
   It must be throttled and chunk-aware (only in loaded chunks, or simulated on load
   from elapsed time).
3. **Elysium jobs as buildings.** The five elysium-npcs professions (and later ones)
   become MineColonies buildings with custom modules (hut blocks, blueprints, job
   AI or a trade-hall module).
4. **Auto-detect other mods' villager professions.** Scan the
   `VillagerProfession` registry at load; for each modded profession with no mapping,
   register a generic "guild hall" building whose worker offers that profession's
   trades (from its trade tables). A config or datapack can map a profession to a
   dedicated building instead.
5. **Themes per world (style packs).** Structurize style packs (blueprints) shipped
   in our jar and chosen by dimension:
   - Emerald Bastion: **black and emerald empire**, gothic fantasy sci-fi.
   - Every other world themed on its environment (pale moon regolith, cinderveil
     ash/sandstone, frostfall ice/rust, orbit station modules, Overworld biomes).
6. **Connections.** Bridges, covered walkways and sealed corridors between
   buildings, so a whole colony can be one `ElysiumRoomScan` room with an Oxygen
   Distributor (space worlds). That needs:
   - a path planner between building doors,
   - corridor blueprints per theme,
   - airlock pieces (doors both sides),
   - a validator rule that corridors connect sealed.
7. Validation: blueprint files parse, every theme covers every building type used by
   the growth plan, and corridors are sealed on airless worlds (a harness over the
   blueprint voxels).

Open decisions: how fast colonies grow; whether players can take over an NPC
colony (and how); raider behaviour against NPC colonies.

## 4. Curios inside the Elysium jar

- Repackage Curios unmodified as a jar-in-jar of elysium-all (NeoForge JarJar, with a
  version range so a separately installed Curios wins cleanly). Ship its LGPL text
  (`COPYING`, `COPYING.LESSER`) and a source link.
- elysium-trinkets keeps using the Curios API as today.
- Only if repackaging proves impossible (it should not be): our own slot system,
  a large job.

## 5. The Elysium retexture of tech and storage mods

Scope in the dev instance, counted from the jars: about 7,700 textures in total:

| Mod | Textures |
|---|---|
| Create | 1,307 |
| Immersive Engineering | 1,191 |
| Mekanism (+ Generators and Tools) | 1,079 |
| Refined Storage | 708 |
| Applied Energistics 2 | 655 |
| Create Deco | 589 |
| Industrial Foregoing | 420 |
| Create Big Cannons | 381 |
| Sophisticated Storage and Backpacks | 454 |
| Create Diesel Generators | 320 |
| Storage Drawers | 271 |
| Create Stuff and Additions | 186 |
| Create Crafts & Additions | 132 |
| Functional Storage | 95 |
| Create New Age | 86 |
| smaller Create addons, Dank Storage | ~ 200 |

Rules: **recolour, never reshape.** Same texture sizes and UV layouts, same models,
nothing new in the world's meshes.

- Because most of these assets are All Rights Reserved, the shipped thing is a
  **generator**, not the art. `tools/retexture` reads the mods installed in an
  instance, maps every texture into the Elysium palette (luminance-preserving
  palette mapping, with per-mod material rules for metal, glass, wood and emissive
  parts, and hand overrides where the automatic map is poor), and writes a local
  resource pack (`Elysium Tech Reskin`). Nothing of theirs is redistributed.
- Animated textures (`.png.mcmeta`) keep their frames; emissive and overlay layers
  keep their alpha.
- Validation: every texture in the pack has the source's size and alpha mask; every
  colour is from the Elysium palette (with emissive exceptions); coverage report per
  mod.
- Later: hand-drawn Elysium art for the most visible pieces (machines, cogs, casings).

## 6. Elysium ores for every supported type

Built on the metal pipeline from step 2 (P2 materials, P9.9 ores): one ore,
ingot and tier definition format serves both the Silent Gear metals and these.

Ore tags present in the dev instance: uranium, nickel, lead, zinc, tin, silver,
platinum, osmium, lithium, aluminum, fluorite, plus mod-specific ones (yellorite,
uraninite, bort, benitoite, anglesite, crimson/azure silver, inferium, prosperity,
soulium). Elysium today has aetherium, voidglass and neutronium ores.

What it needs:
1. Elysium ore blocks (stone and deepslate, plus the space worlds' stones), raw
   ores, ingots/gems, nuggets, storage blocks and dust for each common type: silver,
   tin, lead, zinc, nickel, uranium, osmium, platinum, aluminum, lithium, fluorite
   (and more as needed).
2. `c:` tags for all of them (`c:ores/zinc`, `c:ingots/zinc`, `c:raw_materials/zinc`,
   ...), so every mod's recipes accept ours.
3. **Replace the modded ores in worldgen.** Override each mod's ore biome modifiers
   with `neoforge:none` (same id, datapack priority), and place ours. Where a mod
   generates in code rather than data, use its config or a
   `BiomeModifier`/`FeatureRemoval` fallback.
4. Loot and recipe unification: their ore blocks, if any remain, drop or smelt into
   our items (tag-based recipe replacement).
5. Validation: every replaced ore type has our block, tags and worldgen; no enabled
   worldgen still references a replaced mod ore; a matrix of tag coverage.

## 7. Then resume the prior path

- docs/SPEC_FEATURES.md and the earlier plans (SPEC_CLONE is done by then; see 2).
