package com.elysium.colonies;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Colonies: the sentence the two mods have in common.
 *
 * <h2>The problem</h2>
 *
 * Two progressions in one world that do not know about each other. A player
 * who has spent forty hours building a town of thirty citizens is, to Elysium,
 * whatever their character level happens to be — possibly nothing: no world
 * tier, no standing, no better loot, and mobs that spawn around their capital
 * exactly as hard as the ones around a dirt hut. Meanwhile that town is raided
 * by the same barbarians that raided it when it was four huts.
 *
 * <p>Neither mod is wrong. They simply have no sentence in common, and this is
 * that sentence. It is deliberately short.
 *
 * <ol>
 *   <li><b>A town is progress.</b> Its size lifts its owner's world tier, the
 *       same way going somewhere deep does — a floor, compared against what
 *       they have otherwise earned rather than added to it.</li>
 *   <li><b>A town is conspicuous.</b> You cannot be quietly forgotten by the
 *       Empire while thirty people live somewhere with your name on it.</li>
 *   <li><b>A town is worth raiding properly.</b> Raiders are promoted at their
 *       target's world tier, which now includes what the town itself
 *       contributed. Capped, because a raid is an event a colony is meant to
 *       survive.</li>
 * </ol>
 *
 * <h2>What it is not</h2>
 *
 * It does not change how a colony works, what it builds, who lives in it or
 * how MineColonies decides a raid. Every number that mod owns is still that
 * mod's. This adds three readings of a colony and nothing else, and all three
 * are in {@code rules/ColonyRules}, executed by a harness.
 */
@Mod(ElysiumColonies.MODID)
public class ElysiumColonies {

    public static final String MODID = "elysiumcolonies";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumColonies");

    public ElysiumColonies(IEventBus modEventBus) {
        // The standing floor is a registration rather than a listener: the
        // library asks its sources when it needs them, so this has to be in
        // place before any player is loaded.
        com.elysium.colonies.event.ColonyStanding.install();
        // The spaceport's three blocks. Registered here rather than from a
        // static block for the usual reason: the registries do not exist yet
        // when this class is loaded, and a DeferredHolder resolved during
        // construction is a crash with a very confusing stack trace.
        com.elysium.colonies.registry.PortBlocks.BLOCKS.register(modEventBus);
        com.elysium.colonies.registry.PortBlocks.ITEMS.register(modEventBus);
        // The survey stake the world places where a town will found itself.
        com.elysium.colonies.registry.SettleBlocks.BLOCKS.register(modEventBus);
        // The one thing written into the host, and it is written into the
        // system the host built for it: a reason, beside its own housing and
        // food and security, in the screen a player is already reading.
        com.elysium.colonies.colony.Colonies.registerHappiness(modEventBus,
                (level, colonyId, person) ->
                        com.elysium.colonies.rules.CitizenRules.happiness(
                                com.elysium.colonies.citizen.ColonyMemory.of(level)
                                        .lifeOf(colonyId, person.id()).stress()));
        LOGGER.info("Elysium Colonies ready: a town is progress, conspicuous, "
                + "and worth raiding properly");
    }
}
