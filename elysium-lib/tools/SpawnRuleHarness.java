package com.elysium.lib.event;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Which spawns the distance rule governs, executed.
 *
 * <h2>Why the exemptions are the part worth running</h2>
 *
 * The distance itself is one comparison and hard to get wrong. The list of what
 * the rule applies to is a matrix of seven spawn types against six mob
 * categories, and every wrong cell is a quiet breakage somewhere else in the
 * game: a dungeon spawner that stops working, a farm with no animals, a
 * villager raid that never arrives. None of those looks like a spawn-distance
 * bug from the outside, so none of them would be traced back here.
 *
 * So the matrix is walked in full rather than spot-checked.
 */
public final class SpawnRuleHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Exactly one cell of the matrix is governed, and it is the right one. */
    private static void theExemptionMatrix() {
        int governed = 0;
        for (MobCategory category : MobCategory.values()) {
            for (MobSpawnType type : MobSpawnType.values()) {
                boolean held = ElysiumSpawnDistanceHandler.governed(category, type);
                boolean expected = category == MobCategory.MONSTER
                        && type == MobSpawnType.NATURAL;
                check(held == expected,
                        category + " + " + type + " is "
                                + (held ? "held back" : "allowed")
                                + " and should be "
                                + (expected ? "held back" : "allowed"));
                if (held) {
                    governed++;
                }
            }
        }
        check(governed == 1,
                governed + " combinations are governed, not the single one the "
                        + "rule is meant to cover");

        // The four that matter most, named, so a failure says what broke.
        check(!ElysiumSpawnDistanceHandler.governed(
                        MobCategory.MONSTER, MobSpawnType.SPAWNER),
                "a dungeon spawner is held to the player-distance rule, so every "
                        + "spawner and mob farm in the world stops working and "
                        + "nothing says why");
        check(!ElysiumSpawnDistanceHandler.governed(
                        MobCategory.MONSTER, MobSpawnType.STRUCTURE),
                "structure spawns are held back, so the mobs a structure was "
                        + "built around are missing from it");
        check(!ElysiumSpawnDistanceHandler.governed(
                        MobCategory.CREATURE, MobSpawnType.NATURAL),
                "passive animals are held back, so no cow ever spawns near a "
                        + "base and every farm in the game is quietly ruined");
        check(!ElysiumSpawnDistanceHandler.governed(
                        MobCategory.MONSTER, MobSpawnType.SPAWN_EGG),
                "a spawn egg is held to the distance rule, so the item does "
                        + "nothing in the hand of the player using it");

        System.out.println("  matrix: " + MobCategory.values().length
                + " categories x " + MobSpawnType.values().length
                + " spawn types, exactly one governed — natural monsters");
    }

    /** The distance itself: a real increase, and not an empty world. */
    private static void theDistance() {
        final double vanilla = 24.0D;
        check(ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE > vanilla,
                "the rule allows spawns at "
                        + ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE
                        + " blocks, no further than vanilla's " + vanilla
                        + " — it changes nothing");
        check(ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE <= 48.0D,
                "the rule pushes spawns out to "
                        + ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE
                        + " blocks, which empties the world around a player and "
                        + "puts every mob farm out of render distance");

        // The squared constant has to match the plain one, because the check is
        // written against the square and read by people against the distance.
        check(Math.abs(ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE_SQUARED
                        - ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE
                        * ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE) < 1e-9,
                "the squared distance is not the square of the distance, so the "
                        + "documented radius and the enforced one differ");

        // The boundary, from both sides.
        double d = ElysiumSpawnDistanceHandler.MINIMUM_DISTANCE;
        check(ElysiumSpawnDistanceHandler.farEnough(d * d),
                "a spawn exactly at the radius is refused, so the documented "
                        + "distance is off by one");
        check(!ElysiumSpawnDistanceHandler.farEnough((d - 0.01) * (d - 0.01)),
                "a spawn just inside the radius is allowed");
        check(ElysiumSpawnDistanceHandler.farEnough((d + 0.01) * (d + 0.01)),
                "a spawn just outside the radius is refused");
        check(!ElysiumSpawnDistanceHandler.farEnough(0.0D),
                "a spawn on top of the player is allowed");

        System.out.println("  distance: " + d + " blocks against vanilla's "
                + vanilla + " — about "
                + Math.round((d - vanilla) / 4.3) + " extra seconds of walking "
                + "before it reaches you");
    }

    public static void main(String[] args) {
        theExemptionMatrix();
        theDistance();

        System.out.println("spawn rule: the exemption matrix and the radius — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("natural monsters only, and only the background cycle");
    }
}
