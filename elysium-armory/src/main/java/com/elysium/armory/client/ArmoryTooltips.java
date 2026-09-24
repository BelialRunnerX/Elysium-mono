package com.elysium.armory.client;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryGear;
import com.elysium.armory.item.ArmoryGearLogic;
import com.elysium.armory.item.ArmoryGearStats;
import com.elysium.armory.item.ArmoryGearTraits;
import com.elysium.armory.item.ArmoryPartItem;
import com.elysium.armory.property.ArmoryTraitRules;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Map;

/**
 * What a piece of gear says about itself: the tier it mines at, and the traits
 * it carries.
 *
 * <p>One handler for every gear and part item, rather than an override on each
 * of the nine classes they had to be. Numbers the game already shows — attack
 * damage, armour, durability — are left to the game to show.
 */
@EventBusSubscriber(modid = ElysiumArmory.MODID, value = Dist.CLIENT)
public final class ArmoryTooltips {

    private ArmoryTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        boolean gear = stack.getItem() instanceof ArmoryGear;
        boolean part = stack.getItem() instanceof ArmoryPartItem;
        if (!gear && !part) {
            return;
        }
        if (gear) {
            ArmoryGearStats stats = ArmoryGearLogic.statsOf(stack);
            if (!stats.tier().isEmpty()) {
                event.getToolTip().add(Component.translatable("elysiumarmory.tooltip.tier",
                        Component.translatable("elysiumarmory.tier." + stats.tier()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        // The marks (SPEC_CLONE P8). A grade, a charge and a synergy all change
        // what a piece is worth, and a player who cannot see them is holding
        // two parts that look identical and are not.
        com.elysium.armory.item.ArmoryMarks marks = stack.getOrDefault(
                ArmoryComponents.MARKS.get(), com.elysium.armory.item.ArmoryMarks.PLAIN);
        if (!marks.isPlain()) {
            if (marks.grade() != com.elysium.armory.property.ArmoryMaterialScience.UNGRADED) {
                event.getToolTip().add(Component.translatable("elysiumarmory.marks.grade",
                                Component.literal(com.elysium.armory.property
                                        .ArmoryMaterialScience.gradeName(marks.grade())))
                        .withStyle(marks.grade() < 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
            if (marks.starcharged()) {
                event.getToolTip().add(Component.translatable("elysiumarmory.marks.starcharged")
                        .withStyle(ChatFormatting.AQUA));
            }
            if (marks.synergy() > 1.0D) {
                event.getToolTip().add(Component.translatable("elysiumarmory.marks.synergy",
                                Component.literal(String.format("%.0f%%",
                                        (marks.synergy() - 1.0D) * 100.0D)))
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        ArmoryGearTraits traits = stack.getOrDefault(ArmoryComponents.TRAITS.get(), ArmoryGearTraits.NONE);
        for (Map.Entry<String, Integer> entry : traits.levels().entrySet()) {
            String path = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
            Component name = Component.translatable("elysiumarmory.trait." + path);
            String level = ArmoryTraitRules.roman(entry.getValue());
            event.getToolTip().add(Component.empty().append(name).append(" ").append(level)
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
