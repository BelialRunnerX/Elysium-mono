package com.elysium.lib.standing;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What standing <em>costs</em>.
 *
 * <h2>Why this exists at all</h2>
 *
 * {@link ElysiumRewards} answers "what does the court give you". Until the
 * envoys grew a trade screen there was no matching question, because the old
 * exchange took whatever was in your hand — any item, one of it. That is a fine
 * shape for a court receiving tribute and a poor one for a shop: with no named
 * price, the best thing a player can hand over is the worst thing they own, and
 * a block of dirt bought the same relic as a diamond.
 *
 * A trade screen has to name a price, so something has to decide what a price
 * is. This is that something, and it is a provider registry for exactly the
 * reason rewards are: <b>the library ships no items and has no opinion about
 * what matter is worth.</b> A price table living in the NPC mod would be that
 * mod's own item list, drifting against whatever else is installed — which is
 * the objection the envoy's own documentation raised against a fixed offer list
 * in the first place, and it applies just as hard to the other side of the
 * trade.
 *
 * <h2>The tier means the same thing it means everywhere else</h2>
 *
 * Tier is 0 to 3, matching the standing bands and matching
 * {@link ElysiumRewards#roll}. A provider is asked for the cost of dealing at a
 * tier and returns the whole stack — item and count — or declines with an empty
 * stack so another provider can answer.
 *
 * Returning the count rather than a unit item is deliberate. It keeps this
 * exactly symmetrical with rewards, and it leaves "three of these or one of
 * those" where it belongs: with the mod that knows what its own items are worth
 * relative to each other. An engine that multiplied a unit price by the tier
 * would be making that judgement on the content's behalf with no idea what the
 * content is.
 *
 * <h2>The fallback, and why it is allowed to name an item</h2>
 *
 * With no provider registered this answers in <b>emeralds</b>, scaled by tier.
 *
 * That is not the library breaking its own rule about shipping no items.
 * Emerald is vanilla — nobody's content, present in every world, and already
 * the currency every player associates with a trade screen. The alternative is
 * a merchant window containing nothing, which is not a neutral outcome: it is a
 * broken-looking screen that a player cannot tell from a bug. A reward has a
 * sensible empty state ("they find nothing to give you"); a price does not.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * ElysiumPrices.register((tier, random) -> switch (tier) {
 *     case 3 -> new ItemStack(MY_RARE_INGOT.get(), 4);
 *     case 2 -> new ItemStack(MY_RARE_INGOT.get(), 1);
 *     default -> ItemStack.EMPTY;   // let someone else answer
 * });
 * }</pre>
 */
public final class ElysiumPrices {

    private ElysiumPrices() {
    }

    /** The top tier a provider will ever be asked for. Matches rewards. */
    public static final int MAX_TIER = ElysiumRewards.MAX_TIER;

    /**
     * The most of anything a single trade may ask for.
     *
     * A merchant slot holds one stack and vanilla's own trade UI cannot display
     * a cost above 64. A provider that asks for more is clamped rather than
     * refused, because the alternative is an offer that exists, renders wrong
     * and can never be completed.
     */
    public static final int MAX_COST = 64;

    @FunctionalInterface
    public interface PriceProvider {
        /**
         * @param tier   0..3, the shelf the player is dealing at
         * @param random the envoy's own source, so a price is reproducible with
         *               a seeded world
         * @return the cost, or {@link ItemStack#EMPTY} to decline
         */
        ItemStack roll(int tier, RandomSource random);
    }

    private static final List<PriceProvider> PROVIDERS = new ArrayList<>();

    public static void register(PriceProvider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * What one trade at this tier costs.
     *
     * Providers are tried in a random order rather than registration order, for
     * the same reason rewards are: two mods contributing prices should both get
     * a turn, and neither should be permanently shadowed by whichever loaded
     * first.
     *
     * Never empty. See the note on the fallback above.
     */
    public static ItemStack roll(int tier, RandomSource random) {
        int clamped = Math.max(0, Math.min(MAX_TIER, tier));
        int size = PROVIDERS.size();
        if (size > 0) {
            int start = random.nextInt(size);
            for (int i = 0; i < size; i++) {
                ItemStack stack = PROVIDERS.get((start + i) % size)
                        .roll(clamped, random);
                if (!stack.isEmpty()) {
                    if (stack.getCount() > MAX_COST) {
                        stack.setCount(MAX_COST);
                    }
                    return stack;
                }
            }
        }
        return fallback(clamped, random);
    }

    /**
     * Emeralds, more of them the higher the shelf.
     *
     * The numbers are vanilla-shaped on purpose — a wandering trader asks
     * between one and a handful — so that a court with no content mod installed
     * still reads as a trade rather than as a placeholder.
     */
    private static ItemStack fallback(int tier, RandomSource random) {
        int base = switch (tier) {
            case 3 -> 24;
            case 2 -> 12;
            case 1 -> 5;
            default -> 2;
        };
        int spread = Math.max(1, base / 4);
        return new ItemStack(Items.EMERALD, base + random.nextInt(spread));
    }

    /** Whether anything has registered. False means trades fall back to emeralds. */
    public static boolean isEmpty() {
        return PROVIDERS.isEmpty();
    }
}
