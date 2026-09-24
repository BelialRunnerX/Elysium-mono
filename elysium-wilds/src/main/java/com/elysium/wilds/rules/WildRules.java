package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Where a world puts things, and how far you walk to find them. The JDK and
 * nothing else at all.
 *
 * <h2>The bug that is invisible by construction</h2>
 *
 * The game does not choose a biome from a weighted list. It places every biome
 * at a box in a six-dimensional space — temperature, humidity, continentalness,
 * erosion, depth, weirdness — and then, for each column of the world, works out
 * which box is <em>nearest</em> to the climate that column came out as. The
 * biome with the nearest box wins.
 *
 * <p>Which means a biome can be declared, registered, translated, listed in
 * every compass and wiki in the pack, and <b>never generate anywhere</b>,
 * because some other biome's box is nearer to every climate the noise ever
 * produces. Nothing reports this. It is not a crash, it is not a warning, the
 * biome is genuinely there — it just has no territory. The only way anybody
 * ever finds out is a player spending an afternoon flying, and even then they
 * conclude they were unlucky.
 *
 * <p>The other end of the same coin is a biome whose box is nearest to almost
 * everything, so a world is one biome with a few islands in it. That one is
 * noticed eventually, and blamed on the seed.
 *
 * <p>Neither is a thing anybody can check by reading a list of biomes, because
 * both are properties of <em>all the boxes together</em>. So this class is the
 * game's own nearest-box rule, reimplemented against the JDK alone, and the
 * harness sweeps the space to find out what every biome actually gets.
 *
 * <h2>And the second half: finding your way about it</h2>
 *
 * A compass that says "swamp: 41,000 blocks away" is not a tool, it is a
 * taunt. How far a player walks to the nearest instance of a biome falls with
 * how much of the space that biome holds, so the share this class measures is
 * also the number that says whether a biome is findable at all.
 */
public final class WildRules {

    private WildRules() {
    }

    // ==================================================================
    // The space
    // ==================================================================

    /** The six the game rolls, in the order it stores them. */
    public static final List<String> PARAMETERS = List.of(
            "temperature", "humidity", "continentalness", "erosion", "depth", "weirdness");

    /** How many there are, plus the offset the game searches as a seventh. */
    public static final int AXES = 6;

    /**
     * One axis of a box, in the game's own units.
     *
     * <p>The game stores these as whole numbers a ten-thousandth apart and
     * does its arithmetic in {@code long}; that is an implementation detail of
     * not wanting floating point in worldgen, and it changes no ordering. A
     * point is a span whose ends are equal, which is exactly how a
     * single-number parameter in a dimension file is read.
     */
    public record Span(double low, double high) {

        public Span {
            if (low > high) {
                throw new IllegalArgumentException("a span that starts after it ends");
            }
        }

        public static Span at(double point) {
            return new Span(point, point);
        }

        /**
         * How far a value is from this span, which is nothing if it is inside.
         *
         * <p>The game's own rule, and the only part of it worth restating: a
         * value inside the box costs nothing at all on that axis, so a wide
         * box does not merely win more often, it wins <em>outright</em> against
         * any point outside it whenever the climate lands inside.
         */
        public double distance(double value) {
            double past = value - high;
            double before = low - value;
            return past > 0.0D ? past : Math.max(before, 0.0D);
        }

        /** How much of the range from -1 to 1 this span covers. */
        public double width() {
            double clampedLow = Math.max(-1.0D, Math.min(1.0D, low));
            double clampedHigh = Math.max(-1.0D, Math.min(1.0D, high));
            return Math.max(0.0D, clampedHigh - clampedLow);
        }
    }

    /**
     * One biome's claim on the space.
     *
     * @param biome  what the dimension file calls it
     * @param spans  one span per axis, in {@link #PARAMETERS} order
     * @param offset the game's seventh axis: a flat penalty, so a biome with a
     *               non-zero offset loses every tie
     */
    public record Region(String biome, List<Span> spans, double offset) {

        public Region {
            spans = List.copyOf(spans);
            if (spans.size() != AXES) {
                throw new IllegalArgumentException(biome + " claims " + spans.size()
                        + " axes and the space has " + AXES);
            }
            if (offset < 0.0D) {
                throw new IllegalArgumentException("a biome that wins ties it should "
                        + "lose");
            }
        }

