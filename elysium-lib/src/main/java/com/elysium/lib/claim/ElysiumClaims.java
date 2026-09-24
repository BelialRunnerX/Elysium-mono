package com.elysium.lib.claim;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
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
 * Every claim in one dimension, and the only thing allowed to say whether one
 * exists.
 *
 * <h2>Why this is level data and not the block entity</h2>
 *
 * The obvious home for a claim is the Registry Beacon's own block entity: it is
 * the beacon, the beacon is the claim, and destroying one destroys the other
 * with no bookkeeping at all. That is genuinely the better model for
 * <em>everything except the one question the claim exists to answer</em>.
 *
 * The question is: <b>what is this player's Suspicion floor?</b> It is asked on
 * the decay tick, wherever that player currently is — halfway across the world,
 * or in another dimension — and it must be answered without loading the
 * beacon's chunk. A block entity cannot answer a question asked while it is not
 * loaded, and a system that answers "no floor" when the chunk happens to be
 * unloaded would let a player shed their file by walking away from their own
 * base. That is the whole mechanic, failing silently.
 *
 * So the authority is here, in per-dimension {@link SavedData}, which is loaded
 * for as long as the dimension is. The block entity holds nothing but its own
 * position and asks this.
 *
 * <h2>The one staleness this can have, and its bound</h2>
 *
 * A claim is added when a beacon is placed and removed when it is broken, both
 * of which run on the server with this data loaded — so the two never disagree
 * through ordinary play, online or offline, and a beacon broken while its owner
 * is logged out strikes the claim correctly.
 *
 * The exception is a beacon that stops existing without its block being broken:
 * an external world editor, a chunk file deleted, a backup rolled back. Then a
 * claim is recorded whose beacon is gone, and nothing here can detect it,
 * because detecting it means loading the chunk — which is exactly what this
 * class exists not to do.
 *
 * The bound on that is {@link #confirm}: a loaded beacon re-asserts its claim
 * on a slow tick, and a claim that a beacon <em>contradicts</em> is corrected
 * immediately. So the failure survives only while nobody visits, and a single
 * visit either confirms it or ends it. That is as far as this can honestly be
 * taken, and the alternative — periodically force-loading every claimed chunk
 * to check — costs more than the problem.
 */
public final class ElysiumClaims extends SavedData {

    /** The file this lives in, under each dimension's data directory. */
    public static final String FILE = "elysium_claims";

    private static final Codec<List<ElysiumClaim>> LIST_CODEC =
            ElysiumClaim.CODEC.listOf();

    /** Keyed by anchor: one beacon, one claim, and a beacon is somewhere. */
    private final Map<BlockPos, ElysiumClaim> claims = new HashMap<>();

    private ElysiumClaims() {
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    public static SavedData.Factory<ElysiumClaims> factory() {
        return new SavedData.Factory<>(ElysiumClaims::new, ElysiumClaims::load,
                DataFixTypes.LEVEL);
    }

    private static ElysiumClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        ElysiumClaims data = new ElysiumClaims();
        // A claims file that will not parse must not take the world with it.
        // Losing the claims costs a player their Suspicion floors and their
        // respawn anchors, which is bad; refusing to load the dimension is
        // worse, and a decoding failure here is most likely a mod being
        // uninstalled rather than corruption.
        LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("claims"))
                .resultOrPartial(problem -> {})
                .ifPresent(list -> {
                    for (ElysiumClaim claim : list) {
                        data.claims.put(claim.anchor(), claim);
                    }
                });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, new ArrayList<>(claims.values()))
                .resultOrPartial(problem -> {})
                .ifPresent(encoded -> tag.put("claims", (Tag) encoded));
        return tag;
    }

    public static ElysiumClaims of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    // ------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------

    /** The claim containing this position in this dimension, or null. */
    @Nullable
    public ElysiumClaim at(BlockPos pos) {
        for (ElysiumClaim claim : claims.values()) {
            if (claim.contains(pos)) {
                return claim;
            }
        }
        return null;
    }

    /** The claim anchored exactly here, or null. */
    @Nullable
    public ElysiumClaim anchored(BlockPos anchor) {
        return claims.get(anchor);
    }

    public List<ElysiumClaim> all() {
        return new ArrayList<>(claims.values());
    }

    public int size() {
        return claims.size();
    }

    // ------------------------------------------------------------------
    // Changing
    // ------------------------------------------------------------------

    /**
     * Records a new claim, or returns the existing one if a beacon is already
     * anchored here.
     *
     * Returning the existing claim rather than replacing it is what makes a
     * beacon's chunk reloading harmless: the block entity calls this on load
     * and gets back the claim it already had, structure count intact, instead
     * of resetting its owner's file to zero every time they walk away and come
     * back.
     */
    public ElysiumClaim stake(Player owner, BlockPos anchor, int radius) {
        ElysiumClaim existing = claims.get(anchor);
        if (existing != null) {
            return existing;
        }
        ElysiumClaim claim = ElysiumClaim.staked(owner.getUUID(),
                owner.getGameProfile().getName(), anchor, radius, 0);
        claims.put(anchor, claim);
        setDirty();
        return claim;
    }

    /**
     * Grow a claim by one ascension tier.
     *
     * The radius and the Suspicion floor both move; {@link ElysiumClaim#ascended}
     * owns that arithmetic so there is one place it happens.
     *
     * @return the grown claim, or null when nothing is anchored here
     */
    @Nullable
    public ElysiumClaim ascend(BlockPos anchor) {
        ElysiumClaim existing = claims.get(anchor);
        if (existing == null) {
            return null;
        }
        ElysiumClaim grown = existing.ascended();
        claims.put(anchor, grown);
        setDirty();
        return grown;
    }

    /**
     * The beacon is gone: the claim is struck.
     *
     * §14.3's failure row and §E.1's invariant are the same sentence read twice
     * — <blockquote>beacon destruction releases claim but does not erase
     * constructed world.</blockquote> Nothing here touches a block. The
     * fortress stands; it is simply nobody's.
     *
     * @return the claim that was struck, or null if there was none
     */
    @Nullable
    public ElysiumClaim strike(BlockPos anchor) {
        ElysiumClaim removed = claims.remove(anchor);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Records blocks placed inside a claim. */
    public void grew(BlockPos anchor, int by) {
        ElysiumClaim claim = claims.get(anchor);
        if (claim == null || by <= 0) {
            return;
        }
        // Dirty on every growth, not only when the floor moves. The count is
        // what is saved, and a count lost to an unsaved dimension is a floor
        // lost later — the square root means thousands of placements can sit
        // between one floor step and the next.
        claims.put(anchor, claim.grown(by));
        setDirty();
    }

    /**
     * A loaded beacon says it is still here.
     *
     * The correction path described in the class documentation. A beacon that
     * finds no claim at its own position re-stakes one; anything else is left
     * alone.
     *
     * @return the claim this beacon speaks for
     */
    public ElysiumClaim confirm(Player owner, BlockPos anchor, int radius) {
        return stake(owner, anchor, radius);
    }

    // ------------------------------------------------------------------
    // Across dimensions
    // ------------------------------------------------------------------

    /**
     * The highest floor this player holds anywhere.
     *
     * Across every dimension, because the mod's Suspicion is one meter. The
     * specification scopes Suspicion per star system (§14.1), and a dimension
     * is the closest thing Minecraft has to one — but the meter this is
     * enforcing is global, so taking the maximum is the only reading that does
     * not quietly discard claims. It also says something true: the Empire files
     * you under your most conspicuous holding, not your nearest one.
     *
     * Walks every loaded dimension. That is a handful of small maps, on the
     * decay tick, which is once every two minutes per player.
     */
    public static int floorFor(MinecraftServer server, UUID owner) {
        int highest = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (ElysiumClaim claim : of(level).claims.values()) {
                if (claim.owner().equals(owner)) {
                    highest = Math.max(highest, claim.floor());
                }
            }
        }
        return highest;
    }

    /** The same, for a player who may not be on a server at all. */
    public static int floorFor(Player player) {
        MinecraftServer server = player.level().getServer();
        return server == null ? 0 : floorFor(server, player.getUUID());
    }
}
