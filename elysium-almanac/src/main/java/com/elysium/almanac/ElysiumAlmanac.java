package com.elysium.almanac;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elysium Almanac: the book that tells you what to do next.
 *
 * <h2>What a quest book is for</h2>
 *
 * A player who has just installed a pack of this size has no idea what any of
 * it is, what order to do it in, or which of the several hundred new items is
 * the one that unlocks the next thing. The almanac is a <em>direction</em> —
 * not a reward scheme, not a checklist, and emphatically not a gate. Nothing
 * in the pack is locked behind it. It only ever says: here is what you could
 * do next, and here is why it is worth doing.
 *
 * <h2>Why it is a graph that gets proved</h2>
 *
 * The two ways a quest book fails are both invisible to whoever wrote it. A
 * step whose prerequisite nobody can finish is content the author sees in
 * their editor and the player never sees at all; two steps each waiting on the
 * other look fine from every angle except the one that matters, because a
 * player simply concludes the chapter is not finished yet and stops looking.
 *
 * <p>So the book is a declared graph, and the harness plays the whole of it
 * on every build — repeatedly taking everything the book currently offers —
 * and fails if that does not finish it.
 *
 * <h2>What it depends on</h2>
 *
 * The library, and nothing else. Every task is a string looked up at runtime,
 * so a chapter about a module nobody has installed is a chapter that is not
 * shown rather than a game that will not load.
 */
@Mod(ElysiumAlmanac.MODID)
public class ElysiumAlmanac {

    public static final String MODID = "elysiumalmanac";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumAlmanac");

    public ElysiumAlmanac(IEventBus modEventBus) {
        com.elysium.almanac.registry.AlmanacAttachments.ATTACHMENTS.register(modEventBus);
        com.elysium.almanac.registry.AlmanacItems.ITEMS.register(modEventBus);
        com.elysium.almanac.registry.AlmanacItems.TABS.register(modEventBus);
        LOGGER.info("Elysium Almanac ready");
    }
}
