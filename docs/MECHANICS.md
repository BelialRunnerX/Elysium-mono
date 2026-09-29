# The mechanics, one at a time

Every runtime mechanic of every module we add, read off the Java, the data and each validator, with how a dedicated server exercises it over RCON, what proves it worked, and how it is covered today: `live` (a scenario in tools/livetest), `tree` (a validator section executes the numbers), `client-only` (needs a player: the client stages), `none`. Written 27 September 2026 from four surveys of the tree; the live scenarios named in the coverage column are in tools/livetest/scenarios_mechanics.py. Items marked BUG or CHECK were fixed or resolved the same day (CONVERSION.md §16.2).

## elysium-agronomy — 32 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Pot grows an essence seed (`PotBlock.grow`; light ≥ 9 at y+1) | `data merge {items:{Size:3,Items:[{Slot:0,id:"elysiumagronomy:coal_seed",count:1}]}}`; glowstone at y+2; 8000 ticks | `items.Items[{Slot:1}]` faint_essence | tree → live (agronomy_pots) |
| Pot with soil and a sprout (botany_soil/botany_sprout data) | `{Slot:0 carrot, Slot:2 farmland}`; 640 ticks | slot 1 carrot ×2 | tree → live (agronomy_pots) |
| Accelerator (LOW grid, draws 48 with a pot beside it) | grid → accelerator, seeded pot at x+1; 2000 ticks | pot yields; `demand` 48 | tree → live (agronomy_pots) |
| Hive/apiary work (`HiveBlock.work`; species component) | apiary with `bee[elysiumagronomy:species="iron"]` ×16, flowering_upgrade, speed_upgrade; 2700 ticks | slot 1 iron_comb | live (agronomy_hive, plain) → live (faster apiary) |
| Apiary upgrade-slot rules; hive breeding with jar and treat | hopper upgrades / client | slots 2–3; `mate` | tree |
| Centrifuge (a plain comb enters, makes demand, is never spun) | live | slots 1–3 | live |
| Rock generators tier 0/driven/industrial and the 7 veins | setblock / grid | cobblestone per 80/20/5 ticks; raw_copper per 1000 | live (tier 0) → live (agronomy_veins) |
| Worm farm (`family`), tackle box filter, cabinet | `data merge {family:"river"}`; hoppers | `held` 1 at 1200 ticks; `Items` | tree / live |
| Kitchen station cooking (`StationBlock.cook`; campfire below) | `data merge {contents:[bone,onion,carrot],cooking:1b,making:"elysiumagronomy:stock",progress:0}`; 200 ticks | `made[0]` stock | tree → live (agronomy_kitchen) |
| Board cutting; awakening altar firing; nest jarring; bee temper, scoop, jar; fishing catch; rod tackle; varied diet; meal effects | client-only | — | tree |
| Mushroom colony; kitchen crops (random ticks) | `gamerule randomTickSpeed 300` | `age` | tree → live (agronomy_random) |
| Wild crop patches — `place feature elysiumagronomy:wild_cabbage` on grass probably fails (CropBlock farmland rule) | `place feature` on grass and on farmland | `cabbage_crop[age=4]` | tree → CHECK |
| Nest worldgen | `place feature elysiumagronomy:plains_nest` | `plains_nest[bees=3]` | tree → live |
| Bee entity (no fall damage, `Worked` rises); lava fish | `summon elysiumagronomy:iron_bee` at y+20; magma_carp in lava; 200 ticks | Health 10; `Species` | none → live (agronomy_creatures) |
| Data reload | `reload` | log | tree |

## elysium-almanac — 10 mechanics (all need a player): book on first join; HAVE/READ/VISIT/LEVEL/WEB swept every 20 ticks; PLACE/BREAK/CRAFT/USE/EAT/SMELT tallied from the first event (`tally` attachment); SLAY tally; tags and the book's family tags; rewards claimed from the step screen (`claimed` attachment, `AlmanacClaim` payload, XP through `ElysiumCharacter.addXp`); `/almanac status|claim|finish|reset`; VISIT (`execute in elysiumspace:pale_moon run tp`); the three book screens. All tree (harness plays the 519-step book; validator holds the catalogue against it); client stage `stage_almanac_client.ps1`.

## elysium-arcana — 10 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Budding geode growth (`BuddingGeodeBlock`, 1/30 per random tick) | `budding_iron` with air above; `randomTickSpeed 300`; 2000 ticks | `iron_small_bud` … `iron_cluster` above | tree → live (arcana_geodes) |
| Cluster needs its support | remove the stone under `iron_cluster[facing=up]` | cluster gone, 4 iron_shard items | none → live (arcana_geodes) |
| Cluster drops | `loot spawn ... mine ... iron_pickaxe` | 4 shards | tree |
| Transmuter dissolve/catalogue/condense; price table; motes survive death | client-only / tree | attachments | tree/none |
| Relic active abilities | client-only | ability + cooldown | tree |
| Relic passive abilities (swiftfoot, ironhide, mender…) — **no runtime handler exists, tooltips only** | client-only | attribute unchanged | none → BUG |

