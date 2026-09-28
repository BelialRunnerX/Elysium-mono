package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.SolarpunkContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * Turns water and skylight into biomass fuel.
 *
 * The other half of the loop: a condenser in the sun charges this, this grows
 * something a furnace will burn, and the pair of them together are a renewable
 * fuel supply that needs no coal and no wood. Neither is useful alone, which is
 * why these two graduated out of {@code block/pending/} together.
 *
 * <h2>The water is a supply, not an ingredient</h2>
 *
 * It wants a water block touching it and does not consume one. An algae pond is
 * a thing you build beside water, not a thing you feed buckets to, and making it
 * eat buckets would turn "put it by the lake" into an item-logistics problem for
 * no gain. It has to keep touching water: drain the pond and it stops.
 *
 * <h2>Skylight, again, and why it is the same rule</h2>
 *
 * It needs to see the sky itself, exactly as the condenser does. It would be
 * simpler to let it run anywhere as long as something powered it — but the
 * promise in {@code MACHINES.md} says water <em>and skylight</em>, and a
 * bioreactor happily growing algae at the bottom of a mineshaft on imported
 * electricity is not what that describes.
 */
public class AlgaeBioreactorEntity extends SolarpunkMachineEntity {

    public static final int CAPACITY = 16_000;

    /** Drawn per tick while growing. */
    public static final int DRAW = 12;

    /** Ticks of drawn power one briquette costs. Twenty seconds at full draw. */
    public static final int GROW_TICKS = 400;

    /** How many briquettes it will hold before it stops. */
    public static final int STOCK = 16;

    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        /**
         * Nothing goes in. The slot is an out-tray.
         *
         * Without this a hopper feeding the machine fills its only slot with
         * cobblestone and the bioreactor stops forever, which looks exactly
         * like a bug in the machine rather than a consequence of the pipe.
         */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }
    };

    private int progress;

    public AlgaeBioreactorEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.ALGAE_BIOREACTOR_ENTITY.get(), pos, state,
                CAPACITY, DRAW * 20, 0);
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    /** Whether the site is right, given the two facts the promise names. */
    public static boolean sited(boolean seesSky, boolean touchingWater) {
        return seesSky && touchingWater;
    }

    /** See {@link SolarpunkWater}, which the aquaponics tank shares. */
    private boolean touchingWater() {
        return SolarpunkWater.touching(level, worldPosition);
    }

    @Override
    public void serverTick() {
        ItemStack held = output.getStackInSlot(0);
        if (held.getCount() >= STOCK) {
            return;                 // the tray is full; stop rather than spill
        }
        if (!sited(level.canSeeSky(worldPosition.above()), touchingWater())) {
            // Losing the site loses the part-grown batch. A machine that banked
            // progress through being drained and rebuilt would reward moving
            // water around rather than siting the thing properly.
            progress = 0;
            return;
        }
        if (!energy.canSpend(DRAW)) {
            return;
        }
        energy.spend(DRAW);
        ElysiumVoices.hum(level, worldPosition, "pump");

        if (++progress < GROW_TICKS) {
            return;
        }
        progress = 0;
        if (held.isEmpty()) {
            output.setStackInSlot(0,
                    new ItemStack(SolarpunkContent.BIOMASS_BRIQUETTE.get()));
        } else {
            held.grow(1);
            output.setStackInSlot(0, held);
        }
        setChanged();
    }

    @Override
    public NonNullList<ItemStack> drops() {
        NonNullList<ItemStack> spilled = NonNullList.create();
        if (!output.getStackInSlot(0).isEmpty()) {
            spilled.add(output.getStackInSlot(0));
        }
        return spilled;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("output")) {
            output.deserializeNBT(registries, tag.getCompound("output"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", output.serializeNBT(registries));
    }
}
