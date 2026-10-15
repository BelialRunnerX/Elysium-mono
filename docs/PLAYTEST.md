# Play-test plan, 25-26 September 2026

The integrated build (branch `integration`: agents 2-8's live tree plus agent 1's
work) in the D-drive test instance, `D:\testingbuilds\Elysium`. A fresh world
each run, so the worldgen shows. Every item below is one thing to do in game
and what success looks like; a miss becomes a fix in the generator or the code,
a validator rule, a negative test, a rebuild, and a re-test. Agent 8's work
(relics, haunts, passive fauna) is left out until it is handed back.

How each item is verified: the log (`logs\latest.log`) for errors, warnings
about missing models, textures or recipes, and the module's own log lines; a
screenshot where the thing is visual; a command's chat reply where it is not.

**Read against the tests, 2 October 2026.** The 69 unticked boxes below were
read against the live suite (`tools/livetest/scenarios.py`, 40 scenarios, and
`scenarios_mechanics.py`, 30; all 70 green on the builds of 1-2 October), the
eleven client stages in `tools/livetest/stages/` with their write-ups
(CONVERSION.md §16.4-16.16 and §17, the 28 September handoff), `check_obtainable.py`
and `/elysium audit harvest`. Each box now ends in a bracketed note. *proved*
means a scenario or stage asserts exactly what the box says and passed (a
scenario is named by its title, a stage by its file and shots); *partly* means
the mechanism is exercised but the visual or by-hand part the note names is
not; *open* means nothing exercises it. Counts: 3 proved, 41 partly, 25 open.
A note is not a tick: the boxes stay unticked until the by-hand run, whose
remaining work is listed after the checklist. Two boxes say what the code no
longer does: the rocket's Launch key is H, not G (since 28 September; G is
Curios' key), and the skill tree is not a Puffish Skills datapack but the
pack's own Ledger Web in the character sheet.

## 0. Boot and baseline (first, on every rebuild)

