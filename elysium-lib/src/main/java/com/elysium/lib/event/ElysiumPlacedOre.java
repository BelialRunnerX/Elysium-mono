package com.elysium.lib.event;

import com.elysium.lib.ElysiumHooks;
import com.elysium.lib.ElysiumLib;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Remembers which Elysium ore blocks a player put there.
 *
 * <h2>The hole this closes</h2>
 *
 * Breaking Elysium ore paid character experience and Suspicion. Silk Touch
 * returns the ore block itself, so one ore and one enchanted pickaxe was an
 * unbounded loop: break, place, break, place. Six experience a cycle against a
 * level track with no ceiling meant wall-time converted directly into stat
 * points, which is the one thing an uncapped progression system cannot allow.
 *
 * A vein that was placed by a player is not a vein that was found.
 *
 * <h2>Why a bounded LRU rather than saved block state</h2>
 *
 * The honest alternative is a persistent per-chunk data attachment, which is a
 * lot of machinery to stop something that only pays off while a player stands
 * there doing it. This is an in-memory set with a hard cap, evicting the least
 * recently touched position once it is full. Consequences, stated plainly:
 * marks are lost on restart, and a player who places {@value #CAPACITY} ore
 * blocks can push their own earliest one back out. Both leave the exploit
 * slower than mining normally, which is all it has to be.
 *
 * Positions are keyed by {@link BlockPos#asLong()} alone, so the same
 * coordinates in two dimensions share an entry. The worst case is one
 * naturally generated ore paying nothing, once.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumPlacedOre {

    private ElysiumPlacedOre() {
    }

    /** How many placements are remembered before the oldest is forgotten. */
    private static final int CAPACITY = 8192;

    private static final Set<Long> PLACED = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<Long, Boolean>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > CAPACITY;
                }
            }));

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            return;
        }
        if (isElysiumOre(event.getPlacedBlock().getBlock())) {
            PLACED.add(event.getPos().asLong());
        }
    }

    /**
     * Whether this position holds ore a player placed.
     *
     * Consumes the mark: once asked about, the position is forgotten, because
     * the caller is asking precisely because the block is being broken.
     */
    public static boolean wasPlaced(BlockPos pos) {
        return PLACED.remove(pos.asLong());
    }

    public static boolean isElysiumOre(Block block) {
        return ElysiumHooks.isOre(block);
    }
}
