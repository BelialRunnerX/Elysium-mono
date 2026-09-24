package com.elysium.trinkets.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

/**
 * A charm that keeps doing what a potion did once (Apotheosis' AP-28).
 *
 * <h2>What it holds</h2>
 *
 * A vanilla {@code potion_contents} component, which is the same component a
 * bottle carries — so a charm is readable by anything that reads a potion, its
 * colour comes out right, and a pack can hand one out with {@code /give}
 * without knowing anything about this mod.
 *
 * <h2>What it costs</h2>
 *
 * Durability, and only while it is working. A charm that is doing nothing —
 * because the wearer already has the effect from somewhere else — spends
 * nothing, so a player stacking a potion and a charm is not punished for it.
 * When it is spent it goes inert rather than breaking: a charm is an
 * investment, and an investment that vanishes is one nobody makes twice. It is
 * refilled by crafting it with the potion it holds.
 *
 * <h2>Why it is not an ICurioItem</h2>
 *
 * Because the whole of this module's knowledge of Curios lives in one file,
 * and it is worth keeping that true. {@code CuriosSlots} registers this item
 * as wearable and answers where it is worn; nothing in here has ever heard of
 * an accessory mod.
 *
 * <h2>What may be in one</h2>
 *
 * Nothing instant and nothing harmful. An instant potion in a charm would fire
 * every few seconds forever, and a harmful one is a charm whose only use is on
 * somebody else — which is not a thing a worn item can be.
 */
public class PotionCharmItem extends Item {

    /** How long the charm keeps the effect topped up to. */
    public static final int DURATION = 20 * 12;

    /** How much is left before it is worth topping up again. */
    public static final int REFRESH_AT = 20 * 4;

    /** Ticks between one look at the wearer and the next. */
    public static final int INTERVAL = 20;

    public PotionCharmItem(Properties properties) {
        super(properties.stacksTo(1).durability(512));
    }

    /** What is in this charm, or empty. */
    public static PotionContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    /**
     * Whether an effect is one a charm may hold.
     *
     * Asked in three places — crafting one, tooltipping one and using one — so
     * it lives here rather than in whichever of them was written first.
     */
    public static boolean allowed(MobEffectInstance effect) {
        Holder<MobEffect> holder = effect.getEffect();
        return !holder.value().isInstantenous() && holder.value().isBeneficial();
    }

    /** Whether a whole potion may go in a charm: something in it, and nothing barred. */
    public static boolean allowed(PotionContents contents) {
        boolean any = false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (!allowed(effect)) {
                return false;
            }
            any = true;
        }
        return any;
    }

    @Override
    public Component getName(ItemStack stack) {
        PotionContents contents = contentsOf(stack);
        for (MobEffectInstance effect : contents.getAllEffects()) {
            return Component.translatable("item.elysiumtrinkets.potion_charm.of",
                    Component.translatable(effect.getDescriptionId()));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        PotionContents contents = contentsOf(stack);
        if (contents.getAllEffects().iterator().hasNext()) {
            lines.add(Component.translatable("item.elysiumtrinkets.potion_charm.worn")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.elysiumtrinkets.potion_charm.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            lines.add(Component.translatable("item.elysiumtrinkets.potion_charm.spent")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** A charm with nothing in it is not a charm; a spent one is not either. */
    public static boolean working(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage() && allowed(contentsOf(stack));
    }
}
