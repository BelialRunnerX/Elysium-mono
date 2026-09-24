package com.elysium.kinetics.block;

import com.elysium.kinetics.recipe.KineticRecipe;
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
 * A millstone: the first machine anybody builds.
 *
 * <p>It always turns about the vertical, because a millstone that ground
 * sideways would be a mill nobody has ever seen. That fixes its axis, which
 * means it sits on the end of a vertical shaft — the shape of every mill.
 */
public class MillstoneBlock extends MachineBlock {

    public static final MapCodec<MillstoneBlock> CODEC = simpleCodec(MillstoneBlock::new);

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 12, 16);

    public MillstoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public KineticRules.Load load() {
        return KineticRules.MILLSTONE;
    }

    @Override
    public KineticRecipe.Kind kind() {
        return KineticRecipe.Kind.MILLING;
    }

    @Override
    public String saying() {
        return "elysiumkinetics.millstone";
    }

    @Nullable
    @Override
    protected Direction.Axis fixedAxis() {
        return Direction.Axis.Y;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(KineticBlockEntities.MILLSTONE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.MILLSTONE.get());
    }
}
