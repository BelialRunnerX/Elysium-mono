package com.elysium.lib.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finding out whether a position is inside a room, and what the room is made of.
 *
 * <h2>The rule, in one sentence</h2>
 *
 * A room is a volume of air you cannot walk out of.
 *
 * That is the whole test, and it is deliberately the simplest one that is
 * correct. The fill spreads through anything with no collision — air, torches,
 * grass — and stops at anything with any: a wall, a closed door, a pane of
 * glass. So a glazed greenhouse is sealed and a doorway standing open is not,
 * which is both what a player would guess and what the word means.
 *
 * <h3>Why not "is there a roof and four walls"</h3>
 *
 * Because that is a shape test, and players do not build shapes. A room dug out
 * of a hillside has one wall and five faces of stone; a room built on stilts has
 * a floor nobody would call a wall. Every version of the shape test either
 * refuses a room somebody clearly built or accepts an alcove nobody did. The
 * fill asks the only question that matters — is this volume closed — and the
 * answer is the same whatever it is made of.
 *
 * <h2>Why giving up is an answer</h2>
 *
 * The overwhelmingly common case is a player standing outdoors, where the air
 * is the whole world and no fill would ever finish. {@link #MAX_VOLUME} is what
 * makes the common case cheap: the scan spends a bounded amount of work and
 * reports "not a room", which is both true and the fastest thing it could have
 * said. It is also the right answer for a cavern — a room is a volume somebody
 * bounded on purpose, and nobody bounds five hundred blocks of air by accident.
 */
public final class ElysiumRoomScan {

    private ElysiumRoomScan() {
    }

    /**
     * How much air the scan will walk before deciding this is not a room.
     *
     * Five hundred and twelve is an eight-by-eight-by-eight hall, which is
     * larger than anything the room kinds ask for and small enough that a
     * player standing in the open costs one bounded sweep rather than a
     * flood-fill of the sky.
     */
    public static final int MAX_VOLUME = 512;

    /** The six directions air connects through. */
    private static final Direction[] SIDES = Direction.values();

    /**
     * Survey the volume around a position.
     *
     * Never returns null. A position inside a block, or in the open, or in a
     * space too large to be a room, all report {@link ElysiumRoomCensus#open()}
     * — which no room kind matches, because {@code sealed} is false.
     */
    public static ElysiumRoomCensus survey(BlockGetter level, BlockPos from) {
        Set<TagKey<Block>> interesting = ElysiumRoom.interestingTags();
        Map<TagKey<Block>, Integer> boundary = new HashMap<>();
        Set<BlockPos> inside = fill(level, from, MAX_VOLUME, wall -> {
            BlockState state = level.getBlockState(wall);
            for (TagKey<Block> tag : interesting) {
                if (state.is(tag)) {
                    boundary.merge(tag, 1, Integer::sum);
                }
            }
        });
        if (inside.isEmpty()) {
            return ElysiumRoomCensus.open();
        }
        return new ElysiumRoomCensus(inside.size(), true, boundary);
    }

    /**
     * Every air position of the room around {@code from}, or an empty set when
     * there is no room there: the start is solid, or the air runs on past
     * {@code maxVolume}. The same fill {@link #survey} uses, so anything that
     * asks "is this enclosed" (a room kind, a space module's oxygen) gets the
     * one answer. Only the cap differs, and the caller picks it.
     */
    public static Set<BlockPos> interior(BlockGetter level, BlockPos from, int maxVolume) {
        return fill(level, from, maxVolume, wall -> { });
    }

    /**
     * The flood fill. {@code onWall} sees each boundary block once, however
     * many interior faces touch it: a furnace in a corner is one furnace, not
     * three.
     */
    private static Set<BlockPos> fill(BlockGetter level, BlockPos from, int maxVolume,
                                      java.util.function.Consumer<BlockPos> onWall) {
        if (level == null || from == null || maxVolume <= 0 || !passable(level, from)) {
            return Set.of();
        }
        Set<BlockPos> inside = new HashSet<>();
        Set<BlockPos> counted = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        inside.add(from.immutable());
        queue.add(from.immutable());
        while (!queue.isEmpty()) {
            BlockPos at = queue.removeFirst();
            for (Direction side : SIDES) {
                BlockPos next = at.relative(side);
                if (inside.contains(next)) {
                    continue;
                }
                if (passable(level, next)) {
                    if (inside.size() >= maxVolume) {
                        return Set.of();          // not bounded; not a room
                    }
                    inside.add(next.immutable());
                    queue.add(next.immutable());
                    continue;
                }
                if (counted.add(next.immutable())) {
                    onWall.accept(next);
                }
            }
        }
        return inside;
    }

    /**
     * Whether air spreads through this block.
     *
     * Collision, not opacity. Glass has collision and stops the fill, which is
     * what makes a greenhouse a sealed room; an open door has none and does
     * not, which is what makes "close the door" a thing a player can do about
     * it. Opacity would have got both backwards.
     */
    private static boolean passable(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }
}
