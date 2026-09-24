package com.elysium.lib.practice;

/**
 * How good somebody has got at one particular thing by doing it.
 *
 * <h2>Why this is not vanilla's villager level again</h2>
 *
 * Vanilla already has Novice through Master, earned by trading and unlocking
 * whole tiers of offers. A second bar filled the same way would be the same bar
 * twice, which is the mistake this codebase has been avoiding since the
 * research tracks.
 *
 * What §11 actually asks for is different, and it is the same insight as the
 * maker's mark: skill is <em>per thing</em>, not per person. A baker who has
 * sold you forty loaves is better at bread, and no better at cake. Vanilla
 * levels say what a villager is allowed to sell; this says what they have
 * actually spent their life doing — and those are different facts about the
 * same villager.
 *
 * <h2>What it does</h2>
 *
 * The price of <em>that offer</em> falls. Not every offer, not their level, not
 * their restock rate: the one trade they have made a hundred times. A player
 * who keeps buying bread from the same baker ends up with a baker who does them
 * a good price on bread, which is what a regular customer is.
 *
 * <h2>Why the discount has a floor</h2>
 *
 * Because an offer that reaches zero is an infinite emerald machine, and
 * because it stops being a relationship and becomes an exploit. A third off is
 * generous — vanilla's own Hero of the Village tops out around there — and the
 * cap is checked rather than assumed.
 *
 * <h2>No Minecraft here</h2>
 *
 * Counts and thresholds, so the harness can walk every practice level against
 * every price the game can offer and check that nothing ever reaches zero,
 * inverts, or costs more than it started at.
 */
public enum ElysiumPractice {

    /** They have barely done this. */
    UNFAMILIAR("unfamiliar", 0, 0.00F),

    /** They know the work. */
    ACCUSTOMED("accustomed", 8, 0.08F),

    /** They could do it in their sleep. */
    SEASONED("seasoned", 32, 0.16F),

    /** This is what they are for. */
    MASTER_OF_IT("master_of_it", 96, 0.25F);

    private final String id;
    private final int trades;
    private final float discount;

    ElysiumPractice(String id, int trades, float discount) {
        this.id = id;
        this.trades = trades;
        this.discount = discount;
    }

    public String id() {
        return id;
    }

    /** How many of the same trade must have been made to reach this. */
    public int trades() {
        return trades;
    }

    /**
     * What proportion comes off the first cost.
     *
     * A proportion, not a flat number of items. The first version took a fixed
     * count off and the harness's own table showed what that meant: three off a
     * cost of four is seventy-five per cent, and three off sixty-four is five.
     * The cheapest trades were nearly free and the dearest were untouched,
     * which is exactly backwards — a regular customer gets a better price, not
     * a different economy.
     */
    public float discount() {
        return discount;
    }

    /** The discount as a whole number of per cent, for a readout. */
    public int percent() {
        return Math.round(discount * 100.0F);
    }

    public String translationKey() {
        return "elysium.practice." + id;
    }

    /** The band earned by having made this many of the same trade. */
    public static ElysiumPractice forTrades(int made) {
        ElysiumPractice found = UNFAMILIAR;
        for (ElysiumPractice band : values()) {
            if (made >= band.trades()) {
                found = band;
            }
        }
        return found;
    }

    /** Where counting stops: one past the top band. */
    public static final int CEILING = 97;

    /** The least any trade may ever cost, however practised the seller. */
    public static final int FLOOR = 1;

    /**
     * What the first cost of a trade becomes at this band.
     *
     * Never below {@link #FLOOR}, and never above where it started. Both are
     * checked rather than trusted: a discount that reached zero would be an
     * infinite emerald machine, and one that went negative would be a price
     * that wraps round to enormous.
     */
    public int priceFor(int baseCost) {
        if (baseCost <= FLOOR) {
            return baseCost;            // already as cheap as it can be
        }
        // Rounded up, so the villager keeps the half-item. A player is never
        // worse off than the band promises and the shop is never quite as
        // generous as the arithmetic — which is the right way round for a
        // rounding nobody can see.
        return Math.max(FLOOR, (int) Math.ceil(baseCost * (1.0F - discount)));
    }

    /**
     * The special-price difference to hand to a merchant offer.
     *
     * Negative or zero, always: vanilla adds this to the cost, so a positive
     * number would make a practised villager <em>dearer</em>. Derived from
     * {@link #priceFor} rather than returned directly, so the floor applies to
     * the number that actually reaches the game.
     */
    public int priceDiffFor(int baseCost) {
        return priceFor(baseCost) - baseCost;
    }

    public static ElysiumPractice byId(String id) {
        for (ElysiumPractice band : values()) {
            if (band.id.equals(id)) {
                return band;
            }
        }
        return null;
    }
}
