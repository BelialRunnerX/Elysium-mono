package com.elysium.logistics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compression: fifty-nine things the game gives a player too many of, each
 * nine deep. The JDK and nothing else.
 *
 * <h2>Why this is arithmetic and not four thousand blocks</h2>
 *
 * The jar this replaces ships four and a half thousand blocks. They are fifty
 * or so materials crossed with nine tiers, and every one of them is the same
 * idea: <b>nine of the thing below makes one of the thing above.</b> So what
 * is written down here is the materials, the number nine, and how deep it
 * goes; the blocks are generated.
 *
 * <h2>The one property worth proving hard</h2>
 *
 * <b>The round trip.</b> Compress a pile and decompress it and you must get
 * back exactly what went in — at every tier, for every material, with no
 * rounding and nothing left over. Nine is chosen precisely because it is what
 * a crafting grid does, so the trip is exact by construction; but "exact by
 * construction" is what everybody says about the arithmetic that later turns
 * out to have eaten four ingots, so it is swept rather than asserted.
 *
 * <p>And the second, which is the one that would end a save: <b>a tier must be
 * worth exactly nine of the tier below.</b> Eight and a player compresses and
 * decompresses in a loop to destroy matter; ten and they do it to make matter,
 * and nothing else in this pack survives an infinite source of iron.
 *
 * <h2>And the thing the original does with textures, done with geometry</h2>
 *
 * The jar tells its nine tiers apart by tinting the texture nine ways. There
 * is no retexture generator on this project, so the tiers are told apart by
 * being <em>framed</em>: a compressed block is its own material in a border of
 * a metal that says which tier it is — copper, iron, gold, diamond, emerald,
 * netherite, amethyst, lapis, quartz. Nine materials the game already has,
 * read across a room, and no new sprite anywhere.
 */
public final class CompressRules {

    private CompressRules() {
    }

    /** How many of the tier below make one of the tier above. */
    public static final int PER_STEP = LogisticRules.COMPRESSION;

    /** How deep it goes. */
    public static final int TIERS = 9;

    /** A full stack, which is what a pool is counted in. */
    public static final int STACK = LogisticRules.STACK;

    /**
     * What each tier is called, in order.
     *
     * <p>The ordinals rather than "tier 4", because a player reads "triple
     * compressed cobblestone" and knows what they have without counting.
     */
    public static final List<String> DEPTHS = List.of(
            "compressed", "double_compressed", "triple_compressed",
            "quadruple_compressed", "quintuple_compressed", "sextuple_compressed",
            "septuple_compressed", "octuple_compressed", "nonuple_compressed");

    /**
     * The block whose face frames each tier.
     *
     * <p>Nine of the game's own metals and gems, in the order a player meets
     * them, so that the frame is a ladder rather than nine arbitrary colours.
     */
    public static final List<String> MARKERS = List.of(
            "minecraft:copper_block", "minecraft:iron_block",
            "minecraft:gold_block", "minecraft:diamond_block",
            "minecraft:emerald_block", "minecraft:netherite_block",
            "minecraft:amethyst_block", "minecraft:lapis_block",
            "minecraft:quartz_block");

    /**
     * Everything worth compressing, which is everything the game hands out by
     * the thousand.
     *
     * <p>All of them blocks the game already has, so this module ships no
     * texture: a compressed block wears the face of the thing it is made of.
     */
    public static final List<String> MATERIALS = List.of(
            "cobblestone", "dirt", "stone", "deepslate", "cobbled_deepslate",
            "sand", "red_sand", "gravel", "netherrack", "andesite", "diorite",
            "granite", "tuff", "calcite", "dripstone_block", "basalt",
            "smooth_basalt", "blackstone", "sandstone", "red_sandstone", "clay",
            "obsidian", "glass", "end_stone", "magma_block", "soul_sand",
            "soul_soil", "ice", "packed_ice", "blue_ice", "snow_block",
            "bone_block", "moss_block", "mud", "prismarine", "glowstone",
            "honeycomb_block", "slime_block", "hay_block", "coal_block",
            "iron_block", "gold_block", "redstone_block", "lapis_block",
            "diamond_block", "emerald_block", "quartz_block", "amethyst_block",
            "copper_block", "netherite_block", "oak_planks", "terracotta",
            "bricks", "mud_bricks", "nether_bricks", "purpur_block",
            "sea_lantern", "dried_kelp_block", "bamboo_block");

    /** The vanilla block a tier-one compression is made of. */
    public static String blockOf(String material) {
        return "minecraft:" + material;
    }

