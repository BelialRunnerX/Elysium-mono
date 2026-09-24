package com.elysium.lib.room;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazards;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Set;

/**
 * The rooms of §8.5 that this library ships, and what each is worth.
 *
 * <h2>Three, and not nine</h2>
 *
 * The specification names nine room types. Three are here, and the reason is
 * the same one that keeps the boon vocabulary to a single item: a room's bonus
 * has to be something a player can feel and a designer can defend. Shelter,
 * Bunker and Greenhouse each answer a hazard, which is a thing the mod already
 * has a number for and a player already has a reason to want.
 *
 * The other six — Workshop, Smeltery, Med bay, Armory, Control room, Reactor
 * room — are in the catalogue under A7, "environmental engineering projects",
 * because that is what they are: §10.4's drainage, thermal management and
 * corrosion treatment are rooms, and building them as rooms means they arrive
 * on this engine rather than beside it. Shipping six of them today would have
 * meant inventing six bonuses in an afternoon, which is how a feature ends up
 * with a Workshop that grants Haste because nothing better came to mind.
 *
 * <h2>The shares, and the budget they come out of</h2>
 *
 * HazardHarness reserved 0.35 for "a sealed, treated room" before any of this
 * existed, alongside the meal and the trinket, so that the feature filling the
 * slot could not quietly ship at 0.8 and delete the hazard layer between them.
 * The Bunker spends exactly that. The Shelter is worth less because it is what
 * a player has on their second night, and the Greenhouse spends the full share
 * against one hazard only.
 *
 * <h2>Why a room is never immunity</h2>
 *
 * Because a filed claim already is, and it costs a permanent Suspicion floor
 * and makes its owner a siege target. A room that also granted immunity would
 * be the claim without the price, and nobody would ever file one.
 */
public final class ElysiumRooms {

    private ElysiumRooms() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, id(path));
    }

    /** Anything that emits light and was put there on purpose. */
    public static final TagKey<Block> LIGHT = tag("room/light");

    /** What a bunker's shell may be made of: dense, opaque, deliberate. */
    public static final TagKey<Block> SHIELDING = tag("room/shielding");

    /** Soil a crop will grow in. */
    public static final TagKey<Block> GROWING = tag("room/growing");

    /** Something to treat a wound with: a brewing stand, a cauldron of water. */
    public static final TagKey<Block> MEDICAL = tag("room/medical");

    /**
     * The cheapest room there is: somewhere to sleep with a light on.
     *
     * A bed and a lamp inside a closed volume is what a player builds on their
     * second night without being told to, which is the point — the first room
     * in the game should be one somebody has already built by accident.
     */
    public static final ElysiumRoom SHELTER = ElysiumRoom.register(
            id("shelter"), 10, 27,
            Map.of(BlockTags.BEDS, 1, LIGHT, 1),
            0.20F, Set.of());

    /**
     * A shelter with something to treat a wound with. §8.5's Med bay, and H8's
     * cure: wounds mend four times as fast inside one.
     *
     * Deliberately a shelter plus one block, so the Med bay is the first
     * upgrade a player makes to the room they already sleep in rather than a
     * new building. Ranked above the shelter so a bedroom with a brewing stand
     * in it is a Med bay; below the greenhouse and bunker, which are larger
     * commitments and keep their own answers. It answers no hazard and takes
     * the shelter's share: it is a shelter too.
     */
    public static final ElysiumRoom MED_BAY = ElysiumRoom.register(
            id("med_bay"), 15, 27,
            Map.of(BlockTags.BEDS, 1, LIGHT, 1, MEDICAL, 1),
            0.20F, Set.of());

    /**
     * Glass, soil and things growing in it.
     *
     * Answers Corrosive and nothing else, because that is what a greenhouse is
     * for: §10.2's engineered counter to corrosion is filtration, and a room
     * full of growing things scrubbing the air is the version of that a player
     * can build out of blocks they already have.
     */
    public static final ElysiumRoom GREENHOUSE = ElysiumRoom.register(
            id("greenhouse"), 20, 27,
            Map.of(GROWING, 16),
            0.35F, Set.of(ElysiumHazards.CORROSIVE));

    /**
     * The built answer: a closed volume with a shell of dense stone.
     *
     * Spends the whole of the budget reserved for a treated room, and asks for
     * the most: sixty-four blocks of shielding around sixty-four blocks of air
     * is a deliberate structure rather than a cellar somebody happened to dig.
     * It outranks the others, so a bunker with a bed in it is a bunker.
     */
    public static final ElysiumRoom BUNKER = ElysiumRoom.register(
            id("bunker"), 30, 64,
            Map.of(SHIELDING, 64),
            0.35F, Set.of());

    /** The largest share any room grants. What the budget is measured against. */
    public static float bestShare() {
        float best = 0.0F;
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            best = Math.max(best, room.getShare());
        }
        return best;
    }

    /** What this room removes from this hazard. Nothing, for most pairs. */
    public static float shareAgainst(ElysiumRoom room, ElysiumHazard hazard) {
        return room == null ? 0.0F : room.shareAgainst(hazard);
    }

    /** Touching this class registers all four. Called from the library's setup. */
    public static void bootstrap() {
        // The static initialisers above are the registration.
    }
}
