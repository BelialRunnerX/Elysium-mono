package com.elysium.logistics.block;

import com.elysium.logistics.rules.LogisticRules;
import com.elysium.logistics.rules.NetRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finding a network: which drawers a controller can reach, along bus.
 *
 * <h2>The third traversal in this pack, and deliberately the same shape</h2>
 *
 * Age I walks a line of shafts. Age II walks a grid of wire. This walks a run
 * of bus. All three are breadth first from one block, all three stop at a
 * stated limit rather than truncating, and all three put every rule about who
 * connects to whom in one file instead of spreading it across the blocks.
 *
 * <p>That sameness is worth more than it looks. A player who has understood
 * one of them has understood all three, and a contributor adding a fourth
 * knows what it should look like before they start.
 *
 * <h2>Why the limit is a wall and not a slowdown</h2>
 *
 * {@link LogisticRules#MOST_DEVICES} is the one place in this pack that
 * prefers a hard stop to a gradient. "Your network is full, build another
 * controller" is a sentence a player can act on; "everything is mysteriously
 * slower than it was" is not, and a storage network is exactly the thing a
 * player will not think to blame.
 */
public final class LogisticNetwork {

    private LogisticNetwork() {
    }

    /**
     * What a walk found: the drawers, the devices, what the devices pay over
     * a slot, and whether there were too many of both together.
     */
    public record Found(List<BlockPos> drawers, List<BlockPos> devices, int surcharge,
                        boolean overloaded) {

        /** A walk that found nothing. */
        public static Found nothing() {
            return new Found(List.of(), List.of(), 0, false);
        }

        /** How many slots on the controller are taken: drawers and devices alike. */
        public int slots() {
            return drawers.size() + devices.size();
        }

        /** The network this describes, in the vocabulary the rules use. */
        public LogisticRules.Network network() {
            return new LogisticRules.Network(slots(), surcharge);
        }
    }

    /**
     * How far a walk will go before giving up.
     *
     * Larger than the device limit on purpose: a player may run bus a long way
     * to reach a wall of drawers, and the thing being limited is how many
     * drawers a controller indexes, not how much cable they are allowed.
     */
    public static final int MOST_STEPS = 4096;

    /** Whether the block at this position carries a network through it. */
    public static boolean carries(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BusBlock;
    }

    /** The drawer at this position, or null if there is not one. */
    public static DrawerBlock.DrawerBlockEntity drawerAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof DrawerBlock.DrawerBlockEntity drawer ? drawer : null;
    }

    /** The device at this position, or null if there is not one. */
    public static DeviceBlock.DeviceBlockEntity deviceAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof DeviceBlock.DeviceBlockEntity device ? device : null;
    }

    /**
     * Every drawer and every device reachable from a controller.
     *
     * <p>Bus carries; drawers and devices are found beside it and are not
     * walked through, so a wall of drawers packed together does not silently
     * chain a network across a base without any bus in it. A network is a
     * thing you lay out.
     *
     * <p>A device takes one slot, like a drawer, and pays its surcharge on
     * top: {@link NetRules#surcharge} is summed here so that what the
     * controller bills the grid is the number the harness proved.
     */
    public static Found walk(Level level, BlockPos controller) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> drawers = new HashSet<>();
        Set<BlockPos> devices = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(controller.immutable());
        queue.add(controller.immutable());

        while (!queue.isEmpty()) {
            BlockPos here = queue.poll();
            for (Direction face : Direction.values()) {
                BlockPos next = here.relative(face);
                if (drawerAt(level, next) != null) {
                    drawers.add(next.immutable());
                    continue;
                }
                if (deviceAt(level, next) != null) {
                    devices.add(next.immutable());
                    continue;
                }
                if (!carries(level, next) || !seen.add(next.immutable())) {
                    continue;
                }
                if (seen.size() > MOST_STEPS) {
                    return new Found(sorted(drawers), sorted(devices),
                            surchargeOf(level, devices), true);
                }
                queue.add(next.immutable());
            }
        }
        List<BlockPos> found = sorted(drawers);
        List<BlockPos> faces = sorted(devices);
        return new Found(found, faces, surchargeOf(level, devices),
                found.size() + faces.size() > LogisticRules.MOST_DEVICES);
    }

    /** What these devices pay the grid beyond their slots, off the roster. */
    private static int surchargeOf(Level level, Set<BlockPos> devices) {
        List<NetRules.Device> kinds = new ArrayList<>();
        for (BlockPos pos : devices) {
            DeviceBlock.DeviceBlockEntity device = deviceAt(level, pos);
            if (device != null) {
                kinds.add(device.device());
            }
        }
        return NetRules.surcharge(kinds);
    }

    /**
     * Every block entity of one kind reachable along bus from here.
     *
     * <h2>The same walk again, and deliberately so</h2>
     *
     * {@link #walk} answers "which drawers does this controller index", which
     * carries a device limit and an overload flag because that is a question
     * about capacity. This answers "what else is on this run of bus", which is
     * a question about wiring and has no limit beyond how far the bus goes.
     *
     * <p>Same shape, same rule about what carries, same stable order. A player
     * who has understood how a controller finds its drawers already knows how
     * a core finds its assemblers, and a contributor adding a fifth kind of
     * device knows what it should look like before they start.
     */
    public static <T> List<BlockPos> along(Level level, BlockPos from, Class<T> kind) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(from.immutable());
        queue.add(from.immutable());

        while (!queue.isEmpty()) {
            BlockPos here = queue.poll();
            for (Direction face : Direction.values()) {
                BlockPos next = here.relative(face);
                if (level.isLoaded(next) && kind.isInstance(level.getBlockEntity(next))) {
                    found.add(next.immutable());
                    continue;
                }
                if (!carries(level, next) || !seen.add(next.immutable())) {
                    continue;
                }
                if (seen.size() > MOST_STEPS) {
                    return sorted(found);
                }
                queue.add(next.immutable());
            }
        }
        return sorted(found);
    }

    /**
     * In a stable order, so that a network fills its drawers the same way
     * twice.
     *
     * A set has no order, and a controller that distributed items by whatever
     * order a hash table happened to produce would put the same stack in a
     * different drawer on every world load — which is not a crash and is not
     * data loss, but it is a base that never looks the same twice.
     */
    private static List<BlockPos> sorted(Set<BlockPos> found) {
        List<BlockPos> out = new ArrayList<>(found);
        out.sort((a, b) -> {
            if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            }
            if (a.getX() != b.getX()) {
                return Integer.compare(a.getX(), b.getX());
            }
            return Integer.compare(a.getZ(), b.getZ());
        });
        return out;
    }

    /** Tell whatever controller is near this position that something changed. */
    public static void disturb(Level level, BlockPos around) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-1, -1, -1),
                around.offset(1, 1, 1))) {
            if (level.getBlockEntity(pos)
                    instanceof ControllerBlock.ControllerBlockEntity controller) {
                controller.unsettleNetwork();
            }
        }
    }
}