    /**
     * The block id of a compression, without the namespace.
     *
     * @param material one of {@link #MATERIALS}
     * @param tier     1 for the first compression, up to {@link #TIERS}
     */
    public static String idFor(String material, int tier) {
        if (tier < 1 || tier > TIERS) {
            throw new IllegalArgumentException("there is no tier " + tier
                    + "; compression goes from 1 to " + TIERS);
        }
        if (tier > DEPTHS.size()) {
            // Not an exception: a roster that has lost a name is something
            // muddled() is meant to report, and a crash here would take the
            // harness down before it got that far and leave nobody any wiser.
            return "unnamed_tier_" + tier + "_" + material;
        }
        return DEPTHS.get(tier - 1) + "_" + material;
    }

    /** What one of a tier is worth, counted in the material it is made of. */
    public static long worth(int tier) {
        return LogisticRules.inSmallest(1L, tier);
    }

    /** What {@code amount} at {@code tier} is worth in the base material. */
    public static long inBase(long amount, int tier) {
        return LogisticRules.inSmallest(amount, tier);
    }

    /**
     * How much of {@code tier} a pool of the base material makes, and what is
     * left over.
     *
     * <p>Two numbers, for the reason {@link LogisticRules#fromSmallest} gives
     * two: a drawer that says "seven blocks" while holding seven blocks and
     * four ingots has eaten four ingots as far as the player can tell.
     */
    public static long[] fromBase(long base, int tier) {
        return LogisticRules.fromSmallest(base, tier);
    }

    /** What the tier below this one is, or 0 for the base material. */
    public static int below(int tier) {
        return tier - 1;
    }

    /** The id something at {@code tier} is compressed out of. */
    public static String madeOf(String material, int tier) {
        return tier <= 1 ? blockOf(material)
                : "elysiumlogistics:" + idFor(material, tier - 1);
    }

    // ==================================================================
    // What has to be true
    // ==================================================================

    /**
     * Every tier that is not worth exactly {@link #PER_STEP} of the one below.
     *
     * <p>Must be empty. Eight and a loop of compressing and decompressing
     * destroys matter; ten and it makes matter, and there is nothing else in
     * this pack that survives an infinite source of iron.
     */
    public static List<String> mispriced() {
        List<String> bad = new ArrayList<>();
        if (worth(0) != 1) {
            bad.add("one of the base material is worth " + worth(0)
                    + " of itself, so every tier above it is out by that much");
        }
        for (int tier = 1; tier <= TIERS; tier++) {
            bad.addAll(faultsInStep(tier, worth(tier), worth(tier - 1)));
        }
        return List.copyOf(bad);
    }

    /**
     * What is wrong with one step of the ladder, if anything.
     *
     * <p>Split out from the walk because over a healthy ladder it can only
     * ever answer no, which is what a check that has stopped working also
     * answers. The harness hands it a step worth eight.
     */
    public static List<String> faultsInStep(int tier, long here, long under) {
        List<String> bad = new ArrayList<>();
        if (here != under * PER_STEP) {
            bad.add("tier " + tier + " is worth " + here + " and " + PER_STEP
                    + " of the tier below is " + under * PER_STEP
                    + ", so compressing and decompressing in a loop "
                    + (here > under * PER_STEP ? "makes" : "destroys") + " matter");
        }
        if (here <= under) {
            bad.add("tier " + tier + " is worth no more than the tier below it");
        }
        return List.copyOf(bad);
    }

    /**
     * What is wrong with one round trip, given what came back.
     *
     * <p>Split out for the same reason: the arithmetic here is exact, so run
     * over it this can only answer no. The harness hands it a trip that lost
     * something.
     */
    public static List<String> faultsInTrip(long amount, int tier, long back,
                                            long over) {
        List<String> bad = new ArrayList<>();
        if (back != amount) {
            bad.add(amount + " at tier " + tier + " came back as " + back);
        }
        if (over != 0) {
            bad.add(amount + " at tier " + tier + " left " + over
                    + " over, which is that much the player cannot see");
        }
        return List.copyOf(bad);
    }

