package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.rules.AgronomyRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An accelerator: the machine that makes a pot worth putting on a grid.
 *
 * <p>It hurries every pot it touches, at {@link AgronomyRules#ACCELERATOR_FASTER}
 * when the grid can afford it and proportionally less when it cannot — slow
 * rather than stopped, like everything else in this pack. What it draws is a
 * constant given the pots beside it and never a function of what the grid can
 * give, because that would be a loop with no fixed point.
 *
 * <p>It is also the only reason a resource farm needs Age II at all. A room of
 * pots works with nothing but torches; a room of pots that keeps up with a
 * factory needs a factory.
 */
public class AcceleratorBlock extends GridBlock {

    public static final MapCodec<AcceleratorBlock> CODEC = simpleCodec(AcceleratorBlock::new);

    public AcceleratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AcceleratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AgronomyBlockEntities.ACCELERATOR.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof AcceleratorBlockEntity driver) {
            player.displayClientMessage(driver.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The working half: it counts pots and asks the grid for their sake. */
    public static class AcceleratorBlockEntity extends GridBlockEntity {

        /** How often it counts the pots beside it. They do not move often. */
        public static final int COUNTS_EVERY = 20;

        private int pots;
        private int since = COUNTS_EVERY;

        public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.ACCELERATOR.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return AgronomyRules.ACCELERATOR.rated();
        }

        /**
         * What it asks the grid for.
         *
         * Nothing at all when there is no pot beside it, so an accelerator
         * somebody has walled off is not a permanent tax on their grid — and a
         * constant otherwise, never a function of the share.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return pots <= 0 ? null : AgronomyRules.ACCELERATOR;
        }

        /** How many pots it is hurrying. */
        public int pots() {
            return pots;
        }

        public Component says() {
            if (pots <= 0) {
                return Component.translatable("elysiumagronomy.accelerator.alone");
            }
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumagronomy.accelerator.dark",
                        String.valueOf(pots));
            }
            return Component.translatable("elysiumagronomy.accelerator.running",
                    String.valueOf(pots),
                    String.format("%.1f", AgronomyRules.potSpeed(15, true, share())),
                    String.format("%.0f%%", share() * 100.0F));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (++since < COUNTS_EVERY) {
                return;
            }
            since = 0;
            int found = 0;
            for (Direction face : Direction.values()) {
                if (level.getBlockEntity(pos.relative(face))
                        instanceof PotBlock.PotBlockEntity) {
                    found++;
                }
            }
            if (found != pots) {
                pots = found;
                // What it costs the grid just changed.
                unsettle();
                setChanged();
            }
        }
    }
}
