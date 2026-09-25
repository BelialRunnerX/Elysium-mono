package com.elysium.industry.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The two brick machines Age II starts with, before it has a grid.
 *
 * <h2>What was missing</h2>
 *
 * Age II's whole roster wanted electricity. The first steel came out of an
 * arc furnace, which wants five hundred and twelve FE on a medium wire, which
 * wants a turbine and a transformer and a run of gold. None of that is
 * unreachable — the recipes are all vanilla at the bottom — but it means the
 * age opens with a power station rather than with a furnace, and the age is
 * called the Reconstruction.
 *
 * <p>So: two multiblocks made of brick, both of which work on fire.
 * A <b>coke oven</b> turns coal into coke and gives back the creosote that
 * Age I's engines burn. A <b>blast furnace</b> turns iron and coke into
 * steel. Neither touches a wire. Between them they are the first hour of Age
 * II, and what they make is what the grid is then built out of.
 *
 * <h2>Why they are multiblocks</h2>
 *
 * Because they are the only things in the pack that should be. A multiblock
 * is a block whose cost is <em>time and space</em> rather than materials, and
 * a brick kiln is exactly that: nine by nine bricks is not expensive, it is
 * big, and you have to decide where to put it. Everything else in these two
 * ages is a machine that fits on a shelf.
 */
public final class FurnaceRules {

    private FurnaceRules() {
    }

    /** What the coke oven is built of. */
    public static final String COKE_BRICK = "coke_brick";

    /** What the blast furnace is built of. */
    public static final String BLAST_BRICK = "blast_brick";

    /** What a coke oven makes, and what a blast furnace burns. */
    public static final String COKE = "coke";

    /**
     * The coke oven: three by three by three of coke brick, hollow.
     *
     * <p>Hollow on purpose. A solid cube of twenty-seven bricks is a shape
     * you cannot see inside and cannot tell from a decorative pillar; a shell
     * around one empty block is a kiln, and it is the shape every furnace in
     * every game has had since the first one.
     */
    public static final ShapeRules.Shape COKE_OVEN = new ShapeRules.Shape(
            "coke_oven",
            List.of("BBB\nBBB\nBBB",
                    "BBB\nB B\nBXB",
                    "BBB\nBBB\nBBB"),
            Map.of('B', COKE_BRICK, ShapeRules.CONTROLLER, "coke_oven"));

    /**
     * The blast furnace: the same shape in a different brick, and taller.
     *
     * <p>Four layers rather than three, because two machines that are the
     * same box in two colours are two machines a player builds the wrong one
     * of. The extra layer is also the honest difference: a blast furnace is a
     * stack, and what makes it work is the height of the column above the
     * fire.
     */
    public static final ShapeRules.Shape BLAST_FURNACE = new ShapeRules.Shape(
            "blast_furnace",
            List.of("BBB\nBBB\nBBB",
                    "BBB\nB B\nBXB",
                    "BBB\nB B\nBBB",
                    "BBB\nBBB\nBBB"),
            Map.of('B', BLAST_BRICK, ShapeRules.CONTROLLER, "blast_furnace"));

    /** Both of them, walked rather than listed. */
    public static final List<ShapeRules.Shape> SHAPES =
            List.of(COKE_OVEN, BLAST_FURNACE);

