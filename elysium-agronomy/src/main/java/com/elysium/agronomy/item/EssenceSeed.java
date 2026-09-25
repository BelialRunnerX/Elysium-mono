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
    private final boolean awakened;

    public EssenceSeed(Properties properties, int tier, Supplier<Item> essence) {
        this(properties, tier, essence, false);
    }

    /**
     * @param awakened whether this one came off an altar.
     *                 An awakened seed yields more per plot and is allowed
     *                 fewer plots, so a farm of them earns what a farm of the
     *                 ordinary ones does out of half the ground. The arithmetic
     *                 is {@link com.elysium.agronomy.rules.AltarRules}' and
     *                 not this class's: a second opinion about what a plot is
     *                 worth is one opinion too many.
     */
    public EssenceSeed(Properties properties, int tier, Supplier<Item> essence,
                       boolean awakened) {
        super(properties);
        this.tier = Math.max(1, Math.min(AgronomyRules.TIERS, tier));
        this.essence = essence;
        this.awakened = awakened;
    }

    /** Whether this one came off an altar. */
    public boolean awakened() {
        return awakened;
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
        int plain = AgronomyRules.cropAt(tier).yield();
        return awakened
                ? plain * com.elysium.agronomy.rules.AltarRules.AWAKENED_YIELD
                : plain;
    }
}
