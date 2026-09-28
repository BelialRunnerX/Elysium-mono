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

## 0. Boot and baseline (first, on every rebuild)

- [ ] Client starts; mods screen lists 28 Elysium mods plus Elysium Colonies; no crash, no red "missing model" lines, no datapack load failures, no registry errors.
- [ ] Fresh world creates and loads; first join opens the character pick (race and class); it cannot be dismissed until chosen; Codex, almanac and starting kit arrive.
- [ ] JEI lists every Elysium creative tab without "Error populating items" (the atelier bug).
- [ ] Every creative tab renders: no purple-black textures, no black transparency, no missing item models (walk the tabs, screenshot each).
- [ ] Log sweep at the end of each session: every WARN/ERROR from an `elysium*` namespace is either explained or filed.

## 1. Colony simulation (priority)

Founding is MineColonies' own flow (supply camp, town hall `minecolonies:blockhuttownhall`, build tool). Elysium adds a daily pass, commands under `/elysium colony`, five style packs and the spaceport.

- [ ] Found a colony in the Overworld; citizens spawn (4) and walk, eat, sleep; place a builder hut, assign a builder, watch it build from requests. This is MineColonies itself working under our jars.
- [ ] `/elysium colony` answers with name, citizen count, buildings, a Charter line, "Development X of 3600", who decides siting, and "would build a <hut> next".
- [ ] `/elysium colony people` lists up to five citizens with jobs and stress.
- [ ] After the first colony-day rollover: a citizen's happiness screen shows Elysium's extra factor; log says "now carries the frontier's reason".
- [ ] Development grows only from employed citizens (about 10 per worker per day). Advance days with `/time add 24000`; watch the number.
- [ ] `/elysium colony steward town`, then advance days: the town founds the next hut from `settlement_plan.json` at 400 development (log "founded a minecolonies:builder at ..."); a BUILD work order appears in the town hall.
- [ ] Stuck 4+ days inside the first 100: a grant delivers exactly what open requests need; owner gets the red "made no progress" line if still stuck.
- [ ] Every 3rd colony day: a free repair work order on one building (caretaking).
- [ ] Raising: after level-1 buildings exist and 1,600 development, an UPGRADE order to level 2 (never past 3; 2 days between).
- [ ] 5 citizens: purple "Word of your settlement has travelled..." and the world-tier floor rises (`/elysium tier`).
- [ ] Owning a colony sets a Suspicion floor of 2 per citizen (`/elysium standing`).
- [ ] `/elysium colony market` quotes what you carry; `/elysium colony sell` refuses without a working port, sells with one; selling adds Suspicion.
- [ ] Spaceport: `elysiumcolonies:elevator_anchor` in the border, 80+ `elevator_tether` up, `elevator_counterweight` on top, open sky; right-click reports "spaceport: N blocks of tether" or the exact fault; a braced leg (pylon, collar, deck) raises the daily quota.
- [ ] On a space world (`/elysiumspace travel emerald_bastion`), found a colony: the style switches to Bastion on the first day (log "builds in the X style"); same for Pale Moon to Pale Reach.
- [ ] Style packs load: each of the five packs' supply camp, town halls 1-5, builder huts and `spaceport` blueprints open in the build tool without errors.
- [ ] MineColonies raiders during a raid carry elite ranks matching the owner's tier.
- [x] Self-founded towns: `/locate structure elysiumcolonies:settlement` finds a site; walking there (within simulation distance) turns the survey stake into a town hall, log "X founded itself at ..."; `/elysium colony natural` lists it with distance; citizens spawn; the builder hut and home go up whole after a day; a new building is laid out every two days; `/elysium colony claim` while standing in it makes it yours and its steward steps aside; a world whose charter does not settle (Nether, End, the Vale) never grows one. *(26 Sep: Dunfield and Reedreach founded themselves in the E client's ElysiumBoot world; builder and residence laid out; `natural` lists both, `claim` hands Dunfield over; the hall is raised after the town's first day: at Reedreach four day rollovers completed the town hall and the builder's hut and laid out a residence, and the town went from 1 to 4 citizens. That needed the seam to read the blueprint and queue the placement itself, because MineColonies' future-taking creative placement never queues anything; `natural` now shows each town's open orders and colony day)*

## 2. Worldgen

- [ ] Overworld overlay: `elysiumwilds:*` biomes appear (about half the inland surface); `elysiumwilds:biome_compass` search list opens and finds one; `/locate biome elysiumwilds:acacia_crag` works; biomes have the palette their name promises.
- [ ] Grammar structures: `/locate structure elysiumwilds:hamlet|watchtower|camp|ruin|lodge|mine|shrine|keep`; every room reachable from the door; no floating or buried rooms; loot chests present. *(26 Sep: all eight locate from 3000,3000 on the dedicated server; rooms not yet walked)*
- [ ] Emerald Bastion: flat green plains with black volcanic ranges (blackstone, basalt patches, magma seams); emgrove groves; rivers and lakes; no oceans; no monsters at night; emerald ore common underground; ore inside black mountains.
- [ ] Pale Moon, Frostfall, Cinderveil, High Orbit: terrain, sky (stars, Overworld in the sky), ores, mobs, structures each present; hazards tick without a suit; air gauge and suffocation in vacuum. *(26 Sep: every Cinderveil, Frostfall and Pale Moon structure, the meteor, the satellite, the operation base and the waygate locate in their worlds; terrain, sky, hazards and air not yet checked)*
- [ ] The Vale (`/execute in elysiumwilds:vale run tp @s ~ 120 ~`): 15 biomes, no hard biome seams, structures.
- [ ] Ores: Cordon ores in every stone family; aetherium, neutronium, voidglass in the Overworld; marble, limestone, alabaster.
- [ ] Wild crops (barley, cabbage, chili, garlic, ginger, onion, rice, tomato), loomflax, downbloom, emberwood trees generate and render (no black backgrounds).
- [ ] Dungeon spawners `caged_invader`, `brutal_spawner`, `swarm_spawner` generate; break the cage to start a fight; brutal and swarm finish after finite waves and drop loot.
- [x] `tome_tower` structure generates. *(26 Sep: locates 185 blocks from 3000,3000)*

## 3. Space

- [ ] `/elysiumspace travel <world>` for each of the six; `/elysiumspace air` reports.
- [ ] Rocket: craft from nose cone, fins, engine; place on `launch_pad`; fuel with `fuel_canister` or buckets; sit; press G; 10 s countdown; destination screen offers only worlds in fuel range; Bastion needs `large_fuel_tank`; arrival and landing.
- [ ] Space suit: full set seals vacuum; chestplate air on the HUD drains 5 mB/s outside and refills in a filled room; `oxygen_canister` +1000; suffocation when empty.
- [ ] `oxygen_distributor` with power and oxygen fills a sealed room (vacuum clears inside, not outside).
- [ ] Machines: `coal_generator`, `solar_panel` (x2 on airless worlds), `fuel_refinery`, `water_separator`, `pumpjack` (dry chunks exist), `diesel_generator`, `fluid_tank` (keeps contents when broken), `cable`, `pipe`: each screen opens, sneak-use gives a status line, energy and fluids move.
- [ ] Jet suit: J toggles flight and hover; burns its own fuel.
- [ ] Waygate both ways again on this build (regression from the earlier fix), including a click from 3.5 blocks.
- [ ] Freight terminal: bucket fuels, empty hand opens chest, crouch picks destination and launches; the crate arrives.
- [ ] Stations: each blueprint in High Orbit builds the station from steel and lunite; creative builds free; rockets to High Orbit land on it.
- [ ] Mobs: moonfolk trade (head shake on refusal), hollow moonfolk melee + rime spit, void crawler arrow-proof, frost raptor, cinder boar never zombifies, cinderlings; correct palettes per world.

## 4. Tech ages

Kinetics (Age I): hand crank winds; water wheel turns in flow; windmill bearing counts sails along its axis, more sails more force; shafts, cogwheels, gearbox transmit; millstone, press, saw, mixer process inputs (prod with an empty hand for a line readout; take output); furnace, oil and distillate engines burn fuel from the refinery; dynamo makes FE; anchor and wide anchor load chunks and draw torque; sail renders (the blockstate fix).

Industry (Age II): coke oven 3x3x3 forms, the wrong-block message names the block, coal to coke; blast furnace 3x3x4, iron + coke to steel; grid: steam turbine, diesel engine, alternator, three wires, transformer, crusher, rolling mill, arc furnace, separator, motor; prod for readout; loss visible in the books.

Atomics (Age III): reactor hall 3 to 7 per side with fuel rods; port reports size or fault; heat against cooling as designed; single reactor core + coolant channel throttles with an empty hand and melts down under-cooled; cells, solar panel (cutout fix), servo, enricher, reaction chamber, centrifuge, uranium chain.

Foundry: alloy smelter on tier casings makes alloys; refinery returns the companion metal; Cordon alloys craft (survival source is a known gap).

Logistics: drawers store; bus + controller network; assembly core + assembler + terminal: hold an item up, chat answers steps and runs or why not; compressed blocks: nine to one to nine through all nine tiers; glass, ice and slime compressions render with transparency (this build's fix).

Solarpunk: solar condenser, wind capstan (height matters), ceramic kiln, harvesters on conduit and governor; machine numbers match the validator's.

## 5. Agronomy and arcana

- [ ] Pots + essence seeds + soils grow; accelerator speeds them on Age II power; waxed pots render without holes (this build's fix). *(26 Sep: andesite pot on dirt grows bamboo at 1.5x, harvested by hand; accelerator and waxed pots not yet checked)*
- [ ] Hive and apiary ladder; centrifuge spins combs into chunks (including the 21 foundry materials' combs, this build's regeneration); chunks smelt in the foundry via the raw-material tag.
- [ ] Rock generator and vein generators produce.
- [ ] Awakening altar layout (pedestals diagonal at 2, relays axial at 4); empty-hand click awakens a seed.
- [ ] Kitchen crops grow through 8 stages with transparent stages (this build's fix); `food_book` shows the diet bonus.
- [ ] Transmuter: hold an item up to dissolve into motes; empty hand opens buy-back.
- [ ] Tinted glass (30) translucent; budding blocks without black pixels (earlier fixes).

## 6. Gear, standing and progression

- [ ] Character sheet tabs (Codex or `/elysium sheet`); Alt detail tooltips; `/elysium stats|materials|standing|tier|respec`.
- [ ] Favor up on killing Unsworn Raiders; Suspicion up on killing Enforcers, mining ore, ascending gear; band-change chat lines; patrols and waves at high Suspicion.
- [ ] Registry Beacon claim gives a permanent floor; Inspection Beacon plants at a threshold, doubles ore Suspicion in range, breaking counts as resisting.
- [ ] Imperial Archive: right-click prints the file; sneak-right-click settles and issues a contract (deliver, cull, catalogue) with a deadline; failing costs Favor.
- [ ] Research tab credits each of six fields from its deeds; Field Scanner on mobs and ores; sneak cycles who you file with.
- [ ] Gear benches: reforge, ascension forge, rune socket, gem cutting, augmenting each open and act.
- [ ] Rations give timed immunity to one hazard each (test on Frostfall, Cinderveil).
- [ ] Arsenal ladder (e.g. cobalt warhammer), hammers, excavators, scythes, paxels; brushes on suspicious and fossiliferous dirt; totems render (cutout fix); grades roll on craft and pickup.
- [ ] Armory: gear smithing table builds modular gear; alloy forge, grader, press, reforging, salvager, starlight charger, stone anvil; `/elysium armory properties`.
- [ ] Trinkets: Curios slots plus the companion slot; mote, carrion drone, lattice shard, purge drone hover and act (`/give`, since nothing awards them yet). *(26 Sep: the `trinkets/found` table drops a Long Memory; Cordon loot modifiers add bio culture to jungle-temple rolls and lattice to end-city rolls; hover and act not yet checked)*
- [ ] Enchanting: arcane table powered by the shelves; infusion basin; scriptorium and grand scriptorium; four tomes.
- [ ] Envoys arrive at high Favor or Suspicion (about every 5 minutes), trade or refuse; writs summon; writ of amnesty lowers Suspicion to the floor.
- [ ] Villager jobs with their six blocks.
- [ ] Skill tree (opt-in via `config/puffish_skills/config.json`).

## 7. Mobs, dungeons, spawners

- [ ] Eight Elysium mobs spawn in Overworld squads at any hour; models and animations; drops; choir and praetor as dungeon bosses.
- [ ] Dungeons: rift frame ring (2x3 to 21x21) struck with a rift key opens; tiled dungeon, loot, boss room, return rift; next entry rebuilds.
- [ ] Spawners: silk-touch a vanilla spawner to get `elysiumspawners:spawner`; runes apply; `/spawner` prints stats.
- [ ] Bestiary's nine pre-existing beasts via eggs behave; their spawn modifiers name real mobs.

## 8. Everything else

- [ ] Transit: waystone bind and jump; grid-powered or self-charging; cart, truck, hauler on coal, faster on paving and gravel path.
- [ ] Comforts: grave on death holds everything; trader crate captures a villager with trades; loot chests roll per player.
- [ ] Almanac opens the quest graph; quests progress.
- [ ] Atelier: a sample of each roster (roofs, stairs, bridges, furniture, diagonals, openings) places and renders; recipes exist in JEI.
- [ ] Flora: loomflax and downbloom planted from seeds, wild forms, emberwood set (log animation, door item, sapling grows).

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

Exporter, storage bus, monitor, detector, interface and ender link are unverified (the importer works on a fuelled grid, 26 Sep); satchel, rucksack, crate, vault inert; kitchen stations: pot and colony verified 26 Sep, skillet and board being verified; a reed rod with a barbed hook catches (26 Sep); bobbers, worm farm and tackle box unverified; fish and bee natural spawns unverified; geodes have no worldgen or recipe; Cordon alloy drops and shards have no survival source; nothing awards trinkets and companions; roadmap items in each module's spec (contraptions, IE wiring, Mekanism chemistry, frontier crops, MysticalAgriculture gear, Quark decoration, and so on).

## Live tests on the dedicated server (27 September 2026)

The client stages check by eye; the server answers questions.
`tools/livetest/livetest.py` (CONVERSION.md §17) runs fifty-five scenarios (the
twenty-one core ones, the mechanics pass, the catalogue, the registries and the
world sweep) over
RCON against `E:\testingbuilds\ElysiumServer` (RCON 127.0.0.1:25575, the
password in `ELYSIUM_RCON` or the test default), each on its own plot of a
force-loaded stage at 9000, 200, 9000. Run it after installing a build;
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
