package com.elysium.kinetics.block;

import com.elysium.kinetics.rules.KineticRules;
import com.elysium.kinetics.rules.LineRules;
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
 * Finding the line, and telling it what it is doing.
 *
 * <h2>How two blocks connect</h2>
 *
 * Exactly two ways, and the whole of Age I is built out of them.
 *
 * <ol>
 *   <li><b>Along the axis.</b> Two blocks turning about the same axis, one
 *       directly in front of the other along it, are the same shaft. Ratio 1,
 *       same direction. This is a shaft, a cog on a shaft, a millstone under
 *       one.</li>
 *   <li><b>Meshing.</b> Two cogs turning about the same axis, side by side
 *       perpendicular to it, are in mesh. The ratio is minus the first's teeth
 *       over the second's — minus, because a pair of gears turns opposite
 *       ways, which is why three cogs in a row turn the same way as one.</li>
 * </ol>
 *
 * <p>Deliberately not Create's rule set. Create also meshes cogs at right
 * angles and diagonally and has a large cog that only meshes with a small one
 * at an offset, which is a lot of geometry for the player to learn by trial.
 * Two rules is a machine hall you can read at a glance, and a gearbox — Age
 * I's other connector — covers the right-angle case explicitly instead of by
 * accident.
 *
 * <h2>One walk for the whole line</h2>
 *
 * When anything changes, one member walks the line, works out a single
 * {@link LineRules.Assembly}, and tells every member what it is doing. It does
 * not matter which member walks: a line has no root of its own, the walker
 * simply becomes the root and every ratio is relative to it.
 */
public final class KineticLine {

    private KineticLine() {
    }

    /** A block on the line: where it is, and where on the line it sits. */
    public record Member(BlockPos pos, LineRules.At at) {
    }

    /** What a walk found. */
    public record Found(List<Member> members, boolean jammed) {
    }

    // ------------------------------------------------------------------
    // Connections
    // ------------------------------------------------------------------

    /**
     * The ratio from {@code here} to the neighbour in direction {@code face},
     * or null when the two do not connect at all.
     */
    public static Float across(KineticNode here, KineticNode there, Direction face) {
        if (here.couplesAnyAxis() || there.couplesAnyAxis()) {
            return corner(here, there, face);
        }
        if (here.axis() != there.axis()) {
            return null;                       // two shafts crossing, not touching
        }
        if (face.getAxis() == here.axis()) {
            return 1.0F;                       // the same shaft, further along
        }
        if (here.teeth() > 0 && there.teeth() > 0) {
            return KineticRules.ratio(here.teeth(), there.teeth());
        }
        return null;                           // side by side, and not both cogs
    }

    /**
     * A gearbox, and the one thing it does: turn the corner.
     *
     * <p>A shaft only joins a gearbox if it runs <em>into</em> it — a shaft
     * lying across a gearbox's face is passing by, not connected, and treating
     * it as connected would make a gearbox couple half a machine hall by
     * accident.
     *
     * <p>Every shaft on a gearbox turns the opposite way to the box itself, so
     * two on opposite sides turn together and a corner reverses. That is one
     * sentence a player can hold, and it costs a gear stage like anything else
     * with teeth in it.
     */
    private static Float corner(KineticNode here, KineticNode there, Direction face) {
        if (!here.couplesAnyAxis() && here.axis() != face.getAxis()) {
            return null;
        }
        if (!there.couplesAnyAxis() && there.axis() != face.getAxis()) {
            return null;
        }
        return -1.0F;
    }

    // ------------------------------------------------------------------
    // The walk
    // ------------------------------------------------------------------

