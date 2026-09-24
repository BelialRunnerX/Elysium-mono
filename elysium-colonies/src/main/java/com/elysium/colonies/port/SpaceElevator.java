package com.elysium.colonies.port;

import com.elysium.colonies.registry.PortBlocks;
import com.elysium.colonies.rules.PortRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Reading a space elevator out of the world.
 *
 * <h2>What makes it monolithic</h2>
 *
 * Not its size — a hut with a large footprint is still a hut. It is that every
 * part of it is load-bearing: an anchor, an unbroken tether, a counterweight,
 * and open sky. Take one block out of the middle of the column and the whole
 * thing stops, which is what separates infrastructure from furniture and is
 * the reason a spaceport is worth defending.
 *
 * <h2>Why the sky is checked</h2>
 *
 * Because a space elevator inside a mountain is a lift. The counterweight has
 * to be the highest thing above itself; a player who roofs over their own port
 * has built a very tall warehouse.
 */
public final class SpaceElevator {

    private SpaceElevator() {
    }

    /**
     * What the world has to say about an elevator.
     *
     * @param working whether it can ship anything at all
     * @param tether  how many tether blocks stand above the anchor
     * @param fault   where the column stops, or null when it does not
     * @param roofed  whether something stands between the counterweight and the sky
     * @param legs    how many of the flare's legs are standing under it
     */
    public record Reading(boolean working, int tether, BlockPos fault, boolean roofed,
                          int legs) {

        /** What a day at this port is worth, in items moved. */
        public int throughput() {
            return working ? PortRules.throughput(tether, legs) : 0;
        }
    }

    /**
     * How far the reader will climb before giving up.
     *
     * The world's own height. A column cannot be taller than the world, and a
     * bound that is not the world's would be a second, invisible height limit.
     */
    public static int ceilingOf(LevelReader level) {
        return level.getMaxBuildHeight();
    }

    /** Reads the elevator standing on this anchor. */
    public static Reading read(LevelReader level, BlockPos anchor) {
        // The flare first, because it is the cheaper question and the answer
        // is wanted whether or not the column turns out to stand.
        int legs = ElevatorBase.legs(level, anchor);
        int tether = 0;
        BlockPos at = anchor.above();
        int ceiling = ceilingOf(level);
        while (at.getY() < ceiling
                && level.getBlockState(at).is(PortBlocks.ELEVATOR_TETHER.get())) {
            tether++;
            at = at.above();
        }
        // `at` now stands one above the last tether block: the counterweight's
        // square, or the gap where the column gave out.
        boolean capped = level.getBlockState(at)
                .is(PortBlocks.ELEVATOR_COUNTERWEIGHT.get());
        if (!capped) {
            return new Reading(false, tether, at.immutable(), false, legs);
        }
        boolean roofed = !openAbove(level, at);
        return new Reading(PortRules.tall(tether) && !roofed, tether,
                PortRules.tall(tether) ? null : at.immutable(), roofed, legs);
    }

    /**
     * Whether anything stands between the counterweight and the sky.
     *
     * Read from the heightmap rather than by walking upward: the heightmap
     * already knows the highest solid block in the column and is a single
     * lookup, where a walk is as many block reads as there is sky.
     */
    public static boolean openAbove(LevelReader level, BlockPos counterweight) {
        int highest = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                counterweight.getX(), counterweight.getZ());
        return highest <= counterweight.getY() + 1;
    }

    /** What is wrong with it, as something a player can act on. */
    public static String complaint(Reading reading) {
        if (reading.working()) {
            return "";
        }
        if (reading.roofed()) {
            return "roofed";
        }
        if (reading.fault() != null && reading.tether() < PortRules.MINIMUM_TETHER
                && reading.fault().getY() > 0) {
            return "short";
        }
        return "broken";
    }
}
