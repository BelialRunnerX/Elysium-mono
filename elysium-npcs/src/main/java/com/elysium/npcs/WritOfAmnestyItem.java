package com.elysium.npcs;

import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Writ of Amnesty: the Empire agreeing to stop caring, for a price.
 *
 * <h2>What the specification asks for</h2>
 *
 * Fourth Edition §27.1 tabulates the Imperial actions a settlement faces, and
 * one row is the only one that runs the other way:
 *
 * <pre>
 *   ACTION                     TRIGGER                        PLAYER RESPONSE
 *   Amnesty / reclassification standing milestone or bargain  reduce local
 *                                                             pressure in
 *                                                             exchange for
 *                                                             compliance/cost
 * </pre>
 *
 * <h2>Why the mod needed it</h2>
 *
 * Every other part of §14 pushes Suspicion <em>up</em>. Mining raises it,
 * killing enforcers raises it, ascending gear raises it, planting a beacon puts
 * a permanent floor under it. The only two things that lower it are waiting,
 * which is not a decision, and clearing a register action, which requires
 * surviving the thing the meter summoned in the first place.
 *
 * A meter with no release valve is not a resource a player manages, it is a
 * timer they endure. §27.1's amnesty row is the valve, and "in exchange for
 * compliance/cost" is the important half — it has to be bought.
 *
 * <h2>The floor is the point</h2>
 *
 * Amnesty reduces <b>pressure</b>. It cannot touch §14.2's structural floor,
 * and a writ used by a player already at their floor is refused rather than
 * consumed.
 *
 * That is what keeps the two systems from cancelling each other out. §14.2
 * calls a claim's floor permanent, and it means it: what you have built is a
 * fact about the world, and the Empire does not agree to forget a fortress
 * because you paid them. What they will do is drop everything <em>else</em> —
 * the ore, the bodies, the ascensions — which is the part that was genuinely
 * about your conduct.
 *
 * So the writ walks you down to your own floor and stops, and the message says
 * so. A player whose floor is 44 and whose Suspicion is 44 is not being
 * cheated by a broken item; they are being told that the only way further down
 * is through the beacon.
 *
 * <h2>Why an item rather than a conversation</h2>
 *
 * The envoy already has a trade screen, and a writ is a thing that screen can
 * sell without inventing a third interaction on an entity that already has two.
 * It also makes amnesty <em>portable</em>: you buy it when the Court is in
 * front of you and spend it when you need it, which is a better decision than
 * one made entirely at the moment of purchase. An envoy stays twenty minutes;
 * a file lasts much longer than that.
 */
public class WritOfAmnestyItem extends Item {

    /**
     * How much pressure one writ discharges.
     *
     * Exactly one band. The bands are 25 wide — clear, Noted, Marked, Hunted —
     * so "a writ buys you a band" is the whole mental model, and a player can
     * work out what they need without a table. Anything smaller would take four
     * of them to be felt; anything larger would make a single purchase undo an
     * evening.
     */
    public static final int RELIEF = 25;

    public WritOfAmnestyItem(Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(held);
        }

        int suspicion = ElysiumStanding.getSuspicion(player);
        int floor = ElysiumStanding.floorFor(player);

        if (suspicion <= floor) {
            // Refused, and NOT consumed. An item that vanishes having done
            // nothing is indistinguishable from a bug, and this is the case a
            // player is most likely to hit by accident — they bought a writ,
            // used one, and tried a second.
            player.displayClientMessage(Component.translatable(
                            suspicion <= 0
                                    ? "elysiumnpcs.amnesty.nothing_filed"
                                    : "elysiumnpcs.amnesty.at_floor", floor)
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResultHolder.fail(held);
        }

        int discharged = Math.min(RELIEF, suspicion - floor);
        ElysiumStanding.addSuspicion(player, -discharged,
                ElysiumStanding.CAUSE_AMNESTY);
        held.shrink(1);

        player.displayClientMessage(Component.translatable(
                        "elysiumnpcs.amnesty.granted", discharged,
                        ElysiumStanding.getSuspicion(player))
                .withStyle(ChatFormatting.GREEN), false);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("elysiumnpcs.amnesty.tooltip", RELIEF)
                .withStyle(ChatFormatting.GRAY));
        // The floor caveat is on the tooltip rather than only in the failure
        // message, because the failure message arrives after the player has
        // already carried the writ home.
        lines.add(Component.translatable("elysiumnpcs.amnesty.tooltip_floor")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
