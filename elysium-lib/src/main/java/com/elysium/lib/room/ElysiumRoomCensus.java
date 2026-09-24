package com.elysium.lib.room;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * What one enclosed volume turned out to contain.
 *
 * <h2>Why a census and not a list of blocks</h2>
 *
 * Because the question every room kind asks is "how many of <em>this kind</em>
 * of block are in here", and the kinds are declared as tags. Counting them
 * during the scan means the scan walks the volume once and a room kind never
 * has to; holding the block states instead would mean every kind re-walking a
 * few hundred states to ask its own question, and the whole point of doing this
 * at most once every two seconds is that it is not free.
 *
 * <h2>Why "sealed" is a flag rather than an absence of leaks</h2>
 *
 * The scan gives up at {@link ElysiumRoomScan#MAX_VOLUME} and reports
 * {@code sealed = false}. That is the honest answer for the overwhelmingly
 * common case — a player standing outdoors, where the air is the whole world —
 * and it is also the right answer for a cavern too big to be a room. A room is
 * a volume somebody bounded on purpose, and nobody bounds five hundred blocks
 * of air by accident.
 *
 * @param volume   air blocks reachable from where the scan started
 * @param sealed   whether the fill closed rather than running out of budget
 * @param boundary how many blocks touching that air carry each interesting tag
 */
public record ElysiumRoomCensus(int volume, boolean sealed,
                                Map<TagKey<Block>, Integer> boundary) {

    /** How many blocks touching the interior carry this tag. */
    public int count(TagKey<Block> tag) {
        Integer found = boundary.get(tag);
        return found == null ? 0 : found;
    }

    /** Nothing at all: what an unsealed or unscannable position reports. */
    public static ElysiumRoomCensus open() {
        return new ElysiumRoomCensus(0, false, Map.of());
    }
}
