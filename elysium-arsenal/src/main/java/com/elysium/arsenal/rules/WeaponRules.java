package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weapons, and the proof that none of them is simply the best. JDK only.
 *
 * <h2>What five weapon mods actually add</h2>
 *
 * Between them, about a hundred swords. What a player ends up with is three,
 * and the other ninety-seven are things that occupy a loot table. That is not
 * because ninety-seven of them are badly made — it is because <b>nobody ever
 * compares them all to each other</b>. Each one is balanced against the
 * diamond sword by whoever wrote it, none is balanced against the other
 * ninety-nine, and the handful that happen to come out ahead on every axis
 * become the answer to every question.
 *
 * <h2>Domination, which is the thing to forbid</h2>
 *
 * One weapon <b>dominates</b> another when it is at least as good on every
 * axis and better on one. A dominated weapon is dead content: there is no
 * situation in which a player who has both would reach for it, so it may as
 * well not have been made, and whoever made it will never find out because
 * finding out means comparing it against everything else.
 *
 * <p>So every weapon here differs from every other <em>in a direction</em>.
 * Something is always faster, something always hits harder, something always
 * reaches further — and the harness proves over the whole catalogue that no
 * weapon dominates another and that each one is best at something.
 *
 * <h2>And the ladder, which is the other half</h2>
 *
 * Within a kind, a higher tier must be better, or the tier means nothing.
 * Between kinds it must not be, or the tiers collapse into one ladder and
 * there is only one weapon again with six names.
 */
public final class WeaponRules {

    private WeaponRules() {
    }

    // ==================================================================
    // The axes
    // ==================================================================

    /** A vanilla sword's damage at diamond, for scale. */
    public static final float SWORD_DAMAGE = 7.0F;

    /** And its swings a second. */
    public static final float SWORD_SPEED = 1.6F;

    /** And how far it reaches, in blocks, beyond a player's own reach. */
    public static final float SWORD_REACH = 0.0F;

    /**
     * One weapon, as the five numbers a player can feel.
     *
     * <p>Five and not four. The first version had four, and two of the six
     * kinds came out best at nothing — middling on every axis, beaten
     * outright by nobody and chosen by nobody either, which is the exact
     * shape of the dead content this class exists to forbid. There are only
     * so many crowns to go round, and a catalogue with more entries than
     * crowns has entries that are there to pad the loot table.
     *
     * @param name    what it is called
     * @param damage  what one hit takes off, at diamond
     * @param speed   swings a second
     * @param reach   how much further than a sword it touches, in blocks
     * @param sweep   how many things one swing can hit
     * @param pierce  what share of a target's armour it goes through
     */
    public record Weapon(String name, float damage, float speed, float reach, int sweep,
                         float pierce) {

        public Weapon {
            if (damage <= 0.0F || speed <= 0.0F) {
                throw new IllegalArgumentException(name + " does nothing to anybody");
            }
            if (reach < 0.0F) {
                throw new IllegalArgumentException(name + " reaches less far than a "
                        + "fist");
            }
            if (sweep < 1) {
                throw new IllegalArgumentException(name + " hits nothing at all");
            }
            if (pierce < 0.0F || pierce >= 1.0F) {
                throw new IllegalArgumentException(name + " goes through "
                        + Math.round(pierce * 100) + "% of armour, and a weapon that "
                        + "ignores armour entirely makes armour a cosmetic");
            }
        }

        /** Damage a second against one target. */
        public float dps() {
            return damage * speed;
        }

        /** And against a crowd, which is what a sweep is for. */
        public float crowd() {
            return dps() * sweep;
        }

        /**
         * Whether this is at least as good as that on every axis.
         *
         * <p>Four axes and no weighting, deliberately. A weighted score would
         * let a weapon be "worse overall" while being better at everything a
         * player actually notices, which is how dead content gets defended.
         */
        public boolean atLeastAsGoodAs(Weapon other) {
            return damage >= other.damage() && speed >= other.speed()
                    && reach >= other.reach() && sweep >= other.sweep()
                    && pierce >= other.pierce();
        }

        /** Whether it beats that on some axis. */
        public boolean betterSomewhereThan(Weapon other) {
            return damage > other.damage() || speed > other.speed()
                    || reach > other.reach() || sweep > other.sweep()
                    || pierce > other.pierce();
        }

        /** Whether that weapon has no reason to exist while this one does. */
        public boolean dominates(Weapon other) {
            return atLeastAsGoodAs(other) && betterSomewhereThan(other);
        }
    }

    // ==================================================================
    // The kinds
    // ==================================================================

