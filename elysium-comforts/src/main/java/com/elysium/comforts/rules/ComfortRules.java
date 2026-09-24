package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * The small mercies, in numbers. The JDK and nothing else at all.
 *
 * <h2>What a grave is for</h2>
 *
 * Dying should cost you something. It should not cost you the four hours of
 * your evening that went into what you were carrying, and it should not cost
 * you them <em>silently</em>, in lava, at the bottom of a ravine, while you
 * run back from a bed eight hundred blocks away.
 *
 * <p>So: everything you were carrying goes into a grave where you fell, the
 * grave is somewhere a player can actually stand, and only you can open it
 * until long enough has passed that you have plainly given up on it.
 *
 * <h2>The property, and why it is arithmetic</h2>
 *
 * <b>Nothing a player owned is ever lost.</b> That is not a sentiment, it is a
 * counting problem: a grave with fewer slots than the player had stacks is a
 * grave that quietly eats the remainder, and the only way to have fewer slots
 * than the player had is to have decided in advance how many there are. Every
 * gravestone mod that has ever lost somebody's backpack did it by assuming
 * forty-one.
 */
public final class ComfortRules {

    private ComfortRules() {
    }

    // ==================================================================
    // How big a grave is
    // ==================================================================

    /**
     * The most a grave will ever hold.
     *
     * <p>Generous rather than tight, because the number that matters is on the
     * other side: it has to be larger than any inventory anybody could arrive
     * with, including whatever curios, backpacks and pocket dimensions are
     * installed. Being wrong upwards costs a few empty slots in a block
     * nobody keeps; being wrong downwards costs somebody their evening.
     */
    public static final int MOST_SLOTS = 512;

    /**
     * How many slots a grave needs for this many stacks.
     *
     * <p>Exactly as many as there are, which is the whole rule. A grave is
     * built around what it is given rather than given a size in advance, so
     * there is no arrangement of inventory mods that overflows it.
     */
    public static int slotsFor(int stacks) {
        return Math.max(1, Math.min(MOST_SLOTS, Math.max(0, stacks)));
    }

    /**
     * How many stacks would not fit, which must always be none.
     *
     * <p>Written as its own function rather than left implicit at the call
     * site, because "nothing is lost" deserves one place to be wrong and one
     * thing for a harness to watch.
     */
    public static int spilled(int stacks) {
        return Math.max(0, stacks - slotsFor(stacks));
    }

    // ==================================================================
    // Where a grave goes
    // ==================================================================

    /**
     * How far from where you fell a grave may be moved to find somewhere it
     * can sit.
     *
     * <p>Small on purpose: a grave that wanders is a grave you cannot find,
     * and the point of putting it where you died is that you already know
     * where that is.
     */
    public static final int LOOK_WITHIN = 5;

    /**
     * Where the grave actually goes, given where you fell.
     *
     * <p>Clamped into the world, always. Falling out of the bottom of the
     * world is the single most common way to die with a full inventory, and
     * a grave placed at the height you died at would be a grave in the void —
     * which is to say no grave at all.
     *
     * @param died    the height you died at
     * @param floor   the lowest block the world has
     * @param ceiling the highest
     */
    public static int graveHeight(int died, int floor, int ceiling) {
        int lowest = Math.min(floor, ceiling);
        int highest = Math.max(floor, ceiling);
        // One above the floor rather than on it: a grave in bedrock is a grave
        // nobody can break, and the floor of a world is usually bedrock.
        int atLeast = Math.min(lowest + 1, highest);
        return Math.max(atLeast, Math.min(highest, died));
    }

    /** Whether the grave had to be moved from where the player fell. */
    public static boolean moved(int died, int floor, int ceiling) {
        return graveHeight(died, floor, ceiling) != died;
    }

    // ==================================================================
    // Who may open it
    // ==================================================================

    /**
     * How long a grave is the owner's alone.
     *
     * <p>Half an hour of game time. Long enough that nobody loses a race back
     * to their own death point, short enough that a grave in the middle of a
     * shared base is not a permanent obstacle nobody can clear.
     */
    public static final int PRIVATE_FOR = 20 * 60 * 30;

