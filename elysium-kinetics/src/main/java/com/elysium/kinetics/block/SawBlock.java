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
 * A saw: wants speed more than it wants force.
 *
 * <p>The machine that teaches gearing. No number of water wheels reaches the
 * twenty-four rpm it needs, because no water wheel turns faster than sixteen
 * however many you bolt together — so the first saw a player builds is the
 * first time they put a cog on a cog.
 */
public class SawBlock extends MachineBlock {

    public static final MapCodec<SawBlock> CODEC = simpleCodec(SawBlock::new);

    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 14, 15);

    public SawBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public KineticRules.Load load() {
        return KineticRules.SAW;
    }

    @Override
    public KineticRecipe.Kind kind() {
        return KineticRecipe.Kind.SAWING;
    }

    @Override
    public String saying() {
        return "elysiumkinetics.saw";
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(KineticBlockEntities.SAW.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.SAW.get());
    }
}
