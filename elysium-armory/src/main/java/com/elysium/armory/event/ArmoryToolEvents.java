package com.elysium.armory.event;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryGear;
import com.elysium.armory.item.ArmoryGearLogic;
import com.elysium.armory.item.ArmoryToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the wide tools do to the block around the one they struck: a hammer or
 * excavator takes the face, a saw takes the tree, a machete clears the
 * undergrowth.
 *
 * <p>Hooked to the break event rather than to the item, because that is where
 * the game states a block was broken by a player, and because it then applies
 * however the block was broken. Re-entry is guarded: every extra block is
 * broken inside the guard, so a hammer never triggers itself.
 *
 * <p>The match rule is Silent Gear's: a wide tool takes blocks of the same kind
 * of hardness, and ores only when an ore was struck, so one swing at a wall of
 * stone does not quietly empty a vein behind it.
 */
@EventBusSubscriber(modid = ElysiumArmory.MODID)
public final class ArmoryToolEvents {

    private ArmoryToolEvents() {
    }

    /** How far a saw will follow a tree: enough for a jungle giant, not enough to hang a server. */
    public static final int TREE_LIMIT = 1000;
    public static final int TREE_HEIGHT = 50;
    public static final int TREE_RADIUS = 3;

    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (BREAKING.get() || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ArmoryGear gear) || player.isShiftKeyDown()) {
            return;             // crouching takes one block, as it does with every wide tool
        }
        BlockPos struck = event.getPos();
        BlockState state = event.getState();
        List<BlockPos> extra = switch (gear.gearType()) {
            case "hammer", "excavator" -> plane(level, player, struck, state, radius(held));
            case "saw" -> tree(level, struck, state);
            case "machete", "sickle" -> plants(level, struck, state);
            default -> List.of();
        };
        if (extra.isEmpty()) {
            return;
        }
        BREAKING.set(Boolean.TRUE);
        try {
            for (BlockPos pos : extra) {
                if (held.isEmpty() || held.getDamageValue() >= held.getMaxDamage() - 1) {
                    break;                          // a tool stops when it is spent, not after
                }
                if (player instanceof ServerPlayer server) {
                    server.gameMode.destroyBlock(pos);
                    held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }
            }
        } finally {
            BREAKING.set(Boolean.FALSE);
        }
    }

    /**
     * A wide tool's radius: one, and one wider for each level of Widen — which
     * is the trait a Wide Plate grants, so the plate and a material that
     * happens to grant the same trait both count.
     */
    public static int radius(ItemStack stack) {
        return 1 + com.elysium.armory.trait.ArmoryCodedTraits.widen(stack);
    }

    /** The square of blocks in the plane of the face that was struck. */
    private static List<BlockPos> plane(Level level, Player player, BlockPos struck, BlockState state, int radius) {
        Direction face = faceOf(player, struck);
        List<BlockPos> out = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos pos = switch (face.getAxis()) {
                    case Y -> struck.offset(a, 0, b);
                    case X -> struck.offset(0, a, b);
                    case Z -> struck.offset(a, b, 0);
                };
                if (matches(level, state, level.getBlockState(pos))) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    /** Which face the player is looking at; the top if the ray says nothing. */
    private static Direction faceOf(Player player, BlockPos struck) {
        HitResult hit = player.pick(player.blockInteractionRange() + 1.0D, 0.0F, false);
        if (hit instanceof BlockHitResult block && block.getBlockPos().equals(struck)) {
            return block.getDirection();
        }
        return Direction.UP;
    }

    /**
     * Whether a neighbour goes with the block that was struck. Ore for ore,
     * exactly; anything else by how hard it is to break, so a wall of stone
     * comes away together and the dirt behind it does not.
     */
    private static boolean matches(Level level, BlockState struck, BlockState other) {
        if (other.isAir() || other.getDestroySpeed(level, BlockPos.ZERO) < 0.0F) {
            return false;
        }
        boolean ore = struck.is(net.minecraft.tags.BlockTags.COAL_ORES) || struck.is(BlockTags.IRON_ORES)
                || struck.is(BlockTags.GOLD_ORES) || struck.is(BlockTags.COPPER_ORES)
                || struck.is(BlockTags.DIAMOND_ORES) || struck.is(BlockTags.EMERALD_ORES)
                || struck.is(BlockTags.LAPIS_ORES) || struck.is(BlockTags.REDSTONE_ORES);
        if (ore) {
            return other.is(struck.getBlock());
        }
        float a = struck.getDestroySpeed(level, BlockPos.ZERO);
        float b = other.getDestroySpeed(level, BlockPos.ZERO);
        return Math.abs(a - b) <= 1.0F;
    }

    /** Every log and leaf of the tree the struck log belongs to. */
    private static List<BlockPos> tree(Level level, BlockPos struck, BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return List.of();
        }
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(struck);
        seen.add(struck);
        boolean leaves = false;
        while (!queue.isEmpty() && logs.size() < TREE_LIMIT) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (!seen.add(next) || next.getY() - struck.getY() > TREE_HEIGHT
                                || Math.abs(next.getX() - struck.getX()) > TREE_RADIUS + 6
                                || Math.abs(next.getZ() - struck.getZ()) > TREE_RADIUS + 6) {
                            continue;
                        }
                        BlockState found = level.getBlockState(next);
                        if (found.is(BlockTags.LOGS)) {
                            logs.add(next);
                            queue.add(next);
                        } else if (found.is(BlockTags.LEAVES)) {
                            leaves = true;          // a tree has leaves; a built wall of logs does not
                        }
                    }
                }
            }
        }
        logs.remove(struck);
        return leaves ? logs : List.of();
    }

    /** The plants around a struck plant. */
    private static List<BlockPos> plants(Level level, BlockPos struck, BlockState state) {
        if (!ArmoryToolItem.isPlant(state)) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = struck.offset(dx, dy, dz);
                    if (!pos.equals(struck) && ArmoryToolItem.isPlant(level.getBlockState(pos))) {
                        out.add(pos);
                    }
                }
            }
        }
        return out;
    }
}
