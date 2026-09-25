package com.elysium.atomics.rules;

import com.elysium.industry.rules.ShapeRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reactor you build at the size you want.
 *
 * <h2>Why a second reactor at all</h2>
 *
 * {@link AtomicRules#REACTOR} is a block. It is a triumph the first time and
 * a wiring problem the second, which is the right shape for the first one a
 * player builds — but it is the same reactor forever, and the two jars this
 * answers are both about the other thing: a reactor whose size is a decision.
 *
 * <p>So: a hollow box of casing, between three and seven on a side, with fuel
 * rods standing in it. What it makes is what its rods make. What it costs is
 * the casing, the floor space, and — the part that matters — the cooling,
 * because heat goes up with the rods and the box only sheds heat through its
 * surface.
 *
 * <h2>The one thing that makes it a decision rather than a ladder</h2>
 *
 * <b>A bigger hall is worse per rod.</b> A box of side {@code n} holds rods
 * in proportion to its volume and sheds heat in proportion to its surface, so
 * the bigger it is the hotter each rod has to run — and above
 * {@link AtomicRules#SAFE} it is wearing itself out. The sums are in
 * {@link #settlesAt}, and the harness sweeps every size at every throttle
 * rather than taking the sentence on trust.
 *
 * <p>That is deliberately the opposite of how these mods usually work, where
 * bigger is strictly better and the only question is how much iron you have.
 * Here the question is how much cooling you are willing to plumb, which is a
 * question with a wrong answer.
 */
public final class HallRules {

    private HallRules() {
    }

    /** What the box is built of. */
    public static final String CASING = "reactor_casing";

    /** What stands inside it and makes the heat. */
    public static final String ROD = "fuel_rod";

    /** What a player prods. */
    public static final String CONTROLLER = "reactor_port";

    /** The smallest box that is a box: a shell with one block inside it. */
    public static final int SMALLEST = 3;

    /** The largest this age will hold together. */
    public static final int LARGEST = 7;

    /** How many blocks of casing a hollow box of this side is. */
    public static int casingFor(int side) {
        return side * side * side - inside(side);
    }

    /** How many blocks of room a hollow box of this side has inside it. */
    public static int inside(int side) {
        int hollow = side - 2;
        return hollow <= 0 ? 0 : hollow * hollow * hollow;
    }

    /** How many rods stand in a box of this side, at most. */
    public static int rodsIn(int side) {
        return inside(side);
    }

    /** How many blocks of the box's outside there are, which is what sheds. */
    public static int surfaceOf(int side) {
        return 6 * side * side;
    }

    // ==================================================================
    // What a hall makes, and what it costs to keep it cool
    // ==================================================================

    /**
     * What one rod makes at full throttle.
     *
     * <p>A quarter of the single-block reactor, so the smallest hall a player
     * can build is plainly worse than the reactor they already have and a
     * four-cube already doubles it. A hall is floor space, casing and
     * plumbing traded for output; it is not a free upgrade, and the one-rod
     * box exists to be the thing you build first and then outgrow.
     */
    public static final float PER_ROD = AtomicRules.REACTOR.mostOutput() / 4.0F;

    /** What a hall of this side makes at this throttle. */
    public static float outputAt(int side, float throttle) {
        return PER_ROD * rodsIn(side)
                * Math.max(0.0F, Math.min(1.0F, throttle));
    }

    /**
     * How many coolant channels a hall of this side will take.
     *
     * <p>A channel is a block of the wall, and the wall is a surface, so the
     * most a box takes goes as the square of its side. That is the whole
     * design in one line: <b>heat goes as the cube and cooling goes as the
     * square</b>, so a bigger hall makes more altogether and less per rod,
     * and the throttle stops being a slider nobody touches.
     */
    public static int mostChannelsOn(int side) {
        return side * side;
    }

    /**
     * How much of its excess heat a hall sheds each tick.
     *
     * <p>The same bare loss a single core has, plus what is plumbed into it.
     * Nothing here is divided by the size: the point is precisely that
     * cooling does <em>not</em> keep up with the rods.
     *
     * @param side     how big the box is
     * @param channels coolant channels plumbed into its wall
     */
    public static float coolingOf(int side, int channels) {
        int used = Math.max(0, Math.min(mostChannelsOn(side), channels));
        return AtomicRules.BARE_LOSS + AtomicRules.COOLING_PER_CHANNEL * used;
    }

    /** Where a hall of this side settles at this throttle and this plumbing. */
    public static float settlesAt(int side, float throttle, int channels) {
        float heat = outputAt(side, throttle) * AtomicRules.HEAT_PER_FE;
        return AtomicRules.settlesAt(heat, coolingOf(side, channels));
    }

    /**
     * The most throttle a hall of this side may be run at and stay safe.
     *
     * <p>Solved rather than searched, because a player wants the number and
     * not a slider they discover by melting something. Newton settles at
     * {@code ambient + heat / cooling}, so the heat a hall may make is
     * {@code (safe - ambient) * cooling}, and the throttle that makes it is
     * that over what a hall at full throttle makes in heat.
     */
    public static float safeThrottle(int side, int channels) {
        return safeThrottleWith(side, channels, PER_ROD);
    }

    /** What a hall of this side safely makes with this much plumbing. */
    public static float safeOutput(int side, int channels) {
        return outputAt(side, safeThrottle(side, channels));
    }

    // ==================================================================
    // The shape
    // ==================================================================

    /**
     * The box of a given side, as a shape.
     *
     * <p>Built rather than written out, because seven of them written out is
     * seven things to keep in step and the seventh is three hundred and
     * forty-three characters of B.
     */
    public static ShapeRules.Shape boxOf(int side) {
        List<String> layers = new ArrayList<>();
        for (int layer = 0; layer < side; layer++) {
            StringBuilder here = new StringBuilder();
            for (int row = 0; row < side; row++) {
                if (row > 0) {
                    here.append('\n');
                }
                for (int column = 0; column < side; column++) {
                    boolean wall = layer == 0 || layer == side - 1
                            || row == 0 || row == side - 1
                            || column == 0 || column == side - 1;
                    boolean port = layer == 1 && row == side - 1
                            && column == side / 2;
                    here.append(port ? ShapeRules.CONTROLLER : wall ? 'C' : 'R');
                }
            }
            layers.add(here.toString());
        }
        return new ShapeRules.Shape("reactor_hall_" + side, layers,
                Map.of('C', CASING, 'R', ROD,
                        ShapeRules.CONTROLLER, CONTROLLER));
    }

    /** Every size this age will hold together. */
    public static List<ShapeRules.Shape> SHAPES = boxes();

    private static List<ShapeRules.Shape> boxes() {
        List<ShapeRules.Shape> made = new ArrayList<>();
        for (int side = SMALLEST; side <= LARGEST; side++) {
            made.add(boxOf(side));
        }
        return List.copyOf(made);
    }

    /** What a box of this side is built out of. */
    public static Map<String, Integer> billFor(int side) {
        Map<String, Integer> made = new LinkedHashMap<>(
                ShapeRules.billOf(boxOf(side)));
        return Map.copyOf(made);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the halls. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        for (ShapeRules.Shape shape : SHAPES) {
            bad.addAll(ShapeRules.faultsInShape(shape));
        }
        bad.addAll(ShapeRules.faultsInSet(SHAPES));
        bad.addAll(faultsInRange(SMALLEST, LARGEST));
        bad.addAll(faultsInBoxes(SMALLEST, LARGEST));
        bad.addAll(faultsInLadder(ladderOf(SMALLEST, LARGEST)));
        bad.addAll(faultsInPlumbing(SMALLEST, LARGEST, PER_ROD));
        return List.copyOf(bad);
    }

    /**
     * Whether the range of sizes is a range at all.
     *
     * <p>Split from {@link #faultsInBoxes} for the reason this project keeps
     * finding: a method holding both answers every control with whichever
     * fires first. Hand one a smallest of two to prove it notices a box with
     * nothing inside it, and it reports instead that the box holds no rods.
     */
    public static List<String> faultsInRange(int smallest, int largest) {
        List<String> bad = new ArrayList<>();
        if (smallest < 3) {
            bad.add("the smallest hall is " + smallest + " on a side, and a box "
                    + "smaller than three has nothing inside it");
        }
        if (largest <= smallest) {
            bad.add("the largest hall is " + largest + " on a side and the "
                    + "smallest is " + smallest + ", so there is nothing to "
                    + "choose");
        }
        return bad;
    }

    /** Whether each size in the range is a box that holds something. */
    public static List<String> faultsInBoxes(int smallest, int largest) {
        List<String> bad = new ArrayList<>();
        for (int side = smallest; side <= largest; side++) {
            if (rodsIn(side) <= 0) {
                bad.add("a hall " + side + " on a side holds no rods, so it is "
                        + "a box a player fills with casing");
            }
            if (casingFor(side) <= 0) {
                bad.add("a hall " + side + " on a side is no casing at all");
            }
            if (casingFor(side) + inside(side) != side * side * side) {
                bad.add("a hall " + side + " on a side does not add up: "
                        + casingFor(side) + " of casing and " + inside(side)
                        + " inside is not " + (side * side * side));
            }
        }
        return bad;
    }

    /** What every size safely makes, altogether and per rod. */
    public static List<float[]> ladderOf(int smallest, int largest) {
        List<float[]> made = new ArrayList<>();
        for (int side = smallest; side <= largest; side++) {
            float whole = safeOutput(side, mostChannelsOn(side));
            made.add(new float[] {whole, whole / Math.max(1, rodsIn(side))});
        }
        return List.copyOf(made);
    }

    /**
     * Whether the ladder of sizes is a trade rather than an upgrade.
     *
     * <p><b>Handed the numbers</b> rather than working them out, and that is
     * the whole point of the signature. Price a hall off these rules and then
     * break the pricing, and a rule that re-read it would find the broken
     * numbers agree with themselves and report nothing. A control passes a
     * ladder that rises per rod, which is the mistake this exists to catch.
     *
     * @param ladder each size's safe output, whole and per rod, smallest first
     */
    public static List<String> faultsInLadder(List<float[]> ladder) {
        List<String> bad = new ArrayList<>();
        if (ladder.size() < 2) {
            bad.add("there is " + ladder.size() + " size(s) of hall, which is "
                    + "not a ladder");
            return bad;
        }
        for (int at = 1; at < ladder.size(); at++) {
            float[] here = ladder.get(at);
            float[] under = ladder.get(at - 1);
            if (here[1] > under[1] + 1.0E-4F) {
                bad.add("a hall safely makes " + here[1] + " a rod and the one "
                        + "below it makes " + under[1] + ", so building bigger "
                        + "is free and the size of a reactor is not a decision");
            }
            if (here[0] <= under[0]) {
                bad.add("a hall safely makes " + here[0] + " and the one below "
                        + "it makes " + under[0] + ", so it is a rung of the "
                        + "ladder nobody would ever build");
            }
        }
        return bad;
    }

    /**
     * Whether the plumbing is a decision a player has to make.
     *
     * <p>Handed what a rod makes, for the same reason: a rule that read
     * {@link #PER_ROD} would re-solve against a broken one and agree with
     * itself.
     *
     * @param perRod what one rod makes at full throttle
     */
    public static List<String> faultsInPlumbing(int smallest, int largest,
                                                float perRod) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInUnplumbed(smallest, largest, perRod));
        bad.addAll(faultsInThrottle(largest, perRod));
        return bad;
    }

    /** Whether a hall with nothing plumbed into it has to be throttled. */
    public static List<String> faultsInUnplumbed(int smallest, int largest,
                                                 float perRod) {
        List<String> bad = new ArrayList<>();
        for (int side = smallest; side <= largest; side++) {
            if (safeThrottleWith(side, 0, perRod) >= 1.0F) {
                bad.add("a hall " + side + " on a side is safe at full "
                        + "throttle with no cooling at all, so a reactor is a "
                        + "generator you can simply place");
            }
        }
        return bad;
    }

    /** Whether the biggest hall still has to be throttled when fully plumbed. */
    public static List<String> faultsInThrottle(int largest, float perRod) {
        List<String> bad = new ArrayList<>();
        if (safeThrottleWith(largest, mostChannelsOn(largest), perRod) >= 1.0F) {
            bad.add("the largest hall is safe at full throttle with every "
                    + "channel plumbed, so the throttle is a slider nobody "
                    + "has to touch and the reactor has one control fewer "
                    + "than it looks like it has");
        }
        return bad;
    }

    /** The safe throttle for a hall whose rods make this much. */
    public static float safeThrottleWith(int side, int channels, float perRod) {
        float room = (AtomicRules.SAFE - AtomicRules.AMBIENT)
                * coolingOf(side, channels);
        float flatOut = perRod * rodsIn(side) * AtomicRules.HEAT_PER_FE;
        if (flatOut <= 0.0F) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, room / flatOut));
    }
}
