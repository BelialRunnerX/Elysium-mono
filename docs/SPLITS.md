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
| `naturalist: the fauna` | DONE | elysium-bestiary | twelve animals that do not attack you, over four kinds, priced against time rather than danger: what a pen gives an hour, with the wait derived from the yield so that leaving more always costs longer. No pen makes what a pen eats, nothing you can keep leaves what something in the dark leaves, the land animals are out in the daylight and the swimmers are in the water bucket. 48 negative tests |
| `naturalist: the guide` | PLANNED | elysium-almanac | the field guide: an in-game book that fills itself in as you meet things. elysium-almanac is the module that already writes pages about what the pack contains, and a second book in the bestiary would be a second copy of one |
| `naturalist: the foods` | PLANNED | elysium-agronomy | the 122 items, most of them food: what the animals drop, cooked, and what is made of it. It goes where the cooking is rather than where the animals are |
| `fangs_n_claws: the creatures` | DONE | elysium-bestiary | 27 creatures in 13 kindreds, and the proof that a variant is worth meeting twice. A roster grows by variant because variants are cheap: one scorpion becomes four, the count goes up by three and the number of fights goes up by none. So every creature carries one thing the player has to do differently and lives in one place, and within a kindred neither ever repeats — a frost scorpion freezes you in the snow and a nether scorpion sets you alight in the nether, and whichever you meet second is still a fight you have not had. A variant is also not a harder version: a whole kindred stands on one rung and the spread of threat inside it is capped at twice, because harder is what `tier` is for. 49 negative tests |
| `fangs_n_claws: the gear` | PLANNED | elysium-arsenal | ten weapons — the blowgun and its poisoned darts, the catching claw and its netherite rung, the fang and netherite daggers, the fire and hellfire pitchforks, the silver sword and the tomahawk — plus the velocity arrow. Several of them are the drops of creatures that already exist here, so the piece is blocked on nothing |
| `fangs_n_claws: the armour` | PLANNED | elysium-armory | four sets — fur, owl, scorpion and shrike — sixteen pieces and the shrike smithing template that upgrades into the last of them |
| `fangs_n_claws: the effects` | PLANNED | elysium-lib | six effects (mithridatic, frozen, acid, bleeding, venom, flamebrand) and five enchantments (critical backstab, quick killer, item catcher, scratch, blazing). Shared ground: needs the `shared` unit, and the gear above reads them |
| `fangs_n_claws: the keepsakes` | PLANNED | elysium-trinkets | the totem of frost, the sturdy saddle, the horse blanket and the snow duvet: what you carry rather than what you swing |
| `fangs_n_claws: the fittings` | PLANNED | elysium-atelier | three blocks — the bear trap, the ghost block and the vile lantern — and the materials and food the creatures leave: chitin, fur, long fang, black horn, giant feather, heavy claw, evil eye, spectral essence, vile fat and owlbear meat, raw and cooked |
| `friendsandfoes: the creatures` | DONE | elysium-bestiary | eleven creatures over eleven jobs a block would otherwise do — a copper golem that presses buttons, a glare that shows where the dark is, an iceologer that drops a cloud on your head — each one held under the block it replaces and none of them doing that block's job for free. A block sits there, and that is the whole of what a block has over something alive; so every companion is tamed, fed, or has to be kept close, and the harness sweeps thirty ways of costing nothing to prove it. Ten of them are the jar's; the eleventh is this module's own. 41 negative tests |
| `friendsandfoes: the beekeeper` | PLANNED | elysium-npcs | the villager profession and everything that hangs off it: five tiers of trades, the job-site point of interest, the three village house variants that place it, and the hero-of-the-village gift. It is a profession, not a creature, and elysium-npcs is where the professions are |
| `friendsandfoes: the beehives` | PLANNED | elysium-atelier | eleven wooden beehives, one per wood — acacia, bamboo, birch, cherry, crimson, dark oak, jungle, mangrove, pale oak, spruce and warped. A roster of one block in every wood, which is the shape this module already generates |
| `friendsandfoes: the copper` | PLANNED | elysium-atelier | fifteen blocks of copper button and lightning rod across the four oxidation states and waxed again: the same two blocks weathered, which is a variant roster rather than two new blocks |
| `friendsandfoes: the keepsakes` | PLANNED | elysium-trinkets | what the creatures leave and what it does when carried: the totem of freezing off the iceologer, the totem of illusion off the illusioner, the wildfire crown and its fragment, the crab claw, and the reach effect behind that last one — which this pack already has an attribute for, so the piece is the items rather than the mechanic. Blocked on nothing; the creatures it drops from already exist here |
| `friendsandfoes: the leavings` | PLANNED | elysium-flora | the buttercup a moobloom leaves and its flower pot, the crab egg a crab lays, and the disc in the abandoned mineshaft: the four things in this jar that are neither creature, fitting nor trade |
| `knightquest: the gear` | PLANNED | elysium-armory | 173 of the jar's 200 items: armour sets, shields and weapons. It is the bulk of the mod by a long way and none of it is a creature |
| `knightquest: the creatures` | PLANNED | elysium-bestiary | 13 of them — eldknight, fallen_knight, ghastling, ghosty, gremlin, lizzy and momma_lizzy, netherman, ratman, samhain, swampman, bad_patch, eldbomb — each one built around a single mechanic |
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
| `CreateDragonsPlus: the creatures` | DROP | - | there are none. Read out of the jar rather than off the name: its own language file has no `entity.` key at all, and its 61 blocks and 57 items are liquid dyes, dye buckets, dragon breath and a blaze smithing template. This row existed because the mod is called Dragons Plus |
| `CreateDragonsPlus: the machinery` | DROP | - | the rest is a forge and a blaze burner tier built on Create's heat, which `elysium-kinetics` deliberately does not have: this age burns fuel in an engine rather than under a pot |
| `create_aquatic_ambitions: the wilds` | PLANNED | elysium-wilds | the aquatic worldgen — the features and where they sit — which is the overworld overlay's business |
| `create_aquatic_ambitions: the creatures` | PLANNED | elysium-bestiary | the fish and the rest of what lives in it |

## The order it has to go in

`elysium-ores` first, then gear and armour in either order; creatures, the Vale
and charms are independent of all three. `elysium-lib` is shared ground and
whoever takes it holds up everybody who needs an attribute from it, so it is
worth doing early and quickly rather than thoroughly and late.
