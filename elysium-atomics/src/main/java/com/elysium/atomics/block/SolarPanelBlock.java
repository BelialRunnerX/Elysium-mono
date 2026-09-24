package com.elysium.atomics.block;

import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The sun, which is free and nearly useless.
 *
 * <p>One panel makes three FE a tick, against a steam turbine's hundred and
 * twenty. That is deliberate and it is the point: a solar panel in this pack
 * is not a generator, it is the thing that makes a <em>cell</em> worth
 * building. A roof of them charges a bank through the day and the bank runs
 * the factory through the night, and the second half of that sentence is a
 * build rather than a purchase.
 *
 * <p>It asks the world whether it can see the sky and whether the sky is
 * light, and gives nothing whenever either answer is no — which includes rain,
 * because a panel that ignored weather would make the one interesting day in a
 * Minecraft week uninteresting.
 */
public class SolarPanelBlock extends GridBlock {

    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 4, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.SOLAR_PANEL.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            player.displayClientMessage(panel.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A panel, and the one question it asks the world. */
    public static class SolarPanelBlockEntity extends GridBlockEntity {

        /** How often it looks up. Not every tick: the answer changes slowly. */
        public static final int LOOKS_UP_EVERY = 20;

        private boolean lit;
        private int since;

        public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
            super(AtomicBlockEntities.SOLAR_PANEL.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return AtomicRules.SOLAR_PANEL.voltage();
        }

        @Nullable
        @Override
        public IndustryRules.Generator generator() {
            return lit ? AtomicRules.SOLAR_PANEL : null;
        }

        /** Whether it is making anything. */
        public boolean lit() {
            return lit;
        }

        public Component says() {
            if (!lit) {
                return Component.translatable("elysiumatomics.solar_panel.dark");
            }
            return Component.translatable("elysiumatomics.solar_panel.lit",
                    String.format("%.0f", AtomicRules.SOLAR_PANEL.output()));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (++since < LOOKS_UP_EVERY) {
                return;
            }
            since = 0;
            boolean now = level.canSeeSky(pos.above())
                    && level.isDay()
                    && !level.isRaining();
            if (now != lit) {
                lit = now;
                // What the grid is supplied with just changed.
                unsettle();
                setChanged();
            }
        }
    }
}