## elysium-armory — 25 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| `set_parts` loot function (makes test gear) | `item modify block ... {function:"elysiumarmory:set_parts",materials:["elysiumarmory:iron"]}` on `elysiumarmory:sword` | `parts`, `stats`, `traits` components | tree → live (armory_gear) |
| `gear` loot condition | `loot spawn ... fish {pools:[...condition elysiumarmory:gear trait malleable]} ... elysiumarmory:pickaxe[traits={malleable:1}]` | diamond for modular, none for vanilla | tree |
| Stats recomputed on inventory tick; durability stops one short; wide tools; sickle/prospector; placing/filling traits; dagger spacing; player-tick traits; XP/light/drop traits; confetti; benches; mod kit; elytra wear; trait enchantments; jewellery | client-only | — | tree/none |
| Bounce and Moonwalker on fall (any entity) | husk at y+20 in boots `{bounce:1}` vs control; 60 ticks | Health 20 vs ~3 | tree → live (armory_gear) |
| Snow walker | powder snow column; husk in boots `{snow_walker:1}` | stays on top | tree → live (armory_gear) |
| Trait effects via lib (adamant weapon) | husk holding `sword[traits={adamant:1}]` hits a NoAI iron_golem | golem loses > 5 | tree |
| Starlight charger (open sky, night) | `data merge {held:{...sword_blade parts}}`; `time set 18000`; 6000 ticks | `charged` 6000 | tree → live (armory_charger) |
| Custom crafting (assembly, paint, repair, plan book) | crafter + redstone | `elysiumarmory:pickaxe` with parts | tree → live (armory_crafting) |
| `/elysium armory properties`; data reload | console | messages / log | tree |
| Modular arrow damage | skeleton with a built arrow | arrow `damage` | none |

## elysium-arsenal — 20 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Growth totem (bone-meals within 6 every 200 ticks) | totem + wheat[age=0] field; 2000 ticks | some wheat age > 0 | tree → live (arsenal_totems) |
| Radiance totem (heals + glowing within 12, hostiles only?) | husk within 12; 400 ticks | Health 18.5, glowing | tree → live (arsenal_totems) |
| Held totems, fossil aura, fossil fall cut, brushing, bows, weapon pierce/sweep, broad tools, scythe, hammer regrade, grade on craft/pickup, identify/set artifact, soaring winds, tunneling | client-only | — | tree |
| Arrow effects (`ArrowEffects.onHurt`) | `summon minecraft:arrow x y+3 z {Motion:[0,-2,0],item:{id:"elysiumarsenal:barbed_arrow",count:1}}` on a NoAI husk | `elysiumlib:bleeding` within 10 ticks; rime slowness; ember fire | tree → live (arsenal_arrows) |
| Grade attribute modifiers | husk holding `iron_sword[elysiumarsenal:grade="heavy"]`; `attribute ... attack_damage get` | 8.8 | tree → live (arsenal_arrows) |
| Penetrating strike artifact | husk with `{"elysiumarsenal:artifact":"penetrating_strike","elysiumarsenal:artifact_level":3}` hits a golem for 10 | 86.4 | tree → live |
| Piercing damage type | registries | Applied | live |

## elysium-ascendancy — 2 mechanics: the weighting engine (tree), construction log line (none).

## elysium-atelier — 10 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Fences, panes, bars, walls joining (tags) | setblock pairs | `[east=true]`, `[east=low]` | tree → live (atelier_joins) |
| Door/trapdoor/gate on redstone | redstone_block beside | `open=true,powered=true` | tree → live (atelier_joins) |
| Diagonal pieces in `#fences`/`#walls` — the join is one-sided (the diagonal shows none back) | oak_fence beside acacia_diagonal_fence | `[east=true]` on the fence only | tree (design) |
| Waterlogged shapes never spread water — **probable bug: no `updateShape` scheduling the fluid tick** | `acacia_balcony[waterlogged=true]`; change a neighbour; 40 ticks | water beside? | none → CHECK |
| Climbable pieces (ladder, rope, trellis) | husk atop a 20-high ladder column | no fall damage | tree → live (atelier_joins) |
| Slabs; every block drops itself; stonecutter cuts | loot / client | drops | tree |
## elysium-atomics — 20 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Reactor core fuel, heat and LIT ticker (`block/ReactorBlock.java`) | core with coolant channels on 4 faces; `data merge {notch:1, items:{Items:[{Slot:0,id:"elysiumatomics:enriched_uranium",count:4}]}}`; 200 ticks | `fuel` > 0 and falling, `heat` > 20, `lit=true` | tree → live (atomics_reactor) |
| Coolant channel count sets the settle temperature | two cores at notch 4, 0 and 4 channels; 400 ticks | 4-channel `heat` lower | tree |
| Wear above SAFE (800); spent core (wear ≥ 1) stops | `data merge {heat:1000f}`; `{wear:1.0f,notch:2,fuel:1000}` | `wear` > 0; `fuel` unchanged, `lit=false` | tree |
| Meltdown at 1600: block removed, explosion r8 | `data merge {heat:1700f}`; 2 ticks | core gone | tree |
| Core fuel slot takes hopper input | hopper of enriched_uranium above; 40 ticks | `items.Items[{Slot:0}]` | live (atomics_reactor) |
| Core dial, hand fuelling, plate repair | client-only | `notch`, `wear` | tree |
| Core spills fuel when removed | `setblock air` | item entity | none |
| Core as HIGH generator on the grid | core → steel_wire → enricher; 600 ticks | enricher `share` > 0, copper_ingot | live (atomics_reactor) |
| Reactor hall forms; port side count | 3-cube casing, fuel_rod, port; 40 ticks | `reactor_port[side=3]` | tree |
| Hall temperature and channels (port is not a grid node; `step` fixed at 1) | casing swap for a channel; 200 ticks | `channels` 1, `temperature` > 20 | tree |
| Cells buffer surplus (basic/advanced/quantum) | turbine → wire → basic_cell; 400 ticks | `held` rises then falls | tree |
| Solar panel by sky every 20 ticks | noon vs midnight | `supply` ≥ 3 / 0 | tree |
| Servo: electricity to rotation | diesel → gold → transformer → steel → servo[axis=x] → shaft | shaft `rpm` > 0 | tree |
| Works run from a shaft at 256 rpm | servo-driven shaft into enricher; 900 ticks | copper_ingot in slot 1 | tree |
| Recipe kinds enriching/reacting/centrifuging on a HIGH grid | atomics_machines | slot 1 product | live |
| Machine inputs from a hopper | inventories_all | `items.Items` | live |
| Coolant channel plates, arms, water | runs_plates | state | live |
| Coolant channel dye | `data merge {run_dye:"red"}` | arm `none` toward blue | tree |
| Uranium smelting/blasting | furnace with raw uranium | `container.2` uranium_ingot | none |

