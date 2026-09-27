package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Twenty-eight things that live in the Vale, and the proof that walking away
 * from the way in gets harder rather than riskier.
 *
 * <h2>What a dimension's roster is for</h2>
 *
 * The Vale already exists: fifteen biomes in {@code elysium-wilds}, laid out as
 * five bands of warmth crossed with three of rain, proved to be a partition of
 * the climate plane so that every one of them wins its own rectangle. What it
 * has not had is anything living in it.
 *
 * <p>Filling it is not the same problem as any of the other rosters in this
 * module, and the difference is the thing this file is about. The beasts are a
 * ladder the player climbs by choosing to. A dimension is a place the player
 * walks around in, and which direction they walk is not a difficulty setting —
 * unless somebody makes it one.
 *
 * <h2>The failure</h2>
 *
 * A dimension where the hardest thing can be next to the portal is a dimension
 * where exploring is a coin flip. The player who happened to walk north dies
 * and concludes the dimension is unfair; the player who happened to walk south
 * clears it and concludes it is empty. Neither of them is wrong, and neither
 * can tell you what went wrong, because from inside a single run it looks like
 * luck — which it is.
 *
 * <p>It is also nobody's decision. A roster gets filled in the order somebody
 * thought of the creatures, and where each one lives is chosen for flavour. Of
 * course it is: "the devil belongs in the sulphur springs" is a much better
 * sentence than "the devil belongs two rings out".
 *
 * <h2>Rings</h2>
 *
 * So the Vale is read as rings around its middle. {@link #ring} is how far a
 * biome is from {@code verdant_vale} — mild and fair, the crossing a portal
 * drops you nearest — counted in bands of warmth plus bands of rain. Four
 * rings, fifteen biomes, and every biome has something in it.
 *
 * <p>Each ring owns a band of danger, and the bands are contiguous and do not
 * overlap: everything in ring one is harder than everything in ring zero, and
 * so on out. That is stronger than "roughly rises" on purpose, because roughly
 * rising is what a roster does by accident anyway, and the claim worth making
 * is the one a player can rely on — that walking further out is always the
 * decision, and never the coin flip.
 *
 * <p>{@link #faultsInGradient} is that. {@link #faultsInEmptyPlaces} is the
 * other half: a biome with nothing in it is a direction the player walks and
 * finds nothing, which reads as the dimension being unfinished and is.
 */
public final class DenizenRules {

    private DenizenRules() {
    }

    /**
     * The fifteen crossings of the Vale, in the order {@code ValeRules} names
     * them: warmth-major, so index {@code warmth * 3 + wet}.
     *
     * <p>Written here rather than imported, because the rules of this module
     * are the JDK and the library and nothing else — and a module that reached
     * sideways into another module's tables would be the first one to do it.
     * {@code validate.py} holds this list to the one in {@code elysium-wilds},
     * so the copy cannot drift without a build failure.
     */
    public static final List<String> PLACES = List.of(
            "rime_barrens", "glacier_vale", "frostmere",
            "ashen_steppe", "pinewold", "coldmarsh",
            "chalk_downs", "verdant_vale", "bloomfen",
            "amber_scrub", "goldwood", "reedbasin",
            "cinder_flats", "emberwood", "sulphur_springs");

    /** How many bands of warmth the Vale is cut into. */
    public static final int WARMTHS = 5;

    /** And how many of rain. */
    public static final int WETS = 3;

    /**
     * How far out a crossing is from the one a portal drops you nearest.
     *
     * <p>Bands of warmth away from the middle plus bands of rain away from the
     * middle. Nought for {@code verdant_vale}, three for the four corners.
     */
    public static int ring(String place) {
        int at = PLACES.indexOf(place);
        if (at < 0) {
            return -1;
        }
        int warmth = at / WETS;
        int wet = at % WETS;
        return Math.abs(warmth - WARMTHS / 2) + Math.abs(wet - WETS / 2);
    }

    /** How many rings there are, counting the middle. */
    public static final int RINGS = 4;

    /**
     * The band of danger a ring owns.
     *
     * <p>Contiguous and non-overlapping by construction: each ring starts where
     * the one inside it stopped. Returning the pair rather than storing fifteen
     * numbers means the bands cannot be edited into overlapping, which is the
     * failure the gradient rule would otherwise have to catch after the fact.
     */
    public static double[] band(int ring) {
        double[] edges = {20.0D, 80.0D, 200.0D, 500.0D, 1_100.0D};
        if (ring < 0 || ring >= RINGS) {
            return new double[]{0.0D, 0.0D};
        }
        return new double[]{edges[ring], edges[ring + 1]};
    }

    /**
     * One thing that lives in the Vale.
     *
     * @param id     what it is called
     * @param place  which crossing it lives in
     * @param ring   how far out that is, kept on the row so a control can lie
     * @param health what it can take
     * @param damage what it gives on contact
     * @param speed  how fast it closes
     * @param weight how often it is chosen when something spawns
     */
    public record Denizen(String id, String place, int ring, double health,
                          double damage, double speed, int weight) {

        public Denizen {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("a creature with no name");
            }
            if (place == null || place.isEmpty()) {
                throw new IllegalArgumentException(id + " lives nowhere in the Vale, "
                        + "so nothing says how far out it is");
            }
            if (health <= 0.0D || damage < 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException(id + " has " + health
                        + " health, " + damage + " damage and " + speed
                        + " speed, and a creature with no health or no legs is "
                        + "not a creature");
            }
            if (weight < 1) {
                throw new IllegalArgumentException(id + " is chosen " + weight
                        + " times when something spawns, so it is a creature "
                        + "nobody ever meets");
            }
            if (ring < 0) {
                throw new IllegalArgumentException(id + " is " + ring + " rings out, "
                        + "which is inside the middle of the dimension");
            }
        }

        /** How dangerous it is, on the same crude number everything here uses. */
        public double threat() {
            return health * (1.0D + damage) * speed / 0.25D;
        }
    }

    /**
     * The twenty-eight.
     *
     * <p>Read the {@code ring} column down the table and it counts out from the
     * middle: nought in the verdant vale, one in the ring of four around it,
     * two in the ring of six, three in the four corners. Nothing in the table
     * is in a ring its own crossing does not put it in, which
     * {@link #faultsInDistance} is for — the column is on the row so that a
     * control can make it lie, and so that the rule which catches that is a
     * rule rather than a tautology.
     */
    public static final List<Denizen> DENIZENS = List.of(
            new Denizen("rimeprime", "rime_barrens", 3, 72.0D, 8.0D, 0.28D, 10),
            new Denizen("rimewarden", "rime_barrens", 3, 51.0D, 9.0D, 0.30D, 10),

            new Denizen("glacehulk", "glacier_vale", 2, 56.0D, 6.5D, 0.22D, 20),

            new Denizen("frostprime", "frostmere", 3, 101.0D, 7.5D, 0.28D, 10),
            new Denizen("frostwarden", "frostmere", 3, 74.0D, 8.5D, 0.22D, 10),

            new Denizen("ashulk", "ashen_steppe", 2, 30.0D, 7.0D, 0.26D, 20),
            new Denizen("ashmaw", "ashen_steppe", 2, 55.0D, 5.5D, 0.24D, 20),

            new Denizen("pinekin", "pinewold", 1, 24.0D, 3.5D, 0.24D, 34),
            new Denizen("pinecrawler", "pinewold", 1, 37.0D, 3.5D, 0.20D, 34),

            new Denizen("marshulk", "coldmarsh", 2, 34.0D, 5.5D, 0.30D, 20),
            new Denizen("marshmaw", "coldmarsh", 2, 53.0D, 5.5D, 0.26D, 20),

            new Denizen("chalkin", "chalk_downs", 1, 47.0D, 3.0D, 0.18D, 34),
            new Denizen("chalkcrawler", "chalk_downs", 1, 32.0D, 3.0D, 0.28D, 34),

            new Denizen("verdlet", "verdant_vale", 0, 16.0D, 2.0D, 0.26D, 50),
            new Denizen("verdmote", "verdant_vale", 0, 11.0D, 2.5D, 0.28D, 50),

            new Denizen("bloomkin", "bloomfen", 1, 25.0D, 4.0D, 0.26D, 34),
            new Denizen("bloomcrawler", "bloomfen", 1, 16.0D, 5.0D, 0.30D, 34),

            new Denizen("amberhulk", "amber_scrub", 2, 37.0D, 6.0D, 0.26D, 20),
            new Denizen("ambermaw", "amber_scrub", 2, 32.0D, 7.0D, 0.30D, 20),

            new Denizen("goldkin", "goldwood", 1, 27.0D, 5.0D, 0.26D, 34),

            new Denizen("reedhulk", "reedbasin", 2, 39.0D, 6.0D, 0.28D, 20),
            new Denizen("reedmaw", "reedbasin", 2, 42.0D, 7.0D, 0.30D, 20),

            new Denizen("cinderprime", "cinder_flats", 3, 84.0D, 7.5D, 0.30D, 10),
            new Denizen("cinderwarden", "cinder_flats", 3, 58.0D, 8.5D, 0.28D, 10),

            new Denizen("emberhulk", "emberwood", 2, 92.0D, 5.0D, 0.20D, 20),
            new Denizen("embermaw", "emberwood", 2, 50.0D, 6.5D, 0.22D, 20),

            new Denizen("sulphprime", "sulphur_springs", 3, 114.0D, 7.0D, 0.24D, 10),
            new Denizen("sulphwarden", "sulphur_springs", 3, 61.0D, 8.5D, 0.28D, 10));

    /** What each ring is drawn with, so that how far out you are reads at a glance. */
    public static final Map<Integer, String> SHAPES = Map.of(
            0, FaunaRules.FORAGER,
            1, BeastRules.PROWLER,
            2, BeastRules.WIGHT,
            3, BeastRules.BRUTE);

    /** The shape one of these is drawn with, or the empty string. */
    public static String shapeOf(Denizen one) {
        String found = SHAPES.get(one.ring());
        return found == null ? "" : found;
    }

    /** The creature of this name, or null. */
    public static Denizen denizen(String id) {
        for (Denizen one : DENIZENS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Everything living in one crossing, out of the given roster. */
    public static List<Denizen> living(String place, List<Denizen> roster) {
        List<Denizen> found = new ArrayList<>();
        for (Denizen one : roster) {
            if (one.place().equals(place)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** The rarest anything here may be. */
    public static final int LEAST_WEIGHT = 8;

    /** What one of these leaves behind. */
    public static final List<String> LEAVINGS = List.of(
            "minecraft:amethyst_shard", "minecraft:bone", "minecraft:string",
            "minecraft:glow_ink_sac", "minecraft:copper_ingot",
            "minecraft:rotten_flesh", "minecraft:prismarine_shard",
            "minecraft:blaze_powder");

    /** What this one leaves behind, by where it sits in the roster. */
    public static String leaves(Denizen one) {
        int at = DENIZENS.indexOf(one);
        return at < 0 ? "" : LEAVINGS.get(at % LEAVINGS.size());
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the Vale's roster. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(DENIZENS));
        bad.addAll(faultsInPlaces(DENIZENS, PLACES));
        bad.addAll(faultsInDistance(DENIZENS));
        bad.addAll(faultsInEmptyPlaces(DENIZENS, PLACES));
        bad.addAll(faultsInGradient(DENIZENS));
        bad.addAll(faultsInBands());
        bad.addAll(faultsInCeiling(DENIZENS, BeastRules.BEASTS));
        bad.addAll(faultsInRarity(DENIZENS, LEAST_WEIGHT));
        bad.addAll(faultsInShapes(DENIZENS, MythRules.forms()));
        bad.addAll(faultsInLeavings(DENIZENS));
        return List.copyOf(bad);
    }

    /** No two creatures written under one name. */
    public static List<String> faultsInNames(List<Denizen> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Denizen one : roster) {
            if (!seen.add(one.id())) {
                bad.add("two creatures are called " + one.id() + ", and whichever "
                        + "the game registers second is a row nothing ever spawns");
            }
        }
        return bad;
    }

    /** Everything lives in a crossing the Vale really has. */
    public static List<String> faultsInPlaces(List<Denizen> roster, List<String> places) {
        List<String> bad = new ArrayList<>();
        for (Denizen one : roster) {
            if (!places.contains(one.place())) {
                bad.add(one.id() + " lives in " + one.place() + " and the Vale has "
                        + "no such crossing, so it is a creature the rules place "
                        + "somewhere the world does not have");
            }
        }
        return bad;
    }

    /**
     * The ring on the row is the ring its crossing is in.
     *
     * <p>The column is stored rather than computed everywhere, which is a
     * deliberate exception to this module's usual rule — because a computed
     * column cannot be made to lie, and a rule about a column that cannot lie
     * is not a rule. Stored and checked here, a control can put the devil in
     * the sulphur springs and call it ring one, which is exactly the mistake a
     * roster makes when the flavour is decided first.
     */
    public static List<String> faultsInDistance(List<Denizen> roster) {
        List<String> bad = new ArrayList<>();
        for (Denizen one : roster) {
            int really = ring(one.place());
            if (really >= 0 && one.ring() != really) {
                bad.add(one.id() + " is written as " + one.ring() + " ring(s) out "
                        + "and " + one.place() + " is " + really + ", so the band "
                        + "of danger it is held to is the band of somewhere else");
            }
        }
        return bad;
    }

    /** Every crossing has something in it. */
    public static List<String> faultsInEmptyPlaces(List<Denizen> roster,
                                                   List<String> places) {
        List<String> bad = new ArrayList<>();
        for (String place : places) {
            if (living(place, roster).isEmpty()) {
                bad.add("nothing lives in " + place + ", so it is a direction the "
                        + "player walks and finds nothing — which reads as the "
                        + "dimension being unfinished, and is");
            }
        }
        return bad;
    }

    /**
     * Everything is inside its own ring's band.
     *
     * <p>The rule this file exists for. With {@link #faultsInBands} — which
     * says the bands themselves are contiguous and rising — it gives the whole
     * claim: walking further out is always harder, and never a coin flip.
     */
    public static List<String> faultsInGradient(List<Denizen> roster) {
        List<String> bad = new ArrayList<>();
        for (Denizen one : roster) {
            double[] band = band(one.ring());
            if (one.threat() < band[0]) {
                bad.add(one.id() + " is worth " + Math.round(one.threat()) + " and "
                        + one.ring() + " ring(s) out, where the floor is "
                        + Math.round(band[0]) + " — so a player who walked this far "
                        + "meets something they could have met at the portal, and "
                        + "the walk bought them nothing");
            }
        }
        for (Denizen one : roster) {
            double[] band = band(one.ring());
            if (one.threat() >= band[1]) {
                bad.add(one.id() + " is worth " + Math.round(one.threat()) + " and "
                        + one.ring() + " ring(s) out, where the ceiling is "
                        + Math.round(band[1]) + " — so something further in is "
                        + "harder than something further out, and which direction "
                        + "the player walked stops being a decision");
            }
        }
        return bad;
    }

    /**
     * The bands rise.
     *
     * <p>Note what is <em>not</em> checked here, because it was, and the
     * negative tests found that it could not fail. {@link #band} builds every
     * band out of one list of edges, so ring N starts exactly where ring N−1
     * stopped — two bands cannot overlap, because there is only one number
     * between them. A rule against overlapping would have been a rule that
     * never fires, which is worse than no rule: it reads like a guarantee.
     *
     * <p>What remains is the one way that construction can still stop being a
     * gradient, which is an edge that goes backwards. That turns a band into
     * nothing — a floor above its own ceiling — and every creature in that ring
     * fails {@link #faultsInGradient} at once. This says so in one line instead
     * of in as many lines as the ring has creatures.
     */
    public static List<String> faultsInBands() {
        List<String> bad = new ArrayList<>();
        for (int ring = 0; ring < RINGS; ring++) {
            double[] band = band(ring);
            if (band[0] >= band[1]) {
                bad.add("ring " + ring + " runs from " + band[0] + " to " + band[1]
                        + ", which is not a band at all — so either an edge goes "
                        + "backwards or two of them are the same number, and "
                        + "either way that ring has stopped being anywhere on the "
                        + "gradient");
            }
        }
        return bad;
    }

    /**
     * Nothing out here out-fights the hardest thing in the world.
     *
     * <p>The Vale is a place the player goes to on purpose, so it is allowed to
     * be harder than the overworld's ladder — but not unbounded, because the
     * ladder is what every piece of gear in the pack is priced against.
     */
    public static List<String> faultsInCeiling(List<Denizen> roster,
                                               List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        double hardest = 0.0D;
        String named = "";
        for (BeastRules.Beast beast : beasts) {
            if (beast.threat() > hardest) {
                hardest = beast.threat();
                named = beast.id();
            }
        }
        if (hardest <= 0.0D) {
            bad.add("there is no beast to hold the Vale under, so nothing here is "
                    + "held to anything");
        }
        for (Denizen one : roster) {
            if (hardest > 0.0D && one.threat() >= hardest) {
                bad.add(one.id() + " is worth " + Math.round(one.threat())
                        + " against " + named + "'s " + Math.round(hardest)
                        + ", which is the hardest thing in the world — so the "
                        + "ladder every piece of gear in the pack is priced "
                        + "against has something above it in another dimension");
            }
        }
        return bad;
    }

    /** Nothing so rare it is read about rather than met. */
    public static List<String> faultsInRarity(List<Denizen> roster, int leastWeight) {
        List<String> bad = new ArrayList<>();
        for (Denizen one : roster) {
            if (one.weight() < leastWeight) {
                bad.add(one.id() + " is chosen " + one.weight() + " times against a "
                        + "floor of " + leastWeight + ", so it is a creature most "
                        + "players will read about rather than meet");
            }
        }
        return bad;
    }

    /** Every ring is drawn with a shape this module has. */
    public static List<String> faultsInShapes(List<Denizen> roster, List<String> forms) {
        List<String> bad = new ArrayList<>();
        for (Denizen one : roster) {
            if (shapeOf(one).isEmpty()) {
                bad.add("nothing draws what lives " + one.ring() + " ring(s) out, "
                        + "so " + one.id() + " is a creature this module cannot put "
                        + "on the screen");
            }
        }
        for (Denizen one : roster) {
            String shape = shapeOf(one);
            if (!shape.isEmpty() && !forms.contains(shape)) {
                bad.add(one.id() + " is drawn as a " + shape + " and this module "
                        + "has no such shape, so it renders as a "
                        + "black-and-magenta cube for whoever meets it first");
            }
        }
        return bad;
    }

    /** Everything leaves something, and it is something that exists. */
    public static List<String> faultsInLeavings(List<Denizen> roster) {
        List<String> bad = new ArrayList<>();
        for (Denizen one : roster) {
            if (leaves(one).isEmpty()) {
                bad.add(one.id() + " leaves nothing, so killing it is a thing the "
                        + "player does for the experience alone");
            }
        }
        for (String left : LEAVINGS) {
            if (!left.startsWith("minecraft:") && !left.startsWith("elysium")) {
                bad.add(left + " is left behind and nothing in this pack or the "
                        + "game makes one of those");
            }
        }
        return bad;
    }
}
