package com.elysium.colonies.event;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.ColonyRules;
import com.elysium.lib.tier.ElysiumTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A town is progress.
 *
 * <h2>What this says</h2>
 *
 * The size of a player's colony lifts their world tier, through the same
 * single-writer seam the world floor has always used — so it is a floor, never
 * a bonus, and never falls. A mayor at character level five with a town of
 * thirty is tier six; the same player, once they have levelled to sixty, is
 * tier six from their level and the town adds nothing. That is the correct
 * answer to "does my town make me stronger": no, it makes you
 * <em>noticed</em>, and the world answers accordingly.
 *
 * <h2>Why a floor rather than a term</h2>
 *
 * Because it has to survive the colony. A player who builds a town, reaches
 * tier six by it, spends the evening on Cinderveil and comes home to find the
 * place raided down to ten citizens should not arrive at tier two. Progress
 * that can be taken away by something that happened while you were out is not
 * progress, it is a loan.
 *
 * <h2>How often</h2>
 *
 * Every {@link #INTERVAL} ticks, and only on the server. The question costs a
 * colony lookup and a citizen count, and the answer changes when somebody
 * moves into a hut — an event measured in minutes. Ten seconds is already far
 * more attentive than the thing it is watching.
 */
@EventBusSubscriber(modid = ElysiumColonies.MODID)
public final class ColonyProgress {

    private ColonyProgress() {
    }

    /** Ticks between asking. Ten seconds; a citizen arrives rather less often. */
    public static final int INTERVAL = 20 * 10;

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % INTERVAL != 0) {
            return;
        }
        java.util.List<Colonies.Settlement> towns = Colonies.memberOf(player);
        if (towns.isEmpty()) {
            return;
        }
        java.util.List<Integer> sizes = new java.util.ArrayList<>();
        int largest = 0;
        for (Colonies.Settlement town : towns) {
            sizes.add(town.citizens());
            largest = Math.max(largest, town.citizens());
        }
        int floor = ColonyRules.worldFloor(sizes);
        if (ElysiumTiers.lift(player, floor)) {
            final int seen = largest;
            player.displayClientMessage(Component.translatable(
                            "elysium.colony.world_tier", seen, floor)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }
}
