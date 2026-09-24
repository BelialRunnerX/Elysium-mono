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
 * The press, electrified, and the machine that makes plates.
 *
 * <p>Plate is the material every multiblock in the ages above is skinned in,
 * so this is the first machine whose output is not a shortcut for something a
 * player could already make. Age I could squash things; nothing in Age I could
 * roll a sheet.
 */
public class RollingMillBlock extends PlantBlock {

    public static final MapCodec<RollingMillBlock> CODEC = simpleCodec(RollingMillBlock::new);

    public RollingMillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return IndustryRules.ROLLING_MILL;
    }

    @Override
    public KineticRules.Load load() {
        return IndustryRules.asLoad(IndustryRules.ROLLING_MILL);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return IndustryRecipes.type(IndustryRecipe.Kind.ROLLING);
    }

    @Override
    public String saying() {
        return "elysiumindustry.rolling_mill";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(IndustryBlockEntities.ROLLING_MILL.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.ROLLING_MILL.get());
    }
}
