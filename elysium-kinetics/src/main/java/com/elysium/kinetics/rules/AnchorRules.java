package com.elysium.kinetics.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Anchors: the machines whose product is that the world keeps running.
 *
 * <h2>What an anchor is for</h2>
 *
 * Every machine in this age stops when nobody is looking at it. A farm
 * twenty chunks from where you are standing is a farm that is not growing,
 * and the answer people reach for is a chunk loader — which in most mods is a
 * block you place and forget, and which therefore costs nothing and is the
 * single most common way a world turns into a slideshow.
 *
 * <p>So an anchor here is a <b>load</b>. It keeps chunks awake while a line
 * turns it and stops the moment the line does, and what it takes out of that
 * line is proportional to how much world it is keeping awake. A player who
 * wants a 5×5 anchor has to build the machine hall that pays for one.
 *
 * <h2>The two numbers, and why they are both linear</h2>
 *
 * Both the torque an anchor draws and the speed it needs grow in proportion
 * to the chunks it holds. That is a decision, and it is the one that stops
 * the block being a puzzle with a right answer: at any radius the cost per
 * chunk is identical, so a player picks the radius their farm needs rather
 * than the radius the arithmetic rewards. A curve either way would make one
 * radius the only sensible one and the other two decoration.
 *
 * <h2>And gearing cannot cheat it</h2>
 *
 * The obvious move on a machine with a speed floor is to gear up into it: put
 * the anchor on a sub-line turning eight times as fast and it meets its floor
 * while the hall turns slowly. That does not work, and the reason is worth
 * stating because it is the whole model in one sentence — <b>gearing moves
 * power about and does not make any</b>. The reflected draw grows by exactly
 * the ratio the speed does, so the power the anchor takes off the root is the
 * same whatever gearing reaches it, less what the teeth cost. The harness
 * sweeps that rather than asserting it.
 */
public final class AnchorRules {

    private AnchorRules() {
    }

    // ==================================================================
    // What a radius costs
    // ==================================================================

    /** The most chunks any anchor in this age keeps awake, as a radius. */
    public static final int WIDEST = 2;

    /** How many chunks across a square of this radius is. */
    public static int acrossAt(int radius) {
        return 2 * radius + 1;
    }

    /** How many chunks a square of this radius is. */
    public static int chunksAt(int radius) {
        return acrossAt(radius) * acrossAt(radius);
    }

    /** What one chunk of width costs in torque. */
    public static final float TORQUE_PER_ROW = 6.0F;

    /** What one chunk of width costs in speed. */
    public static final float RPM_PER_ROW = 48.0F;

    /**
     * What an anchor of this radius takes out of a line.
     *
     * <p>Both numbers grow with the width across rather than with the area,
     * and that is the whole of why the cost per chunk comes out the same at
     * every radius: power is torque times speed, and width times width is
     * area. A wider anchor turns faster and pulls harder, each in proportion
     * to its width, and what it costs its owner is its area.
     */
    public static KineticRules.Load loadAt(int radius) {
        int across = acrossAt(radius);
        return new KineticRules.Load(TORQUE_PER_ROW * across,
                RPM_PER_ROW * across);
    }

    /**
     * The mechanical power an anchor of this radius takes, in torque-rpm.
     *
     * <p>At its floor, which is the cheapest it is ever run at — a line
     * turning faster than an anchor needs does not make the anchor cheaper,
     * it makes the rest of the hall faster.
     */
    public static float powerAt(int radius) {
        KineticRules.Load load = loadAt(radius);
        return load.draw() * load.leastRpm();
    }

    /** What one chunk costs in power, at this radius. */
    public static float powerPerChunk(int radius) {
        return powerAt(radius) / chunksAt(radius);
    }

    // ==================================================================
    // The two anchors
    // ==================================================================

    /**
     * One anchor.
     *
     * @param id     what the block is called
     * @param widest the largest radius it will hold
     * @param made   what it is made of, which is what its shaft must survive
     */
    public record Anchor(String id, int widest, KineticRules.Shaft made) {

