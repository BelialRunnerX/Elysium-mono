package com.elysium.core.block;

import com.elysium.core.Elysium;
import com.elysium.core.menu.ReforgeTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Backing block entity for all three Elysium workstations.
 *
 * It owns the three working slots so their contents survive closing the screen,
 * reloading the chunk and restarting the game - the old menu kept its handler
 * in the menu itself, which meant anything left in the table vanished the
 * moment the screen closed.
 */
public class ReforgeTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_COUNT = 3;
    public static final int SLOT_ARMOR = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int SLOT_RUNE = 2;

    private static final String ITEMS_KEY = "Items";

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public ReforgeTableBlockEntity(BlockPos pos, BlockState state) {
        super(Elysium.REFORGE_TABLE_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReforgeTableMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_KEY, items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ITEMS_KEY)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_KEY));
        }
    }
}
