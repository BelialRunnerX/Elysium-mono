package com.elysium.dungeons.level;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One live dungeon: where it is, what seed built it, and who is still inside.
 *
 * <h2>What a dungeon actually is</h2>
 *
 * A rectangle of blocks in the dungeon dimension, at a cell nobody else has
 * been given. Nothing is ever deleted to make a new one — the allocator simply
 * hands out the next cell, a thousand blocks away, and builds there. That is
 * the whole trick behind "a new dungeon every time":
 *
 * <ul>
 *   <li>Deleting region files while the server is running is not safe, and the
 *       chunk system will happily hand out stale copies of what you deleted.</li>
 *   <li>Clearing a volume and rebuilding in place means every entry waits on
 *       hundreds of thousands of block writes, and anything a player left on
 *       the floor is destroyed underneath them.</li>
 *   <li>Building somewhere new is a plain write into empty void chunks, which
 *       is fast, cannot corrupt anything, and leaves the old dungeon intact for
 *       as long as anyone is still standing in it.</li>
 * </ul>
 *
 * The abandoned dungeons are unloaded chunks in a void dimension, which cost
 * disk and nothing else. {@link DungeonInstances} sweeps them when there are
 * enough to be worth sweeping.
 *
 * <h2>The return address</h2>
 *
 * An instance remembers the portal it was opened from, in full — dimension and
 * position — because the way out has to lead back to the way in. A player who
 * arrives through a portal in the Nether must not be posted to the overworld.
 */
public final class DungeonInstance {

    /** Monotonic id, and the number the cell and the seed are both derived from. */
    private final long index;

    /** Where in the dungeon dimension this one was built. */
    private final BlockPos origin;

    /** The seed the layout and every room was rolled from. */
    private final long seed;

    /** The dimension the portal that opened this stands in. */
    private final ResourceKey<Level> returnDimension;

    /** Where to put a player who steps back out. */
    private final BlockPos returnPos;

    /** The portal frame this belongs to, so a second entry finds the same dungeon. */
    private final BlockPos portalAnchor;

    /**
     * Who is inside right now.
     *
     * The whole reroll rule hangs off this set being right: when it empties,
     * the instance is finished and the next entry through that portal builds a
     * new one. It is tracked by UUID rather than by player object so that a
     * disconnect inside the dungeon can be cleaned up without the player being
     * present to ask.
     */
    private final Set<UUID> occupants = new HashSet<>();

    /**
     * True once the last occupant has left.
     *
     * Kept as a flag rather than deleting the record outright, because the
     * blocks are still there and the sweeper needs to know which cells are
     * finished with.
     */
    private boolean retired;

    /**
     * The character level everything in this dungeon was built for.
     *
     * Recorded on the instance rather than worked out per room, because every
     * room has to agree: a dungeon whose entrance is level 12 and whose boss
     * room is level 1 is not a difficulty curve, it is two dungeons.
     *
     * Zero means "not recorded" - an instance saved by a build from before this
     * existed. Read that way it falls back to the old proximity rule rather
     * than becoming a dungeon built for level zero.
     */
    private int partyLevel;

    public DungeonInstance(long index, BlockPos origin, long seed,
                           ResourceKey<Level> returnDimension, BlockPos returnPos,
                           BlockPos portalAnchor) {
        this.index = index;
        this.origin = origin;
        this.seed = seed;
        this.returnDimension = returnDimension;
        this.returnPos = returnPos;
        this.portalAnchor = portalAnchor;
    }

    // ------------------------------------------------------------------
    // Occupancy
    // ------------------------------------------------------------------

    public void enter(UUID player) {
        occupants.add(player);
    }

    /**
     * @return true when that was the last one out, and this dungeon is done
     */
    public boolean leave(UUID player) {
        occupants.remove(player);
        if (occupants.isEmpty() && !retired) {
            retired = true;
            return true;
        }
        return false;
    }

    /** True when this player is one of the ones inside. */
    public boolean contains(UUID player) {
        return occupants.contains(player);
    }

    public boolean isEmpty() {
        return occupants.isEmpty();
    }

    public boolean isRetired() {
        return retired;
    }

    /**
     * Whether a player arriving now should join this dungeon or get a new one.
     *
     * A retired instance is never rejoined even if its blocks are still
     * standing — that is the rule the mod is built around, and softening it
     * ("rejoin if it was only a moment ago") would turn a clear promise into a
     * timing question.
     */
    public boolean acceptsEntry() {
        return !retired;
    }

    public int occupantCount() {
        return occupants.size();
    }

    // ------------------------------------------------------------------

    public long getIndex() {
        return index;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public long getSeed() {
        return seed;
    }

    public ResourceKey<Level> getReturnDimension() {
        return returnDimension;
    }

    public BlockPos getReturnPos() {
        return returnPos;
    }

    /** The level this dungeon's contents were built for; 0 when unrecorded. */
    public int getPartyLevel() {
        return partyLevel;
    }

    /**
     * Set once, when the dungeon is built.
     *
     * Not on re-entry: a level-40 player joining a friend's level-12 dungeon
     * would otherwise re-level a dungeon that is already standing, and the
     * blocks - including the boss already fighting someone - do not get rebuilt
     * to match.
     */
    public void setPartyLevel(int level) {
        this.partyLevel = Math.max(0, level);
    }

    public BlockPos getPortalAnchor() {
        return portalAnchor;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Index", index);
        tag.putLong("Origin", origin.asLong());
        tag.putLong("Seed", seed);
        tag.putString("ReturnDimension", returnDimension.location().toString());
        tag.putLong("ReturnPos", returnPos.asLong());
        tag.putLong("PortalAnchor", portalAnchor.asLong());
        tag.putBoolean("Retired", retired);
        tag.putInt("PartyLevel", partyLevel);

        // Occupants are deliberately NOT saved. A server restart means nobody
        // is inside any dungeon: every player is loaded fresh and re-enters
        // through a portal or not at all. Persisting the set would leave
        // instances permanently occupied by players who are no longer there,
        // and those instances would never retire — so the portal would never
        // reroll, which is the one thing this mod must always do.
        return tag;
    }

    public static DungeonInstance load(CompoundTag tag) {
        DungeonInstance instance = new DungeonInstance(
                tag.getLong("Index"),
                BlockPos.of(tag.getLong("Origin")),
                tag.getLong("Seed"),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(tag.getString("ReturnDimension"))),
                BlockPos.of(tag.getLong("ReturnPos")),
                BlockPos.of(tag.getLong("PortalAnchor")));
        instance.retired = tag.getBoolean("Retired");
        // Absent on an instance saved before dungeons recorded a level.
        // getInt returns 0 for a missing key, which is exactly the
        // "unrecorded" value, so nothing special is needed here.
        instance.partyLevel = tag.getInt("PartyLevel");
        return instance;
    }

    @Override
    public String toString() {
        return "dungeon #" + index + " at " + origin + " seed " + seed
                + (retired ? " (retired)" : " (" + occupants.size() + " inside)");
    }
}
