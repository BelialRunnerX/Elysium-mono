package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bows, bolts and what they throw, on the same terms as the melee weapons.
 *
 * <h2>Why this file exists</h2>
 *
 * Because nothing in this module was ranged. Six melee kinds across six tiers
 * and not one of them could hit something on the other side of a river, which
 * is a weapons module with half a weapon set in it.
 *
 * <h2>The same rule, and why it needs a fifth axis</h2>
 *
 * Nothing is dominated: no bow may be at least as good as another on every
 * axis and better on one. Four axes — how fast it draws, how hard it hits, how
 * far it carries, how many it looses — is not enough to keep six kinds apart,
 * because the cheapest one ends up strictly worse than the next cheapest at
 * everything. <b>Accuracy</b> is the fifth, and it is what makes a sling and a
 * crossbow two different answers rather than two rungs.
 *
 * <p>Every number is at diamond, and measured against the game's own bow, for
 * the reason the melee weapons are: a ranged set on its own scale is a set
 * that either replaces the game's bow or is replaced by it.
 */
public final class RangedRules {

    private RangedRules() {
    }

    /** What the game's own bow does at a full draw, in half-hearts. */
    public static final float VANILLA_BOW_DAMAGE = 9.0F;

    /** How long the game's own bow takes to draw fully, in ticks. */
    public static final int VANILLA_BOW_DRAW = 20;

    /** How far the game's own arrow carries before it is not worth firing. */
    public static final float VANILLA_BOW_RANGE = 1.0F;

    /**
     * One kind of ranged weapon.
     *
     * @param name     what it is called
     * @param draw     how many full draws a second, so higher is faster
     * @param damage   half-hearts at a full draw
     * @param range    how far it carries, against the game's bow at one
     * @param shots    how many it looses at once
     * @param accuracy how tightly, from nothing to perfectly
     * @param handling how much of your own speed you keep while it is drawn
     */
    public record Bow(String name, float draw, float damage, float range, int shots,
                      float accuracy, float handling) {

        public Bow {
            Objects.requireNonNull(name, "a bow with no name");
            if (draw <= 0.0F) {
                throw new IllegalArgumentException(name + " never finishes drawing");
            }
            if (damage <= 0.0F) {
                throw new IllegalArgumentException(name + " does no damage");
            }
            if (range <= 0.0F) {
                throw new IllegalArgumentException(name + " carries nowhere");
            }
            if (shots < 1) {
                throw new IllegalArgumentException(name + " looses nothing");
            }
            if (accuracy <= 0.0F || accuracy > 1.0F) {
                throw new IllegalArgumentException(name + " has an accuracy of "
                        + accuracy + ", and the band is above nothing to perfectly");
            }
            if (handling <= 0.0F || handling > 1.0F) {
                throw new IllegalArgumentException(name + " has a handling of "
                        + handling + ", and the band is above nothing to all of "
                        + "your own speed");
            }
        }

        /** Damage a second at a full draw, against one thing. */
        public float dps() {
            return damage * draw * shots;
        }

        /** How long one draw takes, in ticks. */
        public int drawTicks() {
            return Math.max(1, Math.round(20.0F / draw));
        }

        /** Whether this is at least as good as that on every axis. */
        public boolean atLeastAsGoodAs(Bow other) {
            return draw >= other.draw() && damage >= other.damage()
                    && range >= other.range() && shots >= other.shots()
                    && accuracy >= other.accuracy()
                    && handling >= other.handling();
        }

        /** Whether it beats that on some axis. */
        public boolean betterSomewhereThan(Bow other) {
            return draw > other.draw() || damage > other.damage()
                    || range > other.range() || shots > other.shots()
                    || accuracy > other.accuracy() || handling > other.handling();
        }

        /** Whether that bow has no reason to exist while this one does. */
        public boolean dominates(Bow other) {
            return atLeastAsGoodAs(other) && betterSomewhereThan(other);
        }
    }

    /**
     * Six kinds, each best at one thing.
     *
     * <p>The numbers are at diamond. A sling is here because the bottom of the
     * ladder needs to be something rather than a worse bow: it draws faster
     * than anything else in the game and is wrong about where the arrow goes.
     */
    public static final List<Bow> KINDS = List.of(
            // Fastest draw there is, and wrong about everything else.
            new Bow("sling", 3.0F, 2.5F, 0.70F, 1, 0.50F, 0.95F),
            // The one you can still run with, which is what makes it worth
            // carrying after you can afford something heavier.
            new Bow("shortbow", 2.4F, 4.0F, 1.00F, 1, 0.70F, 1.00F),
            // Three at once and none of them well aimed.
            new Bow("repeater", 1.2F, 3.5F, 0.90F, 3, 0.60F, 0.70F),
            // Carries furthest, which is a different question from hitting
            // hardest and is why it is not just a small longbow.
            new Bow("recurve", 1.6F, 6.0F, 1.60F, 1, 0.80F, 0.85F),
            // Hits hardest, and roots you while you draw it.
            new Bow("longbow", 0.8F, 12.0F, 1.30F, 1, 0.75F, 0.50F),
            // Goes exactly where you pointed it, and takes its time.
            new Bow("crossbow", 0.6F, 10.0F, 1.20F, 1, 1.00F, 0.60F));

