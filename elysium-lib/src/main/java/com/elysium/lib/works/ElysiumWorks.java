package com.elysium.lib.works;

import com.elysium.lib.claim.ElysiumClaim;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Every unregistered works in one dimension.
 *
 * Level data rather than anything on a player, for the reason a works is keyed
 * by place: the Empire finds a factory, not a person. Whoever places the block
 * that tips a site over is the one it is noticed against — the person standing
 * at the furnace bank when the survey flies over.
 *
 * Sites that have decayed to nothing are dropped whenever they are touched and
 * on save, so a world explored for a hundred hours does not accumulate a
 * record of every campfire it ever had.
 */
public final class ElysiumWorks extends SavedData {

    public static final String FILE = "elysium_works";

    private static final Codec<List<ElysiumSite>> LIST_CODEC =
            ElysiumSite.CODEC.listOf();

    private final Map<Long, ElysiumSite> sites = new HashMap<>();

    private ElysiumWorks() {
    }

    public static SavedData.Factory<ElysiumWorks> factory() {
        return new SavedData.Factory<>(ElysiumWorks::new, ElysiumWorks::load,
                DataFixTypes.LEVEL);
    }

    private static ElysiumWorks load(CompoundTag tag, HolderLookup.Provider registries) {
        ElysiumWorks data = new ElysiumWorks();
        // A file that will not parse costs a few sites their weight. Refusing
        // the dimension over it would cost the world.
        LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("sites"))
                .resultOrPartial(problem -> {})
                .ifPresent(list -> {
                    for (ElysiumSite site : list) {
                        data.sites.put(site.key(), site);
                    }
                });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        List<ElysiumSite> live = new ArrayList<>();
        for (ElysiumSite site : sites.values()) {
            if (site.weight() > 0) {
                live.add(site);
            }
        }
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, live)
                .resultOrPartial(problem -> {})
                .ifPresent(encoded -> tag.put("sites", (Tag) encoded));
        return tag;
    }

    public static ElysiumWorks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    // ------------------------------------------------------------------

    /** The site at this position as it stands now. Never null; an empty cell is a quiet site. */
    public ElysiumSite siteAt(BlockPos pos, long now) {
        int cx = ElysiumConspicuity.cellOf(pos.getX());
        int cz = ElysiumConspicuity.cellOf(pos.getZ());
        ElysiumSite stored = sites.get(ElysiumSite.key(cx, cz));
        return stored == null ? ElysiumSite.quiet(cx, cz, now) : stored.at(now);
    }

    /**
     * Records a placement of this weight. The rule is
     * {@link ElysiumSite#placed}; this only stores what it returns.
     */
    public ElysiumSite.Placed built(BlockPos pos, int weight, long now) {
        ElysiumSite.Placed result = siteAt(pos, now).placed(weight, now);
        if (weight > 0) {
            sites.put(result.site().key(), result.site());
            setDirty();
        }
        return result;
    }

    /**
     * A beacon has been planted: every works it covers is now on the record.
     *
     * This is the base lifecycle. A player builds wild, is noticed, and then
     * decides to register — and registering is an amnesty for the works in
     * exchange for the permanent floor. The claim still opens its file at
     * zero structures, as {@code ElysiumClaim} requires; what the claim takes
     * over is the watching, not the history.
     *
     * A site is covered when its centre is inside the claim, so a claim
     * clears the works it is actually standing on and not the neighbour's
     * factory one cell over whose corner it happens to clip.
     *
     * @return how many sites went quiet
     */
    public int registered(ElysiumClaim claim) {
        int cleared = 0;
        Iterator<ElysiumSite> each = sites.values().iterator();
        while (each.hasNext()) {
            ElysiumSite site = each.next();
            if (claim.contains(new BlockPos(site.centreX(), 0, site.centreZ()))) {
                each.remove();
                cleared++;
            }
        }
        if (cleared > 0) {
            setDirty();
        }
        return cleared;
    }

    public int size() {
        return sites.size();
    }
}
