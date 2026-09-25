package com.elysium.wilds;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Wilds: where a world puts things, and how far you walk to find them.
 *
 * <h2>The bug this module was started for</h2>
 *
 * The game does not pick a biome from a weighted list. Every biome claims a box
 * in a six-dimensional climate space and each column of the world goes to
 * whichever box is nearest. So a biome can be declared, registered, translated,
 * listed in every compass and wiki in the pack, and <b>never generate
 * anywhere</b> — because some other box is nearer to every climate the noise
 * ever produces. Nothing reports it. The file is valid, the id resolves, the
 * biome is genuinely there; it has no territory.
 *
 * <p><b>This pack had one.</b> {@code elysiumspace:emerald_bastion_river}
 * claimed a weirdness band that sat entirely inside the plains' claim, on a
 * world where plains was declared first — so every climate it could have won
 * was a tie, and the game breaks ties by declaration order. It was found by
 * {@code check_worldgen.py} running {@link com.elysium.wilds.rules.WildRules}
 * over every dimension file in the repository, and by nothing else.
 *
 * <p>That is what this module is: the game's own nearest-box rule, against the
 * JDK alone, so that every world the pack ships can be asked what each of its
 * biomes actually gets before anybody has to go and look.
 */
@Mod(ElysiumWilds.MODID)
public class ElysiumWilds {

    public static final String MODID = "elysiumwilds";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumWilds");

    public ElysiumWilds(IEventBus modEventBus) {
        com.elysium.wilds.registry.WildComponents.TYPES.register(modEventBus);
        com.elysium.wilds.registry.WildItems.ITEMS.register(modEventBus);
        com.elysium.wilds.registry.WildItems.TABS.register(modEventBus);
        com.elysium.wilds.registry.WildStructures.TYPES.register(modEventBus);
        com.elysium.wilds.registry.WildStructures.PIECES.register(modEventBus);
        LOGGER.info("Elysium Wilds ready: one search of {} sample(s), no larger for "
                        + "anything", com.elysium.wilds.rules.WildRules.SAMPLES_PER_SEARCH);
    }
}
