package com.elysium.comforts.event;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.block.GraveBlock;
import com.elysium.comforts.registry.ComfortBlocks;
import com.elysium.comforts.rules.ComfortRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catching what a player drops, before anything else can.
 *
 * <h2>Why it takes the drops rather than reading the inventory</h2>
 *
 * Because "what would have hit the floor" is exactly the right list, and it is
 * a list somebody else has already worked out. Reading the inventory means
 * knowing about every mod that adds a slot — curios, backpacks, whatever
 * arrives next — and getting one of them wrong means quietly losing it. The
 * drops are whatever the game and every other mod between them decided a
 * player was carrying, and taking that list wholesale is the only way to be
 * right about mods this module has never heard of.
 */
@EventBusSubscriber(modid = ElysiumComforts.MODID)
public final class GraveKeeper {

    private GraveKeeper() {
    }

    /**
     * Players who have just been buried and whose levels are therefore already
     * accounted for.
     *
     * <p>Two events, fired one after the other by the same death, and no
     * argument passes between them. A set is the smallest thing that can carry
     * "this one is handled" across the gap; it is emptied the moment it is
     * read, so nothing accumulates.
     */
    private static final Set<UUID> buried =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.getDrops().isEmpty()) {
            // Keep-inventory is on, or they were carrying nothing. Either way
            // there is nothing to bury and a grave would be an empty block in
            // somebody's floor.
            return;
        }
        List<ItemStack> carried = new ArrayList<>(event.getDrops().size());
        for (ItemEntity dropped : event.getDrops()) {
            if (!dropped.getItem().isEmpty()) {
                carried.add(dropped.getItem().copy());
            }
        }
        if (carried.isEmpty()) {
            return;
        }
        Level level = player.level();
        BlockPos where = restingPlace(level, player.blockPosition());
        BlockState was = level.getBlockState(where);
        if (!level.setBlockAndUpdate(where, ComfortBlocks.GRAVE.get().defaultBlockState()
                .setValue(GraveBlock.FACING, player.getDirection().getOpposite()))) {
            return;                       // it could not be placed; let them drop
        }
        if (!(level.getBlockEntity(where) instanceof GraveBlock.GraveBlockEntity grave)) {
            level.setBlockAndUpdate(where, was);
            return;
        }
        grave.fill(player, carried, player.totalExperience, level.getGameTime());
        event.getDrops().clear();
        event.setCanceled(true);
        buried.add(player.getUUID());
        if (player instanceof ServerPlayer told) {
            told.sendSystemMessage(Component.translatable("elysiumcomforts.grave.made",
                    String.valueOf(where.getX()), String.valueOf(where.getY()),
                    String.valueOf(where.getZ()), String.valueOf(carried.size())));
        }
    }

    /**
     * The levels, which the grave is already holding a share of.
     *
     * <p>Zeroed rather than reduced: what the grave keeps is decided in one
     * place, and leaving some on the floor as well would be paying twice out
     * of one purse.
     */
    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player player
                && buried.remove(player.getUUID())) {
            event.setDroppedExperience(0);
        }
    }

    /**
     * Somewhere the grave can actually sit.
     *
     * <p>The height is clamped into the world first — falling out of the
     * bottom of it is the commonest way to die with a full inventory, and a
     * grave at the height you died at would be a grave in the void. Then a
     * short search outwards for a block worth replacing, because dying inside
     * somebody's wall should not put a grave inside it.
     */
    public static BlockPos restingPlace(Level level, BlockPos died) {
        int y = ComfortRules.graveHeight(died.getY(), level.getMinBuildHeight(),
                level.getMaxBuildHeight() - 1);
        BlockPos start = new BlockPos(died.getX(), y, died.getZ());
        if (replaceable(level, start)) {
            return start;
        }
        for (int reach = 1; reach <= ComfortRules.LOOK_WITHIN; reach++) {
            for (Direction face : Direction.values()) {
                BlockPos next = start.relative(face, reach);
                if (level.isInWorldBounds(next) && replaceable(level, next)) {
                    return next;
                }
            }
        }
        return start;
    }

    private static boolean replaceable(Level level, BlockPos pos) {
        return level.isInWorldBounds(pos)
                && level.getBlockState(pos).canBeReplaced()
                && level.getBlockState(pos).getFluidState().isEmpty();
    }
}