    /** A shape by name, or null. */
    public static ShapeRules.Shape shape(String id) {
        for (ShapeRules.Shape one : SHAPES) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Every block these add. */
    public static List<String> blocks() {
        Set<String> made = new LinkedHashSet<>();
        for (ShapeRules.Shape shape : SHAPES) {
            made.addAll(ShapeRules.billOf(shape).keySet());
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // What they do
    // ==================================================================

    /**
     * One job one of these does.
     *
     * @param shape  which machine does it
     * @param input  what goes in
     * @param count  how many of it
     * @param output what comes out
     * @param makes  how many of that
     * @param ticks  how long it takes
     */
    public record Firing(String shape, String input, int count, String output,
                         int makes, int ticks) {

        public Firing {
            Objects.requireNonNull(shape, "a firing in nothing");
            Objects.requireNonNull(input, "a firing of nothing");
            Objects.requireNonNull(output, "a firing making nothing");
            if (count <= 0 || makes <= 0) {
                throw new IllegalArgumentException(input + " -> " + output
                        + " takes " + count + " and makes " + makes);
            }
            if (ticks <= 0) {
                throw new IllegalArgumentException(input + " -> " + output
                        + " takes no time, which is not a furnace");
            }
        }
    }

    /**
     * How long a coke oven takes over one thing.
     *
     * <p>Slow, and that is the whole design of it. A coke oven is not a
     * faster furnace — it is a furnace that needs no fuel, and what it costs
     * instead is a quarter of an hour of not watching it. A player builds
     * three.
     */
    public static final int COKING_TICKS = 900;

    /** How long a blast furnace takes over one thing. */
    public static final int BLASTING_TICKS = 600;

    /**
     * Everything these two make.
     *
     * <p>The oven's second job is the one that makes it worth building
     * before anything else: charcoal out of wood, in a kiln that burns
     * nothing. A furnace making charcoal spends a fuel to make a fuel; this
     * spends a quarter of an hour instead, which is the trade the whole
     * block is about.
     */
    public static final List<Firing> FIRINGS = List.of(
            new Firing("coke_oven", "minecraft:coal", 1,
                    "elysiumindustry:coke", 1, COKING_TICKS),
            new Firing("coke_oven", "#minecraft:logs", 1,
                    "minecraft:charcoal", 1, COKING_TICKS * 2 / 3),
            new Firing("blast_furnace", "minecraft:iron_ingot", 1,
                    "elysiumindustry:steel_ingot", 1, BLASTING_TICKS));

    /**
     * How much coke a blast furnace burns for one job.
     *
     * <p>The reason the oven comes first: a blast furnace with no coke in it
     * is a stack of bricks, and coke is the only thing that makes one work.
     */
    public static final int COKE_PER_BLAST = 1;

    /**
     * How long one coke keeps a furnace going, in ticks.
     *
     * <p>Above coal, because a lump of it cost a lump of coal and a quarter
     * of an hour. Below what Age I's refinery gets out of a coal block, so
     * that this is a better lump rather than a better refinery.
     */
    public static final int COKE_BURN = 3_200;

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the brick machines. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        for (ShapeRules.Shape shape : SHAPES) {
            bad.addAll(ShapeRules.faultsInShape(shape));
        }
        bad.addAll(ShapeRules.faultsInSet(SHAPES));
        bad.addAll(faultsInFirings(FIRINGS, SHAPES));
        return List.copyOf(bad);
    }

    /**
     * Whether every job belongs to a machine and every machine has jobs.
     *
     * <p>Handed both rosters so a control can pass a job for a machine that
     * does not exist, which over these two this can only ever answer no to.
     */
    public static List<String> faultsInFirings(List<Firing> firings,
                                               List<ShapeRules.Shape> shapes) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInEachFiring(firings, shapes));
        bad.addAll(faultsInCoverage(firings, shapes));
        return bad;
    }

    /**
     * The jobs, one at a time.
     *
     * <p>Kept apart from {@link #faultsInCoverage} for the reason this
     * project keeps finding: a method holding both answers every control with
     * whichever fires first. Hand it one job for a machine that does not
     * exist to prove it notices, and it reports instead that two machines
     * have nothing to do.
     */
    public static List<String> faultsInEachFiring(List<Firing> firings,
                                                  List<ShapeRules.Shape> shapes) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (ShapeRules.Shape shape : shapes) {
            named.add(shape.id());
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Firing one : firings) {
            if (!named.contains(one.shape())) {
                bad.add(one.input() + " is fired in a " + one.shape()
                        + ", which is not a machine this module has");
            }
            if (!seen.add(one.shape() + "/" + one.input())) {
                bad.add(one.shape() + " has two recipes for " + one.input()
                        + ", so one of them is a file the other overwrites");
            }
            if (one.input().equals(one.output())) {
                bad.add(one.input() + " is fired into itself");
            }
        }
        return bad;
    }

    /** Whether every machine has something to do. */
    public static List<String> faultsInCoverage(List<Firing> firings,
                                                List<ShapeRules.Shape> shapes) {
        List<String> bad = new ArrayList<>();
        Set<String> busy = new LinkedHashSet<>();
        for (Firing one : firings) {
            busy.add(one.shape());
        }
        for (ShapeRules.Shape shape : shapes) {
            if (!busy.contains(shape.id())) {
                bad.add(shape.id() + " has nothing to fire, so it is twenty-six "
                        + "bricks a player puts down once");
            }
        }
        return bad;
    }

    /**
     * Whether a lump of coke is worth what it cost.
     *
     * <p>Handed the numbers, because reading them here would make this a
     * rule that re-solves itself the moment one of them is broken.
     *
     * @param cokeBurn   how long a lump of coke burns
     * @param coalBurn   how long the coal it was made from burns
     * @param bestBurn   the longest-burning thing the pack already makes
     */
    public static List<String> faultsInCoke(int cokeBurn, int coalBurn,
                                            int bestBurn) {
        List<String> bad = new ArrayList<>();
        if (cokeBurn <= coalBurn) {
            bad.add("a lump of coke burns for " + cokeBurn + " ticks and the "
                    + "lump of coal it was made from burns for " + coalBurn
                    + ", so a quarter of an hour in an oven made it worse");
        }
        if (cokeBurn >= bestBurn) {
            bad.add("a lump of coke burns for " + cokeBurn + " ticks and the "
                    + "best thing the pack refines burns for " + bestBurn
                    + ", so an oven that needs no power beats a refinery that "
                    + "needs a machine hall");
        }
        return bad;
    }
}
