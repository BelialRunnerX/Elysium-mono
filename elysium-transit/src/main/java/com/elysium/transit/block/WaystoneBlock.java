package com.elysium.transit.block;

import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.transit.registry.TransitAttachments;
import com.elysium.transit.registry.TransitBlockEntities;
import com.elysium.transit.rules.TransitRules;
import com.elysium.transit.travel.WaystoneTravel;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A waystone: somewhere you have been, and a price to go back.
 *
 * <h2>What it is, in one line</h2>
 *
 * A machine on Age II's grid that spends its charge sending you somewhere you
 * have already walked to. Binding one is free and is the only thing here that
 * cannot be bought with power; going anywhere costs, and the price is
 * arranged so that no chain of jumps is ever cheaper than the jump it
 * replaces.
 *
 * <h2>Why an unpowered one still works</h2>
 *
 * Not by a special case in the price — that is exactly the mistake that makes
 * a network free — but because a stone fills from nothing at a trickle. The
 * short way home costs about a minute of standing about when it is unwired,
 * and nothing at all when it is wired, which is the same argument the rest of
 * this pack makes about every other machine.
 */
public class WaystoneBlock extends GridBlock {

    public static final MapCodec<WaystoneBlock> CODEC = simpleCodec(WaystoneBlock::new);

    public WaystoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaystoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, TransitBlockEntities.WAYSTONE.get());
    }

    /**
     * Whoever puts one down has been there, so they know it.
     *
     * A stone you have to place and then click is a stone somebody will place,
     * walk away from and never bind — and find out at the far end of a
     * journey.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            TransitAttachments.learn(player, WaystoneTravel.placeOf(level, pos),
                    WaystoneTravel.nameOf(level, pos));
        }
    }

    /** A name tag names it, which is the only naming this module needs. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!held.is(Items.NAME_TAG) || !held.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof WaystoneBlockEntity waystone) {
            String name = held.getHoverName().getString();
            waystone.rename(name);
            TransitAttachments.learn(player, WaystoneTravel.placeOf(level, pos), name);
            player.displayClientMessage(
                    Component.translatable("elysiumtransit.waystone.named", name), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(level.getBlockEntity(pos) instanceof WaystoneBlockEntity waystone)
                || !(player instanceof ServerPlayer sent)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(waystone.says(), true);
            return InteractionResult.CONSUME;
        }
        TransitRules.Waypoint here = WaystoneTravel.placeOf(level, pos);
        if (TransitAttachments.learn(player, here, waystone.name(level, pos))) {
            player.displayClientMessage(Component.translatable(
                    "elysiumtransit.waystone.bound", waystone.name(level, pos)), true);
            return InteractionResult.CONSUME;
        }
        WaystoneTravel.open(sent, pos, waystone);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------

    /** The working half: a charge, and a name. */
    public static class WaystoneBlockEntity extends GridBlockEntity {

        private float held;
        private String named = "";

        public WaystoneBlockEntity(BlockPos pos, BlockState state) {
            super(TransitBlockEntities.WAYSTONE.get(), pos, state);
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return TransitRules.WAYSTONE.rated();
        }

        /**
         * What it asks the grid for.
         *
         * <p>A constant while it has room, and nothing when it is full. Never
         * a function of what the grid can give it: a machine that asked for
         * less when the grid was short would change what the grid could
         * afford, which would change what it asked for, and that never
         * settles.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return held >= TransitRules.BUFFER ? null : TransitRules.WAYSTONE;
        }

        public float held() {
            return held;
        }

        public void rename(String name) {
            named = name == null ? "" : name.replace('|', ' ');
            setChanged();
        }

        /** What it is called: what somebody named it, or where it is. */
        public String name(Level level, BlockPos pos) {
            return named.isBlank() ? pos.getX() + ", " + pos.getZ() : named;
        }

        /** Take the price of a jump out of it. */
        public boolean spend(float price) {
            if (held < price) {
                return false;
            }
            held -= price;
            unsettle();
            setChanged();
            return true;
        }

        /** What it would say if it could talk. */
        public Component says() {
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            double reach = TransitRules.reach(held);
            return Component.translatable("elysiumtransit.waystone.charge",
                    String.format("%.0f", held), String.valueOf(TransitRules.BUFFER),
                    Double.isInfinite(reach) ? "∞" : String.format("%.0f", reach),
                    String.format("%.0f", TransitRules.fillRate(share())));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            float was = held;
            held = TransitRules.after(held, share());
            if (was < TransitRules.BUFFER && held >= TransitRules.BUFFER) {
                // It has stopped asking the grid for anything, which is a
                // change to what the grid is carrying.
                unsettle();
            }
            if (held != was) {
                setChanged();
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            held = tag.getFloat("held");
            named = tag.getString("named");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putFloat("held", held);
            tag.putString("named", named);
        }
    }
}
