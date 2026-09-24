package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A transformer: the only block that may have two voltages on it.
 *
 * <p>Everywhere else a higher tier meeting a lower one destroys the lower one.
 * That rule is the whole reason this block is worth building rather than worth
 * skipping, and it is why a grid is ever more than one tier wide.
 *
 * <p>What it does <b>not</b> do is isolate. A grid still carries only what its
 * weakest wire carries, so joining a high run to a low one through a
 * transformer gives you a grid limited by the low one — which is both what a
 * real weakest link does and the thing that makes a player eventually replace
 * every length rather than patching the ends. The transformer's job is to stop
 * the tiers destroying each other where they meet, and that is all.
 */
public class TransformerBlock extends GridBlock {

    public static final MapCodec<TransformerBlock> CODEC = simpleCodec(TransformerBlock::new);

    public TransformerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransformerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.TRANSFORMER.get());
    }

    /** The one node that carries between tiers. */
    public static class TransformerBlockEntity extends GridBlockEntity {

        public TransformerBlockEntity(BlockPos pos, BlockState state) {
            super(IndustryBlockEntities.TRANSFORMER.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return IndustryRules.Voltage.EXTREME;
        }

        @Override
        public boolean carries() {
            return false;                      // not wire: it adds no distance
        }

        @Override
        public boolean transforms() {
            return true;
        }
    }
}