## elysium-bestiary — 55 mechanics

Bait: `summon minecraft:zombie S {NoAI:1b,PersistenceRequired:1b,Tags:["bait"]}` then `damage @e[type=<creature>,limit=1,sort=nearest] 1 minecraft:mob_attack by @e[tag=bait,limit=1]` gives a creature a target with no player online.

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| spallet SPLIT on death (`entity/act/BeastActs`) | summon, kill | 1–2 spallets `{ElysiumPiece:1b}` at half health | tree |
| shearjaw LEAP; drayhulk ANCHOR shove; siegehulk CHARGE; skimwing HEIGHT hop; kin LEAPS | bait at range; 200 ticks | `Motion` spikes / bait displaced | tree |
| prowlframe STALK freezes while watched | bait facing it vs away | Pos unchanged while watched | tree |
| pileframe SUNDER breaks a block ahead | dirt ahead; 200 ticks | dirt gone | tree |
| carrier DRIFT predicts; sidetone CHILL slows | bait moving / in reach | bait slowness | tree |
| nullprime CALL summons ≤ 2 wights | 400 ticks | `ElysiumCalled:2` | tree |
| Wights burn in sunlight | noon, open sky; 40 ticks | `Fire` > 0 | tree |
| Brutes add knockback on hit | bait; 200 ticks | displacement > vanilla | none |
| glasseye PETRIFY, blastjaw BREATH, bloomlet SPORES, pilferhand THIEVERY | client-only | player effects | tree |
| siphon DRAIN −2 max health per hit | bait; 200 ticks | bait max_health base falls | tree |
| plateframe PLATING resistance; swarmhand NUMBERS strength ×3 | summon; 100 ticks | effects on self | tree |
| Companion chiller/igniter affect monsters within range | bait; 60 ticks | slowness / `Fire` | tree |
| Companion player gifts (tether, borer, doubler, snapper, lumen, seeder) | client-only | player effects | tree |
| Companion holdfast/tapper/stasher: no gift branch | summon; 120 ticks | nothing | tree |
| Companion taming with wheat seeds | client-only (`data merge {ElysiumOwned:1b}`) | `ElysiumOwned` | tree |
| Kin SUMMONS copies (≤3), HOWLS pack buffs, BURROWS, DISGUISES, THROWS, CARRIES, contact POISONS/FREEZES/IGNITES, DRAINS | summon / bait; 80–260 ticks | counts, effects, `ElysiumUnder`, `ElysiumDisguised` | tree |
| Court HERALD copies every 60 ticks — copies start at ElysiumCalled 0, so the chain never ends | summon forgecaller; 200 ticks | ≥3 forgecallers | tree → BUG to fix |
| Court HEALER, ARTILLERY, SHIELD, MOUNT, HEAD/SWARM contact effects, two-phase heads | summon pairs; 60–200 ticks | `Health`, `Passengers`, effects, `ElysiumTurned` | tree |
| Warded bite blunted by the right ward | two baits, one with fire_resistance | less health lost | tree |
| Denizens plain melee | bait; 200 ticks | bait damaged | tree |
| Fauna breeding — `resetLove` puts adults on cooldown at Age 0, one-tick window to feed | two fenstag `{Age:0,InLove:600}`; 100 ticks | a baby | tree → BUG to fix |
| Fauna tempt and swim | client-only | follows | none |
| Shade fights monsters, gone after 600 ticks | summon beside bait; 620 ticks | gone | tree |
| Relic bolt gone on ground or after 60 ticks | summon with Motion; 70 ticks | gone | tree |
| mob_fan MOVES, grinder_saw KILLS (doubles drops, XP), experience_press KEEPS | setblock + mob; 100–120 ticks | Pos, orbs, `ElysiumHeldOrbs` | tree → live (bestiary_farm) |
| Unpowered farm blocks get no ticker | setblock; 100 ticks | `ElysiumSinceAct` 0 | tree |
| Natural spawns (166 biome modifiers), haunt bands | client-only | creatures in bands | tree |
| Entity and farm loot tables (175) | `loot spawn S loot elysiumbestiary:entities/<id>` | items | tree |

