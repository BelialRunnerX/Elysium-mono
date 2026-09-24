package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A hundred and five fish, and the water they are actually in.
 *
 * <h2>What a fishing mod is, underneath</h2>
 *
 * A roster and a weighted table. The roster is the easy half and the half
 * everybody writes; the table is where a fishing mod is either a system or a
 * list of names you will never see. A fish that is in the registry, has a
 * texture and a bucket and a spawn egg, and never once comes up on a rod, is
 * the same kind of nothing as a bee for a metal the pack cannot make.
 *
 * <p>So the table is the thing that is proved here. {@link #neverCaught} walks
 * every water a player can stand in and every rod they can hold and reports
 * any fish that never appears; the harness runs it on every build. The roster
 * below is a hundred and five fish and the checker says all hundred and five
 * are catchable somewhere, which is a claim rather than a hope.
 *
 * <h2>How a cast is decided</h2>
 *
 * Where you are gives a set of candidates: the body of water, the depth
 * within one band, and the time of day. Each candidate has a weight from its
 * rarity. A better rod tilts the weights towards the rare ones without ever
 * removing a common one; bait pulls towards one family without ever removing
 * anything at all. The result is normalised, so the shares in any water sum to
 * one and a table that has quietly become empty is an arithmetic error rather
 * than a silent nothing.
 */
public final class FishRules {

    private FishRules() {
    }

    // ==================================================================
    // Where fish live
    // ==================================================================

    /** Ordinary water, in a river or a lake. */
    public static final String FRESH = "fresh";

    /** The sea. */
    public static final String SALT = "salt";

    /** Lava, which is a body of liquid and has things living in it here. */
    public static final String LAVA = "lava";

    /** Whatever the end has instead. */
    public static final String VOID = "void";

    /** Every kind of water there is. */
    public static final List<String> WATERS = List.of(FRESH, SALT, LAVA, VOID);

    /** How many depth bands a body of water has: surface, shallow, deep, abyss. */
    public static final int BANDS = 4;

    /** Any time of day. */
    public static final String ANY = "any";

    /** Only while the sun is up. */
    public static final String DAY = "day";

    /** Only after dark. */
    public static final String NIGHT = "night";

    /** Every time band there is. */
    public static final List<String> TIMES = List.of(ANY, DAY, NIGHT);

    /**
     * A family of fish: what water it is in, how deep, and when.
     *
     * <p>The family carries the habitat rather than each fish, because a
     * hundred and five fish each with their own three fields is a hundred and
     * five chances to put a reef fish in a cave.
     */
    public record Family(String name, String water, int band, String when) {

        public Family {
            Objects.requireNonNull(name, "a family with no name");
            if (!WATERS.contains(water)) {
                throw new IllegalArgumentException("no such water: " + water);
            }
            if (band < 0 || band >= BANDS) {
                throw new IllegalArgumentException("no such depth band: " + band);
            }
            if (!TIMES.contains(when)) {
                throw new IllegalArgumentException("no such time: " + when);
            }
        }
    }

    public static final Family RIVER = new Family("river", FRESH, 1, ANY);
    public static final Family LAKE = new Family("lake", FRESH, 2, ANY);
    public static final Family MARSH = new Family("marsh", FRESH, 0, NIGHT);
    public static final Family REEF = new Family("reef", SALT, 1, DAY);
    public static final Family OCEAN = new Family("ocean", SALT, 2, ANY);
    public static final Family DEEP = new Family("deep", SALT, 3, ANY);
    public static final Family ABYSS = new Family("abyss", SALT, 3, NIGHT);
    public static final Family ARCTIC = new Family("arctic", SALT, 1, ANY);
    public static final Family CAVE = new Family("cave", FRESH, 2, ANY);
    public static final Family NETHER = new Family("nether", LAVA, 1, ANY);
    public static final Family END = new Family("end", VOID, 2, ANY);

    /** Every family there is. */
    public static final List<Family> FAMILIES = List.of(RIVER, LAKE, MARSH, REEF,
            OCEAN, DEEP, ABYSS, ARCTIC, CAVE, NETHER, END);

    // ==================================================================
    // The fish
    // ==================================================================

    /** How rare the commonest fish is, and the rarest. */
    public static final int COMMONEST = 1;

    /** The other end of it. */
    public static final int RAREST = 5;

    /**
     * One species.
     *
     * @param id        what it is called
     * @param family    where it lives
     * @param rarity    1 for everywhere, 5 for a story
     * @param length    how long a big one is, in centimetres
     * @param nutrition what eating it is worth
     */
    public record Fish(String id, Family family, int rarity, float length,
                       int nutrition) {

        public Fish {
            Objects.requireNonNull(id, "a fish with no name");
            Objects.requireNonNull(family, "a fish that lives nowhere");
            if (rarity < COMMONEST || rarity > RAREST) {
                throw new IllegalArgumentException(id + " has a rarity of " + rarity
                        + ", and the band is " + COMMONEST + " to " + RAREST);
            }
            if (length <= 0.0F) {
                throw new IllegalArgumentException(id + " has no length, so no "
                        + "record of it can ever be kept");
            }
            if (nutrition <= 0) {
                throw new IllegalArgumentException(id + " feeds nobody");
            }
        }

        /** The bucket you carry it home in. */
        public String bucket() {
            return id + "_bucket";
        }

        /** The egg that gives you one. */
        public String egg() {
            return id + "_spawn_egg";
        }
    }

    private static Fish fish(String id, Family family, int rarity, float length,
                             int nutrition) {
        return new Fish(id, family, rarity, length, nutrition);
    }

    /** Every fish there is. */
    public static final List<Fish> FISH = List.of(
            // river — fresh, band 1, any
            fish("river_dace", RIVER, 1, 18.0F, 2),
            fish("silver_roach", RIVER, 1, 22.0F, 2),
            fish("brook_trout", RIVER, 2, 34.0F, 3),
            fish("mirror_carp", RIVER, 2, 55.0F, 4),
            fish("grayling", RIVER, 2, 40.0F, 3),
            fish("bleak_minnow", RIVER, 1, 12.0F, 1),
            fish("pike_perch", RIVER, 3, 70.0F, 5),
            fish("northern_pike", RIVER, 3, 90.0F, 5),
            fish("barbel", RIVER, 2, 60.0F, 4),
            fish("chub", RIVER, 1, 45.0F, 3),
            fish("river_eel", RIVER, 3, 120.0F, 5),
            fish("gudgeon", RIVER, 1, 14.0F, 1),
            fish("nase", RIVER, 2, 38.0F, 3),
            fish("asp", RIVER, 3, 75.0F, 5),
            // lake — fresh, band 2, any
            fish("lake_char", LAKE, 2, 60.0F, 4),
            fish("whitefish", LAKE, 2, 45.0F, 3),
            fish("burbot", LAKE, 3, 80.0F, 5),
            fish("tench", LAKE, 2, 50.0F, 4),
            fish("bream", LAKE, 1, 40.0F, 3),
            fish("rudd", LAKE, 1, 28.0F, 2),
            fish("zander", LAKE, 3, 85.0F, 5),
            fish("crucian", LAKE, 1, 30.0F, 2),
            fish("vendace", LAKE, 2, 25.0F, 2),
            fish("catfish_wels", LAKE, 4, 200.0F, 7),
            // marsh — fresh, band 0, night
            fish("bog_loach", MARSH, 1, 16.0F, 1),
            fish("marsh_carp", MARSH, 2, 48.0F, 4),
            fish("mud_sleeper", MARSH, 2, 26.0F, 2),
            fish("reed_perch", MARSH, 1, 24.0F, 2),
            fish("swamp_gar", MARSH, 3, 95.0F, 5),
            fish("bitterling", MARSH, 1, 8.0F, 1),
            fish("weatherfish", MARSH, 2, 30.0F, 2),
            fish("marbled_eel", MARSH, 3, 110.0F, 5),
            // reef — salt, band 1, day
            fish("parrot_wrasse", REEF, 1, 30.0F, 2),
            fish("clown_goby", REEF, 1, 10.0F, 1),
            fish("banner_fish", REEF, 2, 20.0F, 2),
            fish("angel_tang", REEF, 2, 26.0F, 3),
            fish("coral_grouper", REEF, 3, 60.0F, 4),
            fish("lionfish", REEF, 3, 38.0F, 4),
            fish("moray", REEF, 3, 120.0F, 5),
            fish("triggerfish", REEF, 2, 35.0F, 3),
            fish("surgeonfish", REEF, 1, 24.0F, 2),
            fish("boxfish", REEF, 2, 18.0F, 2),
            fish("razorfish", REEF, 1, 15.0F, 1),
            fish("reef_snapper", REEF, 2, 50.0F, 4),
            fish("harlequin_sweetlips", REEF, 3, 55.0F, 4),
            fish("pipefish", REEF, 1, 12.0F, 1),
            // ocean — salt, band 2, any
            fish("mackerel", OCEAN, 1, 35.0F, 3),
            fish("atlantic_cod", OCEAN, 1, 80.0F, 4),
            fish("haddock", OCEAN, 2, 60.0F, 4),
            fish("pollock", OCEAN, 1, 70.0F, 4),
            fish("sea_bass", OCEAN, 2, 55.0F, 4),
            fish("john_dory", OCEAN, 3, 50.0F, 4),
            fish("blue_marlin", OCEAN, 5, 300.0F, 8),
            fish("yellowfin_tuna", OCEAN, 4, 180.0F, 7),
            fish("swordfish", OCEAN, 4, 250.0F, 8),
            fish("mahi_mahi", OCEAN, 3, 120.0F, 6),
            fish("bonito", OCEAN, 2, 65.0F, 4),
            fish("amberjack", OCEAN, 3, 140.0F, 6),
            fish("halibut", OCEAN, 4, 200.0F, 7),
            fish("turbot", OCEAN, 3, 85.0F, 5),
            // deep — salt, band 3, any
            fish("lanternfish", DEEP, 2, 12.0F, 2),
            fish("hatchetfish", DEEP, 3, 9.0F, 2),
            fish("grenadier", DEEP, 3, 70.0F, 4),
            fish("orange_roughy", DEEP, 4, 50.0F, 5),
            fish("oarfish", DEEP, 5, 500.0F, 9),
            fish("chimaera", DEEP, 4, 120.0F, 6),
            fish("gulper_eel", DEEP, 4, 75.0F, 5),
            fish("dragonfish", DEEP, 4, 35.0F, 4),
            fish("viperfish", DEEP, 4, 30.0F, 4),
            fish("blobfish", DEEP, 3, 28.0F, 2),
            fish("slickhead", DEEP, 3, 40.0F, 3),
            fish("spookfish", DEEP, 4, 45.0F, 4),
            // abyss — salt, band 3, night
            fish("abyss_angler", ABYSS, 5, 60.0F, 6),
            fish("tripod_fish", ABYSS, 4, 35.0F, 4),
            fish("black_swallower", ABYSS, 5, 25.0F, 5),
            fish("frilled_shark", ABYSS, 5, 180.0F, 8),
            fish("goblin_shark", ABYSS, 5, 380.0F, 9),
            fish("pelican_eel", ABYSS, 4, 80.0F, 5),
            fish("bone_eater", ABYSS, 5, 20.0F, 4),
            fish("glass_squid", ABYSS, 4, 40.0F, 4),
            // arctic — salt, band 1, any
            fish("arctic_char", ARCTIC, 2, 55.0F, 4),
            fish("polar_cod", ARCTIC, 2, 30.0F, 3),
            fish("capelin", ARCTIC, 1, 20.0F, 2),
            fish("greenland_halibut", ARCTIC, 4, 110.0F, 6),
            fish("wolffish", ARCTIC, 3, 120.0F, 5),
            fish("lumpsucker", ARCTIC, 2, 50.0F, 3),
            fish("icefish", ARCTIC, 3, 25.0F, 3),
            fish("navaga", ARCTIC, 1, 32.0F, 2),
            // cave — fresh, band 2, any
            fish("blind_cavefish", CAVE, 3, 14.0F, 2),
            fish("stone_loach", CAVE, 2, 12.0F, 1),
            fish("grotto_sculpin", CAVE, 3, 18.0F, 2),
            fish("pale_salamander_fish", CAVE, 4, 22.0F, 3),
            fish("deepslate_char", CAVE, 3, 40.0F, 4),
            fish("dripstone_minnow", CAVE, 2, 10.0F, 1),
            // nether — lava, band 1, any
            fish("magma_carp", NETHER, 3, 45.0F, 4),
            fish("cinder_eel", NETHER, 4, 90.0F, 5),
            fish("basalt_lurker", NETHER, 3, 60.0F, 4),
            fish("emberfin", NETHER, 2, 25.0F, 3),
            fish("soulfish", NETHER, 4, 35.0F, 5),
            fish("blazefin", NETHER, 5, 70.0F, 6),
            // end — void, band 2, any
            fish("void_ray", END, 4, 140.0F, 6),
            fish("chorus_perch", END, 3, 35.0F, 4),
            fish("ender_lamprey", END, 4, 60.0F, 5),
            fish("pale_drifter", END, 3, 28.0F, 3),
            fish("singularity_eel", END, 5, 200.0F, 8));

    private static final Map<String, Fish> BY_ID = new LinkedHashMap<>();

    static {
        for (Fish one : FISH) {
            BY_ID.put(one.id(), one);
        }
    }

    /** The fish of that name, or null. */
    public static Fish fish(String id) {
        return BY_ID.get(id);
    }

    /** Every fish in one family. */
    public static List<Fish> ofFamily(Family family) {
        List<Fish> found = new ArrayList<>();
        for (Fish one : FISH) {
            if (one.family().equals(family)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // A cast
    // ==================================================================

    /** Where somebody is standing, as far as fishing is concerned. */
    public record Water(String kind, int band, String when) {

        public Water {
            if (!WATERS.contains(kind)) {
                throw new IllegalArgumentException("no such water: " + kind);
            }
            if (band < 0 || band >= BANDS) {
                throw new IllegalArgumentException("no such depth band: " + band);
            }
            if (!TIMES.contains(when)) {
                throw new IllegalArgumentException("no such time: " + when);
            }
        }
    }

    /** One fish's share of a cast, between zero and one. */
    public record Chance(String fish, double share) {
    }

    /** How many rungs of rod there are, counting the bare hook as the first. */
    public static final int RODS = 5;

    /** How much each rung tilts the table towards the rare end. */
    public static final double LUCK_TILT = 0.34D;

    /** How much bait pulls towards its own family. */
    public static final double BAIT_PULL = 4.0D;

    /** No bait at all. */
    public static final String NOTHING = "";

    /**
     * How much a fish of a given rarity is worth before anything else.
     *
     * <p>Halving rather than a curve somebody chose: it is the shape a player
     * already expects from everything else in the game, and it means a rarity
     * five fish is thirty-two times rarer than a rarity one rather than some
     * number that has to be looked up.
     */
    public static double baseWeight(int rarity) {
        double weight = 64.0D;
        for (int step = COMMONEST; step < rarity; step++) {
            weight /= 2.0D;
        }
        return weight;
    }

    /** Whether a fish can be caught in this water at all. */
    public static boolean lives(Fish one, Water where) {
        if (!one.family().water().equals(where.kind())) {
            return false;
        }
        if (Math.abs(one.family().band() - where.band()) > 1) {
            return false;
        }
        String wants = one.family().when();
        return ANY.equals(wants) || ANY.equals(where.when()) || wants.equals(where.when());
    }

    /**
     * What a cast here, with this rod and this bait, can bring up.
     *
     * <p>Normalised, and in roster order so two runs agree. An empty list means
     * there is genuinely nothing in this water, which {@link #deadWater} then
     * reports rather than leaving as a rod that never catches anything.
     */
    public static List<Chance> table(Water where, int luck, String bait) {
        int rod = Math.max(0, Math.min(RODS - 1, luck));
        List<Fish> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0D;
        for (Fish one : FISH) {
            if (!lives(one, where)) {
                continue;
            }
            double weight = baseWeight(one.rarity());
            for (int step = COMMONEST; step < one.rarity(); step++) {
                weight *= 1.0D + LUCK_TILT * rod;
            }
            if (!NOTHING.equals(bait) && one.family().name().equals(bait)) {
                weight *= BAIT_PULL;
            }
            candidates.add(one);
            weights.add(weight);
            total += weight;
        }
        if (candidates.isEmpty() || total <= 0.0D) {
            return List.of();
        }
        List<Chance> out = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            out.add(new Chance(candidates.get(index).id(), weights.get(index) / total));
        }
        return List.copyOf(out);
    }

    /** Every water a player can actually stand in. */
    public static List<Water> everyWater() {
        List<Water> all = new ArrayList<>();
        for (String kind : WATERS) {
            for (int band = 0; band < BANDS; band++) {
                for (String when : List.of(DAY, NIGHT)) {
                    all.add(new Water(kind, band, when));
                }
            }
        }
        return List.copyOf(all);
    }

    /** Every water that has fish in it. */
    public static List<Water> fishableWaters() {
        List<Water> found = new ArrayList<>();
        for (Water where : everyWater()) {
            if (!table(where, 0, NOTHING).isEmpty()) {
                found.add(where);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // Whether any of it can be caught
    // ==================================================================

    /**
     * Every fish that never comes up, in any water, with any rod or bait.
     *
     * <p>This is the check the whole file is built around. A fish nobody can
     * catch is a registry entry, a texture, a bucket, a spawn egg and a
     * translation, and no content at all.
     */
    public static List<String> neverCaught() {
        Set<String> seen = new TreeSet<>();
        for (Water where : everyWater()) {
            for (int luck = 0; luck < RODS; luck++) {
                for (Chance chance : table(where, luck, NOTHING)) {
                    if (chance.share() > 0.0D) {
                        seen.add(chance.fish());
                    }
                }
            }
        }
        List<String> lost = new ArrayList<>();
        for (Fish one : FISH) {
            if (!seen.contains(one.id())) {
                lost.add(one.id());
            }
        }
        return List.copyOf(lost);
    }

    /** How thin a water is allowed to be before it is not worth fishing. */
    public static final int FEWEST_IN_A_WATER = 3;

    /** Every fishable water with too few fish in it to be worth a rod. */
    public static List<String> thinWater() {
        List<String> thin = new ArrayList<>();
        for (Water where : fishableWaters()) {
            int count = table(where, 0, NOTHING).size();
            if (count < FEWEST_IN_A_WATER) {
                thin.add(where.kind() + " band " + where.band() + " by "
                        + where.when() + " has only " + count + " fish in it");
            }
        }
        return List.copyOf(thin);
    }

    /** Every kind of water that has no fish anywhere in it at any depth. */
    public static List<String> deadWater() {
        List<String> dead = new ArrayList<>();
        for (String kind : WATERS) {
            boolean any = false;
            for (Water where : fishableWaters()) {
                if (where.kind().equals(kind)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                dead.add(kind);
            }
        }
        return List.copyOf(dead);
    }

    /**
     * Every place a better rod is worse at being a rod.
     *
     * <p>The naive form of this check — "no rare fish may lose share" — is
     * wrong, and was written that way first. Tilting a normalised table
     * towards the very rarest necessarily takes share from the middle, so a
     * rarity two fish getting slightly rarer as the rod improves is correct
     * behaviour rather than a bug, and a checker that reports it reports four
     * hundred and sixty times and gets switched off.
     *
     * <p>What a player actually feels is the <em>ratio</em>: with a better rod,
     * the rarer of any two fish must never become harder to get relative to the
     * commoner one, and the rarest thing in the water must strictly improve.
     * That is the property, and it is the one checked here.
     */
    public static List<String> luckThatHurts() {
        List<String> wrong = new ArrayList<>();
        for (Water where : fishableWaters()) {
            for (int luck = 1; luck < RODS; luck++) {
                Map<String, Double> worse = shares(table(where, luck - 1, NOTHING));
                Map<String, Double> better = shares(table(where, luck, NOTHING));
                for (Fish rare : FISH) {
                    Double rareBefore = worse.get(rare.id());
                    Double rareAfter = better.get(rare.id());
                    if (rareBefore == null || rareAfter == null) {
                        continue;
                    }
                    for (Fish common : FISH) {
                        if (common.rarity() >= rare.rarity()) {
                            continue;
                        }
                        Double commonBefore = worse.get(common.id());
                        Double commonAfter = better.get(common.id());
                        if (commonBefore == null || commonAfter == null
                                || commonBefore <= 0.0D || commonAfter <= 0.0D) {
                            continue;
                        }
                        double was = rareBefore / commonBefore;
                        double now = rareAfter / commonAfter;
                        if (now < was - 1.0E-9D) {
                            wrong.add("rod " + luck + " makes " + rare.id()
                                    + " harder to get than " + common.id()
                                    + " than rod " + (luck - 1) + " did, in "
                                    + where.kind() + " band " + where.band());
                        }
                    }
                }
                String rarest = rarestIn(where);
                if (rarest != null) {
                    Double had = worse.get(rarest);
                    Double now = better.get(rarest);
                    if (had != null && now != null && now <= had + 1.0E-12D
                            && countRarities(where) > 1) {
                        wrong.add("rod " + luck + " does not improve the chance of "
                                + rarest + ", the rarest thing in " + where.kind()
                                + " band " + where.band() + ", so the rod is not a rod");
                    }
                }
            }
        }
        return List.copyOf(wrong);
    }

    /** The rarest fish in a water, or null if there are none. */
    public static String rarestIn(Water where) {
        String rarest = null;
        int worst = COMMONEST - 1;
        for (Fish one : FISH) {
            if (lives(one, where) && one.rarity() > worst) {
                worst = one.rarity();
                rarest = one.id();
            }
        }
        return rarest;
    }

    /** How many different rarities a water holds. */
    public static int countRarities(Water where) {
        Set<Integer> seen = new TreeSet<>();
        for (Fish one : FISH) {
            if (lives(one, where)) {
                seen.add(one.rarity());
            }
        }
        return seen.size();
    }

    /** Every place bait removes a fish from the water rather than shifting it. */
    public static List<String> baitThatExcludes() {
        List<String> wrong = new ArrayList<>();
        for (Water where : fishableWaters()) {
            Set<String> without = shares(table(where, 0, NOTHING)).keySet();
            for (Family family : FAMILIES) {
                Set<String> with = shares(table(where, 0, family.name())).keySet();
                for (String id : without) {
                    if (!with.contains(id)) {
                        wrong.add(family.name() + " bait removes " + id + " from "
                                + where.kind() + " band " + where.band());
                    }
                }
            }
        }
        return List.copyOf(wrong);
    }

    /**
     * Every place bait does not pull towards the family it names.
     *
     * <p>"Bait never removes a fish" is a weaker claim than it sounds: a bait
     * that multiplies every <em>other</em> family satisfies it perfectly and
     * is exactly backwards. This is the claim that matters — using river bait
     * has to make river fish likelier than using none.
     */
    public static List<String> baitThatPullsWrong() {
        List<String> wrong = new ArrayList<>();
        for (Water where : fishableWaters()) {
            for (Family family : FAMILIES) {
                double without = familyShare(where, NOTHING, family);
                if (without <= 0.0D) {
                    continue;
                }
                // A family that is already the whole water cannot be pulled
                // towards — lava has only nether fish in it. That is the water
                // being narrow, not the bait being backwards, and a checker
                // that reports it reports fourteen times and gets ignored.
                if (without >= 1.0D - 1.0E-9D) {
                    continue;
                }
                double with = familyShare(where, family.name(), family);
                if (with <= without + 1.0E-12D) {
                    wrong.add(family.name() + " bait does not make " + family.name()
                            + " fish likelier in " + where.kind() + " band "
                            + where.band() + ", so it is bait for something else");
                }
            }
        }
        return List.copyOf(wrong);
    }

    /** What share of a cast one family takes, with a given bait. */
    public static double familyShare(Water where, String bait, Family family) {
        double total = 0.0D;
        for (Chance chance : table(where, 0, bait)) {
            Fish one = BY_ID.get(chance.fish());
            if (one != null && one.family().equals(family)) {
                total += chance.share();
            }
        }
        return total;
    }

    /**
     * Every rarity band whose best fish feeds a player better than the best
     * fish of the band above it.
     *
     * <p>The averages rising is not enough. One common fish that is the best
     * meal in the game is a fishing system where nobody ever changes rod, and
     * an average across twenty-one fish hides it comfortably.
     */
    public static List<String> bestFoodIsCommoner() {
        List<String> wrong = new ArrayList<>();
        int below = -1;
        int belowRarity = -1;
        for (int rarity = COMMONEST; rarity <= RAREST; rarity++) {
            int best = -1;
            for (Fish one : FISH) {
                if (one.rarity() == rarity) {
                    best = Math.max(best, one.nutrition());
                }
            }
            if (best < 0) {
                continue;
            }
            if (below >= 0 && best < below) {
                wrong.add("the best rarity " + rarity + " fish feeds a player worse ("
                        + best + ") than the best rarity " + belowRarity + " one ("
                        + below + "), so the commoner fish is the better meal");
            }
            below = best;
            belowRarity = rarity;
        }
        return List.copyOf(wrong);
    }

    /** The shares of a table, by fish. */
    public static Map<String, Double> shares(List<Chance> table) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Chance chance : table) {
            out.put(chance.fish(), chance.share());
        }
        return Map.copyOf(out);
    }

    /** The most casts it may take to see the rarest thing in a water. */
    public static final int MOST_CASTS = 400;

    /**
     * Every fish that takes more than {@link #MOST_CASTS} expected casts even
     * with the best rod and the right bait.
     *
     * <p>Rarity is supposed to be a story, not a second job. This is the line
     * between the two, written down rather than felt.
     */
    public static List<String> unreasonablyRare() {
        List<String> slow = new ArrayList<>();
        for (Fish one : FISH) {
            double best = 0.0D;
            for (Water where : fishableWaters()) {
                for (Chance chance : table(where, RODS - 1, one.family().name())) {
                    if (chance.fish().equals(one.id())) {
                        best = Math.max(best, chance.share());
                    }
                }
            }
            if (best <= 0.0D) {
                slow.add(one.id() + " never comes up at all, at any rod or bait");
            } else if (1.0D / best > MOST_CASTS) {
                slow.add(one.id() + " takes about " + Math.round(1.0D / best)
                        + " casts at the best rod and its own bait, and the line "
                        + "is " + MOST_CASTS);
            }
        }
        return List.copyOf(slow);
    }

    /**
     * Every rarity band whose average fish feeds a player better than the band
     * above it.
     *
     * <p>A common fish that is the best food in the game is a fishing system
     * where nobody ever changes rod.
     */
    public static List<String> betterFoodIsCommoner() {
        List<String> wrong = new ArrayList<>();
        double below = -1.0D;
        for (int rarity = COMMONEST; rarity <= RAREST; rarity++) {
            double total = 0.0D;
            int count = 0;
            for (Fish one : FISH) {
                if (one.rarity() == rarity) {
                    total += one.nutrition();
                    count++;
                }
            }
            if (count == 0) {
                wrong.add("nothing at all has rarity " + rarity);
                continue;
            }
            double mean = total / count;
            if (mean < below) {
                wrong.add("rarity " + rarity + " feeds a player worse on average than "
                        + "rarity " + (rarity - 1) + ", so the rarer fish is the "
                        + "worse meal");
            }
            below = mean;
        }
        return List.copyOf(wrong);
    }

    /** Every fish sharing a name with another. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Fish one : FISH) {
            if (!seen.add(one.id())) {
                twice.add(one.id());
            }
        }
        return List.copyOf(twice);
    }
}
