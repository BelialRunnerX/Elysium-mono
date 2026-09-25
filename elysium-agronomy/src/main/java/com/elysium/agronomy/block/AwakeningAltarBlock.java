package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.rules.AltarRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The middle of an altar: what turns four pedestals of essence into one thing.
 *
 * <p>Everything it knows about its own shape comes from {@link
 * AltarRules#AROUND}, walked rather than typed, so an altar that works facing
 * north and not east is not a thing that can happen here — the shape is proved
 * symmetric in the harness and this block reads the same list.
 *
 * <p>And everything it knows about what it can make comes from {@link
 * AltarRules#AWAKENINGS}. Which is thirty-one recipes it has no opinion about:
 * an altar that decided for itself what a seed awakens into would be a second
 * opinion about the ladder, and the ladder is the thing three ages are built on.
 */
public class AwakeningAltarBlock extends BaseEntityBlock {

    public static final MapCodec<AwakeningAltarBlock> CODEC =
            simpleCodec(AwakeningAltarBlock::new);

    public AwakeningAltarBlock(Properties properties) {
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
        return new AltarBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof AltarBlockEntity altar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && altar.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(altar.awaken(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof AltarBlockEntity altar) {
            Containers.dropContents(level, pos, altar.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: one slot, and eight neighbours it reads. */
    public static class AltarBlockEntity extends BlockEntity {

        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };

        public AltarBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.ALTAR.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        public boolean offer(ItemStack coming) {
            ItemStack left = items.insertItem(0, coming.copy(), false);
            if (left.getCount() == coming.getCount()) {
                return false;
            }
            coming.setCount(left.getCount());
            return true;
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(0));
        }

        /**
         * Where every block of the altar should be, in the world.
         *
         * <p>Walked out of the rules. The altar lies flat, so the offsets are
         * east and north and the height never changes — a pedestal one block
         * up is a pedestal the harness never looked at.
         */
        public java.util.List<BlockPos> standsAround(BlockPos centre) {
            java.util.List<BlockPos> made = new java.util.ArrayList<>();
            for (AltarRules.Place place : AltarRules.AROUND) {
                made.add(centre.offset(place.east(), 0, place.north()));
            }
            return made;
        }

        /**
         * Whether the altar is actually built, block for block.
         *
         * <p>Every place the rules name has to hold the kind of stand the
         * rules name there. Half an altar that fires is an altar whose
         * pedestals are decoration.
         */
        public boolean built(Level level, BlockPos centre) {
            for (AltarRules.Place place : AltarRules.AROUND) {
                BlockPos where = centre.offset(place.east(), 0, place.north());
                if (!(level.getBlockState(where).getBlock() instanceof AltarBlock stand)
                        || !stand.kind().equals(place.kind())) {
                    return false;
                }
            }
            return true;
        }

        /** The stands that hold essence, in the order the rules name them. */
        public java.util.List<AltarBlock.HeldBlockEntity> pedestals(
                Level level, BlockPos centre) {
            java.util.List<AltarBlock.HeldBlockEntity> made =
                    new java.util.ArrayList<>();
            for (AltarRules.Place place : AltarRules.AROUND) {
                if (!AltarRules.PEDESTAL.equals(place.kind())) {
                    continue;
                }
                BlockPos where = centre.offset(place.east(), 0, place.north());
                if (level.getBlockEntity(where)
                        instanceof AltarBlock.HeldBlockEntity stand) {
                    made.add(stand);
                }
            }
            return made;
        }

        /**
         * Which awakening this altar is set up for, or null.
         *
         * <p>By what is standing on it against the roster, never by guessing.
         * An altar that guessed would make something out of an arrangement
         * nobody designed.
         */
        @Nullable
        public AltarRules.Awakening ready(Level level, BlockPos centre) {
            if (!built(level, centre)) {
                return null;
            }
            java.util.List<AltarBlock.HeldBlockEntity> stands =
                    pedestals(level, centre);
            if (stands.size() != AltarRules.pedestals()) {
                return null;
            }
            String middle = AltarBlock.nameOf(items.getStackInSlot(0));
            for (AltarRules.Awakening one : AltarRules.AWAKENINGS) {
                if (!one.takes().equals(middle)) {
                    continue;
                }
                boolean fed = true;
                for (AltarBlock.HeldBlockEntity stand : stands) {
                    fed &= AltarBlock.matches(stand.held(), one.essence(),
                            one.each());
                }
                if (fed) {
                    return one;
                }
            }
            return null;
        }

        /** Fire it, if it is ready, and say what happened either way. */
        public Component awaken(Level level, BlockPos centre) {
            if (!built(level, centre)) {
                return Component.translatable("elysiumagronomy.altar.unbuilt",
                        String.valueOf(AltarRules.pedestals()),
                        String.valueOf(AltarRules.relays()));
            }
            AltarRules.Awakening one = ready(level, centre);
            if (one == null) {
                return Component.translatable("elysiumagronomy.altar.notready");
            }
            ItemStack made = AltarBlock.result(one);
            if (made.isEmpty()) {
                return Component.translatable("elysiumagronomy.altar.notready");
            }
            for (AltarBlock.HeldBlockEntity stand : pedestals(level, centre)) {
                stand.spend(one.each());
            }
            items.extractItem(0, 1, false);
            items.setStackInSlot(0, made);
            setChanged();
            return Component.translatable("elysiumagronomy.altar.awoke",
                    made.getHoverName());
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
        }
    }
}
