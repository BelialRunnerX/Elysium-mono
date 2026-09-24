package com.elysium.industry.block;

import com.elysium.industry.rules.IndustryRules;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Anything a grid can run through.
 *
 * <p>The electrical twin of {@code KineticNode}, and deliberately the same
 * shape: three questions, answered by the block, and every rule about who
 * connects to whom lives in {@link GridLine} instead of being spread across
 * the blocks. A new machine implements this; a new kind of connection is a
 * change to one file.
 */
public interface GridNode {

    /** What this block is wired for. A wire's own tier; a machine's rating. */
    IndustryRules.Voltage voltage();

    /** Whether power passes through this block rather than stopping at it. */
    default boolean carries() {
        return false;
    }

    /**
     * Whether this block steps power between tiers.
     *
     * A transformer is the one thing that may sit between two voltages without
     * one of them destroying the other, and it is the only reason a grid is
     * ever more than one tier wide.
     */
    default boolean transforms() {
        return false;
    }

    /** Which faces power may enter or leave by. All of them, by default. */
    default boolean connects(Direction face) {
        return true;
    }

    /** What this block puts into the grid, or null if it puts nothing. */
    @Nullable
    default IndustryRules.Generator generator() {
        return null;
    }

    /** What this block takes out, or null if it takes nothing. */
    @Nullable
    default IndustryRules.Machine machine() {
        return null;
    }

    /**
     * What this block could lend to the grid or borrow from it this tick, or
     * null if it stores nothing.
     *
     * <p>Nothing in Age II answers this. It is here because there is one grid
     * in the pack and one walk over it, and the age that adds storage has to
     * be able to join that grid rather than running a second one beside it —
     * see {@link IndustryRules.Reserve}, which is the whole of what this age
     * knows about batteries.
     */
    @Nullable
    default IndustryRules.Reserve reserve() {
        return null;
    }

    /**
     * Told what moved in and out of storage, and what the whole bank could
     * have moved, so a lender can work out its own share.
     *
     * Shared out by what each could have contributed rather than by position,
     * because build order is not something a player can see.
     */
    default void flowed(IndustryRules.Flow flow, IndustryRules.Reserve bank) {
    }

    /**
     * Told what the grid settled at.
     *
     * <p>The whole reading and not just the share, because a generator needs
     * to know whether anything wants what it is making. An engine that burned
     * fuel into a grid with nothing on it would be an engine every player
     * learns to break rather than switch off, and there is no switch.
     *
     * @param grid   what the whole grid settled at
     * @param blocks how many blocks of wire lie between this and the nearest
     *               generator, which is what the loss is charged on
     */
    void fed(IndustryRules.Reading grid, int blocks);

    /** Ask for the grid to be worked out again, because something changed. */
    void unsettle();
}