## elysium-colonies — 22 mechanics (all need MineColonies; founding needs a player)

Daily reckoning every 200 ticks once per colony day; frontier happiness; citizen stress/wounds/practice; founding, raising and repairing buildings; relief grants; exchange purchases; stuck reports; charter style; natural towns completing orders; settlement seed random tick; settlement structure (live: locate); raider elite promotion; world-tier lift; standing floor; spaceport anchor, dismantle and daily trade run; charter and market reload; `/elysium colony`. All `tree`; the seed-founded town's autonomy is the client stage `stage_seven_client.ps1`.

## elysium-comforts — 23 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Grave on death (drops, XP, resting place, opening, pistons) | client-only / `setblock grave{Held:[...]}` then air | items and orb spill | tree |
| Per-player loot copy on chest open | client-only | `elysiumcomforts:shares` attachment | tree |
| Rest blocks (bag by night, hammock by day, no spawn set, sleep through) | client-only | `occupied`, day time | tree |
| Chime sound; chime falls when the ceiling goes | setblock/air above | P is air | tree |
| Mailbox parcel lands (`ElysiumFlying`, `ElysiumArrives`) | `data merge {ElysiumAddress:"home",ElysiumFlying:{id:"minecraft:paper",count:1},ElysiumArrives:0L}`; 2 ticks | `mailbox[full=true]`, `Items[0]` paper | tree → live (comforts_post) |
| Mailbox naming, posting, mail unpacking | client-only | components | tree |
| Pigeon turns after 300 ticks in the wrong dimension | summon in the nether; 320 ticks | `charred_pigeon` | tree |
| Pigeon breeding | two `{Age:0,InLove:600}`; 100 ticks | baby | none → live (comforts_post) |
| Pigeonhole waste — `PigeonEntity.cameOutOf` has no caller, waste never rises | client-only | `waste` | tree → BUG to fix |
| Trader item captures a villager; stations count work while holding one | client-only / `data merge {ElysiumHeld:{VillagerData:{...}}}`; 130 ticks | `ElysiumGoes` 2 | tree → live (comforts_post) |
| Light fittings by row, underfoot no collision | setblock, drop item | falls through | tree |

## elysium-core (`elysium`) — 30 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Starting kit on first login | client-only | codex, bread, torch; attachment | tree |
| Rune drops; emperor crown | client-only | drops | tree |
| `/elysium materials` | `elysium materials` | header + lists | none → live (core_commands) |
| `/elysium sheet/stats/standing/tier/respec` | client-only | chat | tree |
| Uncovered-ingot report on reload | `reload` | log line | none |
| Rogue spawner waves (player within 24/20) | client-only | `waves` 4→0 | tree |
| Spawner and caged-invader worldgen | `place feature elysium:brutal_spawner` | block present, `waves` 4 | tree → live (core_features) |
| Ore worldgen (neutronium, aetherium, voidglass) | `place feature elysium:neutronium_ore` in stone | ore blocks | tree → live (core_features) |
| Caged invader releases an elite | client-only | elite mob | tree |
| Registry beacon claim/strike; inspection beacon plant/lapse | client-only / `setblock air` | claim data, chat | tree |
| Imperial archive report | client-only | chat | none |
| Imperial enforcer: not sun-sensitive, no drown conversion | noon under sky vs zombie; water 700 ticks | `Fire` ≤ 0; still an enforcer | none → live (core_enforcer) |
| Dispatch equips by standing band | client-only | gear | none |
| Field rations ward; codex sheet; scanner filing; gem case | client-only | effects, screens | tree/none |
| Gem grinding crafting | crafter + redstone | gem_dust | tree → live (core_recipes) |
| Convergence smithing; reforge, socket, ascension; gem cutting and augmenting | client-only | components | tree |
| Gear set bonuses (2-piece knockback resistance) | zombie in neutronium set; `attribute get` | ≥ 0.1 | none → live (core_gear) |
| Material gear attributes | zombie holding voidcut_blade; `attribute get` | attack_damage > 3 | tree → live (core_gear) |
| Loot tables | `loot spawn` | items | tree |
| Puffish skill tree; Silent Gear materials | client-only | screens | tree |