        public Anchor {
            Objects.requireNonNull(id, "an anchor with no name");
            Objects.requireNonNull(made, "an anchor made of nothing");
            if (widest < 0 || widest > WIDEST) {
                throw new IllegalArgumentException(id + " holds a radius of "
                        + widest + " and the widest this age has is " + WIDEST);
            }
        }

        /** Every radius a player can set it to. */
        public List<Integer> radii() {
            List<Integer> made = new ArrayList<>();
            for (int at = 0; at <= widest; at++) {
                made.add(at);
            }
            return List.copyOf(made);
        }
    }

    /**
     * Two of them: one that holds the ground it stands on and one a player
     * chooses the width of.
     *
     * <p>The first is a block you put next to a farm. The second is a block
     * you build a machine hall for, and the difference between them is the
     * whole reason there are two.
     */
    public static final List<Anchor> ANCHORS = List.of(
            new Anchor("anchor", 0, KineticRules.Shaft.BRASS),
            new Anchor("wide_anchor", WIDEST, KineticRules.Shaft.STEEL));

    /** An anchor by name, or null. */
    public static Anchor anchor(String id) {
        for (Anchor one : ANCHORS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Every block this adds. */
    public static List<String> blocks() {
        List<String> made = new ArrayList<>();
        for (Anchor one : ANCHORS) {
            made.add(one.id());
        }
        return List.copyOf(made);
    }

    /**
     * How long an anchor keeps its chunks awake after the line stops.
     *
     * <p>Not zero. A hall driven by a windmill drops below the floor for a
     * second whenever the wind turns, and an anchor that let go each time
     * would unload the farm it is holding several times a minute — which is
     * worse than not holding it, because a chunk that loads and unloads over
     * and over is a chunk doing its most expensive work forever.
     */
    public static final int GRACE_TICKS = 200;

    /** What a comparator reads off an anchor: zero to fifteen. */
    public static int comparator(boolean holding, int radius) {
        if (!holding) {
            return 0;
        }
        return Math.max(1, Math.min(15, chunksAt(radius)));
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the anchors. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInAnchors(ANCHORS));
        bad.addAll(faultsInCost(everyWidth()));
        bad.addAll(faultsInReach(ANCHORS));
        return List.copyOf(bad);
    }

    /** The roster: named once, a ladder, and each on a shaft that survives it. */
    public static List<String> faultsInAnchors(List<Anchor> anchors) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int at = 0; at < anchors.size(); at++) {
            Anchor one = anchors.get(at);
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two anchors");
            }
            if (one.radii().size() != one.widest() + 1) {
                bad.add(one.id() + " offers " + one.radii().size()
                        + " radius(es) and holds up to " + one.widest());
            }
            if (KineticRules.shears(one.made(),
                    loadAt(one.widest()).leastRpm())) {
                bad.add(one.id() + " is made of " + one.made() + " and needs "
                        + loadAt(one.widest()).leastRpm() + " rpm, which "
                        + "shears it; it is a block that breaks itself the "
                        + "first time it works");
            }
            if (at == 0) {
                continue;
            }
            Anchor under = anchors.get(at - 1);
            if (one.widest() <= under.widest()) {
                bad.add(one.id() + " holds no more than " + under.id()
                        + ", so nobody would ever build it");
            }
        }
        if (anchors.size() < 2) {
            bad.add("there are " + anchors.size() + " anchor(s), which is not "
                    + "a choice");
        }
        return bad;
    }

    /** Every width this age offers, priced. */
    public static List<KineticRules.Load> everyWidth() {
        List<KineticRules.Load> made = new ArrayList<>();
        for (int radius = 0; radius <= WIDEST; radius++) {
            made.add(loadAt(radius));
        }
        return List.copyOf(made);
    }

    /**
     * Whether a chunk costs the same however many of them are held.
     *
     * <p>Handed the prices rather than reading them, and that is the whole
     * point of the signature: price a radius off the rules and then break the
     * pricing, and the rule re-reads the broken price, finds it agrees with
     * itself, and reports nothing. A control passes a list of widths priced
     * by area, which is the mistake this exists to catch.
     *
     * @param byRadius what an anchor draws at each radius, narrowest first
     */
    public static List<String> faultsInCost(List<KineticRules.Load> byRadius) {
        List<String> bad = new ArrayList<>();
        if (byRadius.size() < 2) {
            bad.add("there is " + byRadius.size() + " width(s) to choose from, "
                    + "so there is nothing to choose and the panel is a screen "
                    + "with one button");
            return bad;
        }
        float first = perChunk(byRadius.get(0), 0);
        if (!(first > 0.0F)) {
            bad.add("a chunk costs " + first + " to keep awake, so an anchor "
                    + "is free and a world full of them is somebody else's "
                    + "problem");
        }
        for (int radius = 1; radius < byRadius.size(); radius++) {
            float here = perChunk(byRadius.get(radius), radius);
            if (Math.abs(here - first) > first * 1.0E-3F) {
                bad.add("a chunk at radius " + radius + " costs " + here
                        + " and a chunk at radius 0 costs " + first
                        + ", so one width is the only one worth building and "
                        + "the others are decoration");
            }
        }
        return bad;
    }

    /** What one chunk costs, given what the whole square costs. */
    public static float perChunk(KineticRules.Load load, int radius) {
        return load.draw() * load.leastRpm() / chunksAt(radius);
    }

    /**
     * Whether every anchor can actually be driven, and the widest is a build.
     *
     * <p>Both halves matter. An anchor nothing in the pack can turn is a
     * block only the creative menu has; an anchor the first water wheel turns
     * is the place-and-forget chunk loader this one exists not to be.
     */
    public static List<String> faultsInReach(List<Anchor> anchors) {
        List<String> bad = new ArrayList<>();
        for (Anchor one : anchors) {
            KineticRules.Load wants = loadAt(one.widest());
            if (wants.leastRpm() > KineticRules.MAX_RPM) {
                bad.add(one.id() + " needs " + wants.leastRpm()
                        + " rpm and nothing in any age turns faster than "
                        + KineticRules.MAX_RPM + ", so it is a block nobody "
                        + "can ever switch on");
            }
        }
        // The first one is meant to be within Age I on its own.
        Anchor first = anchors.get(0);
        if (!drivenBySomething(loadAt(first.widest()))) {
            bad.add(first.id() + " cannot be driven by anything this age has, "
                    + "so the cheap anchor is not a cheap anchor");
        }
        // And the widest is not.
        Anchor last = anchors.get(anchors.size() - 1);
        if (drivenBySomething(loadAt(last.widest()))) {
            bad.add(last.id() + " is turned by one of Age I's own sources with "
                    + "no gearing at all, so the biggest anchor in the pack is "
                    + "a block you place and forget");
        }
        return bad;
    }

    /** Whether one of this age's own sources drives this load ungeared. */
    public static boolean drivenBySomething(KineticRules.Load load) {
        for (KineticRules.Source source : KineticRules.EVERY_SOURCE) {
            KineticRules.Reading settled = KineticRules.settle(
                    List.of(source), List.of(load), KineticRules.Shaft.ASCENDANT);
            if (settled.running() && load.working(settled.rpm())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What an anchor costs the root of a line it is geared onto.
     *
     * <p>The number the harness sweeps: gearing moves power about and does
     * not make any, so this may never come out below {@link #powerAt} for the
     * same radius.
     *
     * @param radius how much world it is holding
     * @param ratio  how much faster it turns than the root
     * @param stages how many gear stages away it is
     */
    public static float costAtRoot(int radius, float ratio, int stages) {
        KineticRules.Load reflected = loadAt(radius).through(ratio, stages);
        return reflected.draw() * reflected.leastRpm();
    }
}
