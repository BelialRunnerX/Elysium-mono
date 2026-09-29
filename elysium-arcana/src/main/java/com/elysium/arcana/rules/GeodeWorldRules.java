package com.elysium.arcana.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the first shard of every material comes from: a geode in the world.
 *
 * <h2>A chain with no first link</h2>
 *
 * A shard comes from a cluster, a cluster grows out of a budding block, and a
 * budding block is made of shards. Every link is right, and for as long as
 * this module put no geode in any world the chain had no first one: two
 * hundred and ten blocks, sixty items and thirty relics that no survival
 * player could hold. {@link BuddingRules} multiplies the shards a player has.
 * This is where they find the first ones.
 *
 * <h2>Where</h2>
 *
 * Each material once, in a world a player walks to, in the band its ore is
 * dug from. What the overworld digs sits where the game puts its own amethyst
 * geode, six above the bottom to y 30, and the two ores that come only in
 * deepslate sit in deepslate. Emerald sits only under the biomes the game
 * grows emerald ore in. The Nether's own three sit where the game puts them,
 * and the twelve metals with no overworld ore in this pack sit beside their
 * netherrack ore. Nothing waits on a rocket: the first bronze already needs
 * the Nether, and it must not need space as well.
 *
 * <h2>How rare</h2>
 *
 * One chunk in {@link #CHUNKS_PER_SHARD} times the shards its seeding costs.
 * The price is read out of {@link BuddingRules}, never copied, so the two
 * tables cannot drift and a geode is exactly as rare as seeding one is dear:
 * coal at eight shards is one chunk in 64, platinum at thirty-two one in 256.
 * The game's own amethyst geode is one in 24, so every one of these is rarer
 * than a thing a player already knows to be rare. All thirty together come to
 * about one geode in eight overworld chunks and one in eleven nether chunks.
 */
public final class GeodeWorldRules {

    private GeodeWorldRules() {
    }

    /** Chunks per shard of the seeding price: the whole of the rarity. */
    public static final int CHUNKS_PER_SHARD = 8;

    /**
     * The game's own amethyst geode is in one chunk in this many.
     *
     * <p>The floor. A geode as common as that one is on every trip
     * underground, and seeding one would never be worth the shards.
     */
    public static final int THE_GAMES_GEODE = 24;

    /**
     * The most of these one chunk of a world holds, on average: four of the
     * game's own geodes' worth.
     *
     * <p>The thirty come to about one in eight overworld chunks. Past one in
     * six, a crystal wall is round every corner and finding one is not a find.
     */
    public static final double MOST_PER_CHUNK = 4.0 / THE_GAMES_GEODE;

    /**
     * The share of a found crystal wall that is budding, for what the game
     * hands you: the game's own amethyst share. The game's geode arithmetic
     * gives a wall of about 380 blocks (140 to 700), so some thirty budding.
     */
    public static final double GIVEN_BUDDING = 0.083;

    /**
     * And for what the foundry mines, about half: some sixteen in a wall.
     *
     * <p>Every budding block in a found geode grows clusters for ever, so each
     * is a seeding nobody paid for, and a mined seeding costs twenty to
     * thirty-two shards where a given one costs eight to sixteen. Even the
     * smallest wall still holds three or four.
     */
    public static final double MINED_BUDDING = 0.04;

    /** How far off a world's floor and roof a geode is centred: the game's own six. */
    public static final int MARGIN = 6;

    /** The step the game places its own geode in. */
    public static final String STEP = "local_modifications";

    // ==================================================================
    // The worlds
    // ==================================================================

    /** A world a geode can be put in, and its lowest and highest block. */
    public record World(String id, int bottom, int top) {

        public World {
            if (id == null || !id.contains(":")) {
                throw new IllegalArgumentException("a world called " + id
                        + ", which is not the name of anything");
            }
            if (bottom >= top) {
                throw new IllegalArgumentException(id + " has its floor at y "
                        + bottom + " and its roof at y " + top);
            }
        }
    }

    public static final World OVERWORLD = new World("minecraft:overworld", -64, 319);

    public static final World NETHER = new World("minecraft:the_nether", 0, 127);

    /**
     * The worlds a player walks to: the one they wake in, and the one a
     * portal of obsidian opens.
     */
    public static final List<World> ON_FOOT = List.of(OVERWORLD, NETHER);

    // ==================================================================
    // The ground: which biomes, and what rock
    // ==================================================================

    /**
     * Biomes of one world that geodes are put under, and the rock their
     * crystal walls are wrapped in. One biome tag and one biome modifier
     * each.
     *
     * @param name   the tag and the modifier are both called geodes_ and this
     * @param world  the world the biomes are in
     * @param biomes biome ids and #tags; they go into a tag of this module's
     *               own, because a biome modifier takes one tag or a list of
     *               ids, never a list with a tag in it
     * @param middle the layer round the crystal wall
     * @param outer  and the rind round that
     */
    public record Ground(String name, String world, List<String> biomes,
                         String middle, String outer) {

        public Ground {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("a ground with no name, so its "
                        + "tag and its biome modifier are both called geodes_");
            }
            if (world == null || middle == null || outer == null) {
                throw new IllegalArgumentException("the " + name + " geodes are "
                        + "in no world or wrapped in nothing");
            }
            if (biomes == null || biomes.isEmpty()) {
                throw new IllegalArgumentException("the " + name + " geodes are "
                        + "under no biome, so nothing places them");
            }
            biomes = List.copyOf(biomes);
        }
    }

    /**
     * Under every overworld biome, in calcite and smooth basalt: the rind of
     * the game's own geode, so a player who has found one amethyst geode
     * knows this for a geode on sight. The crystal inside says which.
     */
    public static final Ground OVERWORLD_BIOMES = new Ground("overworld",
            "minecraft:overworld", List.of("#minecraft:is_overworld"),
            "minecraft:calcite", "minecraft:smooth_basalt");

    /**
     * Under the ten biomes the game grows emerald ore in, and no others: the
     * six it tags as mountains, the three windswept hills (which it called
     * mountains until 1.18) and the grove.
     */
    public static final Ground MOUNTAIN_BIOMES = new Ground("mountains",
            "minecraft:overworld",
            List.of("#minecraft:is_mountain", "#minecraft:is_hill", "minecraft:grove"),
            "minecraft:calcite", "minecraft:smooth_basalt");

    /**
     * Under all five nether biomes, in blackstone and basalt: the Nether's own
     * dark rock, which shows against netherrack the way calcite shows against
     * stone.
     */
    public static final Ground NETHER_BIOMES = new Ground("nether",
            "minecraft:the_nether", List.of("#minecraft:is_nether"),
            "minecraft:blackstone", "minecraft:basalt");

    /** Every ground, one biome modifier each. */
    public static final List<Ground> GROUNDS =
            List.of(OVERWORLD_BIOMES, MOUNTAIN_BIOMES, NETHER_BIOMES);

    // ==================================================================
    // The bands
    // ==================================================================

    /** Heights a geode's centre is drawn from, evenly, both ends included. */
    public record Band(int lowest, int highest) {

        public Band {
            if (lowest > highest) {
                throw new IllegalArgumentException("a band from y " + lowest
                        + " up to y " + highest + ", which holds no height at all");
            }
        }
    }

    /**
     * Where the game puts its own amethyst geode: six above the bottom to
     * y 30. Every overworld ore of the twelve that sit here is dug somewhere
     * in it.
     */
    public static final Band THE_GAMES_BAND = new Band(-58, 30);

    /** Deepslate, the only rock crimson iron and azure silver ore come in. */
    public static final Band DEEPSLATE = new Band(-58, 0);

    /** The netherrack ores' own band. */
    public static final Band NETHERRACK = new Band(10, 118);

    /** The top half of the Nether, where glowstone hangs from the roof. */
    public static final Band UNDER_THE_ROOF = new Band(64, 118);

    /** Where the game hides ancient debris. */
    public static final Band ON_THE_FLOOR = new Band(8, 24);

    // ==================================================================
    // The geodes
    // ==================================================================

    /**
     * One material's geode: the ground it is under and the band it is in.
     *
     * <p>How rare it is and how much of it buds are not written in a row.
     * They are worked out, from the seeding price and from which list the
     * material is on, so there is nothing in a row to get out of step.
     */
    public record Find(String material, Ground ground, Band band) {

        public Find {
            if (material == null || material.isEmpty()) {
                throw new IllegalArgumentException("a geode of nothing");
            }
            if (ground == null || band == null) {
                throw new IllegalArgumentException("a " + material
                        + " geode that is nowhere");
            }
        }

        /** The configured feature and the placed feature are both called this. */
        public String id() {
            return material + "_geode";
        }

        /** One chunk in this many holds one. */
        public int chance() {
            return chanceOf(material, BuddingRules.BUDDINGS, CHUNKS_PER_SHARD);
        }

        /** The share of its crystal wall that is budding. */
        public double budding() {
            return GeodeRules.MINED.contains(material) ? MINED_BUDDING : GIVEN_BUDDING;
        }
    }

    /** Every geode there is, one per material. */
    public static final List<Find> FINDS = List.of(
            // What the overworld digs, where the game puts its own geode.
            new Find("coal", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("redstone", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("lapis", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("amethyst", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("obsidian", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("diamond", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("iron", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("copper", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("gold", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("certus_quartz", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("platinum", OVERWORLD_BIOMES, THE_GAMES_BAND),
            new Find("rime_shard", OVERWORLD_BIOMES, THE_GAMES_BAND),

            // Their ores come in deepslate and nothing else.
            new Find("crimson_iron", OVERWORLD_BIOMES, DEEPSLATE),
            new Find("azure_silver", OVERWORLD_BIOMES, DEEPSLATE),

            // Only where the game grows emerald ore.
            new Find("emerald", MOUNTAIN_BIOMES, THE_GAMES_BAND),

            // The Nether's own quartz, and the twelve metals with no overworld
            // ore in this pack, beside their netherrack ore.
            new Find("quartz", NETHER_BIOMES, NETHERRACK),
            new Find("tin", NETHER_BIOMES, NETHERRACK),
            new Find("zinc", NETHER_BIOMES, NETHERRACK),
            new Find("nickel", NETHER_BIOMES, NETHERRACK),
            new Find("lead", NETHER_BIOMES, NETHERRACK),
            new Find("aluminum", NETHER_BIOMES, NETHERRACK),
            new Find("silver", NETHER_BIOMES, NETHERRACK),
            new Find("lithium", NETHER_BIOMES, NETHERRACK),
            new Find("fluorite", NETHER_BIOMES, NETHERRACK),
            new Find("osmium", NETHER_BIOMES, NETHERRACK),
            new Find("uranium", NETHER_BIOMES, NETHERRACK),
            new Find("uraninite", NETHER_BIOMES, NETHERRACK),
            new Find("bort", NETHER_BIOMES, NETHERRACK),

            // Glowstone hangs from the roof; ancient debris lies on the floor.
            new Find("glowstone", NETHER_BIOMES, UNDER_THE_ROOF),
            new Find("ancient_debris", NETHER_BIOMES, ON_THE_FLOOR));

    /**
     * One chunk in how many holds a geode of this material: its seeding price
     * in shards times the chunks per shard, or nothing if it has no price.
     */
    public static int chanceOf(String material, List<BuddingRules.Budding> roster,
                               int perShard) {
        for (BuddingRules.Budding one : roster) {
            if (one.material().equals(material)) {
                return perShard * one.shards();
            }
        }
        return 0;
    }

    /** The world a geode is in, out of these, or null. */
    public static World worldOf(Find find, List<World> worlds) {
        for (World world : worlds) {
            if (world.id().equals(find.ground().world())) {
                return world;
            }
        }
        return null;
    }

    /** How many geodes one chunk of each world holds, on average. */
    public static Map<String, Double> perChunk(List<Find> finds,
                                               List<BuddingRules.Budding> roster,
                                               int perShard) {
        Map<String, Double> held = new LinkedHashMap<>();
        for (Find find : finds) {
            int chance = chanceOf(find.material(), roster, perShard);
            if (chance > 0) {
                held.merge(find.ground().world(), 1.0 / chance, Double::sum);
            }
        }
        return held;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with where the geodes are. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInCoverage(FINDS, GeodeRules.materials()));
        bad.addAll(faultsInStrangers(FINDS, GeodeRules.materials()));
        bad.addAll(faultsInWorlds(FINDS, ON_FOOT));
        bad.addAll(faultsInBands(FINDS, ON_FOOT, MARGIN));
        bad.addAll(faultsInRarity(FINDS, BuddingRules.BUDDINGS, CHUNKS_PER_SHARD,
                THE_GAMES_GEODE));
        bad.addAll(faultsInCrowding(FINDS, BuddingRules.BUDDINGS, CHUNKS_PER_SHARD,
                MOST_PER_CHUNK));
        bad.addAll(faultsInGrounds(GROUNDS, FINDS));
        bad.addAll(faultsInRinds(GROUNDS));
        bad.addAll(faultsInBudding(GIVEN_BUDDING, MINED_BUDDING));
        return List.copyOf(bad);
    }

    /**
     * Every material is found somewhere, and once.
     *
     * <p>The rule this file exists for. A material with no geode is one whose
     * first shard comes from nowhere, which is what all thirty were.
     */
    public static List<String> faultsInCoverage(List<Find> finds,
                                                List<String> materials) {
        List<String> bad = new ArrayList<>();
        for (String material : materials) {
            int found = 0;
            for (Find find : finds) {
                if (find.material().equals(material)) {
                    found++;
                }
            }
            if (found == 0) {
                bad.add("there is no " + material + " geode in any world, so its "
                        + "first shard comes from nowhere: a shard comes from a "
                        + "cluster, a cluster from a budding block and a budding "
                        + "block from shards");
            } else if (found > 1) {
                bad.add(material + " is found in " + found + " places, and they all "
                        + "write the one feature file, so whichever is written "
                        + "last is the only one there is");
            }
        }
        return bad;
    }

    /** And no geode is of something the module does not ship. */
    public static List<String> faultsInStrangers(List<Find> finds,
                                                 List<String> materials) {
        List<String> bad = new ArrayList<>();
        for (Find find : finds) {
            if (!materials.contains(find.material())) {
                bad.add("a " + find.material() + " geode names blocks this module "
                        + "does not register, so the game refuses the feature and "
                        + "the world does not load");
            }
        }
        return bad;
    }

    /**
     * Every geode is in a world a player walks to.
     *
     * <p>Never one that needs a rocket. A first shard that waited on space
     * would wait on everything that comes before the rocket, which is most of
     * the pack.
     */
    public static List<String> faultsInWorlds(List<Find> finds, List<World> onFoot) {
        List<String> bad = new ArrayList<>();
        for (Find find : finds) {
            if (worldOf(find, onFoot) == null) {
                bad.add("the " + find.material() + " geode is in "
                        + find.ground().world() + ", which a player does not walk "
                        + "to, so the first " + find.material() + " shard waits on "
                        + "a rocket and everything that comes before it");
            }
        }
        return bad;
    }

    /**
     * Every band is inside its world, off the floor and the roof by the
     * game's own margin.
     *
     * <p>A geode is a ball some ten blocks across. Centred on the floor, half
     * of it is bedrock it may not replace, and what is left is a bowl.
     */
    public static List<String> faultsInBands(List<Find> finds, List<World> worlds,
                                             int margin) {
        List<String> bad = new ArrayList<>();
        for (Find find : finds) {
            World world = worldOf(find, worlds);
            if (world == null) {
                continue;                          // faultsInWorlds says why
            }
            if (find.band().lowest() < world.bottom() + margin) {
                bad.add("the " + find.material() + " geode is drawn from y "
                        + find.band().lowest() + ", less than " + margin
                        + " above the floor of " + world.id() + " at y "
                        + world.bottom() + ", so part of it is in the bedrock");
            }
            if (find.band().highest() > world.top() - margin) {
                bad.add("the " + find.material() + " geode is drawn up to y "
                        + find.band().highest() + ", less than " + margin
                        + " under the roof of " + world.id() + " at y "
                        + world.top() + ", so part of it is outside the world");
            }
        }
        return bad;
    }

    /**
     * A geode is as rare as seeding one is dear, and rarer than the game's
     * own.
     *
     * <p>Takes the roster and both numbers rather than reading the constants,
     * so a control can move one and be caught by this rule rather than by
     * arithmetic that re-solved itself against the broken number.
     */
    public static List<String> faultsInRarity(List<Find> finds,
                                              List<BuddingRules.Budding> roster,
                                              int perShard, int floor) {
        List<String> bad = new ArrayList<>();
        for (Find find : finds) {
            int chance = chanceOf(find.material(), roster, perShard);
            if (chance <= 0) {
                bad.add(find.material() + " has no seeding price, so nothing says "
                        + "how rare its geode is and the rarity filter is handed a "
                        + "number the game refuses");
            } else if (chance < floor) {
                bad.add("a " + find.material() + " geode is in one chunk in "
                        + chance + ", commoner than the game's own amethyst geode "
                        + "at one in " + floor + ", so it is on every trip "
                        + "underground and seeding one is never worth the shards");
            }
        }
        return bad;
    }

    /** And no world is so full of them that finding one is not a find. */
    public static List<String> faultsInCrowding(List<Find> finds,
                                                List<BuddingRules.Budding> roster,
                                                int perShard, double most) {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Double> world
                : perChunk(finds, roster, perShard).entrySet()) {
            if (world.getValue() > most) {
                bad.add(world.getKey() + " has a geode of this module in one chunk "
                        + "in " + Math.round(1.0 / world.getValue()) + ", where the "
                        + "most allowed is one in " + Math.round(1.0 / most)
                        + ", four of the game's own amethyst geodes: a crystal wall "
                        + "round every corner is not a find");
            }
        }
        return bad;
    }

    /**
     * Every ground has a name of its own and holds a geode, and every geode is
     * under a ground that is written out.
     *
     * <p>A ground is one biome tag and one biome modifier. Two with one name
     * write the same two files; an empty one is a modifier that adds nothing;
     * and a geode under a ground the list does not hold is in no modifier at
     * all, so it is placed nowhere and nothing says so.
     */
    public static List<String> faultsInGrounds(List<Ground> grounds, List<Find> finds) {
        List<String> bad = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Ground ground : grounds) {
            if (!names.add(ground.name())) {
                bad.add("two grounds are called " + ground.name() + " and both "
                        + "write geodes_" + ground.name() + ", so whichever is "
                        + "written last is the only one placed");
            }
            boolean used = false;
            for (Find find : finds) {
                if (find.ground().equals(ground)) {
                    used = true;
                }
            }
            if (!used) {
                bad.add("the " + ground.name() + " ground holds no geode, so its "
                        + "biome modifier adds nothing to any biome");
            }
        }
        for (Find find : finds) {
            if (!grounds.contains(find.ground())) {
                bad.add("the " + find.material() + " geode is under the "
                        + find.ground().name() + " ground, which is not written "
                        + "out, so no biome modifier names it and it is placed "
                        + "nowhere");
            }
        }
        return bad;
    }

    /**
     * A geode's rind is the game's own rock, never this module's blocks.
     *
     * <p>The seeding's rule from the other side. A geode of one material
     * wrapped in another's blocks hands out the second for finding the first,
     * which is the back door by a different route.
     */
    public static List<String> faultsInRinds(List<Ground> grounds) {
        List<String> bad = new ArrayList<>();
        for (Ground ground : grounds) {
            for (String block : List.of(ground.middle(), ground.outer())) {
                if (!block.startsWith("minecraft:")) {
                    bad.add("the " + ground.name() + " geodes are wrapped in "
                            + block + ", which is not the game's own rock, so "
                            + "finding one hands out a block that is not what the "
                            + "geode is of");
                }
            }
            if (ground.middle().equals(ground.outer())) {
                bad.add("the " + ground.name() + " geodes are one rock through "
                        + "both layers, so the rind that tells a player this is a "
                        + "geode is not there");
            }
        }
        return bad;
    }

    /**
     * A found geode buds, and buds less for the dearer seedings.
     *
     * <p>Its crystals grow only on budding blocks, so a geode with none is a
     * wall that never grows a shard; and a share is a chance, which the game's
     * own feature refuses outside nought to one.
     */
    public static List<String> faultsInBudding(double given, double mined) {
        List<String> bad = new ArrayList<>();
        for (double share : new double[]{given, mined}) {
            if (share <= 0.0) {
                bad.add("a found crystal wall is " + share + " budding, and its "
                        + "crystals grow only on budding blocks, so the geode is "
                        + "a wall that never grows a shard");
            }
            if (share > 1.0) {
                bad.add("a found crystal wall is " + share + " budding, a chance "
                        + "over one, which the game's own feature refuses, so the "
                        + "world does not load");
            }
        }
        if (mined > given) {
            bad.add("a geode of what the foundry mines is " + mined + " budding "
                    + "and one of what the game hands you " + given + ", so the "
                    + "dearer seeding is the one found for nothing more often");
        }
        return bad;
    }
}
