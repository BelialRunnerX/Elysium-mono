package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Seven skeletons, in halves, and what having both halves is worth.
 *
 * <h2>Why a fossil is two blocks and not one</h2>
 *
 * A decoration that does something is a decoration people place for the thing
 * it does, in the corner, out of sight. Splitting each fossil into a skull and
 * a body and paying only for the pair turns it into something a player
 * arranges: the two halves have to be near each other, which means the fossil
 * has to be somewhere a fossil looks right.
 *
 * <h2>The two properties that keep seven from becoming one</h2>
 *
 * Every fossil does something no other fossil does, and nothing any of them
 * does is a strictly better version of what another does. Without the first,
 * two of the seven are the same block in different shapes; without the second,
 * five of the seven are what a player digs past on the way to the other two.
 *
 * <p>The second is the same domination rule the weapons use, and it needs the
 * same escape: a fossil that keeps creepers away and a fossil that speeds
 * crops up are not comparable at all, and a rule that compares them would
 * demand that one of them be deleted. So the comparison only ever runs between
 * fossils that do the same <em>sort</em> of thing.
 */
public final class FossilRules {

    private FossilRules() {
    }

    /** The two halves a fossil comes in, and the whole thing. */
    public static final String SKULL = "head";
    public static final String BODY = "body";
    public static final String WHOLE = "whole";

    /** Every part, in the order a player finds them. */
    public static final List<String> PARTS = List.of(SKULL, BODY, WHOLE);

    /**
     * One creature's fossil.
     *
     * @param creature what it was
     * @param effect   what having it does
     * @param sort     the kind of thing that effect is, for comparing
     * @param power    how much it does
     * @param reach    how far from the fossil it works
     */
    public record Fossil(String creature, String effect, String sort, float power,
                         int reach) {

        public Fossil {
            Objects.requireNonNull(creature, "a fossil of nothing");
            Objects.requireNonNull(effect, creature + " does nothing");
            Objects.requireNonNull(sort, effect + " is no sort of thing");
            if (power <= 0.0F) {
                throw new IllegalArgumentException(creature + " does nothing at all");
            }
            if (reach < 1) {
                throw new IllegalArgumentException(creature + " works nowhere");
            }
            if (reach > FURTHEST) {
                throw new IllegalArgumentException(creature + " works " + reach
                        + " blocks out, and the furthest is " + FURTHEST
                        + " — past that it is not a fossil in a room, it is a rule "
                        + "about the world");
            }
        }

        /** The id of one of its parts. */
        public String id(String part) {
            return WHOLE.equals(part) ? creature + "_fossil"
                    : creature + "_fossil_" + part;
        }

        /**
         * Whether this fossil makes that one pointless.
         *
         * <p>Only ever between fossils that do the same sort of thing. A
         * fossil that frightens creepers and one that grows wheat are not
         * comparable, and a rule that compared them would insist one of them
         * be deleted — which is the failure this whole module is built to
         * avoid, arrived at from the other direction.
         */
        public boolean dominates(Fossil other) {
            if (!sort.equals(other.sort())) {
                return false;
            }
            boolean asGood = power >= other.power() && reach >= other.reach();
            boolean better = power > other.power() || reach > other.reach();
            return asGood && better;
        }
    }

    /**
     * The furthest any fossil may work.
     *
     * <p>Eight blocks is a room. Thirty-two is a base, and a fossil that works
     * across a base is not a thing a player arranges, it is a switch they
     * flip once and forget.
     */
    public static final int FURTHEST = 8;

    /** How much of a fossil's effect one half gives. */
    public static final float HALF = 0.4F;

    /**
     * What a set is worth, against the two halves apart.
     *
     * <p>Above one, or pairing them buys nothing and the halves may as well
     * be two separate blocks. Below two, or a half is worthless and the
     * player who has found only a skull has found nothing.
     */
    public static float worthOfASet() {
        return 1.0F / HALF / 2.0F;
    }

