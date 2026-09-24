package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Tools that take more than one block. The JDK and nothing else at all.
 *
 * <h2>What a broad tool is for, and the trap in it</h2>
 *
 * A hammer clears a three-by-three in one swing. That is the whole idea, and
 * it is a good one: quarrying by hand is the least interesting hour in the
 * game. The trap is that it is almost impossible to add one without making the
 * pickaxe pointless — and then the pickaxe is a crafting ingredient rather than
 * a tool, and a recipe tier of the game quietly stops existing.
 *
 * <h2>So a broad tool is worse at everything except breadth</h2>
 *
 * It is <b>slower per swing</b> than the tool it replaces, and every block it
 * takes costs a point of durability just as if it had been mined singly. Which
 * leaves exactly one band of speed where it is worth carrying:
 *
 * <ul>
 *   <li>slower than the specialist on a single block, or it is strictly
 *       better and the specialist is dead;</li>
 *   <li>faster than the specialist over a full nine, or it is strictly worse
 *       and nobody makes one.</li>
 * </ul>
 *
 * <p>That is a band, not a number, and the harness checks that every tool in
 * the module sits inside it at every tier rather than trusting that somebody
 * chose well. It is the same shape of proof as the one that keeps the
 * transmuter honest: <em>no arrangement dominates another</em>.
 */
public final class ArsenalRules {

    private ArsenalRules() {
    }

    /**
     * How far from the block you hit a broad tool reaches.
     *
     * <p>One, so three by three. Two would be five by five, which is
     * twenty-five blocks a swing and a tunnel you cannot see the end of; it is
     * also enough to drop a ceiling on yourself before you have noticed.
     */
    public static final int REACH = 1;

    /** How many blocks one swing of a broad tool takes. */
    public static int blocksIn(int reach) {
        int across = 2 * Math.max(0, reach) + 1;
        return across * across;
    }

    /**
     * What one block costs a broad tool, in durability.
     *
     * <p>One, exactly as if it had been mined singly. A broad tool that spent
     * one point for nine blocks would be nine times the tool for the same
     * price, and every other tool in the game would be a mistake.
     */
    public static final int COST_PER_BLOCK = 1;

    /** What a swing that took this many blocks costs. */
    public static int costOf(int blocks) {
        return Math.max(0, blocks) * COST_PER_BLOCK;
    }

    /**
     * How fast a broad tool swings, as a share of the specialist it replaces.
     *
     * <p>It has to sit strictly inside the band: below one, or the specialist
     * is dead, and above one ninth, or nobody makes one. Just over a half puts
     * it a little less than twice as slow on a single block and nearly five
     * times as fast over a full nine, which is a trade a player can feel in
     * both directions.
     */
    public static final float BROAD_SHARE = 0.55F;

    /**
     * And how fast the one that does every job swings.
     *
     * <p>Lower again, because a paxel is three tools in one slot and the price
     * of that is being worse than all three. Anything else and a player
     * carries one tool for the rest of the game.
     */
    public static final float PAXEL_SHARE = 0.75F;

    /** One shape of broad tool. */
    public record Broad(String name, boolean wide, float share, String replaces) {

        public Broad {
            if (share <= 0.0F || share >= 1.0F) {
                throw new IllegalArgumentException("a tool that is as fast as the one "
                        + "it replaces, or slower than standing still, is not a trade");
            }
        }

        /** How many blocks one swing takes. */
        public int blocks() {
            return wide ? blocksIn(REACH) : 1;
        }

        /** How long one swing takes, against a specialist's swing of one. */
        public float swing() {
            return 1.0F / share;
        }

        /** How long taking {@code blocks()} blocks takes, the same way. */
        public float clearing() {
            return swing();
        }

        /** How long the specialist takes over the same ground. */
        public float specialistClearing() {
            return blocks();
        }

        /** Whether it beats the specialist over a full swing's worth. */
        public boolean worthCarrying() {
            return clearing() < specialistClearing();
        }

        /** Whether the specialist still beats it on one block. */
        public boolean leavesTheSpecialistAlive() {
            return swing() > 1.0F;
        }
    }

    /**
     * The four, and what each replaces.
     *
     * <p>Three that take a wall at a time and one that takes one block of
     * anything. Between them they are the reason nobody quarries by hand, and
     * not one of them is a reason to stop carrying a pickaxe.
     */
    public static final List<Broad> BROADS = List.of(
            new Broad("hammer", true, BROAD_SHARE, "pickaxe"),
            new Broad("excavator", true, BROAD_SHARE, "shovel"),
            new Broad("scythe", true, BROAD_SHARE, "hoe"),
            new Broad("paxel", false, PAXEL_SHARE, "all three"));

    /** Every vanilla tier a tool of ours comes in. */
    public static final List<String> TIERS =
            List.of("wooden", "stone", "iron", "golden", "diamond", "netherite");

    /** Every item this module adds. */
    public static List<String> catalogue() {
        List<String> names = new ArrayList<>(BROADS.size() * TIERS.size());
        for (String tier : TIERS) {
            for (Broad broad : BROADS) {
                names.add(tier + "_" + broad.name());
            }
        }
        return List.copyOf(names);
    }

    /** Every tool that is simply better than what it replaces. */
    public static List<String> dominant() {
        List<String> caught = new ArrayList<>();
        for (Broad broad : BROADS) {
            if (!broad.leavesTheSpecialistAlive()) {
                caught.add(broad.name());
            }
        }
        return caught;
    }

    /** Every tool nobody would ever make. */
    public static List<String> pointless() {
        List<String> caught = new ArrayList<>();
        for (Broad broad : BROADS) {
            if (broad.wide() && !broad.worthCarrying()) {
                caught.add(broad.name());
            }
        }
        return caught;
    }

    // ==================================================================
    // The scythe, which is the one that could make seeds
    // ==================================================================

    /**
     * How many seeds come back from harvesting this many grown crops.
     *
     * <p>One each, and one of them is put back in the ground, so a swing of
     * nine takes nine crops and leaves nine planted. What it must never do is
     * hand back more seed than there were crops: a scythe that did would be a
     * seed farm that needs no field, and it would take somebody a week to work
     * out where the wheat was coming from.
     */
    public static int seedsFrom(int harvested) {
        return Math.max(0, harvested);
    }

    /** How many of those go straight back into the ground. */
    public static int replanted(int harvested) {
        return seedsFrom(harvested);
    }

    /** What the player actually keeps. Never more than they broke. */
    public static int seedsKept(int harvested) {
        return Math.max(0, seedsFrom(harvested) - replanted(harvested));
    }
}