## elysium-dungeons — 11 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Dungeon dimension loads | `execute in elysiumdungeons:dungeon run forceload add 0 0` | force loaded | live |
| Rift key lights a frame (interior 2×3..21×21) | client-only | `rift_portal[axis=x]`, key `Damage` 1 | none |
| Breaking a frame closes the portal | frame ring + portal blocks; break a frame | portal gone in 1 tick | none → live (dungeons_frame) |
| Portal entry allocates an instance, builds 20 rooms, teleports | client-only | log `Allocated`, `Built N rooms`, `elysium_dungeons.dat` | tree |
| Room grammar (entrance, loot, boss, fluids refused) | client-only then `execute in ... if block` | chests, boss | tree |
| Leaving, 60-tick cooldown, logout, dimension change, research credit | client-only | positions, log | none |
| Rift frame and key recipes | crafter | rift_key | tree |

## elysium-enchanting — 25 mechanics

Mob-attack rig: villager `{NoAI:1b,Tags:["v"]}` and 2 blocks away a zombie with an enchanted iron sword (`components:{"minecraft:enchantments":{levels:{"elysiumenchanting:<e>":1}}}`).

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Stat-block reload (35 blocks) | `reload` | log `loaded 35 enchanting stat block(s)` | tree |
| Shelf survey (`max_eterna` attribute registered but never attached to players) | client-only | displayed eterna | tree → check |
| Arcane table, infusion basin, scriptorium menus | client-only | items | tree |
| Scriptorium shelf persistence | `data merge {shelf:[{enchantment:"minecraft:sharpness",pages:4}]}`; restart | `shelf[0].pages` 4 | tree |
| Anvil tomes and the "too expensive" wall | client-only | costs | tree |
| venom/hoarfrost/sundering on the victim; siphon regeneration; kindling fire | mob-attack rig; 200 ticks | villager effects, zombie regeneration / `Fire` | tree → live (enchanting_blades) |
| vitality, ironhide armour attributes | zombie in enchanted chestplate; `attribute get` | max_health 22 | tree → live (enchanting_blades) |
| nightward, emberwalk | dark vs lit zombies; lava + boots | health difference; magma_block | tree |
| bulwark, ambush, charge, grave_robber, scholar, prospector, volley, thrift, steady_aim, maelstrom, long_arm | client-only | damage, drops, range | tree |
| Re-paced vanilla enchantments (31) | compare | costs | tree |
| Advancements | client-only | granted | tree |
| Tome tower structure and chest loot | `locate`; `loot spawn ... elysiumenchanting:chests/tome_tower` | nearest; items | live / tree |
## elysium-flora — 12 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Ripe/unripe crop drops (`FibreCropBlock.getDrops`) | `setblock loomflax[age=7]` on farmland; `setblock air destroy` | fibre + seeds ripe, seeds only unripe | tree → live (flora_crops) |
| Fortune on the harvest | `loot spawn ... mine ... diamond_hoe[fortune 3]` ×20 | 1–4 fibre | tree |
| World-tier bonus yield | client-only | 2 fibre ~40% | tree |
| Bone-meal growth | dispenser of bone meal pulsed 8× | `[age=7]` | none → live (flora_crops) |
| Farmland-only placement | catalogue | pops on grass | live |
| Wild plant gives 2 seeds | `setblock wild_loomflax`; destroy | 2 seeds | tree → live (flora_crops) |
| Fuel burn times (`FloraHandler.onBurnTime`) | furnace with emberwood_charcoal | `BurnTime` ≈ 2398 | tree → live (flora_wood) |
| Emberwood charcoal smelting | furnace log + coal; 220 ticks | slot 2 emberwood_charcoal | none → live (flora_wood) |
| Sapling grows (configured_feature emberwood) | bone-meal rig 10 pulses | `emberwood_log` at y+1 | none → live (flora_wood) |
| Axe stripping — `FloraBlocks.stripping()` is never called | client-only | stripped log | none → BUG |
| Wild crop worldgen; emberwood worldgen | `place feature` | blocks | tree |

## elysium-foundry — 10 mechanics

Alloy smelter (live), refinery HIGH (live), refinery voltage gate (none), hopper I/O (live), foundry crushing/rolling recipes on industry machines (tree), vanilla smelting of dust and raw ore (tree), crafting (tree), contents spill on break (none), right-click offer/readout (client).

## elysium-industry — 23 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Grid solve/share, turbine lights on demand, diesel MEDIUM, transformer bridges tiers, wires as runs, crusher/mill/arc/separator | scenarios | fields, products | live |
| Turbine idles with no demand | turbine + coal, no consumer; 200 ticks | `burning:0`, fuel kept | none → live (industry_idle) |
| Wire loss by distance; short grid share; overload burn | long line; 2 mills on 1 turbine; 3 turbines + 5 mills | `blocks`, `share` ≈ 0.62, `burned:1b` | tree → live (industry_grid_rules) |
| Cross-tier contact — `joins()` refuses different voltages before `mixed` is checked; lines just do not connect | copper line touching gold | `supply 0` | none (design) |
| Wire dye | `data merge` | link split | tree |
| Plant driven by a shaft (≥96 rpm) | oil_engine[axis=y] + brass_shaft[axis=y] + crusher below | bone_meal | tree |
| Alternator (≥48 rpm → FE); Motor (FE → rotation) | oil_engine → brass_shaft → alternator → copper wire → crusher; turbine → wire → motor[axis=y] → shaft → millstone | crusher `supply`; millstone `running` | tree → live (industry_seams) |
| Coke oven / blast furnace multiblocks (no item capability: no hopper can feed them) | fill coke_brick 3×3×3 hollow, coke_oven, `data merge items`; 920 ticks | `formed=true`, coke / steel_ingot | tree → live (industry_multiblocks) |
| Contents spill; right-click readouts | destroy / client | items / text | none |

