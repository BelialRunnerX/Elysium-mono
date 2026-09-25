package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.registry.AgronomyItems;
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
 * A pedestal, and the relay that carries essence in from further out.
 *
 * <p>One class for both because they differ in exactly one thing — how far
 * out they stand — and that is {@link AltarRules}' business rather than this
 * file's. Two classes differing in a constant is two places for the constant
 * to drift.
 *
 * <p>Neither of them does anything on its own. They hold one stack and hand
 * it to the altar in the middle when it asks, which is why there is no
 * ticker here: an altar that is not being used should cost nothing at all.
 */
public class AltarBlock extends BaseEntityBlock {

    public static final MapCodec<AltarBlock> CODEC =
            simpleCodec(properties -> new AltarBlock(properties,
                    AltarRules.PEDESTAL));

    private final String kind;

    public AltarBlock(Properties properties, String kind) {
        super(properties);
        this.kind = kind;
    }

    /** Which of the two this is. */
    public String kind() {
        return kind;
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
        return new HeldBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof HeldBlockEntity stand)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && stand.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HeldBlockEntity stand)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = stand.take();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(
                        Component.translatable("elysiumagronomy.altar.bare"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof HeldBlockEntity stand) {
            Containers.dropContents(level, pos, stand.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** One stack, held where the altar can see it. */
    public static class HeldBlockEntity extends BlockEntity {

        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };

        public HeldBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.ALTAR_STAND.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        public ItemStack held() {
            return items.getStackInSlot(0);
        }

        public boolean offer(ItemStack coming) {
            ItemStack left = items.insertItem(0, coming.copy(), false);
            if (left.getCount() == coming.getCount()) {
                return false;
            }
            coming.setCount(left.getCount());
            return true;
        }

        public ItemStack take() {
            return items.extractItem(0, items.getStackInSlot(0).getCount(), false);
        }

        /** Take a stated number, for the altar when it fires. */
        public ItemStack spend(int many) {
            return items.extractItem(0, many, false);
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(0));
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

    /** What an empty stand looks like to the code that walks an altar. */
    public static ItemStack heldAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HeldBlockEntity stand
                ? stand.held() : ItemStack.EMPTY;
    }

    /** The item id the rules would call what is in this stack. */
    public static String nameOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        net.minecraft.resources.ResourceLocation where =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                        stack.getItem());
        return where.getNamespace().equals(
                com.elysium.agronomy.ElysiumAgronomy.MODID)
                ? where.getPath() : where.toString();
    }

    /** Whether the stack in a stand is what an awakening asks for. */
    public static boolean matches(ItemStack stack, String wanted, int many) {
        return !stack.isEmpty() && stack.getCount() >= many
                && nameOf(stack).equals(wanted);
    }

    /** The item an awakening makes, or an empty stack. */
    public static ItemStack result(AltarRules.Awakening awakening) {
        return AgronomyItems.thing(
                com.elysium.agronomy.ElysiumAgronomy.MODID + ":"
                        + awakening.makes());
    }
}
