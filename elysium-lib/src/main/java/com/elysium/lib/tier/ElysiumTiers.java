package com.elysium.lib.tier;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A player's world tier, and the worlds that raise its floor.
 *
 * <h2>Why worlds are declared rather than listed</h2>
 *
 * The library cannot know what dimensions exist — that is the whole point of
 * it being the library. So a module that adds a world says what standing it
 * takes to be there, and this keeps the table. An undeclared dimension raises
 * nothing, which is the right answer for the overworld and for any world a mod
 * adds without an opinion.
 *
 * <p>The floor is remembered rather than re-derived: a player who has been to
 * Cinderveil and come home is still someone who has been to Cinderveil.
 */
public final class ElysiumTiers {

    private ElysiumTiers() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /** The highest world floor this player has ever stood on. */
    public static final Supplier<AttachmentType<Integer>> WORLD_FLOOR =
            ATTACHMENTS.register("world_floor", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT).copyOnDeath().build());

    private static final Map<ResourceLocation, Integer> WORLDS = new LinkedHashMap<>();

    /**
     * Declares what world tier a dimension stands at.
     *
     * Called by whoever adds the world. Declaring the same dimension twice
     * keeps the higher of the two, so two modules disagreeing about a shared
     * world cannot lower it.
     */
    public static void declareWorld(ResourceLocation dimension, int tier) {
        WORLDS.merge(dimension, Math.max(0, tier), Math::max);
    }

    /** The tier a dimension stands at; zero for one nobody has declared. */
    public static int worldTier(ResourceLocation dimension) {
        return WORLDS.getOrDefault(dimension, 0);
    }

    /** Every declared world, for a log line or a codex page. */
    public static Map<ResourceLocation, Integer> worlds() {
        return Map.copyOf(WORLDS);
    }

    /** The floor this player has earned by going places. */
    public static int floorOf(Player player) {
        return player.getData(WORLD_FLOOR.get());
    }

    /**
     * Raises the floor to at least this much, and never lowers it.
     *
     * The one place the floor is written. Going somewhere deep is how a floor
     * is usually earned, but it is not the only way a pack or an add-on might
     * want to earn one — a settlement, a rank, a contract with somebody who
     * matters — and every one of those wants the same sentence: at least this
     * much, from now on. Writing it once means the "never lowers it" half
     * cannot be forgotten by the second caller.
     *
     * @return true when the floor actually moved, so a caller can say so
     */
    public static boolean lift(Player player, int floor) {
        if (floor <= floorOf(player)) {
            return false;
        }
        player.setData(WORLD_FLOOR.get(), floor);
        return true;
    }

    /**
     * Raises the floor if this world is deeper than anywhere the player has
     * been. Returns true when it moved, so a caller can say so.
     */
    public static boolean visited(Player player, Level level) {
        return lift(player, worldTier(level.dimension().location()));
    }

    /** This player's world tier. */
    public static int of(Player player) {
        int worked = ElysiumWorldTier.of(player.getData(ElysiumCharacter.LEVEL.get()),
                floorOf(player));
        // Asked here rather than at each of the dozen places that want a tier:
        // a pack keeping progression somewhere else answers once, and every
        // system downstream gets the same answer.
        return com.elysium.lib.api.ElysiumEvents.fireWorldTier(player, worked);
    }

    /**
     * The tier that decides what a fight or a chest is worth to the party
     * present: the highest anyone here has reached.
     *
     * The alternative — an average — would have a high-tier player dragging
     * loot down for everyone they helped, which is the opposite of what
     * helping should do.
     */
    public static int of(Iterable<? extends Player> players) {
        int best = 0;
        for (Player player : players) {
            best = Math.max(best, of(player));
        }
        return best;
    }
}
