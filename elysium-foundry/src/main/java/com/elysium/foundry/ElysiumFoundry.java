package com.elysium.foundry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Foundry: the fourth age.
 *
 * <h2>It adds no new physics, and that is the point</h2>
 *
 * Age I is rotation, Age II a divided grid, Age III a buffered one. There is
 * no fourth mechanism. What this age adds is the thing the other three were
 * built to pay for: a material chain deep enough that a factory becomes a
 * <em>supply</em> problem rather than a crafting problem.
 *
 * <h2>And it pays a debt</h2>
 *
 * {@code elysium-ores} generates ore for thirty-one materials, and most of
 * them were only ever supplied by the mods this conversion is replacing — so
 * without this module, a great many of the ores in the world drop nothing at
 * all once those jars go. The metals are here, and the module's validator
 * fails the build if an ore family the pack generates has no supplier
 * anywhere in it.
 *
 * <h2>What is checked</h2>
 *
 * Two things, over the whole declared chain, by execution rather than by
 * reading: no material is made of itself however far down, and no step gives
 * back more material than went into it. A pack that gets either wrong does not
 * crash — it leaks, and by the time anybody notices, every save has a chest of
 * free metal in it.
 */
@Mod(ElysiumFoundry.MODID)
public class ElysiumFoundry {

    public static final String MODID = "elysiumfoundry";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumFoundry");

    public ElysiumFoundry(IEventBus modEventBus) {
        com.elysium.foundry.registry.FoundryBlocks.BLOCKS.register(modEventBus);
        com.elysium.foundry.registry.FoundryItems.ITEMS.register(modEventBus);
        com.elysium.foundry.registry.FoundryItems.TABS.register(modEventBus);
        com.elysium.foundry.registry.FoundryBlockEntities.TYPES.register(modEventBus);
        com.elysium.foundry.registry.FoundryRecipes.TYPES.register(modEventBus);
        com.elysium.foundry.registry.FoundryRecipes.SERIALIZERS.register(modEventBus);
        LOGGER.info("Elysium Foundry ready");
    }
}
