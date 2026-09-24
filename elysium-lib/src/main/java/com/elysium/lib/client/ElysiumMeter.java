package com.elysium.lib.client;

/**
 * The arithmetic every Elysium bar on the HUD shares.
 *
 * <h2>Why a bar and not a row of icons</h2>
 *
 * Vanilla counts. Ten hearts, ten drumsticks, ten shields: each icon is a fixed
 * two points, and the display works exactly as long as the number stays at
 * twenty. This mod's numbers do not — Vitality carries maximum health into the
 * hundreds, Fortitude fills vanilla's armour ceiling and then keeps going into
 * toughness — and vanilla's answer to a number that outgrows its icons is to
 * stack rows of them behind each other, which is a display you read by
 * squinting.
 *
 * A bar is a fraction, and a fraction does not care how large its denominator
 * is. That is the whole reason these exist, and it is why the two functions
 * below are the only two any of them needs.
 *
 * <h2>Why they live in one class</h2>
 *
 * Because health, food and armour are three instances of the same problem, and
 * three copies of {@code value / capacity} is three places for a clamp to be
 * forgotten. {@link ElysiumHealthBar} was written first and kept its own names
 * for these; it delegates here rather than carrying a second copy.
 */
public final class ElysiumMeter {

    private ElysiumMeter() {
    }

    /**
     * How much of a bar belongs to the first of two things sharing it.
     *
     * <p>Sized by the two <em>capacities</em> rather than by what is currently
     * in them, which is the property that makes a split bar stable. A shield
     * recharging must not shorten the red beside it, and a suit of armour
     * gaining toughness must not shorten the armour.
     *
     * <p>Both regions are given at least a pixel when they exist at all, and
     * they always sum to the full width — a bar with a gap in it looks like a
     * rendering fault, and a region of zero width is a bar that has silently
     * stopped reporting one of the two things it is for.
     *
     * @param width  the whole bar
     * @param first  the first thing's capacity, however large
     * @param second the second thing's capacity, or 0 when there is no second
     */
    public static int split(int width, float first, float second) {
        if (width <= 0) {
            return 0;
        }
        if (!(second > 0.0F) || !(first > 0.0F)) {
            return first > 0.0F ? width : 0;
        }
        int head = Math.round(width * (first / (first + second)));
        return Math.max(1, Math.min(width - 1, head));
    }

    /** A fraction of a capacity, safe at any capacity including none. */
    public static float fraction(float value, float capacity) {
        if (!(capacity > 0.0F) || !(value > 0.0F)) {
            return 0.0F;
        }
        return Math.min(1.0F, value / capacity);
    }
}