    /** The tiers, which are the melee weapons' and the game's. */
    public static final List<String> TIERS = WeaponRules.TIERS;

    /** The rungs that climb, which is the melee ladder without gold in it. */
    public static final List<String> LADDER = WeaponRules.LADDER;

    /** One kind at one tier. */
    public static Bow at(Bow kind, String tier) {
        Float of = WeaponRules.TIER_SHARE.get(tier);
        Float quick = WeaponRules.TIER_HASTE.get(tier);
        if (of == null || quick == null) {
            throw new IllegalArgumentException(tier + " is not a tier");
        }
        // Damage and draw are what the material decides. How far it carries,
        // how many it looses and how straight it goes are properties of the
        // shape: a netherite sling is not a more accurate sling.
        return new Bow(kind.name(), kind.draw() * quick, kind.damage() * of,
                kind.range(), kind.shots(), kind.accuracy(), kind.handling());
    }

    /** Every ranged weapon this module adds. */
    public static List<String> catalogue() {
        List<String> names = new ArrayList<>(KINDS.size() * TIERS.size());
        for (String tier : TIERS) {
            for (Bow kind : KINDS) {
                names.add(tier + "_" + kind.name());
            }
        }
        return List.copyOf(names);
    }

    // ==================================================================
    // What they throw
    // ==================================================================

    /**
     * One kind of ammunition.
     *
     * @param name    what it is called
     * @param damage  what it adds to the bow's own, in half-hearts
     * @param effect  what it does besides damage, or empty
     * @param forBolt whether it is a bolt rather than an arrow
     */
    public record Shot(String name, float damage, String effect, boolean forBolt) {

        public Shot {
            Objects.requireNonNull(name, "ammunition with no name");
            Objects.requireNonNull(effect, "null effect");
            if (damage < 0.0F) {
                throw new IllegalArgumentException(name + " heals what it hits");
            }
        }
    }

    /**
     * Nine arrows and three bolts.
     *
     * <p>A bolt is not an arrow with a different name: it is what a crossbow
     * takes and a bow does not, which is the one thing that makes a crossbow
     * a different weapon rather than a slow bow. Three of them, because three
     * is enough to be a choice and few enough that carrying them is possible.
     */
    public static final List<Shot> SHOTS = List.of(
            new Shot("flint_arrow", 0.0F, "", false),
            new Shot("iron_arrow", 1.0F, "", false),
            new Shot("barbed_arrow", 2.0F, "bleed", false),
            new Shot("ember_arrow", 1.0F, "burning", false),
            new Shot("rime_arrow", 1.0F, "slowing", false),
            new Shot("sundering_arrow", 0.0F, "sundering", false),
            new Shot("hollow_arrow", 0.0F, "marking", false),
            new Shot("thunder_arrow", 2.0F, "chaining", false),
            new Shot("void_arrow", 3.0F, "piercing", false),
            new Shot("iron_bolt", 2.0F, "", true),
            new Shot("piercing_bolt", 2.0F, "piercing", true),
            new Shot("shattering_bolt", 3.0F, "sundering", true));

    private static final Map<String, Bow> BY_NAME = new LinkedHashMap<>();

    static {
        for (Bow kind : KINDS) {
            BY_NAME.put(kind.name(), kind);
        }
    }

    /** The kind of that name, or null. */
    public static Bow kind(String name) {
        return BY_NAME.get(name);
    }

    /** Every arrow, or every bolt. */
    public static List<Shot> ammunition(boolean bolts) {
        List<Shot> found = new ArrayList<>();
        for (Shot shot : SHOTS) {
            if (shot.forBolt() == bolts) {
                found.add(shot);
            }
        }
        return List.copyOf(found);
    }

    /** Whether a kind takes bolts rather than arrows. */
    public static boolean takesBolts(Bow kind) {
        return "crossbow".equals(kind.name());
    }

    // ==================================================================
    // Whether any of it has a reason to exist
    // ==================================================================

