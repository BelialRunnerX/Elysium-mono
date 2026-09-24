package com.elysium.lib.medical;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import java.util.EnumSet;
import java.util.Set;

/**
 * Wounds, executed against every blow size and every combination of caps.
 *
 * <h2>What has to be true</h2>
 *
 * <ol>
 *   <li><b>No death spiral.</b> A player carrying every kind of wound at its
 *       cap is weakened, never crippled: fourteen of twenty health, and no
 *       attribute below sixty per cent.</li>
 *   <li><b>Lasting, not permanent.</b> Everything heals on its own inside half
 *       an hour, and nothing is a scratch that heals before it is noticed.</li>
 *   <li><b>The Med bay is worth building.</b> Strictly faster, and the worst
 *       load a player can carry is gone in minutes asleep in one.</li>
 *   <li><b>The huge case is right.</b> NaN, infinity, a year of elapsed time,
 *       a hundred wounds in a row.</li>
 * </ol>
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/mh elysium-lib/tools/MedicalHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/mh" \
 *         com.elysium.lib.medical.MedicalHarness
 */
public final class MedicalHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static final double BASE_HEALTH = 20.0D;
    private static final double HEALTH_FLOOR = 14.0D;
    private static final double SHARE_FLOOR = 0.60D;

    private static final long MINUTE = ElysiumMedicine.TICKS_PER_MINUTE;

    private static void noDeathSpiral() {
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            check(kind.penalty() < 0.0D, kind + " gives rather than takes");
            check(kind.stack() >= 1, kind + " can never be carried");
            check(kind.threshold() > 0.0F, kind + " wounds on a blow of nothing");
            // Past the cap nothing more is counted.
            check(ElysiumMedicine.penaltyFor(kind, 1000)
                            == ElysiumMedicine.penaltyFor(kind, kind.stack()),
                    kind + " keeps counting past its cap");
            check(ElysiumMedicine.penaltyFor(kind, -5) == 0.0D,
                    kind + " counts a negative number of wounds");

            double worst = ElysiumMedicine.penaltyFor(kind, kind.stack());
            if (kind == ElysiumWoundKind.LACERATION) {
                check(BASE_HEALTH + worst >= HEALTH_FLOOR,
                        "lacerations at their cap leave " + (BASE_HEALTH + worst)
                                + " health; the floor is " + HEALTH_FLOOR);
            } else {
                check(1.0D + worst >= SHARE_FLOOR,
                        kind + " at its cap leaves " + (1.0D + worst)
                                + " of the attribute; the floor is " + SHARE_FLOOR);
            }
        }

        // Every kind at its cap at once, which is the real worst case.
        ElysiumInjury worst = ElysiumInjury.NONE;
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            for (int i = 0; i < 100; i++) {
                worst = worst.wounded(kind);
            }
        }
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            check(worst.count(kind) == kind.stack(),
                    "a hundred " + kind + " wounds became " + worst.count(kind)
                            + "; the cap is " + kind.stack());
        }
        check(BASE_HEALTH + ElysiumMedicine.penaltyFor(ElysiumWoundKind.LACERATION,
                        worst.count(ElysiumWoundKind.LACERATION)) >= HEALTH_FLOOR,
                "the worst load a player can carry takes them under the health floor");
    }

    private static void chances() {
        // The cap itself, and not only obedience to it. Every check below
        // compares against MAX_CHANCE, so on their own they would pass with
        // the cap raised to certainty.
        check(ElysiumMedicine.MAX_CHANCE <= 0.5F,
                "a single blow can be more likely than not to leave a wound ("
                        + ElysiumMedicine.MAX_CHANCE + "); every heavy fight "
                        + "would end in a Med bay");

        float[] specials = {Float.NaN, Float.NEGATIVE_INFINITY, -100.0F, -0.0F,
                0.0F, Float.MIN_VALUE};
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            for (float nothing : specials) {
                check(ElysiumMedicine.chanceFor(kind, nothing) == 0.0F,
                        kind + " wounds on a blow of " + nothing);
            }
            check(ElysiumMedicine.chanceFor(kind, Float.POSITIVE_INFINITY)
                            == ElysiumMedicine.MAX_CHANCE,
                    kind + " on an infinite blow gave "
                            + ElysiumMedicine.chanceFor(kind, Float.POSITIVE_INFINITY));
            check(ElysiumMedicine.chanceFor(kind, Float.MAX_VALUE)
                            == ElysiumMedicine.MAX_CHANCE,
                    kind + " on the largest finite blow is not at the cap");
            check(ElysiumMedicine.chanceFor(kind, Math.nextDown(kind.threshold())) == 0.0F,
                    kind + " wounds just under its own threshold");
            check(ElysiumMedicine.chanceFor(kind, kind.threshold()) > 0.0F,
                    kind + " cannot wound at its own threshold");

            float last = 0.0F;
            for (float blow = 0.0F; blow <= 200.0F; blow += 0.25F) {
                float chance = ElysiumMedicine.chanceFor(kind, blow);
                check(chance >= 0.0F && chance <= ElysiumMedicine.MAX_CHANCE,
                        kind + " at " + blow + " gave a chance of " + chance);
                check(chance >= last, kind + " grew less likely at " + blow);
                last = chance;
            }
        }
        // A zombie's ordinary hit is 3 to 5 in Normal: a laceration should be
        // possible from one only at the top of that range.
        check(ElysiumMedicine.chanceFor(ElysiumWoundKind.LACERATION, 4.0F) == 0.0F,
                "an ordinary zombie hit can lacerate; every fight would leave a wound");
    }

    private static void lastingNotPermanent() {
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            int full = ElysiumMedicine.fullTicks(kind);
            check(full >= 10 * MINUTE,
                    kind + " heals in " + full / MINUTE + " minutes: a scratch, not a wound");
            check(full <= 30 * MINUTE,
                    kind + " takes " + full / MINUTE + " minutes: not lasting, permanent");

            ElysiumInjury one = ElysiumInjury.NONE.wounded(kind);
            check(one.recovered(full - 1L, ElysiumMedicine.RATE_NATURAL).count(kind) == 1,
                    kind + " healed early");
            check(one.recovered(full, ElysiumMedicine.RATE_NATURAL).isEmpty(),
                    kind + " did not heal in its own time");
        }
    }

    private static void medBayIsWorthBuilding() {
        int natural = ElysiumMedicine.rate(false, false);
        int bay = ElysiumMedicine.rate(true, false);
        int rest = ElysiumMedicine.rate(false, true);
        int both = ElysiumMedicine.rate(true, true);
        check(natural >= 1, "natural recovery is " + natural + "; wounds would never heal");
        check(bay > natural && rest > natural, "a Med bay or sleep is no faster");
        check(both > bay && both > rest, "sleeping in a Med bay is no better than either");

        // The worst load, asleep in a Med bay, gone within five minutes.
        ElysiumInjury worst = ElysiumInjury.NONE;
        for (ElysiumWoundKind kind : ElysiumWoundKind.values()) {
            for (int i = 0; i < kind.stack(); i++) {
                worst = worst.wounded(kind);
            }
        }
        long ticks = 0;
        ElysiumInjury now = worst;
        while (!now.isEmpty() && ticks < 60 * MINUTE) {
            now = now.recovered(ElysiumMedicine.TICK_INTERVAL, both);
            ticks += ElysiumMedicine.TICK_INTERVAL;
        }
        check(now.isEmpty() && ticks <= 5 * MINUTE,
                "the worst load took " + ticks / MINUTE + " minutes asleep in a Med bay");
    }

    private static void recoveryIsSane() {
        ElysiumInjury some = ElysiumInjury.NONE.wounded(ElysiumWoundKind.FRACTURE)
                .wounded(ElysiumWoundKind.BURN);
        check(some.recovered(0L, 8).equals(some), "no time changed an injury");
        check(some.recovered(-50L, 8).equals(some), "negative time reopened a wound");
        check(some.recovered(1000L, 0).equals(some), "a rate of zero healed something");
        check(some.recovered(1000L, -4).equals(some), "a negative rate did something");
        check(some.recovered(Long.MAX_VALUE, 8).isEmpty(),
                "a year of healing left a wound — the multiplication wrapped");
        check(ElysiumMedicine.healed(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE) == 0,
                "the largest heal overflowed");

        int last = Integer.MAX_VALUE;
        for (long t = 0; t <= 40 * MINUTE; t += 137) {
            int left = some.recovered(t, 1).longest();
            check(left >= 0, "a wound went negative at " + t);
            check(left <= last, "a wound grew at " + t);
            last = left;
        }

        // healedBy names exactly what went.
        ElysiumInjury after = some.recovered(
                (long) ElysiumMedicine.fullTicks(ElysiumWoundKind.BURN), 1);
        Set<ElysiumWoundKind> healed = some.healedBy(after);
        check(healed.equals(EnumSet.of(ElysiumWoundKind.BURN)),
                "healing the burn reported " + healed);
    }

    private static void atTheCapReopensTheMostHealed() {
        ElysiumWoundKind kind = ElysiumWoundKind.LACERATION;
        ElysiumInjury full = ElysiumInjury.NONE;
        for (int i = 0; i < kind.stack(); i++) {
            full = full.wounded(kind).recovered(MINUTE, 1);
        }
        // The first wound has now healed the most.
        int before = full.wounds().get(0).remaining();
        ElysiumInjury again = full.wounded(kind);
        check(again.count(kind) == kind.stack(), "a wound past the cap stacked");
        check(again.wounds().get(0).remaining() == ElysiumMedicine.fullTicks(kind)
                        && again.wounds().get(0).remaining() > before,
                "a wound past the cap did not reopen the most healed one");
    }

    private static void savesSurviveAVersionChange() {
        JsonArray saved = new JsonArray();
        JsonObject good = new JsonObject();
        good.addProperty("kind", "fracture");
        good.addProperty("remaining", 1234);
        JsonObject gone = new JsonObject();
        gone.addProperty("kind", "frostbite");        // a kind this build never had
        gone.addProperty("remaining", 999);
        saved.add(good);
        saved.add(gone);

        ElysiumInjury read = ElysiumInjury.CODEC.parse(JsonOps.INSTANCE, saved)
                .result().orElse(null);
        check(read != null, "one unreadable wound lost the whole injury");
        if (read != null) {
            check(read.count(ElysiumWoundKind.FRACTURE) == 1
                            && read.wounds().size() == 1
                            && read.wounds().get(0).remaining() == 1234,
                    "the readable wound did not survive beside an unreadable one: " + read);
        }

        ElysiumInjury round = ElysiumInjury.NONE.wounded(ElysiumWoundKind.BURN)
                .wounded(ElysiumWoundKind.CONCUSSION);
        JsonElement encoded = ElysiumInjury.CODEC.encodeStart(JsonOps.INSTANCE, round)
                .result().orElse(null);
        ElysiumInjury back = encoded == null ? null
                : ElysiumInjury.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElse(null);
        check(round.equals(back), "an injury did not survive a save and load");
    }

    public static void main(String[] args) {
        noDeathSpiral();
        chances();
        lastingNotPermanent();
        medBayIsWorthBuilding();
        recoveryIsSane();
        atTheCapReopensTheMostHealed();
        savesSurviveAVersionChange();

        if (failures > 0) {
            System.out.println(failures + " of " + checked + " checks failed");
            System.exit(1);
        }
        int bay = ElysiumMedicine.rate(true, true);
        System.out.println(checked + " checks: worst load leaves "
                + (int) (BASE_HEALTH + ElysiumMedicine.penaltyFor(ElysiumWoundKind.LACERATION,
                        ElysiumWoundKind.LACERATION.stack()))
                + " health, heals alone in at most 30 minutes, " + bay
                + "x asleep in a Med bay");
    }
}