    /**
     * Six kinds, each best at one thing and worst at another.
     *
     * <p>Six because that is as many distinct answers as the four axes
     * support: fastest, hardest, longest, widest, and the two useful
     * compromises. A seventh would have to be a copy of one of these with a
     * different name, which is the hundred-swords problem in miniature.
     *
     * <p>The numbers are at diamond. Every one of them is measured against a
     * vanilla diamond sword — {@link #SWORD_DAMAGE}, {@link #SWORD_SPEED} —
     * because a weapon set on its own scale is a weapon set that either
     * replaces the game's own or is replaced by it.
     */
    public static final List<Weapon> KINDS = List.of(
            // Fastest, and it pays for it everywhere else.
            new Weapon("sabre", 5.0F, 2.4F, 0.0F, 1, 0.0F),
            // Most damage a second against one thing — and deliberately a
            // shade *under* a vanilla sword on raw damage, because a weapon
            // that beats the game's own sword on every axis has not been
            // added, the sword has been deleted.
            new Weapon("longsword", 6.5F, 1.9F, 0.25F, 1, 0.0F),
            // Slow and enormous, and it takes a rank with it.
            new Weapon("greatsword", 11.0F, 0.9F, 0.25F, 3, 0.0F),
            // Reach, and nothing else at all.
            new Weapon("spear", 6.5F, 1.2F, 1.5F, 1, 0.0F),
            // Hits one thing very hard and is no use against a crowd.
            new Weapon("warhammer", 13.0F, 0.75F, 0.0F, 1, 0.0F),
            // The answer to a target in full plate, and to nothing else.
            new Weapon("estoc", 6.0F, 1.3F, 0.5F, 1, 0.5F));

    /** The tiers, which are the game's own. */
    public static final List<String> TIERS = ArsenalRules.TIERS;

    /**
     * How much of a diamond weapon each tier is, in damage.
     *
     * <p>The game's own numbers. Its tool tiers add 0, 1, 2 and 3 to a sword
     * and netherite adds 4 — and <b>gold adds nothing at all</b>, the same as
     * wood. A gold sword is not a rung between iron and diamond however the
     * registry lists it, and a ladder written from the registry order dips in
     * the middle. The first version of this file did exactly that and the
     * check below caught it.
     */
    public static final Map<String, Float> TIER_SHARE = share();

    private static Map<String, Float> share() {
        Map<String, Float> made = new LinkedHashMap<>();
        made.put("wooden", 0.55F);
        made.put("stone", 0.70F);
        made.put("golden", 0.55F);
        made.put("iron", 0.85F);
        made.put("diamond", 1.00F);
        made.put("netherite", 1.12F);
        return Map.copyOf(made);
    }

    /**
     * And how much faster each tier swings.
     *
     * <p>One, everywhere but gold. What gold is in this game is <em>quick and
     * disposable</em>, which is a side-grade rather than a rung, and giving it
     * the damage of iron to make the list tidy would be making the list tidy
     * at the cost of being wrong about the game.
     */
    public static final Map<String, Float> TIER_HASTE = haste();

    private static Map<String, Float> haste() {
        Map<String, Float> made = new LinkedHashMap<>();
        for (String tier : TIER_SHARE.keySet()) {
            made.put(tier, "golden".equals(tier) ? 1.25F : 1.0F);
        }
        return Map.copyOf(made);
    }

    /**
     * The tiers that are a ladder, in the order they climb.
     *
     * <p>Gold is not in it, and that is the point rather than an omission: it
     * is a side-grade, and a check that walks the registry's order instead
     * reports every weapon in the module as broken at gold.
     */
    public static final List<String> LADDER =
            List.of("wooden", "stone", "iron", "diamond", "netherite");

    /** One kind at one tier. */
    public static Weapon at(Weapon kind, String tier) {
        Float of = TIER_SHARE.get(tier);
        Float quick = TIER_HASTE.get(tier);
        if (of == null || quick == null) {
            throw new IllegalArgumentException(tier + " is not a tier");
        }
        // Damage and speed are what the material decides. How far a thing
        // reaches and how many it hits are properties of its shape: a
        // netherite spear is not a longer spear.
        return new Weapon(kind.name(), kind.damage() * of, kind.speed() * quick,
                kind.reach(), kind.sweep(), kind.pierce());
    }

    /** Every weapon this module adds. */
    public static List<String> catalogue() {
        List<String> names = new ArrayList<>(KINDS.size() * TIERS.size());
        for (String tier : TIERS) {
            for (Weapon kind : KINDS) {
                names.add(tier + "_" + kind.name());
            }
        }
        return List.copyOf(names);
    }

    // ==================================================================
    // The properties
    // ==================================================================

