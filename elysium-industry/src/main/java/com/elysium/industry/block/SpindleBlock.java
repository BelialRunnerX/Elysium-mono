package com.elysium.industry.block;

import com.elysium.kinetics.block.KineticLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A block with a shaft through it and wires on it: where the two ages meet.
 *
 * <p>There are exactly two of these — the alternator, which takes rotation and
 * gives electricity, and the motor, which does the reverse — and they need the
 * same three things that no other Age II block needs: an axis to turn about, a
 * place on Age I's line, and a line that gets solved when something on it
 * changes. Those are here so that neither of them owns them.
 *
 * <h2>Steel, and why it matters that it is said out loud</h2>
 *
 * Anything on an Age I line contributes its material to that line's weakest
 * link, and the default for a node is timber. An alternator that forgot to say
 * what it was made of would quietly cap a brass line at timber's shear rating
 * — the line would come apart at a speed the player had built for and been
 * told was safe. Age II machinery is steel, and says so.
 */
public abstract class SpindleBlock extends GridBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    protected SpindleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        KineticLine.disturb(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            KineticLine.disturb(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** On both networks at once: the grid's rules, and a place on a line. */
    public abstract static class SpindleBlockEntity extends GridBlockEntity
            implements SteelNode {

        /** What Age I's line last told this block it was turning at. */
        private float rpm;
        private boolean lineRunning;
        private boolean lineUnsettled = true;

        protected SpindleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().hasProperty(AXIS)
                    ? getBlockState().getValue(AXIS) : Direction.Axis.Y;
        }

        @Override
        public void driven(float speed, boolean running) {
            boolean changed = lineRunning != running || Math.abs(rpm - speed) > 0.01F;
            rpm = speed;
            lineRunning = running;
            lineUnsettled = false;
            if (changed) {
                // The grid's supply depends on how fast this is turning, so a
                // change in rotation is a change to the grid.
                unsettleGrid();
            }
        }

        /** How fast it is turning, unsigned. Zero when the line is not running. */
        public float rpm() {
            return lineRunning ? Math.abs(rpm) : 0.0F;
        }

        /** Whether the line through it is turning and intact. */
        public boolean lineRunning() {
            return lineRunning;
        }

        @Override
        public void unsettle() {
            super.unsettle();
            lineUnsettled = true;
        }

        /** Unsettle only the grid, without asking for the line again. */
        protected void unsettleGrid() {
            super.unsettle();
        }

        /** Unsettle only the line, for a block whose rotation follows its supply. */
        protected void unsettleLine() {
            lineUnsettled = true;
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (lineUnsettled) {
                // The line is Age I's to work out: this module knows what a
                // load is, and nothing about how a line is found.
                KineticLine.solve(level, pos);
            }
        }
    }
}
