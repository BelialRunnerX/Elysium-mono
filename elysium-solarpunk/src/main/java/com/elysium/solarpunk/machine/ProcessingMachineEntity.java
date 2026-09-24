package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.recipe.SolarpunkProcessRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * A machine that turns one item into another by spending power on it.
 *
 * <h2>What a subclass is left to decide</h2>
 *
 * Only which recipe book it reads. Everything else — the two slots, the input
 * rule, the out-tray, the stalling behaviour, saving, spilling on break — is
 * the same for every machine of this shape, and a second copy of it is a second
 * place for the subtle parts to be got wrong.
 *
 * The subtle parts, for the record:
 *
 * <ul>
 *   <li>an unpaid tick must not advance progress, or a starved machine works
 *       for free;</li>
 *   <li>a blocked out-tray must not reset progress, or a hopper arriving a
 *       moment late costs the player the run that was nearly done;</li>
 *   <li>the input must be re-checked every tick, because a player can swap it
 *       mid-run and the machine would otherwise finish the old recipe with the
 *       new item.</li>
 * </ul>
 *
 * <h2>Why the input rule is the recipe book itself</h2>
 *
 * The input slot accepts anything that some loaded recipe of this machine's
 * type wants. Not a tag, not a list: the recipes already say what the machine
 * eats, and any second statement of it is one that can disagree. A pack that
 * adds a recipe gets a machine that accepts its input, with nothing else to
 * remember.
 */
public abstract class ProcessingMachineEntity extends SolarpunkMachineEntity {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private final ItemStackHandler slots;

    private int progress;

    protected ProcessingMachineEntity(BlockEntityType<?> type, BlockPos pos,
                                      BlockState state, int capacity, int maxReceive) {
        this(type, pos, state, capacity, maxReceive, 0);
    }

    /**
     * @param extraSlots out-trays beyond the first, for a machine that yields
     *                   more than one thing. Zero for a machine that turns one
     *                   item into one item, which is most of them — the
     *                   aquaponics tank is the exception, and its fish are a
     *                   product of the tank rather than of the recipe.
     */
    protected ProcessingMachineEntity(BlockEntityType<?> type, BlockPos pos,
                                      BlockState state, int capacity,
                                      int maxReceive, int extraSlots) {
        super(type, pos, state, capacity, maxReceive, 0);
        // Built here rather than as a field initialiser.
        //
        // Two earlier versions of this got it wrong in the same way. The first
        // read an overridable extraSlots() from the initialiser, which runs
        // before the subclass constructor; the second read a blank final field
        // assigned in this constructor, which runs *after* the initialiser. In
        // both the handler would have been silently built with two slots and a
        // machine's extra out-tray would simply not exist — no error, no log,
        // just fish that never appear. A local, used immediately, cannot be
        // read before it is written.
        int slotCount = 2 + extraSlots;
        this.slots = new ItemStackHandler(slotCount) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == INPUT_SLOT && level != null && wants(stack);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                // Every slot from the output onward is an out-tray. A hopper
                // pointed at the machine fills the input and can never jam a
                // result back into a tray it was just emptied from.
                return slot >= OUTPUT_SLOT ? stack : super.insertItem(slot, stack, simulate);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    /** Which recipe book this machine reads. */
    protected abstract SolarpunkProcessRecipe.Kind kind();

    /**
     * Whether this machine's surroundings allow it to run at all.
     *
     * True for a machine that only needs power, which is most of them. A
     * machine that needs water or light overrides this — and overriding it is
     * the whole difference between a press, which works in a sealed room, and a
     * tank, which does not.
     *
     * Checked every tick rather than on placement, because the world changes
     * around a machine: a player drains the pond, or builds a roof, and the
     * machine has to notice.
     */
    protected boolean sited() {
        return true;
    }

    /**
     * Called once each time a run finishes, after the result is in the tray.
     *
     * The hook for anything a machine yields that is not its recipe's output.
     * Deliberately not passed the recipe: a side yield that varied per recipe
     * would be a second output column in the recipe format, and the format says
     * what it produces in one place.
     */
    protected void onCycleComplete() {
    }

    public ItemStackHandler getInventory() {
        return slots;
    }

    /** Ticks done on the current run, for a future screen to draw. */
    public int getProgress() {
        return progress;
    }

    /** Whether any loaded recipe for this machine takes this item. */
    private boolean wants(ItemStack stack) {
        return !stack.isEmpty() && recipeFor(stack).isPresent();
    }

    private Optional<RecipeHolder<SolarpunkProcessRecipe>> recipeFor(ItemStack input) {
        if (input.isEmpty() || level == null) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(
                kind().type().get(), new SingleRecipeInput(input), level);
    }

    private boolean roomFor(ItemStack result) {
        ItemStack held = slots.getStackInSlot(OUTPUT_SLOT);
        if (held.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(held, result)
                && held.getCount() + result.getCount() <= held.getMaxStackSize();
    }

    @Override
    public void serverTick() {
        if (!sited()) {
            // Not idle and not blocked: the site is wrong. Progress is kept,
            // because a player who drains a pond and refills it should not have
            // lost the run that was nearly done — the same reasoning as a full
            // out-tray below.
            return;
        }
        ItemStack input = slots.getStackInSlot(INPUT_SLOT);
        Optional<RecipeHolder<SolarpunkProcessRecipe>> found = recipeFor(input);
        if (found.isEmpty()) {
            progress = 0;
            return;
        }
        SolarpunkProcessRecipe recipe = found.get().value();

        // getRecipeFor already checked the ingredient; the count is this
        // machine's own rule and has to be checked separately, or a run starts
        // on two of something that needs four and consumes them at the end.
        if (input.getCount() < recipe.inputCount()) {
            progress = 0;
            return;
        }
        ItemStack result = recipe.assemble(
                new SingleRecipeInput(input), level.registryAccess());
        if (result.isEmpty() || !roomFor(result)) {
            return;                      // blocked, not idle: keep the progress
        }
        int perTick = recipe.energyPerTick();
        if (energy.extractEnergy(perTick, true) < perTick) {
            return;                      // starved: an unpaid tick buys nothing
        }
        energy.extractEnergy(perTick, false);

        if (++progress < recipe.time()) {
            return;
        }
        progress = 0;
        input.shrink(recipe.inputCount());
        ItemStack held = slots.getStackInSlot(OUTPUT_SLOT);
        if (held.isEmpty()) {
            slots.setStackInSlot(OUTPUT_SLOT, result);
        } else {
            held.grow(result.getCount());
        }
        onCycleComplete();
        setChanged();
    }

    @Override
    public NonNullList<ItemStack> drops() {
        NonNullList<ItemStack> spilled = NonNullList.create();
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            ItemStack held = slots.getStackInSlot(slot);
            if (!held.isEmpty()) {
                spilled.add(held);
            }
        }
        return spilled;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slots")) {
            slots.deserializeNBT(registries, tag.getCompound("slots"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
    }
}
