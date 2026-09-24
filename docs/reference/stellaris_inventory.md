# Stellaris 1.4.25 (NeoForge 1.21.1): what it contains

Source: the jar in Dylan's `Techpack` instance (`stellaris-1.21-neoforge-1.4.25.jar`),
unpacked at `/tmp/ref/stellaris`. It holds 530 classes, 188 block and 104 item lang
entries, and 16 entity names. License: **CC BY-NC-SA 4.0**, © Exodus Studio (the
jar's mods.toml and github.com/st0x0ef/Stellaris, branch 1.21). Dylan's ruling
(21 Sep 2026): the port is for personal, non-commercial use, and he will settle
licensing with the author if that changes. `elysium-space/CREDITS.md` carries the
attribution the license requires.

No decompiler is reachable from this session (Maven Central, npm, and the desktop's
network are all 403). Logic is therefore read from bytecode with `javap -c -p` and
rewritten natively for NeoForge. Data and art are ported file for file.

## Dependencies, and what replaces each

| Stellaris needs | Used for | Elysium-space uses instead |
|---|---|---|
| Architectury (220 classes reference it) | platform registries, events, networking | NeoForge DeferredRegister, the event bus and payloads directly |
| Potentials (69 classes) | energy and fluid transfer on machines and tanks | NeoForge `Capabilities.EnergyStorage` / `FluidHandler` |
| Sky Aesthetics (jar-in-jar; data in `assets/stellaris/sky_aesthetics/`) | per-dimension skies (stars, sun, planets) | our own `DimensionSpecialEffects` (client) |

## Systems

| System | Where (classes) | How it works |
|---|---|---|
| Planets | `common/data/planets/Planet`, `data/stellaris/planets/*.json` | data: system, level, name, oxygen flag, temperature °C, distance, gravity m/s², bar textures; orbits name a `mainPlanet` |
| Oxygen rooms | `common/oxygen/{Global,Dimension}OxygenManager`, `OxygenRoom` | an **Oxygen Distributor** flood-fills *air* (`isAir` only) from its six faces, breadth first. If any cell lies further than `maxOxygenRoomSize/2` from the distributor on any axis, the room is **open** and gets no oxygen. Each newly filled cell costs the distributor oxygen and energy (`useOxygenAndEnergy`). Breathing: `hasOxygenAt` / `breathOxygenAt` |
| Breathing / suits | `mixin/LivingEntityMixin`, spacesuit / jetsuit items, `OxygenTankOverlay` | a periodic check; with no room and no suit oxygen, the entity takes oxygen damage |
| Gravity / falls | `mixin/EntityFallMixin`, `Planet.gravity` | gravity scales against Earth's 9.80665 |
| Temperature | `Planet.temperature` | hot and cold planets hurt without a suit |
| Radiation | `common/effects/RadioactiveEffect`, `Events` (antenna check) | a radioactive effect near certain blocks |
| Storms | `Planet$StormParameters`, `SandStormEffect`, `mixin/StormyPlanetMixin` | fog and sand storms on some planets |
| Travel | `RocketEntity` (tiny, small, normal, big), `RocketLaunchPad`, `RocketStation` (rocket-component crafting), `PlanetSelectionScreen`, `TeleportUtil.teleportToPlanet` | fuel the rocket, launch, pick a planet, then teleport and land |
| Space stations | `data/stellaris/space_stations`, orbit dimensions | stations are built in orbit dimensions |
| Machines | 55 block classes, 40 menus: oxygen distributor, fuel refinery, generators, … | energy and fluids through Potentials |
| World content | 9 dimensions, 7 dimension types, 11 biomes, ores (desh, ostrum, calorite-like: tharsite, solerium, …), 35 structures, 4 alien mobs + more | datapack worldgen |

## The four placeholder worlds reuse this much

Starting from `dimension/{earth_orbit,moon,venus,mars}` and following every
`stellaris:` reference: 33 worldgen files, 9 blocks (moon/mars/venus sand and
stone, venus sandstone, moon vines), and 4 mob spawn entries (dropped until mobs
are ported). Structures come in through structure sets, not biomes, so none are
pulled in.
