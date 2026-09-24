package com.elysium.solarpunk.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gathers power from direct skylight, and less of it under cover.
 *
 * That sentence was this machine's entry in {@code MACHINES.md} for as long as
 * it was a prop, and it is the specification: both halves of it are checked in
 * {@code validate.py} against the numbers below.
 *
 * <h2>How "direct" is decided</h2>
 *
 * Two questions, not one, because they fail differently:
 *
 * <ul>
 *   <li><b>Can it see the sky at all?</b> {@code canSeeSky} is a column test —
 *       nothing solid between this block and the height limit. A condenser with
 *       a roof over it produces nothing, whatever the light level says.</li>
 *   <li><b>How bright is the sky right now?</b> The skylight value, which falls
 *       at dusk, in rain and under a thunderstorm, and is zero at night.</li>
 * </ul>
 *
 * Asking only the second would let a condenser in a brightly torch-lit cellar
 * generate, because {@code getMaxLocalRawBrightness} does not care where light
 * came from. Asking only the first would have it run at full rate at midnight.
 *
 * <h2>Why it pushes rather than waiting to be pulled</h2>
 *
 * A generator that only fills its own buffer is a generator that stops the
 * moment the buffer is full, and the player has to build something that pulls.
 * Pushing to whatever is adjacent means a condenser next to a machine works
 * with no intermediate part, which is the whole promise of "put it in the sun".
 * The pushing itself is in {@link SolarpunkPower}, shared with the capstan.
 */
public class SolarCondenserEntity extends SolarpunkMachineEntity {

    /** Ceiling on the buffer. Roughly a minute of full output. */
    public static final int CAPACITY = 24_000;

    /** What it makes per tick at noon under open sky. */
    public static final int PEAK_OUTPUT = 20;

    /** What it will hand a neighbour per tick. */
    public static final int TRANSFER = 200;

    public SolarCondenserEntity(BlockPos pos, BlockState state) {
        // Receives nothing: this is a source. A condenser that accepted power
        // would let two of them sit facing each other looking productive.
        super(SolarpunkMachines.SOLAR_CONDENSER_ENTITY.get(), pos, state,
                CAPACITY, 0, TRANSFER);
    }

    /**
     * What this position is worth, from 0 to {@link #PEAK_OUTPUT}.
     *
     * Separated out and given only the two facts it needs so the curve can be
     * exercised without a world — see {@code tools/MachineHarness.java}.
     */
    public static int outputFor(boolean seesSky, int skyLight) {
        if (!seesSky || skyLight <= 0) {
            return 0;
        }
        // Linear in the light level rather than a step, so dusk, rain and a
        // thunderhead each cost something instead of everything at once.
        return Math.max(1, PEAK_OUTPUT * Math.min(15, skyLight) / 15);
    }

    @Override
    public void serverTick() {
        int made = outputFor(
                level.canSeeSky(worldPosition.above()),
                level.getBrightness(LightLayer.SKY, worldPosition.above())
                        - level.getSkyDarken());
        if (made > 0) {
            energy.receiveEnergy(made, false);
        }
        SolarpunkPower.push(level, worldPosition, energy, TRANSFER);
    }

}
