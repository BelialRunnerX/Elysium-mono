package com.elysium.solarpunk.power;

import com.elysium.solarpunk.machine.SolarpunkMachineEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything one generator can reach, and in what order it is fed.
 *
 * <h2>Why there has to be a grid at all</h2>
 *
 * Before this, a generator pushed to its six neighbours and machines never
 * relayed, so a machine had to physically touch a generator. That made a
 * priority system meaningless twice over: six slots is not a base, and the only
 * allocation rule was {@link Direction#values()}, which means the block
 * underneath always won. A player could not express "the water comes first"
 * because there was nowhere to say it and nothing to say it about.
 *
 * <h2>The walk</h2>
 *
 * Breadth-first from the generator, through conduit blocks only. Any block
 * beside the generator or beside a conduit that offers an energy capability is
 * a receiver. Conduits hold nothing and have no block entity — a base with a
 * thousand conduits would otherwise be a thousand ticking block entities to
 * carry energy that is already accounted for in the buffers at each end.
 *
 * <h2>What bounds it</h2>
 *
 * <ul>
 *   <li>{@link #MAX_CONDUITS} positions. A grid is a base, not a continent, and
 *       an unbounded flood fill is a server hang waiting for somebody to build
 *       a long enough line.</li>
 *   <li>Unloaded chunks end the walk at their edge. Reading through one loads
 *       it, which turns a generator into a chunk loader by accident — the same
 *       rule the hazard field's lattice scan follows.</li>
 * </ul>
 *
 * <h2>One walk, not three</h2>
 *
 * The walk gathers receivers, conduits and governors together, because they are
 * found by the same traversal and a second traversal to collect governors would
 * double the cost of every generator tick to answer a question the first walk
 * had already passed. {@link Survey} is what comes back.
 *
 * <h2>Why the order is fixed and not sorted</h2>
 *
 * Receivers come out in breadth-first order: by distance from the generator,
 * and within a distance by {@link Direction#values()}. That is arbitrary, but
 * it is <em>stable</em> — the same base gives the same order every tick, so two
 * machines of the same class split a short supply the same way each time rather
 * than flickering. The class ordering is applied afterwards, by
 * {@link PowerBudget}, which is the only place priority is decided.
 */
public final class PowerGrid {

    private PowerGrid() {
    }

    /** How far a single generator's network may extend, in conduit blocks. */
    public static final int MAX_CONDUITS = 512;

    /** How many receivers one generator will feed in a tick. */
    public static final int MAX_RECEIVERS = 64;

    /**
     * How many governors one grid is read for.
     *
     * Bounded like everything else on this walk. Past this the extra rules
     * could only ever hold classes already held — union saturates at five — so
     * the ceiling costs a player nothing they could have noticed.
     */
    public static final int MAX_RULES = 32;

    /**
     * One thing that can be given power.
     *
     * @param pos   where it is, for the readout
     * @param kind  its class, or {@link PowerClass#UNLABELLED} for a machine
     *              from another mod
     * @param store what actually takes the energy
     */
    public record Receiver(BlockPos pos, PowerClass kind, IEnergyStorage store) {
    }

    /**
     * Walk the network from a generator.
     *
     * @param from the generator's own position; it is never a receiver of its
     *             own output, which is checked rather than assumed because a
     *             generator that charges itself is an infinite loop that looks
     *             like a working machine
     */
    /**
     * What one walk found.
     *
     * @param receivers  everything that can take power, in walk order
     * @param rules      every governor's rule, unevaluated — the conditions
     *                   need a {@link GridState} and that needs the receivers,
     *                   so the two cannot be worked out in one pass
     * @param conduits   how much wire, for the readout
     * @param stored     energy currently held across every buffer found
     * @param capacity   what those buffers could hold
     */
    public record Survey(List<Receiver> receivers, List<PowerRule> rules,
                         int conduits, long stored, long capacity) {

        /** How full the grid is, 0..1. Zero capacity reads as empty rather
         *  than as a division by nothing. */
        public float charge() {
            return capacity <= 0 ? 0.0F
                    : Math.min(1.0F, (float) ((double) stored / (double) capacity));
        }

        static final Survey EMPTY = new Survey(List.of(), List.of(), 0, 0L, 0L);
    }

    /** Everything one generator can reach. Convenience over {@link #survey}. */
    public static List<Receiver> receivers(Level level, BlockPos from) {
        return survey(level, from).receivers();
    }

    public static Survey survey(Level level, BlockPos from) {
        List<Receiver> found = new ArrayList<>();
        List<PowerRule> rules = new ArrayList<>();
        long stored = 0L;
        long capacity = 0L;
        if (level == null || level.isClientSide()) {
            return Survey.EMPTY;
        }
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> taken = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        seen.add(from.immutable());
        taken.add(from.immutable());     // never feed the generator itself
        queue.add(from.immutable());
        int conduits = 0;

        while (!queue.isEmpty() && found.size() < MAX_RECEIVERS) {
            BlockPos at = queue.poll();
            for (Direction face : Direction.values()) {
                BlockPos side = at.relative(face);
                if (!seen.add(side.immutable())) {
                    continue;
                }
                if (!level.isLoaded(side)) {
                    continue;
                }
                if (isConduit(level, side)) {
                    if (conduits < MAX_CONDUITS) {
                        conduits++;
                        queue.add(side.immutable());
                    }
                    continue;
                }
                // A governor is read where it is found and is not walked
                // through: it is a note pinned to the wire, not part of it.
                // Making it carry power too would mean a player could not
                // place one without changing where the power goes, which is
                // the opposite of what a rule is for.
                BlockState side_state = level.getBlockState(side);
                if (side_state.getBlock() instanceof PowerGovernorBlock) {
                    if (rules.size() < MAX_RULES) {
                        rules.add(PowerGovernorBlock.ruleOf(side_state));
                    }
                    continue;
                }
                if (!taken.add(side.immutable())) {
                    continue;
                }
                IEnergyStorage store = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, side, face.getOpposite());
                if (store == null || !store.canReceive()) {
                    continue;
                }
                found.add(new Receiver(side.immutable(), classOf(level, side), store));
                stored += Math.max(0, store.getEnergyStored());
                capacity += Math.max(0, store.getMaxEnergyStored());
                if (found.size() >= MAX_RECEIVERS) {
                    break;
                }
            }
        }
        return new Survey(found, rules, conduits, stored, capacity);
    }

    /** How many conduit blocks a generator's network runs over. For the readout. */
    public static int conduitCount(Level level, BlockPos from) {
        if (level == null || level.isClientSide()) {
            return 0;
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(from.immutable());
        queue.add(from.immutable());
        int conduits = 0;
        while (!queue.isEmpty() && conduits < MAX_CONDUITS) {
            BlockPos at = queue.poll();
            for (Direction face : Direction.values()) {
                BlockPos side = at.relative(face);
                if (!seen.add(side.immutable()) || !level.isLoaded(side)) {
                    continue;
                }
                if (isConduit(level, side)) {
                    conduits++;
                    queue.add(side.immutable());
                }
            }
        }
        return conduits;
    }

    private static boolean isConduit(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PowerConduitBlock;
    }

    /**
     * What class a receiver belongs to.
     *
     * An Elysium machine says so itself. Anything else — a battery from another
     * mod, a cable's own buffer — is {@link PowerClass#UNLABELLED}, which is
     * industry. See that field for why it is not last.
     */
    public static PowerClass classOf(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof SolarpunkMachineEntity machine) {
            return machine.powerClass();
        }
        return PowerClass.UNLABELLED;
    }
}
