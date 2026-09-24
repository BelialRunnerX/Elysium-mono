package com.elysium.lib.room;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A kind of room: what makes a volume one, and what standing in it is worth.
 *
 * <h2>§8.5, and why it is a registry rather than an enum</h2>
 *
 * The specification lists nine room types and says a room grants its bonus when
 * its <em>minimum function</em> is present — a smeltery is a smeltery because
 * there are furnaces in it, not because somebody placed a "smeltery" block.
 * That is a rule about contents, and contents are the one thing a content mod
 * is going to want to add to: a solarpunk reactor hall and an Imperial control
 * room are the same mechanism with a different list.
 *
 * So the requirements are block <em>tags</em>, and the kinds are registered.
 * A mod that wants its generator to count toward a reactor room adds it to the
 * tag; a mod that wants a room this library never imagined registers one.
 *
 * <h2>One volume is one room</h2>
 *
 * A bunker with a bed in it also satisfies the shelter's requirements, and if
 * both applied a player would be quietly collecting rooms. {@link #getRank}
 * settles it: the highest-ranked kind a volume satisfies is what the volume
 * <em>is</em>, and the player is told which one. A room a player can name is a
 * room they can improve on purpose.
 *
 * <h2>What a room is worth, and the one thing it is never worth</h2>
 *
 * A share against a hazard, and nothing else. Not a stat, not an attribute, not
 * a mob effect — because the moment a room grants combat power, the correct
 * play is to build nine of them and stand in each before a fight, and the base
 * stops being a place you live and becomes a pre-fight checklist.
 *
 * The share is a source in {@link com.elysium.lib.hazard.ElysiumResistance}'s
 * product like any other. A room never seals: the two named immunity states are
 * a complete matching armour set and a filed claim, and a third arriving
 * through architecture would make both of them arbitrary.
 */
public final class ElysiumRoom {

    public static final ElysiumRegistry<ElysiumRoom> REGISTRY =
            new ElysiumRegistry<>("room");

    private final String translationKey;
    private final int rank;
    private final int minimumVolume;
    private final Map<TagKey<Block>, Integer> requires;
    private final float share;
    private final Set<ElysiumHazard> answers;

    private ElysiumRoom(String translationKey, int rank, int minimumVolume,
                        Map<TagKey<Block>, Integer> requires, float share,
                        Set<ElysiumHazard> answers) {
        this.translationKey = translationKey;
        this.rank = rank;
        this.minimumVolume = minimumVolume;
        this.requires = Map.copyOf(requires);
        this.share = share;
        this.answers = Set.copyOf(answers);
    }

    /**
     * @param rank          higher wins when a volume satisfies several kinds
     * @param minimumVolume air blocks, so a cupboard is not a bunker
     * @param requires      tag to how many blocks touching the interior must
     *                      carry it
     * @param share         what standing inside removes, as one source
     * @param answers       which hazards it answers; empty means all of them
     */
    public static ElysiumRoom register(ResourceLocation id, int rank,
                                       int minimumVolume,
                                       Map<TagKey<Block>, Integer> requires,
                                       float share,
                                       Set<ElysiumHazard> answers) {
        String bare = id.getNamespace().equals("elysium")
                || id.getNamespace().equals("elysiumlib")
                ? id.getPath()
                : id.getNamespace() + "." + id.getPath();
        return REGISTRY.register(id, new ElysiumRoom("elysium.room." + bare, rank,
                minimumVolume, requires, share, answers));
    }

    public int getRank() {
        return rank;
    }

    public int getMinimumVolume() {
        return minimumVolume;
    }

    public Map<TagKey<Block>, Integer> getRequirements() {
        return requires;
    }

    public float getShare() {
        return share;
    }

    public Set<ElysiumHazard> getAnswers() {
        return answers;
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    /** What the player is told the first time they stand in one. */
    public Component getDescription() {
        return Component.translatable(translationKey + ".desc");
    }

    /**
     * Whether a volume is this kind of room.
     *
     * Pure, and deliberately so — this is the whole of §8.5 in one function,
     * and RoomHarness executes it against censuses built by hand. In its only
     * other form it would need a world with a building in it before it could
     * say anything at all.
     */
    public boolean matches(ElysiumRoomCensus census) {
        if (census == null || !census.sealed() || census.volume() < minimumVolume) {
            return false;
        }
        for (Map.Entry<TagKey<Block>, Integer> requirement : requires.entrySet()) {
            if (census.count(requirement.getKey()) < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** What this room removes from one hazard, which is nothing for most of them. */
    public float shareAgainst(ElysiumHazard hazard) {
        if (hazard == null) {
            return 0.0F;
        }
        return answers.isEmpty() || answers.contains(hazard) ? share : 0.0F;
    }

    /**
     * Every tag any registered room asks about.
     *
     * The scan counts these and nothing else, so a tag nobody needs costs
     * nothing and a room kind registered by an add-on is counted from the next
     * scan onward without the scanner knowing it exists.
     */
    public static Set<TagKey<Block>> interestingTags() {
        Set<TagKey<Block>> tags = new HashSet<>();
        for (ElysiumRoom room : REGISTRY.all()) {
            tags.addAll(room.requires.keySet());
        }
        return tags;
    }

    /**
     * Which kind this volume is, or null if it is not a room.
     *
     * Highest rank wins, and ties are impossible by construction — see
     * RoomHarness, which fails on two kinds sharing a rank precisely so that
     * "which room am I in" never depends on registration order.
     */
    public static ElysiumRoom classify(ElysiumRoomCensus census) {
        ElysiumRoom best = null;
        for (ElysiumRoom room : REGISTRY.all()) {
            if (room.matches(census) && (best == null || room.rank > best.rank)) {
                best = room;
            }
        }
        return best;
    }

    /** A copy of the requirements with counts, for reports and validation. */
    public static Map<String, Integer> requirementReport(ElysiumRoom room) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<TagKey<Block>, Integer> entry : room.requires.entrySet()) {
            out.put(entry.getKey().location().toString(), entry.getValue());
        }
        return out;
    }

    @Override
    public String toString() {
        return "ElysiumRoom[" + translationKey + "]";
    }
}
