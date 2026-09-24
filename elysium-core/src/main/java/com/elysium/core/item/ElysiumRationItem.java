package com.elysium.core.item;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumWards;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A field ration: one meal that answers one hazard.
 *
 * <h2>Why this is an item class and not a food property</h2>
 *
 * {@code FoodProperties.Builder.effect} would apply the ward on its own, and
 * for a plain buff it would be the right tool. It is not used here because the
 * effect it takes has to be resolved while the item is being <em>constructed</em>,
 * and the item is constructed during item registration — which has no ordering
 * relationship with the effect registry being filled. That is a class of bug
 * that appears on somebody else's machine, once, after a mod is added.
 *
 * Applying the ward in {@link #finishUsingItem} resolves it at the moment a
 * player swallows, when every registry in the game is long since frozen.
 *
 * <h2>Why the tooltip says how long</h2>
 *
 * Because the ward is immunity, and the one thing that keeps immunity from a
 * meal being the cheapest route to the expensive answer is that it ends. A
 * player who reads "immune to Thermal" and not "for three minutes" walks into
 * a lava field on the strength of it and finds out about the clock by dying at
 * the far end. The duration is the whole of the second line.
 */
public class ElysiumRationItem extends Item {

    /**
     * What a ration is worth as food, before the ward.
     *
     * Deliberately ordinary — about a cooked potato. A ration that were also
     * the best meal in the game would be eaten for its nutrition by players who
     * never meet a hazard, and the ward would become a thing that happens to
     * them rather than a thing they chose.
     */
    public static final int NUTRITION = 5;
    public static final float SATURATION = 0.5F;

    private final ElysiumHazard hazard;

    public ElysiumRationItem(ElysiumHazard hazard) {
        super(new Properties().food(new FoodProperties.Builder()
                .nutrition(NUTRITION)
                .saturationModifier(SATURATION)
                .build()));
        this.hazard = hazard;
    }

    public ElysiumHazard getHazard() {
        return hazard;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide()) {
            Holder<MobEffect> ward = ElysiumWards.wardFor(hazard);
            if (ward != null) {
                // Plain re-application rather than an extension: eating a second
                // ration resets the clock and never stacks a second share. See
                // ElysiumWards — duration is the axis more food buys.
                entity.addEffect(new MobEffectInstance(ward, ElysiumWards.DURATION));
            }
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("elysium.tooltip.ration",
                        hazard.getDisplayName(), duration())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("elysium.tooltip.ration.whole")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** m:ss, the way every other timed thing in the game states itself. */
    private static String duration() {
        int seconds = ElysiumWards.DURATION / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
