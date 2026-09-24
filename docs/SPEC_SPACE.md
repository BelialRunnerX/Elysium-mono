# Elysium-space: porting Stellaris, then making it Elysium's

Requested 21 Sep 2026. Port Stellaris 1.4.25 into our own module, add
placeholder worlds with hazards, and merge its enclosed-versus-open logic with
Elysium's rooms. Reskin to the Elysium theme after that. Reference:
`docs/reference/stellaris_inventory.md`.

## Rulings

- **Base: Stellaris 1.4.25**, for personal, non-commercial use (Dylan). Credit is
  in `elysium-space/CREDITS.md`.
- **Separate jar, bundled into elysium-all.** Mod id `elysiumspace`. Depends on
  elysiumlib only: no Architectury, no Potentials, no Sky Aesthetics (see the
  inventory for what replaces each).
- **Ids are Elysium names from day one**, so the reskin changes art and lang
  only, never registry ids that worlds have saved.
- **One rule for "enclosed".** Stellaris fills only `isAir` and caps the fill
  with a cube around the distributor. Elysium's `ElysiumRoomScan` fills anything
  with no collision and caps by volume. The port uses Elysium's scan: it is the
  more correct rule (a torch or a sign does not seal a room, a closed door
  does). A space room is an Elysium room that also has a working distributor.
- **Hazards are Elysium hazards.** Each world's atmosphere feeds the existing
  hazard pipeline (intensity → wards, protection, rooms, resistance share)
  through one new library hook. Vacuum is a new hazard registered by the space
  module. Nothing is special-cased per world in code.
- **Vacuum is sealed by:** an oxygenated room, or an oxygen supply carried by
  the player (suit tank, later). A room that is not oxygenated gives only the
  usual room share. That follows the lib rule that rooms never seal on their
  own: here the distributor seals, and the room is only where it can.

## The five worlds (placeholders; names are working names until the reskin)

| Id `elysiumspace:` | Built from | Gravity | Air | Hazards (intensity) |
|---|---|---|---|---|
| `high_orbit` | Stellaris earth_orbit | 0.07 g | none | vacuum 1, radiological 0.5 |
| `pale_moon` | Stellaris moon | 0.165 g | none | vacuum 1, cryogenic 0.6 |
| `cinderveil` (hot, corrosive) | Stellaris venus | 0.9 g | toxic | thermal 1, corrosive 1, pressure 0.5 |
| `frostfall` (frozen, irradiated) | Stellaris mars | 0.38 g | thin | cryogenic 1, radiological 1, vacuum 0.5 |
| `emerald_bastion` (paradise) | new | 1 g | yes | none; no natural hostile spawns |