        /**
         * How far this box is from a climate, the way the game measures it.
         *
         * <p>Squared distances summed, because the game never takes a square
         * root — it only ever compares — and taking one here would introduce a
         * rounding difference against the thing this is meant to predict.
         */
        public double distance(double[] climate) {
            double total = 0.0D;
            for (int axis = 0; axis < AXES; axis++) {
                double apart = spans.get(axis).distance(climate[axis]);
                total += apart * apart;
            }
            return total + offset * offset;
        }

        /** Whether this claim is a single point rather than a box. */
        public boolean point() {
            for (Span span : spans) {
                if (span.low() != span.high()) {
                    return false;
                }
            }
            return true;
        }

    /**
         * Whether this box sits entirely inside another.
         *
         * <p>Which matters more than it looks. A climate inside both boxes is at
         * distance nothing from both, the game breaks that tie by declaration
         * order, and a climate inside only the outer one belongs to the outer one
         * — so a box contained in an <em>earlier</em> box wins nothing anywhere,
         * ever. Not rarely. Never.
         */
        public boolean inside(Region other) {
            for (int axis = 0; axis < AXES; axis++) {
                Span mine = spans.get(axis);
                Span theirs = other.spans().get(axis);
                if (mine.low() < theirs.low() || mine.high() > theirs.high()) {
                    return false;
                }
            }
            return offset >= other.offset();
        }
    }

    // ==================================================================
    // Who wins where
    // ==================================================================

    /**
     * Which biome a climate lands in.
     *
     * <p>Ties go to whichever was declared first, which is the game's own
     * behaviour and is the reason two biomes at the same point are not two
     * biomes.
     */
    public static Region nearest(List<Region> regions, double[] climate) {
        Region best = null;
        double closest = Double.MAX_VALUE;
        for (Region region : regions) {
            double apart = region.distance(climate);
            if (apart < closest) {
                closest = apart;
                best = region;
            }
        }
        return best;
    }

    /** How many climates to try before believing a share. */
    public static final int SAMPLES = 200_000;

