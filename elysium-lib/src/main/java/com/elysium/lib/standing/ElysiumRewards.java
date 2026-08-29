package com.elysium.lib.standing;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What standing pays out.
 *
 * <h2>The split the whole system rests on</h2>
 *
 * <b>Favor sets the tier</b> — which shelf the reward comes off. <b>Suspicion
 * sets the amount</b> — how many of it. So the two loops pay differently and a
 * player can feel which one they are on: pure Favor is a trickle of good
 * things, pure Suspicion a pile of cheap ones.
 *
 * The engine owns that arithmetic. A provider owns the shelf itself, because
 * the library ships no items and has no opinion about what a reward is.
 *
 * <h2>Tiers</h2>
 *
 * Tier is 0 to 3, matching the standing bands. A provider is asked for a tier
 * and may decline by returning an empty stack, in which case another provider
 * is tried. Register a provider that answers every tier, or several that each
 * answer one — both work.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * ElysiumRewards.register((tier, random) -> switch (tier) {
 *     case 3 -> new ItemStack(MY_RELIC.get());
 *     case 2 -> new ItemStack(MY_RUNE.get());
 *     default -> ItemStack.EMPTY;   // let someone else answer
 * });
 * }</pre>
 */
public final class ElysiumRewards {

    private ElysiumRewards() {
    }

    /** The top tier a provider will ever be asked for. */
    public static final int MAX_TIER = 3;

    @FunctionalInterface
    public interface RewardProvider {
        /**
         * @param tier   0..3, from the player's Favor band
         * @param random the player's own source, so a reward is reproducible
         *               with a seeded world
         * @return the reward, or {@link ItemStack#EMPTY} to decline
         */
        ItemStack roll(int tier, RandomSource random);
    }

    private static final List<RewardProvider> PROVIDERS = new ArrayList<>();

    public static void register(RewardProvider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * One reward off the shelf the tier has unlocked.
     *
     * Providers are tried in a random order rather than registration order, so
     * that two mods contributing rewards both get a turn and neither is
     * permanently shadowed by whichever loaded first.
     *
     * @return a stack, or empty when nothing wanted to pay out
     */
    public static ItemStack roll(int tier, RandomSource random) {
        if (PROVIDERS.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int size = PROVIDERS.size();
        int start = random.nextInt(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = PROVIDERS.get((start + i) % size)
                    .roll(Math.max(0, Math.min(MAX_TIER, tier)), random);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean isEmpty() {
        return PROVIDERS.isEmpty();
    }
}