## elysium-kinetics — 22 mechanics

Line solve, furnace engine, millstone/press/saw/mixer/refinery, hoppers (live). Oil/distillate engines, hand crank (`data merge {wound:100}`), water wheel (2 water faces), windmill bearing (sails), shear on timber, cog ratios, gearbox right angle, jam, recipe minimum speed, dynamo seam (→ `elysiumspace:water_separator` Energy), chunk anchor grace (tree). Anchor ticket purge on world load, contents spill, readouts (none/client).

## elysium-logistics — 25 mechanics

Controller walk/demand, bus runs, importer, drawer store, packs take hoppers (live). Device limit >64, unpowered network, bus dye, importer filter, exporter with filter, storage bus, interface → hopper, detector threshold, ender link (`channel` only joins on load/right-click), drawer fullness/comparator, drawer void/lock (only `/data` sets them: no item or UI calls `voiding()`/`locked()`), assembly core + assembler queue, terminal, pack parts (ember_plate cooks, press_screw 9→1, void_seal, sieve_mesh), worn packs, compression crafting (tree). Drawer right-click, break spill (none).

## elysium-lib — 48 mechanics

Experience from every action (`character/ElysiumXpRules`, `event/ElysiumXpHandler`): mine, dig, build, craft, smelt, strike, harvest, fish, tame, breed, explore, trade, enchant, eat, each capped per minute; creative pays nothing. Tree (`XpHarness`, validator section, `negtest_xp.sh`); client stage: break stone in survival and read `elysiumlib:xp`.


