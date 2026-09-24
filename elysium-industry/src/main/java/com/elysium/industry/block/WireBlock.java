package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
 * A run of wire. The connective tissue of the second age.
 *
 * <p>It gives nothing and takes nothing: it is on the grid so that two things
 * further apart than one block can be on the same grid. What it contributes is
 * its <b>tier</b> — and the weakest tier anywhere on a grid is what decides how
 * much that grid may carry before something burns, exactly as the weakest
 * shaft decides how fast an Age I line may turn.
 */
public class WireBlock extends GridBlock {

    public static final MapCodec<WireBlock> CODEC =
            simpleCodec(properties -> new WireBlock(properties, IndustryRules.Voltage.LOW));

    private static final VoxelShape SHAPE = box(6, 6, 6, 10, 10, 10);

    private final IndustryRules.Voltage voltage;

    public WireBlock(Properties properties, IndustryRules.Voltage voltage) {
        super(properties);
        this.voltage = voltage;
    }

    public IndustryRules.Voltage voltage() {
        return voltage;
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
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
        return new WireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.WIRE.get());
    }

    /** A length of wire, and the tier it is rated for. */
    public static class WireBlockEntity extends GridBlockEntity {

        public WireBlockEntity(BlockPos pos, BlockState state) {
            super(IndustryBlockEntities.WIRE.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return getBlockState().getBlock() instanceof WireBlock wire
                    ? wire.voltage() : IndustryRules.Voltage.LOW;
        }

        @Override
        public boolean carries() {
            return true;
        }
    }
}
