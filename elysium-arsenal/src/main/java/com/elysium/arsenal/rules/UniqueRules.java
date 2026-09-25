package com.elysium.arsenal.rules;

import com.elysium.lib.ability.ElysiumAbilityRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Twenty-four named weapons that do something the others do not.
 *
 * <h2>Why this file could not be written until now</h2>
 *
 * Because "does something extra" had nowhere to live. The mods this replaces
 * ship hundreds of named blades whose whole difference is an effect, and
 * writing those effects here would have meant writing them again in arcana for
 * the relics — two sets of cooldowns that drift apart, and an ability that
 * behaves differently depending on which module got to it first.
 *
 * <p>So the abilities live in the library, below both, and a unique here is an
 * ordinary weapon from {@link WeaponRules} or {@link RangedRules} with a short
 * list of ability names on it. Every one of those lists goes through
 * {@link ElysiumAbilityRules#cannotShareAnItem}, which is the same check the
 * relics pass.
 *
 * <h2>What a unique is not</h2>
 *
 * It is not a better weapon. It is a weapon of a shape that already exists, at
 * a tier that already exists, that also does something — and the something is
 * priced by the library. A unique that was simply stronger would be the
 * hundred-swords problem with extra steps: the shape it is built on would stop
 * being worth carrying, and this module's whole claim is that none of its six
 * shapes is beaten on every axis by another.
 */
public final class UniqueRules {

    private UniqueRules() {
    }

    /** The tiers a unique may sit at: the top three rungs, and no lower. */
    public static final List<String> TIERS = List.of("iron", "diamond", "netherite");

    /** The commonest a unique can be, and the rarest. */
    public static final int COMMONEST = 1;
    public static final int RAREST = 4;

    /**
     * One named weapon.
     *
     * @param id        what it is called
     * @param shape     the melee kind or bow kind it is built on
     * @param tier      the material it is made of
     * @param rarity    how much it does beyond the weapon underneath
     * @param abilities what it does, named from the library's roster
     */
    public record Unique(String id, String shape, String tier, int rarity,
                         List<String> abilities) {

        public Unique {
            Objects.requireNonNull(id, "a unique with no name");
            Objects.requireNonNull(shape, "a unique of no shape");
            abilities = List.copyOf(Objects.requireNonNull(abilities,
                    "a unique that does nothing extra"));
            if (!TIERS.contains(tier)) {
                throw new IllegalArgumentException(id + " sits at " + tier
                        + ", and a unique sits at one of " + TIERS);
            }
            if (rarity < COMMONEST || rarity > RAREST) {
                throw new IllegalArgumentException(id + " has a rarity of " + rarity
                        + " and the band is " + COMMONEST + " to " + RAREST);
            }
            if (abilities.isEmpty()) {
                throw new IllegalArgumentException(id + " does nothing the weapon "
                        + "underneath does not, so it is a skin rather than a unique");
            }
        }

        /** Whether it is built on a bow rather than a melee weapon. */
        public boolean ranged() {
            return RangedRules.kind(shape) != null;
        }

        /** What its abilities are worth together. */
        public float worth() {
            float total = 0.0F;
            for (String name : abilities) {
                ElysiumAbilityRules.Ability ability =
                        ElysiumAbilityRules.ability(name);
                if (ability != null) {
                    total += ability.worth();
                }
            }
            return total;
        }

        /** How much it does when it fires, which is what rarity tracks. */
        public float power() {
            float total = 0.0F;
            for (String name : abilities) {
                ElysiumAbilityRules.Ability ability =
                        ElysiumAbilityRules.ability(name);
                if (ability != null) {
                    total += ability.power();
                }
            }
            return total;
        }
    }

    private static Unique unique(String id, String shape, String tier, int rarity,
                                 List<String> abilities) {
        return new Unique(id, shape, tier, rarity, abilities);
    }

    /** Every unique there is. */
    public static final List<Unique> UNIQUES = List.of(
            // on the sabre
            unique("whisperfang", "sabre", "iron", 1, List.of("swiftfoot")),
            unique("duelists_grace", "sabre", "diamond", 2, List.of("riposte")),
            unique("stormfang", "sabre", "netherite", 3, List.of("static_arc", "swiftfoot")),

            // on the longsword
            unique("wardens_edge", "longsword", "iron", 1, List.of("keen_edge")),
            unique("emberline", "longsword", "diamond", 2, List.of("ember_brand")),
            unique("kingsmourn", "longsword", "netherite", 3, List.of("cleave", "keen_edge")),

            // on the greatsword
            unique("grovecleaver", "greatsword", "iron", 1, List.of("ironhide")),
            unique("sunderer", "greatsword", "diamond", 2, List.of("sunder")),
            unique("worldsplitter", "greatsword", "netherite", 4, List.of("shockwave")),

            // on the spear
            unique("riverreach", "spear", "iron", 1, List.of("sure_grip")),
            unique("frostpike", "spear", "diamond", 2, List.of("chill_touch")),
            unique("skypiercer", "spear", "netherite", 3, List.of("hunters_mark")),

            // on the warhammer
            unique("stoneshaper", "warhammer", "iron", 1, List.of("steadfast")),
            unique("faultline", "warhammer", "diamond", 2, List.of("sunder", "steadfast")),
            unique("mountainfall", "warhammer", "netherite", 4, List.of("bulwark")),

            // on the estoc
            unique("needlepoint", "estoc", "iron", 1, List.of("night_eyes")),
            unique("bloodletter", "estoc", "diamond", 2, List.of("bloodscent")),
            unique("soulpiercer", "estoc", "netherite", 3, List.of("soul_harvest")),

            // on the sling
            unique("pebblethrower", "sling", "iron", 1, List.of("gleaner")),

            // on the shortbow
            unique("swiftstring", "shortbow", "diamond", 2, List.of("swiftfoot", "mender")),

            // on the repeater
            unique("hailstorm", "repeater", "diamond", 2, List.of("chill_touch")),

            // on the recurve
            unique("hornbow_of_the_wild", "recurve", "diamond", 2, List.of("bloodscent")),

            // on the longbow
            unique("sunspine", "longbow", "netherite", 3, List.of("ember_brand", "keen_edge")),

            // on the crossbow
            unique("gravebolt", "crossbow", "netherite", 4, List.of("gravewake")));

    private static final Map<String, Unique> BY_ID = new LinkedHashMap<>();

    static {
        for (Unique one : UNIQUES) {
            BY_ID.put(one.id(), one);
        }
    }

    /** The unique of that name, or null. */
    public static Unique unique(String id) {
        return BY_ID.get(id);
    }

    /** Every shape a weapon in this module comes in, melee and ranged. */
    public static List<String> shapes() {
        List<String> all = new ArrayList<>();
        for (WeaponRules.Weapon weapon : WeaponRules.KINDS) {
            all.add(weapon.name());
        }
        for (RangedRules.Bow bow : RangedRules.KINDS) {
            all.add(bow.name());
        }
        return List.copyOf(all);
    }

    /** Every unique built on one shape. */
    public static List<Unique> onShape(String shape) {
        List<Unique> found = new ArrayList<>();
        for (Unique one : UNIQUES) {
            if (one.shape().equals(shape)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // Whether the roster hangs together
    // ==================================================================

    /** Every unique built on a shape this module does not make. */
    public static List<String> shapeless() {
        Set<String> known = new TreeSet<>(shapes());
        List<String> bad = new ArrayList<>();
        for (Unique one : UNIQUES) {
            if (!known.contains(one.shape())) {
                bad.add(one.id() + " is built on a " + one.shape()
                        + ", which this module does not make");
            }
        }
        return List.copyOf(bad);
    }

    /** Every unique carrying a set of abilities no item may carry. */
    public static List<String> illegal() {
        List<String> bad = new ArrayList<>();
        for (Unique one : UNIQUES) {
            for (String trouble
                    : ElysiumAbilityRules.cannotShareAnItem(one.abilities())) {
                bad.add(one.id() + ": " + trouble);
            }
        }
        return List.copyOf(bad);
    }

    /** Every shape with no unique built on it. */
    public static List<String> shapesWithout() {
        List<String> bad = new ArrayList<>();
        for (String shape : shapes()) {
            if (onShape(shape).isEmpty()) {
                bad.add("nothing unique is built on the " + shape + ", so that shape "
                        + "stops being interesting the moment uniques exist");
            }
        }
        return List.copyOf(bad);
    }

    /** Every pair of uniques that are the same unique under two names. */
    public static List<String> indistinguishable() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Unique one : UNIQUES) {
            List<String> sorted = new ArrayList<>(one.abilities());
            sorted.sort(String::compareTo);
            String key = one.shape() + "/" + String.join("+", sorted);
            String had = seen.putIfAbsent(key, one.id());
            if (had != null) {
                bad.add(one.id() + " and " + had + " are the same unique under two "
                        + "names");
            }
        }
        return List.copyOf(bad);
    }

    /** Every band of rarity whose uniques do less than the band below. */
    public static List<String> rarityBackwards() {
        List<String> bad = new ArrayList<>();
        float below = -1.0F;
        int belowBand = -1;
        for (int rarity = COMMONEST; rarity <= RAREST; rarity++) {
            float total = 0.0F;
            int count = 0;
            for (Unique one : UNIQUES) {
                if (one.rarity() == rarity) {
                    total += one.power();
                    count++;
                }
            }
            if (count == 0) {
                bad.add("nothing at all is rarity " + rarity);
                continue;
            }
            float mean = total / count;
            if (below >= 0.0F && mean < below) {
                bad.add("rarity " + rarity + " uniques do less when they fire ("
                        + mean + ") than rarity " + belowBand + " ones (" + below
                        + ")");
            }
            below = mean;
            belowBand = rarity;
        }
        return List.copyOf(bad);
    }

    /**
     * How much a unique of each tier is allowed to do beyond its shape.
     *
     * <p>A unique is an ordinary weapon that also does something. The
     * something has to be worth less the earlier a player can hold it, or an
     * iron unique carrying a netherite unique's abilities makes both the
     * netherite unique and the whole tier above it pointless — which is the
     * hundred-swords problem with extra steps, and the thing this module
     * exists not to do.
     */
    public static final Map<String, Float> WORTH_AT_TIER = bands();

    /**
     * The bands, worked out rather than typed.
     *
     * <p>The top rung is the most any item in the game may carry, which the
     * library decides; each rung below it is half the one above. Three typed
     * numbers would be three numbers to forget to move the day the library's
     * ceiling changes, and the failure would be silent in the direction that
     * matters: a ceiling that came down would leave the bands where they were
     * and every unique still passing.
     */
    private static Map<String, Float> bands() {
        Map<String, Float> made = new LinkedHashMap<>();
        int rungs = TIERS.size();
        for (int rung = 0; rung < rungs; rung++) {
            made.put(TIERS.get(rung), ElysiumAbilityRules.MOST_ON_AN_ITEM_WORTH
                    / (1 << (rungs - 1 - rung)));
        }
        return Map.copyOf(made);
    }

    /**
     * Every unique that is a better weapon rather than a different one.
     *
     * <p>Two ways of being that, and both are checked because the first draft
     * of this method checked neither: it compared a weapon's damage to the
     * same weapon's damage, so it could not report anything, ever, and it sat
     * in the file looking like a check for as long as nobody ran it against a
     * unique built to break it.
     *
     * <p>The first way is carrying more than the tier's band allows. The
     * second is sitting at a tier whose weapon the catalogue does not make —
     * a unique with no base is a unique nothing can smith, and its recipe
     * would silently fail to load rather than say anything.
     */
    public static List<String> strongerThanTheirShape() {
        List<String> bad = new ArrayList<>();
        Set<String> catalogue = new TreeSet<>(WeaponRules.catalogue());
        catalogue.addAll(RangedRules.catalogue());
        for (Unique one : UNIQUES) {
            Float allowed = WORTH_AT_TIER.get(one.tier());
            if (allowed == null) {
                bad.add(one.id() + " sits at " + one.tier() + ", and nothing says how "
                        + "much a unique of that tier may carry");
                continue;
            }
            if (one.worth() > allowed) {
                bad.add(one.id() + " carries " + one.worth() + " at " + one.tier()
                        + ", and the band there is " + allowed + " — so the tier above "
                        + "it has nothing left to offer");
            }
            String base = one.tier() + "_" + one.shape();
            if (!catalogue.contains(base)) {
                bad.add(one.id() + " is smithed from " + base + ", which this module "
                        + "does not make, so it cannot be made at all");
            }
        }
        return List.copyOf(bad);
    }

    /** Every unique sharing a name with another. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Unique one : UNIQUES) {
            if (!seen.add(one.id())) {
                twice.add(one.id());
            }
        }
        return List.copyOf(twice);
    }

    /** Every unique whose name collides with an ordinary weapon's. */
    public static List<String> collidingWithTheCatalogue() {
        Set<String> made = new TreeSet<>(WeaponRules.catalogue());
        made.addAll(RangedRules.catalogue());
        List<String> bad = new ArrayList<>();
        for (Unique one : UNIQUES) {
            if (made.contains(one.id())) {
                bad.add(one.id() + " is also the name of an ordinary weapon, so one "
                        + "of the two registrations will lose");
            }
        }
        return List.copyOf(bad);
    }
}