    /**
     * Every weapon that another weapon of the same tier makes pointless.
     *
     * <p>Must be empty. Same tier, because a diamond sabre being beaten by a
     * netherite one is the ladder working; a diamond sabre being beaten by a
     * diamond longsword is a sabre nobody ever makes.
     */
    public static List<String> dominated() {
        List<String> caught = new ArrayList<>();
        for (String tier : TIERS) {
            for (Weapon one : KINDS) {
                for (Weapon other : KINDS) {
                    if (one == other) {
                        continue;
                    }
                    if (at(one, tier).dominates(at(other, tier))) {
                        caught.add(tier + " " + other.name() + " is beaten on every "
                                + "axis by the " + tier + " " + one.name()
                                + ", so nobody who has both ever makes one");
                    }
                }
            }
        }
        return caught;
    }

    /**
     * Every weapon that is not the best at anything.
     *
     * <p>Must be empty, and it is a stronger claim than not being dominated:
     * a weapon can be middling on all four axes, beaten by nobody outright and
     * chosen by nobody ever. "Is there a question this is the answer to" is the
     * question a catalogue has to be able to answer about every entry in it.
     */
    public static List<String> bestAtNothing() {
        List<String> caught = new ArrayList<>();
        for (Weapon kind : KINDS) {
            boolean fastest = true;
            boolean hardest = true;
            boolean longest = true;
            boolean widest = true;
            boolean crowds = true;
            boolean sharpest = true;
            boolean deadliest = true;
            for (Weapon other : KINDS) {
                if (other == kind) {
                    continue;
                }
                fastest &= kind.speed() > other.speed();
                hardest &= kind.damage() > other.damage();
                longest &= kind.reach() > other.reach();
                widest &= kind.sweep() > other.sweep();
                crowds &= kind.crowd() > other.crowd();
                sharpest &= kind.pierce() > other.pierce();
                deadliest &= kind.dps() > other.dps();
            }
            if (!(fastest || hardest || longest || widest || crowds || sharpest
                    || deadliest)) {
                caught.add(kind.name() + " is not the fastest, the hardest, the "
                        + "longest, the widest, the sharpest, the best against a crowd "
                        + "or the best against one thing, so there is no question it is "
                        + "the answer to");
            }
        }
        return caught;
    }

    /** Every kind whose tiers do not climb. */
    public static List<String> flatLadders() {
        List<String> caught = new ArrayList<>();
        for (Weapon kind : KINDS) {
            for (int rung = 1; rung < LADDER.size(); rung++) {
                Weapon below = at(kind, LADDER.get(rung - 1));
                Weapon above = at(kind, LADDER.get(rung));
                if (above.damage() <= below.damage()) {
                    caught.add(kind.name() + " does no more damage at "
                            + LADDER.get(rung) + " than at " + LADDER.get(rung - 1));
                }
            }
        }
        for (String tier : TIERS) {
            if (!LADDER.contains(tier) && !TIER_SHARE.containsKey(tier)) {
                caught.add(tier + " is a tier the module registers and has no numbers "
                        + "for, so every weapon at it is whatever the last one was");
            }
        }
        return caught;
    }

    /**
     * How far the best single-target weapon is ahead of the worst, at a tier.
     *
     * <p>Not zero — they are different weapons — and not large, or the choice
     * is not a choice.
     */
    public static float spread(String tier) {
        float least = Float.MAX_VALUE;
        float most = 0.0F;
        for (Weapon kind : KINDS) {
            float dps = at(kind, tier).dps();
            least = Math.min(least, dps);
            most = Math.max(most, dps);
        }
        return least <= 0.0F ? Float.MAX_VALUE : most / least;
    }

    /** The widest the damage a second may span at one tier. */
    public static final float WIDEST_SPREAD = 1.8F;

    /**
     * Whether the game's own sword is still worth carrying.
     *
     * <p>The check nobody writes. A weapon set whose worst entry beats a
     * diamond sword has not added six weapons, it has deleted one.
     */
    public static List<String> outclassesTheGame() {
        List<String> caught0 = new ArrayList<>();
        List<String> caught = caught0;
        Weapon sword = new Weapon("sword", SWORD_DAMAGE, SWORD_SPEED, SWORD_REACH, 1,
                0.0F);

        // And the side-grade, which is only a side-grade if it is actually to
        // one side: weaker than iron and quicker than it.
        Weapon goldIron = at(KINDS.get(0), "golden");
        Weapon plainIron = at(KINDS.get(0), "iron");
        if (goldIron.damage() >= plainIron.damage()) {
            caught0.add("gold hits at least as hard as iron, which is not the game this "
                    + "is in");
        }
        if (goldIron.speed() <= plainIron.speed()) {
            caught0.add("gold swings no faster than iron, so it is simply a worse iron "
                    + "and nobody ever makes one");
        }
        for (Weapon kind : KINDS) {
            Weapon ours = at(kind, "diamond");
            if (ours.dominates(sword)) {
                caught.add("a diamond " + kind.name() + " beats the game's own diamond "
                        + "sword on every axis, so the sword is now a crafting step");
            }
        }
        return caught;
    }
}
