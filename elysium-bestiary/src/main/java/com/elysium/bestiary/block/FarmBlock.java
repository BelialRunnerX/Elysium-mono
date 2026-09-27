package com.elysium.bestiary.block;

import com.elysium.bestiary.rules.FarmRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * One class for all nine machines, driven by its row.
 *
 * <p>The same argument every other roster in this module makes, and here it is
 * not really about saving files: the nine machines differ in a kind and two
 * multipliers, and the multipliers are the thing {@link FarmRules} proves
 * something about. Nine block classes would be nine places where a multiplier
 * could quietly stop being the row's, and the proof would still pass while the
 * game did something else.
 *
 * <p>So this class knows how to find its own row and nothing about what any
 * kind of machine does. That lives in
 * {@link com.elysium.bestiary.block.act.FarmActs}, one branch per kind and no
 * machine named in any of them.
 */
public class FarmBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Which machine this is, by the name it was registered under. */
    private final String name;

    public static final com.mojang.serialization.MapCodec<FarmBlock> CODEC =
            simpleCodec(properties -> new FarmBlock(properties, "mob_fan"));

    public FarmBlock(Properties properties, String name) {
        super(properties);
        this.name = name;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    /** The row this one is. */
    public FarmRules.Machine machine() {
        FarmRules.Machine found = FarmRules.machine(name);
        return found == null ? FarmRules.MACHINES.get(0) : found;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos where, BlockState state) {
        return new FarmBlockEntity(where, state, name);
    }

    /**
     * What ticks and what does not.
     *
     * <p>Only the powered ones. A block that sits there and costs a tick every
     * tick is the thing that makes a big farm unplayable, and three of the nine
     * have nothing to do on a clock at all.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || !machine().powered()) {
            return null;
        }
        return (ticking, where, at, entity) -> {
            if (entity instanceof FarmBlockEntity farm) {
                farm.serverTick();
            }
        };
    }
}