**Emerald Bastion** (Dylan's spec): a green and dark-rock palette. Moss replaces
grass and mud replaces dirt. There is no stone or cobblestone: the underground is
all deepslate, with deepslate ores of every type and a lot of emerald. No oceans,
but lakes and rivers. Mostly flat plains with some mountains. **Emgrove** trees
are mangrove-like but with greenish wood and planks. It is an imperial emerald
stronghold world, and nothing hostile spawns naturally at any light level.

## Phases

| Phase | Content | Status |
|---|---|---|
| S0 | module scaffold, credits, inventory, this plan | DONE |
| S1 | atmosphere hook in the lib; vacuum hazard; the world table (pure, harness); gravity | DONE — `ElysiumHazardField.registerSource` in the lib; vacuum hazard (Void); `SpaceWorld` table + WorldHarness; gravity via attributes |
| S2 | the four ported worlds + Emerald Bastion (worldgen, blocks, emgrove set) | DONE (placeholders) — 4 worlds ported (33 worldgen files via `tools/port_worldgen.py`), Emerald Bastion (no-ocean terrain, deepslate, moss/mud, emerald-rich, emgrove groves, lakes, rivers, no monsters); structures (S6c) and mobs (S6d) ported |
| S3 | Oxygen Distributor on the unified room scan; breathing in vacuum | DONE (unfuelled) — Oxygen Distributor on `ElysiumRoomScan.interior`, the lib's one fill; clears vacuum, and corrosion on toxic worlds |
| S4 | travel: an op command for testing now, then rockets, launch pad, planet selection screen | PARTIAL: tier-1 rocket (launch key G, 10 s countdown, climb, destination screen offering only what the fuel reaches, arrival and landing), Launch Pad, Fuel Canister (250 mB), `SpaceRoute` (Stellaris's distance and fuel rule; the Bastion needs an upgrade). Rocket station, part-built tiers, upgrades and fluid fuel come in S6 |
| S5 | suits and oxygen tanks (a full set seals vacuum, as armour sets do) | DONE (placeholder recipes): Space Suit (4 pieces, Void-element Elysium gear: a full set seals vacuum and radiation, takes runes and ascension); chestplate air 3000 mB (`SuitAir`: 5 mB/s outside, refills in a filled room, suffocation when empty); Oxygen Canister (+1000 mB); air gauge HUD; asphyxiation damage type. Jet suit and suit modules come in S6 |
| S6 | machines, fuel, ores and materials, structures, mobs, space stations | PARTIAL, S6a DONE: Stellaris's fuel and air chain on NeoForge FE and fluids (other mods' cables and pipes connect). Fluids oil, fuel (liquids with buckets), oxygen, hydrogen (gases); Coal Generator, Solar Panel (x2 on airless worlds), Fuel Refinery (10 oil to 5 fuel, 100 FE), Water Separator (5 water to 10 H2 + 5 O2, 50 FE); the Oxygen Distributor now costs oxygen and power; bottling makes the canisters; oil lakes; rocket takes fuel buckets; the Large Fuel Tank (6000 mB) opens the Bastion. S6b PARTIAL: rocket parts (nose cone, fin, engine) and the rocket crafted from them; Hydrogen Motor upgrade (hydrogen canisters, bottled at the separator); Cable (250 FE/t) and Pipe (250 mB/t) conduits that connect to any mod's energy or fluid blocks. Pumpjack (per-chunk oil deposits from the seed, half of chunks dry, 1 000-40 000 mB), refinery now also makes diesel, Diesel Generator (40 FE/t), Fluid Tank (16 000 mB, keeps contents when broken, drains downward). Jet Suit (flight and hover modes, key J, its own 2000 mB fuel; JetRules). S6c PARTIAL: Stellaris's ores on each world with Elysium names (desh->lunite, ostrum->frostite, tharsite->cryonite, corronium->cindrium, ice shard->rime shard; steel ore in the Overworld and on the Pale Moon), raw/ingot/nugget/blocks, c: tags, Fortune; rocket recipes now use steel, the Large Fuel Tank lunite, the Hydrogen Motor lunite and rime. Structures: 25 Stellaris structures ported (moon to the Pale Moon, Mars to Frostfall, Venus to Cinderveil, Earth and the meteor to the Overworld), 63 templates rewritten by `tools/port_structures.py` (own NBT reader and writer, byte-exact round-trip), 17 chest loot tables, the structure-void processor, and 49 building blocks (stone families, plating, pillars). S6d DONE: Stellaris's seven mobs, under Elysium names: Moonfolk (alien; villagers with no iron golems, head-shake on a refused trade), Hollow Moonfolk (alien zombie; melee plus Rime Spit, which chills), Void Crawler (star crawler; arrow-proof), Frost Raptor (martian raptor), Cinder Boar (mogler; a hoglin that never zombifies), Cinderling and Cinderling Brute (pygro; piglins at home on Cinderveil). Stats from `MobRules` (pure, harness-checked), sizes and spawn eggs, Elysium drops. Art ported by `tools/port_mobs.py`, a symbolic interpreter over the models' bytecode: geometry JSON (with each model's rig) and Stellaris's keyframe animations as NeoForge JSON animations; one `RiggedModel` draws every mob, cinderlings use vanilla's PiglinModel. Biome spawns, structure spawners, template entities and village spawn overrides now name our mobs. The validator bakes every geometry with Minecraft's classes. S6e DONE: space stations. Small, Medium and Big Station Blueprints (Stellaris's three stations, templates ported); used in High Orbit they build your station from steel and lunite ingots (100/150/200 each; creative builds free) on your own slot of a 1024-block grid (`StationRules`, pure), stand you on its pad, and rockets to High Orbit then land there; a bigger blueprint rebuilds the station in place. Radioactive generator moved to the ore unification (needs uranium) |
| S7 | skies (in place of Sky Aesthetics) | DONE: our own DimensionSpecialEffects per world (`client/sky`), from sky files ported by `tools/port_skies.py` out of Stellaris's Sky Aesthetics data: sun size, moon (the Overworld hangs in the Pale Moon's sky), fixed sky objects (the Overworld below High Orbit), star fields (always visible on airless worlds), sunsets only where there is air, clouds only on Cinderveil, no weather in space. The Emerald Bastion keeps the Overworld's sky. |
| S8 | reskin: textures, item and block names, lore, all in the Elysium palette | DONE: `tools/reskin.py` redraws every Stellaris texture into its world's Voidforged ramp (Pale Moon neutronium and aetherium glow, Frostfall aetherium and dimensional glow, Cinderveil scorched basalt and plasma glow), keeping each pixel's lightness and sending off-hue lit parts to the glow ramp; blocks fully, mobs at 65% so a moonfolk's profession still reads, planets as seen from afar; the Overworld's steel ores are redrawn in vanilla stone. The validator fails if any Stellaris texture ships unchanged (Earth excepted). All names are Elysium's. Machine screens: one menu and ElysiumUI screen for every machine (energy, each tank in its fluid's colour, the generator's fuel slot, the inventory); sneak-use keeps the one-line status. The rocket station is not ported: rockets are crafted from parts on a crafting table. |
