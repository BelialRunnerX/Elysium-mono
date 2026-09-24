package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A cogwheel: the only thing in Age I that changes a speed.
 *
 * <p>Two sizes, eight teeth and twenty-four, and therefore three ratios: three
 * to one, one to three, and one to one between two of a size. That is a small
 * vocabulary on purpose — a player can work out what a gear train will do by
 * counting cogs, which is not true of a mod with six sizes.
 *
 * <p>A cog meshes with a cog beside it, and sits on the shaft through it. Both
 * of those are {@link KineticLine}'s rules rather than this block's; all this
 * has to know is how many teeth it has.
 */
public class CogwheelBlock extends KineticBlock {

    public static final MapCodec<CogwheelBlock> CODEC =
            simpleCodec(properties -> new CogwheelBlock(properties, KineticRules.SMALL_TEETH));

    private static final VoxelShape X = box(4, 0, 0, 12, 16, 16);
    private static final VoxelShape Y = box(0, 4, 0, 16, 12, 16);
    private static final VoxelShape Z = box(0, 0, 4, 16, 16, 12);

    private final int teeth;

    public CogwheelBlock(Properties properties, int teeth) {
        super(properties);
        this.teeth = teeth;
    }

    public int teeth() {
        return teeth;
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CogwheelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.COGWHEEL.get());
    }

    /** A cog, which is a shaft that also has teeth. */
    public static class CogwheelBlockEntity extends KineticBlockEntity {

        public CogwheelBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.COGWHEEL.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Override
        public int teeth() {
            return getBlockState().getBlock() instanceof CogwheelBlock cog
                    ? cog.teeth() : KineticRules.SMALL_TEETH;
        }
    }
}
