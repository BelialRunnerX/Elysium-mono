"""
Declared sources for the homestead group: elysiumcomforts, elysiumflora,
elysiumsolarpunk, elysiumatelier. Each one pinned to the Java that hands the
thing out; see OBTAIN_BRIEF.md. Loaded by check_obtainable.py through
OBTAINABLE_SOURCES_EXTRA.

Left undeclared on purpose (real gaps, in gaps_homestead.md):
  creature:elysiumcomforts:pigeon   - no spawn anywhere (no biome modifier, no
                                      structure, no code); breeding needs a pair.
  elysiumcomforts:lost_mail         - nothing creates it: PostRules prices a
                                      loss (LOSS_IN_A_THOUSAND) that no mailbox
                                      code ever rolls.
"""

FLORA = "elysium-flora/src/main/java/com/elysium/flora"
COMFORTS = "elysium-comforts/src/main/java/com/elysium/comforts"
SOLARPUNK = "elysium-solarpunk/src/main/java/com/elysium/solarpunk"


def declare(declared):
    # --- elysium-flora ------------------------------------------------------
    #
    # The wild plants (wild_loomflax, wild_downbloom) are placed by
    # neoforge/biome_modifier/wild_crops.json in #minecraft:is_overworld, which
    # the checker already follows; they have no loot table (noLootTable() in
    # FloraBlocks) and drop their seed from WildPlantBlock.getDrops. The gate is
    # the overworld, because "needs" cannot name a block only the world places.
    declared("/^elysiumflora:(loomflax|downbloom)_seeds$/", ["dimension:minecraft:overworld"],
             f"{FLORA}/block/WildPlantBlock.java",
             "new ItemStack(seed.get(), FloraRules.WILD_SEEDS)",
             "a wild loomflax or downbloom (worldgen wild_crops, every overworld biome) breaks for two of its seeds "
             "in WildPlantBlock.getDrops; the blocks have no loot table")

    # The seed is an ItemNameBlockItem of the crop: planting it on farmland
    # (FibreCropBlock.mayPlaceOn is farmland only) puts the crop block down.
    declared("placed:elysiumflora:loomflax", ["elysiumflora:loomflax_seeds", "#minecraft:hoes"],
             f"{FLORA}/registry/FloraItems.java",
             "FloraBlocks.LOOMFLAX.get(), new Item.Properties()",
             "loomflax seeds planted on hoed farmland are the loomflax crop")
    declared("placed:elysiumflora:downbloom", ["elysiumflora:downbloom_seeds", "#minecraft:hoes"],
             f"{FLORA}/registry/FloraItems.java",
             "FloraBlocks.DOWNBLOOM.get(), new Item.Properties()",
             "downbloom seeds planted on hoed farmland are the downbloom crop")

    # A ripe crop drops its fibre from FibreCropBlock.getDrops (no loot table:
    # the yield reads the breaker's world tier).
    declared("elysiumflora:loomflax_fibre", ["elysiumflora:loomflax_seeds", "#minecraft:hoes"],
             f"{FLORA}/block/FibreCropBlock.java",
             "drops.add(new ItemStack(fibre.get(), amount));",
             "a ripe loomflax crop (age 7) breaks for loomflax fibre and a seed in FibreCropBlock.getDrops")
    declared("elysiumflora:downbloom_tuft", ["elysiumflora:downbloom_seeds", "#minecraft:hoes"],
             f"{FLORA}/block/FibreCropBlock.java",
             "drops.add(new ItemStack(fibre.get(), amount));",
             "a ripe downbloom crop (age 7) breaks for downbloom tufts and a seed in FibreCropBlock.getDrops")

    # An axe strips emberwood in FloraHandler.onToolModification, reading
    # FloraBlocks.stripping(); the stripped blocks' loot tables drop themselves.
    declared("placed:elysiumflora:stripped_emberwood_log", ["elysiumflora:emberwood_log", "#minecraft:axes"],
             f"{FLORA}/event/FloraHandler.java",
             "FloraBlocks.stripping().get(state.getBlock())",
             "an axe on a placed emberwood log strips it (BlockToolModificationEvent, AXE_STRIP)")
    declared("placed:elysiumflora:stripped_emberwood_wood", ["elysiumflora:emberwood_wood", "#minecraft:axes"],
             f"{FLORA}/event/FloraHandler.java",
             "FloraBlocks.stripping().get(state.getBlock())",
             "an axe on placed emberwood wood strips it (BlockToolModificationEvent, AXE_STRIP)")

    # --- elysium-comforts ---------------------------------------------------

    # GraveKeeper.onDrops puts a grave where a player died carrying anything.
    # Its loot table is empty, so the block is met, never held.
    declared("placed:elysiumcomforts:grave", [],
             f"{COMFORTS}/event/GraveKeeper.java",
             "setBlockAndUpdate(where, ComfortBlocks.GRAVE.get().defaultBlockState()",
             "a player who dies carrying anything is buried in a grave at the spot (LivingDropsEvent)")

    # trader_crate_full is not a registered item: it is the model the
    # trader_crate switches to (predicate elysiumcomforts:occupied) once
    # TraderItem.interactLivingEntity has written a villager into it. The
    # villager is vanilla (villages, overworld); "creature:minecraft:villager"
    # is never satisfied by the checker, so the gate is the overworld.
    declared("elysiumcomforts:trader_crate_full", ["elysiumcomforts:trader_crate", "dimension:minecraft:overworld"],
             f"{COMFORTS}/item/TraderItem.java",
             "stack.set(ComfortComponents.TRADER.get(), written);",
             "a trader's crate used on a villager takes it in, and the crate draws as trader_crate_full")

    # A pigeon (home: overworld) that stays 300 ticks where dimensionType()
    # is ultraWarm turns into a charred pigeon (PostRules.BIRDS; turn()).
    declared("creature:elysiumcomforts:charred_pigeon",
             ["creature:elysiumcomforts:pigeon", "dimension:minecraft:the_nether"],
             f"{COMFORTS}/entity/PigeonEntity.java",
             "PigeonEntity made = ComfortEntities.ALL.get(next.id()).get().create(server);",
             "a pigeon carried into the nether turns into a charred pigeon after 300 ticks (PostRules.BIRDS)")

    # --- elysium-solarpunk --------------------------------------------------

    # The algae bioreactor grows a briquette per 400 powered ticks, sited
    # touching water under open sky; its power comes from a generator beside it
    # or down a conduit (the solar condenser is the one its design pairs it with;
    # a wind capstan or any FE source serves as well).
    declared("elysiumsolarpunk:biomass_briquette",
             ["elysiumsolarpunk:algae_bioreactor", "elysiumsolarpunk:solar_condenser", "minecraft:water_bucket"],
             f"{SOLARPUNK}/machine/AlgaeBioreactorEntity.java",
             "new ItemStack(SolarpunkContent.BIOMASS_BRIQUETTE.get())",
             "a powered algae bioreactor touching water under open sky makes a biomass briquette every 400 ticks")
