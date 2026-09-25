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
| `Valoria: gear` | DONE | elysium-arsenal | (agent1) the weapon and bow ladder takes its rungs from a roster (`WeaponRules.RUNGS`): the game's six, then the ten Cordon tiers of the sets that had tools, placed by Valoria's own bonus damage by the rule gold obeys — a tie is a side-grade (less damage, gold's speed), a new damage is a new rung fifteen percent up, so reforging's ceiling never moves. A player meets sixty new weapons and sixty new bows (cobalt, lunite and phasium beside the ladder; relicsteel, biolith, bathysteel, slagsteel, jade, hemalite and nullsteel climbing past netherite), each made of its ingot's tag, with Valoria's durability and drawn in its ingot's colour. The broad tools stay on the game's six. The validator holds every rung to `elysium-ores`' roster |
| `Valoria: armour` | DONE | elysium-armory | (agent1) sixteen Cordon materials for the modular gear bench, generated from `elysium-ores`' roster in Valoria's own numbers and uncapped (a nullsteel plate is worth what Valoria said, well past netherite), each in the new `cordon` category and each granting the trait its set was known for: the infernal set's fire resistance, the ethereal set's night vision, the void sets' Null defence, the lunar set on the Pale Moon's lunite. Shields come from the bench like everything else. Not done: Valoria's 13 trim materials — the armory has no trim system |
| `Valoria: materials` | DONE | elysium-ores | (agent1) the roster behind all 17 sets, `rules/CordonMaterials.java`, read by the arsenal and the armory rather than retyped: 8 dug families (cobalt, pearlium, jade, pyratite, amber, ruby, sapphire, null amethyst) as new ore rows in every stone the pack is made of, dropping this module's own raw metal or gem; 7 alloys (nightgold, slagsteel, biolith, bathysteel, hemalite, nullsteel, relicsteel) made of a dug metal and something a Cordoned creature or ruin leaves; 3 found shards (phasium, lattice, chitin) and a woven cloth (mireweave); the lunar set stands on the Pale Moon's lunite. Every family has its items, storage block, `c:` tags, compaction and cooking recipes, and a sprite drawn fresh in the Voidforged style. The six drops the alloys eat ship here and are handed to the bestiary and the wilds to place |
| `Valoria: creatures` | PLANNED | elysium-bestiary | 50 entities, their spawns, drops and loot |
| `Valoria: the Vale` | DONE | elysium-wilds | the dimension and its 15 biomes: five bands of warmth crossed with three of rain, a partition of the climate plane so every biome wins its own rectangle and none can sit inside another's — proved by arithmetic over 322,267 checks and again by the sampling that once found a river that never generated. The structures are the eight room grammars, which the whole pack gets rather than this dimension alone |
| `Valoria: charms` | DONE | elysium-trinkets | (agent1) Valoria's pet slot as the **companion** slot (its own icon and order; not a Curios preset), and four companions in it where Valoria had one cosmetic pixie: the Mote lights the dark and is seen doing it, the Carrion Drone finds more and the Empire frowns, the Lattice Shard guards a player who holds still and hinders one who moves, the Purge Drone bleeds the Null gauge and draws on the body to do it. Found, never crafted, none ascending — each a rule with a price, as every found trinket here is |
| `Valoria: effects` | DONE | elysium-lib | (agent1) Valoria's seven status effects (Salve, Stunned, Tipsy, Exhaustion, Renewal, Foresight, Overload) beside Apothic's seven; its Nihility as the **Null field**: a sixth hazard on the Void element that fills a gauge instead of hurting, decays away from it, hurts past half, speaks past three quarters and reclaims the player at the top (or extracts them, if a world switches the failsafe), warded by a Null Ward effect whose shield leaks back into the gauge; 21 attributes (dash distance, strike radius, summon damage/count/lifetime, miss chance, a damage and a resistance per element, four Null stats), each read by a rule and uncapped; and three enchantments as data (Bleeding, Accuracy, Explosive Charge). Nothing in the library sources the field — the Cordoned Planet will, the way space sources vacuum. Docs: `docs/SPEC_VALORIA.md`, `docs/reference/valoria_inventory.md` |
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
| `ImmersiveEngineering: the multiblocks` | DONE | elysium-industry | the coke oven and the blast furnace, and the shape arithmetic under them: a shape is a grid of marks, every rotation and mirror of it is accepted, every one-block mutation of every one of those is refused, and the refusal names the block and where it is. No two shapes may be one arrangement of blocks. Neither machine wants a wire, which is what gives Age II a first hour that is not a power station |
| `ImmersiveEngineering: the wiring` | PLANNED | elysium-industry | connectors, relays and the wire that hangs between two posts rather than sitting in a block — the part of this jar that is a different transmission model from the grid already here |
| `ImmersiveEngineering: the machines` | PLANNED | elysium-industry | the excavator, the assembler, the bottling machine and the rest of the powered roster |
| `ImmersiveEngineering: the gear` | PLANNED | elysium-arsenal | the revolver, the railgun, the drill and the chemthrower |
| `tfmg: the steel` | DONE | elysium-industry | a blast furnace that wants no wire: iron and coke into steel, in a brick box that is the first thing Age II builds rather than the thing it builds after a power station |
| `tfmg: the oil` | PLANNED | elysium-industry | crude, the derrick that pumps it and the tower that cracks it. Needs a fluid chain, which no module in this pack has yet and which is a feature of its own rather than a roster |
| `tfmg: the decoration` | PLANNED | elysium-atelier | industrial panelling, catwalks and signage, which is a decoration roster in metals |
| `oritech: the machines` | PLANNED | elysium-industry | its own roster of processing steps, laid on the grid this module already has and priced against its books |
| `oritech: the compat` | PLANNED | elysium-arcana | the recipes naming `geore:` items, which are why GeOre is still in the mods folder although it is marked DONE. Nothing may retire that jar until these are ours |
| `Mekanism: the fission` | DONE | elysium-atomics | the reactor hall: a size you choose, cooling you plumb, and heat that goes as the cube against cooling that goes as the square |
| `Mekanism: the chemistry` | PLANNED | elysium-atomics | the ore chain from three to five - dust, clump, shard, crystal - and the gases that drive it. The ladder to three is done; this is the step that makes Age III about chemistry rather than about heat |
| `Mekanism: the logistics` | PLANNED | elysium-logistics | the pipes, the cardboard box and the quantum entangloporter, which are storage-and-movement and belong where the movement is |
| `MekanismTools: the gear` | PLANNED | elysium-arsenal | tools and armour in Mekanism's alloys, as new rungs on the ladders the arsenal already has |
| `industrialforegoing: the farm machines` | PLANNED | elysium-atomics | the planter, the harvester and the animal machines, joined to elysium-agronomy's crops the way the kinetics' saw is joined to its kitchen |
| `industrialforegoing: the rest` | PLANNED | elysium-atomics | the laser drill and the fluid machines, which want the same fluid chain tfmg's oil does |
| `createdeco: the fittings` | PLANNED | elysium-atelier | 1,221 blocks of brass, copper and industrial iron — catwalks, ladders, bars, panels, lamps and the rest. It is a decoration roster in metals, and the decoration rosters are the atelier's. Nothing in it is kinetic |
| `copycats: the blocks` | PLANNED | elysium-atelier | blocks that wear another block's texture: one mechanic and a handful of shapes, which is a decoration idea and belongs with the decoration |
| `interiors: the furniture` | PLANNED | elysium-atelier | seats, cushions, curtains, blinds and the shutters, built on Create's fittings in the jar and on the atelier's here |
| `create-stuff-additions: the fittings` | PLANNED | elysium-atelier | the decorative half — plates, lights, panels — in metals the atelier already has |
| `create-stuff-additions: the rest` | DROP | - | the tools and the backpack half: a jetpack and a wrench for a mod that is now ours, so the tool is a loop over our own roster |
| `CreateDragonsPlus: the creatures` | PLANNED | elysium-bestiary | the drakes and what they drop; entities rather than machinery, so it goes where the mob work is |
| `CreateDragonsPlus: the machinery` | DROP | - | the rest is a forge and a blaze burner tier built on Create's heat, which `elysium-kinetics` deliberately does not have: this age burns fuel in an engine rather than under a pot |
| `create_aquatic_ambitions: the wilds` | PLANNED | elysium-wilds | the aquatic worldgen — the features and where they sit — which is the overworld overlay's business |
| `create_aquatic_ambitions: the creatures` | PLANNED | elysium-bestiary | the fish and the rest of what lives in it |

## The order it has to go in

`elysium-ores` first, then gear and armour in either order; creatures, the Vale
and charms are independent of all three. `elysium-lib` is shared ground and
whoever takes it holds up everybody who needs an attribute from it, so it is
worth doing early and quickly rather than thoroughly and late.