    /**
     * Every round trip that does not come back with what went in.
     *
     * <p>Must be empty. Handed the amounts to try rather than choosing them,
     * so the harness can sweep and the negative tests can hand it one.
     */
    public static List<String> lossy(List<Long> amounts) {
        List<String> bad = new ArrayList<>();
        for (int tier = 1; tier <= TIERS; tier++) {
            for (long amount : amounts) {
                long[] back = fromBase(inBase(amount, tier), tier);
                bad.addAll(faultsInTrip(amount, tier, back[0], back[1]));
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every name that is not distinct, or every tier with no marker.
     *
     * <p>Must be empty. Two tiers that look the same are two tiers a player
     * miscrafts, and the mistake costs them the difference between them.
     */
    public static List<String> muddled() {
        List<String> bad = new ArrayList<>();
        if (DEPTHS.size() != TIERS) {
            bad.add("there are " + DEPTHS.size() + " names for " + TIERS
                    + " tiers, so a tier is unnamed or a name is for nothing");
        }
        if (MARKERS.size() != TIERS) {
            bad.add("there are " + MARKERS.size() + " frames for " + TIERS
                    + " tiers, so two tiers are framed the same and a player "
                    + "cannot tell them apart across a room");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String depth : DEPTHS) {
            if (!seen.add(depth)) {
                bad.add(depth + " names two tiers");
            }
        }
        Set<String> frames = new LinkedHashSet<>();
        for (String marker : MARKERS) {
            if (!frames.add(marker)) {
                bad.add(marker + " frames two tiers");
            }
        }
        Set<String> materials = new LinkedHashSet<>();
        for (String material : MATERIALS) {
            if (!materials.add(material)) {
                bad.add(material + " is compressed twice");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * The largest pool the game can put in one slot, in base material.
     *
     * <p>A stack of the deepest tier. It does not fit in an {@code int}, which
     * is the whole reason every count in this file is a {@code long}, and the
     * harness proves that rather than trusting the comment.
     */
    public static long biggestPool() {
        return inBase(STACK, TIERS);
    }

    // ==================================================================
    // What compression does to a block besides stack it
    // ==================================================================

    /** The hardest anything here may be: the game's own obsidian. */
    public static final float HARDEST = 50.0F;

    /** The most blast-proof anything here may be: the game's own obsidian. */
    public static final float TOUGHEST = 1200.0F;

    /**
     * How long a tier takes to break.
     *
     * <p>Climbing, and capped. A compressed block holding a million cobble
     * that breaks as fast as one cobble is a million cobble somebody takes
     * with a stone pickaxe while you are asleep; one that takes an hour is a
     * block nobody places.
     */
    public static float hardness(int tier) {
        return Math.min(HARDEST, 2.0F + tier * 1.5F);
    }

    /**
     * How well a tier stands up to an explosion.
     *
     * <p>Climbing much faster than the hardness, and capped at the same place
     * the game caps obsidian, so that a deep compression is a wall worth
     * building and never a wall nothing can touch.
     */
    public static float resistance(int tier) {
        float made = 6.0F;
        for (int step = 0; step < Math.max(0, tier); step++) {
            made *= 3.0F;
            if (made >= TOUGHEST) {
                return TOUGHEST;
            }
        }
        return made;
    }

    /**
     * Every tier that is softer or more fragile than the one below it, or
     * past the ceiling. Must be empty.
     *
     * <p>And the ceiling has to be reached by something, or it is not a
     * ceiling, it is a comment.
     */
    public static List<String> unhardened() {
        List<String> bad = new ArrayList<>();
        float lastHard = 0;
        float lastTough = 0;
        boolean capped = false;
        for (int tier = 1; tier <= TIERS; tier++) {
            float hard = hardness(tier);
            float tough = resistance(tier);
            if (hard < lastHard) {
                bad.add("tier " + tier + " is softer than the tier below it");
            }
            if (tough < lastTough) {
                bad.add("tier " + tier + " is more fragile than the tier below it");
            }
            if (hard > HARDEST || tough > TOUGHEST) {
                bad.add("tier " + tier + " is harder or tougher than the game's "
                        + "own obsidian, which is a block nothing can touch");
            }
            if (tough >= TOUGHEST) {
                capped = true;
            }
            lastHard = hard;
            lastTough = tough;
        }
        if (!capped) {
            bad.add("nothing reaches the blast ceiling, so the ceiling is not a "
                    + "ceiling, it is a comment");
        }
        return List.copyOf(bad);
    }

    /**
     * Whether a ladder this deep would go round the end of the number it is
     * kept in.
     *
     * <p>Asked of a depth rather than of the ladder that ships, so the answer
     * can be checked against one that is known: forty tiers of nine is far
     * past a long, one tier is not, and a check that cannot tell those apart
     * is a check that would let a tenth tier ship holding a negative number
     * of cobblestone.
     */
    public static boolean overflows(int tiers) {
        long worth = 1;
        for (int step = 0; step < Math.max(0, tiers); step++) {
            if (worth > Long.MAX_VALUE / PER_STEP / STACK) {
                return true;
            }
            worth *= PER_STEP;
        }
        return false;
    }

    /** Every id this file adds. */
    public static List<String> catalogue() {
        List<String> made = new ArrayList<>();
        for (String material : MATERIALS) {
            for (int tier = 1; tier <= TIERS; tier++) {
                made.add(idFor(material, tier));
            }
        }
        return List.copyOf(made);
    }

    /** How many blocks that is. */
    public static int howMany() {
        return catalogue().size();
    }
}
