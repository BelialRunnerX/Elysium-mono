package com.elysium.lib.item;

import com.elysium.lib.event.ElysiumPassives;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared block-breaking behaviour for the Elysium tool line.
 *
 * Two shapes: a 3x3 in the plane of the face you struck, and a flood fill up a
 * tree. Both stop short of destroying the tool, and both are guarded against
 * re-entry so a tool can never trigger itself.
 */
public final class ElysiumAreaBreak {

    private ElysiumAreaBreak() {
    }

    /**
     * Re-entry guard. {@code Level#destroyBlock} does not itself call back into
     * the item, but other mods hook block breaking, and a tool that recursed
     * would take the world with it.
     */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    /** A fell stops here however big the tree is. */
    private static final int MAX_FELL = 192;

    // ------------------------------------------------------------------

    /**
     * Breaks the 3x3 centred on {@code origin}, in the plane of the face the
     * player is looking at — so mining a wall takes a vertical slice and mining
     * the floor takes a horizontal one.
     */
    public static void area(Level level, BlockPos origin, Player player,
                            ItemStack tool, Predicate<BlockState> allowed) {
        if (level.isClientSide() || ACTIVE.get()) {
            return;
        }
        ACTIVE.set(true);
        try {
            Direction.Axis axis = lookAxis(player);
            List<BlockPos> targets = new ArrayList<>(8);
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    targets.add(switch (axis) {
                        case Y -> origin.offset(a, 0, b);
                        case Z -> origin.offset(a, b, 0);
                        case X -> origin.offset(0, a, b);
                    });
                }
            }
            for (BlockPos target : targets) {
                if (!breakOne(level, target, player, tool, allowed)) {
                    break;
                }
            }
        } finally {
            ACTIVE.set(false);
        }
    }

    /**
     * Flood fills from the broken log through every connected log and takes the
     * lot. Connectivity includes diagonals, because a natural tree's branches
     * only touch at their corners.
     */
    public static void fell(Level level, BlockPos origin, Player player,
                            ItemStack tool, Predicate<BlockState> allowed) {
        if (level.isClientSide() || ACTIVE.get()) {
            return;
        }
        ACTIVE.set(true);
        try {
            Set<BlockPos> seen = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            seen.add(origin.immutable());
            queue.add(origin.immutable());

            int felled = 0;
            while (!queue.isEmpty() && felled < MAX_FELL) {
                BlockPos current = queue.poll();

                if (!current.equals(origin)) {
                    if (!breakOne(level, current, player, tool, allowed)) {
                        return;
                    }
                    felled++;
                }

                // Search upward and outward only: a fell should take the tree,
                // not tunnel into the forest floor beside it.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos next = current.offset(dx, dy, dz);
                            if (seen.size() >= MAX_FELL * 2 || !seen.add(next)) {
                                continue;
                            }
                            if (allowed.test(level.getBlockState(next))) {
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        } finally {
            ACTIVE.set(false);
        }
    }

    // ------------------------------------------------------------------

    private static boolean breakOne(Level level, BlockPos pos, Player player,
                                    ItemStack tool, Predicate<BlockState> allowed) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !allowed.test(state)) {
            return true;
        }
        // Unbreakable blocks report a negative destroy speed. Bedrock is not a
        // resource.
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return true;
        }
        if (!spendDurability(tool, player)) {
            return false;
        }
        level.destroyBlock(pos, true, player);
        return true;
    }

    /**
     * One point of wear per extra block, and the tool refuses the last point
     * rather than snapping mid-swing.
     *
     * This does the arithmetic by hand instead of calling {@code hurtAndBreak}.
     * That helper's signature changed across 1.21.x and could not be verified
     * for this exact build; {@code getDamageValue}/{@code setDamageValue} have
     * been stable for years. The tradeoff is that Unbreaking does not apply to
     * the extra blocks — swap in {@code hurtAndBreak} once you can check the
     * signature against the real jar.
     *
     * @return false when the tool has no wear left to give
     */
    private static boolean spendDurability(ItemStack tool, Player player) {
        if (player.isCreative() || !tool.isDamageableItem()) {
            return true;
        }
        // Field Repair: an Artificer's gear wears at about two thirds the rate.
        if (ElysiumPassives.savesDurability(player)) {
            return true;
        }
        int next = tool.getDamageValue() + 1;
        if (next >= tool.getMaxDamage()) {
            return false;
        }
        tool.setDamageValue(next);
        return true;
    }

    /** The axis of the face the player is looking at, defaulting to vertical. */
    private static Direction.Axis lookAxis(Player player) {
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            return blockHit.getDirection().getAxis();
        }
        return Direction.Axis.Y;
    }
}
