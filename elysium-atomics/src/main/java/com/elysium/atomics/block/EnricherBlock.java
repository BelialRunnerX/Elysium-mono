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
 * Ore dissolved in acid. The third unit of metal, and the last one.
 *
 * <p>Age I gives you the metal the ground had, Age II doubles it, this triples
 * it, and nothing above does better — which is the point of stating the ladder
 * as three rungs rather than as a number that keeps going. The separator is
 * not retired by this, it is <em>fed</em> by it: the enricher wants a wire and
 * a supply the separator never did.
 */
public class EnricherBlock extends WorksBlock {

    public static final MapCodec<EnricherBlock> CODEC = simpleCodec(EnricherBlock::new);

    public EnricherBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Machine machine() {
        return AtomicRules.ENRICHER;
    }

    @Override
    public KineticRules.Load load() {
        return AtomicRules.asLoad(AtomicRules.ENRICHER);
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {
        return AtomicRecipes.type(AtomicRecipe.Kind.ENRICHING);
    }

    @Override
    public String saying() {
        return "elysiumatomics.enricher";
    }

    /**
     * Three times what the recipe says, which is the whole reason to build one.
     *
     * The number is {@link AtomicRules#ENRICHER_MULTIPLIER}, and that is
     * written as Age II's plus one rather than as a three — so the spine's
     * material ladder is 1, 2, 3 by construction and cannot be re-tuned into
     * 1, 2, 4 in a single file.
     */
    @Override
    public int multiplier() {
        return AtomicRules.ENRICHER_MULTIPLIER;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(AtomicBlockEntities.ENRICHER.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.ENRICHER.get());
    }
}
