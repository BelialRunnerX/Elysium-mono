package com.elysium.arsenal.item;

import com.elysium.arsenal.rules.ArsenalRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * A tool that takes a wall at a time.
 *
 * <h2>What makes it a trade rather than an upgrade</h2>
 *
 * It swings more slowly than the tool it replaces, and <b>every block it takes
 * costs a point of durability just as if it had been mined singly</b>. Nine
 * blocks for one point would be nine times the tool for the same price, and
 * every other tool in the game would be a mistake. The arithmetic behind both
 * is in {@link ArsenalRules}, where the harness sweeps it.
 *
 * <h2>And the three refusals</h2>
 *
 * It will not take a block the specialist could not have taken, it will not
 * take one much harder than the one it was swung at, and it will not take
 * anything at all when the block it was swung at breaks instantly — because
 * a hammer that cleared nine blocks of grass every time a player brushed a
 * leaf would be unusable rather than powerful.
 */
public class BroadToolItem extends DiggerItem implements com.elysium.lib.item.ElysiumKinded {

    /**
     * Whether we are already inside a break of our own.
     *
     * <p>Breaking a block calls back into this method for that block, and a
     * tool that answered would clear nine blocks around each of nine blocks
     * around each of nine blocks. One flag, per thread, and it is set for the
     * length of one swing.
     */
    private static final ThreadLocal<Boolean> SWINGING =
            ThreadLocal.withInitial(() -> false);

    /** How much harder than its target a block may be and still be taken. */
    public static final float HARDER_BY = 4.0F;

    private final String shape;
    private final boolean wide;

    public BroadToolItem(Tier tier, TagKey<Block> mineable, Properties properties,
                         String shape, boolean wide) {
        super(tier, mineable, properties);
        this.shape = shape;
        this.wide = wide;
    }

    public String shape() {
        return shape;
    }

    /**
     * What the library calls this shape. A DiggerItem is no particular tool
     * to a class test, so the tool says: the hammer and excavator and paxel
     * are kinds the gear tree has, and a scythe is read as a hoe, which is
     * the block tag it mines by and the vanilla list it enchants from.
     */
    @Override
    public String gearKind() {
        return "scythe".equals(shape) ? "hoe" : shape;
    }

    public boolean wide() {
        return wide;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos,
                             LivingEntity miner) {
        boolean broke = super.mineBlock(stack, level, state, pos, miner);
        if (!wide || level.isClientSide || SWINGING.get()
                || !(miner instanceof Player player)) {
            return broke;
        }
        if (state.getDestroySpeed(level, pos) <= 0.0F) {
            // Instant. A tool that cleared nine blocks every time somebody
            // brushed a leaf would be unusable rather than powerful.
            return broke;
        }
        SWINGING.set(true);
        try {
            int taken = 0;
            for (BlockPos next : around(level, player, pos)) {
                BlockState other = level.getBlockState(next);
                if (other.isAir() || !stack.isCorrectToolForDrops(other)) {
                    continue;
                }
                float hardness = other.getDestroySpeed(level, next);
                if (hardness < 0.0F
                        || hardness > state.getDestroySpeed(level, pos) + HARDER_BY) {
                    continue;
                }
                if (level.destroyBlock(next, true, player)) {
                    taken++;
                }
            }
            if (taken > 0) {
                // Paid for as if each had been mined singly, which is the
                // whole of why this is a trade.
                stack.hurtAndBreak(ArsenalRules.costOf(taken), player,
                        EquipmentSlot.MAINHAND);
            }
        } finally {
            SWINGING.set(false);
        }
        return broke;
    }

    /**
     * The eight blocks around the one that was hit, in the plane the player is
     * looking at.
     *
     * <p>The plane rather than a cube: a hammer swung at a wall takes the
     * wall, and one swung at a floor takes the floor. A cube would take the
     * block behind whatever you hit, which is how a player ends up standing in
     * a hole they did not dig.
     */
    private List<BlockPos> around(Level level, Player player, BlockPos hit) {
        List<BlockPos> found = new ArrayList<>(
                ArsenalRules.blocksIn(ArsenalRules.REACH) - 1);
        Direction face = facing(player);
        int reach = ArsenalRules.REACH;
        for (int first = -reach; first <= reach; first++) {
            for (int second = -reach; second <= reach; second++) {
                if (first == 0 && second == 0) {
                    continue;
                }
                found.add(switch (face.getAxis()) {
                    case Y -> hit.offset(first, 0, second);
                    case X -> hit.offset(0, first, second);
                    default -> hit.offset(first, second, 0);
                });
            }
        }
        return found;
    }

    /** Which way the player is looking, or down if the ray found nothing. */
    private static Direction facing(Player player) {
        HitResult looking = player.pick(6.0D, 0.0F, false);
        return looking instanceof BlockHitResult block ? block.getDirection()
                : Direction.orderedByNearest(player)[0].getOpposite();
    }
}
