package com.elysium.arcana.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Every number in {@link ArcanaRules}, executed.
 *
 * The claim this file exists for is that no sequence of crafting, dissolving
 * and condensing ever leaves a player better off than they started. That is a
 * property over every walk through the whole recipe set, which is not
 * something anybody checks by reading a table of ten thousand numbers.
 */
public final class ArcanaHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    /**
     * Whether building that threw, which is how a compact constructor is
     * tested: a record that refuses to exist is a whole class of bad row that
     * no rule below ever has to be written for.
     */
    private static boolean threwOn(Runnable building) {
        try {
            building.run();
            return false;
        } catch (RuntimeException refused) {
            return true;
        }
    }

    public static void main(String[] args) {
        theSeeds();
        theDerivation();
        theDirections();
        theWalk();
        theGeodes();
        theBudding();
        theRelics();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: no recipe anywhere makes more than it "
                + "consumes, a round trip always loses, and over a million random acts "
                + "of crafting, dissolving and condensing not one left anybody better "
                + "off than they started, and "
                + GeodeRules.catalogue().size() + " geode blocks whose growth chain "
                + "has no hole in it, and "
                + RelicRules.RELICS.size() + " relics carrying every ability the "
                + "library defines");
    }

    // ==================================================================

    private static void theSeeds() {
        check(ArcanaRules.SEEDS.size() >= 20,
                "there are only " + ArcanaRules.SEEDS.size() + " decided values, which "
                        + "leaves most of the game unpriceable");
        for (Map.Entry<String, Long> seed : ArcanaRules.SEEDS.entrySet()) {
            check(seed.getValue() > 0L,
                    seed.getKey() + " is worth " + seed.getValue() + ", and something "
                            + "worth nothing is something you can dissolve forever");
            check(seed.getKey().contains(":"),
                    seed.getKey() + " is not a namespaced id, so it names nothing");
        }
        check(ArcanaRules.SEEDS.get("minecraft:cobblestone") == 1L,
                "the unit is not a cobblestone, so every ratio in the module is "
                        + "relative to something nobody can hold");
        check(ArcanaRules.SEEDS.get("minecraft:diamond")
                        > ArcanaRules.SEEDS.get("minecraft:iron_ingot"),
                "iron is worth more than diamond");
        System.out.println("  seeds: " + ArcanaRules.SEEDS.size()
                + " decided values, all positive, all named");
    }

    private static ArcanaRules.Step step(String output, int makes, Object... pairs) {
        Map<String, Integer> needs = new HashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            needs.put((String) pairs[at], (Integer) pairs[at + 1]);
        }
        return new ArcanaRules.Step(output, makes, needs);
    }

    private static void theDerivation() {
        Map<String, ArcanaRules.Step> recipes = new HashMap<>();
        recipes.put("plank", step("plank", 4, "minecraft:oak_log", 1));
        recipes.put("stick", step("stick", 4, "plank", 2));
        recipes.put("torch", step("torch", 4, "stick", 1, "minecraft:coal", 1));
        recipes.put("pick", step("pick", 1, "stick", 2, "minecraft:cobblestone", 3));

        Map<String, Long> worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        check(worth.get("plank") == 8L, "four planks out of a 32 log is not 8 each");
        check(worth.get("stick") == 4L, "four sticks out of two 8 planks is not 4 each");
        check(worth.get("pick") == 11L, "a stone pick is not two sticks and three stone");
        check(worth.get("minecraft:oak_log") == 32L, "a decided value was derived over");

        check(ArcanaRules.profitable(recipes, worth).isEmpty(),
                "an ordinary recipe set contains a profitable recipe: "
                        + ArcanaRules.profitable(recipes, worth));

        // Cheapest wins. A second, worse way of making a thing must not raise
        // its price, because the cheap way is what it actually costs and the
        // gap between them is what a player would ride.
        recipes.put("plank_expensive", step("plank", 1, "minecraft:oak_log", 1));
        Map<String, Long> again = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        check(again.get("plank") == 8L,
                "a second, worse recipe raised the price of a plank to "
                        + again.get("plank"));
        check(ArcanaRules.profitable(recipes, again).isEmpty(),
                "adding a worse recipe made something profitable");

        // An ingredient nobody can price makes its recipe unpriceable, rather
        // than free.
        recipes.put("mystery", step("mystery", 1, "something_nobody_ships", 1));
        recipes.put("mystery_box", step("mystery_box", 1, "mystery", 4));
        Map<String, Long> third = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        check(!third.containsKey("mystery"),
                "an item made of something unpriceable was priced anyway");
        check(!third.containsKey("mystery_box"),
                "an item made of an unpriceable item was priced anyway");

        // The shape that breaks every naive table: a loop.
        Map<String, ArcanaRules.Step> looped = new HashMap<>();
        looped.put("block", step("block", 1, "ingot", 9));
        looped.put("ingot", step("ingot", 9, "block", 1));
        looped.put("ingot_from_ore", step("ingot", 1, "minecraft:cobblestone", 300));
        Map<String, Long> spun = ArcanaRules.derive(looped, ArcanaRules.SEEDS);
        check(ArcanaRules.profitable(looped, spun).isEmpty(),
                "the nine-to-one loop every pack contains came out profitable: "
                        + ArcanaRules.profitable(looped, spun));

        // And a set that deliberately cannot be settled at all.
        Map<String, ArcanaRules.Step> nonsense = new HashMap<>();
        nonsense.put("a", step("a", 2, "b", 1));
        nonsense.put("b", step("b", 2, "a", 1));
        nonsense.put("a_seeded", step("a", 1, "minecraft:diamond", 1));
        Map<String, Long> settled = ArcanaRules.derive(nonsense, ArcanaRules.SEEDS);
        check(ArcanaRules.profitable(nonsense, settled).isEmpty(),
                "a halving loop was priced into something profitable: "
                        + ArcanaRules.profitable(nonsense, settled));

        System.out.println("  derivation: cheapest wins, unpriceable stays unpriceable, "
                + "and neither a nine-to-one loop nor a halving loop is profitable");
    }

    private static void theDirections() {
        check(ArcanaRules.BURN_SHARE < 1.0F,
                "a round trip is lossless, so any rounding error anywhere in a table of "
                        + "ten thousand numbers is an infinite supply of something");
        check(ArcanaRules.BURN_SHARE > 0.5F,
                "dissolving loses more than half, which is not a transmutation system, "
                        + "it is a bin");

        Random random = new Random(1_618_033L);
        for (int round = 0; round < 400_000; round++) {
            long worth = 1L + random.nextInt(100_000);
            int count = 1 + random.nextInt(64);
            long got = ArcanaRules.dissolve(worth, count);
            long costs = ArcanaRules.condensing(worth, count);
            check(got <= costs, "dissolving " + count + " at " + worth + " gave " + got
                    + " and condensing them back costs " + costs);
            check(got >= 0L, "dissolving gave a negative amount");
            int back = ArcanaRules.affords(got, worth);
            check(back <= count, "dissolving " + count + " bought back " + back);
            check(ArcanaRules.condensing(worth, back) <= got,
                    "what a balance says it affords costs more than the balance");
        }
        check(ArcanaRules.dissolve(0L, 10) == 0L, "something worth nothing gave motes");
        check(ArcanaRules.dissolve(100L, 0) == 0L, "dissolving nothing gave motes");
        check(ArcanaRules.dissolve(-5L, 10) == 0L, "a negative worth gave motes");
        check(ArcanaRules.affords(1_000L, 0L) == 0,
                "a balance buys any number of something worth nothing");
        check(ArcanaRules.affords(-1L, 10L) == 0, "a negative balance buys something");
        System.out.println("  directions: 400,000 round trips, and not one of them came "
                + "back with as much as it started with");
    }

    /**
     * The property the module exists for.
     *
     * <p>A random walk of the three things a player can do — craft, dissolve,
     * condense — watching one number: what they have, plus what it is worth.
     * It may fall. It may stay still. It may never rise.
     */
    private static void theWalk() {
        Random random = new Random(2_718_281L);
        Map<String, ArcanaRules.Step> recipes = new HashMap<>();
        List<String> names = new ArrayList<>(ArcanaRules.SEEDS.keySet());
        for (int index = 0; index < 120; index++) {
            String made = "thing" + index;
            Map<String, Integer> needs = new HashMap<>();
            int count = 1 + random.nextInt(3);
            for (int need = 0; need < count; need++) {
                needs.put(names.get(random.nextInt(names.size())), 1 + random.nextInt(8));
            }
            recipes.put("r" + index, new ArcanaRules.Step(made, 1 + random.nextInt(4),
                    needs));
            names.add(made);
        }
        // And some recipes that consume the derived things, so the table has
        // depth in it rather than one layer over the seeds.
        for (int index = 0; index < 120; index++) {
            Map<String, Integer> needs = new HashMap<>();
            int count = 1 + random.nextInt(3);
            for (int need = 0; need < count; need++) {
                needs.put(names.get(random.nextInt(names.size())), 1 + random.nextInt(4));
            }
            recipes.put("deep" + index, new ArcanaRules.Step("deep" + index,
                    1 + random.nextInt(3), needs));
            names.add("deep" + index);
        }

        Map<String, Long> worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);
        List<String> profitable = ArcanaRules.profitable(recipes, worth);
        check(profitable.isEmpty(),
                profitable.size() + " of 240 random recipes came out profitable: "
                        + profitable.subList(0, Math.min(4, profitable.size())));
        check(worth.size() > ArcanaRules.SEEDS.size() + 100,
                "only " + worth.size() + " things could be priced out of a set of 240 "
                        + "recipes, so the walk below is mostly about nothing");

        List<String> priced = new ArrayList<>(worth.keySet());
        long crafted = 0;
        long dissolved = 0;
        long condensed = 0;
        for (int life = 0; life < 400; life++) {
            Map<String, Integer> held = new HashMap<>();
            long motes = 0L;
            for (int index = 0; index < 8; index++) {
                held.merge(priced.get(random.nextInt(priced.size())),
                        1 + random.nextInt(32), Integer::sum);
            }
            long was = ArcanaRules.estate(motes, held, worth);

            for (int act = 0; act < 2_500; act++) {
                switch (random.nextInt(3)) {
                    case 0 -> {                                   // craft
                        ArcanaRules.Step step = new ArrayList<>(recipes.values())
                                .get(random.nextInt(recipes.size()));
                        boolean can = true;
                        for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                            if (held.getOrDefault(need.getKey(), 0) < need.getValue()) {
                                can = false;
                                break;
                            }
                        }
                        if (!can) {
                            break;
                        }
                        for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                            held.merge(need.getKey(), -need.getValue(), Integer::sum);
                        }
                        held.merge(step.output(), step.makes(), Integer::sum);
                        crafted++;
                    }
                    case 1 -> {                                   // dissolve
                        String what = priced.get(random.nextInt(priced.size()));
                        int have = held.getOrDefault(what, 0);
                        if (have <= 0) {
                            break;
                        }
                        int spend = 1 + random.nextInt(have);
                        held.merge(what, -spend, Integer::sum);
                        motes += ArcanaRules.dissolve(worth.get(what), spend);
                        dissolved++;
                    }
                    default -> {                                  // condense
                        String what = priced.get(random.nextInt(priced.size()));
                        long each = worth.get(what);
                        int most = ArcanaRules.affords(motes, each);
                        if (most <= 0) {
                            break;
                        }
                        int want = 1 + random.nextInt(most);
                        motes -= ArcanaRules.condensing(each, want);
                        held.merge(what, want, Integer::sum);
                        condensed++;
                    }
                }
                long now = ArcanaRules.estate(motes, held, worth);
                check(now <= was, "a player's estate rose from " + was + " to " + now
                        + " on act " + act + " of life " + life);
                check(motes >= 0L, "a balance went negative");
                was = now;
            }
        }
        check(crafted > 1_000 && dissolved > 1_000 && condensed > 1_000,
                "the walk did " + crafted + " crafts, " + dissolved + " dissolves and "
                        + condensed + " condenses, so one of the three is untested");
        System.out.println("  the walk: 400 lives, " + crafted + " crafts, " + dissolved
                + " dissolves and " + condensed + " condenses, and the estate never "
                + "rose once");
    }

    // ==================================================================

    /**
     * Whether the geodes are a catalogue anybody can reach.
     *
     * <p>The mod this replaces ships twenty-nine materials, most of which do
     * not exist in this pack. The materials here are the ones the ground
     * actually gives you, and validate.py checks that against the foundry's
     * own mined list. What is checked here is the shape: seven blocks for
     * every material and a growth chain with no hole in it, because a chain
     * that stops somewhere is a player standing next to a bud that never
     * changes with nothing in the world to tell them why.
     */
    private static void theGeodes() {
        check(!GeodeRules.materials().isEmpty(), "there are no geodes at all");
        check(GeodeRules.halfMade().isEmpty(),
                "a material missing one of the seven shapes: "
                        + GeodeRules.halfMade());
        check(GeodeRules.brokenChain().isEmpty(),
                "a growth chain with a hole in it: " + GeodeRules.brokenChain());
        check(GeodeRules.repeated().isEmpty(),
                "two geode blocks with the same id: " + GeodeRules.repeated());
        check(GeodeRules.namedTwice().isEmpty(),
                "a material in both lists at once: " + GeodeRules.namedTwice());
        check(GeodeRules.deadSteps().isEmpty(),
                "a step of the chain that leads nowhere: " + GeodeRules.deadSteps());

        check(GeodeRules.catalogue().size()
                        == GeodeRules.materials().size() * GeodeRules.KINDS.size(),
                "the catalogue is not every material times every shape, so it was "
                        + "written out rather than derived");
        check(GeodeRules.items().size()
                        == GeodeRules.materials().size() * GeodeRules.ITEMS.size(),
                "the items are not every material times every kind");

        java.util.Set<String> ids = new java.util.HashSet<>();
        for (GeodeRules.Geode geode : GeodeRules.catalogue()) {
            check(ids.add(geode.id()), "two geodes called " + geode.id());
            check(geode.grows() == GeodeRules.CHAIN.contains(geode.kind()),
                    geode.id() + " disagrees with itself about whether it grows");
        }

        // The budding block reads the game's way round. A player should not
        // have to learn a second convention for an idea they already know.
        check(new GeodeRules.Geode("amethyst", GeodeRules.BUDDING).id()
                        .equals("budding_amethyst"),
                "the budding block is named the other way round from the game's own");

        // Every step grows into the next, and the last grows into nothing.
        for (int step = 0; step < GeodeRules.CHAIN.size() - 1; step++) {
            GeodeRules.Geode one = new GeodeRules.Geode("iron",
                    GeodeRules.CHAIN.get(step));
            check(one.next().equals(GeodeRules.CHAIN.get(step + 1)),
                    GeodeRules.CHAIN.get(step) + " does not grow into "
                            + GeodeRules.CHAIN.get(step + 1));
        }
        check(new GeodeRules.Geode("iron",
                        GeodeRules.CHAIN.get(GeodeRules.CHAIN.size() - 1))
                        .next().isEmpty(),
                "the last step of the chain grows into something, so the chain has "
                        + "no end");
        check(new GeodeRules.Geode("iron", GeodeRules.BLOCK).next().isEmpty(),
                "a plain block claims to grow into something");
        check(!new GeodeRules.Geode("iron", GeodeRules.BLOCK).grows(),
                "a plain block is a step in the growth chain");
    }

    // ==================================================================

    /**
     * Whether thirty relics are thirty relics.
     *
     * <p>The balance work is not here — it is in the library, where the
     * abilities live — and that is the point of having done the ability system
     * first. What is checked here is the join: every relic carries a set the
     * library allows, every ability is carried by something, and rarity means
     * what it claims to.
     */
    private static void theRelics() {
        check(!RelicRules.RELICS.isEmpty(), "there are no relics");
        check(RelicRules.repeated().isEmpty(),
                "two relics with the same name: " + RelicRules.repeated());
        check(RelicRules.illegal().isEmpty(),
                "a relic carrying abilities no item is allowed to carry: "
                        + RelicRules.illegal());
        check(RelicRules.unworn().isEmpty(),
                "an ability nothing in the world carries: " + RelicRules.unworn());
        check(RelicRules.indistinguishable().isEmpty(),
                "two relics that are the same relic under two names: "
                        + RelicRules.indistinguishable());
        check(RelicRules.rarityBackwards().isEmpty(),
                "the rarer relic is the weaker one: " + RelicRules.rarityBackwards());
        check(RelicRules.emptySlots().isEmpty(),
                "a slot nothing is worn in: " + RelicRules.emptySlots());

        for (RelicRules.Relic relic : RelicRules.RELICS) {
            check(!relic.abilities().isEmpty(),
                    relic.id() + " does nothing at all");
            check(relic.worth() > 0.0F, relic.id() + " is worth nothing");
            check(relic.power() > 0.0F, relic.id() + " does nothing when it fires");
            for (String name : relic.abilities()) {
                check(com.elysium.lib.ability.ElysiumAbilityRules.ability(name) != null,
                        relic.id() + " names " + name + ", which the library does "
                                + "not have");
            }
        }

        // A positive control on the join itself: a relic carrying the same
        // ability twice must be refused, or `illegal()` is reading an empty
        // list from a rule that never finds anything.
        check(!com.elysium.lib.ability.ElysiumAbilityRules
                        .cannotShareAnItem(java.util.List.of("blink", "blink"))
                        .isEmpty(),
                "the library allows one item to carry the same ability twice, so the "
                        + "check every relic above passed through finds nothing");
    }

    /**
     * How a budding block is made.
     *
     * <p>The claim under test is the short loop: that breaking a seeded block
     * never returns what seeding it cost. The long loop — a block that grows
     * for ever — is the thing the player is buying and is supposed to pay off,
     * so no amount of sampling would ever show it to be wrong. The short one is
     * pure arithmetic, so it is swept rather than sampled.
     */
    private static void theBudding() {
        List<String> faults = BuddingRules.faults();
        check(faults.isEmpty(), "something wrong with the seedings: " + faults);

        BuddingRules.Budding first = BuddingRules.BUDDINGS.get(0);

        check(!BuddingRules.faultsInNames(List.of(first, first)).isEmpty(),
                "a material priced twice is not reported");
        check(BuddingRules.faultsInNames(BuddingRules.BUDDINGS).isEmpty(),
                "the materials this module prices are reported as doubled");

        check(!BuddingRules.faultsInCoverage(List.of(first),
                        GeodeRules.materials()).isEmpty(),
                "a geode with no way to seed it is not reported");
        check(BuddingRules.faultsInCoverage(BuddingRules.BUDDINGS,
                        GeodeRules.materials()).isEmpty(),
                "a geode this module ships cannot be seeded");

        check(!BuddingRules.faultsInStrangers(List.of(new BuddingRules.Budding(
                        "unobtainium", "unobtainium", 12, "minecraft:stone")),
                GeodeRules.materials()).isEmpty(),
                "a price for a geode this module does not have is not reported");
        check(BuddingRules.faultsInStrangers(BuddingRules.BUDDINGS,
                        GeodeRules.materials()).isEmpty(),
                "this module prices a geode it does not ship");

        // The rule the file is arranged around: a multiplier, not a source.
        check(!BuddingRules.faultsInBootstrap(List.of(new BuddingRules.Budding(
                        "platinum", "coal", 32, "minecraft:glow_ink_sac")))
                .isEmpty(),
                "a geode seeded out of another material's shards is not "
                        + "reported, and that makes every ore in the pack "
                        + "obtainable from whichever one is cheapest");
        check(BuddingRules.faultsInBootstrap(BuddingRules.BUDDINGS).isEmpty(),
                "something this module prices is a source rather than a multiplier");

        check(!BuddingRules.faultsInSmuggling(List.of(new BuddingRules.Budding(
                        "platinum", "platinum", 32, "elysiumarcana:coal_shard")),
                GeodeRules.materials()).isEmpty(),
                "a catalyst that is another material's shard is not reported, "
                        + "and that is the same back door through the other "
                        + "ingredient");
        check(BuddingRules.faultsInSmuggling(BuddingRules.BUDDINGS,
                        GeodeRules.materials()).isEmpty(),
                "a catalyst this module names is another material's shard");

        check(!BuddingRules.faultsInWholeBlocks(List.of(new BuddingRules.Budding(
                        "coal", "coal", 10, "minecraft:glow_ink_sac")),
                BuddingRules.CLUSTER_GIVES).isEmpty(),
                "a price that is not a whole number of blocks is not reported");
        check(BuddingRules.faultsInWholeBlocks(BuddingRules.BUDDINGS,
                        BuddingRules.CLUSTER_GIVES).isEmpty(),
                "a price this module sets is not a whole number of blocks");

        check(!BuddingRules.faultsInGrid(List.of(new BuddingRules.Budding(
                        "coal", "coal", 400, "minecraft:glow_ink_sac")),
                BuddingRules.CLUSTER_GIVES, BuddingRules.GRID).isEmpty(),
                "a recipe too big for the grid is not reported, and the game "
                        + "drops those at load without saying anything");
        check(BuddingRules.faultsInGrid(BuddingRules.BUDDINGS,
                        BuddingRules.CLUSTER_GIVES, BuddingRules.GRID).isEmpty(),
                "a recipe this module writes is too big for the grid");

        check(!BuddingRules.faultsInPayback(List.of(new BuddingRules.Budding(
                        "coal", "coal", 4, "minecraft:stone")),
                BuddingRules.LEAST_CYCLES, BuddingRules.MOST_CYCLES).isEmpty(),
                "a seeding that pays for itself in one cluster is not reported");
        check(!BuddingRules.faultsInPayback(List.of(new BuddingRules.Budding(
                        "coal", "coal", 4_000, "minecraft:stone")),
                BuddingRules.LEAST_CYCLES, BuddingRules.MOST_CYCLES).isEmpty(),
                "a seeding nobody would ever do is not reported");
        check(BuddingRules.faultsInPayback(BuddingRules.BUDDINGS,
                        BuddingRules.LEAST_CYCLES,
                        BuddingRules.MOST_CYCLES).isEmpty(),
                "something this module prices is outside the wait it allows");

        check(!BuddingRules.faultsInLadder(List.of(
                        new BuddingRules.Budding("coal", "coal", 32, "minecraft:stone"),
                        new BuddingRules.Budding("iron", "iron", 8, "minecraft:stone")),
                GeodeRules.MINED, GeodeRules.THE_GAMES_OWN).isEmpty(),
                "a mined material seeded cheaper than a given one is not "
                        + "reported, and that is a way round the ladder four "
                        + "other modules are priced against");
        check(BuddingRules.faultsInLadder(BuddingRules.BUDDINGS,
                        GeodeRules.MINED, GeodeRules.THE_GAMES_OWN).isEmpty(),
                "this module seeds something mined cheaper than something given");

        check(!BuddingRules.faultsInCatalysts(List.of(new BuddingRules.Budding(
                        "coal", "coal", 8, "a shard"))).isEmpty(),
                "a catalyst that is not the name of anything is not reported");
        check(BuddingRules.faultsInCatalysts(BuddingRules.BUDDINGS).isEmpty(),
                "this module names a catalyst that is not a name");

        check(threwOn(() -> new BuddingRules.Budding("coal", "coal", 0,
                        "minecraft:stone")),
                "a seeding that costs nothing is accepted");
        check(threwOn(() -> new BuddingRules.Budding("coal", "coal", 8, "")),
                "a seeding gated only by the thing it makes is accepted");
        check(threwOn(() -> new BuddingRules.Budding("coal", "", 8,
                        "minecraft:stone")),
                "a seeding out of nothing in particular is accepted");

        // The sweep: every price against the grid and the blocks, checked
        // against the division rather than against a table. The bootstrap rule
        // is not swept because it is a string comparison with nothing to get
        // wrong; the arithmetic is where the mistakes live.
        int swept = 0;
        int overGrid = 0;
        int ragged = 0;
        java.util.Random random = new java.util.Random(30_7717L);
        for (int round = 0; round < 200_000; round++) {
            int shards = 1 + random.nextInt(80);
            BuddingRules.Budding made = new BuddingRules.Budding("coal", "coal",
                    shards, "minecraft:glow_ink_sac");
            int blocks = (shards + BuddingRules.CLUSTER_GIVES - 1)
                    / BuddingRules.CLUSTER_GIVES;
            boolean tooBig = blocks + 1 > BuddingRules.GRID;
            boolean notWhole = shards % BuddingRules.CLUSTER_GIVES != 0;
            check(tooBig == !BuddingRules.faultsInGrid(List.of(made),
                            BuddingRules.CLUSTER_GIVES,
                            BuddingRules.GRID).isEmpty(),
                    "a seeding of " + shards + " shard(s) is reported wrongly "
                            + "against the grid");
            check(notWhole == !BuddingRules.faultsInWholeBlocks(List.of(made),
                            BuddingRules.CLUSTER_GIVES).isEmpty(),
                    "a seeding of " + shards + " shard(s) is reported wrongly "
                            + "against the block");
            check(made.cycles() * BuddingRules.CLUSTER_GIVES >= shards,
                    "a seeding costing " + shards + " is said to pay for itself "
                            + "in " + made.cycles() + " cluster(s) of "
                            + BuddingRules.CLUSTER_GIVES);
            check(made.cycles() >= 1,
                    "a seeding costing " + shards + " pays for itself in no "
                            + "clusters at all");
            if (tooBig) {
                overGrid++;
            }
            if (notWhole) {
                ragged++;
            }
            swept++;
        }
        check(overGrid > 0 && ragged > 0,
                "the sweep never built a recipe too big for the grid or never "
                        + "built a ragged price, so it is reporting that none of "
                        + "them are while never having tried one that is");

        System.out.println("  the seeding: " + BuddingRules.BUDDINGS.size()
                + " material(s), every geode this module ships able to be seeded "
                + "and nothing priced that it does not ship, every one paying for "
                + "itself in between " + BuddingRules.LEAST_CYCLES + " and "
                + BuddingRules.MOST_CYCLES + " cluster(s), nothing the foundry "
                + "mines cheaper than anything the game hands you, every one "
                + "of them paid in its own shards, and " + swept + " price(s) swept "
                + "against the grid with " + overGrid + " too big for it and "
                + ragged + " not a whole number of blocks — none of them ours");
    }
}
