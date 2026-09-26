package com.elysium.agronomy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Agronomy: things that grow.
 *
 * <h2>The one thing this module must not do</h2>
 *
 * Growing resources is the feature that most often ruins a modpack. A seed
 * that yields iron turns a world into a field: mining stops mattering, the ore
 * ladder three ages were built around stops mattering, and everything priced
 * against scarce metal becomes free.
 *
 * <p>So the rule is written down and executed. A farm must be <em>worse</em>
 * than a mine — not forbidden, not nerfed into pointlessness, but worse by a
 * stated margin per hour, and the size at which it overtakes one has to be a
 * deliberate industrial project rather than a corner of a basement. The
 * harness compares the two on every build, so a retuned growth time moves a
 * number somebody can see rather than quietly turning the pack into a farming
 * simulator.
 *
 * <h2>What is in it</h2>
 *
 * A pot, which is farming without a field. A hive, which is farming without
 * either. A rock generator, which is the oldest cheat in Minecraft made
 * explicit and then priced. Crops for the frontier worlds, each tied to a
 * condition a player can read off the world they are standing on. And a diet:
 * the one mechanic here that changes how a player plays rather than what they
 * own.
 */
@Mod(ElysiumAgronomy.MODID)
public class ElysiumAgronomy {

    public static final String MODID = "elysiumagronomy";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumAgronomy");

    public ElysiumAgronomy(IEventBus modEventBus) {
        com.elysium.agronomy.registry.AgronomyBlocks.BLOCKS.register(modEventBus);
        com.elysium.agronomy.registry.AgronomyItems.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.AgronomyItems.TABS.register(modEventBus);
        com.elysium.agronomy.registry.AgronomyBlockEntities.TYPES.register(modEventBus);
        com.elysium.agronomy.registry.FishingBlockEntities.TYPES.register(modEventBus);
        com.elysium.agronomy.registry.AgronomyComponents.TYPES.register(modEventBus);
        com.elysium.agronomy.registry.BeeEntities.TYPES.register(modEventBus);
        com.elysium.agronomy.registry.BeeBlocks.BLOCKS.register(modEventBus);
        com.elysium.agronomy.registry.BeeBlocks.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.BeeItems.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.FishEntities.TYPES.register(modEventBus);
        com.elysium.agronomy.registry.FishItems.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.PotBlocks.BLOCKS.register(modEventBus);
        com.elysium.agronomy.registry.PotBlocks.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.KitchenItems.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.KitchenBlocks.BLOCKS.register(modEventBus);
        com.elysium.agronomy.registry.KitchenBlocks.ITEMS.register(modEventBus);
        com.elysium.agronomy.registry.DietAttachments.ATTACHMENTS.register(modEventBus);
        LOGGER.info("Elysium Agronomy ready");
    }
}
