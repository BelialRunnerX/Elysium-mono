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
 * Chemistry, which is what the age is actually about.
 *
 * <p>It makes the acid the enricher drinks and most of what the centrifuge
 * spins, so it is the machine a player builds second and the one everything
 * else in the age is downstream of. It is also the cheapest of the three, on
 * purpose: the gate into Age III should be the wire and the grid, not the
 * first machine.
 */
public class ReactionChamberBlock extends WorksBlock {

    public static final MapCodec<ReactionChamberBlock> CODEC = simpleCodec(ReactionChamberBlock::new);

    public ReactionChamberBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return AtomicRules.REACTION_CHAMBER;
    }

    @Override
    public KineticRules.Load load() {
        return AtomicRules.asLoad(AtomicRules.REACTION_CHAMBER);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return AtomicRecipes.type(AtomicRecipe.Kind.REACTING);
    }

    @Override
    public String saying() {
        return "elysiumatomics.reaction_chamber";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(AtomicBlockEntities.REACTION_CHAMBER.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.REACTION_CHAMBER.get());
    }
}
