package com.elysium.lib.doctrine;

/**
 * The standing choices a skill tree can commit a player to.
 *
 * <h2>Why a scoreboard tag and not an attribute or a command</h2>
 *
 * Because the thing being expressed is a <em>commitment</em>, not a number. "I
 * extract quietly" is a fact about how this player has chosen to work, and the
 * places it changes something — what the Empire notices, what a blow is worth,
 * what the weather does — are scattered across the mod and are not all
 * attributes. A tag is the one representation every one of them can read,
 * costs nothing to check, and that an external skill tree can set through a
 * plain {@code tag @s add} with no bridge mod between.
 *
 * It also respecs correctly for free. Pufferfish's Skills pairs an unlock
 * command with a lock command, so a player who refunds the node has the tag
 * removed by the same mechanism that added it, and Elysium never has to know a
 * skill tree exists.
 *
 * <h2>Why the tags live here rather than in the datapack alone</h2>
 *
 * Because a tag the datapack sets and nothing reads is a skill node that does
 * nothing, and a tag Elysium reads and nothing sets is dead code — and both
 * are invisible. This enum is the single list, the datapack is generated from
 * it, and the validator checks the generated files against it in both
 * directions. Neither half can drift.
 *
 * <h2>No Minecraft here</h2>
 *
 * Names and numbers. What reads them lives in {@link ElysiumDoctrines}, so the
 * harness can check the whole table balances without a world.
 */
public enum ElysiumDoctrine {

    // --- how you take things out of the ground --------------------------
    /**
     * Extraction is licensed: the Empire is told, and pays.
     *
     * Costs more attention than working unlicensed, and returns Favor for it.
     * The bargain is the mod's central tension made explicit.
     */
    SANCTIONED("sanctioned", 1.35F, 1.0F, 0.0F),

    /** Extraction is quiet: less is noticed, and nothing is paid for it. */
    CLANDESTINE("clandestine", 0.60F, 1.0F, 0.0F),

    // --- what you do about the Empire's soldiers -------------------------
    /** A warrant: the Empire's enemies are yours, and it shows. */
    WARRANTED("warranted", 1.0F, 1.20F, 0.0F),

    /** A writ from the other side. The Empire's own are the softer target. */
    UNSWORN_WRIT("unsworn_writ", 1.0F, 1.20F, 0.0F),

    // --- what the world itself does to you -------------------------------
    /** Acclimatised: a standing share against every hazard. */
    ACCLIMATISED("acclimatised", 1.0F, 1.0F, 0.20F),

    /** Deep-lunged: a larger share, bought with a deeper commitment. */
    DEEP_LUNGED("deep_lunged", 1.0F, 1.0F, 0.35F);

    /** Every tag this mod recognises begins with this. */
    public static final String PREFIX = "elysium.doctrine.";

    private final String id;
    private final float suspicion;
    private final float damage;
    private final float share;

    ElysiumDoctrine(String id, float suspicion, float damage, float share) {
        this.id = id;
        this.suspicion = suspicion;
        this.damage = damage;
        this.share = share;
    }

    public String id() {
        return id;
    }

    /** The scoreboard tag a skill tree sets to commit to this. */
    public String tag() {
        return PREFIX + id;
    }

    /** What it multiplies Suspicion from extraction by. One for no change. */
    public float suspicionMultiplier() {
        return suspicion;
    }

    /** What it multiplies damage against its opposed faction by. */
    public float damageMultiplier() {
        return damage;
    }

    /** A flat share taken off every hazard, 0..1. */
    public float hazardShare() {
        return share;
    }

    /**
     * Whether this doctrine touches anything at all.
     *
     * A doctrine that multiplies everything by one and shares nothing is a tag
     * a player earns and never feels. Checked rather than assumed, because the
     * way a node quietly becomes decorative is somebody tuning its numbers back
     * to neutral one at a time.
     */
    public boolean doesSomething() {
        return suspicion != 1.0F || damage != 1.0F || share != 0.0F;
    }

    public String translationKey() {
        return "elysium.doctrine." + id;
    }

    public static ElysiumDoctrine byId(String id) {
        for (ElysiumDoctrine doctrine : values()) {
            if (doctrine.id.equals(id)) {
                return doctrine;
            }
        }
        return null;
    }

    /** Pairs that must never be held at once. The tree enforces it; so do we. */
    public static final String[][] OPPOSED = {
            {"sanctioned", "clandestine"},
            {"warranted", "unsworn_writ"},
            {"acclimatised", "deep_lunged"},
    };
}