Runs (live). Affix loot modifier (tier from players within 32; console negative control), `elysiumlib:roll_affixes` item function, affix attribute modifiers, on-hit extra damage, damage negation, brewing mixes (30; brewing stand `data merge` awkward + shulker_shell + blaze_powder → `elysiumlib:resistance` in 420 ticks), attribute ceilings lifted (`attribute ... base set 5000`), tier hardening on spawn (`elysiumlib:tier_hardened` attachment), destructible cover (`#elysiumlib:fragile_cover`, >6 damage 25%) (tree → live candidates). Effects with behaviour: bleeding damage over time, detonation (fire → burst), salve heals, sundering makes incoming bigger, grievous/exhaustion/renewal scale healing, stunned cancels blows, tipsy/foresight/overload attribute modifiers (applied only → live (lib_effects)). Bleeding enchantment post_attack (placed only → live). Everything player-attribute-driven (crit, lifesteal, standing, dispatch, squads, hazards, null field, wounds, social, works, research, maker's mark, character, runes, weather, join) is client-only. Natural-spawn distance ≥32 (none, client).

## elysium-mobs — 27 mechanics (20 uncovered)

`/summon` never calls `initialise`, so a summoned mob has no variant; set `{ElysiumVariant:"elysiummobs:<variant>",ElysiumLevel:1,PersistenceRequired:1b}`. Abilities every 10 ticks; provoke with `damage <mob> 0.5 minecraft:mob_attack by @e[tag=v,limit=1]`.

| mechanic | console exercise | observable | coverage |
|---|---|---|---|
| bulwark absorption (interdictor/aegis) | drone with interdictor; 100 ticks | `AbsorptionAmount` 4.0 | none → live (mobs_abilities) |
| hardened (slagfist ×0.75 physical, ×1.25 fire) | reaver; `damage 4 magic` then `4 on_fire` | loss 3.0 then 5.0 | none → live |
| knitting heal (flenser…) | harrier `Health:5f`; 100 ticks | Health up | none → live |
| cornered, venomous, sundering, standard aura, echo, deadfall, blinding, swift, overcharged | provoke / damage; 20–100 ticks | effects on victim/self | none → live (mobs_abilities) |
| unshaken knockback resistance (`onSpawn` only from `initialise`) | client-only | attribute 1 | tree |
| variant roll/scaling/name on `initialise` | Choir phase summons are initialised | grunts carry `ElysiumVariant`, `ElysiumLevel`, CustomName | tree → live (mobs_bosses) |
| standard melee with telegraph; paced melee (harrier 8t, bulwark 40t) | provoke at a villager 80 ticks | hits count | none |
| Choir boss phases (summons Unsworn grunts) | `summon elysiummobs:choir {Health:50f}`; 40 ticks | `ElysiumPhase` 1; ≥2 grunts within 16 | none → live (mobs_bosses) |
| Praetor boss (shield regen, phase 1 strength + thorns) | summon; 100 ticks; then `{Health:60f}` + hit | `AbsorptionAmount` 37.5; `ElysiumPhase` 1 | none → live (mobs_bosses) |
| Boss fire immunity | summon on fire; 100 ticks | Health unchanged | none → live |
| Boss bar; natural spawns; dispatchers; register actions; roaming bands; despawn consent | client-only | — | tree/none |
| Every family and boss summons | catalogue | exists | live |
## elysium-npcs — 16 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Envoy arrival scheduler (standing band, 35% roll every 6000 ticks) | client-only | envoy appears with `Kind` | none |
| Visitor leaves after 24000 ticks (`ImperialEnvoy.tick`) | `summon elysiumnpcs:envoy {Kind:"sentinel",Transient:1b,TicksHere:23990}` vs `Transient:0b`; 40 ticks | transient gone, other stays | none → live (npcs_envoys) |
| Kind, name, save; no distance despawn; AI goals; attributes (max_health 40, knockback 0.6, follow 24) | summon; `attribute get` | fields | none → live (npcs_envoys) |
| Trade ledger, refusal below band, trade raises standing, sold-out redraw, court commissions, summons writs, writ of amnesty | client-only | screens, standing | tree/none |
| Workstation POIs and professions (`ElysiumStations`) | `setblock elysiumnpcs:gear_bench`; villager `profession:none` beside; `time set 1000`; 1200 ticks | `VillagerData.profession` `elysiumnpcs:gear_smith` (signal_mast→signaller, splicing_vat→splicer, void_crucible→voidwright, hydroponics_rack→hydroponicist, salvage_rig→salvager) | tree → live (npcs_villagers) |
| Profession trade tables | villager `profession:"elysiumnpcs:gear_smith",level:1`; `data get Offers.Recipes` | buys 12 copper_ingot, sells stone_pickaxe | tree → live (npcs_villagers) |
| Wandering trader pockets | 8 traders; read Offers | one of ours among them | tree |

## elysium-ores — 14 mechanics

| mechanic / where | console exercise | observable | coverage |
|---|---|---|---|
| Ore drop resolved from `c:` tags; silk touch; fortune | `loot spawn ... mine stone_ruby_ore iron_pickaxe` / silk touch / fortune ×50 | ruby ×1; ore; 1–4 | tree → live (ores_drops) |
| World-tier yield bonus | client-only | extra | tree |
| XP by band | `setblock air destroy` on an ore | experience_orb | none → live (ores_drops) |
| Family gate; ore worldgen (212 placed features) | tree; destructive fill count in a fresh forceloaded area | count > 0 | tree |
| Unifying loot modifier; unify recipes at server start; recompute on reload; `/elysium ores` | `elysium ores` over RCON; crafter with a losing item | report lines; canonical item | tree/none → live (ores_unify) |
| Cordon chest drops (13 add_table at 6%) | `loot spawn ... loot minecraft:chests/ancient_city` ×100 | ≥1 blight_crystal | tree → live (ores_cordon) |
| Cordon mob drops (killed_by_player) | client-only | — | tree |
| Trail-ruins relic_shard modifiers target `minecraft:chests/trail_ruins_*`, which do not exist in 1.21.1 (vanilla uses `archaeology/trail_ruins_*`) | `loot spawn ... loot minecraft:archaeology/trail_ruins_common` ×200 | 0 relic_shard | tree → BUG |

## elysium-solarpunk — 31 mechanics

Condenser, conduit grid, plates, kiln on fuel, press, loom, tank crop, harvester, tree harvester, hoppers (live). Wind capstan (y 200 vs 64: `energy` ≈ 1400 vs 100), dyed conduits carry only their colour, governor rules (`power_governor[condition=0,hold=3]` holds industry-class machines), algae bioreactor output (`output.Items` briquettes with water + sky + energy), briquette as fuel (1600), kiln on power with clay (brick + XP orb), kiln accepts only its tag, aquaponics fish byproduct (`slots.Items[{Slot:2}]` cod after 960 powered ticks), aquaponics siting, harvester replants and pays a seed, harvester on nether wart, water reclaimer (`tank.Fluid.amount` 200 per 200 ticks, 4× in rain) and cauldron filling, a fluid tank counts as water, contents spill, stone worldgen → live (solarpunk_more). Tree harvester never replants (only logs are cut, no sapling in the tray) → design gap to fix. Governor cycling and readouts are client-only.

## elysium-space — 50 mechanics

Generator, cable, pipe, tank, dyes, plates, structures, dimensions, terminal hopper (live). Diesel generator (`Tank0` diesel → `Energy` 4000/100 ticks), solar panel (10/t, 20/t on pale_moon/high_orbit, 0 at night), fuel refinery (oil → fuel + diesel), pumpjack per-chunk oil (`chunk_oil` attachment), water separator electrolysis (water → oxygen + hydrogen), machines push output into a tank, oxygen distributor room fill (glass 5×5×5 hollow: Tank0 and Energy fall only when sealed), world gravity attribute per dimension (`generic.gravity` 0.0132 pale_moon …), cinderveil ultrawarm (dispenser water bucket → empty), rocket phases on load, void crawler deflects arrows, cinder mobs immune to zombification, cinder boar breeding, moonfolk never summon golems, hollow moonfolk spit, mob loot, oil lakes → live (space_machines, space_worlds, space_mobs). Weather share, suits, hazards, canister, `/elysiumspace` commands, rocket launch/refuel/pickup/item, stations, waygates, freight launch/landing, jet suit, machine hand interactions, emgrove stripping, pale vines growth, natural spawns: client-only or tree.

## elysium-spawners — 17 mechanics

Tuning written into the vanilla fields (`setblock elysiumspawners:spawner{tuning:{count:2,delay:3,ignores_players:1b},SpawnData:{entity:{id:"minecraft:pig"}}}` → `SpawnCount` 6, `RequiredPlayerRange` 256), smashed spawner drops (`unstable_spawner` loot on destroy), spawner XP, capturing loot modifier (a zombie with capturing III `loot spawn ... kill` cows ×40 → ~10 cow_spawn_egg) → live (spawners). Cage spawning, redstone flag, ignores light, no_ai/silent/persistent flags, rune application, silk-touch pickup, unstable explosion, tuned placement, `/spawner`, advancements: client-only. Rune definitions and numbers: tree.

## elysium-transit — 14 mechanics

Waystone trickle charge (`held` ≈ 200 per 100 ticks; on a LOW grid ~64/t), idle vehicle falls, → live (transit). Rename, bind, learn, jump, vanished destination, readout, driving, refuel, player break, item placement: client-only. **Non-player damage destroys a vehicle and its cargo with no drop** (cactus, fire, mobs) and **nothing in-game can fill or empty `Cargo`** (no menu or item capability) → BUGS to fix.

## elysium-trinkets — 52 mechanics

Found-trinket chest modifiers (5 tables at 5%: `loot spawn ... loot minecraft:chests/ancient_city` ×100 → ≥1 trinket), per-trinket loot tables, potion charm filling recipe in a crafter → live (trinkets). Every worn passive (44) and the Curios slots are client-only; the client stage should wear a handful and read the effect.

## elysium-wilds — 8 mechanics

Structures and biomes (live: world_places). Compass: client-only. Room pieces carved (`place structure elysiumwilds:keep` then count stone_bricks), boulders and patches (`place feature elysiumwilds:boulder_mossy`) → live (wilds_places). Presets and the nearest-box rule: tree.

## What the first passes settled (27 September, evening)

* Portal blocks set by command clear themselves: only a rift key lights a portal, so the portal's life is the client stage's. The frame ring stands (`dungeons_frame`).
* Capturing needs a player's kill (`loot ... kill` has no player), a heavy grade lands on the library's own attributes (players only), cinder boars are hoglins and breed from a fungus, not from `InLove`: all three are the client stage's.
* A mob with no AI never walks off the push a fan gives it: the fan test reads the push (`Motion`), not the distance.
* Zombies burn at noon and confound any health reading on a zombie probe: the probes are husks.
* Several machines are stronger than one source: the alternator draws six dynamos' torque (two steam-class engines on one line), the motor draws 256 FE (a diesel through a transformer), the reactor's tier is extreme (a transformer steps it down to steel wire).
* The agronomy wild patches placed on no grass; kitchen crops now stand on dirt as well as farmland, as the wild patches need.
* A motor turns at 256 rpm and every Age I shaft shears below that (timber 64, brass 192), so a motor drives a steel plant beside it directly, or through a gear-down; the alternator draws six dynamos' torque, so two steam-class engines share its line, and its proof is the product on the wire, because engines burn out before a long read.
* A placed grammar structure stands on the natural surface under the stage, not on the stage: the scan for it looks low and wide.
* Random ticks reach only chunks a player is near, so budding geodes, kitchen crops and mushroom colonies grow only in the client stages.
* The client stage `tools/livetest/stages/stage_mechanics_client.ps1` (27 September, evening) covers the player-only rows it can: relic passives, axe stripping, grades, the reactor throttle, the cart's hold, capturing, budding and crop growth, the rift key and portal, the grave. It found every grade doing nothing (attribute names without the game's `generic.`/`player.` prefixes, CONVERSION.md §16.4). A stage is only as new as the jar the client booted with: rerun the launcher after every install.
* A rift portal takes a player who stands in it for a few seconds; a walk through a one-block portal overshoots it. In creative as in survival.
* `stage_screens_client.ps1` (27 September, night) opened every menu block once: the gear smithing table, salvager, reforging table and stone anvil; the reforge, gem cutting and augmenting tables; the arcane table, infusion basin, scriptorium and grand scriptorium; a kitchen cabinet and the tackle box; the coal generator and the freight terminal. Fifteen screens open and close on ESC. The transmuter and the gear bench open nothing, by design (item use and a villager's job site). Two minutes of night around a player brought an adept, a harrier and seven skeletons within 128 blocks: the squads spawn naturally.
* `stage_firstjoin_client.ps1` covers first join (POLISH.md P3): the kit, the declaration screen, the character commands, and a grave made in deep water (which water washed away until the headstone was declared solid).
