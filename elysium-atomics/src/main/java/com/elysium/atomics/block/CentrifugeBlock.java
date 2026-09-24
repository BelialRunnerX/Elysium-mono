package com.elysium.atomics.block;

import com.elysium.atomics.recipe.AtomicRecipe;
import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.registry.AtomicRecipes;
import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.recipe.PlantRecipe;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Spinning a mixture until it separates. Where reactor fuel comes from.
 *
 * <p>The hungriest machine in the age, and the only route to enriched uranium,
 * so the reactor is not something a player places — it is something they have
 * already been running a factory for a while to be able to feed. That ordering
 * is deliberate: the one block in the pack that can take the roof off should
 * arrive after the player has learned what a grid does.
 */
public class CentrifugeBlock extends WorksBlock {

    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);

    public CentrifugeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return AtomicRules.CENTRIFUGE;
    }

    @Override
    public KineticRules.Load load() {
        return AtomicRules.asLoad(AtomicRules.CENTRIFUGE);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return AtomicRecipes.type(AtomicRecipe.Kind.CENTRIFUGING);
    }

    @Override
    public String saying() {
        return "elysiumatomics.centrifuge";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(AtomicBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.CENTRIFUGE.get());
    }
}
