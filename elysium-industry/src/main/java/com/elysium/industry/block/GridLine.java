package com.elysium.industry.block;

import com.elysium.industry.rules.IndustryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding the grid, and telling it what it is doing.
 *
 * <h2>How two blocks connect</h2>
 *
 * Two rules, as in Age I.
 *
 * <ol>
 *   <li><b>Same tier, touching.</b> A wire joins anything of its own voltage
 *       that it touches on a face they both accept. That is a grid.</li>
 *   <li><b>Through a transformer.</b> The one block that may have two tiers on
 *       it, and therefore the only reason a grid is ever more than one tier
 *       wide. Everywhere else, a higher tier meeting a lower one destroys the
 *       lower one, which is the rule that makes transformers worth building
 *       rather than worth skipping.</li>
 * </ol>
 *
 * <h2>Distance</h2>
 *
 * Every member records how far it is from the nearest <em>generator</em>, in
 * blocks, walked breadth first so the number is the shortest path — which is
 * the one the power takes. That distance is what the wire loss is charged on,
 * so a machine at the far end of a valley genuinely receives less than one
 * beside the turbine.
 */
public final class GridLine {

    private GridLine() {
    }

    /** A block on the grid: where it is, and how far from the nearest generator. */
    public record Member(BlockPos pos, int blocks) {
    }

    /** What a walk found. */
    public record Found(List<Member> members, IndustryRules.Voltage weakest, boolean mixed) {
    }

    /** How far a grid may run before we stop walking it. */
    public static final int MOST_MEMBERS = 4096;

    /** Whether power passes between these two, and nothing about how far. */
    public static boolean joins(GridNode here, GridNode there, Direction face) {
        if (!here.connects(face) || !there.connects(face.getOpposite())) {
            return false;
        }
        if (here.transforms() || there.transforms()) {
            return true;                       // the one block allowed two tiers
        }
        return here.voltage() == there.voltage();
    }

    /**
     * Every block on the grid through {@code start}.
     *
     * <p>Breadth first from the generators rather than from the starting
     * block, so the distance recorded on each member is its distance from
     * power and not from whatever happened to be clicked. A grid with no
     * generator on it records everything at distance zero, which is right:
     * nothing is travelling, so nothing is lost.
     */
    public static Found walk(Level level, BlockPos start) {
        // First pass: everything reachable, ignoring distance.
        Map<BlockPos, Integer> seen = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> sources = new ArrayList<>();
        IndustryRules.Voltage weakest = IndustryRules.Voltage.EXTREME;
        boolean mixed = false;

        GridNode first = nodeAt(level, start);
        if (first == null) {
            return new Found(List.of(), weakest, false);
        }
        seen.put(start.immutable(), Integer.MAX_VALUE);
        queue.add(start.immutable());

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            GridNode here = nodeAt(level, pos);
            if (here == null) {
                continue;
            }
            if (here.generator() != null) {
                sources.add(pos);
            }
            if (here.carries() && here.voltage().ordinal() < weakest.ordinal()) {
                weakest = here.voltage();
            }
            for (Direction face : Direction.values()) {
                BlockPos next = pos.relative(face);
                GridNode there = nodeAt(level, next);
                if (there == null || !joins(here, there, face)) {
                    continue;
                }
                if (here.voltage() != there.voltage()
                        && !here.transforms() && !there.transforms()) {
                    mixed = true;
                }
                if (seen.putIfAbsent(next.immutable(), Integer.MAX_VALUE) == null) {
                    if (seen.size() > MOST_MEMBERS) {
                        return new Found(members(seen), weakest, true);
                    }
                    queue.add(next.immutable());
                }
            }
        }

