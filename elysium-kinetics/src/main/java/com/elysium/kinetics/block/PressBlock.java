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
 * A press: heavy, slow, and the machine that needs the least speed.
 *
 * <p>It draws twice a millstone's torque and asks twice its speed, which is
 * the trade Age I is built on: a line that turns a press comfortably is a line
 * that has had a second water wheel put on it.
 */
public class PressBlock extends MachineBlock {

    public static final MapCodec<PressBlock> CODEC = simpleCodec(PressBlock::new);

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 14, 16);

    public PressBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public KineticRules.Load load() {
        return KineticRules.PRESS;
    }

    @Override
    public KineticRecipe.Kind kind() {
        return KineticRecipe.Kind.PRESSING;
    }

    @Override
    public String saying() {
        return "elysiumkinetics.press";
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
        return new MachineBlockEntity(KineticBlockEntities.PRESS.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.PRESS.get());
    }
}
