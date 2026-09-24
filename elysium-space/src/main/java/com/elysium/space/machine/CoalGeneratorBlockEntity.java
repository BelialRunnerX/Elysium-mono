package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Burns anything a furnace burns for {@link MachineRules#COAL_FE_PER_TICK} FE a tick. */
public class CoalGeneratorBlockEntity extends MachineBlockEntity {

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(RecipeType.SMELTING) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int burnLeft;

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceBlockEntities.COAL_GENERATOR.get(), pos, state,
                new MachineEnergy(MachineRules.GENERATOR_BUFFER, 0, MachineRules.PUSH_PER_TICK), List.of(), List.of());
    }

    public ItemStackHandler items() {
        return fuel;
    }

    @Override
    public net.neoforged.neoforge.items.IItemHandler slots() {
        return fuel;
    }

    @Override
    public void serverTick() {
        if (burnLeft <= 0 && !energy.isFull()) {
            ItemStack stack = fuel.getStackInSlot(0);
            int time = stack.getBurnTime(RecipeType.SMELTING);
            if (time > 0) {
                ItemStack remainder = stack.getCraftingRemainingItem();
                fuel.extractItem(0, 1, false);
                if (!remainder.isEmpty() && fuel.getStackInSlot(0).isEmpty()) {
                    fuel.setStackInSlot(0, remainder);
                }
                burnLeft = time;
            }
        }
        if (burnLeft > 0 && !energy.isFull()) {
            energy.produce(MachineRules.COAL_FE_PER_TICK);
            burnLeft--;
            setChanged();
        }
        pushEnergy(MachineRules.PUSH_PER_TICK);
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.generator", energy.getEnergyStored(),
                energy.getMaxEnergyStored(), burnLeft > 0 ? Component.translatable("elysiumspace.machine.running")
                        : Component.translatable("elysiumspace.machine.idle"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.putInt("Burn", burnLeft);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        burnLeft = tag.getInt("Burn");
    }
}
