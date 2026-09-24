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
 * Ore in, twice the metal out. The reason Age II exists.
 *
 * <p>Everything else in the age is faster or bigger. This is the one machine
 * that gives a player something they could not have got at all, and it is
 * placed at {@code MEDIUM} voltage on purpose: the doubling is the payment for
 * having built a real grid, not a reward for having built a machine.
 */
public class SeparatorBlock extends PlantBlock {

    public static final MapCodec<SeparatorBlock> CODEC = simpleCodec(SeparatorBlock::new);

    public SeparatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return IndustryRules.SEPARATOR;
    }

    @Override
    public KineticRules.Load load() {
        return IndustryRules.asLoad(IndustryRules.SEPARATOR);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return IndustryRecipes.type(IndustryRecipe.Kind.SEPARATING);
    }

    @Override
    public String saying() {
        return "elysiumindustry.separator";
    }

    /**
     * Twice what the recipe says, which is the whole reason to build one.
     *
     * The number is {@link IndustryRules#SEPARATOR_MULTIPLIER} and lives
     * there, not in the recipe files: it is a rung on the material ladder —
     * Age I gives you the metal, Age II doubles it, Age III triples it — and a
     * ladder written into data in two hundred places is a ladder nobody can
     * re-tune.
     */
    @Override
    public int multiplier() {
        return IndustryRules.SEPARATOR_MULTIPLIER;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(IndustryBlockEntities.SEPARATOR.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.SEPARATOR.get());
    }
}