    /** Every fossil there is. */
    public static final List<Fossil> FOSSILS = List.of(
            // Frightening things off: two of them, deliberately, because they
            // frighten different things and a player with a creeper problem
            // and a player with a skeleton problem want different fossils.
            new Fossil("ocelot", "creepers_keep_away", "fear", 1.0F, 8),
            new Fossil("wolf", "skeletons_keep_away", "fear", 1.0F, 8),

            // Things that happen to the player standing near them.
            new Fossil("chicken", "lighter_landings", "body", 0.6F, 5),
            new Fossil("guardian", "breathe_under_water", "body", 1.0F, 4),
            new Fossil("creeper", "blasts_hurt_less", "body", 0.5F, 6),

            // Things that happen to the world around them.
            new Fossil("sheep", "wool_grows_back", "world", 0.5F, 6),
            new Fossil("villager", "traders_restock", "world", 1.0F, 5));

    private static final Map<String, Fossil> BY_CREATURE = new LinkedHashMap<>();

    static {
        for (Fossil one : FOSSILS) {
            BY_CREATURE.put(one.creature(), one);
        }
    }

    /** The fossil of that creature, or null. */
    public static Fossil fossil(String creature) {
        return BY_CREATURE.get(creature);
    }

    /** Every block id a fossil needs. */
    public static List<String> catalogue() {
        List<String> all = new ArrayList<>();
        for (Fossil one : FOSSILS) {
            for (String part : PARTS) {
                all.add(one.id(part));
            }
        }
        return List.copyOf(all);
    }

    /**
     * What a fossil gives, given which of its halves are nearby.
     *
     * @param skull whether the skull is within reach
     * @param body  whether the body is
     */
    public static float given(Fossil one, boolean skull, boolean body) {
        if (skull && body) {
            return one.power();
        }
        if (skull || body) {
            return one.power() * HALF;
        }
        return 0.0F;
    }

    // ==================================================================
    // Whether seven fossils are seven fossils
    // ==================================================================

    /** Every pair of fossils that do the same thing. */
    public static List<String> sharingAnEffect() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Fossil one : FOSSILS) {
            String had = seen.putIfAbsent(one.effect(), one.creature());
            if (had != null) {
                bad.add(one.creature() + " and " + had + " both " + one.effect()
                        + ", so one of them is the other in a different shape");
            }
        }
        return List.copyOf(bad);
    }

    /** Every fossil another fossil makes pointless. */
    public static List<String> dominated() {
        List<String> bad = new ArrayList<>();
        for (Fossil better : FOSSILS) {
            for (Fossil worse : FOSSILS) {
                if (better != worse && better.dominates(worse)) {
                    bad.add(worse.creature() + " does less than " + better.creature()
                            + " of the same sort of thing and works no further out, "
                            + "so it is what a player digs past");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every fossil declared twice. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (Fossil one : FOSSILS) {
            if (!seen.add(one.creature())) {
                bad.add(one.creature() + " is declared twice");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every way the halves could stop being worth pairing.
     *
     * <p>Both directions matter and they fail differently. A set worth no
     * more than the halves apart means pairing buys nothing; a half worth
     * nothing means a player who has found a skull has found a decoration and
     * does not know it.
     */
    public static List<String> halvesThatDoNotAddUp() {
        List<String> bad = new ArrayList<>();
        if (HALF <= 0.0F) {
            bad.add("half a fossil does nothing, so a player who has found a skull "
                    + "has found a decoration and has no way of knowing");
        }
        if (HALF >= 0.5F) {
            bad.add("two halves apart are worth as much as a set, so pairing them "
                    + "buys nothing and the two blocks may as well be two blocks");
        }
        for (Fossil one : FOSSILS) {
            float set = given(one, true, true);
            float apart = given(one, true, false) + given(one, false, true);
            if (set <= apart) {
                bad.add(one.creature() + "'s set is worth no more than its halves "
                        + "apart");
            }
            if (given(one, true, false) <= 0.0F) {
                bad.add(one.creature() + "'s skull alone does nothing");
            }
            if (given(one, false, false) != 0.0F) {
                bad.add(one.creature() + " works with neither half nearby, so the "
                        + "blocks are not what is doing it");
            }
        }
        return List.copyOf(bad);
    }

    /** Every sort of thing with only one fossil doing it. */
    public static List<String> sortsOfOne() {
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (Fossil one : FOSSILS) {
            counted.merge(one.sort(), 1, Integer::sum);
        }
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Integer> row : counted.entrySet()) {
            if (row.getValue() < 2) {
                bad.add("only one fossil is a " + row.getKey() + " fossil, so the "
                        + "domination check can never run on that sort and a second "
                        + "one added later would never be compared to it");
            }
        }
        return List.copyOf(bad);
    }
}
