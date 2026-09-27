package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.PowerClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fells grown trees around it, replants, and posts the timber downward.
 *
 * <h2>Why this is a separate machine from the crop harvester</h2>
 *
 * A crop is one block that is either ripe or not. A tree is an irregular solid
 * of a hundred blocks that has to be found, bounded, judged and taken whole,
 * and the judging is the hard part: the difference between a tree and a
 * player's log cabin is not visible from any single block.
 *
 * Sharing a class would have meant one method with a flag in it and two sets of
 * rules interleaved. They share {@link SolarpunkOutput}, which is the part
 * where being wrong destroys property, and nothing else.
 *
 * <h2>How it knows a tree from a house</h2>
 *
 * Three things have to be true at once, and a log wall fails all three:
 *
 * <ul>
 *   <li>the trunk's base sits on soil — dirt, grass, podzol, whatever a sapling
 *       will grow on;</li>
 *   <li>the connected log mass carries leaves — {@link #LEAVES_REQUIRED} of
 *       them, touching the logs;</li>
 *   <li>the whole mass fits inside {@link #MAX_TREE}, so a log warehouse is too
 *       big to be mistaken for a tree and is left alone.</li>
 * </ul>
 *
 * None of those is sufficient alone. Together they are the difference between a
 * machine a player puts in a forest and a machine that eats their house — and
 * that is not a bug anybody gets to fix afterwards, so the test is deliberately
 * strict and deliberately refuses anything ambiguous.
 *
 * <h2>Why the flood fill is bounded</h2>
 *
 * An unbounded walk over connected logs is a server freeze waiting for a modded
 * redwood, and a grief vector on any large wooden build. The cap is checked
 * during the walk rather than after it, so the expensive case costs the cap and
 * not the structure.
 */
public class TreeHarvesterEntity extends SolarpunkMachineEntity {

    public static final int CAPACITY = 16_000;
    public static final int MAX_RECEIVE = 200;

    /**
     * How far it reaches, in blocks, on its own level.
     *
     * Smaller than the crop harvester's, because a tree is not one block: a
     * radius of four here is already up to eighty trees, which is a forest
     * rather than a field.
     */
    public static final int RADIUS = 3;

    /** Energy for felling one tree, whatever its size. */
    public static final int COST_PER_TREE = 400;

    /** Columns examined each tick. */
    public static final int SCAN_PER_TICK = 4;

    public static final int TRAY_SLOTS = 12;

    /**
     * The largest connected log mass this will take.
     *
     * A dark oak runs to about a hundred logs and a large jungle tree to two
     * hundred. Five hundred is comfortably above both and far below anything a
     * player builds out of logs on purpose.
     */
    public static final int MAX_TREE = 512;

    /** How many leaves have to be touching the logs for it to be a tree. */
    public static final int LEAVES_REQUIRED = 6;

    private final ItemStackHandler tray = new ItemStackHandler(TRAY_SLOTS) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;               // an out-tray; see SolarpunkOutput
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int cursor;

    public TreeHarvesterEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.TREE_HARVESTER_ENTITY.get(), pos, state,
                CAPACITY, MAX_RECEIVE, 0);
    }

    public ItemStackHandler getInventory() {
        return tray;
    }

    public static int fieldSize() {
        int side = RADIUS * 2 + 1;
        return side * side;
    }

    /** The offset of the nth column, in scan order. Pure, so it can be checked. */
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
            return;
        }
        if (!energy.canSpend(COST_PER_TREE)) {
            return;
        }
        for (int step = 0; step < SCAN_PER_TICK; step++) {
            BlockPos base = worldPosition.offset(offsetFor(cursor++));
            if (base.equals(worldPosition)) {
                continue;
            }
            if (fell(server, base)) {
                energy.spend(COST_PER_TREE);
                return;                 // one tree a tick at most
            }
        }
    }

    /**
     * Take the whole tree rooted here, if there is one.
     *
     * @return whether anything was felled
     */
    private boolean fell(ServerLevel server, BlockPos base) {
        if (!isTrunkBase(server, base)) {
            return false;
        }
        Set<BlockPos> logs = connectedLogs(server, base);
        if (logs.isEmpty() || !hasCanopy(server, logs)) {
            return false;               // a log wall, or a stack of stripped logs
        }

        // Every drop from every log, gathered before anything is broken.
        // The canopy comes down with the trunk: leaves are where saplings come
        // from, and a machine that took only logs never had one to replant with
        // (the mechanics survey, 27 September 2026).
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (Direction face : Direction.values()) {
                BlockPos next = log.relative(face);
                if (server.getBlockState(next).is(BlockTags.LEAVES)) {
                    leaves.add(next.immutable());
                }
            }
        }
        List<ItemStack> harvest = new ArrayList<>();
        for (BlockPos log : logs) {
            harvest.addAll(Block.getDrops(server.getBlockState(log), server, log, null));
        }
        for (BlockPos leaf : leaves) {
            harvest.addAll(Block.getDrops(server.getBlockState(leaf), server, leaf, null));
        }
        if (!SolarpunkOutput.fits(tray, harvest)) {
            return false;               // leave the tree standing until there is room
        }

        BlockState baseState = server.getBlockState(base);
        for (BlockPos log : logs) {
            // Air rather than destroyBlock: the drops are already accounted
            // for, and destroyBlock would scatter a second copy on the ground.
            server.setBlock(log, net.minecraft.world.level.block.Blocks.AIR
                    .defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos leaf : leaves) {
            server.setBlock(leaf, net.minecraft.world.level.block.Blocks.AIR
                    .defaultBlockState(), Block.UPDATE_ALL);
        }
        for (ItemStack drop : harvest) {
            SolarpunkOutput.store(tray, drop);
        }

        replant(server, base, baseState);
        setChanged();
        return true;
    }

    /** A log standing on ground a sapling would grow in. */
    private static boolean isTrunkBase(ServerLevel server, BlockPos pos) {
        if (!server.getBlockState(pos).is(BlockTags.LOGS)) {
            return false;
        }
        BlockState under = server.getBlockState(pos.below());
        return under.is(BlockTags.DIRT) || under.is(BlockTags.SAND)
                || under.is(net.minecraft.world.level.block.Blocks.FARMLAND);
    }

    /**
     * Every log connected to this one, including diagonally.
     *
     * Diagonal because real trees branch that way — an orthogonal-only walk
     * leaves the canopy of an oak behind as floating logs. Bounded at
     * {@link #MAX_TREE} during the walk, and an empty result means the mass was
     * too big to be a tree.
     */
    private static Set<BlockPos> connectedLogs(ServerLevel server, BlockPos base) {
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        pending.add(base);
        found.add(base);

        while (!pending.isEmpty()) {
            BlockPos at = pending.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = at.offset(dx, dy, dz);
                        if (next.getY() < base.getY()) {
                            continue;   // roots and neighbours below the base
                        }
                        if (found.contains(next)
                                || !server.getBlockState(next).is(BlockTags.LOGS)) {
                            continue;
                        }
                        if (found.size() >= MAX_TREE) {
                            // Too big to be a tree. Refuse the whole thing
                            // rather than take part of it: half a felled
                            // warehouse is worse than none.
                            return Set.of();
                        }
                        found.add(next);
                        pending.add(next);
                    }
                }
            }
        }
        return found;
    }

    /** Whether enough leaves touch this log mass for it to be a grown tree. */
    private static boolean hasCanopy(ServerLevel server, Set<BlockPos> logs) {
        int leaves = 0;
        for (BlockPos log : logs) {
            for (Direction face : Direction.values()) {
                if (server.getBlockState(log.relative(face)).is(BlockTags.LEAVES)) {
                    if (++leaves >= LEAVES_REQUIRED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Put a sapling back where the trunk was.
     *
     * Paid for out of the harvest, and failing that out of stock a previous
     * tree left in the tray. The second half matters: leaves only sometimes
     * drop a sapling, so a machine that could only replant from *this* tree
     * would slowly clear the forest it was put in.
     *
     * When neither has one, the ground is left bare. That is the honest
     * outcome — inventing a sapling would make this a sapling generator, and
     * the next tree along will fund the replant.
     */
    private void replant(ServerLevel server, BlockPos base, BlockState wasLog) {
        BlockState sapling = saplingFor(server, base);
        if (sapling == null) {
            return;
        }
        if (!payForSapling(sapling)) {
            return;
        }
        server.setBlockAndUpdate(base, sapling);
    }

    /**
     * The sapling this tray can plant, or null when it holds none.
     *
     * Read from what the machine is actually holding rather than from a table
     * of logs and their saplings. A modded tree replants correctly with nothing
     * here knowing it exists, and a machine that harvested oak and birch plants
     * whichever it has.
     */
    private BlockState saplingFor(ServerLevel server, BlockPos base) {
        for (int slot = 0; slot < tray.getSlots(); slot++) {
            ItemStack held = tray.getStackInSlot(slot);
            if (held.isEmpty() || !(held.getItem() instanceof BlockItem item)) {
                continue;
            }
            BlockState state = item.getBlock().defaultBlockState();
            if (state.is(BlockTags.SAPLINGS) && state.canSurvive(server, base)) {
                return state;
            }
        }
        return null;
    }

    private boolean payForSapling(BlockState sapling) {
        return SolarpunkOutput.takeOne(tray, stack ->
                stack.getItem() instanceof BlockItem item
                        && item.getBlock() == sapling.getBlock());
    }

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
