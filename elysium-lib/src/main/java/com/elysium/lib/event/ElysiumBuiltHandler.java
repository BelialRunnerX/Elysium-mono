package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.research.ElysiumDeed;
import com.elysium.lib.research.ElysiumResearch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Building a working machine for the first time, toward Engineering.
 *
 * <h2>Why a namespace prefix and not a list</h2>
 *
 * Engineering should be taught by every Elysium machine, and the machines live
 * in six different mods — including {@code elysiumsolarpunk}, which by design
 * depends on nothing Elysium and must not be made to. A list here would mean
 * this file importing every module, which is the dependency the whole
 * architecture exists to avoid; and a registry the modules pushed into would
 * mean solarpunk registering with a library it is not allowed to know about.
 *
 * A namespace prefix is neither. It is a string comparison against a block's
 * own id, so a solarpunk kiln counts without solarpunk containing one line
 * about research, and a seventh Elysium module counts on the day it ships.
 *
 * <h2>Why "has a block entity" is the filter</h2>
 *
 * The mod registers several hundred blocks and most of them are walls. The
 * honest distinction between a machine and a wall is not a tag somebody has to
 * remember to apply — it is whether the block <em>does</em> anything, and a
 * block that does something has a block entity. That is a fact about the block
 * rather than a fact about a list, so it cannot fall out of date.
 *
 * It is not perfect: a purely decorative block with an animated block entity
 * would count. That is a better failure than the alternative, where a machine
 * somebody forgot to list teaches nothing and nobody ever finds out.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumBuiltHandler {

    private ElysiumBuiltHandler() {
    }

    /** Every Elysium module's namespace begins with this. */
    public static final String NAMESPACE_PREFIX = "elysium";

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getLevel().isClientSide()) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (!isElysiumMachine(placed)) {
            return;
        }
        // Novel per block id, so a factory floor of twelve kilns teaches what
        // one kiln teaches. Building the twelfth is a decision about
        // throughput, not a thing that was learned.
        ElysiumResearch.credit(player, ElysiumDeed.BUILT_A_MACHINE,
                idOf(placed));
    }

    /** Whether a block is one of this mod family's working machines. */
    public static boolean isElysiumMachine(BlockState state) {
        return state.hasBlockEntity() && isElysium(idOf(state));
    }

    /** Whether an id belongs to one of the Elysium mods. */
    public static boolean isElysium(String id) {
        return id != null && id.startsWith(NAMESPACE_PREFIX + ":");
    }

    private static String idOf(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? null : key.toString();
    }
}
