package com.elysium.arsenal.item;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.forge.ForgeComponents;
import com.elysium.arsenal.forge.ForgeKinds;
import com.elysium.arsenal.rules.ForgeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A smith's hammer: roll the gear in your other hand again.
 *
 * <h2>Why the off hand and not a block</h2>
 *
 * The mod this replaces puts reforging behind an anvil-like screen. A screen
 * is a lot of machinery for one decision, and it is also a thing a player has
 * to find. Hammer in one hand, gear in the other, right-click: the mechanic is
 * discoverable by anybody who picks up two items, and everything it costs is
 * visible before it is spent.
 *
 * <h2>What it costs, and why the cost is on the grade you have</h2>
 *
 * The price is read off the grade the gear <em>currently</em> is, not the one
 * it is about to become — which is the one genuinely clever thing in the mod
 * this replaces. Rolling away from a Fabled sword is expensive; rolling away
 * from a Dull one is nearly free. That is what stops the right play from being
 * "hold the hammer down": every good roll you get makes the next roll cost
 * more, so at some point you stop, and stopping is the decision.
 */
public class HammerItem extends Item {

    private final String hammer;

    public HammerItem(String hammer, Properties properties) {
        super(properties);
        this.hammer = hammer;
    }

    /** Which hammer this is, in the rules' own terms. */
    public String hammer() {
        return hammer;
    }

    /** Whether this hammer rolls gear of that kind. */
    public boolean rolls(String kind) {
        List<String> kinds = ForgeRules.HAMMERS.get(hammer);
        return kinds != null && kinds.contains(kind);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(held);
        }
        ItemStack gear = player.getOffhandItem();
        String kind = ForgeKinds.of(gear);
        if (kind == null || !rolls(kind)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        ElysiumArsenal.MODID + ".forge.wrong_hammer"), true);
            }
            return InteractionResultHolder.fail(held);
        }

        String was = ForgeComponents.gradeOn(gear);
        ForgeRules.Grade current = was == null ? null : ForgeRules.grade(was);
        // Ungraded gear costs the cheapest grade's price to roll for the first
        // time. Charging nothing would make the hammer a free upgrade on
        // everything looted, which is the mechanic the odds were tuned against.
        int experience = current != null ? current.experience() : cheapest(kind);
        int wear = current != null ? current.durability() : 1;

        if (!player.hasInfiniteMaterials() && player.experienceLevel < experience) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        ElysiumArsenal.MODID + ".forge.too_poor", experience), true);
            }
            return InteractionResultHolder.fail(held);
        }

        if (!level.isClientSide) {
            if (!player.hasInfiniteMaterials()) {
                player.giveExperienceLevels(-experience);
                held.hurtAndBreak(wear, player,
                        LivingEntity.getSlotForHand(hand));
            }
            ForgeRules.Grade rolled =
                    ForgeRules.roll(kind, level.getRandom().nextInt());
            ForgeComponents.setGrade(gear, rolled.id());
            player.displayClientMessage(Component.translatable(
                            ElysiumArsenal.MODID + ".grade." + rolled.id())
                    .withStyle(com.elysium.arsenal.forge.ForgeEvents.colourOf(rolled)),
                    true);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    /** How long before the same hammer may be swung again. */
    public static final int COOLDOWN = 20;

    /** What the cheapest grade of a kind costs to leave. */
    private static int cheapest(String kind) {
        int least = Integer.MAX_VALUE;
        for (ForgeRules.Grade one : ForgeRules.of(kind)) {
            least = Math.min(least, one.experience());
        }
        return least == Integer.MAX_VALUE ? 1 : least;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        List<String> kinds = ForgeRules.HAMMERS.get(hammer);
        if (kinds == null) {
            return;
        }
        for (String kind : kinds) {
            lines.add(Component.translatable(ElysiumArsenal.MODID + ".forge.rolls",
                            Component.translatable(
                                    ElysiumArsenal.MODID + ".gear." + kind))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(ElysiumArsenal.MODID + ".forge.how")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
