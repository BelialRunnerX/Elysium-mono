# Jars that span more than one module

A row in `docs/MANIFEST.md` is one jar and one module, which is right for
almost all of them. A few mods are four mods in a trench coat, and giving one
of those a single module means one unit of work nobody can take without
touching three other people's directories.

Those get status **SPLIT** in the manifest and a set of rows here instead. Each
row is a unit the work board hands out on its own; the jar is not DONE until
every one of its rows is.

The counts come from the jar's own language file rather than from a read of the
wiki, so a piece cannot be missed by being forgotten.

| piece | status | module | note |
|---|---|---|---|
| `Valoria: gear` | PLANNED | elysium-arsenal | 17 material sets — infernal, nature, crimtane, jade, aquarius, cobalt, ethereal, void, lunar, phantasm, pyratite, spider, black_gold, awakened_void, depth, marsh, samurai — as new rungs on the weapon and tool ladders. Blocked on elysium-ores: a tier with no material behind it is a weapon nobody can make |
| `Valoria: armour` | PLANNED | elysium-armory | the armour and shields of those same 17 sets, plus 13 trim materials. Blocked on elysium-ores for the same reason |
| `Valoria: materials` | PLANNED | elysium-ores | the ores, ingots and drops the 17 sets are made of. Nothing else in the split can start until this lands |
| `Valoria: creatures` | PLANNED | elysium-bestiary | 50 entities, their spawns, drops and loot |
| `Valoria: the Vale` | DONE | elysium-wilds | the dimension and its 15 biomes: five bands of warmth crossed with three of rain, a partition of the climate plane so every biome wins its own rectangle and none can sit inside another's — proved by arithmetic over 322,267 checks and again by the sampling that once found a river that never generated. The structures are the eight room grammars, which the whole pack gets rather than this dimension alone |
| `Valoria: charms` | PLANNED | elysium-trinkets | the pet curio slot and what goes in it |
| `Valoria: effects` | PLANNED | elysium-lib | 17 effects, 18 attributes and 6 enchantments the rest of the split reads. Shared ground: needs the `shared` unit |
| `Quark: the worldgen` | DONE | elysium-wilds | 23 biomes and 9 features, answered by the overworld overlay rather than one at a time: 140 biomes of ours in half of the inland surface, declared ahead of vanilla's 7,593 entries so each of them wins its own cell outright |
| `Quark: the decoration` | PLANNED | elysium-atelier | 815 blocks and 106 items, most of them vertical slabs, shingles, thatch, framed glass and hedges — the roster this module already generates, in materials it already has |
| `Quark: the rest` | PLANNED | elysium-kinetics | the automation half: pipes, crates, hoppers, the pistons that push more than twelve, and the 16 entities |
| `MysticalAgriculture: the farm` | DONE | elysium-agronomy | 30 seeds on 5 rungs, the awakening altar, the sixth rung and an awakened seed for every ordinary one |
| `MysticalAgriculture: the gear` | PLANNED | elysium-arsenal | the essence tools: five rungs of sword, pickaxe, axe, shovel and hoe, each made of the essence of its own rung. Needs the augment slots below it |
| `MysticalAgriculture: the armour` | PLANNED | elysium-armory | the essence armour of the same five rungs, and the augments that slot into it |
| `Aquaculture: the water` | DONE | elysium-agronomy | the fish, the rods, the hooks and bobbers, a mount for all 105 of them, the worm farm and the tackle box |
| `Aquaculture: neptunium` | PLANNED | elysium-arsenal | the neptunium tools, and the material behind them — a rare catch rather than an ore, which is the one thing about this set worth keeping |
| `Aquaculture: neptunium armour` | PLANNED | elysium-armory | the armour of the same set |
| `FarmersDelight: the kitchen` | DONE | elysium-agronomy | the meals, the stations, the cuts, the crops behind them and the larder that makes the rest |
| `FarmersDelight: the signs` | PLANNED | elysium-atelier | canvas signs in sixteen colours, which is a decoration roster and belongs where the decoration rosters are |
| `supplementaries: the fittings` | PLANNED | elysium-atelier | sconces, item shelves, signposts, flags, planters, bellows and ash — the part of the jar that is decoration, in the materials this module already has |
| `supplementaries: the machines` | PLANNED | elysium-logistics | hourglasses, pulleys, cages, jars, safes and the blocks that move or hold things |
| `supplementaries: the comforts` | PLANNED | elysium-comforts | candle holders, lanterns, the doormat, the rope and what they do to a room |
| `bellsandwhistles: the rolling stock` | PLANNED | elysium-transit | pilots, grab rails, door steps and bogie steps — fittings for trains, which is where the trains are |
| `bellsandwhistles: the station` | PLANNED | elysium-transit | metro casing, panels, doors, trapdoors, windows, station platform and headlight |
| `Design-n-Decor: the machinery` | PLANNED | elysium-kinetics | cogwheels, flywheels, millstones, boilers, fans and girders — 300 of its 903 blocks are a machine shop, and the machine shop is kinetics' |
| `Design-n-Decor: the decoration` | KEEP | elysium-atelier | the rest is fifteen colours of plating, board and tile: the same block retextured, which is the decision in docs/CONVERSION.md section 9 |

## The order it has to go in

`elysium-ores` first, then gear and armour in either order; creatures, the Vale
and charms are independent of all three. `elysium-lib` is shared ground and
whoever takes it holds up everybody who needs an attribute from it, so it is
worth doing early and quickly rather than thoroughly and late.
