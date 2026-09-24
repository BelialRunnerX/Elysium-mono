package com.elysium.lib.room;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazards;
import com.elysium.lib.hazard.ElysiumResistance;

import java.util.HashMap;
import java.util.Map;

/**
 * §8.5's rooms, executed without a world.
 *
 * <h2>What is worth running here</h2>
 *
 * Not the flood fill — that needs a level with a building in it, and mocking
 * one would be testing the mock. What is pure, and what decides how the feature
 * <em>feels</em>, is everything downstream of the census:
 *
 * <ul>
 *   <li>the minimum function of each room: what a volume has to contain before
 *       it counts, checked one requirement short in every direction;</li>
 *   <li>which room a volume is when it satisfies several, because "one volume
 *       is one room" is the rule that stops a player quietly collecting
 *       them;</li>
 *   <li>the shares, against the budget HazardHarness reserved for a treated
 *       room before any of this existed;</li>
 *   <li>and that no room, alone or stacked with anything else short of the two
 *       named states, amounts to immunity.</li>
 * </ul>
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/rh elysium-lib/tools/RoomHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/rh" \
 *         com.elysium.lib.room.RoomHarness
 */
public final class RoomHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static String pct(float share) {
        return String.format("%.2f%%", share * 100.0F);
    }

    /** A census that satisfies exactly one room, with room to spare. */
    private static ElysiumRoomCensus exactly(ElysiumRoom room, int volume) {
        Map<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>, Integer>
                boundary = new HashMap<>(room.getRequirements());
        return new ElysiumRoomCensus(volume, true, boundary);
    }

    /** Every room's minimum function is genuinely its minimum. */
    private static void minimumFunctionIsMinimal() {
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            String name = room.getId().getPath();
            int volume = room.getMinimumVolume();

            check(room.matches(exactly(room, volume)),
                    name + " does not match a volume that meets every one of its "
                            + "own requirements exactly, so it can never be built");

            // One block of air short.
            check(!room.matches(exactly(room, volume - 1)),
                    name + " matches a volume of " + (volume - 1) + ", one short "
                            + "of the " + volume + " it asks for");

            // One of each requirement short, in turn.
            for (var requirement : room.getRequirements().entrySet()) {
                Map<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>, Integer>
                        short_ = new HashMap<>(room.getRequirements());
                short_.put(requirement.getKey(), requirement.getValue() - 1);
                check(!room.matches(new ElysiumRoomCensus(volume, true, short_)),
                        name + " still matches with one "
                                + requirement.getKey().location() + " missing, so "
                                + "that requirement is decoration");
            }

            // And an unsealed volume is never a room, however well appointed.
            Map<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>, Integer>
                    generous = new HashMap<>();
            room.getRequirements().forEach((tag, count) -> generous.put(tag, count * 10));
            check(!room.matches(new ElysiumRoomCensus(volume * 4, false, generous)),
                    name + " matches an unsealed volume, so standing in an open "
                            + "field with enough blocks nearby is a room");

            check(!room.matches(null), name + " matches nothing at all");
            check(!room.matches(ElysiumRoomCensus.open()),
                    name + " matches the census a failed scan reports");
        }
        System.out.println("  minimum function: " + ElysiumRoom.REGISTRY.size()
                + " rooms, each checked one block and one fitting short");
    }

    /**
     * One volume is one room, and which one never depends on registration order.
     *
     * A bunker with a bed and a light in it satisfies the shelter as well. If
     * both applied, a player would be collecting rooms without knowing it and
     * the shares would stack into something nobody designed.
     */
    private static void oneVolumeIsOneRoom() {
        java.util.Set<Integer> ranks = new java.util.HashSet<>();
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            check(ranks.add(room.getRank()),
                    room.getId().getPath() + " shares a rank with another room, so "
                            + "which one a volume *is* depends on the order they "
                            + "happened to be registered in");
        }

        // A volume that satisfies everything at once is the highest-ranked room.
        Map<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>, Integer>
                everything = new HashMap<>();
        int biggest = 0;
        ElysiumRoom expected = null;
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            room.getRequirements().forEach((tag, count) ->
                    everything.merge(tag, count, Math::max));
            biggest = Math.max(biggest, room.getMinimumVolume());
            if (expected == null || room.getRank() > expected.getRank()) {
                expected = room;
            }
        }
        ElysiumRoom found = ElysiumRoom.classify(
                new ElysiumRoomCensus(biggest, true, everything));
        check(found == expected,
                "a volume satisfying every room was classified as "
                        + (found == null ? "nothing" : found.getId().getPath())
                        + " rather than the highest-ranked "
                        + expected.getId().getPath());

        check(ElysiumRoom.classify(ElysiumRoomCensus.open()) == null,
                "the open air is a room");
        check(ElysiumRoom.classify(null) == null, "nothing at all is a room");

        System.out.println("  one room: ranks are distinct, and a volume that is "
                + "every room is the best one — " + expected.getId().getPath());
    }

    /** The shares, against the budget reserved before any of this existed. */
    private static void roomsFitTheBudget() {
        float best = ElysiumRooms.bestShare();

        // A literal on one side on purpose. Deriving the limit from bestShare()
        // would move both sides together and the assertion would hold at any
        // value at all — the same trap the claim radius fell into.
        check(best <= 0.35F,
                "the best room removes " + pct(best) + ", above the 0.35 the "
                        + "hazard budget reserved for a sealed, treated room");
        check(best >= 0.20F,
                "the best room removes only " + pct(best) + ", so building one "
                        + "is not worth the blocks");

        // Every room, not only the best one. A tenth is the floor: below it a
        // source is not felt, and a room costs dozens of blocks and a decision
        // about where to build.
        //
        // Written as a per-room check after a deliberate test dropped the
        // shelter to 2% and walked straight through the budget assertion above
        // — that one reads bestShare(), so a single feeble room is invisible to
        // it however feeble it is.
        final float floor = 0.10F;
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            check(room.getShare() >= floor,
                    room.getId().getPath() + " removes " + pct(room.getShare())
                            + ", under the tenth a source has to be worth to be "
                            + "felt — it costs dozens of blocks and is not worth "
                            + "the trouble of building");
            check(room.getShare() > 0.0F,
                    room.getId().getPath() + " removes nothing, so it is a name "
                            + "with no effect behind it");
            check(room.getShare() < 1.0F,
                    room.getId().getPath() + " removes everything — immunity is "
                            + "the complete matching set and the filed claim, "
                            + "and a room that grants it is the claim without "
                            + "the Suspicion floor it costs");
        }

        // The cheapest room must be worth less than the dearest, or the ranks
        // reward nothing.
        ElysiumRoom cheapest = null;
        ElysiumRoom dearest = null;
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            if (cheapest == null || room.getRank() < cheapest.getRank()) {
                cheapest = room;
            }
            if (dearest == null || room.getRank() > dearest.getRank()) {
                dearest = room;
            }
        }
        check(cheapest.getShare() < dearest.getShare()
                        || cheapest.getAnswers().size() != dearest.getAnswers().size(),
                "the lowest-ranked room is worth as much as the highest against "
                        + "the same hazards, so there is no reason to build the "
                        + "harder one");

        System.out.println("  the budget: best room " + pct(best)
                + " against the 0.35 reserved, cheapest "
                + pct(cheapest.getShare()) + " — every one short of a seal");
    }

    /** A room answers what it says it answers, and nothing else. */
    private static void roomsAnswerWhatTheyClaim() {
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            String name = room.getId().getPath();
            for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
                float share = room.shareAgainst(hazard);
                boolean answers = room.getAnswers().isEmpty()
                        || room.getAnswers().contains(hazard);
                check(answers ? share == room.getShare() : share == 0.0F,
                        name + " removes " + pct(share) + " of "
                                + hazard.getId().getPath() + ", which is not what "
                                + "its own answer list says");
            }
            check(room.shareAgainst(null) == 0.0F,
                    name + " removes something from no hazard at all");

            // A room that answers one hazard has to be visibly worse overall
            // than one that answers five at the same share, or specialising
            // costs nothing.
            if (!room.getAnswers().isEmpty()) {
                check(room.getAnswers().size() < ElysiumHazard.REGISTRY.size(),
                        name + " lists every hazard individually rather than "
                                + "leaving the list empty, which means the same "
                                + "thing twice");
            }
        }
        System.out.println("  what they answer: every room against every hazard, "
                + ElysiumRoom.REGISTRY.size() * ElysiumHazard.REGISTRY.size()
                + " pairs");
    }

    /** A room plus everything else short of the seal is still not immunity. */
    private static void aRoomIsNotASeal() {
        final float trinket = 0.12F;
        final float ringPiece =
                (com.elysium.lib.hazard.ElysiumProtection.BASE_SHARE
                 + com.elysium.lib.hazard.ElysiumProtection.TIER_STEP * 12)
                * com.elysium.lib.hazard.ElysiumProtection.RING_FRACTION;

        float[] everything = {
            ringPiece, ringPiece, ringPiece, ringPiece,
            ElysiumRooms.bestShare(), trinket,
        };
        float total = ElysiumResistance.combine(everything);
        float through = 1.0F - total;

        check(through >= 0.10F,
                "the best room, a trinket and a full set of the ring answer "
                        + "combine to " + pct(total) + ", leaving "
                        + pct(through) + " — that is "
                        + Math.round(20.0F / ElysiumResistance.after(
                                ElysiumHazards.PRESSURE.getDamagePerSecond(),
                                everything))
                        + "s in the worst hazard for a player who has never "
                        + "assembled the set that immunity is supposed to mean");

        System.out.println("  short of the seal, indoors: " + pct(total)
                + " removed, " + pct(through) + " still lands");
    }

    /** The cache has to be bounded, or a walking player fills memory. */
    private static void theCacheIsBounded() {
        check(ElysiumRoomCache.CAPACITY > 0,
                "the room cache holds nothing, so every hazard tick rescans");
        check(ElysiumRoomCache.CAPACITY <= 4096,
                "the room cache holds " + ElysiumRoomCache.CAPACITY + " positions, "
                        + "which is a leak with a limit rather than a cache");
        check(ElysiumRoomCache.TTL > 0,
                "a cached room never expires, so breaking a wall never unseals "
                        + "the room it was holding up");
        check(ElysiumRoomCache.TTL <= 20 * 10,
                "a cached room is trusted for " + (ElysiumRoomCache.TTL / 20)
                        + "s, long enough that a player can dismantle a bunker "
                        + "and keep its protection");
        check(ElysiumRoomScan.MAX_VOLUME > 0,
                "the scan walks no air at all, so nothing is ever a room");
        check(ElysiumRoomScan.MAX_VOLUME >= 64,
                "the scan gives up after " + ElysiumRoomScan.MAX_VOLUME
                        + " blocks of air, which is smaller than rooms the kinds "
                        + "themselves ask for");
        for (ElysiumRoom room : ElysiumRoom.REGISTRY.all()) {
            check(room.getMinimumVolume() <= ElysiumRoomScan.MAX_VOLUME,
                    room.getId().getPath() + " needs " + room.getMinimumVolume()
                            + " blocks of air and the scan gives up at "
                            + ElysiumRoomScan.MAX_VOLUME + ", so it can never be "
                            + "detected however carefully it is built");
        }
        System.out.println("  the cache: " + ElysiumRoomCache.CAPACITY
                + " positions, " + (ElysiumRoomCache.TTL / 20) + "s each, over a "
                + "scan bounded at " + ElysiumRoomScan.MAX_VOLUME + " blocks");
    }

    public static void main(String[] args) {
        ElysiumHazards.bootstrap();
        ElysiumRooms.bootstrap();

        minimumFunctionIsMinimal();
        oneVolumeIsOneRoom();
        roomsFitTheBudget();
        roomsAnswerWhatTheyClaim();
        aRoomIsNotASeal();
        theCacheIsBounded();

        System.out.println("rooms: the minimum function of "
                + ElysiumRoom.REGISTRY.size() + " kinds, which one a volume is, "
                + "the shares and the cache's bounds — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("a room is a volume you cannot walk out of, and it is "
                + "worth a share — never a seal");
    }
}