- [ ] Client starts; mods screen lists 28 Elysium mods plus Elysium Colonies; no crash, no red "missing model" lines, no datapack load failures, no registry errors. *(partly: every stage boots this client, and the 26 Sep sweep below found no registry errors and no missing Elysium models; the live "catalogue: every block stands somewhere, every item exists, every creature can be summoned" and "registries: every dimension loads, every effect applies, every enchantment sits on a sword, every damage type strikes" prove the registrations (elysium-all declares 28 modIds, elysiumcolonies its own); the mods screen itself and a per-boot read of the red lines are by hand)*
- [ ] Fresh world creates and loads; first join opens the character pick (race and class); it cannot be dismissed until chosen; Codex, almanac and starting kit arrive. *(proved: stage_firstjoin_client.ps1 - a fresh world through -FreshWorld, ESC refused on the declaration screen, Imperial and Marksman chosen and confirmed, codex, almanac, bread and torch found in the inventory; shots firstjoin_1-6)*
- [ ] JEI lists every Elysium creative tab without "Error populating items" (the atelier bug). *(partly: JEI is a non-dependency now and sits in mods/_superseded; stage_jei_client.ps1 with the jar dropped in showed the item panel and the alloy smelter's, crusher's and millstone's own recipe categories (CONVERSION §16.7); walking every Elysium tab for "Error populating items" is by hand)*
- [ ] Every creative tab renders: no purple-black textures, no black transparency, no missing item models (walk the tabs, screenshot each). *(partly: the live "catalogue: every block stands somewhere, every item exists..." sets every block and gives every item; purple-black textures, black transparency and missing models are by eye - the tab walk and its screenshots remain)*
- [ ] Log sweep at the end of each session: every WARN/ERROR from an `elysium*` namespace is either explained or filed. *(open)*

## 1. Colony simulation (priority)

Founding is MineColonies' own flow (supply camp, town hall `minecolonies:blockhuttownhall`, build tool). Elysium adds a daily pass, commands under `/elysium colony`, five style packs and the spaceport.

- [ ] Found a colony in the Overworld; citizens spawn (4) and walk, eat, sleep; place a builder hut, assign a builder, watch it build from requests. This is MineColonies itself working under our jars. *(partly: MineColonies runs under the jars - the self-founded Dunfield and Reedreach of 26 Sep (box below) spawned citizens and built the hall, the builder's hut and a residence over four days; founding by supply camp and build tool, assigning a builder and watching requests are by hand, and the colony stage CONVERSION §16.8 names, stage_seven_client.ps1, is not in the tree)*
- [ ] `/elysium colony` answers with name, citizen count, buildings, a Charter line, "Development X of 3600", who decides siting, and "would build a <hut> next". *(partly: `natural`, `claim` and `ruin` answer - live "colonies: a ruin marker raises a decayed building with a chest and founds no town", and `natural`/`claim` on the 26 Sep client; the main readout's name, count, Charter, Development, siting and "would build" lines are by hand)*
- [ ] `/elysium colony people` lists up to five citizens with jobs and stress. *(open)*
- [ ] After the first colony-day rollover: a citizen's happiness screen shows Elysium's extra factor; log says "now carries the frontier's reason". *(open)*
- [ ] Development grows only from employed citizens (about 10 per worker per day). Advance days with `/time add 24000`; watch the number. *(open - GrowthRules.PER_CITIZEN_DAY is 10, held by the harness only)*
- [ ] `/elysium colony steward town`, then advance days: the town founds the next hut from `settlement_plan.json` at 400 development (log "founded a minecolonies:builder at ..."); a BUILD work order appears in the town hall. *(open - GrowthRules.FIRST_UPGRADE is 400, harness only)*
- [ ] Stuck 4+ days inside the first 100: a grant delivers exactly what open requests need; owner gets the red "made no progress" line if still stuck. *(open - ReliefRules PATIENCE 4, GRANT_DAYS 100, REPORT_AFTER 4 match the box, harness only)*
- [ ] Every 3rd colony day: a free repair work order on one building (caretaking). *(open)*
- [ ] Raising: after level-1 buildings exist and 1,600 development, an UPGRADE order to level 2 (never past 3; 2 days between). *(open - GrowthRules.UNAIDED_LEVEL 3 and DAYS_BETWEEN 2 match; the 1,600 is not a named constant, read it off the climb)*
- [ ] 5 citizens: purple "Word of your settlement has travelled..." and the world-tier floor rises (`/elysium tier`). *(open - ColonyRules.CITIZENS_PER_TIER is 5, harness only)*
- [ ] Owning a colony sets a Suspicion floor of 2 per citizen (`/elysium standing`). *(open - ColonyRules.NOTICE_PER_CITIZEN is 2, harness only)*
- [ ] `/elysium colony market` quotes what you carry; `/elysium colony sell` refuses without a working port, sells with one; selling adds Suspicion. *(open)*
- [ ] Spaceport: `elysiumcolonies:elevator_anchor` in the border, 80+ `elevator_tether` up, `elevator_counterweight` on top, open sky; right-click reports "spaceport: N blocks of tether" or the exact fault; a braced leg (pylon, collar, deck) raises the daily quota. *(open - PortRules.MINIMUM_TETHER is 80; the anchor, dismantle and daily run are "tree" only in MECHANICS.md)*
- [ ] On a space world (`/elysiumspace travel emerald_bastion`), found a colony: the style switches to Bastion on the first day (log "builds in the X style"); same for Pale Moon to Pale Reach. *(open - the Bastion generates without the water-lake crash of 28 Sep (CONVERSION §16.8) and every space world force-loads live; founding there is by hand)*
- [ ] Style packs load: each of the five packs' supply camp, town halls 1-5, builder huts and `spaceport` blueprints open in the build tool without errors. *(open)*
- [ ] MineColonies raiders during a raid carry elite ranks matching the owner's tier. *(open)*
- [x] Self-founded towns: `/locate structure elysiumcolonies:settlement` finds a site; walking there (within simulation distance) turns the survey stake into a town hall, log "X founded itself at ..."; `/elysium colony natural` lists it with distance; citizens spawn; the builder hut and home go up whole after a day; a new building is laid out every two days; `/elysium colony claim` while standing in it makes it yours and its steward steps aside; a world whose charter does not settle (Nether, End, the Vale) never grows one. *(26 Sep: Dunfield and Reedreach founded themselves in the E client's ElysiumBoot world; builder and residence laid out; `natural` lists both, `claim` hands Dunfield over; the hall is raised after the town's first day: at Reedreach four day rollovers completed the town hall and the builder's hut and laid out a residence, and the town went from 1 to 4 citizens. That needed the seam to read the blueprint and queue the placement itself, because MineColonies' future-taking creative placement never queues anything; `natural` now shows each town's open orders and colony day)*

## 2. Worldgen

- [ ] Overworld overlay: `elysiumwilds:*` biomes appear (about half the inland surface); `elysiumwilds:biome_compass` search list opens and finds one; `/locate biome elysiumwilds:acacia_crag` works; biomes have the palette their name promises. *(partly: "world: every biome can be found in its dimension, and every structure somewhere" locates every wilds biome on the remade server world, crags and hoar hills included; the compass search, `/locate biome` from the client and the palettes are by eye)*
- [ ] Grammar structures: `/locate structure elysiumwilds:hamlet|watchtower|camp|ruin|lodge|mine|shrine|keep`; every room reachable from the door; no floating or buried rooms; loot chests present. *(26 Sep: all eight locate from 3000,3000 on the dedicated server; rooms not yet walked)* *(partly: all eight locate live in "world: ..." and "wilds: a keep places its rooms, a mossy boulder places on grass" counts a placed keep's stone-brick floor; walking the rooms, floating or buried pieces and the loot chests are by hand)*
- [ ] Emerald Bastion: flat green plains with black volcanic ranges (blackstone, basalt patches, magma seams); emgrove groves; rivers and lakes; no oceans; no monsters at night; emerald ore common underground; ore inside black mountains. *(partly: emerald_bastion force-loads ("registries: every dimension loads...") and its biomes and structures locate ("world: ..."); the water lake that crashed chunk generation is gone (CONVERSION §16.8); plains, ranges, emgroves, rivers, night safety and emerald ore are by eye)*
- [ ] Pale Moon, Frostfall, Cinderveil, High Orbit: terrain, sky (stars, Overworld in the sky), ores, mobs, structures each present; hazards tick without a suit; air gauge and suffocation in vacuum. *(26 Sep: every Cinderveil, Frostfall and Pale Moon structure, the meteor, the satellite, the operation base and the waygate locate in their worlds; terrain, sky, hazards and air not yet checked)* *(partly: every space world loads and its biomes and structures locate live; "space: gravity differs by world, the Cinderveil is ultrawarm, a rocket lands when loaded mid-flight" holds the Pale Moon's gravity and the Cinderveil's water; terrain, sky, mobs, hazards, the air gauge and suffocation need a suited player)*
- [ ] The Vale (`/execute in elysiumwilds:vale run tp @s ~ 120 ~`): 15 biomes, no hard biome seams, structures. *(partly: stage_vale_client.ps1 carried a survival player into elysiumwilds:vale through a frame ring and back (1-2 Oct, shots vale_1-4) and "world: ..." locates all 15 Vale biomes; seams and structures by eye are by hand)*
- [ ] Ores: Cordon ores in every stone family; aetherium, neutronium, voidglass in the Overworld; marble, limestone, alabaster. *(partly: "ores: a mined ore yields the family's canonical material through the unifier" loots five Cordon ores into their family tags, "ores: a ruby ore drops a ruby, silk touch keeps the ore..." and the neutronium feature placing in stone ("core: the spawner feature places, neutronium ore seeds stone...") are live, and every ore block stands in the catalogue; finding them in a real world's stone families is by hand - and marble, limestone and alabaster are solarpunk building stones (white_marble, white_limestone, alabaster), not ores)*
- [ ] Wild crops (barley, cabbage, chili, garlic, ginger, onion, rice, tomato), loomflax, downbloom, emberwood trees generate and render (no black backgrounds). *(partly: a wild cabbage patch places on grass ("agronomy: a bee flies without falling... a nest and a wild patch place"), wild and ripe loomflax drop seeds and fibre and emberwood smelts and burns long ("flora: ..."), and every block stands in the catalogue; natural generation and the black-background rendering are by eye)*
- [ ] Dungeon spawners `caged_invader`, `brutal_spawner`, `swarm_spawner` generate; break the cage to start a fight; brutal and swarm finish after finite waves and drop loot. *(partly: the brutal_spawner feature places live ("core: the spawner feature places..."); caged_invader and swarm_spawner in a real world, breaking the cage, the finite waves and the loot need a player)*
- [x] `tome_tower` structure generates. *(26 Sep: locates 185 blocks from 3000,3000)*

## 3. Space

- [ ] `/elysiumspace travel <world>` for each of the six; `/elysiumspace air` reports. *(partly: all five space worlds force-load live ("registries: every dimension loads...") and the 28 Sep colony stage travelled to the Bastion by this command; the command for each world and `/elysiumspace air` are by hand)*
- [ ] Rocket: craft from nose cone, fins, engine; place on `launch_pad`; fuel with `fuel_canister` or buckets; sit; press G; 10 s countdown; destination screen offers only worlds in fuel range; Bastion needs `large_fuel_tank`; arrival and landing. *(partly: stage_rocket_client.ps1 places a rocket on a launch_pad, fits the uranium motor, fuels it with uranium ingots, launches with the countdown to the destination screen and lands when nothing is chosen (shots rocket_1-5); "space: ... a rocket lands when loaded mid-flight" is live; crafting, canister or bucket fuel, the fuel-range filter, large_fuel_tank for the Bastion and arrival are by hand - and the Launch key is H, not G, since 28 Sep (G is Curios'))*
- [ ] Space suit: full set seals vacuum; chestplate air on the HUD drains 5 mB/s outside and refills in a filled room; `oxygen_canister` +1000; suffocation when empty. *(open - SuitAir's DRAIN_PER_SECOND 5 and CANISTER 1000 match the box; nothing exercises them)*
- [ ] `oxygen_distributor` with power and oxygen fills a sealed room (vacuum clears inside, not outside). *(partly: "space: a separator splits water, a refinery cracks oil, a diesel generator burns diesel, a panel makes power, a distributor fills a sealed room" is live - its oxygen falls inside a 5x5x5 glass box; that the vacuum clears inside and not outside needs a suited player)*
- [ ] Machines: `coal_generator`, `solar_panel` (x2 on airless worlds), `fuel_refinery`, `water_separator`, `pumpjack` (dry chunks exist), `diesel_generator`, `fluid_tank` (keeps contents when broken), `cable`, `pipe`: each screen opens, sneak-use gives a status line, energy and fluids move. *(partly: live - the coal generator burns and a cable carries into a separator, a tank pours into a pipe, dyed cables refuse each other, the separator splits water and pushes oxygen into the tank beside it, the refinery cracks oil, the diesel generator burns diesel, the solar panel makes power at noon (the four "space: ..." scenarios), and every machine takes a hopper's input; the coal generator's screen opened in stage_screens_client.ps1 and the oil finder read a chunk in stage_rocket_client.ps1; every other screen, the sneak-use status lines, the tank keeping its contents when broken and the pumpjack drawing are by hand)*
- [ ] Jet suit: J toggles flight and hover; burns its own fuel. *(open - J is SpaceKeys.JET_MODE; nothing exercises it)*
- [ ] Waygate both ways again on this build (regression from the earlier fix), including a click from 3.5 blocks. *(open)*
- [ ] Freight terminal: bucket fuels, empty hand opens chest, crouch picks destination and launches; the crate arrives. *(partly: the terminal takes a hopper's cobblestone ("inventories: every machine with a hopper-fed slot takes its input from a hopper above") and its chest screen opened in stage_screens_client.ps1; bucket fuelling, the crouch launch and the crate arriving are by hand)*
- [ ] Stations: each blueprint in High Orbit builds the station from steel and lunite; creative builds free; rockets to High Orbit land on it. *(open)*
- [ ] Mobs: moonfolk trade (head shake on refusal), hollow moonfolk melee + rime spit, void crawler arrow-proof, frost raptor, cinder boar never zombifies, cinderlings; correct palettes per world. *(partly: "space: a void crawler shrugs off arrows, cinder boars stand in the overworld, moonfolk raise no golem" is live and the catalogue summons every space creature; the trade head-shake, hollow moonfolk melee and rime spit, the frost raptor, the cinderlings and the palettes are by eye)*

## 4. Tech ages

Kinetics (Age I): hand crank winds; water wheel turns in flow; windmill bearing counts sails along its axis, more sails more force; shafts, cogwheels, gearbox transmit; millstone, press, saw, mixer process inputs (prod with an empty hand for a line readout; take output); furnace, oil and distillate engines burn fuel from the refinery; dynamo makes FE; anchor and wide anchor load chunks and draw torque; sail renders (the blockstate fix).

Industry (Age II): coke oven 3x3x3 forms, the wrong-block message names the block, coal to coke; blast furnace 3x3x4, iron + coke (third slot, one a blast, "Waiting for Coke" until it has it) to steel; an empty hand on a formed controller takes what it made; a brick used on the controller goes on the wall; coke burns 3,200 ticks in a furnace (29 Sep); grid: steam turbine, diesel engine, alternator, three wires, transformer, crusher, rolling mill, arc furnace, separator, motor; prod for readout; loss visible in the books.

Atomics (Age III): reactor hall 3 to 7 per side with fuel rods; port reports size or fault; heat against cooling as designed; single reactor core + coolant channel throttles with an empty hand and melts down under-cooled; cells, solar panel (cutout fix), servo, enricher, reaction chamber, centrifuge, uranium chain.

Foundry: alloy smelter on tier casings makes alloys; refinery returns the companion metal; Cordon alloys craft (cobalt and pearlium are dug, the seven alloys smelt from them; the walk of 29 Sep reaches every one — the old "known gap" is closed).

Logistics: drawers store (a void seal used on one fits it; crouch with an empty hand takes it back; a drawer key locks and unlocks; the status line says so, 29 Sep); bus + controller network; assembly core + assembler + terminal: hold an item up, chat answers steps and runs or why not; compressed blocks: nine to one to nine through all nine tiers; glass, ice and slime compressions render with transparency (this build's fix).

Solarpunk: solar condenser, wind capstan (height matters), ceramic kiln, harvesters on conduit and governor; machine numbers match the validator's.

## 5. Agronomy and arcana

- [ ] Pots + essence seeds + soils grow; accelerator speeds them on Age II power; waxed pots render without holes (this build's fix). *(26 Sep: andesite pot on dirt grows bamboo at 1.5x, harvested by hand; accelerator and waxed pots not yet checked)* *(partly: "agronomy: a pot grows a carrot on farmland soil, a copper vein makes raw copper on a grid, a pot makes stock over a fire" is live and the pot takes a coal seed from a hopper; the accelerator on Age II power and the waxed pots' rendering are still by hand)*
- [ ] Hive and apiary ladder; centrifuge spins combs into chunks (including the 21 foundry materials' combs, this build's regeneration); chunks smelt in the foundry via the raw-material tag. *(partly: "agronomy: a hive with flowers around it fills with comb" and "agronomy: a rock generator makes cobblestone by itself, and a centrifuge spins a comb on a grid" (an iron comb) are live; the apiary ladder, the 21 foundry combs and the chunks smelting in the foundry are by hand - the ores scenario does prove a chunk carries its family's raw tag)*
- [ ] Rock generator and vein generators produce. *(partly: the rock generator ("agronomy: a rock generator makes cobblestone by itself...") and the copper vein ("agronomy: a pot grows a carrot..., a copper vein makes raw copper on a grid...") produce live; the other six veins only stand in the catalogue)*
- [ ] Awakening altar layout (pedestals diagonal at 2, relays axial at 4); empty-hand click awakens a seed. *(open - AltarRules PEDESTAL_REACH 2 and RELAY_REACH 4 match the box)*
- [ ] Kitchen crops grow through 8 stages with transparent stages (this build's fix); `food_book` shows the diet bonus. *(partly: stage_mechanics_client.ps1 grew all 49 onions off age 0 under a player's random ticks (shot mech_8); the eight stages' transparency and the food_book's diet line are by eye)*
- [ ] Transmuter: hold an item up to dissolve into motes; empty hand opens buy-back. *(proved: stage_menus_client.ps1 - iron ingots in hand dissolve to motes and the empty hand opens the chooser (shots menus_9_*); the chooser had never opened before 28 Sep, CONVERSION §16.8)*
- [ ] Tinted glass (30) translucent; budding blocks without black pixels (earlier fixes). *(partly: budding iron grew its whole chain under a player's random ticks (stage_mechanics_client.ps1, 25 clusters) and "arcana: a cluster falls when its support goes" is live; translucency and black pixels are by eye)*

## 6. Gear, standing and progression

- [ ] Character sheet tabs (Codex or `/elysium sheet`); Alt detail tooltips; `/elysium stats|materials|standing|tier|respec`. *(partly: stage_firstjoin_client.ps1 runs `/elysium stats|standing|tier` and opens `/elysium sheet` (shots firstjoin_5-6), stage_web_client.ps1 the Web tab, stage_menus_client.ps1 the sheet with a trinket worn; `materials`, `respec` and the Alt tooltips are by hand)*
- [ ] Favor up on killing Unsworn Raiders; Suspicion up on killing Enforcers, mining ore, ascending gear; band-change chat lines; patrols and waves at high Suspicion. *(open)*
- [ ] Registry Beacon claim gives a permanent floor; Inspection Beacon plants at a threshold, doubles ore Suspicion in range, breaking counts as resisting. *(open)*
- [ ] Imperial Archive: right-click prints the file; sneak-right-click settles and issues a contract (deliver, cull, catalogue) with a deadline; failing costs Favor. *(open)*
- [ ] Research tab credits each of six fields from its deeds; Field Scanner on mobs and ores; sneak cycles who you file with. *(open)*
- [ ] Gear benches: reforge, ascension forge, rune socket, gem cutting, augmenting each open and act. *(partly: stage_menus_client.ps1 - the reforge table sockets a barrier rune then reforges with iron (`elysium:barrier` and `reforge_count` read back), the gem cutting table cuts a rune and spends the dust, the augmenting table keeps an affix and spends the sigil; stage_screens_client.ps1 opened all three; the ascension forge is in neither stage)*
- [ ] Rations give timed immunity to one hazard each (test on Frostfall, Cinderveil). *(open)*
- [ ] Arsenal ladder (e.g. cobalt warhammer), hammers, excavators, scythes, paxels; brushes on suspicious and fossiliferous dirt; totems render (cutout fix); grades roll on craft and pickup. *(partly: "arsenal: totems tend a field and heal, a barbed arrow bleeds" is live, stage_mechanics_client.ps1 shows a heavy grade sharpening a held sword (shot mech_5), and "gear: every module's gear is on the vanilla list it enchants by" holds the sabre, hammer and shortbow; the ladder in hand, brushing, the totem cutouts and grades rolling on craft and pickup are by hand)*
- [ ] Armory: gear smithing table builds modular gear; alloy forge, grader, press, reforging, salvager, starlight charger, stone anvil; `/elysium armory properties`. *(partly: stage_menus_client.ps1 - the gear smithing table turns blueprint paper into a blueprint and the reforging table rolls affixes; stage_screens_client.ps1 opened the smithing table, salvager, reforging table and stone anvil; "armory: set_parts builds gear, bounce boots break a fall, snow-walker boots stand on powder snow" is live; the alloy forge, grader, press, starlight charger and `/elysium armory properties` are by hand)*
- [ ] Trinkets: Curios slots plus the companion slot; mote, carrion drone, lattice shard, purge drone hover and act (found: the `trinkets/found` table rolls in ancient-city, bastion, end-city, stronghold-library and woodland-mansion chests, and every trinket has a recipe; 29 Sep). *(26 Sep: the `trinkets/found` table drops a Long Memory; Cordon loot modifiers add bio culture to jungle-temple rolls and lattice to end-city rolls; hover and act not yet checked)* *(partly: stage_menus_client.ps1 wears an aetherium band in the Curios screen and the sheet shows it (shots menus_10_*); "trinkets: a city chest carries a found trinket, and a crafter fills a potion charm" is live; the companion slot and the four companions hovering and acting are still by hand)*
- [ ] Enchanting: arcane table powered by the shelves; infusion basin; scriptorium and grand scriptorium; four tomes. *(partly: stage_menus_client.ps1 - the arcane table puts Sharpness II on an aetherium sword, the infusion basin Sharpness I from a book, the scriptorium files Sharpness III and sells a copy (shots menus_4-6); stage_screens_client.ps1 opened the grand scriptorium; "enchanting: a venom blade poisons its victim, and a vitality chestplate adds health" and every enchantment on a sword ("registries: ...") are live; the shelves' power and the four tomes are by hand)*
- [ ] Envoys arrive at high Favor or Suspicion (about every 5 minutes), trade or refuse; writs summon; writ of amnesty lowers Suspicion to the floor. *(partly: "npcs: an envoy keeps its kind and its health, a passing one leaves, a gear smith trades as one" is live; the five-minute arrival roll (ImperialEnvoy.VISIT_TICKS), trading or refusing, writs and amnesty are by hand)*
- [ ] Villager jobs with their six blocks. *(partly: a gear smith trades as one ("npcs: ... a gear smith trades as one") and the six blocks stand in the catalogue; villagers taking the job sites needs a player)*
- [ ] Skill tree (opt-in via `config/puffish_skills/config.json`). *(partly, and the box is stale: Puffish Skills was never one of the shipped jars and the datapack was retired on 28 Sep (CONVERSION §16.9); the skill tree is the Ledger Web in the sheet's Web tab, 723 nodes - stage_web_client.ps1 proves allocate, refund, the refusals and a Ledger Erasure paid in survival (shots web_1-5); reading the drawn web by eye is by hand)*

## 7. Mobs, dungeons, spawners

- [ ] Eight Elysium mobs spawn in Overworld squads at any hour; models and animations; drops; choir and praetor as dungeon bosses. *(partly: "mobs: the variants' abilities run, and the Choir and the Praetor keep their phases" is live, stage_screens_client.ps1 counted the natural spawns around a player after two minutes of night (shot spawns_night), and the catalogue summons all ten; squads by day, models, animations and drops are by eye)*
- [ ] Dungeons: rift frame ring (2x3 to 21x21) struck with a rift key opens; tiled dungeon, loot, boss room, return rift; next entry rebuilds. *(partly: stage_mechanics_client.ps1 - a rift key lights a 2x3 frame and a player standing in the portal arrives in a dungeon room (shots mech_10-11); "dungeons: a rift frame ring stands, and portal blocks set without a key do not" is live; rings up to 21x21, the tiled loot and boss rooms, the return rift and the rebuild are by hand)*
- [ ] Spawners: silk-touch a vanilla spawner to get `elysiumspawners:spawner`; runes apply; `/spawner` prints stats. *(partly: "spawners: a tuned spawner writes its tuning into the cage, a smashed spawner drops its unstable parts" is live and stage_mechanics_client.ps1's capturing III sword dropped a cow egg; silk-touching a vanilla spawner, applying runes and `/spawner` are by hand)*
- [ ] Bestiary's nine pre-existing beasts via eggs behave; their spawn modifiers name real mobs. *(partly: stage_creatures_client.ps1 photographs the beasts standing, moving and with their subtitles (shots creatures_*); "bestiary: a herald calls its court and the court does not call a court of its own", "bestiary: two fenstag in love make a calf" and the farm blocks are live and the catalogue summons all 168; behaviour by eye is by hand, and the spawn modifiers' targets are validator-held only)*

## 8. Everything else

- [ ] Transit: waystone bind and jump; grid-powered or self-charging; cart, truck, hauler on coal, faster on paving and gravel path. *(partly: "transit: a waystone trickles charge and charges faster on a grid, a cart falls to the ground, a truck destroyed by a cactus spills its cargo" is live and stage_mechanics_client.ps1's cart takes 32 cobblestone from a sneaking hand and gives it back (shot mech_3); binding and jumping, driving on coal and the paving and gravel speed-up are by hand)*
- [ ] Comforts: grave on death holds everything; trader crate captures a villager with trades; loot chests roll per player. *(partly: the grave is proved - stage_mechanics_client.ps1 holds five gold ingots and twelve levels with nothing on the floor, stage_firstjoin_client.ps1 keeps three ingots lost in deep water after the solid-grave fix (shots mech_7, firstjoin_7); "comforts: a parcel lands in the mailbox, a station counts its trader's work, pigeons breed" is live, but the capture itself and the per-player loot roll need a player)*
- [ ] Almanac opens the quest graph; quests progress. *(proved: stage_almanac_client.ps1 - a step finishes on holding a hand crank, the contents, chapter and step pages draw, the reward is taken from the step's last row, `/almanac status|claim` answer every case and the XP attachment rises (shots almanac_0-5; CONVERSION §16.12))*
- [ ] Atelier: a sample of each roster (roofs, stairs, bridges, furniture, diagonals, openings) places and renders; recipes exist in JEI. *(partly: "atelier: fences join, a door opens to redstone, a ladder holds a fall, a waterlogged balcony spreads its water" is live, the catalogue stands all 2,990 blocks and check_obtainable.py reaches every one by recipe; placing a sample of each roster and seeing it render, and the recipes in JEI, are by hand)*
- [ ] Flora: loomflax and downbloom planted from seeds, wild forms, emberwood set (log animation, door item, sapling grows). *(partly: "flora: a ripe loomflax drops fibre and a wild plant seeds, bone meal grows a sprout, emberwood burns long" is live and stage_mechanics_client.ps1 strips an emberwood log with an axe (shot mech_2); planting from seeds, downbloom, the log animation, the door item and the sapling growing are by hand)*

## What the by-hand run still has to do (2 October 2026)

The 25 open boxes and the by-hand halves of the 41 partly-proved ones, grouped
by what one client session can cover together. Each group is one world and one
sitting; the log sweep (§0) closes every one of them.

Drivers to start each sitting from, all in `tools/livetest/stages/` (the
September ones have not run since they were written, so expect their
coordinates and anchors to want a look first): the colony's days —
`stage_settle_client.ps1`, `stage_days_client.ps1`, `stage_report_client.ps1`
and `stage_seven_client.ps1`; the suited run — `stage_rocket_client.ps1`; the
benches — `stage_augment_client.ps1`, `stage_menus_client.ps1` and
`stage_screens_client.ps1`; the kitchen — `stage_kitchen_client.ps1` and
`stage_stations_client.ps1`; the worldgen and tab walk —
`stage_features_client.ps1`, `stage_agent8_client.ps1`,
`stage_creatures_client.ps1` and `stage_jei_client.ps1`; the standing walk
and the rift and road have no driver yet. `stage_vale_client.ps1` is the
pattern to copy: it fronts the game before every step and its header lists
the traps. How a sitting is run, the machine and the report it ends with, is
`docs/handoff/SITTING_BRIEF.md`; `tools/livetest/stages/client_up.ps1` brings
the client up on the installed jar and waits for the join.

1. **The colony's days** (one Overworld world, one colony, `/time add 24000`
   between reads): found it by supply camp and build tool and watch a builder
   work from requests; read every line of `/elysium colony` and `people`; the
   happiness factor and its log line after the first rollover; development
   growing only from the employed (10 a worker a day); `steward town` founding
   the next hut at 400 with its work order; the stuck grant inside the first
   100 days and the red "made no progress" line; the caretaking repair every
   third day; raising to level 2 at the threshold, never past 3, two days
   between; the purple "Word of your settlement" line at 5 citizens and
   `/elysium tier`; the Suspicion floor of 2 a citizen in `/elysium standing`;
   `market` and `sell` without and with a port, and the Suspicion it adds;
   the spaceport itself (anchor in the border, 80 tethers, counterweight, the
   right-click report and its faults, a braced leg raising the quota); a raid's
   elite ranks. Then with that colony in hand, the space half: travel to the
   Bastion and the Pale Moon, found there and watch the style switch on the
   first day; open the five packs' supply camp, town halls 1-5, builder huts
   and `spaceport` in the build tool.
2. **The suited run** (Frostfall, Cinderveil, Pale Moon, High Orbit): the
   suit sealing vacuum, the HUD air draining 5 mB/s and refilling in a filled
   room, a canister's +1000 and suffocation when empty; the distributor's room
   breathable inside and not outside; the jet suit on J, flight, hover and its
   fuel; the waygate both ways and the click from 3.5 blocks; each station
   blueprint in High Orbit built from steel and lunite, free in creative, a
   rocket landing on it; a ration's timed immunity on Frostfall and Cinderveil;
   `/elysiumspace travel` to each world and `air`; the rocket crafted from nose
   cone, fins and engine, fuelled from a canister and a bucket, the destination
   screen offering only worlds in range and the Bastion only with the large
   tank, arrival and landing; every machine's screen and sneak-use line, the
   tank keeping its contents when broken, the pumpjack on a wet chunk; freight
   fuelled by bucket, launched by crouch, the crate arriving; each world's
   terrain, sky, ores, structures by eye, hazards ticking without the suit;
   moonfolk trading and shaking their heads, hollow moonfolk melee and rime
   spit, the frost raptor, the cinderlings and their palettes.
3. **The standing walk** (Overworld, survival): Favor up on Unsworn Raiders,
   Suspicion up on Enforcers, ore and ascension, the band-change lines,
   patrols and waves at high Suspicion; the Registry Beacon's permanent floor;
   the Inspection Beacon planting at a threshold, doubling ore Suspicion in
   range and counting a break as resisting; the Imperial Archive's file and a
   contract with a deadline, and the Favor a failure costs; the Research tab
   crediting six fields, the Field Scanner on mobs and ores, sneak cycling the
   office; an envoy arriving on its five-minute roll, trading or refusing, a
   writ summoning, amnesty lowering Suspicion to the floor; `/elysium
   materials` and `respec`, and the Alt tooltips.
4. **Benches and stations on one platform** (creative): the awakening altar
   laid out (pedestals diagonal at 2, relays axial at 4) and a seed awakened
   by an empty hand; the ascension forge; the alloy forge, material grader,
   metal press and starlight charger, and `/elysium armory properties`; the
   shelves' power at the arcane table and the four tomes; a companion in its
   slot and the mote, carrion drone, lattice shard and purge drone hovering and
   acting; the accelerator on Age II power and a waxed pot's rendering; the
   apiary ladder and the 21 foundry combs through the centrifuge into the
   foundry; the six veins beside the copper one; the food_book's diet line;
   tinted glass and the budding blocks by eye; the arsenal ladder in hand,
   brushing suspicious and fossiliferous dirt, the totems' cutout, a grade
   rolling on craft and on pickup; a vanilla spawner silk-touched, runes
   applied, `/spawner`; six villagers taking their six job sites.
5. **The worldgen and tab walk** (creative, a fresh world): the mods screen
   and its count; every creative tab photographed; JEI's tabs with the jar
   dropped back in; the biome compass finding a wilds biome and the palettes
   of a dozen biomes; the eight grammar structures walked room by room with
   their chests; the Bastion by eye (ranges, emgroves, rivers, no oceans, no
   night monsters, emerald underground); the Vale's seams and structures;
   Cordon ores in each stone family and aetherium, neutronium and voidglass in
   the Overworld; the wild crops, loomflax, downbloom and emberwood growing and
   rendering; a caged invader broken, the brutal and swarm spawners' waves and
   loot; Elysium squads by day with their models, animations and drops; the
   nine beasts from eggs; an atelier sample of each roster placed; loomflax
   and downbloom planted, the emberwood log animation, door and sapling.
6. **The rift and the road** (survival): a 21x21 ring struck with a key, the
   tiled dungeon, its loot and boss room, the return rift, the rebuild on the
   next entry; a waystone bound and jumped to, grid-powered and self-charging;
   cart, truck and hauler on coal and faster on paving and gravel path; the
   trader crate capturing a villager with trades; a loot chest rolling per
   player.

## Third-party jars in the test instances

The E instances carry the NeoForge ports of Tridot and Valoria (built from `C:\ClaudeValoria\Tridot` and `...\Valoria`). On 26 Sep the client's world crashed forty seconds after joining at Dunfield: a Valoria sorcerer cast a spell and Tridot's `AbstractTridotArrow.getDefaultPickupItem` hit a null `arrowItem` during the arrow's construction. The fix (Tridot commit `ded8025`, 24 Sep) was already in the port repo and its `build/libs` jar, but both E instances still had the jar built before it. When a crash report names `tridot` or `valoria`, check the installed jar's date against the port repo's build before filing anything against Elysium.

## Known gaps (do not file as bugs)

Boot of the seven-jar pack, 26 Sep: client (fresh ElysiumBoot) and dedicated server both open with no registry errors, no missing Elysium models (the 25 missing-model lines left are MineColonies' own), and `Elysium Colonies ready` on both. The reference world still carries a `valoria:the_valoria` dimension key, which the loader reports and skips; harmless, and gone with the next fresh reference save.

The pack as ours alone, 26 Sep (Dylan: retire everything that isn't ours or a dependency of an Elysium mod): `tools/retire.py --all-but-ours` left seven jars in each E instance - `elysium-all`, `elysiumcolonies`, and what they declare required: MineColonies, Structurize, BlockUI, Domum Ornamentum and Curios. 121 more jars left the client and 110 the server (its client-only set went with them), every one with a row in `docs/LICENSING.md` (199 rows). JEI, Jade, the Xaero maps, ModernFix and the other conveniences went too, because none is ours or required; they sit in `mods/_superseded` and come back by moving the jar. Play-test items that named a third-party jar (Create, Mekanism, Powah, AE2, Refined Storage, Farmer's Delight, the Macaw sets) are moot on these instances now.

Log sweep after the retirement (26 Sep, E client): `elysiumkinetics:blockstates/anchor.json` had no model for radius 1 and 2 (the RADIUS property is 0..WIDEST on every anchor, the narrow anchor's blockstate stopped at its own width) - fixed in the generator and validator. The rest of the 466 missing-model lines are third-party (industrialforegoing 320, cluttered 291, dndecor 102, born_in_chaos, tfmg) and predate the pack. Two lines worth knowing: MineColonies logs "Couldnt analyze animal: elysiumcomforts:pigeon / charred_pigeon" while sizing animals for its herders (cosmetic; for the comforts module to look at: the pigeons are Animals it cannot classify), and the recipe book drops `everycomp:mcw/...` and `stonezone:mcw/...` recipes that named the retired Macaw jars (expected, harmless).

Retirement, 26 Sep, on the E instances: `tools/retire.py` moved the 46 jars the merged manifest marks DONE and nothing else requires into `mods/_superseded` in both `E:	estingbuilds\Elysium` and `E:\testingbuilds\ElysiumServer`, and wrote their licences and authors into `docs/LICENSING.md`. Left in place, still required by others: appliedenergistics2, create, woodworks. The client now runs 128 jars, the server 117 (its client-only mods live in `mods/_clientonly`).

Merged build, 26 Sep: the first jar built from agent 8's hand-back would not open any world on either instance. Registry loading failed on `add_forge_spawns.json` and `add_hoar_spawns.json`, spawn rules for `elysiumbestiary:forge` and `elysiumbestiary:hoar`, which are court labels, not creatures. They were leftovers of the rename: the bestiary generator writes a creature's files and never deletes the old name's. Eight files removed (two spawn rules, two eggs, two loot tables, two sheets); `elysium-bestiary/validate.py` now fails on any spawn rule, egg or loot table named for a creature no roster has, and `negtest_bestiary_leftovers.sh` proves it. The comforts fittings also needed the cutout layer (121 models), caught by the root models check.

Open observation (26 Sep, once): after the fourth relaunch of the E client in the same world, the player spawned at Dunwell (-9600, 9216), fell through unloaded terrain to y -6300, and the integrated server hung in a chunk-load wait (ModernFix watchdog dumps, server thread in `ServerChunkCache.getChunk`). Nothing Elysium is on the stack. The client had been force-stopped several times that hour, so a region file at that far site may be at fault; if it recurs, check the region files before anything else.

Negative tests: `tools/negtest/negtest_colonies3.sh` reports 18 caught and 9 missed on 26 Sep. The nine are exchange, trading and build-order cases whose edit anchors point at code rewritten on 24 Sep, so the mutation never lands and the case counts as missed; the rules themselves were not weakened. Re-anchoring them is a follow-up. `negtest_colonies6.sh` catches all 26 of its cases.

Exporter, storage bus, monitor, detector, interface and ender link are live-tested on a fed bus (29 Sep, `livetest.py --only logistics`: the exporter moves only what it was shown and one shown nothing is idle; the storage bus lends the chest it faces to the network; the detector lights a lamp at 64 of its item and not at 63; the interface hands the network to the hopper under it; the ender link carries between two networks on one channel once their plot has loaded again, which is when a channel is joined; each device bills the controller the draws NetRules lists, the monitor included - its sentence itself is player-only). The drawer's key holds live (locked and run dry, it keeps its kind, refuses dirt and takes its own kind); its void seal did not through a hopper: NeoForge's hopper hook calls a slot full at `getSlotLimit` and never offers to it, so a sealed drawer stalled at 2,048 exactly like a plain one - fixed the same day (`LogisticRules.ceiling`, `DrawerStore.getSlotLimit`; the seal half of the scenario passes once the jar is rebuilt); the satchel, rucksack, crate and vault are worn and placed packs (`PackRules`: 9, 36, 27 and 63 slots; packs take hoppers live); kitchen stations: pot and colony verified 26 Sep, the skillet (bacon and eggs in 240 ticks over a fire, idle when cold, refusing what is no recipe) and the cutting board (a sandwich, pasta and a crust in 200 ticks, two dough refused) live-tested 30 Sep; a reed rod with a barbed hook catches (26 Sep); the worm farm (a river fish to river bait, one every 1,200 ticks, sixteen at most, dropped when broken), the tackle box (every hook, bobber, bait and rod from a hopper, cobblestone refused, emptied below) and the four bobbers (stack to one, sit on a rod as the component its cast reads) live-tested 30 Sep; casting itself and fish and bee natural spawns need a player; geodes grow wild, thirty kinds, fifteen below the overworld and fifteen in the Nether (29 Sep); the Vale gate walked by hand on the client (1 Oct, stage_vale_client.ps1): a ring of ten Vale Frames lit by the surveyor's compass, a survival player standing in it carried to elysiumwilds:vale (the first trip's biome search holds the server some ten seconds), landed one block out from a return ring, and a step back through that ring brought home — note the way back lands on the far side of the home ring, so a ring at a cliff's edge drops you; the Vale side settles on dry ground — a 64-block spiral, then eight compass lines out to 256 for a shore — and a fresh ring's first trip on 2 Oct landed at a shore with land a step away, the ring straddling the waterline so its landing pad sat over the edge (settling inland is done, 2 Oct: the column found is walked on along its line, up to `PortalRules.SETTLE_IN_BLOCKS`, until the ring's sill and both landings are dry — the harness proves it, the live confirmation waits for a day the client may run); the Cordon metals, their shards and every trinket and companion are reached by the survival walk (check_obtainable.py, 29 Sep); roadmap items in each module's spec (contraptions, IE wiring, Mekanism chemistry, frontier crops, MysticalAgriculture gear, Quark decoration, and so on).

## Live tests on the dedicated server (27 September 2026)

The client stages check by eye; the server answers questions.
Run the suite on a quiet machine: with a client, a compile or a negtest's javac competing for the CPU the server falls hundreds of ticks behind ("Can't keep up!") and the entity-tick scenarios — pigeons breeding, a zombie burning, the Choir's phases, an envoy leaving, a cart falling — fail spuriously and pass on a rerun (1 Oct: six such of seventy, all green alone). Since 2 Oct the runner asks `tick query` before each scenario and waits up to a minute for the server to keep up; a scenario that still ran with the server behind is marked beside its result and counted in the summary, so such a FAIL reads as "rerun alone" rather than as a fault.

`tools/livetest/livetest.py` (CONVERSION.md §17) runs seventy scenarios (the
forty core ones, the mechanics pass, the catalogue, the registries and the
world sweep) over
RCON against `C:\testingbuilds\ElysiumServer` (RCON 127.0.0.1:25575, the
password in `ELYSIUM_RCON` or the test default), each on its own plot of a
force-loaded stage at 9000, 200, 9000. The server and the dev client
(`C:\testingbuilds\Elysium`) live under `ELYSIUM_TEST_ROOT` (default
`C:\testingbuilds`); the E: drive that held them was lost, and on 14 October
2026 the rigs were rebuilt on C:. Run it after installing a build
(`bash tools/livetest/install.sh`);
`--only <word>` runs a subset, `--verbose` prints every command and answer.
The stage is left standing so a failing rig can be probed with `/data get`.
Random ticks do not run there without a player, so crops and pots are
still checked in the client. The client stages live in
`tools/livetest/stages/` with their driver `mc.ps1`: `stage_runs_client.ps1`
covers the plates and dyes by hand placement, `stage_mechanics_client.ps1`
the player-only mechanics (relic passives, stripping, grades, the reactor
throttle, the cart's hold, capturing, growth, the rift portal, the grave), and
both take the screenshots. A stage is only as new as the jar the client
booted with: rerun the launcher after every install.

The dedicated server's world was remade twice on 27 September (the old ones are `world.before-crags-*` and `world.before-hoar-*` beside it) so its overworld carries the biome list in which every wilds biome owns its box; a world keeps the biome list it was made with, so a world made before this fix never grows a crag or a hoar hill.
