package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.craft.ElysiumCraftLog;
import com.elysium.lib.craft.ElysiumMakersMark;
import com.elysium.lib.craft.ElysiumQuality;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Signing work, and reading a signature.
 *
 * <h2>What gets a mark</h2>
 *
 * Gear this mod family made: something with durability, from an Elysium
 * namespace. Both halves matter. Durability, because quality here <em>is</em>
 * durability and a mark on an ingot would be a grade that means nothing;
 * namespace, because stamping another mod's sword would be this mod quietly
 * rewriting somebody else's item, and a tooltip line nobody asked for is the
 * politest version of that.
 *
 * The namespace test is a string comparison rather than a registry, for the
 * same reason the machine-built deed uses one: a seventh Elysium module gets
 * maker's marks on the day it ships, without this file importing it.
 *
 * <h2>Why the grade is read before the count is raised</h2>
 *
 * Because a maker's mark records the skill at the moment of making. Raising the
 * count first would put Plain on everybody's first attempt, and the whole
 * ladder would start one rung up — which is exactly the sort of off-by-one
 * that nobody notices until somebody writes down what the first grade is for.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumCraftHandler {

    private ElysiumCraftHandler() {
    }

    /** Every Elysium module's namespace begins with this. */
    public static final String NAMESPACE_PREFIX = "elysium";

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack made = event.getCrafting();
        if (player == null || player.level().isClientSide() || !signable(made)) {
            return;
        }
        ElysiumQuality grade = ElysiumCraftLog.gradeFor(player, made);
        ElysiumMakersMark.stamp(made, player.getUUID(),
                player.getGameProfile().getName(), grade,
                player.level().getGameTime());
        // After the stamp. See the note above on why the order is the feature.
        ElysiumCraftLog.record(player, made, Math.max(1, made.getCount()));

        if (grade == ElysiumQuality.MASTERWORK) {
            // Said out loud, once per piece, because it is the end of a long
            // ladder and nothing else in the game would mark the moment. The
            // lower grades are left to the tooltip: a line every time somebody
            // crafts anything is a line they stop reading.
            player.displayClientMessage(Component.translatable(
                            "elysium.craft.masterwork", made.getHoverName())
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /**
     * The mark, read back.
     *
     * On {@code ItemTooltipEvent} rather than in each item's appendHoverText,
     * because a mark can end up on gear from any of the six modules and on a
     * piece whose own item class this library has never seen. One listener
     * covers all of them and cannot be forgotten by the seventh.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ElysiumMakersMark mark = ElysiumMakersMark.of(event.getItemStack());
        if (mark == null) {
            return;
        }
        ElysiumQuality grade = mark.grade();
        event.getToolTip().add(Component.translatable("elysium.craft.mark",
                        Component.literal(mark.name())
                                .withStyle(ChatFormatting.WHITE),
                        grade == null
                                ? Component.translatable("elysium.craft.quality.unknown")
                                : Component.translatable(grade.translationKey()))
                .withStyle(grade == ElysiumQuality.MASTERWORK
                        ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    /** Whether this is a thing this mod should be signing. */
    public static boolean signable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        return ElysiumCraftLog.keyFor(stack).startsWith(NAMESPACE_PREFIX + ":");
    }
}
