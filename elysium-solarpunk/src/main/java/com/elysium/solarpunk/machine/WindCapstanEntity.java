package com.elysium.solarpunk.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Gathers power from open sky, and more of it with height.
 *
 * <h2>Why a second generator at all</h2>
 *
 * Because one generator is not a decision. The condenser is the obvious first
 * machine and it has a hard problem: it stops at dusk and stays stopped for half
 * the day, so a player's only answer is "build more condensers and a bigger
 * buffer", which is arithmetic rather than a choice.
 *
 * This runs at night. It is worse than a condenser at noon and better than one
 * at midnight, it wants altitude rather than a clear roof, and it is hurt by
 * being boxed in rather than by the weather. Put the two together and a base has
 * a reason to have both — and a reason to put them in different places, which is
 * the part that actually shows up in what a player builds.
 *
 * <h2>What "open" means here, and why it is not the condenser's test</h2>
 *
 * The condenser asks a column question: is there anything between me and the
 * sky. That is right for light and wrong for wind — a turbine in the bottom of a
 * narrow shaft has a clear view straight up and is in no wind at all.
 *
 * So this counts how many of its own sides are exposed. A capstan on a mast with
 * nothing around it gets everything; one bricked into a wall gets a fraction.
 * It still needs to see the sky, because a turbine under a floor is not in the
 * weather whatever its neighbours are doing.
 */
public class WindCapstanEntity extends SolarpunkMachineEntity {

    public static final int CAPACITY = 24_000;

    /** What it makes per tick, fully exposed, at the top of the world. */
    public static final int PEAK_OUTPUT = 14;

    /** At and below this, height counts for nothing. */
    public static final int BASE_HEIGHT = 64;

    /** At and above this, height counts for everything. */
    public static final int FULL_HEIGHT = 192;

    public static final int TRANSFER = 200;

    public WindCapstanEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.WIND_CAPSTAN_ENTITY.get(), pos, state,
                CAPACITY, 0, TRANSFER);
    }

    /**
     * What this position is worth, from 0 to {@link #PEAK_OUTPUT}.
     *
     * @param seesSky  nothing solid overhead
     * @param exposed  how many of the four horizontal sides are open, 0 to 4
     * @param height   the block's Y
     */
    public static int outputFor(boolean seesSky, int exposed, int height) {
        if (!seesSky || exposed <= 0) {
            return 0;
        }
        // Height first, as a fraction between the two marks. Sea level is
        // nothing extra; a tower is everything. Clamped at both ends so a
        // capstan in the void does not earn more than one on a mountain.
        int span = FULL_HEIGHT - BASE_HEIGHT;
        int above = Math.max(0, Math.min(span, height - BASE_HEIGHT));

        // Then exposure, as a share of the four sides. A single open face is a
        // quarter, which is deliberately still worth building: walling one in
        // should be a cost, not a disqualification.
        int byHeight = PEAK_OUTPUT * above / span;
        return Math.max(1, byHeight * Math.min(4, exposed) / 4);
    }

    /** How many of the four horizontal sides have nothing solid against them. */
    private int exposure() {
        int open = 0;
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos beside = worldPosition.relative(face);
            if (!level.getBlockState(beside).isSolidRender(level, beside)) {
                open++;
            }
        }
        return open;
    }

    @Override
    public void serverTick() {
        int made = outputFor(level.canSeeSky(worldPosition.above()),
                exposure(), worldPosition.getY());
        if (made > 0) {
            energy.produce(made);
        }
        SolarpunkPower.push(level, worldPosition, energy, TRANSFER);
    }
}
