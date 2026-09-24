package com.elysium.arcana.block;

import com.elysium.arcana.rules.GeodeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.function.Supplier;

/**
 * The block buds grow out of.
 *
 * <p>Handed the four steps of its own chain rather than knowing them: the
 * chain lives in {@link GeodeRules#CHAIN} and this walks it. That is what
 * makes a fifth step a line in the rules, and what makes a chain with a hole
 * in it something the harness can refuse rather than something a player finds
 * by standing next to a bud that never changes.
 */
public class BuddingGeodeBlock extends Block {

    /** One chance in this many, per random tick, that a bud appears or grows. */
    public static final int CHANCE = 5;

    private final List<Supplier<Block>> chain;

    public BuddingGeodeBlock(BlockBehaviour.Properties properties,
                             List<Supplier<Block>> chain) {
        super(properties.randomTicks());
        if (chain.size() != GeodeRules.CHAIN.size()) {
            throw new IllegalStateException("the chain has " + GeodeRules.CHAIN.size()
                    + " steps and this budding block was given " + chain.size());
        }
        this.chain = List.copyOf(chain);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random) {
        if (random.nextInt(CHANCE) != 0) {
            return;
        }
        Direction face = Direction.values()[random.nextInt(Direction.values().length)];
        BlockPos at = pos.relative(face);
        BlockState there = level.getBlockState(at);
        Block next = null;
        if (canGrowInto(there)) {
            next = chain.get(0).get();
        } else {
            for (int step = 0; step < chain.size() - 1; step++) {
                if (there.is(chain.get(step).get())
                        && there.getValue(GeodeClusterBlock.FACING) == face) {
                    next = chain.get(step + 1).get();
                    break;
                }
            }
        }
        if (next == null) {
            return;
        }
        boolean water = there.getFluidState().getType() == Fluids.WATER;
        level.setBlockAndUpdate(at, next.defaultBlockState()
                .setValue(GeodeClusterBlock.FACING, face)
                .setValue(GeodeClusterBlock.WATERLOGGED, water));
    }

    private static boolean canGrowInto(BlockState state) {
        return state.isAir()
                || (state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8);
    }
}
