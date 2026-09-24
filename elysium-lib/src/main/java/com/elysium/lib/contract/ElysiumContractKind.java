package com.elysium.lib.contract;

/**
 * The three things an office will ask you to do.
 *
 * <h2>Why only three, and why these three</h2>
 *
 * Each one is measured from something the mod already counts, and that is the
 * whole selection criterion. A contract whose progress needs a new counter is a
 * contract with a second source of truth: the ledger says four, the world says
 * five, and the player is right and cannot prove it.
 *
 * <ul>
 *   <li>{@link #DELIVER} is counted out of the inventory at the counter, the
 *       way a delivery always has been.</li>
 *   <li>{@link #CULL} rides on the kill hooks that already move standing, so a
 *       kill that pays Favor and a kill that advances a contract are by
 *       construction the same kill.</li>
 *   <li>{@link #CATALOGUE} reads the discovery catalogue, which is already the
 *       number the scanner writes and the character screen shows.</li>
 * </ul>
 *
 * A fourth kind is welcome the day something else in the mod keeps a count
 * worth asking about — and not before.
 *
 * <h2>No Minecraft here</h2>
 *
 * Ids and difficulty weights. What a delivery actually counts lives beside the
 * inventory; this file is what the harness walks.
 */
public enum ElysiumContractKind {

    /**
     * Bring us this many of something.
     *
     * The cheapest ask per unit, because the player may already have them.
     */
    DELIVER("deliver", 1.0F),

    /**
     * Kill this many of a faction's own.
     *
     * Dearer per unit than a delivery: it cannot be done from a chest, and
     * whichever faction is named will mind.
     */
    CULL("cull", 3.0F),

    /**
     * Have this many things in your catalogue.
     *
     * The dearest, and the only one that can be partly done before it is
     * taken — a well-travelled player starts it half finished, which is the
     * point: the Empire is paying for a survey it could not do itself.
     */
    CATALOGUE("catalogue", 5.0F);

    private final String id;
    private final float weight;

    ElysiumContractKind(String id, float weight) {
        this.id = id;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    /** What one unit of this is worth, relative to a delivered item. */
    public float weight() {
        return weight;
    }

    public String translationKey() {
        return "elysium.contract.kind." + id;
    }

    public static ElysiumContractKind byId(String id) {
        for (ElysiumContractKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
