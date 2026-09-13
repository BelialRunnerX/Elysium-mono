package com.elysium.lib.imperial;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every inspection post in one dimension.
 *
 * Deliberately the same shape as {@code ElysiumClaims} and deliberately not
 * sharing code with it. The two answer different questions — one is "whose
 * ground is this and what does it cost them", the other is "is anybody watching
 * this block right now" — and they agree about almost nothing except that both
 * need a position queryable from a block-break handler. A shared base would
 * have to grow a flag for every difference, which is the argument the
 * schedulers in elysium-mobs already make about themselves.
 *
 * <h2>Why level data rather than scanning for the block</h2>
 *
 * The question is asked on <em>every ore block a player breaks</em>. Scanning a
 * 48-block radius for a particular block is roughly nine hundred thousand block
 * lookups; a map with one entry per post in the dimension is a handful of
 * comparisons. There is usually zero or one.
 *
 * <h2>The library plants nothing</h2>
 *
 * It has no blocks and will not grow any. A content mod registers a
 * {@link Planter}, and if none does, the threshold is crossed and nothing
 * happens — which is the correct behaviour for a library installed on its own,
 * and the same arrangement rewards, prices, dispatch and the bestiary all use.
 */
public final class ElysiumInspections extends SavedData {

    public static final String FILE = "elysium_inspections";

    private static final Codec<List<ElysiumInspection>> LIST_CODEC =
            ElysiumInspection.CODEC.listOf();

    private final Map<BlockPos, ElysiumInspection> posts = new HashMap<>();

    private ElysiumInspections() {
    }

    // ------------------------------------------------------------------
    // Planting is somebody else's job
    // ------------------------------------------------------------------

    /**
     * Whatever knows how to put an inspection beacon into the world.
     *
     * @return the position it was placed at, or null if it could not be placed
     */
    @FunctionalInterface
    public interface Planter {
        @Nullable
        BlockPos plant(ServerLevel level, Player watching, BlockPos near);
    }

    @Nullable
    private static Planter planter;

    /**
     * Registers the one thing that can plant a post.
     *
     * Singular rather than a list, unlike every other provider registry here.
     * Rewards and prices take many providers because many mods can have an
     * opinion about what a relic is worth; an inspection beacon is one block,
     * and two mods each planting their own would put two of them at every
     * threshold crossing. The last registration wins and that is fine — a pack
     * with two mods supplying Imperial infrastructure has a content decision to
     * make, not a merge to perform.
     */
    public static void setPlanter(Planter newPlanter) {
        planter = newPlanter;
    }

    public static boolean canPlant() {
        return planter != null;
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    public static SavedData.Factory<ElysiumInspections> factory() {
        return new SavedData.Factory<>(ElysiumInspections::new,
                ElysiumInspections::load, DataFixTypes.LEVEL);
    }

    private static ElysiumInspections load(CompoundTag tag,
                                           HolderLookup.Provider registries) {
        ElysiumInspections data = new ElysiumInspections();
        LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("posts"))
                .resultOrPartial(problem -> {})
                .ifPresent(list -> {
                    for (ElysiumInspection post : list) {
                        data.posts.put(post.anchor(), post);
                    }
                });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, new ArrayList<>(posts.values()))
                .resultOrPartial(problem -> {})
                .ifPresent(encoded -> tag.put("posts", (Tag) encoded));
        return tag;
    }

    public static ElysiumInspections of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    // ------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------

    /**
     * The post reporting this position, or null.
     *
     * Lapsed posts are skipped rather than removed here. Removing during a read
     * would mutate the save from inside a block-break handler and mark the
     * dimension dirty on an ordinary mining swing; {@link #sweep} does the
     * cleanup on its own schedule, and until it runs a lapsed post simply does
     * not report.
     */
    @Nullable
    public ElysiumInspection reporting(BlockPos pos, long now) {
        for (ElysiumInspection post : posts.values()) {
            if (!post.hasLapsed(now) && post.covers(pos)) {
                return post;
            }
        }
        return null;
    }

    @Nullable
    public ElysiumInspection at(BlockPos anchor) {
        return posts.get(anchor);
    }

    /** How many posts are watching this player here, lapsed ones excluded. */
    public int watching(UUID player, long now) {
        int found = 0;
        for (ElysiumInspection post : posts.values()) {
            if (post.watching().equals(player) && !post.hasLapsed(now)) {
                found++;
            }
        }
        return found;
    }

    public int size() {
        return posts.size();
    }

    // ------------------------------------------------------------------
    // Changing
    // ------------------------------------------------------------------

    /**
     * Plants a post near this player, if anything knows how.
     *
     * @return the post, or null when there is no planter, nowhere to put one,
     *         or one is already watching
     */
    @Nullable
    public ElysiumInspection plant(ServerLevel level, Player watching, BlockPos near) {
        long now = level.getGameTime();
        if (planter == null || watching(watching.getUUID(), now) > 0) {
            // One at a time. Two posts covering the same mine would multiply
            // the rate twice for no extra decision — the player can only break
            // one at a time and the second would feel like the first did
            // nothing.
            return null;
        }
        BlockPos at = planter.plant(level, watching, near);
        if (at == null) {
            return null;
        }
        ElysiumInspection post = new ElysiumInspection(watching.getUUID(), at,
                now, now + ElysiumInspection.LIFETIME);
        posts.put(at, post);
        setDirty();
        return post;
    }

    /**
     * The post is gone — broken, or swept after lapsing.
     *
     * @return what was removed, or null if nothing was there
     */
    @Nullable
    public ElysiumInspection remove(BlockPos anchor) {
        ElysiumInspection removed = posts.remove(anchor);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /**
     * Forgets posts that have lapsed.
     *
     * The record is dropped; the block is left standing, because removing a
     * block from a chunk that may not be loaded is not something this can do
     * and is not worth forcing a chunk load for. A lapsed beacon is scenery the
     * player can break at no cost, which is a reasonable thing for an expired
     * piece of Imperial equipment to be.
     */
    public int sweep(long now) {
        List<BlockPos> lapsed = new ArrayList<>();
        for (ElysiumInspection post : posts.values()) {
            if (post.hasLapsed(now)) {
                lapsed.add(post.anchor());
            }
        }
        for (BlockPos anchor : lapsed) {
            posts.remove(anchor);
        }
        if (!lapsed.isEmpty()) {
            setDirty();
        }
        return lapsed.size();
    }
}
