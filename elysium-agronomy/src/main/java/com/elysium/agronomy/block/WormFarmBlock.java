package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.FishItems;
import com.elysium.agronomy.registry.FishingBlockEntities;
import com.elysium.agronomy.rules.TackleRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A worm farm: bait, at a rate, for the family it was fed.
 *
 * <p>Feed it a fish and it makes the bait that catches more of that fish's
 * family ({@link TackleRules#baitFor}), one every {@link
 * TackleRules#WORM_TICKS}, until it holds {@link TackleRules#WORM_FARM_HOLDS}
 * and waits to be emptied. Use it empty-handed to take what it has made. The
 * rate is the rules' rate, which the harness holds under {@link
 * TackleRules#MOST_BAIT_PER_HOUR}: bait without limit is a rod that always
 * catches what it is pointed at.
 */
public class WormFarmBlock extends BaseEntityBlock {

    public static final MapCodec<WormFarmBlock> CODEC = simpleCodec(WormFarmBlock::new);

    public WormFarmBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Farm(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != FishingBlockEntities.WORM_FARM.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((Farm) entity).work();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        String family = TackleRules.baitFor(AgronomyComponents.ourId(held));
        if (family.isEmpty() || !(level.getBlockEntity(pos) instanceof Farm farm)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack left = farm.feed(family);
            if (!left.isEmpty()) {
                player.getInventory().placeItemBackInInventory(left);
            }
            held.consume(1, player);
            player.displayClientMessage(Component.translatable(
                    "elysiumagronomy.worm_farm.fed",
                    Component.translatable("item.elysiumagronomy." + family + "_bait")), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Farm farm)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = farm.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(farm.says(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof Farm farm) {
            ItemStack out = farm.takeAll();
            if (!out.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(),
                        pos.getZ(), out);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: which family it was fed, what it holds, and how far through the next. */
    public static class Farm extends BlockEntity {

        private String family = "";
        private int held;
        private int worked;

        public Farm(BlockPos pos, BlockState state) {
            super(FishingBlockEntities.WORM_FARM.get(), pos, state);
        }

        /** One tick: a bait every {@link TackleRules#WORM_TICKS}, until it is full. */
        public void work() {
            if (family.isEmpty() || held >= TackleRules.WORM_FARM_HOLDS) {
                return;
            }
            if (++worked >= TackleRules.WORM_TICKS) {
                worked = 0;
                held++;
                setChanged();
            }
        }

        /**
         * Feed it a fish of this family. Returns the bait of the family it was
         * making before, if it was making another.
         */
        public ItemStack feed(String fed) {
            ItemStack left = ItemStack.EMPTY;
            if (!fed.equals(family)) {
                left = takeAll();
                family = fed;
                worked = 0;
            }
            setChanged();
            return left;
        }

        /** Everything it has made, out of it. */
        public ItemStack takeAll() {
            if (held <= 0 || family.isEmpty() || !FishItems.BAITS.containsKey(family)) {
                return ItemStack.EMPTY;
            }
            ItemStack out = new ItemStack(FishItems.BAITS.get(family).get(), held);
            held = 0;
            setChanged();
            return out;
        }

        public Component says() {
            if (family.isEmpty()) {
                return Component.translatable("elysiumagronomy.worm_farm.hungry");
            }
            return Component.translatable("elysiumagronomy.worm_farm.working",
                    Component.translatable("item.elysiumagronomy." + family + "_bait"),
                    String.valueOf(held), String.valueOf(TackleRules.WORM_FARM_HOLDS));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            family = tag.getString("family");
            held = Math.max(0, Math.min(TackleRules.WORM_FARM_HOLDS, tag.getInt("held")));
            worked = Math.max(0, tag.getInt("worked"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putString("family", family);
            tag.putInt("held", held);
            tag.putInt("worked", worked);
        }
    }
}