    /** Every kind another kind makes pointless. */
    public static List<String> dominated() {
        List<String> bad = new ArrayList<>();
        for (Bow better : KINDS) {
            for (Bow worse : KINDS) {
                if (!better.name().equals(worse.name()) && better.dominates(worse)) {
                    bad.add(worse.name() + " is made pointless by " + better.name());
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind that is not the best in the set at anything at all. */
    public static List<String> bestAtNothing() {
        List<String> bad = new ArrayList<>();
        for (Bow kind : KINDS) {
            boolean best = false;
            for (int axis = 0; axis < 6 && !best; axis++) {
                best = true;
                for (Bow other : KINDS) {
                    if (kind.name().equals(other.name())) {
                        continue;
                    }
                    float mine = axisOf(kind, axis);
                    float theirs = axisOf(other, axis);
                    if (theirs > mine) {
                        best = false;
                        break;
                    }
                }
            }
            if (!best) {
                bad.add(kind.name() + " is the best in the set at nothing, so there "
                        + "is no reason to reach for it");
            }
        }
        return List.copyOf(bad);
    }

    /** How many axes there are, which is how many corners there are to own. */
    public static final int AXES = 6;

    /**
     * Every axis no kind is the best at, and every kind that owns more than
     * one.
     *
     * <p>Six kinds and six axes: each should own exactly one corner. Two kinds
     * sharing a corner leaves another corner empty, and an empty corner is a
     * question the set has no answer to. Five axes was not enough — the
     * shortbow and the recurve were the best in the set at nothing at all, and
     * handling is the sixth.
     */
    public static List<String> cornersUnclaimed() {
        List<String> bad = new ArrayList<>();
        for (int axis = 0; axis < AXES; axis++) {
            List<String> best = new ArrayList<>();
            float most = -1.0F;
            for (Bow kind : KINDS) {
                float mine = axisOf(kind, axis);
                if (mine > most) {
                    most = mine;
                    best.clear();
                    best.add(kind.name());
                } else if (mine == most) {
                    best.add(kind.name());
                }
            }
            if (best.size() > 1) {
                bad.add("axis " + axis + " is shared by " + best
                        + ", so some other axis has nobody at its corner");
            }
        }
        return List.copyOf(bad);
    }

    private static float axisOf(Bow bow, int axis) {
        return switch (axis) {
            case 0 -> bow.draw();
            case 1 -> bow.damage();
            case 2 -> bow.range();
            case 3 -> bow.shots();
            case 4 -> bow.accuracy();
            default -> bow.handling();
        };
    }

    /**
     * Every rung where the ladder does not climb.
     *
     * <p>Damage a second, at every kind, walking the ladder that excludes
     * gold — because gold is a side-grade in this game and a check that walks
     * the registry's order reports every weapon in the module as broken.
     */
    public static List<String> ladderDips() {
        List<String> bad = new ArrayList<>();
        for (Bow kind : KINDS) {
            float below = -1.0F;
            String belowTier = "";
            for (String tier : LADDER) {
                float dps = at(kind, tier).dps();
                if (below >= 0.0F && dps <= below) {
                    bad.add(kind.name() + " at " + tier + " does no more a second "
                            + "than at " + belowTier + ", so that rung buys nothing");
                }
                below = dps;
                belowTier = tier;
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind that beats the game's own bow on every axis at once. */
    public static List<String> replacingTheGamesBow() {
        List<String> bad = new ArrayList<>();
        for (Bow kind : KINDS) {
            Bow diamond = at(kind, "diamond");
            if (diamond.damage() > VANILLA_BOW_DAMAGE
                    && diamond.draw() > 20.0F / VANILLA_BOW_DRAW
                    && diamond.range() > VANILLA_BOW_RANGE) {
                bad.add(kind.name() + " beats the game's own bow on damage, draw and "
                        + "range at once, so the bow has not been added to, it has "
                        + "been deleted");
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece of ammunition nothing can fire. */
    public static List<String> unfireable() {
        boolean anyBow = false;
        boolean anyCrossbow = false;
        for (Bow kind : KINDS) {
            if (takesBolts(kind)) {
                anyCrossbow = true;
            } else {
                anyBow = true;
            }
        }
        List<String> bad = new ArrayList<>();
        for (Shot shot : SHOTS) {
            if (shot.forBolt() && !anyCrossbow) {
                bad.add(shot.name() + " is a bolt and nothing here takes bolts");
            }
            if (!shot.forBolt() && !anyBow) {
                bad.add(shot.name() + " is an arrow and nothing here takes arrows");
            }
        }
        return List.copyOf(bad);
    }

    /** Every piece of ammunition that is another under a different name. */
    public static List<String> repeatedShots() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        Set<String> names = new TreeSet<>();
        for (Shot shot : SHOTS) {
            if (!names.add(shot.name())) {
                bad.add("two things called " + shot.name());
            }
            String key = shot.damage() + "/" + shot.effect() + "/" + shot.forBolt();
            String had = seen.putIfAbsent(key, shot.name());
            if (had != null) {
                bad.add(shot.name() + " and " + had + " are the same thing under two "
                        + "names");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind sharing a name with a melee weapon, which would collide. */
    public static List<String> collidingWithMelee() {
        Set<String> melee = new TreeSet<>();
        for (WeaponRules.Weapon weapon : WeaponRules.KINDS) {
            melee.add(weapon.name());
        }
        List<String> bad = new ArrayList<>();
        for (Bow kind : KINDS) {
            if (melee.contains(kind.name())) {
                bad.add(kind.name() + " is both a bow and a melee weapon, so one of "
                        + "the two registries will lose");
            }
        }
        return List.copyOf(bad);
    }
}
