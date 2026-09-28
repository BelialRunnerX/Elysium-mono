package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.PowerClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * Cuts and replants the field around it, and posts the crop downward.
 *
 * <h2>What it is for</h2>
 *
 * Harvesting a field by hand is the one farming action that is pure repetition:
 * the decisions — what to plant, where, how much water and light — are all made
 * before the crop is ripe, and breaking ninety blocks afterwards adds nothing to
 * any of them. This compresses that and leaves the decisions alone, which is
 * what the rest of the industrial layer does to smelting and pressing.
 *
 * <h2>Replanting costs a seed from the harvest</h2>
 *
 * Not from a hopper, not from thin air: one of the seeds it just cut goes back
 * into the ground and the surplus is what the player keeps.
 *
 * Setting the crop back to age zero without paying for it would have been one
 * line shorter and would have made this a seed duplicator — every wheat block
 * yielding its seeds *and* regrowing free. Paying from the harvest keeps the
 * machine's arithmetic exactly the arithmetic of farming by hand, which is the
 * property that lets it be an early machine rather than a late one.
 *
 * <h2>Nothing is ever voided</h2>
 *
 * The harvest goes into an internal tray, the tray is pushed into whatever sits
 * below, and when the tray is full the machine stops cutting. It never breaks a
 * crop it has nowhere to put.
 *
 * That ordering is the whole of it. A harvester that cut first and looked for
 * space afterwards would quietly destroy a player's field the first time their
 * chest filled up while they were away — which is the kind of loss that is
 * discovered days later and cannot be undone.
 */
public class HarvesterEntity extends SolarpunkMachineEntity {

    public static final int CAPACITY = 16_000;
    public static final int MAX_RECEIVE = 200;

    /**
     * How far it reaches, in blocks, on its own level.
     *
     * Four, which is a nine-by-nine field minus the block the machine stands
     * in. Big enough that one machine is worth building and small enough that
     * a player can see the whole of what it owns while standing next to it —
     * a machine whose effect extends past what you can look at is one you
     * cannot reason about.
     */
    public static final int RADIUS = 4;

    /** Energy spent cutting and replanting one block. */
    public static final int COST_PER_HARVEST = 60;

    /** How many columns it examines each tick. */
    public static final int SCAN_PER_TICK = 8;

    /** Slots in the tray. */
    public static final int TRAY_SLOTS = 9;

    private final ItemStackHandler tray = new ItemStackHandler(TRAY_SLOTS) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // An out-tray: the machine fills it and the world empties it. A
            // hopper feeding seeds in would look like it was supplying the
            // replant and would in fact be filling the space the harvest needs.
            return stack;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /**
     * Where in the field the next scan starts.
     *
     * Kept between ticks so the machine sweeps rather than restarting at the
     * same corner every time. Without it, a field whose first column ripens
     * late would be examined thousands of times before anything further out was
     * looked at once.
     */
    private int cursor;

    public HarvesterEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.HARVESTER_ENTITY.get(), pos, state,
                CAPACITY, MAX_RECEIVE, 0);
    }

    public ItemStackHandler getInventory() {
        return tray;
    }

    /** The number of columns in the square this machine owns. */
    public static int fieldSize() {
        int side = RADIUS * 2 + 1;
        return side * side;
    }

    /**
     * The offset of the nth column of the field, in scan order.
     *
     * A plain row-major walk of the square. Exposed and pure so the harness can
     * check that the sweep visits every column exactly once and never steps
     * outside the radius — an off-by-one here is a machine that ignores one
     * edge of its own field, which looks like a crop that will not grow.
     */
    public static BlockPos offsetFor(int index) {
        int side = RADIUS * 2 + 1;
        int wrapped = Math.floorMod(index, side * side);
        return new BlockPos(wrapped % side - RADIUS, 0, wrapped / side - RADIUS);
    }

    /**
     * Logistics. It moves goods rather than making them, and a field that goes
     * uncut for an afternoon is a delay; a kiln that goes cold mid-firing is
     * not much worse, but the field can wait and the base's water cannot.
     */
    @Override
    public PowerClass powerClass() {
        return PowerClass.LOGISTICS;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        SolarpunkOutput.pushDown(level, worldPosition, tray);

        if (SolarpunkOutput.isFull(tray)) {
            return;                     // nowhere to put a harvest; do not cut
        }
        if (!energy.canSpend(COST_PER_HARVEST)) {
            return;
        }
        for (int step = 0; step < SCAN_PER_TICK; step++) {
            BlockPos target = worldPosition.offset(offsetFor(cursor++));
            if (target.equals(worldPosition)) {
                continue;
            }
            if (harvest(server, target)) {
                energy.spend(COST_PER_HARVEST);
                ElysiumVoices.hum(level, worldPosition, "shaft");
                return;                 // one block a tick, so the field ripples
            }
        }
    }

    /**
     * Cut one block if it is ripe, and put it back in the ground.
     *
     * @return whether anything was harvested
     */
    private boolean harvest(ServerLevel server, BlockPos target) {
        BlockState state = server.getBlockState(target);
        if (!isRipe(state)) {
            return false;
        }
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, server, target, null);
        if (!SolarpunkOutput.fits(tray, drops)) {
            return false;               // leave it standing until there is room
        }

        BlockState replanted = replantedState(state);
        server.setBlockAndUpdate(target, replanted);

        boolean seedPaid = false;
        for (ItemStack drop : drops) {
            if (!seedPaid && paysForReplant(drop, replanted)) {
                drop.shrink(1);
                seedPaid = true;
            }
            if (!drop.isEmpty()) {
                SolarpunkOutput.store(tray, drop);
            }
        }
        setChanged();
        return true;
    }

    /** Whether this block is a crop at its final growth stage. */
    public static boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        return false;
    }

    /** The same crop, back at age zero. */
    private static BlockState replantedState(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            IntegerProperty age = crop.getStateDefinition()
                    .getProperty("age") instanceof IntegerProperty property
                    ? property : CropBlock.AGE;
            return state.setValue(age, 0);
        }
        return state.setValue(NetherWartBlock.AGE, 0);
    }

    /**
     * Whether this dropped stack is the seed that was put back.
     *
     * Matched against what the replanted block itself drops, rather than
     * against a table of crops and their seeds. A modded crop nobody here has
     * heard of pays for its own replant correctly, and no list has to be kept
     * in step with the game's contents.
     */
    private boolean paysForReplant(ItemStack drop, BlockState replanted) {
        if (drop.isEmpty() || !(level instanceof ServerLevel server)) {
            return false;
        }
        ItemStack seed = new ItemStack(replanted.getBlock().asItem());
        if (!seed.isEmpty()) {
            return ItemStack.isSameItem(drop, seed);
        }
        // A crop whose block has no item form — its seed is whatever a
        // freshly-planted one drops.
        for (ItemStack fresh : net.minecraft.world.level.block.Block.getDrops(
                replanted, server, worldPosition, null)) {
            if (ItemStack.isSameItem(drop, fresh)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------

    @Override
    public NonNullList<ItemStack> drops() {
        return SolarpunkOutput.spill(tray);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tray")) {
            tray.deserializeNBT(registries, tag.getCompound("tray"));
        }
        cursor = tag.getInt("cursor");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tray", tray.serializeNBT(registries));
        tag.putInt("cursor", cursor);
    }
}
