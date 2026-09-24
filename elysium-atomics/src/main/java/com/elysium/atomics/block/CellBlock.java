package com.elysium.atomics.block;

import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

import java.util.List;

/**
 * A cell: the first block in the pack that keeps what it is given.
 *
 * <h2>What it changes about the game</h2>
 *
 * Every grid until now has been instantaneous — what is made this tick is what
 * is spent this tick, and building more generation than you need is waste. A
 * cell makes surplus worth having. It is the difference between a factory that
 * works while the sun is up and a factory, and it is why the solar panel in
 * this age is deliberately almost useless on its own.
 *
 * <h2>How it joins the grid</h2>
 *
 * Not by running one of its own. It tells Age II's grid two numbers — the most
 * it could hand over this tick and the most it could accept — and Age II's
 * settle does the rest, so there is one model of electricity in the pack and
 * a machine never has to know whether there is a battery behind it.
 *
 * <h2>And why it does not re-walk the grid every tick</h2>
 *
 * Because it almost never has to. What it reports is
 * {@code min(mostOut, held)} and {@code min(mostIn, room)}, and for all but
 * the first and last few seconds of a cell's life both of those are simply its
 * throughput — a constant. So it asks for the grid to be worked out again only
 * when the numbers it would report have actually moved, which on a full or
 * empty cell is once, not sixty times a second.
 */
public class CellBlock extends GridBlock {

    public static final MapCodec<CellBlock> CODEC =
            simpleCodec(properties -> new CellBlock(properties, AtomicRules.BASIC_CELL));

    private final AtomicRules.Cell cell;

    public CellBlock(Properties properties, AtomicRules.Cell cell) {
        super(properties);
        this.cell = cell;
    }

    public AtomicRules.Cell cell() {
        return cell;
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.CELL.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CellBlockEntity held) {
            player.displayClientMessage(held.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * How much a cell is holding, for a comparator to read.
     *
     * The one place a redstone signal is the right interface: "the bank is
     * below a quarter" is a sentence a player wants a hopper or a generator to
     * be able to act on without being there.
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CellBlockEntity held)) {
            return 0;
        }
        float full = held.held() / (float) held.cell().capacity();
        return full <= 0.0F ? 0 : 1 + Math.round(full * 14.0F);
    }

    // ------------------------------------------------------------------

    /** The working half: a number, and the discipline that keeps it honest. */
    public static class CellBlockEntity extends GridBlockEntity {

        private int held;

        /** What this last told the grid it could do, so it knows when that changed. */
        private float toldGive;
        private float toldTake;

        public CellBlockEntity(BlockPos pos, BlockState state) {
            super(AtomicBlockEntities.CELL.get(), pos, state);
        }

        public AtomicRules.Cell cell() {
            return getBlockState().getBlock() instanceof CellBlock block
                    ? block.cell() : AtomicRules.BASIC_CELL;
        }

        /** How much is in it. */
        public int held() {
            return held;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return cell().wire();
        }

        @Nullable
        @Override
        public IndustryRules.Reserve reserve() {
            return AtomicRules.reserveOf(List.of(cell()), List.of(held));
        }

        @Override
        public void flowed(IndustryRules.Flow flow, IndustryRules.Reserve bank) {
            int was = held;
            held = AtomicRules.after(cell(), held, flow, bank);
            IndustryRules.Reserve now = reserve();
            boolean moved = now.couldGive() != toldGive || now.couldTake() != toldTake;
            toldGive = now.couldGive();
            toldTake = now.couldTake();
            if (was != held) {
                setChanged();
            }
            if (moved) {
                // Only then. A cell in the middle of its range reports the same
                // two numbers every tick, and re-walking a grid of four thousand
                // blocks sixty times a second to be told so is how a tech mod
                // becomes the reason somebody's server lags.
                unsettle();
            }
        }

        /** What it would say if it could talk. */
        public Component says() {
            AtomicRules.Cell cell = cell();
            String amount = String.format("%,d", held);
            String capacity = String.format("%,d", cell.capacity());
            String full = String.format("%.0f%%", 100.0F * held / cell.capacity());
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (supply() > demand()) {
                return Component.translatable("elysiumatomics.cell.charging",
                        amount, capacity, full);
            }
            if (demand() > supply() && held > 0) {
                return Component.translatable("elysiumatomics.cell.draining",
                        amount, capacity, full);
            }
            return Component.translatable("elysiumatomics.cell.holding",
                    amount, capacity, full);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            held = Math.max(0, Math.min(cell().capacity(), tag.getInt("held")));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("held", held);
        }
    }
}
