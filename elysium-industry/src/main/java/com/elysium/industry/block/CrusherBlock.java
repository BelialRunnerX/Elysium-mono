package com.elysium.industry.block;

import com.elysium.industry.recipe.IndustryRecipe;
import com.elysium.industry.recipe.PlantRecipe;
import com.elysium.industry.registry.IndustryRecipes;
import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What the millstone becomes. Ore in, grit out, at industrial scale.
 *
 * <p>The cheapest machine on the grid and the one a player builds first,
 * because it is the same job they have already been doing by hand and they can
 * see immediately what the grid bought them. It is deliberately not the ore
 * doubler — that is the separator, two tiers of wire away — so the first
 * machine of the age is a convenience and not the prize.
 */
public class CrusherBlock extends PlantBlock {

    public static final MapCodec<CrusherBlock> CODEC = simpleCodec(CrusherBlock::new);

    public CrusherBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return IndustryRules.CRUSHER;
    }

    @Override
    public KineticRules.Load load() {
        return IndustryRules.asLoad(IndustryRules.CRUSHER);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return IndustryRecipes.type(IndustryRecipe.Kind.CRUSHING);
    }

    @Override
    public String saying() {
        return "elysiumindustry.crusher";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(IndustryBlockEntities.CRUSHER.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.CRUSHER.get());
    }
}
