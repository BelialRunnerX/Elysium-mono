package com.elysium.colonies.port;

import com.elysium.colonies.registry.PortBlocks;
import com.elysium.colonies.rules.PortRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * How many legs are holding this elevator up.
 *
 * <h2>Why it is read rather than recorded</h2>
 *
 * Because the alternative is a spaceport that remembers being braced. A player
 * who blows the legs off a port — or a creeper that does it for them — should
 * find the port shipping less that day, and the only way that happens is if
 * the question is asked of the world rather than of a file.
 *
 * <h2>Why it is forgiving about where a leg is</h2>
 *
 * The blueprint draws the curve exactly; a player building by hand will not,
 * and should not have to. So a leg counts if there is anything load-bearing
 * within {@link PortRules#LEG_TOLERANCE} of where the curve says it should be,
 * at four heights spread up the flare. That accepts a hand-built flare of the
 * right shape and refuses a decorative stub at the bottom, which is the line
 * worth drawing.
 */
public final class ElevatorBase {

    private ElevatorBase() {
    }

    /**
     * How many of the four legs are standing.
     *
     * @param anchor the block at the bottom of the elevator, on the deck
     */
    public static int legs(LevelReader level, BlockPos anchor) {
        int standing = 0;
        for (int leg = 0; leg < PortRules.LEGS; leg++) {
            if (holds(level, anchor, leg)) {
                standing++;
            }
        }
        return standing;
    }

    /** Whether one leg is there at every height the rules look at. */
    private static boolean holds(LevelReader level, BlockPos anchor, int leg) {
        for (int height : PortRules.legSamples()) {
            if (!found(level, anchor, leg, height)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether anything of the leg is near where the curve puts it.
     *
     * A square search rather than a single block, because a leg is several
     * blocks thick and a player's hand-built one will not land on the exact
     * pixel the arithmetic chose.
     */
    private static boolean found(LevelReader level, BlockPos anchor, int leg,
                                 int height) {
        int wanted = PortRules.LEG_TOLERANCE;
        BlockPos middle = anchor.offset(PortRules.legX(leg, height), height,
                PortRules.legZ(leg, height));
        for (int x = -wanted; x <= wanted; x++) {
            for (int z = -wanted; z <= wanted; z++) {
                BlockPos at = middle.offset(x, 0, z);
                if (!level.hasChunkAt(at)) {
                    continue;
                }
                if (level.getBlockState(at).is(PortBlocks.ELEVATOR_PYLON.get())) {
                    return true;
                }
            }
        }
        return false;
    }
}
