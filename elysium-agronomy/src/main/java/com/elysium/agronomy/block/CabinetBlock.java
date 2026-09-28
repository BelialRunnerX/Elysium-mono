package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.rules.KitchenRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * A kitchen cabinet: a chest with a door on it, in every wood.
 *
 * <p>Eleven cabinets were plain blocks, which is eleven blocks that looked
 * like somewhere to put things and were not. A cabinet now holds
 * {@link KitchenRules#cabinetSlots()} stacks and opens the game's own
 * three-row chest menu, so there is no screen of ours to theme, and a hopper
 * reaches it the way it reaches a barrel.
 */
public class CabinetBlock extends BaseEntityBlock {

    public static final MapCodec<CabinetBlock> CODEC = simpleCodec(CabinetBlock::new);

    public CabinetBlock(Properties properties) {
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
        return new CabinetBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
            ElysiumVoices.click(level, pos, "station");
            player.openMenu(cabinet);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
            Containers.dropContents(level, pos, cabinet);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** What is in it: three rows of nine, and the game's chest menu to see them. */
    public static class CabinetBlockEntity extends BaseContainerBlockEntity {

        private NonNullList<ItemStack> items =
                NonNullList.withSize(KitchenRules.cabinetSlots(), ItemStack.EMPTY);

        public CabinetBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.CABINET.get(), pos, state);
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("container.elysiumagronomy.cabinet");
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return items;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> replacement) {
            items = replacement;
        }

        @Override
        public int getContainerSize() {
            return KitchenRules.cabinetSlots();
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return com.elysium.lib.menu.ElysiumChests.threeRows(id, inventory, this);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ContainerHelper.saveAllItems(tag, items, registries);
        }
    }
}
