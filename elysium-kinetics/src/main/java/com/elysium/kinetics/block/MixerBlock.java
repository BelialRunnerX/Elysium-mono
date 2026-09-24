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
 * A mixer: the expensive one.
 *
 * <p>Three times a millstone's draw and four times its speed. It is the reason
 * a player stops adding water wheels and starts thinking about steam, which is
 * the door out of Age I.
 */
public class MixerBlock extends MachineBlock {

    public static final MapCodec<MixerBlock> CODEC = simpleCodec(MixerBlock::new);

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public MixerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public KineticRules.Load load() {
        return KineticRules.MIXER;
    }

    @Override
    public KineticRecipe.Kind kind() {
        return KineticRecipe.Kind.MIXING;
    }

    @Override
    public String saying() {
        return "elysiumkinetics.mixer";
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
        return new MachineBlockEntity(KineticBlockEntities.MIXER.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.MIXER.get());
    }
}