    /**
     * Whether somebody may open this grave.
     *
     * @param owner    whether they are the player who died
     * @param operator whether they can bypass it anyway
     * @param age      how many ticks old the grave is
     */
    public static boolean mayOpen(boolean owner, boolean operator, long age) {
        return owner || operator || age >= PRIVATE_FOR;
    }

    /** How many ticks until anybody may open it. Zero once anybody may. */
    public static long publicIn(long age) {
        return Math.max(0L, PRIVATE_FOR - Math.max(0L, age));
    }

    // ==================================================================
    // What dying costs
    // ==================================================================

    /**
     * The share of your experience a grave keeps for you.
     *
     * <h2>Why it is not all of it</h2>
     *
     * Because dying has to cost something, or the whole of the game's risk
     * goes with it — and experience is the right thing for it to cost, since
     * it is the one resource you can always get more of by playing. Your
     * items are four hours of your evening; your levels are twenty minutes in
     * a mine.
     */
    public static final float XP_KEPT = 0.75F;

    /** How much experience is in the grave, given what you had. */
    public static int experienceKept(int had) {
        if (had <= 0) {
            return 0;
        }
        return (int) Math.floor(had * XP_KEPT);
    }

    // ==================================================================
    // Loot that belongs to whoever opened it
    // ==================================================================

    /**
     * The seed one player gets for one container.
     *
     * <h2>Why it is derived rather than stored</h2>
     *
     * A chest in a dungeon should give every player their own roll, and the
     * same player the <em>same</em> roll every time they come back — otherwise
     * closing and reopening is a reroll, and a reroll is a slot machine.
     *
     * <p>The obvious way to do that is to remember what each player got, and
     * that is what every mod in this space does: a table of player against
     * container that grows for as long as the server runs and is never safe to
     * clear. The better way is to make the roll a <em>function</em> of the
     * player and the container. Then there is nothing to remember, nothing to
     * clear, and a player who comes back in a year gets exactly what they left
     * — with no record of them anywhere.
     *
     * <p>What still has to be stored is what they have <em>taken</em>, which
     * is unavoidable and is at least a thing that empties itself.
     *
     * <h2>The mixing</h2>
     *
     * SplitMix64's finaliser, applied to the three numbers in turn. It is four
     * lines, it has no state, and its avalanche is measured by the harness
     * rather than asserted here: flipping one bit of a player's id has to
     * change about half the bits of the seed, or two players who differ by one
     * bit get two rolls that differ by nothing.
     */
    public static long seedFor(long container, long playerHigh, long playerLow) {
        return mix(mix(mix(container) ^ playerHigh) ^ playerLow);
    }

    /** SplitMix64's finaliser: the whole of the mixing, in one place. */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Whether a player's copy of a container may be forgotten.
     *
     * <p>Only once it is empty. A copy with something in it is a copy somebody
     * is coming back for, however long ago they opened it; a copy with nothing
     * in it says nothing the seed cannot say again, so keeping it is a table
     * that grows for no reason.
     */
    public static boolean forgettable(boolean emptied) {
        return emptied;
    }

    // ==================================================================
    // What a grave is holding, as something a harness can count
    // ==================================================================

    /**
     * One stack, as a name and a number.
     *
     * <p>Deliberately not an item stack: what is worth proving about a grave
     * has nothing to do with durability or components, so it is proved on a
     * structure the harness can build a million of.
     */
    public record Holding(String item, int count) {

        public Holding {
            if (count < 1) {
                throw new IllegalArgumentException("a stack of none is not a stack");
            }
        }
    }

    /**
     * Put a player's carried stacks into a grave of the right size.
     *
     * <p>The list that comes back is the list that went in, in the same order,
     * with nothing merged and nothing dropped. Merging looks harmless and is
     * not: two stacks of the same item with different damage are not the same
     * stack, and a grave that merged them would hand back one of them twice
     * and neither of them properly.
     */
    public static List<Holding> intoGrave(List<Holding> carried) {
        List<Holding> grave = new ArrayList<>(slotsFor(carried.size()));
        for (int at = 0; at < carried.size() && at < MOST_SLOTS; at++) {
            grave.add(carried.get(at));
        }
        return List.copyOf(grave);
    }

    /** How many items in total a list of stacks comes to. */
    public static long itemsIn(List<Holding> holdings) {
        long total = 0L;
        for (Holding holding : holdings) {
            total += holding.count();
        }
        return total;
    }
}
