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
 * Heat without a fire. The first thing that needs a real grid.
 *
 * <h2>Why it is the expensive one</h2>
 *
 * At {@code 512} FE per tick it is two thirds of what a diesel engine makes
 * and twice what a whole {@code LOW} wire will carry before it burns, so the
 * first arc furnace is the moment a player has to understand voltage rather
 * than read about it: aluminium wire, a transformer where it meets the copper,
 * and a generator that can actually feed it.
 *
 * <p>That lesson is the machine's real output. The alloys are what it is for.
 */
public class ArcFurnaceBlock extends PlantBlock {

    public static final MapCodec<ArcFurnaceBlock> CODEC = simpleCodec(ArcFurnaceBlock::new);

    public ArcFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return IndustryRules.ARC_FURNACE;
    }

    @Override
    public KineticRules.Load load() {
        return IndustryRules.asLoad(IndustryRules.ARC_FURNACE);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return IndustryRecipes.type(IndustryRecipe.Kind.SMELTING);
    }

    @Override
    public String saying() {
        return "elysiumindustry.arc_furnace";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(IndustryBlockEntities.ARC_FURNACE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.ARC_FURNACE.get());
    }
}
