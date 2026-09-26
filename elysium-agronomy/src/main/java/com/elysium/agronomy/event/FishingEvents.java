package com.elysium.agronomy.event;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.ElysiumHook;
import com.elysium.agronomy.rules.FishRules;
import com.elysium.agronomy.rules.TackleRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

import java.util.List;

/**
 * What comes up on a line, in water.
 *
 * <p>The game rolls its own loot when a float in water is reeled in with a
 * bite on it, and hands a copy of it to this event. That loot is not edited:
 * the event is cancelled and a draw from {@link TackleRules#table} is thrown
 * to the player instead, so every catch in water is a catch the harness has
 * proved something about.
 *
 * <p>Our own float carries the rod it came off &mdash; its rung, what was
 * fitted, and the bait &mdash; and the draw is made with those, so luck is
 * the rod's rung plus the fitted hook's. The game's own rod is the bare end
 * of a line: rung zero, nothing fitted, no bait. It still fishes <em>here</em>,
 * so its catch comes from the same table at the bottom of the ladder.
 *
 * <p>Lava and the void never reach this event: the game's float does not
 * fish there, and {@link ElysiumHook} draws its own catch in both.
 */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID)
public final class FishingEvents {

    private FishingEvents() {
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        FishingHook hook = event.getHookEntity();
        if (!(hook.level() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getEntity();
        int rung = 0;
        TackleRules.Tackle tackle = TackleRules.NONE;
        String bait = FishRules.NOTHING;
        ItemStack rod = ItemStack.EMPTY;
        if (hook instanceof ElysiumHook ours) {
            rung = ours.rung();
            tackle = ours.tackle();
            bait = ours.bait();
            rod = ours.reeling();
        }
        List<ItemStack> drops = Casts.pick(level,
                Casts.liquidUnder(level, hook.blockPosition()), rung, tackle, bait,
                level.getRandom());
        if (drops.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        Casts.deliver(level, hook, player, drops);
        Casts.spendBait(rod);
    }
}
