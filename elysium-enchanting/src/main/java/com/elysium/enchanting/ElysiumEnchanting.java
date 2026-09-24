package com.elysium.enchanting;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Enchanting: a table whose power you build around it.
 *
 * <h2>What this module replaces</h2>
 *
 * Vanilla's enchanting table has one input — how many bookshelves are near it
 * — and the ceiling arrives about an hour into a world and never moves again.
 * Everything a player can do about enchanting after that is "wait for a better
 * roll".
 *
 * <p>This asks four questions instead, and different blocks answer different
 * ones: how much power the table has, how uncertain it is, how rare what it
 * offers may be, and how much of the uncertainty has been paid off. A room
 * built for power and a room built for rarity are different rooms.
 *
 * <h2>What it does not do</h2>
 *
 * It does not reach for Apothic Enchanting, Apotheosis or Silent Gear, in any
 * direction, optional or otherwise. The validator enforces that, the same way
 * every other module in this repository enforces its own independence.
 */
@Mod(ElysiumEnchanting.MODID)
public class ElysiumEnchanting {

    public static final String MODID = "elysiumenchanting";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumEnchanting");

    public ElysiumEnchanting(IEventBus modEventBus) {
        com.elysium.enchanting.registry.EnchantingBlocks.register(modEventBus);
        com.elysium.enchanting.registry.EnchantingItems.register(modEventBus);
        com.elysium.enchanting.registry.EnchantingBlockEntities.register(modEventBus);
        com.elysium.enchanting.registry.EnchantingMenus.register(modEventBus);
        com.elysium.enchanting.registry.EnchantingAttributes.register(modEventBus);
        LOGGER.info("Elysium Enchanting ready");
    }
}