        // Second pass: distance from the nearest generator.
        for (BlockPos source : sources) {
            seen.put(source, 0);
            queue.add(source);
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int here = seen.getOrDefault(pos, Integer.MAX_VALUE);
            if (here == Integer.MAX_VALUE) {
                continue;
            }
            GridNode node = nodeAt(level, pos);
            if (node == null) {
                continue;
            }
            for (Direction face : Direction.values()) {
                BlockPos next = pos.relative(face);
                GridNode there = nodeAt(level, next);
                if (there == null || !joins(node, there, face) || !seen.containsKey(next)) {
                    continue;
                }
                // Only wire counts as distance: a machine is not a mile of cable.
                int step = here + (node.carries() ? 1 : 0);
                if (step < seen.get(next)) {
                    seen.put(next.immutable(), step);
                    queue.add(next.immutable());
                }
            }
        }
        if (sources.isEmpty()) {
            seen.replaceAll((pos, blocks) -> 0);
        }
        return new Found(members(seen), weakest, mixed);
    }

    private static List<Member> members(Map<BlockPos, Integer> seen) {
        List<Member> members = new ArrayList<>(seen.size());
        for (Map.Entry<BlockPos, Integer> entry : seen.entrySet()) {
            members.add(new Member(entry.getKey(),
                    entry.getValue() == Integer.MAX_VALUE ? 0 : entry.getValue()));
        }
        return members;
    }

    /**
     * Walk the grid through this block, work out what it is doing, and tell
     * every member.
     *
     * <p>Each generator's output is charged the loss for its own distance to
     * the machine that is furthest from it — the honest simplification. A full
     * per-pair loss would be a matrix, and a player cannot hold a matrix; "the
     * far end of the line gets less" is a sentence they can build around.
     */
    public static IndustryRules.Reading solve(Level level, BlockPos start) {
        Found found = walk(level, start);
        List<IndustryRules.Placed<IndustryRules.Generator>> generators =
                new ArrayList<>();
        List<IndustryRules.Placed<IndustryRules.Machine>> machines =
                new ArrayList<>();

        for (Member member : found.members()) {
            GridNode node = nodeAt(level, member.pos());
            if (node == null) {
                continue;
            }
            IndustryRules.Machine machine = node.machine();
            if (machine != null) {
                machines.add(new IndustryRules.Placed<>(machine, member.blocks()));
            }
        }
        for (Member member : found.members()) {
            GridNode node = nodeAt(level, member.pos());
            IndustryRules.Generator generator = node == null ? null : node.generator();
            if (generator == null) {
                continue;
            }
            // Not discounted here. The loss down a wire is the one number
            // that makes stepping a voltage up worth doing, and it used to be
            // applied in this method — which meant the arithmetic never saw
            // it and nothing anywhere said that a grid hands out what it
            // made. IndustryRules.along does the discounting now, and
            // IndustryRules.books is swept over ten thousand random grids to
            // prove it conserves.
            generators.add(new IndustryRules.Placed<>(generator, member.blocks()));
        }

        // Everything on the grid that can lend or borrow, added up. Age II
        // ships nothing that answers this; the age above does, and there is
        // one grid in the pack rather than one per age.
        float couldGive = 0.0F;
        float couldTake = 0.0F;
        for (Member member : found.members()) {
            GridNode node = nodeAt(level, member.pos());
            IndustryRules.Reserve reserve = node == null ? null : node.reserve();
            if (reserve != null) {
                couldGive += reserve.couldGive();
                couldTake += reserve.couldTake();
            }
        }
        IndustryRules.Reserve bank = new IndustryRules.Reserve(couldGive, couldTake);

        IndustryRules.Flow flow =
                IndustryRules.along(generators, machines, found.weakest(), bank);
        IndustryRules.Reading reading = flow.grid();
        // A grid wired across two tiers without a transformer is a grid that
        // has destroyed something. Reported as burned, which is the state a
        // player can see and the state that stops everything.
        boolean burned = reading.burned() || found.mixed();
        IndustryRules.Reading told = new IndustryRules.Reading(
                reading.supply(), reading.demand(), burned);
        IndustryRules.Flow moved = burned
                ? new IndustryRules.Flow(told, 0.0F, 0.0F)
                : new IndustryRules.Flow(told, flow.fromReserve(), flow.toReserve());
        for (Member member : found.members()) {
            GridNode node = nodeAt(level, member.pos());
            if (node != null) {
                node.fed(told, member.blocks());
                node.flowed(moved, bank);
            }
        }
        return told;
    }

    /** Tell the grid through this block that something changed. */
    public static void disturb(Level level, BlockPos around) {
        for (Direction face : Direction.values()) {
            GridNode node = nodeAt(level, around.relative(face));
            if (node != null) {
                node.unsettle();
            }
        }
        GridNode here = nodeAt(level, around);
        if (here != null) {
            here.unsettle();
        }
    }

    /** The node at a position, or null if there is not one. */
    public static GridNode nodeAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof GridNode node ? node : null;
    }
}