    /**
     * What share of the space each biome actually holds.
     *
     * <p>Sampled rather than integrated, because the shape of a Voronoi cell
     * in six dimensions under a box metric has no closed form worth writing
     * and this is a question about whether a number is roughly a tenth or
     * roughly nothing.
     *
     * <p>The sampler is a fixed seed on purpose. A share that moves between
     * runs is a check nobody can act on: a build that fails one time in five
     * gets rerun rather than read.
     */
    public static Map<String, Double> shares(List<Region> regions, int samples) {
        Map<String, Integer> won = new LinkedHashMap<>();
        for (Region region : regions) {
            won.put(region.biome(), 0);
        }
        Random random = new Random(0x5EED_C11FFL);
        double[] climate = new double[AXES];
        for (int sample = 0; sample < samples; sample++) {
            for (int axis = 0; axis < AXES; axis++) {
                climate[axis] = random.nextDouble() * 2.0D - 1.0D;
            }
            Region winner = nearest(regions, climate);
            if (winner != null) {
                won.merge(winner.biome(), 1, Integer::sum);
            }
        }
        Map<String, Double> found = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : won.entrySet()) {
            found.put(entry.getKey(), (double) entry.getValue() / samples);
        }
        return Map.copyOf(found);
    }

    /**
     * Every distinct biome in a world, in the order first declared.
     *
     * <p>Distinct, because a biome is allowed to claim several boxes and
     * vanilla does it constantly — a band either side of a river, a cold
     * variant and a warm one. Counting entries instead of biomes would make a
     * biome's fair share depend on how many pieces somebody cut its claim
     * into, which is exactly backwards.
     */
    public static List<String> biomes(List<Region> regions) {
        List<String> named = new ArrayList<>();
        for (Region region : regions) {
            if (!named.contains(region.biome())) {
                named.add(region.biome());
            }
        }
        return List.copyOf(named);
    }

    /** Every biome swallowed whole by one declared before it. */
    public static List<String> swallowed(List<Region> regions) {
        List<String> caught = new ArrayList<>();
        for (int later = 0; later < regions.size(); later++) {
            for (int earlier = 0; earlier < later; earlier++) {
                Region inner = regions.get(later);
                Region outer = regions.get(earlier);
                if (!inner.biome().equals(outer.biome()) && inner.inside(outer)) {
                    caught.add(inner.biome() + "'s claim sits entirely inside "
                            + outer.biome() + "'s, which was declared first, so it ties "
                            + "everywhere it could win and loses every tie");
                }
            }
        }
        return caught;
    }

    /** Every biome that is declared, registered, named — and never generated. */
    public static List<String> unreachable(List<Region> regions) {
        Map<String, Double> held = shares(regions, SAMPLES);
        List<String> caught = new ArrayList<>();
        for (String biome : biomes(regions)) {
            if (held.getOrDefault(biome, 0.0D) <= 0.0D) {
                caught.add(biome + " holds none of the space, so it is registered, "
                        + "translated, listed and never generated");
            }
        }
        return caught;
    }

    /**
     * How much of itself one biome may be, above which nothing is a world.
     *
     * <p>Four fifths. This is not a design decision about any particular
     * world; it is where the word stops applying. A place that is 80% one
     * biome is that biome, with some scenery, and calling the other entries
     * biomes is a courtesy.
     */
    public static final double NOTHING_IS_A_WORLD_ABOVE = 0.80D;

    /**
     * Every biome that is too much, or too little, of the world it is in.
     *
     * <p><b>How much is too much is not something this file can know.</b> A
     * frozen moon ought to be mostly frozen desert and an overworld ought not
     * to be mostly anything, and no constant is right for both. So each world
     * states what it is meant to look like — {@code mostOfIt}, the largest
     * share any one of its biomes is allowed — and this proves the world it
     * actually generates matches what it said about itself. A world that
     * claims a high ceiling is making a visible claim somebody can disagree
     * with in review, which is the most that can honestly be asked of it.
     *
     * <p>There is deliberately <b>no floor here</b>, and there was one until a
     * river failed it. A river biome is a thin thread through somewhere else;
     * that is what a river is. It held three per cent of a world against an
     * even share of thirty-three, and a rule comparing those two numbers said
     * it was vanishing — which was the rule asking a question nobody has,
     * because nobody wants rivers to be a third of the world.
     *
     * <p>The question a player actually has is how far they walk to find one,
     * and that is {@link #tooFar}. A biome too thin to stumble into fails
     * there, in blocks, for a reason anybody can check by walking.
     */
    public static List<String> lopsided(List<Region> regions, double mostOfIt) {
        Map<String, Double> held = shares(regions, SAMPLES);
        List<String> named = biomes(regions);
        List<String> caught = new ArrayList<>();
        if (mostOfIt > NOTHING_IS_A_WORLD_ABOVE) {
            caught.add("this world allows one biome " + Math.round(mostOfIt * 100)
                    + "% of itself, and above " + Math.round(NOTHING_IS_A_WORLD_ABOVE * 100)
                    + "% a place is that biome with scenery rather than a world");
        }
        for (String biome : named) {
            double share = held.getOrDefault(biome, 0.0D);
            if (share > mostOfIt) {
                caught.add(biome + " is " + Math.round(share * 100) + "% of the world "
                        + "and the world says no biome of its own may be more than "
                        + Math.round(mostOfIt * 100) + "%");
            }
        }
        return caught;
    }

    // ==================================================================
    // How far you walk
    // ==================================================================

    /**
     * How wide a patch of one biome is, in blocks, when it holds the whole
     * world.
     *
     * <p>The game samples climate on a four-block grid and its noise turns
     * over at roughly this scale. It is a measured constant rather than a
     * derived one, and it is here so that the arithmetic below has one number
     * in it that is honest about being a measurement.
     */
    public static final double WORLD_GRAIN = 1_400.0D;

    /**
     * Roughly how far a player walks to the nearest instance of a biome.
     *
     * <p>A biome holding share {@code s} of the space turns up in about that
     * fraction of patches, so the nearest one is about one patch away divided
     * by the square root of the share — area, not distance, is what scales
     * with how much of the space you hold. It is an estimate and it is
     * deliberately crude; what it is for is telling the difference between
     * "over that hill" and "past the edge of the map", and for that it does
     * not need to be better.
     */
    public static double walkTo(double share) {
        if (share <= 0.0D) {
            return Double.MAX_VALUE;
        }
        return WORLD_GRAIN / Math.sqrt(share);
    }

    /**
     * How far a player may be asked to walk for a biome.
     *
     * <p>Twenty thousand blocks. A compass that answers with a bigger number
     * than this is not a tool for finding somewhere, it is a tool for learning
     * that you are not going there.
     */
    public static final double FURTHEST_WORTH_WALKING = 20_000.0D;

    /** Every biome nobody is ever going to walk to. */
    public static List<String> tooFar(List<Region> regions) {
        Map<String, Double> held = shares(regions, SAMPLES);
        List<String> caught = new ArrayList<>();
        for (String biome : biomes(regions)) {
            double share = held.getOrDefault(biome, 0.0D);
            if (share <= 0.0D) {
                // Already reported, and far better, by unreachable(). Saying
                // it again here as a nine-quintillion-block walk is a second
                // line about one problem, dressed as a joke.
                continue;
            }
            double walk = walkTo(share);
            if (walk > FURTHEST_WORTH_WALKING) {
                caught.add(biome + " is about " + Math.round(walk)
                        + " blocks away, which is a compass telling a player where they "
                        + "are not going");
            }
        }
        return caught;
    }

    // ==================================================================
    // Looking for one, which is where a compass becomes a server problem
    // ==================================================================
    //
    // Finding the nearest instance of a biome is a scan: the game samples
    // climate on a grid out from where you are standing and stops when it hits
    // one. The cost of that scan grows with the square of how far it looks, it
    // runs on the server thread, and it is synchronous.
    //
    // Which is how a compass becomes a performance bug that nobody files
    // against the compass. A player holding one down, or four players on a
    // server each looking for something rare, is a server that stutters — and
    // what gets reported is "the server lags", so whoever wrote the compass
    // never hears about it. The mod this replaces has exactly this shape.
    //
    // So the scan here costs the same no matter what is being looked for. How
    // far it looks comes from how common the thing is; how coarsely it looks
    // is then worked out from how far, so that the two multiply out to one
    // stated number of samples every time.

    /** How many climates one search may sample, however far it reaches. */
    public static final int SAMPLES_PER_SEARCH = 40_000;

    /** And how far it may reach, however rare the thing is. */
    public static final double MOST_SEARCH_BLOCKS = 12_800.0D;

    /** How much further than the expected distance to look before giving up. */
    public static final double SEARCH_MARGIN = 2.5D;

    /** The finest grid worth sampling, since climate does not change faster. */
    public static final double FINEST_STEP = 16.0D;

    /**
     * How far to look for a biome holding this share of the world.
     *
     * <p>Not a constant, because looking twelve thousand blocks for a thing
     * that is four hundred blocks away is eight hundred times the work for the
     * same answer. It is the expected distance with room for a bad seed, and
     * then a ceiling, because past the ceiling the answer is not useful to a
     * player anyway.
     */
    public static double searchRadius(double share) {
        if (share <= 0.0D) {
            return MOST_SEARCH_BLOCKS;
        }
        return Math.min(MOST_SEARCH_BLOCKS, walkTo(share) * SEARCH_MARGIN);
    }

    /**
     * How coarse a grid to sample at, so that the search costs what it says.
     *
     * <p>This is the whole trick and it is not a clever one: a square of side
     * {@code 2r} sampled every {@code step} blocks takes {@code (2r/step)²}
     * samples, so choosing the step from the radius makes the product a
     * constant. A search for something far away is not a slower search, it is
     * a blurrier one.
     */
    public static double stepFor(double radius) {
        double across = 2.0D * Math.max(0.0D, radius);
        return Math.max(FINEST_STEP, across / Math.sqrt(SAMPLES_PER_SEARCH));
    }

    /** How many samples a scan of this radius at this step actually takes. */
    public static long samples(double radius, double step) {
        if (step <= 0.0D) {
            return Long.MAX_VALUE;
        }
        long across = (long) Math.ceil(2.0D * Math.max(0.0D, radius) / step) + 1L;
        return across * across;
    }

    /**
     * The smallest patch a scan at this step can pass straight over.
     *
     * <p>A blurrier search can miss things, and saying so is the honest part.
     * What makes it acceptable is {@link #WORLD_GRAIN}: a patch of biome is
     * about fourteen hundred blocks across, so a step well under that finds
     * every patch there is. A step that ever climbed above the grain would be
     * a compass that says "no such place" about somewhere a player is standing
     * in, which is worse than a slow compass.
     */
    public static double mightMiss(double step) {
        return step;
    }

    /** How long a player must wait between searches. */
    public static final int COOLDOWN_TICKS = 60;

    /** And how many samples a second one player can therefore ask for. */
    public static double worstPerSecond() {
        return SAMPLES_PER_SEARCH * 20.0D / Math.max(1, COOLDOWN_TICKS);
    }

    /**
     * The most sampling a single player may cost the server, per second.
     *
     * <p>Fifteen thousand. A climate sample is a handful of noise lookups, so
     * this is a small slice of one second of one thread — small enough that a
     * server full of people all holding the compass down is a server that is
     * fine, which is the only version of this that is actually safe.
     */
    public static final double MOST_PER_SECOND = 15_000.0D;

    /** Everything about the search that would make it a server problem. */
    public static List<String> tooExpensive() {
        List<String> caught = new ArrayList<>();
        if (worstPerSecond() > MOST_PER_SECOND) {
            caught.add("one player holding the compass down costs "
                    + Math.round(worstPerSecond()) + " samples a second, and the most "
                    + "any one player may cost is " + Math.round(MOST_PER_SECOND));
        }
        double coarsest = stepFor(MOST_SEARCH_BLOCKS);
        if (mightMiss(coarsest) >= WORLD_GRAIN) {
            caught.add("the furthest search samples every " + Math.round(coarsest)
                    + " blocks and a patch of biome is about " + Math.round(WORLD_GRAIN)
                    + " across, so the compass would walk over things and report them "
                    + "missing");
        }
        return caught;
    }

    // ==================================================================
    // Structures, which are the same question with different words
    // ==================================================================

    /**
     * How the game spaces a kind of structure.
     *
     * @param spacing    the average distance between attempts, in chunks
     * @param separation the least distance between attempts, in chunks
     */
    public record Spacing(int spacing, int separation) {

        public Spacing {
            if (spacing < 1) {
                throw new IllegalArgumentException("a structure with no spacing is a "
                        + "structure in every chunk");
            }
            if (separation < 0 || separation >= spacing) {
                throw new IllegalArgumentException("a separation that is not smaller "
                        + "than the spacing is a structure that never places: the game "
                        + "rejects it outright");
            }
        }

        /** Roughly how far apart two of these end up, in blocks. */
        public double apart() {
            return spacing * 16.0D;
        }

        /** How many you would expect inside a square of this many blocks. */
        public double within(double blocks) {
            double side = blocks / apart();
            return side * side;
        }
    }

    /**
     * How far a player will explore before they have met one of everything.
     *
     * <p>Not a decision — a consequence. It is here because the number that
     * gets typed into a structure set is chunks-between-attempts, and the
     * number a player experiences is how long before they see one, and nobody
     * converts between the two.
     */
    public static final double EXPLORED_EARLY = 3_000.0D;

    /** Every structure a player will not meet in their first few thousand blocks. */
    public static List<String> neverMet(Map<String, Spacing> structures) {
        List<String> caught = new ArrayList<>();
        for (Map.Entry<String, Spacing> entry : structures.entrySet()) {
            double expected = entry.getValue().within(EXPLORED_EARLY);
            if (expected < 1.0D) {
                caught.add(entry.getKey() + " turns up " + String.format("%.2f", expected)
                        + " times in the first " + Math.round(EXPLORED_EARLY)
                        + " blocks, so most players never see one and it may as well "
                        + "not be in the pack");
            }
        }
        return caught;
    }

    /** How many of a kind of structure is too many to still be worth finding. */
    public static final double COMMON_ENOUGH_TO_IGNORE = 40.0D;

    /** Every structure there are so many of that finding one means nothing. */
    public static List<String> wallpaper(Map<String, Spacing> structures) {
        List<String> caught = new ArrayList<>();
        for (Map.Entry<String, Spacing> entry : structures.entrySet()) {
            double expected = entry.getValue().within(EXPLORED_EARLY);
            if (expected > COMMON_ENOUGH_TO_IGNORE) {
                caught.add(entry.getKey() + " turns up " + Math.round(expected)
                        + " times in the first " + Math.round(EXPLORED_EARLY)
                        + " blocks, so finding one is not finding anything");
            }
        }
        return caught;
    }
}
