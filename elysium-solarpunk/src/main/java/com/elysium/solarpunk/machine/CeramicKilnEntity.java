package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.ElysiumSolarpunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * Fires clay and glass on less fuel than a furnace needs.
 *
 * <h2>Why it is a specialist and not a better furnace</h2>
 *
 * It only takes what is in {@code #elysiumsolarpunk:kiln_inputs} — vanilla's own
 * "blocks" smelting category, which is to say the recipes Mojang filed as firing
 * rather than as cooking or ore-processing. No ore, no food.
 *
 * That restriction is the whole design. A machine that smelts everything for
 * less fuel is simply the furnace, later, and the furnace stops being a block
 * anybody builds. A machine that is twice as good at terracotta and cannot
 * touch iron is a second tool rather than a replacement, and a base ends up
 * with both — which is the outcome worth designing for.
 *
 * <h2>Why it does not carry its own recipes</h2>
 *
 * It looks up the vanilla smelting recipe for whatever it is given and uses that
 * result and that experience. Shipping a parallel set of kiln recipes would mean
 * every mod that adds a smeltable block has to also add a kiln recipe before the
 * kiln sees it, which is the kind of integration work nobody does. This way a
 * pack widens the kiln by widening one tag.
 *
 * <h2>Fuel or power, and why both</h2>
 *
 * A player meets this machine before they have a generator — it is a furnace
 * upgrade, and furnaces are early. So it burns fuel, and the fuel it most wants
 * is the bioreactor's own briquette, which closes the loop the rest of this set
 * is building: sun, power, algae, briquette, fired brick.
 *
 * And it takes power, so that later, when there are condensers on the roof, the
 * same block stops needing to be fed. One machine that changes how it is
 * supplied is better than two machines that do the same job — the second one
 * would obsolete the first, and obsoleting a block a player built is a bad
 * thing to do to them.
 *
 * Power is spent first when both are available, because energy is the thing
 * that goes to waste if unused: a condenser with a full buffer at noon is
 * throwing away everything it makes, and a briquette in a slot is not.
 */
public class CeramicKilnEntity extends SolarpunkMachineEntity {

    /** Everything the kiln will fire. Widened by a data pack, not by code. */
    public static final TagKey<Item> INPUTS = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ElysiumSolarpunk.MODID,
                    "kiln_inputs"));

    public static final int CAPACITY = 16_000;

    /** Ticks of firing per item. A furnace takes 200. */
    public static final int COOK_TICKS = 160;

    /**
     * Energy per tick of firing, when running on power.
     *
     * Set against the briquette rather than chosen: at 6 the biomass route was
     * four times better than spending the same power directly, which made the
     * power input decoration — nobody wires up a machine to use it four times
     * worse. At 3 the detour through the bioreactor is worth roughly double,
     * which is a reason to build the chain rather than an obligation.
     * MachineHarness holds that ratio inside a band in both directions.
     */
    public static final int DRAW = 3;

    /**
     * Ticks of firing bought by one tick of a fuel's burn time.
     *
     * This is the "less fuel than a furnace needs" of the promise, as a number.
     * A furnace spends {@link AbstractFurnaceBlockEntity#BURN_TIME_STANDARD}
     * ticks of burn per item; the kiln spends {@link #COOK_TICKS} divided by
     * this. Both halves matter and neither is visible in the other, which is
     * why MachineHarness derives the ratio rather than trusting either.
     */
    public static final int FUEL_EFFICIENCY = 2;

    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;

    /**
     * Three slots, each with its own rule about what may be put in it.
     *
     * The output refuses insertion outright: it is an out-tray, so a hopper
     * pointed at the kiln fills the input and never jams the results back in.
     */
    private final ItemStackHandler slots = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case INPUT_SLOT -> stack.is(INPUTS);
                case FUEL_SLOT -> stack.getBurnTime(RecipeType.SMELTING) > 0;
                default -> false;
            };
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == OUTPUT_SLOT ? stack : super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Ticks of firing done on the item currently in the input slot. */
    private int progress;

    /** Ticks of firing still paid for by the fuel that is already burning. */
    private int burning;

    /** Experience owed, accumulated across items because recipes pay fractions. */
    private float owedExperience;

    public CeramicKilnEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.CERAMIC_KILN_ENTITY.get(), pos, state,
                CAPACITY, DRAW * 20, 0);
    }

    public ItemStackHandler getInventory() {
        return slots;
    }

    /**
     * How many items one fuel item is worth here, against a furnace's count.
     *
     * Pure arithmetic, exposed so the harness can check the promise rather than
     * re-derive it from constants it would have to keep in step by hand.
     */
    public static int itemsPerFuel(int burnTime) {
        return burnTime * FUEL_EFFICIENCY / COOK_TICKS;
    }

    /** What a furnace gets out of the same fuel. */
    public static int furnaceItemsPerFuel(int burnTime) {
        return burnTime / AbstractFurnaceBlockEntity.BURN_TIME_STANDARD;
    }

    private Optional<RecipeHolder<SmeltingRecipe>> recipeFor(ItemStack input) {
        if (input.isEmpty() || !input.is(INPUTS)) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    /** Whether the result of this recipe will fit in the out-tray. */
    private boolean roomFor(ItemStack result) {
        ItemStack held = slots.getStackInSlot(OUTPUT_SLOT);
        if (held.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(held, result)
                && held.getCount() + result.getCount() <= held.getMaxStackSize();
    }

    /**
     * Pay for one tick of firing, from power first and then from fuel.
     *
     * Returns false when neither is available, which stalls rather than
     * wasting: an unpaid tick must not advance progress, or the kiln fires for
     * free whenever it is starved.
     */
    private boolean payForATick() {
        if (energy.extractEnergy(DRAW, true) >= DRAW) {
            energy.extractEnergy(DRAW, false);
            return true;
        }
        if (burning > 0) {
            burning--;
            return true;
        }
        ItemStack fuel = slots.getStackInSlot(FUEL_SLOT);
        int burnTime = fuel.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) {
            return false;
        }
        // Light it. The fuel is spent now, in full, exactly as a furnace spends
        // it — a kiln that gave change would let a player tap one coal for one
        // item at a time and never lose the remainder.
        burning = burnTime * FUEL_EFFICIENCY - 1;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !remainder.isEmpty()) {
            slots.setStackInSlot(FUEL_SLOT, remainder);
        }
        setChanged();
        return true;
    }

    @Override
    public void serverTick() {
        ItemStack input = slots.getStackInSlot(INPUT_SLOT);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = recipeFor(input);
        if (recipe.isEmpty()) {
            progress = 0;
            return;
        }
        ItemStack result = recipe.get().value()
                .assemble(new SingleRecipeInput(input), level.registryAccess());
        if (result.isEmpty() || !roomFor(result)) {
            // Blocked rather than idle. Do not reset progress: a full out-tray
            // that a hopper empties a moment later should not have cost the
            // player the firing that was nearly done.
            return;
        }
        if (!payForATick()) {
            return;
        }
        if (++progress < COOK_TICKS) {
            return;
        }
        progress = 0;
        input.shrink(1);
        ItemStack held = slots.getStackInSlot(OUTPUT_SLOT);
        if (held.isEmpty()) {
            slots.setStackInSlot(OUTPUT_SLOT, result);
        } else {
            held.grow(result.getCount());
        }
        owedExperience += recipe.get().value().getExperience();
        payExperience();
        setChanged();
    }

    /**
     * Hand over whole experience points as they are earned.
     *
     * Vanilla banks fractional experience in the furnace and pays it when a
     * player takes the results out. That needs a screen and a player to hand it
     * to; this machine has neither, and a kiln that silently ate the experience
     * a furnace would have given is a downgrade nothing mentions. So it drops
     * orbs at the block as it goes, and keeps the fraction for next time.
     */
    private void payExperience() {
        int whole = (int) owedExperience;
        if (whole <= 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        owedExperience -= whole;
        ExperienceOrb.award(server, Vec3.atCenterOf(worldPosition.above()), whole);
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
        // Burn time is saved and progress is not, for the same reason the base
        // class saves energy and not progress: a lit fuel is a thing the player
        // spent, and losing it on a chunk unload would be a machine eating coal.
        burning = tag.getInt("burning");
        owedExperience = tag.getFloat("experience");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slots", slots.serializeNBT(registries));
        tag.putInt("burning", burning);
        tag.putFloat("experience", owedExperience);
    }
}
