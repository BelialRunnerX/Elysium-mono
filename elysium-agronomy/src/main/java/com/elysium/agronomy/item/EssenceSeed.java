package com.elysium.agronomy.item;

import com.elysium.agronomy.rules.AgronomyRules;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * A seed that grows a material rather than a food.
 *
 * <p>It carries two things: which rung of the ladder it sits on, which is the
 * whole of how long it takes, and what one harvest yields. Everything else
 * about growing it is the pot's business — so adding a material is a line in a
 * table rather than a class.
 */
public class EssenceSeed extends Item {

    private final int tier;
    private final Supplier<Item> essence;

    public EssenceSeed(Properties properties, int tier, Supplier<Item> essence) {
        super(properties);
        this.tier = Math.max(1, Math.min(AgronomyRules.TIERS, tier));
        this.essence = essence;
    }

    /** Which rung of the ladder, and therefore how slowly it grows. */
    public int tier() {
        return tier;
    }

    /** What one harvest gives. */
    public Item essence() {
        return essence.get();
    }

    /** How long one growth takes at speed one. */
    public int ticks() {
        return AgronomyRules.ticksAt(tier);
    }

    /** How many come out of one harvest. */
    public int yield() {
        return AgronomyRules.cropAt(tier).yield();
    }
}
