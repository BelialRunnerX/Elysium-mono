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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A length of shaft. The connective tissue of the whole age.
 *
 * <p>It gives nothing and takes nothing: it is on the line so that two things
 * further apart than one block can be on the same line. The material it is
 * made of is the only thing it contributes, and that is the thing that decides
 * how fast the whole line may turn before something comes apart.
 */
public class ShaftBlock extends KineticBlock {

    public static final MapCodec<ShaftBlock> CODEC =
            simpleCodec(properties -> new ShaftBlock(properties, KineticRules.Shaft.TIMBER));

    private static final double THIN = 5.0D;
    private static final VoxelShape X = box(0, THIN, THIN, 16, 16 - THIN, 16 - THIN);
    private static final VoxelShape Y = box(THIN, 0, THIN, 16 - THIN, 16, 16 - THIN);
    private static final VoxelShape Z = box(THIN, THIN, 0, 16 - THIN, 16 - THIN, 16);

    private final KineticRules.Shaft material;

    public ShaftBlock(Properties properties, KineticRules.Shaft material) {
        super(properties);
        this.material = material;
    }

    public KineticRules.Shaft material() {
        return material;
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

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShaftBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.SHAFT.get());
    }

    /** Nothing but a length of shaft, and what it is made of. */
    public static class ShaftBlockEntity extends KineticBlockEntity {

        public ShaftBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.SHAFT.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Override
        public KineticRules.Shaft material() {
            return getBlockState().getBlock() instanceof ShaftBlock shaft
                    ? shaft.material() : KineticRules.Shaft.TIMBER;
        }
    }
}
