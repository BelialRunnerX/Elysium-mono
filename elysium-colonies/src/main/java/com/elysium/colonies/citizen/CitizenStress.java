package com.elysium.colonies.citizen;

import com.elysium.colonies.charter.Charter;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.CharterRules;
import com.elysium.lib.stress.ElysiumStress;
import com.elysium.lib.stress.ElysiumStressor;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a citizen's life is like, in the library's vocabulary.
 *
 * <h2>Why read a colony rather than a brain</h2>
 *
 * The library already has a sensor that works out why a villager is unhappy,
 * and it reads the villager's brain: the home memory, the job-site memory, the
 * nearest hostile. A colony citizen has none of those — it is not a villager,
 * it is a {@code PathfinderMob} with a job object and a building — so the same
 * twelve stressors have to be read from a different set of facts.
 *
 * <p>The same twelve, though, and with the same weights. A homeless citizen
 * and a homeless villager are unhappy by exactly the same amount, which is
 * what makes the two halves of the mod one mod.
 *
 * <h2>The world's share</h2>
 *
 * Standing on a hostile world is a thirteenth thing, and it is not a stressor
 * with a fixed weight — how bad it is depends on the world and on how much of
 * the town is built. So it is added as a number afterwards, from
 * {@link CharterRules}, rather than pretended into the enum.
 */
public final class CitizenStress {

    private CitizenStress() {
    }

    /**
     * Below this share of their health, somebody is injured rather than hurt.
     *
     * Half, matching the library's own {@code ElysiumStressField}: the two
     * sensors answer the same question about the same number, so they must
     * answer it the same way.
     */
    public static final float INJURED = 0.5F;

    /**
     * People per building above which a town is crowded.
     *
     * Four. A settlement with one building per four people is a settlement
     * where most people are sharing; the alternative would be counting beds,
     * which is a number the host does not hand out.
     */
    public static final int PER_BUILDING = 4;

    /** How many people it takes before somebody has friends here. */
    public static final int COMPANY = 4;

    /**
     * Why this person is unhappy, and why they are not.
     *
     * @param person    the citizen
     * @param town      the settlement they live in
     * @param buildings how many buildings it has
     * @param raided    whether a raid is under way
     */
    public static Set<ElysiumStressor> reasonsFor(Colonies.Person person,
                                                  Colonies.Settlement town,
                                                  int buildings, boolean raided) {
        Set<ElysiumStressor> reasons = EnumSet.noneOf(ElysiumStressor.class);
        if (!person.housed()) {
            reasons.add(ElysiumStressor.HOMELESS);
        } else {
            reasons.add(ElysiumStressor.AT_HOME);
        }
        if (!person.employed()) {
            reasons.add(ElysiumStressor.UNEMPLOYED);
        }
        if (person.health() <= INJURED) {
            reasons.add(ElysiumStressor.INJURED);
        } else if (person.health() < 1.0F) {
            reasons.add(ElysiumStressor.HURT_RECENTLY);
        }
        if (raided) {
            reasons.add(ElysiumStressor.THREATENED);
        }
        if (buildings > 0 && town.citizens() > buildings * PER_BUILDING) {
            reasons.add(ElysiumStressor.CROWDED);
        }
        if (town.citizens() >= COMPANY) {
            reasons.add(ElysiumStressor.AMONG_FRIENDS);
        }
        return reasons;
    }

    /**
     * How sealed a town is, as a share.
     *
     * Read from how much of it is built rather than from block-by-block room
     * detection, which would mean scanning every building of every colony
     * every day. Buildings per citizen, so a town that keeps building as it
     * grows stays sealed and one that lets its population outrun its walls
     * does not — which is the behaviour the number is standing in for.
     */
    public static float sealed(int buildings, int citizens) {
        if (citizens <= 0) {
            return 1.0F;
        }
        float per = (float) buildings / citizens;
        return Math.max(0.0F, Math.min(1.0F, per * PER_BUILDING));
    }

    /**
     * Everything at once: this person's stress, world included.
     *
     * @return the total, never below zero — a very contented citizen in a
     *         meadow is at rest, not in credit
     */
    public static int totalFor(Colonies.Person person, Colonies.Settlement town,
                               Charter charter, int buildings, boolean raided) {
        int fromLife = ElysiumStress.total(reasonsFor(person, town, buildings, raided));
        int fromWorld = CharterRules.burden(charter.hazard(),
                sealed(buildings, town.citizens()))
                + CharterRules.airBurden(charter.air() == Charter.Air.BREATHABLE,
                        charter.air() == Charter.Air.NONE);
        return Math.max(0, fromLife + fromWorld);
    }
}
