package com.elysium.lib.craft;

/**
 * How well a thing was made.
 *
 * <h2>Where quality comes from, and where it deliberately does not</h2>
 *
 * From <b>practice at that particular thing</b> — how many of this exact item
 * the maker has made before. Not from a stat, not from a level, and
 * emphatically not from the Engineering research track.
 *
 * The research tracks were built on a promise that they buy information and
 * never power, and there is a validator that refuses any unlock described as
 * granting any. Letting a research rank raise the durability of everything a
 * player forges would keep the letter of that rule and break it completely: the
 * tree would become something a player has to pursue, which is the exact
 * outcome B3 exists to avoid. Practice is the honest source — it is earned at
 * the anvil, by the person at the anvil, doing the thing the mark is about.
 *
 * <h2>Per item, not per player</h2>
 *
 * Five hundred shovels do not make a swordsmith. Counting by item id means a
 * player who has specialised reads as a specialist, and a player who has made
 * one of everything reads as a beginner at all of it — which is both truer and
 * more interesting than a single crafting level.
 *
 * <h2>Why the first one is always Crude</h2>
 *
 * Because a maker's mark that said Masterwork on somebody's first attempt would
 * be a label rather than a record. The count is read <em>before</em> it is
 * incremented, so the mark states the skill at the moment of making — which is
 * what a maker's mark is.
 *
 * <h2>No Minecraft here</h2>
 *
 * Thresholds and a multiplier, so the harness can walk every practice count and
 * check that the curve is monotonic, bounded, and worth pursuing without being
 * worth grinding.
 */
public enum ElysiumQuality {

    /** First attempts. */
    CRUDE("crude", 0, 0.80F),

    /** You have done this before. */
    PLAIN("plain", 4, 1.00F),

    /** You know what you are doing. */
    SOUND("sound", 16, 1.15F),

    /** People would notice. */
    FINE("fine", 48, 1.30F),

    /** The best you are going to manage. */
    MASTERWORK("masterwork", 128, 1.50F);

    /**
     * Where counting practice stops.
     *
     * One past the top grade, so the highest is reachable and nothing beyond it
     * is recorded.
     *
     * Declared here rather than on {@code ElysiumCraftLog}, which is where it
     * belongs by subject and cannot live by mechanics: that class registers an
     * attachment in its static initialiser, so reading one constant off it
     * pulls in NeoForge's registries and fails outside a running game. This is
     * the third time in this codebase — after ElysiumLevelling and the research
     * ceiling — so there is now a validator rule that looks for it rather than
     * a memory that has to hold.
     */
    public static final int COUNT_CEILING = 129;

    private final String id;
    private final int practice;
    private final float durability;

    ElysiumQuality(String id, int practice, float durability) {
        this.id = id;
        this.practice = practice;
        this.durability = durability;
    }

    public String id() {
        return id;
    }

    /** How many of the same thing must have been made before this grade. */
    public int practice() {
        return practice;
    }

    /**
     * What it does to how long the thing lasts.
     *
     * Durability and nothing else. A well-made blade is a blade that survives
     * the fight, not a blade that hits harder — quality is craftsmanship, and
     * craftsmanship does not change what a sword is. It also keeps the whole
     * feature out of the damage pipeline, where a multiplier would have to be
     * argued about against every other multiplier already there.
     *
     * The bottom grade is deliberately below one: a first attempt is worse
     * than an ordinary one, or there is no cost to being new and the ladder has
     * no bottom rung.
     */
    public float durabilityMultiplier() {
        return durability;
    }

    public String translationKey() {
        return "elysium.craft.quality." + id;
    }

    /** The grade earned by having made this many before. */
    public static ElysiumQuality forPractice(int made) {
        ElysiumQuality found = CRUDE;
        for (ElysiumQuality grade : values()) {
            if (made >= grade.practice()) {
                found = grade;
            }
        }
        return found;
    }

    /** By id, or null for a grade this build no longer has. */
    public static ElysiumQuality byId(String id) {
        for (ElysiumQuality grade : values()) {
            if (grade.id.equals(id)) {
                return grade;
            }
        }
        return null;
    }

    /**
     * What a piece's durability becomes at this grade.
     *
     * Floored at one. A multiplier that could round a one-use item down to zero
     * would produce a tool that breaks on the tick it is made, and the player
     * would report it as the item not existing.
     */
    public int durabilityFor(int base) {
        if (base <= 0) {
            return base;                 // not a damageable thing at all
        }
        return Math.max(1, Math.round(base * durability));
    }
}
