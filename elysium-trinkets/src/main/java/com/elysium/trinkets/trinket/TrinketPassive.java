package com.elysium.trinkets.trinket;

import com.elysium.lib.character.ElysiumPassive;
import com.elysium.lib.trinket.ElysiumTrinket;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/**
 * The small amount of shared machinery forty trinkets need.
 *
 * <h2>Names come from the id</h2>
 *
 * Every passive has to answer {@code getDisplayName} and {@code getDescription},
 * and for a trinket both are the same two translation keys derived from the same
 * id every time. Written out per trinket that is eighty methods that can each
 * name the wrong key — a mistake that shows up as a raw translation string in a
 * tooltip and nowhere else. Derived once, it cannot happen.
 */
public abstract class TrinketPassive implements ElysiumPassive {

    private final String id;

    protected TrinketPassive(String id) {
        this.id = id;
    }

    protected String id() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("trinket.elysiumtrinkets." + id);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("trinket.elysiumtrinkets." + id + ".desc");
    }

    // ------------------------------------------------------------------

    /**
     * A behaviour that builds one passive per tier and then reuses it.
     *
     * The library is explicit that {@code Behaviour#at} is called on every hook
     * and must be cheap, and that the natural mistake is {@code tier -> new
     * Whatever(tier)} — which allocates a passive on every hit taken, every hit
     * dealt and every tick, for every trinket worn. This is the shape that
     * requirement asks for, written once.
     *
     * A map rather than an array because ascension has no ceiling: there is no
     * length to size an array to, and any guess would be a cap on the system by
     * accident.
     */
    public static ElysiumTrinket.Behaviour perTier(IntFunction<ElysiumPassive> factory) {
        Map<Integer, ElysiumPassive> cache = new ConcurrentHashMap<>();
        return ascension -> cache.computeIfAbsent(ascension, factory::apply);
    }

    /**
     * A crafted trinket's description, with the number it currently gives.
     *
     * Read at the tier the stack is actually at, so an ascended trinket
     * describes what it does now rather than what it did when it was crafted.
     */
    protected static Component scaledDescription(String id, String amount) {
        return Component.translatable("trinket.elysiumtrinkets." + id + ".desc", amount);
    }

    /** A percentage, for descriptions: 0.25 reads as "25%". */
    protected static String percent(float fraction) {
        return Math.round(fraction * 100.0F) + "%";
    }
}
