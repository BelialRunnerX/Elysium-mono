package com.elysium.agronomy.rules;

import com.elysium.industry.rules.IndustryRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The machinery around a hive, and the one line it may not cross.
 *
 * <h2>The awkward rule this is built around</h2>
 *
 * {@link BeeRules#HIVE_AT_MOST} says a hive may be worth a quarter of a mine
 * and no more, and the reason is the same one the crops are held to: the
 * moment a farm out-earns a mine, the ore ladder that three ages are built on
 * stops mattering. A hive wants no light, no water, no replanting and no
 * attention at all, so the only honest way to price it is slowly.
 *
 * <p>A bare hive sat <em>exactly</em> on that ceiling — twelve bees, a comb
 * every four minutes, sixty units an hour against a mine's two hundred and
 * forty. Which left nowhere for an upgrade to go. Any upgrade worth fitting
 * put a hive over the line, and an upgrade not worth fitting is a recipe
 * nobody makes twice.
 *
 * <h2>So the ceiling stays and the floor drops</h2>
 *
 * A hive nobody has built anything for runs at about a fifth of the ceiling.
 * The ladder earns the rest back, and a fully fitted industrial apiary lands
 * on the line and not a comb over it — which is not a number anybody chose.
 * {@link #ticksForTheCeiling()} solves for it, so retuning the mine moves the
 * whole ladder rather than breaking the rule quietly at the top.
 *
 * <h2>What is actually proved</h2>
 *
 * Not that the best case is under the line. <em>Every</em> case: four
 * housings, seven upgrades, every way of filling the slots, every tier of
 * bee. Eighteen thousand combinations, and the sweep is the whole reason an
 * upgrade system is written as arithmetic rather than as a handful of block
 * entities. "No combination of anything out-earns a mine" is a sentence that
 * can be checked. "We think the top one is about right" is not.
 */
public final class ApiaryRules {

    private ApiaryRules() {
    }

    // ==================================================================
    // What a hive is
    // ==================================================================

    /**
     * One rung of the housing ladder.
     *
     * @param id    what the block is called
     * @param bees  how many bees live in it before any upgrade
     * @param slots how many upgrades it takes
     * @param from  the rung it is built out of, or "" for the first
     */
    public record Housing(String id, int bees, int slots, String from) {

        public Housing {
            Objects.requireNonNull(id, "a housing with no name");
            if (bees <= 0) {
                throw new IllegalArgumentException(id + " holds " + bees + " bees");
            }
            if (slots < 0) {
                throw new IllegalArgumentException(id + " has " + slots + " slots");
            }
        }

        /** Whether this is the one you find rather than build. */
        public boolean first() {
            return from.isEmpty();
        }
    }

    /** The housing ladder, the one you can make first at the bottom. */
    public static final List<Housing> HOUSINGS = List.of(
            new Housing("hive", AgronomyRules.MOST_BEES, 0, ""),
            new Housing("apiary", 16, 2, "hive"),
            new Housing("advanced_apiary", 20, 4, "apiary"),
            new Housing("industrial_apiary", 24, 6, "advanced_apiary"));

    /** A housing by name, or null. */
    public static Housing housing(String id) {
        for (Housing one : HOUSINGS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The last rung, which is the one the ceiling is solved against. */
    public static Housing best() {
        return HOUSINGS.get(HOUSINGS.size() - 1);
    }

    // ==================================================================
    // What goes in the slots
    // ==================================================================

    /**
     * One upgrade.
     *
     * @param id        what the item is called
     * @param bees      how many more bees it houses
     * @param faster    how much faster combs come, as a multiplier
     * @param attention what chore it takes away, or "" if it takes none
     */
    public record Upgrade(String id, int bees, float faster, String attention) {

        public Upgrade {
            Objects.requireNonNull(id, "an upgrade with no name");
            if (bees < 0) {
                throw new IllegalArgumentException(id + " houses " + bees + " bees");
            }
            if (faster < 1.0F) {
                throw new IllegalArgumentException(id + " makes a hive slower");
            }
        }

        /** Whether fitting this changes what comes out of the hive at all. */
        public boolean earns() {
            return bees > 0 || faster > 1.0F;
        }
    }

    /**
     * The seven, of which two touch what a hive earns and five buy attention.
     *
     * <p>That split is the design. Flowers that no longer have to be planted,
     * combs that no longer have to be collected, bees that no longer wander
     * off, breeding that happens in the box rather than in a field — none of
     * it produces anything, so none of it is priced against the ore ladder.
     * It is also what the jar being replaced is actually for.
     */
    public static final List<Upgrade> UPGRADES = List.of(
            new Upgrade("capacity_upgrade", 12, 1.0F, ""),
            new Upgrade("speed_upgrade", 0, 1.5F, ""),
            new Upgrade("flowering_upgrade", 0, 1.0F, "planting flowers"),
            new Upgrade("collection_upgrade", 0, 1.0F, "collecting combs"),
            new Upgrade("simulation_upgrade", 0, 1.0F, "bees wandering off"),
            new Upgrade("breeding_upgrade", 0, 1.0F, "breeding in a field"),
            new Upgrade("sampling_upgrade", 0, 1.0F, "catching one to look at"));

    /** An upgrade by name, or null. */
    public static Upgrade upgrade(String id) {
        for (Upgrade one : UPGRADES) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The ones that change what comes out, which are the ones priced. */
    public static List<Upgrade> earning() {
        List<Upgrade> made = new ArrayList<>();
        for (Upgrade one : UPGRADES) {
            if (one.earns()) {
                made.add(one);
            }
        }
        return List.copyOf(made);
    }

    /** How many upgrades of one kind a housing takes. One, and one only. */
    public static final int OF_EACH_KIND = 1;

    // ==================================================================
    // What a fitted hive earns
    // ==================================================================

    /** How many bees a housing holds with these upgrades fitted. */
    public static int beesIn(Housing housing, List<Upgrade> fitted) {
        int bees = housing.bees();
        for (Upgrade one : fitted) {
            bees += one.bees();
        }
        return bees;
    }

    /** How much faster combs come with these upgrades fitted. */
    public static float speedIn(List<Upgrade> fitted) {
        float faster = 1.0F;
        for (Upgrade one : fitted) {
            faster *= one.faster();
        }
        return faster;
    }

    /**
     * How many combs an hour, at this tier, in this housing, fitted like this.
     *
     * <p>The same shape as {@link BeeRules#perHour(int)} and deliberately so:
     * bees times hours divided by how long a comb takes, with the speed
     * upgrades shortening the second of those rather than multiplying the
     * first. Multiplying the first would be the same number today and a
     * different one the moment a comb yields more than one of anything.
     */
    public static float perHour(int tier, Housing housing, List<Upgrade> fitted) {
        float ticks = BeeRules.combTicks(tier) / speedIn(fitted);
        return beesIn(housing, fitted)
                * (AgronomyRules.TICKS_PER_HOUR / ticks) * BeeRules.PER_COMB;
    }

    /** The most any hive of any kind is allowed to earn in an hour. */
    public static float ceiling() {
        return AgronomyRules.minedPerHour() * BeeRules.HIVE_AT_MOST;
    }

    /**
     * How long the first resource comb has to take for the best possible
     * apiary to land exactly on the ceiling.
     *
     * <p>Solved rather than chosen. {@link BeeRules#FIRST_RESOURCE_TICKS} is
     * this number, and a harness check says so, so moving the mine moves the
     * whole ladder instead of leaving the top of it over the line.
     */
    public static int ticksForTheCeiling() {
        Housing top = best();
        List<Upgrade> everything = fittable(top);
        float effective = beesIn(top, everything) * speedIn(everything);
        return Math.round(effective * AgronomyRules.TICKS_PER_HOUR / ceiling());
    }

    /**
     * The most earning a housing's slots can hold.
     *
     * <p>Only the earning ones, and only as many as there are slots, best
     * first. An attention upgrade in a slot is a slot not spent on throughput,
     * so the worst case for the ceiling is a hive with nothing but earning
     * upgrades in it.
     */
    public static List<Upgrade> fittable(Housing housing) {
        List<Upgrade> made = new ArrayList<>();
        for (Upgrade one : earning()) {
            if (made.size() < housing.slots()) {
                made.add(one);
            }
        }
        return List.copyOf(made);
    }

    /** Whether a hive fitted like this stays under the line. */
    public static boolean underTheCeiling(int tier, Housing housing,
                                          List<Upgrade> fitted) {
        return perHour(tier, housing, fitted) <= ceiling() + 0.001F;
    }

    // ==================================================================
    // The centrifuge, which is what a comb is for
    // ==================================================================

    /** What a centrifuge is called. */
    public static final String CENTRIFUGE = "centrifuge";

    /** How long one comb takes to come apart. */
    public static final int SPIN_TICKS = 200;

    /**
     * How much power spinning one comb draws.
     *
     * <p>The same shape as the accelerator's, because the bus that checks
     * every machine in the pack against the generators that feed it reads
     * this type and nothing else.
     */
    public static final IndustryRules.Machine SPINNER =
            new IndustryRules.Machine(24.0F, IndustryRules.Voltage.LOW);

    /** How much of its material one comb comes apart into. */
    public static final int MATERIAL_PER_COMB = BeeRules.PER_COMB;

    /** How much wax, which is the same for every comb there is. */
    public static final int WAX_PER_COMB = 2;

    /** How much honey, likewise. */
    public static final int HONEY_PER_COMB = 1;

    /** The namespace this module's own items are in. */
    public static final String OURS = "elysiumagronomy";

    /** The game's own namespace, for the one thing here that is its. */
    public static final String VANILLA = "minecraft";

    /**
     * Every material this module hands over as a chunk.
     *
     * <p>The seeds' seventeen plus every material a resource bee names that
     * is not already one of them. A chunk rather than the ingot itself
     * because the ingot belongs to the foundry, the foundry sits above this
     * module, and a module reaching up into the one above it for an item id
     * is a module that stops compiling the day that one is renamed. The
     * chunk is ours and it carries the tag the foundry already smelts.
     */
    public static List<String> chunked() {
        List<String> made = new ArrayList<>(SeedRules.chunked());
        for (String material : BeeRules.materialsNamed()) {
            if (!made.contains(material)) {
                made.add(material);
            }
        }
        return List.copyOf(made);
    }

    /** What the chunk of a material is called. */
    public static String chunkOf(String material) {
        return SeedRules.chunkOf(material);
    }

    /**
     * Everything one comb comes apart into, in the order the recipe lists it.
     *
     * <p>Named in full, namespace and all, so the block that reads this looks
     * an item up rather than guessing at one — and so a validator can check
     * every id against what is actually registered.
     *
     * <p>Taken as the bee rather than looked up from an id, so a control can
     * hand this a bee built to be caught.
     */
    public static List<String> spun(BeeRules.Bee bee) {
        List<String> made = new ArrayList<>();
        if (BeeRules.RESOURCE.equals(bee.kind())) {
            made.add(OURS + ":" + chunkOf(bee.produce()));
        }
        made.add(OURS + ":wax");
        made.add(VANILLA + ":honey_bottle");
        return List.copyOf(made);
    }

    /** How many of each thing a comb comes apart into, in the same order. */
    public static List<Integer> spunCounts(BeeRules.Bee bee) {
        List<Integer> made = new ArrayList<>();
        if (BeeRules.RESOURCE.equals(bee.kind())) {
            made.add(MATERIAL_PER_COMB);
        }
        made.add(WAX_PER_COMB);
        made.add(HONEY_PER_COMB);
        return List.copyOf(made);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the machinery. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInLadder(HOUSINGS));
        bad.addAll(faultsInUpgrades(UPGRADES));
        bad.addAll(faultsInCeiling());
        return List.copyOf(bad);
    }

    /**
     * The housing ladder: named once, built out of the rung below, and
     * getting better as it goes up.
     */
    public static List<String> faultsInLadder(List<Housing> ladder) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int at = 0; at < ladder.size(); at++) {
            Housing one = ladder.get(at);
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two rungs of the ladder");
            }
            if (at == 0) {
                if (!one.first()) {
                    bad.add(one.id() + " is the first rung and is built out of "
                            + one.from() + ", which is further up it");
                }
                continue;
            }
            Housing under = ladder.get(at - 1);
            if (!one.from().equals(under.id())) {
                bad.add(one.id() + " is built out of " + one.from() + " and the "
                        + "rung below it is " + under.id() + ", so the ladder has "
                        + "a rung nobody climbs through");
            }
            if (one.bees() <= under.bees() && one.slots() <= under.slots()) {
                bad.add(one.id() + " holds " + one.bees() + " bees in "
                        + one.slots() + " slots and " + under.id() + " holds "
                        + under.bees() + " in " + under.slots()
                        + ", so the rung above is no better than the one below "
                        + "and nobody would ever build it");
            }
        }
        if (ladder.size() < 2) {
            bad.add("the housing ladder has " + ladder.size() + " rung(s), which "
                    + "is not a ladder");
        }
        return bad;
    }

    /**
     * The upgrades: named once, and every one of them worth fitting.
     *
     * <p>An upgrade that neither earns anything nor takes a chore away is a
     * recipe that costs something and does nothing, which is worse than not
     * shipping it.
     */
    public static List<String> faultsInUpgrades(List<Upgrade> upgrades) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> chores = new LinkedHashSet<>();
        for (Upgrade one : upgrades) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two upgrades");
            }
            if (!one.earns() && one.attention().isEmpty()) {
                bad.add(one.id() + " earns nothing and takes no chore away, so "
                        + "it is a recipe that costs something and does nothing");
            }
            if (one.earns() && !one.attention().isEmpty()) {
                bad.add(one.id() + " both earns and takes a chore away, and the "
                        + "whole of the ceiling argument is that those are two "
                        + "different kinds of thing");
            }
            if (!one.attention().isEmpty() && !chores.add(one.attention())) {
                bad.add("two upgrades take away " + one.attention() + ", so one "
                        + "of them is a slot nobody would spend");
            }
        }
        if (upgrades.stream().noneMatch(Upgrade::earns)) {
            bad.add("no upgrade earns anything, so the ladder is decoration");
        }
        if (upgrades.stream().allMatch(Upgrade::earns)) {
            bad.add("every upgrade earns something, so a hive is a machine and "
                    + "none of the attention this jar exists to buy back is "
                    + "bought back");
        }
        return bad;
    }

    /** How far under the ceiling a bare hive has to stay for a ladder to fit. */
    public static final float BARE_AT_MOST = 0.5F;

    /**
     * The one line, and where the two ends of the ladder sit against it.
     *
     * <p>Not "is FIRST_RESOURCE_TICKS what ticksForTheCeiling() says" — that
     * one is the definition read back to itself now that the constant is the
     * function's answer, and it would pass with the ladder in any shape at
     * all. What is claimed here is about the ladder: the top of it reaches
     * the ceiling, the bottom of it is well under, and neither is true by
     * construction.
     */
    public static List<String> faultsInCeiling() {
        List<String> bad = new ArrayList<>();
        if (ceiling() <= 0.0F) {
            bad.add("there is no ceiling at all, so a hive may out-earn anything");
            return bad;
        }
        Housing top = best();
        float reached = perHour(1, top, fittable(top));
        if (reached > ceiling() + 0.001F) {
            bad.add("the best apiary anybody can build earns " + reached
                    + " an hour and the ceiling is " + ceiling()
                    + ", so a hive out-earns a mine");
        }
        if (reached < ceiling() * 0.98F) {
            bad.add("the best apiary anybody can build earns " + reached
                    + " an hour against a ceiling of " + ceiling()
                    + ", so the top of the ladder is a block nobody would climb "
                    + "to and the ceiling is not what is pricing anything");
        }
        float bare = perHour(1, HOUSINGS.get(0), List.of());
        if (bare > ceiling() * BARE_AT_MOST) {
            bad.add("a hive nobody has built anything for already earns " + bare
                    + " an hour against a ceiling of " + ceiling()
                    + ", so there is no room under the line for a ladder and "
                    + "every upgrade is one that cannot be fitted");
        }
        if (bare <= 0.0F) {
            bad.add("a bare hive earns nothing at all, so the first thing a "
                    + "player builds does nothing");
        }
        return bad;
    }

    /**
     * Every way of filling a housing's slots, one of each kind at most.
     *
     * <p>Every way, not the interesting ones. The check this feeds is that
     * nothing anywhere crosses the line, and a sweep that skips the
     * combinations somebody thought were dull is a sweep that will one day
     * skip the one that was not.
     */
    public static List<List<Upgrade>> everyFitting(Housing housing) {
        List<List<Upgrade>> made = new ArrayList<>();
        int kinds = UPGRADES.size();
        for (int mask = 0; mask < (1 << kinds); mask++) {
            List<Upgrade> fitted = new ArrayList<>();
            for (int at = 0; at < kinds; at++) {
                if ((mask & (1 << at)) != 0) {
                    fitted.add(UPGRADES.get(at));
                }
            }
            if (fitted.size() <= housing.slots()) {
                made.add(List.copyOf(fitted));
            }
        }
        return List.copyOf(made);
    }

    /** Every housing and fitting at which a hive would out-earn a mine. */
    public static List<String> outMining() {
        List<String> earning = new ArrayList<>();
        for (BeeRules.Bee bee : BeeRules.ofKind(BeeRules.RESOURCE)) {
            int tier = BeeRules.tierOf(bee.id());
            if (tier == BeeRules.A_CYCLE) {
                continue;
            }
            for (Housing housing : HOUSINGS) {
                for (List<Upgrade> fitted : everyFitting(housing)) {
                    if (!underTheCeiling(tier, housing, fitted)) {
                        earning.add(housing.id() + " at tier " + tier + " with "
                                + fitted.size() + " upgrade(s) earns "
                                + perHour(tier, housing, fitted)
                                + " an hour and the ceiling is " + ceiling());
                    }
                }
            }
        }
        return List.copyOf(earning);
    }

    /** Every block and item the machinery adds, for a registry to walk. */
    public static List<String> everything() {
        List<String> made = new ArrayList<>();
        for (Housing one : HOUSINGS) {
            if (!one.first()) {
                made.add(one.id());
            }
        }
        made.add(CENTRIFUGE);
        for (Upgrade one : UPGRADES) {
            made.add(one.id());
        }
        return List.copyOf(made);
    }

    /** How many of those there are. */
    public static int howMany() {
        return everything().size();
    }
}
