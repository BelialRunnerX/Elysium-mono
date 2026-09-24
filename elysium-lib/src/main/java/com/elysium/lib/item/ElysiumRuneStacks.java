package com.elysium.lib.item;

import net.minecraft.world.item.ItemStack;

/**
 * Reading and writing a rune item's tier. Kept apart from
 * {@link ElysiumRuneTiers} because that file is executed without a game and
 * this one touches item stacks.
 */
public final class ElysiumRuneStacks {

    private ElysiumRuneStacks() {
    }

    /** A rune stack's tier. Tier 1 where none was ever written. */
    public static int tierOf(ItemStack stack) {
        Integer tier = stack.get(ElysiumComponents.RUNE_TIER.get());
        return ElysiumRuneTiers.clamp(tier == null ? ElysiumRuneTiers.BASE : tier);
    }

    /**
     * Sets a rune stack's tier. Tier 1 is written as no component at all, so a
     * merged-then-found tier-1 rune and a freshly dropped one are the same
     * stack and stack together.
     */
    public static void setTier(ItemStack stack, int tier) {
        int clamped = ElysiumRuneTiers.clamp(tier);
        if (clamped == ElysiumRuneTiers.BASE) {
            stack.remove(ElysiumComponents.RUNE_TIER.get());
        } else {
            stack.set(ElysiumComponents.RUNE_TIER.get(), clamped);
        }
    }
}
