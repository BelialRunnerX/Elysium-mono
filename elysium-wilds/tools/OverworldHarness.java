package com.elysium.wilds.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A hundred and forty biomes in somebody else's overworld, executed.
 *
 * <p>The Vale's harness had one thing to prove: that every biome generates.
 * This one has two, and the second is the harder.
 *
 * <ol>
 *   <li><b>Our claim is a partition of the region we say we took.</b> Five
 *       warmths by five wets by five reliefs, bands meeting edge to edge, so
 *       every climate in the region belongs to exactly one of ours. No gaps,
 *       no overlaps, and a biome contained in another's claim is a biome that
 *       never generates.</li>
 *   <li><b>Being declared first is enough.</b> Vanilla's 7,593 entries are
 *       not in this file and cannot be — they are a dump. What can be proved
 *       here is the rule that makes the dump not matter: inside one of our
 *       boxes our distance is the depth term and nothing else, so no box
 *       anywhere can beat it and a tie goes to whoever was declared first.
 *       Proved against boxes built to be the worst case a dump could
 *       contain.</li>
 * </ol>
 */
public final class OverworldHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theRoster();
        thePartition();
        theFirstDeclared();
        theShare();
        theColours();
        theDecoration();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a hundred and forty biomes on a "
                + "partition of the half of the inland surface they claim, every "
                + "one of them winning its own cell against the worst list "
                + "vanilla could be, none of them inside another's claim, none "
                + "of them painted like another, and every one of them decorated "
                + "like a biome the game already ships");
    }

    // ==================================================================

    private static void theRoster() {
        System.out.println("the roster");
        check(OverworldRules.faults().isEmpty(),
                "something wrong with the roster itself: " + OverworldRules.faults());

        int lands = OverworldRules.WARMTHS.size() * OverworldRules.WETS.size()
                * OverworldRules.RELIEFS.size();
        check(OverworldRules.LANDS.size() == lands, "the crossing is " + lands
                + " and there are " + OverworldRules.LANDS.size() + " surface "
                + "biomes, so the roster is not the crossing");
        int caves = OverworldRules.WARMTHS.size() * OverworldRules.CAVE_WETS;
        check(OverworldRules.CAVES.size() == caves, "the cave crossing is " + caves
                + " and there are " + OverworldRules.CAVES.size());
        check(OverworldRules.howMany() == lands + caves,
                "the roster does not add up to its two halves");

        // A hundred and ten biomes went out with wwoo and twenty-three more
        // with Quark's worldgen half. Fewer than that is a conversion that
        // quietly did less than the jar it replaced.
        check(OverworldRules.howMany() >= 133, "there are only "
                + OverworldRules.howMany() + " biomes and the two jars this "
                + "replaces had 133 between them");

        Set<String> seen = new LinkedHashSet<>();
        for (String name : OverworldRules.names()) {
            check(seen.add(name), name + " names two biomes");
            check(name.matches("[a-z][a-z_]*[a-z]"), name + " is not a name the "
                    + "game will take as a path");
        }
        check(OverworldRules.claims().size()
                        == OverworldRules.howMany() * OverworldRules.STRANGENESS.size(),
                "every biome claims one box per band of weirdness and the total "
                        + "does not say so");

        for (OverworldRules.Land one : OverworldRules.LANDS) {
            check(OverworldRules.land(one.name()) == one,
                    one.name() + " cannot be found by its own name");
        }
        for (OverworldRules.Cave one : OverworldRules.CAVES) {
            check(OverworldRules.cave(one.name()) == one,
                    one.name() + " cannot be found by its own name");
        }
        System.out.println("  " + OverworldRules.LANDS.size() + " on the surface, "
                + OverworldRules.CAVES.size() + " underground, "
                + OverworldRules.claims().size() + " boxes between them");
    }

    // ==================================================================

    /**
     * Every climate in the region belongs to exactly one of ours, and every
     * climate outside it to none.
     *
     * <p>Swept rather than sampled where sweeping is possible: the bands are
     * a few dozen numbers, so every crossing of every band with every other
     * can be walked, and the answer is arithmetic. Sampling is then thrown at
     * it as well, because a sweep only ever visits the middles of cells and
     * the interesting failures are at the edges.
     */
    private static void thePartition() {
        System.out.println("the partition");
        List<WildRules.Region> claims = OverworldRules.claims();
        check(OverworldRules.faultsInClaims(claims).isEmpty(),
                "the claims overlap or contain one another: "
                        + OverworldRules.faultsInClaims(claims));

        // Every cell of the surface grid, at its middle, belongs to the biome
        // whose cell it is.
        for (OverworldRules.Land one : OverworldRules.LANDS) {
            for (WildRules.Span strange : OverworldRules.STRANGENESS) {
                double[] climate = OverworldRules.climate(
                        middle(OverworldRules.WARMTHS.get(one.warmth())),
                        middle(OverworldRules.WETS.get(one.wet())),
                        middle(OverworldRules.INLAND),
                        middle(OverworldRules.RELIEFS.get(one.relief())),
                        0.0D, middle(strange));
                String got = OverworldRules.at(climate);
                check(one.name().equals(got), "the middle of " + one.name()
                        + "'s own cell belongs to " + got);
                check(one.name().equals(
                                WildRules.nearest(claims, climate).biome()),
                        "the middle of " + one.name() + "'s own cell is nearest "
                                + WildRules.nearest(claims, climate).biome()
                                + " of our own boxes");
            }
        }
        for (OverworldRules.Cave one : OverworldRules.CAVES) {
            for (WildRules.Span strange : OverworldRules.STRANGENESS) {
                double[] climate = OverworldRules.climate(
                        middle(OverworldRules.WARMTHS.get(one.warmth())),
                        middle(one.wetSpan()), middle(OverworldRules.INLAND),
                        0.0D, 1.0D, middle(strange));
                check(one.name().equals(OverworldRules.at(climate)),
                        "the middle of " + one.name() + "'s own cell belongs to "
                                + OverworldRules.at(climate));
            }
        }

        // Climate we did not claim belongs to nobody of ours. The three that
        // matter: the sea, the shore, and the bands of weirdness we left
        // alone.
        Random random = new Random(20260925L);
        int outside = 0;
        for (int at = 0; at < 60_000; at++) {
            double weirdness = pick(random);
            double continentalness = pick(random);
            double[] climate = OverworldRules.climate(pick(random), pick(random),
                    continentalness, pick(random), 0.0D, weirdness);
            boolean inland = continentalness >= OverworldRules.INLAND.low();
            boolean takenBand = false;
            for (WildRules.Span strange : OverworldRules.STRANGENESS) {
                takenBand |= weirdness >= strange.low() && weirdness <= strange.high();
            }
            String got = OverworldRules.at(climate);
            if (inland && takenBand) {
                check(got != null, "a climate inland, at the surface, in a band "
                        + "we took belongs to none of ours, so there is a hole in "
                        + "the partition");
            } else {
                outside++;
                check(got == null, "a climate we did not claim belongs to " + got);
            }
        }
        check(outside > 20_000, "only " + outside + " of 60,000 climates fell "
                + "outside the claim, so this check barely tried the thing it "
                + "is about");

        // The seams. A gap between two bands is climate that belongs to
        // nothing and goes to whatever happens to be nearest, and it is
        // invisible everywhere except right on the line.
        for (int warmth = 1; warmth < OverworldRules.WARMTHS.size(); warmth++) {
            double seam = OverworldRules.WARMTHS.get(warmth).low();
            check(seam == OverworldRules.WARMTHS.get(warmth - 1).high(),
                    "temperature band " + warmth + " does not start where the one "
                            + "below it stopped");
            for (WildRules.Span strange : OverworldRules.STRANGENESS) {
                double[] climate = OverworldRules.climate(seam, 0.0D,
                        middle(OverworldRules.INLAND), 0.0D, 0.0D, middle(strange));
                check(OverworldRules.at(climate) != null,
                        "the seam between two temperature bands belongs to nothing");
            }
        }
        System.out.println("  every cell's own middle its own, every seam claimed, "
                + "and 60,000 climates told apart");
    }

    // ==================================================================

    /**
     * Being declared first is enough.
     *
     * <p>Vanilla's list is a dump and not a Java file, so the thing to prove
     * here is the rule rather than the data: inside one of our boxes our
     * distance is the depth term alone, and no box anywhere can do better
     * than equal it. So a list of ours followed by the worst list vanilla
     * could possibly be still answers with ours.
     */
    private static void theFirstDeclared() {
        System.out.println("what being declared first buys");
        List<WildRules.Region> ours = OverworldRules.claims();

        // The worst vanilla could be: a box covering the whole space at the
        // surface, and another covering the whole space underground. Nothing
        // in a real list can beat a box that contains everything.
        List<WildRules.Region> worst = List.of(
                new WildRules.Region("vanilla:everything_surface", List.of(
                        whole(), whole(), whole(), whole(),
                        OverworldRules.SURFACE, whole()), 0.0D),
                new WildRules.Region("vanilla:everything_underground", List.of(
                        whole(), whole(), whole(), whole(),
                        OverworldRules.UNDERGROUND, whole()), 0.0D));

        List<WildRules.Region> oursFirst = new ArrayList<>(ours);
        oursFirst.addAll(worst);
        List<WildRules.Region> oursLast = new ArrayList<>(worst);
        oursLast.addAll(ours);

        Random random = new Random(770099L);
        int inside = 0;
        for (int at = 0; at < 40_000; at++) {
            WildRules.Region claim = ours.get(random.nextInt(ours.size()));
            double[] climate = within(claim, random);
            inside++;
            WildRules.Region won = WildRules.nearest(oursFirst, climate);
            check(won.biome().equals(claim.biome()),
                    "a climate inside " + claim.biome() + "'s own box went to "
                            + won.biome() + " with our list declared first, which "
                            + "is the whole of why this design works");
        }
        check(inside == 40_000, "the sweep did not run");

        // And the other way about, which is the bug this is a guard against:
        // declared last, a box inside a box that came first wins nothing.
        int stolen = 0;
        for (int at = 0; at < 2_000; at++) {
            WildRules.Region claim = ours.get(random.nextInt(ours.size()));
            double[] climate = within(claim, random);
            if (!WildRules.nearest(oursLast, climate).biome().equals(claim.biome())) {
                stolen++;
            }
        }
        check(stolen == 2_000, "declared after a box that contains ours, only "
                + stolen + " of 2,000 climates were lost — if that number is not "
                + "all of them then this harness is not testing what it says, "
                + "because a contained box declared second wins nothing at all");

        // The depth term is the same for everybody at the surface, which is
        // the step the argument above rests on.
        for (double depth : new double[] {-0.4D, 0.0D, 0.3D, 1.0D, 1.6D}) {
            WildRules.Span surface = OverworldRules.SURFACE;
            double mine = surface.distance(depth);
            check(mine == Math.abs(depth), "the surface is a point at 0 and the "
                    + "distance to it from " + depth + " is " + mine);
        }
        System.out.println("  40,000 climates inside our own boxes kept against a "
                + "box that contains the whole world, and all 2,000 lost when the "
                + "order is reversed");
    }

    // ==================================================================

    private static void theShare() {
        System.out.println("what we took");
        double share = OverworldRules.shareOfInlandSurface();
        check(share <= OverworldRules.MOST_OF_THE_LAND, "we take "
                + Math.round(share * 1000) / 10.0 + "% of the inland surface and "
                + "the ceiling is " + OverworldRules.MOST_OF_THE_LAND * 100
                + "%, which is an overhaul rather than a share of one");
        check(share >= OverworldRules.LEAST_OF_THE_LAND, "we take only "
                + Math.round(share * 1000) / 10.0 + "% of the inland surface, "
                + "which is 140 biomes nobody will ever stand in");

        // And it is a share rather than all of it: vanilla keeps bands of its
        // own, which is the one thing that keeps its biomes alive.
        double left = 0.0D;
        double taken = 0.0D;
        for (WildRules.Span band : OverworldRules.STRANGENESS) {
            taken += band.high() - band.low();
        }
        left = 2.0D - taken;
        check(left > 0.5D, "vanilla is left " + left + " of the weirdness axis, "
                + "which is not enough room for 53 biomes");

        // Every one of ours gets a cell of the same size, because the bands
        // are the same width for everybody on every axis but the ends.
        double smallest = Double.MAX_VALUE;
        for (OverworldRules.Land one : OverworldRules.LANDS) {
            double volume = 1.0D;
            volume *= OverworldRules.WARMTHS.get(one.warmth()).width();
            volume *= OverworldRules.WETS.get(one.wet()).width();
            volume *= OverworldRules.RELIEFS.get(one.relief()).width();
            smallest = Math.min(smallest, volume);
            check(volume > 0.0D, one.name() + " claims a cell of no width at all, "
                    + "so it never generates");
        }
        check(smallest > 0.0005D, "the smallest cell is " + smallest + " of the "
                + "three axes it is cut on, which is a biome a player would have "
                + "to be told about to ever find");
        System.out.println("  " + Math.round(share * 1000) / 10.0
                + "% of the inland surface, 125 ways, the smallest cell "
                + String.format("%.4f", smallest) + " of the grid");
    }

    // ==================================================================

    private static void theColours() {
        System.out.println("no two the same biome under two names");
        check(OverworldRules.faultsInPaint().isEmpty(),
                "two biomes painted alike: " + OverworldRules.faultsInPaint());

        Set<List<Integer>> seen = new LinkedHashSet<>();
        for (OverworldRules.Land one : OverworldRules.LANDS) {
            check(seen.add(one.paint()), one.name() + " is painted like something "
                    + "already on the roster");
            for (int colour : one.paint()) {
                check(colour >= 0 && colour <= 0xFFFFFF, one.name()
                        + " is painted a colour that is not a colour: " + colour);
            }
        }
        for (OverworldRules.Cave one : OverworldRules.CAVES) {
            check(seen.add(one.paint()), one.name() + " is painted like something "
                    + "already on the roster");
        }

        // The coldest is not warmer than the warmest, and the wettest is not
        // drier than the driest, whatever the colours do.
        for (int warmth = 1; warmth < OverworldRules.WARMTHS.size(); warmth++) {
            check(OverworldRules.warmthOf(warmth)
                            > OverworldRules.warmthOf(warmth - 1),
                    "temperature band " + warmth + " is no warmer than the one "
                            + "below it, so the bands are a comment");
        }
        for (OverworldRules.Land one : OverworldRules.LANDS) {
            check(one.rains() == (one.wet() > 0), one.name() + " rains when it "
                    + "should not, or does not when it should");
            check(one.wetValue() >= 0.0F && one.wetValue() <= 1.0F,
                    one.name() + " has a downfall of " + one.wetValue());
        }
        System.out.println("  " + seen.size() + " biomes, "
                + seen.size() + " different sets of colours");
    }

    // ==================================================================

    /**
     * The decoration, and the one order it is all listed in.
     *
     * <p>The game builds one order out of every biome in the world and
     * refuses to start if two of them disagree about which of two features
     * comes first. Ours are at the end of their step, always in the same
     * order, so no edge can ever point backwards — and that is a property of
     * the roster, which means it can be checked here rather than found out
     * when somebody presses Create World.
     */
    private static void theDecoration() {
        System.out.println("the decoration");
        check(OverworldRules.faultsInExtras().isEmpty(),
                "what we add of our own: " + OverworldRules.faultsInExtras());

        for (OverworldRules.Land one : OverworldRules.LANDS) {
            check(!one.model().isBlank(), one.name() + " decorates like nothing");
            List<String> added = OverworldRules.extras(one);
            check(!added.isEmpty(), one.name() + " adds nothing of ours");
            int last = -1;
            for (String feature : added) {
                int at = OverworldRules.OURS.indexOf(feature);
                check(at > last, one.name() + " lists " + feature + " out of order");
                last = at;
            }
        }
        for (OverworldRules.Cave one : OverworldRules.CAVES) {
            check(!OverworldRules.extras(one).isEmpty(),
                    one.name() + " adds nothing of ours");
        }

        // Every feature of ours goes in a step a biome file has room for, and
        // a boulder does not go in with the grass.
        for (String feature : OverworldRules.OURS) {
            int step = OverworldRules.stepOf(feature);
            check(step >= 0 && step < OverworldRules.STEPS, feature
                    + " goes in step " + step);
            check(feature.startsWith("boulder_")
                            == (step == OverworldRules.LOCAL_MODIFICATIONS),
                    feature + " is in the step for the other kind of thing");
        }

        // A crag decorates like a mountain whatever the rain does.
        //
        // Against the game's own mountains, not against the crag table. The
        // check that stood here compared a crag's model to CRAG_MODELS and a
        // crag's model *is* CRAG_MODELS, so it was the definition read back
        // and it passed with a meadow in the list.
        check(OverworldRules.faultsInModels().isEmpty(),
                "a crag decorates like somewhere that is not a mountain: "
                        + OverworldRules.faultsInModels());
        for (OverworldRules.Land one : OverworldRules.LANDS) {
            if (one.relief() == 0) {
                check(OverworldRules.MOUNTAINS.contains(one.model()),
                        one.name() + " is a crag and decorates like "
                                + one.model() + ", which is not a mountain");
            } else {
                check(one.model().equals(OverworldRules.MODELS
                                .get(one.warmth()).get(one.wet())),
                        one.name() + " does not decorate like its own climate");
            }
        }
        check(OverworldRules.models().size() >= 12, "only "
                + OverworldRules.models().size() + " vanilla biomes are used as "
                + "models, so most of ours look like each other");
        System.out.println("  " + OverworldRules.models().size()
                + " vanilla biomes copied, " + OverworldRules.OURS.size()
                + " features of ours, one order for all of them");
    }

    // ==================================================================

    /**
     * Things built to be caught.
     *
     * <p>Every check above walks a healthy roster, and a check that has only
     * ever been asked about healthy things has never been shown able to say
     * no.
     */
    private static void theControls() {
        System.out.println("the controls");

        // Two claims that overlap.
        WildRules.Region first = new WildRules.Region("control_one", List.of(
                new WildRules.Span(-1.0D, 0.0D), whole(), whole(), whole(),
                OverworldRules.SURFACE, whole()), 0.0D);
        WildRules.Region second = new WildRules.Region("control_two", List.of(
                new WildRules.Span(-0.5D, 0.5D), whole(), whole(), whole(),
                OverworldRules.SURFACE, whole()), 0.0D);
        check(!OverworldRules.faultsInClaims(List.of(first, second)).isEmpty(),
                "two claims that share half an axis are not reported, so the "
                        + "second of them silently loses the ground they share");
        check(OverworldRules.faultsInContainment(List.of(first, second)).isEmpty(),
                "two claims that merely overlap are reported as one containing "
                        + "the other: " + OverworldRules.faultsInContainment(
                                List.of(first, second)));
        check(OverworldRules.overlap(first, second),
                "two boxes that share half an axis are not said to overlap");

        // A claim inside an earlier one: the shape that made a river never
        // generate once in this repository.
        WildRules.Region swallowed = new WildRules.Region("control_inside", List.of(
                new WildRules.Span(-0.4D, -0.2D), whole(), whole(), whole(),
                OverworldRules.SURFACE, whole()), 0.0D);
        check(!OverworldRules.faultsInContainment(List.of(first, swallowed))
                        .isEmpty(),
                "a claim wholly inside one declared before it is not reported, "
                        + "and it wins nothing anywhere, ever");
        // Asked of the containment rule alone. Anything contained overlaps,
        // so asking faultsInClaims here is a control the overlap rule answers
        // and the containment rule could be deleted under.
        check(OverworldRules.faultsInContainment(List.of(swallowed, first))
                        .isEmpty(),
                "the bigger claim declared second is reported as swallowed by "
                        + "the smaller one, which is the rule backwards");
        check(!OverworldRules.faultsInOverlap(List.of(first, second)).isEmpty(),
                "two claims that share half an axis are not reported by the "
                        + "overlap rule on its own");

        // And an ordinary pair, so the check is not simply always saying yes.
        WildRules.Region apart = new WildRules.Region("control_apart", List.of(
                new WildRules.Span(0.5D, 1.0D), whole(), whole(), whole(),
                OverworldRules.SURFACE, whole()), 0.0D);
        check(OverworldRules.faultsInClaims(List.of(first, apart)).isEmpty(),
                "two claims that share nothing are reported as if they did: "
                        + OverworldRules.faultsInClaims(List.of(first, apart)));
        check(!OverworldRules.overlap(first, apart),
                "two boxes that touch at a face are said to overlap, which would "
                        + "make every band in this file overlap its neighbour");

        // A biome with nothing of ours in it.
        check(!OverworldRules.faultsInExtras("control", List.of()).isEmpty(),
                "a biome that adds nothing of ours is not reported, so a vanilla "
                        + "biome under a name of ours would ship");
        // Ours listed out of the one order.
        List<String> backwards = List.of(
                OverworldRules.OURS.get(OverworldRules.OURS.size() - 1),
                OverworldRules.OURS.get(0));
        check(!OverworldRules.faultsInExtras("control", backwards).isEmpty(),
                "a biome listing our features out of the one order is not "
                        + "reported, and two biomes that disagree about which "
                        + "feature comes first is a world that will not start");
        check(OverworldRules.faultsInExtras("control",
                        List.of(OverworldRules.OURS.get(0))).isEmpty(),
                "an ordinary list of ours is reported as faulty");

        // A crag table with somewhere flat in it, built to be caught, and an
        // ordinary one so the check is not simply always complaining.
        check(!OverworldRules.MOUNTAINS.contains("plains"),
                "the plains are listed among the game's mountains, and then a "
                        + "crag may decorate like anything at all");
        check(OverworldRules.MOUNTAINS.contains(OverworldRules.CRAG_MODELS.get(0)),
                "the coldest crag decorates like "
                        + OverworldRules.CRAG_MODELS.get(0)
                        + ", which is not among the game's mountains");

        // A band that does not exist.
        List<String> allowed = new ArrayList<>();
        try {
            new OverworldRules.Land(OverworldRules.WARMTHS.size(), 0, 0);
            allowed.add("a biome in a temperature band that does not exist");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new OverworldRules.Land(0, 0, OverworldRules.RELIEFS.size());
            allowed.add("a biome in an erosion band that does not exist");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new OverworldRules.Cave(0, OverworldRules.CAVE_WETS);
            allowed.add("a cave in a damp band that does not exist");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(allowed.isEmpty(), "the bands allow what they say they forbid: "
                + allowed);

        // Bands with a gap in them, and bands that overlap, both caught.
        List<WildRules.Span> gapped = List.of(new WildRules.Span(-1.0D, 0.0D),
                new WildRules.Span(0.2D, 1.0D));
        check(!OverworldRules.faultsInAxis("control", gapped, true).isEmpty(),
                "a gap between two bands is not reported, so climate in the gap "
                        + "goes to whatever box happens to be nearest");
        List<WildRules.Span> lapped = List.of(new WildRules.Span(-1.0D, 0.2D),
                new WildRules.Span(0.0D, 1.0D));
        check(!OverworldRules.faultsInAxis("control", lapped, true).isEmpty(),
                "two bands that overlap are not reported");
        List<WildRules.Span> tidy = List.of(new WildRules.Span(-1.0D, 0.0D),
                new WildRules.Span(0.0D, 1.0D));
        check(OverworldRules.faultsInAxis("control", tidy, true).isEmpty(),
                "two bands that meet exactly are reported as faulty: "
                        + OverworldRules.faultsInAxis("control", tidy, true));
        // And a set of bands that leaves room, which weirdness is allowed to
        // do and the other three are not.
        check(OverworldRules.faultsInAxis("control", gapped, false).isEmpty(),
                "bands that are allowed to leave room are reported for leaving "
                        + "room, which is what every band of weirdness does");

        System.out.println("  the controls: an overlap caught, a claim inside an "
                + "earlier one caught, a biome with nothing of ours caught, our "
                + "features out of order caught, and a band that does not exist "
                + "refused");
    }

    // ==================================================================

    private static WildRules.Span whole() {
        return new WildRules.Span(-1.0D, 1.0D);
    }

    private static double middle(WildRules.Span span) {
        return (span.low() + span.high()) / 2.0D;
    }

    private static double pick(Random random) {
        return -1.0D + 2.0D * random.nextDouble();
    }

    private static double[] within(WildRules.Region claim, Random random) {
        double[] made = new double[WildRules.AXES];
        for (int axis = 0; axis < WildRules.AXES; axis++) {
            WildRules.Span span = claim.spans().get(axis);
            made[axis] = span.low()
                    + (span.high() - span.low()) * random.nextDouble();
        }
        return made;
    }
}
