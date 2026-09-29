package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Brushing: the slowest way to get anything, and the only way to get some of it.
 *
 * <h2>What the game already has, and what is wrong with it</h2>
 *
 * The game ships one brush, two suspicious blocks and a handful of sherds. The
 * brush is deliberately slow — that is the point of it — but it never gets any
 * faster, so the hundredth site is exactly as tedious as the first and nobody
 * digs a hundredth site. A tier ladder fixes that, and it has to fix it
 * without ever making brushing <em>fast</em>, because a fast brush is a block
 * that drops its loot when you hit it and there is no reason for the mechanic
 * to exist.
 *
 * <h2>The rule the whole thing rests on</h2>
 *
 * What a site holds is decided by <em>where</em> it is, not by when it is
 * brushed. A player who brushes a block halfway, walks away and comes back has
 * to find the same thing waiting; a player who does not like what they found
 * must not be able to break the block, place it again and roll for something
 * better. {@link #whatIsBuried} is a function of the position and the seed and
 * of nothing else, and that is what makes the loot a place rather than a
 * lottery.
 */
public final class DigRules {

    private DigRules() {
    }

    // ==================================================================
    // The brushes
    // ==================================================================

    /**
     * One brush.
     *
     * @param name  what it is called
     * @param ticks how long one brushing takes
     * @param reach how many blocks out it can be used from
     */
    public record Brush(String name, int ticks, float reach) {

        public Brush {
            Objects.requireNonNull(name, "a brush with no name");
            if (ticks < FASTEST) {
                throw new IllegalArgumentException(name + " brushes in " + ticks
                        + " ticks, and the fastest a brush may ever be is " + FASTEST
                        + " — below that it is a block that drops its loot when you "
                        + "hit it, and the mechanic has no reason to exist");
            }
            if (reach <= 0.0F) {
                throw new IllegalArgumentException(name + " cannot reach anything");
            }
        }

        /** How many brushings a whole site takes at this speed, in ticks. */
        public int aWholeSite() {
            return ticks * STAGES;
        }
    }

    /**
     * The fastest any brush may be.
     *
     * <p>The game's own is ten. Half that is still four times as slow as
     * breaking the block would be, which is the band the mechanic lives in.
     */
    public static final int FASTEST = 4;

    /** How many stages a site goes through before it gives anything up. */
    public static final int STAGES = 4;

    /** The game's own brush, which ours are measured against. */
    public static final int VANILLA_TICKS = 10;

    /** Every brush, slowest first. */
    public static final List<Brush> BRUSHES = List.of(
            new Brush("iron_brush", 8, 4.0F),
            new Brush("diamond_brush", 6, 4.5F),
            new Brush("netherite_brush", 4, 5.0F));

    /** The brush of that name, or null. */
    public static Brush brush(String name) {
        for (Brush one : BRUSHES) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // What can be brushed, and what comes out
    // ==================================================================

    /**
     * One thing that can be brushed.
     *
     * @param block what it is
     * @param below the block it leaves behind
     * @param pool  the name of what it holds
     */
    public record Site(String block, String below, String pool) {

        public Site {
            Objects.requireNonNull(block, "a site that is no block");
            Objects.requireNonNull(below, "a site that leaves nothing behind");
            if (block.equals(below)) {
                throw new IllegalArgumentException(block + " leaves itself behind, so "
                        + "it can be brushed for ever");
            }
        }
    }

    /** Every brushable block this module adds. */
    public static final List<Site> SITES = List.of(
            new Site("suspicious_dirt", "minecraft:dirt", "dirt"),
            new Site("suspicious_red_sand", "minecraft:red_sand", "red_sand"),
            new Site("fossiliferous_dirt", "minecraft:coarse_dirt", "fossil"));

    /**
     * One thing a site can give up, and how likely it is.
     *
     * @param item   what it is
     * @param weight how likely, against the rest of its pool
     */
    public record Find(String item, int weight) {

        public Find {
            Objects.requireNonNull(item, "a find that is nothing");
            if (weight < 1) {
                throw new IllegalArgumentException(item + " has a weight of " + weight
                        + ", so it can never be found and is a thing that does not "
                        + "exist");
            }
        }
    }

    /** What this module's own finds are called, in a pool. */
    private static final String OURS = "elysiumarsenal:";

    /**
     * How likely each half of each fossil is, against the rest of the fossil
     * pool.
     *
     * <p>Two, for fourteen halves: twenty-eight of the pool's 133, so about
     * one fossiliferous site in five gives up a half and one in 67 gives up a
     * particular one. Rarer than a shard, and far above
     * {@link #RAREST_WORTH_HAVING}, so a player after a pair can dig for it.
     */
    public static final int HALF_A_FOSSIL = 2;

    /** What each pool holds. */
    public static final Map<String, List<Find>> POOLS = pools();

    private static Map<String, List<Find>> pools() {
        Map<String, List<Find>> made = new LinkedHashMap<>();
        // The temperate dirt every player starts on: the rotten log a rotten
        // set is built from, and the growth totem, found where farms are.
        made.put("dirt", List.of(
                new Find("minecraft:bone", 30),
                new Find("minecraft:flint", 24),
                new Find("minecraft:clay_ball", 18),
                new Find("elysiumarsenal:artifact_shard", 12),
                new Find("elysiumarsenal:rotten_log", 10),
                new Find("minecraft:emerald", 8),
                new Find("minecraft:music_disc_relic", 4),
                new Find("elysiumarsenal:growth_totem", 2),
                new Find("elysiumarsenal:legend_sigil", 1)));
        // The badlands: both mud bricks, and the torrent totem, the one find
        // a player has to travel for.
        made.put("red_sand", List.of(
                new Find("minecraft:bone", 28),
                new Find("minecraft:gunpowder", 22),
                new Find("minecraft:gold_nugget", 18),
                new Find("elysiumarsenal:artifact_shard", 14),
                new Find("elysiumarsenal:cracked_mud_bricks", 10),
                new Find("minecraft:gold_ingot", 9),
                new Find("minecraft:diamond", 5),
                new Find("elysiumarsenal:infested_mud_bricks", 3),
                new Find("elysiumarsenal:torrent_totem", 3),
                new Find("elysiumarsenal:legend_sigil", 1)));
        // The seam under every overworld biome: the fossils, and the soul and
        // radiance totems.
        List<Find> fossil = new ArrayList<>(List.of(
                new Find("minecraft:bone", 34),
                new Find("minecraft:bone_meal", 20),
                new Find("elysiumarsenal:chiseled_bone_block", 16),
                new Find("elysiumarsenal:artifact_shard", 16),
                new Find("minecraft:amethyst_shard", 8),
                new Find("minecraft:echo_shard", 5),
                new Find("elysiumarsenal:soul_totem", 3),
                new Find("elysiumarsenal:radiance_totem", 2),
                new Find("elysiumarsenal:legend_sigil", 1)));
        // Every half of every fossil, walked from the roster rather than
        // listed: an eighth creature there is two more finds here, and a half
        // no pool holds is a fossil nobody can make whole.
        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            fossil.add(new Find(OURS + one.id(FossilRules.SKULL), HALF_A_FOSSIL));
            fossil.add(new Find(OURS + one.id(FossilRules.BODY), HALF_A_FOSSIL));
        }
        made.put("fossil", List.copyOf(fossil));
        return Map.copyOf(made);
    }

    /** The weights of a pool, added up. */
    public static int weightOf(String pool) {
        int total = 0;
        for (Find one : POOLS.getOrDefault(pool, List.of())) {
            total += one.weight();
        }
        return total;
    }

    /**
     * What is buried at a place.
     *
     * <h2>Why this takes a position and not a random number</h2>
     *
     * Because a site has to be a place rather than a lottery. A player who
     * brushes halfway, logs out and comes back must find the same thing; and a
     * player who does not like what they found must not be able to break the
     * block, put it back and roll again. Both of those are the same bug, and
     * both of them are only avoided by the answer being a function of where
     * the block is.
     *
     * <p>The mixing is deliberate rather than decorative. Multiplying the
     * three coordinates by different large odd numbers and stirring the result
     * is what stops two sites a block apart from holding the same thing — a
     * hash that simply added the coordinates would give a whole diagonal plane
     * of identical loot, which a player notices in about ten minutes.
     */
    public static Find whatIsBuried(String pool, long seed, int x, int y, int z) {
        List<Find> finds = POOLS.get(pool);
        if (finds == null || finds.isEmpty()) {
            return null;
        }
        long mixed = seed;
        mixed = mixed * 6_364_136_223_846_793_005L + (x * 3_129_871L);
        mixed = mixed * 6_364_136_223_846_793_005L + (y * 116_129_781L);
        mixed = mixed * 6_364_136_223_846_793_005L + (z * 1_296_871L);
        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);
        int landed = (int) Math.floorMod(mixed, weightOf(pool));
        for (Find one : finds) {
            landed -= one.weight();
            if (landed < 0) {
                return one;
            }
        }
        return finds.get(finds.size() - 1);
    }

    // ==================================================================
    // Where the sites are
    // ==================================================================

    /**
     * The widest patch the game will load.
     *
     * <p>A disk's radius goes through a codec that stops at eight, and a file
     * past that is not a wide patch: it is a world that will not load, found
     * at the moment somebody presses Create World.
     */
    public static final int WIDEST_PATCH = 8;

    /** The largest vein the game will load, for the same reason: its codec stops at 64. */
    public static final int LARGEST_SEAM = 64;

    /** The overworld's floor. */
    public static final int WORLD_FLOOR = -64;

    /** The first height above the overworld's ceiling. */
    public static final int WORLD_CEILING = 320;

    /**
     * A patch of sites on the surface: the game's own disk, one block deep,
     * laid over the ground the site looks like.
     *
     * @param site     the site it lays down
     * @param oneIn    one chunk in this many gets a patch
     * @param least    the smallest radius a patch has
     * @param most     the largest
     * @param replaces the only blocks it may take the place of
     * @param biomes   where it lies: each a biome's id or a #tag of them
     */
    public record Patch(String site, int oneIn, int least, int most,
                        List<String> replaces, List<String> biomes) {

        public Patch {
            Objects.requireNonNull(site, "a patch of nothing");
            if (oneIn < 1) {
                throw new IllegalArgumentException(site + " lies in one chunk in "
                        + oneIn + ", which is no chunk at all");
            }
            if (least < 1 || most < least) {
                throw new IllegalArgumentException(site + "'s patches run from " + least
                        + " to " + most + " out, which is no size at all");
            }
            if (most > WIDEST_PATCH) {
                throw new IllegalArgumentException(site + "'s patches reach " + most
                        + " out, and the game refuses a disk past " + WIDEST_PATCH
                        + ": the world would not load");
            }
            replaces = List.copyOf(Objects.requireNonNull(replaces,
                    site + " may take the place of nothing"));
            biomes = List.copyOf(Objects.requireNonNull(biomes, site + " lies nowhere"));
            if (replaces.isEmpty()) {
                throw new IllegalArgumentException(site + " may take the place of "
                        + "nothing, so no patch of it is ever laid");
            }
            if (biomes.isEmpty()) {
                throw new IllegalArgumentException(site + " lies in no biome");
            }
        }

        /** How many sites one patch lays on flat ground, on average over its radii. */
        public double sitesInAPatch() {
            double total = 0.0D;
            for (int radius = least; radius <= most; radius++) {
                total += columnsWithin(radius);
            }
            return total / (most - least + 1);
        }

        /** How many sites a chunk holds on flat ground, on average. */
        public double sitesAChunk() {
            return sitesInAPatch() / oneIn;
        }
    }

    /**
     * How many columns a disk of that radius lays: the game's own test, the
     * two distances across squared and added, no more than the radius
     * squared. One out is five columns and two out is thirteen.
     */
    public static int columnsWithin(int radius) {
        int columns = 0;
        for (int across = -radius; across <= radius; across++) {
            for (int along = -radius; along <= radius; along++) {
                if (across * across + along * along <= radius * radius) {
                    columns++;
                }
            }
        }
        return columns;
    }

    /**
     * Sites buried the way the game buries its fossils: small veins that take
     * the place of the ground they are told they may, and of nothing else.
     *
     * @param site    the site it buries
     * @param tries   how many veins a chunk tries for
     * @param size    how big a vein is, in the game's own measure
     * @param lowest  the lowest a vein may lie
     * @param highest the highest
     * @param within  the block tag a vein may take the place of
     * @param biomes  where it lies: each a biome's id or a #tag of them
     */
    public record Seam(String site, int tries, int size, int lowest, int highest,
                       String within, List<String> biomes) {

        public Seam {
            Objects.requireNonNull(site, "a seam of nothing");
            Objects.requireNonNull(within, site + " may take the place of nothing");
            if (tries < 1) {
                throw new IllegalArgumentException(site + " is tried for " + tries
                        + " times a chunk, which is never");
            }
            if (size < 1 || size > LARGEST_SEAM) {
                throw new IllegalArgumentException(site + "'s veins are " + size
                        + " big, and the game loads a vein of 1 to " + LARGEST_SEAM);
            }
            if (lowest >= highest) {
                throw new IllegalArgumentException(site + " lies between " + lowest
                        + " and " + highest + ", which is no height at all");
            }
            if (lowest < WORLD_FLOOR || highest >= WORLD_CEILING) {
                throw new IllegalArgumentException(site + " lies from " + lowest + " to "
                        + highest + ", and the overworld runs from " + WORLD_FLOOR
                        + " to " + (WORLD_CEILING - 1));
            }
            biomes = List.copyOf(Objects.requireNonNull(biomes, site + " lies nowhere"));
            if (biomes.isEmpty()) {
                throw new IllegalArgumentException(site + " lies in no biome");
            }
        }
    }

    /**
     * The two patches on the surface.
     *
     * <p>Suspicious dirt lies in the temperate lands where every player
     * starts: one chunk in ten gets a patch of five to thirteen sites, nine
     * on flat ground on average. Red sand lies in the badlands only, one
     * chunk in six, so a player who travels for it finds it.
     */
    public static final List<Patch> PATCHES = List.of(
            new Patch("suspicious_dirt", 10, 1, 2,
                    List.of("minecraft:grass_block", "minecraft:dirt",
                            "minecraft:coarse_dirt", "minecraft:podzol"),
                    List.of("#minecraft:is_forest", "#minecraft:is_taiga",
                            "minecraft:plains", "minecraft:sunflower_plains",
                            "minecraft:meadow")),
            new Patch("suspicious_red_sand", 6, 1, 2,
                    List.of("minecraft:red_sand"),
                    List.of("#minecraft:is_badlands")));

    /**
     * The one seam: fossiliferous dirt, three small veins a chunk through the
     * band the game scatters its own underground dirt through (0 to 160),
     * under every biome of the overworld. A vein takes only dirt, so it lies
     * in the dirt under the grass and in the pockets of dirt in the stone.
     */
    public static final List<Seam> SEAMS = List.of(
            new Seam("fossiliferous_dirt", 3, 6, 0, 160, "minecraft:dirt",
                    List.of("#minecraft:is_overworld")));

    // ==================================================================
    // Whether any of it hangs together
    // ==================================================================

    /** Every brush that is not faster than the one below it. */
    public static List<String> ladderDips() {
        List<String> bad = new ArrayList<>();
        for (int rung = 1; rung < BRUSHES.size(); rung++) {
            Brush below = BRUSHES.get(rung - 1);
            Brush above = BRUSHES.get(rung);
            if (above.ticks() >= below.ticks()) {
                bad.add(above.name() + " brushes no faster than " + below.name()
                        + ", so that rung buys nothing and nobody makes one");
            }
            if (above.reach() < below.reach()) {
                bad.add(above.name() + " reaches less far than " + below.name());
            }
        }
        return List.copyOf(bad);
    }

    /** Every brush that is not slower than simply breaking the block. */
    public static List<String> tooFast() {
        List<String> bad = new ArrayList<>();
        for (Brush one : BRUSHES) {
            if (one.ticks() < FASTEST) {
                bad.add(one.name() + " brushes in " + one.ticks() + " ticks, which is "
                        + "faster than the floor, so it is a block that drops its "
                        + "loot when you hit it");
            }
        }
        return List.copyOf(bad);
    }

    /** Every brush no slower than the game's own, at the bottom of the ladder. */
    public static List<String> replacingTheGamesBrush() {
        List<String> bad = new ArrayList<>();
        Brush slowest = BRUSHES.isEmpty() ? null : BRUSHES.get(0);
        if (slowest != null && slowest.ticks() < VANILLA_TICKS / 2) {
            bad.add(slowest.name() + " is the first rung and is already more than "
                    + "twice the game's own brush, so the game's brush is a crafting "
                    + "ingredient rather than a tool");
        }
        return List.copyOf(bad);
    }

    /** Every site that holds a pool nothing fills. */
    public static List<String> emptySites() {
        List<String> bad = new ArrayList<>();
        for (Site site : SITES) {
            List<Find> finds = POOLS.get(site.pool());
            if (finds == null || finds.isEmpty()) {
                bad.add(site.block() + " holds the " + site.pool() + " pool and "
                        + "nothing is in it, so brushing it gives nothing and the "
                        + "player has no way to know that is a bug");
            }
        }
        for (String pool : POOLS.keySet()) {
            boolean used = false;
            for (Site site : SITES) {
                if (site.pool().equals(pool)) {
                    used = true;
                }
            }
            if (!used) {
                bad.add("the " + pool + " pool is filled and nothing holds it");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every pool whose rarest find is so rare nobody will ever see it.
     *
     * <p>A weight of one in a thousand is not rare, it is absent: a player
     * would have to brush a thousand sites, and brushing a site takes half a
     * minute. The floor here is what keeps "rare" meaning something a person
     * could actually reach.
     */
    public static final double RAREST_WORTH_HAVING = 1.0D / 200.0D;

    public static List<String> unreachablyRare() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, List<Find>> row : POOLS.entrySet()) {
            int total = weightOf(row.getKey());
            for (Find one : row.getValue()) {
                double chance = (double) one.weight() / total;
                if (chance < RAREST_WORTH_HAVING) {
                    bad.add(one.item() + " turns up in one site in "
                            + Math.round(1.0D / chance) + ", which at half a minute a "
                            + "site is not rare, it is absent");
                }
            }
        }
        return List.copyOf(bad);
    }

    /**
     * The dig's own finds that are neither fossils nor totems: the log a
     * rotten set is built from, and the two mud bricks the badlands bury.
     */
    public static final List<String> TURNED_UP = List.of(
            "rotten_log", "cracked_mud_bricks", "infested_mud_bricks");

    /**
     * Everything the dig promises that no pool holds: every half of every
     * fossil, every totem, and the finds above.
     *
     * <p>Brushing is the only thing in the game that hands most of these out
     * (cracked mud bricks can be smelted as well). One missing from every
     * pool is a block a survival player can never have, which is where all
     * of them stood until 29 September 2026: the pools held the game's own
     * things, shards, chiselled bone and the sigil, and nothing else.
     */
    public static List<String> neverDugUp() {
        return neverDugUp(POOLS);
    }

    /** The same, over any pools: the harness hands it empty ones to be caught. */
    public static List<String> neverDugUp(Map<String, List<Find>> pools) {
        Set<String> held = new TreeSet<>();
        for (List<Find> finds : pools.values()) {
            for (Find one : finds) {
                held.add(one.item());
            }
        }
        List<String> promised = new ArrayList<>();
        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            promised.add(one.id(FossilRules.SKULL));
            promised.add(one.id(FossilRules.BODY));
        }
        for (TotemRules.Totem one : TotemRules.TOTEMS) {
            promised.add(one.name());
        }
        promised.addAll(TURNED_UP);
        List<String> bad = new ArrayList<>();
        for (String name : promised) {
            if (!held.contains(OURS + name)) {
                bad.add(name + " is in no pool, so nothing in a survival world hands "
                        + "it out");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every site nothing lays in a world, and every placement of something
     * that is not a site.
     *
     * <p>The first is the bug this was written for. Until 29 September 2026
     * the three sites were registered, drawn, named and brushable, and no
     * world ever held one: nothing laid a site down, so the whole dig could
     * be had from a command and nowhere else.
     */
    public static List<String> unplaced() {
        return unplaced(SITES, PATCHES, SEAMS);
    }

    /** The same, over any rosters: the harness hands it ones built to be caught. */
    public static List<String> unplaced(List<Site> sites, List<Patch> patches,
                                        List<Seam> seams) {
        Map<String, Integer> laid = new LinkedHashMap<>();
        for (Patch one : patches) {
            laid.merge(one.site(), 1, Integer::sum);
        }
        for (Seam one : seams) {
            laid.merge(one.site(), 1, Integer::sum);
        }
        Set<String> known = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (Site site : sites) {
            known.add(site.block());
            if (!laid.containsKey(site.block())) {
                bad.add(site.block() + " is in no world: nothing lays it down, so it "
                        + "and everything only its pool holds come from a command and "
                        + "from nowhere else");
            }
        }
        for (Map.Entry<String, Integer> row : laid.entrySet()) {
            if (!known.contains(row.getKey())) {
                bad.add(row.getKey() + " is laid in the world and is not a site, so it "
                        + "is a block no brush can open");
            }
            if (row.getValue() > 1) {
                bad.add(row.getKey() + " is laid " + row.getValue() + " ways, and a "
                        + "site's feature is named after it, so one overwrites the other");
            }
        }
        return List.copyOf(bad);
    }

    /** Every pool holding the same thing twice. */
    public static List<String> repeatedFinds() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, List<Find>> row : POOLS.entrySet()) {
            Set<String> seen = new TreeSet<>();
            for (Find one : row.getValue()) {
                if (!seen.add(one.item())) {
                    bad.add(one.item() + " is in the " + row.getKey()
                            + " pool twice, which is one entry with the wrong weight");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every name this module needs on the way into a game. */
    public static List<String> catalogue() {
        List<String> all = new ArrayList<>();
        for (Brush one : BRUSHES) {
            all.add(one.name());
        }
        for (Site site : SITES) {
            all.add(site.block());
        }
        return List.copyOf(all);
    }
}
