package com.elysium.ores.event;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.ore.OreDrops;
import com.elysium.ores.unify.OreUnification;
import com.elysium.ores.unify.UnifyRecipes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * When the pack's answer is worked out.
 *
 * <h2>Once, when tags settle</h2>
 *
 * Families are tags, so nothing can be decided until tags exist — and they are
 * rebuilt on every {@code /reload}. Both the unification map and the ore
 * drops' cache are thrown away and rebuilt here, which means a player who
 * edits the priority in a datapack sees the result without restarting.
 *
 * <h2>Then the recipes, once the server is up</h2>
 *
 * Injected after the server starts rather than in the tag handler, because the
 * tag event also fires on the client, where there is no recipe manager worth
 * writing to and where a rewritten list would be replaced by the server's next
 * sync anyway.
 *
 * <h2>What deliberately has no handler</h2>
 *
 * The moment an item is crafted or smelted. Both events hand out the finished
 * stack and accept no replacement, so "unifying" there means emptying the
 * player's result and posting them a different item — a trick that works until
 * it races an automated crafter, and then silently eats materials. The honest
 * cover for machine output is the conversion recipe: a player who ends up with
 * the wrong lead is one crafting step from the right one, and never a step
 * from losing it. What loot can be unified is unified in
 * {@code loot/UnifyingModifier}, where replacing a stack is what the interface
 * is for.
 */
@EventBusSubscriber(modid = ElysiumOres.MODID)
public final class OreUnifyHandler {

    private OreUnifyHandler() {
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        OreDrops.invalidate();
        OreUnification.recompute();
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        UnifyRecipes.inject(event.getServer().getRecipeManager());
    }
}