    /**
     * Every block on the line through {@code start}, and whether it jams.
     *
     * <p>Breadth first, so that the shortest path to each block is the one
     * whose gear-stage count is recorded — which is the one the power actually
     * takes, and which decides what the gearing costs.
     *
     * <p>A line that runs past {@link LineRules#MOST_MEMBERS} is reported as
     * jammed rather than truncated. Truncating would mean the same build gives
     * a different answer depending on which block was changed last, which is
     * the worst kind of bug to be told about.
     */
    public static Found walk(Level level, BlockPos start) {
        Map<BlockPos, LineRules.At> seen = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        boolean jammed = false;

        if (!(nodeAt(level, start) instanceof KineticNode)) {
            return new Found(List.of(), false);
        }
        seen.put(start.immutable(), LineRules.At.ROOT);
        queue.add(start.immutable());

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            KineticNode here = nodeAt(level, pos);
            if (here == null) {
                continue;
            }
            LineRules.At at = seen.get(pos);
            for (Direction face : Direction.values()) {
                BlockPos next = pos.relative(face);
                KineticNode there = nodeAt(level, next);
                if (there == null) {
                    continue;
                }
                Float ratio = across(here, there, face);
                if (ratio == null) {
                    continue;
                }
                float reached = LineRules.compose(at.ratio(), ratio);
                LineRules.At already = seen.get(next);
                if (already != null) {
                    if (LineRules.conflict(already.ratio(), reached)) {
                        jammed = true;
                    }
                    continue;
                }
                // A plain shaft is not a gear stage; only a mesh costs power.
                int stages = at.stages() + (ratio == 1.0F ? 0 : 1);
                seen.put(next.immutable(), new LineRules.At(reached, stages));
                if (LineRules.tooBig(seen.size())) {
                    return new Found(asMembers(seen), true);
                }
                queue.add(next.immutable());
            }
        }
        return new Found(asMembers(seen), jammed);
    }

    private static List<Member> asMembers(Map<BlockPos, LineRules.At> seen) {
        List<Member> members = new ArrayList<>(seen.size());
        for (Map.Entry<BlockPos, LineRules.At> entry : seen.entrySet()) {
            members.add(new Member(entry.getKey(), entry.getValue()));
        }
        return members;
    }

    // ------------------------------------------------------------------
    // Solving
    // ------------------------------------------------------------------

    /**
     * Walk the line through this block, work out what it is doing, and tell
     * every member.
     *
     * @return the assembly, so the caller can show it without walking again
     */
    public static LineRules.Assembly solve(Level level, BlockPos start) {
        Found found = walk(level, start);
        List<LineRules.Driven> sources = new ArrayList<>();
        List<LineRules.Drawn> loads = new ArrayList<>();
        KineticRules.Shaft weakest = KineticRules.Shaft.ASCENDANT;

        for (Member member : found.members()) {
            KineticNode node = nodeAt(level, member.pos());
            if (node == null) {
                continue;
            }
            if (node.material().rating() < weakest.rating()) {
                weakest = node.material();
            }
            KineticRules.Source source = node.source();
            if (source != null) {
                sources.add(new LineRules.Driven(source, member.at()));
            }
            KineticRules.Load load = node.load();
            if (load != null) {
                loads.add(new LineRules.Drawn(load, member.at()));
            }
        }

        LineRules.Assembly assembly =
                LineRules.assemble(sources, loads, weakest, found.jammed());
        for (Member member : found.members()) {
            KineticNode node = nodeAt(level, member.pos());
            if (node != null) {
                node.driven(assembly.speedOf(member.at()), assembly.running());
            }
        }
        return assembly;
    }

    /**
     * Tell the line through this block that something changed.
     *
     * Called when a block is placed or broken next to it. Every member is
     * unsettled rather than re-solved on the spot, because a placement can be
     * one of several in a tick and solving once at the end of it is both
     * cheaper and the only way to get a consistent answer.
     */
    public static void disturb(Level level, BlockPos around) {
        for (Direction face : Direction.values()) {
            BlockPos pos = around.relative(face);
            KineticNode node = nodeAt(level, pos);
            if (node != null) {
                node.unsettle();
            }
        }
        KineticNode here = nodeAt(level, around);
        if (here != null) {
            here.unsettle();
        }
    }

    /** The node at a position, or null if there is not one. */
    public static KineticNode nodeAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof KineticNode node ? node : null;
    }
}
