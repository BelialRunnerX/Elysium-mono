package com.elysium.ores;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Ores: one of each material, and one of each in every stone.
 *
 * <h2>The two problems, which are the same problem</h2>
 *
 * A large pack says the same thing several times and then does not say it at
 * all. Five mods each add steel, so the player carries five steels and has to
 * remember which machine wanted which; and all five put their ores in stone
 * and deepslate, so every world past the Overworld is a place with nothing
 * under it. Both are the same failure — nobody was in a position to make a
 * decision about the pack as a whole — and this module is that decision.
 *
 * <h2>What it does, and what it refuses to do</h2>
 *
 * It picks one canonical item per material by a stated priority, completes
 * every mod's {@code c:} tags so a recipe written against the tag accepts all
 * the variants, and offers a one-for-one conversion from each variant to the
 * canonical. It then fills in every material-and-stone pair nobody had: lead
 * in the Nether, osmium in the End, copper in cinder stone.
 *
 * <p>It refuses to rewrite or remove another mod's recipes. Everything here is
 * additive; removing this module leaves a world that still works, with the
 * player's items still in it.
 *
 * <h2>What it names</h2>
 *
 * No other mod, anywhere in Java. Every material is a {@code c:} tag, every
 * variant is an optional tag entry, and a family nothing supplies generates
 * nothing and is mentioned nowhere. The validator enforces that: this module
 * loads next to vanilla alone.
 */
@Mod(ElysiumOres.MODID)
public class ElysiumOres {

    public static final String MODID = "elysiumores";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumOres");

    public ElysiumOres(IEventBus modEventBus) {
        com.elysium.ores.registry.OreBlocks.register(modEventBus);
        com.elysium.ores.registry.OreItems.register(modEventBus);
        com.elysium.ores.registry.OrePlacement.register(modEventBus);
        com.elysium.ores.registry.OreLoot.register(modEventBus);
        LOGGER.info("Elysium Ores ready: {} ores over {} materials",
                com.elysium.ores.data.OreCatalogue.ENTRIES.size(),
                com.elysium.ores.data.OreCatalogue.FAMILIES.size());
    }
}
