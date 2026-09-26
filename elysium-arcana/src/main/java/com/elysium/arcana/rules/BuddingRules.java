package com.elysium.arcana.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How a budding block is made, and the proof that making one is not a way of
 * printing shards.
 *
 * <h2>Why this exists at all</h2>
 *
 * {@link GeodeRules} ships thirty materials with a whole growth chain each: a
 * budding block puts out a small bud, which becomes a medium, which becomes a
 * large, which becomes a cluster you break for shards. What it has never had is
 * a way of <em>getting</em> a budding block other than finding one.
 *
 * <p>Something else in the pack did. `oritech` ships thirty-two compat recipes
 * that assemble a `geore:budding_x` out of two `geore:x_shard`, and those
 * recipes are the reason the GeOre jar is still in the mods folder with DONE
 * written next to it: take the jar out and thirty-two recipes quietly stop
 * loading, because the items they name stop existing. Nothing in the game says
 * so. A feature simply is not there any more.
 *
 * <p>So this file is that capability, in the pack's own items, and the jar can
 * go.
 *
 * <h2>Multiplier, not source</h2>
 *
 * A budding block grows for ever and drops itself when broken, so seeding one
 * is a single price for a permanent source. That is the thing the player is
 * buying and it is meant to pay off; no amount of sampling would ever show it
 * to be wrong.
 *
 * <p>What decides whether the feature is a shortcut round four other modules is
 * what the price is paid <em>in</em>. Paid in the material's own shards it is a
 * multiplier: you must already have thirty-two platinum shards to farm
 * platinum, and where the first thirty-two came from is still the foundry's
 * problem. Paid in anything else it is a source, and every ore in the pack has
 * become obtainable from whichever one is cheapest — the ladder would still be
 * there and nobody would climb it again.
 *
 * <p>{@link #faultsInBootstrap} and {@link #faultsInSmuggling} are that.
 * {@link ArcanaRules} already proves that no arrangement of transmutation makes
 * value out of nothing; this is the same claim about the other half of the
 * module, which had no such proof because until now it had no way in.
 *
 * <h2>And the wait</h2>
 *
 * The long loop is allowed but it has to be a wait somebody notices. Priced
 * under {@link #LEAST_CYCLES} clusters, seeding pays for itself almost at once
 * and finding a geode in the world stops being worth anything. Priced over
 * {@link #MOST_CYCLES}, nobody ever does it and the recipe is a line in a book.
 * {@link #faultsInPayback} holds every material between the two.
 */
public final class BuddingRules {

    private BuddingRules() {
    }

    /**
     * How many shards one grown cluster gives back.
     *
     * <p>The number the loot tables hand out, kept here because the payback
     * arithmetic below is entirely about it. {@code validate.py} reads the
     * cluster loot tables on disk and fails if they disagree, so the two
     * cannot drift — which they would, because one of them is generated and
     * the other is reasoning.
     */
    public static final int CLUSTER_GIVES = 4;

    /** The fewest clusters a seeded block may take to pay for itself. */
    public static final int LEAST_CYCLES = 2;

    /** And the most, past which nobody ever seeds one. */
    public static final int MOST_CYCLES = 10;

    /**
     * How many things fit in the grid a player crafts in.
     *
     * <p>Nine, and it is the constraint that decides the whole price list. The
     * shards go in as blocks — four to a block, which is a recipe this module
     * already had — so a seeding costing more than eight blocks and a catalyst
     * is a recipe that cannot be written down. The game does not say so; it
     * drops the recipe at load and the feature is simply not there.
     */
    public static final int GRID = 9;

    /**
     * One material's price.
     *
     * @param material which geode this seeds
     * @param from     whose shards it takes, which must be its own
     * @param shards   how many of them
     * @param catalyst the other thing it takes, which the pack really makes
     */
    public record Budding(String material, String from, int shards,
                          String catalyst) {

        public Budding {
            if (material == null || material.isEmpty()) {
                throw new IllegalArgumentException("a seeding of nothing");
            }
            if (shards < 1) {
                throw new IllegalArgumentException(material + " is seeded with "
                        + shards + " shard(s), so it is free");
            }
            if (from == null || from.isEmpty()) {
                throw new IllegalArgumentException(material + " is seeded out of "
                        + "nothing in particular, so nothing says whether it is a "
                        + "multiplier or a source");
            }
            if (catalyst == null || catalyst.isEmpty()) {
                throw new IllegalArgumentException(material + " is seeded with "
                        + "shards and nothing else, so the only thing gating it "
                        + "is the thing it makes");
            }
        }

        /**
         * How many grown clusters it takes to get the shards back.
         *
         * <p>Rounded up, because half a cluster is not a cluster — and rounding
         * the other way would make the floor below say a thing it does not
         * mean at the cheapest end, which is exactly where it matters.
         */
        public int cycles() {
            return (shards + CLUSTER_GIVES - 1) / CLUSTER_GIVES;
        }
    }

    /** Something cheap enough for the things the world hands you. */
    public static final String COMMON_CATALYST = "minecraft:glow_ink_sac";

    /** And something that costs a trip for the things it does not. */
    public static final String DEARER_CATALYST = "minecraft:echo_shard";

    /**
     * What each material costs.
     *
     * <p>Two bands, and the gap between them is on purpose: nothing the foundry
     * digs out of the ground is cheaper to seed than anything the game hands
     * you, because the foundry's ladder is what the rest of the pack is priced
     * against and a seeding recipe that ignored it would be a way round it.
     */
    public static final List<Budding> BUDDINGS = List.of(
            // What the game itself digs up: cheap, and the catalyst is common.
            new Budding("coal", "coal", 8, COMMON_CATALYST),
            new Budding("redstone", "redstone", 8, COMMON_CATALYST),
            new Budding("lapis", "lapis", 8, COMMON_CATALYST),
            new Budding("quartz", "quartz", 8, COMMON_CATALYST),
            new Budding("amethyst", "amethyst", 8, COMMON_CATALYST),
            new Budding("glowstone", "glowstone", 12, COMMON_CATALYST),
            new Budding("obsidian", "obsidian", 12, COMMON_CATALYST),
            new Budding("emerald", "emerald", 12, COMMON_CATALYST),
            new Budding("diamond", "diamond", 16, COMMON_CATALYST),
            new Budding("ancient_debris", "ancient_debris", 16, COMMON_CATALYST),

            // And what the foundry mines: dearer, every one of them, and the
            // catalyst costs a trip somewhere.
            new Budding("iron", "iron", 20, DEARER_CATALYST),
            new Budding("copper", "copper", 20, DEARER_CATALYST),
            new Budding("tin", "tin", 20, DEARER_CATALYST),
            new Budding("zinc", "zinc", 20, DEARER_CATALYST),
            new Budding("nickel", "nickel", 20, DEARER_CATALYST),
            new Budding("lead", "lead", 20, DEARER_CATALYST),
            new Budding("aluminum", "aluminum", 24, DEARER_CATALYST),
            new Budding("silver", "silver", 24, DEARER_CATALYST),
            new Budding("gold", "gold", 24, DEARER_CATALYST),
            new Budding("lithium", "lithium", 24, DEARER_CATALYST),
            new Budding("fluorite", "fluorite", 28, DEARER_CATALYST),
            new Budding("certus_quartz", "certus_quartz", 28, DEARER_CATALYST),
            new Budding("osmium", "osmium", 28, DEARER_CATALYST),
            new Budding("crimson_iron", "crimson_iron", 32, DEARER_CATALYST),
            new Budding("azure_silver", "azure_silver", 32, DEARER_CATALYST),
            new Budding("rime_shard", "rime_shard", 32, DEARER_CATALYST),
            new Budding("platinum", "platinum", 32, DEARER_CATALYST),
            new Budding("uranium", "uranium", 32, DEARER_CATALYST),
            new Budding("uraninite", "uraninite", 32, DEARER_CATALYST),
            new Budding("bort", "bort", 32, DEARER_CATALYST));

    /** The price of this material, or null. */
    public static Budding budding(String material) {
        for (Budding one : BUDDINGS) {
            if (one.material().equals(material)) {
                return one;
            }
        }
        return null;
    }

    /** The dearest of the things the game hands you, out of a roster. */
    public static int dearestGiven(List<Budding> roster, List<String> given) {
        int most = 0;
        for (Budding one : roster) {
            if (given.contains(one.material())) {
                most = Math.max(most, one.shards());
            }
        }
        return most;
    }

    /** And the cheapest of the things the foundry mines. */
    public static int cheapestMined(List<Budding> roster, List<String> mined) {
        int least = Integer.MAX_VALUE;
        for (Budding one : roster) {
            if (mined.contains(one.material())) {
                least = Math.min(least, one.shards());
            }
        }
        return least == Integer.MAX_VALUE ? 0 : least;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the seedings. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(BUDDINGS));
        bad.addAll(faultsInCoverage(BUDDINGS, GeodeRules.materials()));
        bad.addAll(faultsInStrangers(BUDDINGS, GeodeRules.materials()));
        bad.addAll(faultsInBootstrap(BUDDINGS));
        bad.addAll(faultsInSmuggling(BUDDINGS, GeodeRules.materials()));
        bad.addAll(faultsInPayback(BUDDINGS, LEAST_CYCLES, MOST_CYCLES));
        bad.addAll(faultsInLadder(BUDDINGS, GeodeRules.MINED,
                GeodeRules.THE_GAMES_OWN));
        bad.addAll(faultsInCatalysts(BUDDINGS));
        bad.addAll(faultsInWholeBlocks(BUDDINGS, CLUSTER_GIVES));
        bad.addAll(faultsInGrid(BUDDINGS, CLUSTER_GIVES, GRID));
        return List.copyOf(bad);
    }

    /** No material priced twice. */
    public static List<String> faultsInNames(List<Budding> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Budding one : roster) {
            if (!seen.add(one.material())) {
                bad.add(one.material() + " is priced twice, and which of the two "
                        + "the game uses is whichever recipe file is written last");
            }
        }
        return bad;
    }

    /** Every geode the module ships can be seeded. */
    public static List<String> faultsInCoverage(List<Budding> roster,
                                                List<String> materials) {
        List<String> bad = new ArrayList<>();
        for (String material : materials) {
            boolean priced = false;
            for (Budding one : roster) {
                if (one.material().equals(material)) {
                    priced = true;
                }
            }
            if (!priced) {
                bad.add("there is no way to seed a " + material + " geode, so it "
                        + "is the one material a player has to go and find — "
                        + "which is a rule nothing states and nobody could guess");
            }
        }
        return bad;
    }

    /** And nothing is priced that the module does not ship. */
    public static List<String> faultsInStrangers(List<Budding> roster,
                                                 List<String> materials) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            if (!materials.contains(one.material())) {
                bad.add(one.material() + " has a price and no geode, so the recipe "
                        + "makes a block this module does not have");
            }
        }
        return bad;
    }

    /**
     * A seeding is a multiplier on what you have, never a source of what you
     * have not.
     *
     * <p>The rule this file is arranged around, and the one that decides
     * whether the whole feature is a shortcut round four other modules.
     *
     * <p>A budding block grows for ever and it drops itself when broken, so
     * seeding one is a single price for a permanent source. Paid in the
     * material's own shards, that is a multiplier: you must already have
     * thirty-two platinum shards before you can farm platinum, and where the
     * first thirty-two came from is still the foundry's problem. Paid in
     * anything else — coal shards, say — it is a <em>source</em>, and every ore
     * in the pack has just become obtainable from the cheapest one. The ore
     * ladder four modules are priced against would still be there; nobody
     * would ever climb it again.
     *
     * <p>oritech's compat recipes, which this file replaces, got this right:
     * two {@code allthemodium_shard} make a {@code budding_allthemodium}. The
     * rule exists to record why, because the version that gets it wrong is
     * easier to write and reads identically.
     */
    public static List<String> faultsInBootstrap(List<Budding> roster) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            if (!one.from().equals(one.material())) {
                bad.add(one.material() + " is seeded out of " + one.from()
                        + " shards, so it is not a multiplier on something a "
                        + "player already has — it is a source of something they "
                        + "have not, and every ore in the pack has just become "
                        + "obtainable from whichever one is cheapest");
            }
        }
        return bad;
    }

    /**
     * And the catalyst is not a second way in.
     *
     * <p>Half of the rule above, arriving through the other ingredient. A
     * seeding paid in its own shards plus a shard of something else is still a
     * recipe that turns one material into another, and the fact that the price
     * is mostly honest does not make the exception one.
     *
     * <p>The check ignores namespaces on purpose, and it cost this file its
     * first choice of catalyst: {@code minecraft:amethyst_shard} is the game's
     * item and {@code elysiumarcana:amethyst_shard} is this module's, and they
     * are different things with the same name. A rule that told them apart
     * would be a rule about namespaces, and the recipe it permitted would be
     * one whose safety depended on which of the two a reader assumed — which
     * is a recipe that gets edited wrong. Anything called {@code x_shard}
     * where {@code x} is a material here is refused, and the catalyst is
     * something that cannot be mistaken for one.
     */
    public static List<String> faultsInSmuggling(List<Budding> roster,
                                                 List<String> materials) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            String named = one.catalyst();
            int at = named.indexOf(':');
            String plain = at < 0 ? named : named.substring(at + 1);
            for (String material : materials) {
                if (plain.equals(material + "_shard")) {
                    bad.add(one.material() + " is seeded with " + named
                            + " as its catalyst, which turns " + material
                            + " into " + one.material() + " by the back door");
                }
            }
        }
        return bad;
    }

    /**
     * The wait is a wait somebody notices, and not one nobody takes.
     *
     * <p>Takes both ends in rather than reading the constants, so a control can
     * move one and be caught by this rule rather than by arithmetic that
     * re-solved itself against the broken number.
     */
    public static List<String> faultsInPayback(List<Budding> roster,
                                               int leastCycles, int mostCycles) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            if (one.cycles() < leastCycles) {
                bad.add(one.material() + " pays for itself in " + one.cycles()
                        + " cluster(s) against a floor of " + leastCycles
                        + ", so seeding is free almost at once and finding a "
                        + "geode in the world stops being worth anything");
            }
        }
        for (Budding one : roster) {
            if (one.cycles() > mostCycles) {
                bad.add(one.material() + " pays for itself in " + one.cycles()
                        + " cluster(s) against a ceiling of " + mostCycles
                        + ", so nobody ever seeds one and the recipe is a line "
                        + "in a book");
            }
        }
        return bad;
    }

    /**
     * Nothing the foundry mines is cheaper than anything the game hands you.
     *
     * <p>The foundry's ladder is what the rest of the pack is priced against.
     * A seeding recipe that let somebody grow platinum for less than coal
     * would not be a cheap recipe, it would be a way round the ladder — and
     * the ladder is load-bearing for four other modules.
     */
    public static List<String> faultsInLadder(List<Budding> roster,
                                              List<String> mined,
                                              List<String> given) {
        List<String> bad = new ArrayList<>();
        int dearestGiven = dearestGiven(roster, given);
        int cheapestMined = cheapestMined(roster, mined);
        if (dearestGiven > 0 && cheapestMined > 0 && cheapestMined <= dearestGiven) {
            bad.add("the cheapest thing the foundry mines is seeded for "
                    + cheapestMined + " shard(s) and the dearest thing the game "
                    + "hands you for " + dearestGiven + ", so seeding is a way "
                    + "round the ladder four other modules are priced against");
        }
        return bad;
    }

    /** Every catalyst is a thing, named the way a thing is named. */
    public static List<String> faultsInCatalysts(List<Budding> roster) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            if (!one.catalyst().contains(":")) {
                bad.add(one.material() + " is seeded with " + one.catalyst()
                        + ", which is not the name of anything — an ingredient "
                        + "with no namespace is a recipe the game drops at load "
                        + "without saying so");
            }
        }
        return bad;
    }

    /**
     * Every price is a whole number of blocks.
     *
     * <p>Because that is what the recipe is made of. Four shards make a block
     * and the seeding takes blocks, so a price of ten shards is two and a half
     * blocks — which is not a thing a crafting grid can hold, and the recipe
     * that tried would either round the price silently or not exist.
     */
    public static List<String> faultsInWholeBlocks(List<Budding> roster, int toABlock) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            if (toABlock > 0 && one.shards() % toABlock != 0) {
                bad.add(one.material() + " costs " + one.shards() + " shard(s) and "
                        + toABlock + " of them make a block, so its price is "
                        + "not a whole number of the thing the recipe is made of "
                        + "— which is a recipe that either rounds the price "
                        + "silently or does not exist");
            }
        }
        return bad;
    }

    /**
     * And every recipe fits in the grid.
     *
     * <p>The blocks plus the catalyst. Past nine, the game drops the recipe at
     * load without saying anything, and the symptom is one material out of
     * thirty that cannot be seeded — which looks exactly like somebody having
     * decided that on purpose.
     */
    public static List<String> faultsInGrid(List<Budding> roster, int toABlock,
                                            int grid) {
        List<String> bad = new ArrayList<>();
        for (Budding one : roster) {
            int blocks = toABlock > 0 ? (one.shards() + toABlock - 1) / toABlock : 0;
            if (blocks + 1 > grid) {
                bad.add(one.material() + " costs " + blocks + " block(s) and a "
                        + "catalyst, which is " + (blocks + 1) + " things in a "
                        + "grid of " + grid + " — so the game drops the recipe at "
                        + "load and one material in thirty quietly cannot be "
                        + "seeded");
            }
        }
        return bad;
    }
}
