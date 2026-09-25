package com.elysium.kinetics.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the machines do, and the one thing none of it may add up to.
 *
 * <h2>Why this is a rules file at all</h2>
 *
 * It was a Python dictionary in the generator, which is to say it was a list
 * of numbers no harness had ever executed. That was survivable while there
 * were sixteen of them and all sixteen were about stone. It stopped being
 * survivable the moment this module started joining onto another one's
 * crops: a recipe roster spanning two modules is a graph, and a graph is the
 * kind of thing that is wrong in ways nobody sees by reading a row of it.
 *
 * <h2>The one thing</h2>
 *
 * <b>No loop may come back with more than it started with.</b> Mill a thing
 * into another thing, saw that into a third, press the third back into the
 * first, and if the counts multiply out above one then a player with two
 * machines and a hopper has infinite material. That is the same bug as a line
 * that gains torque and as an engine that makes energy, arrived at for the
 * third time from a third direction, and it is the one this module has now
 * been bitten by in every form it takes.
 *
 * <p>It is checked as a shortest-path problem. Give every recipe an edge from
 * its input to its output weighted {@code -log(count)}, and a loop that
 * multiplies is a cycle whose weights sum to less than zero, which
 * Bellman-Ford finds in one pass and no amount of reading finds at all.
 *
 * <h2>And the game's own recipes are in the graph</h2>
 *
 * A rule that only looked at these would be a rule that cannot see the loop
 * that matters, because the way back is almost always a crafting recipe the
 * game already ships: we mill a sandstone into sand and the game presses four
 * sand back into a sandstone. So {@link #THE_GAMES_ROUTES} names every
 * recipe the game has that turns something these make back into something
 * these take, and {@code validate.py} reads the game's own jar to check both
 * that each one is really what the game does and — the half that matters —
 * that there is no such recipe missing from it.
 */
public final class ProcessRules {

    private ProcessRules() {
    }

    /** Where a recipe is read. One of the machines, by the name of its folder. */
    public static final List<String> MACHINES =
            List.of("milling", "pressing", "sawing", "mixing");

    /**
     * One job a machine does.
     *
     * @param machine which machine reads it
     * @param name    what the recipe file is called
     * @param input   what goes in: an item id, or a tag with a leading {@code #}
     * @param output  what comes out
     * @param count   how many come out of one that goes in
     * @param ticks   how long at {@link KineticRules#REFERENCE_RPM}
     * @param pace    the speed word a line must reach for it to run
     */
    public record Step(String machine, String name, String input, String output,
                       int count, int ticks, KineticRules.Pace pace) {

        public Step {
            Objects.requireNonNull(machine, "a recipe on no machine");
            Objects.requireNonNull(name, "a recipe with no name");
            Objects.requireNonNull(input, "a recipe taking nothing");
            Objects.requireNonNull(output, "a recipe making nothing");
            Objects.requireNonNull(pace, "a recipe at no pace");
            if (count <= 0) {
                throw new IllegalArgumentException(name + " makes " + count
                        + " of " + output + ", which is a way of destroying "
                        + input);
            }
            if (ticks <= 0) {
                throw new IllegalArgumentException(name + " takes " + ticks
                        + " ticks, which is a machine that is not a machine");
            }
        }

        /** Whether the input is a tag rather than one item. */
        public boolean fromTag() {
            return input.startsWith("#");
        }
    }

    /**
     * One recipe the game itself ships whose result and at least one
     * ingredient are both in this graph.
     *
     * @param recipe which of the game's recipes this is
     * @param input  the ingredient of it that is in this graph
     * @param takes  how many of that ingredient it uses
     * @param output what it makes
     * @param makes  how many of that it makes
     */
    public record Route(String recipe, String input, int takes,
                        String output, int makes) {

        public Route {
            Objects.requireNonNull(recipe, "a route with no recipe");
            if (takes <= 0 || makes <= 0) {
                throw new IllegalArgumentException(recipe + " takes " + takes
                        + " and makes " + makes + ", which is not a recipe");
            }
        }

        /** How many outputs one input is worth. */
        public double rate() {
            return (double) makes / takes;
        }
    }

    /** One item that is a member of one tag these recipes read from. */
    public record Membership(String item, String tag) {

        public Membership {
            Objects.requireNonNull(item, "a member of a tag that is nothing");
            if (!Objects.requireNonNull(tag, "a member of no tag").startsWith("#")) {
                throw new IllegalArgumentException(tag + " is not a tag");
            }
        }
    }

    // ==================================================================
    // The stone: what Age I can do before it has met anything else
    // ==================================================================

    private static final KineticRules.Pace SLOW = KineticRules.Pace.SLOW;
    private static final KineticRules.Pace STEADY = KineticRules.Pace.STEADY;

    /** The module whose crops and kitchen these join onto. */
    public static final String FARM = "elysiumagronomy";

    /** The crops that go back through a millstone for their own seed. */
    public static final List<String> SEEDED = List.of(
            "onion", "barley", "garlic", "cabbage",
            "tomato", "chili", "ginger", "rice");


    /**
     * Grinding.
     *
     * <p>The first seven are the jobs vanilla alone justifies. The eight
     * after them are the seed supply, which is what made a farm automatable
     * in the jar this replaces: a surplus crop goes back through the
     * millstone and comes out as exactly the seed it grew from — one for one,
     * because a harvest already gives seeds and a mill that gave two would be
     * a field that plants itself twice over.
     */
    public static final List<Step> MILLING = milling();

    private static List<Step> milling() {
        List<Step> made = new ArrayList<>(List.of(
                mill("cobblestone", "minecraft:cobblestone", "minecraft:gravel", 1, 120, SLOW),
                mill("gravel", "minecraft:gravel", "minecraft:sand", 1, 120, SLOW),
                mill("sandstone", "minecraft:sandstone", "minecraft:sand", 2, 140, SLOW),
                mill("bone", "minecraft:bone", "minecraft:bone_meal", 4, 100, SLOW),
                mill("sugar_cane", "minecraft:sugar_cane", "minecraft:sugar", 2, 80, SLOW),
                mill("deepslate", "minecraft:deepslate",
                        "minecraft:cobbled_deepslate", 1, 200, STEADY),
                mill("blaze_rod", "minecraft:blaze_rod",
                        "minecraft:blaze_powder", 4, 160, STEADY)));
        for (String crop : SEEDED) {
            made.add(mill(crop + "_seeds", FARM + ":" + crop,
                    FARM + ":" + crop + "_seeds", 1, 140, SLOW));
        }
        return List.copyOf(made);
    }

    /** Pressing. */
    public static final List<Step> PRESSING = List.of(
            press("paper_from_sugar_cane", "minecraft:sugar_cane",
                    "minecraft:paper", 1, 100, SLOW),
            press("paper_from_bamboo", "minecraft:bamboo",
                    "minecraft:paper", 1, 120, SLOW),
            press("green_dye", "minecraft:cactus", "minecraft:green_dye", 2, 160, STEADY));

    /** Mixing. */
    public static final List<Step> MIXING = List.of(
            stir("flint_from_gravel", "minecraft:gravel", "minecraft:flint", 1, 200, STEADY),
            stir("sand_from_red_sand", "minecraft:red_sand", "minecraft:sand", 1, 160, STEADY),
            stir("soul_sand", "minecraft:soul_soil", "minecraft:soul_sand", 1, 180, STEADY));

    /**
     * Sawing, which is also the cutting board on a line.
     *
     * <p>The first three are timber. The eleven after them are every job the
     * kitchen's knife does, done by a machine instead — which is the whole of
     * what the three jars about automating a kitchen were for.
     *
     * <p><b>The saw only ever gives the main thing.</b> A knife cutting a
     * milk bucket gives butter <em>and</em> cream, and a hay bale gives
     * barley <em>and</em> rice; the saw gives the butter and the barley and
     * drops the rest on the floor. That is the price of not standing there,
     * and it is why {@code validate.py} reads the kitchen's own recipes and
     * refuses a mechanical route that yields more than the hand one it
     * automates.
     */
    public static final List<Step> SAWING = List.of(
            saw("planks_from_logs", "#minecraft:logs", "minecraft:oak_planks", 6, 140, STEADY),
            saw("sticks_from_planks", "#minecraft:planks", "minecraft:stick", 2, 80, STEADY),
            saw("bamboo_from_block", "minecraft:bamboo_block",
                    "minecraft:bamboo", 9, 100, STEADY),
            saw("minced_beef", "minecraft:beef", FARM + ":minced_beef", 2, 120, STEADY),
            saw("minced_pork", "minecraft:porkchop", FARM + ":minced_pork", 2, 120, STEADY),
            saw("minced_mutton", "minecraft:mutton", FARM + ":minced_mutton", 2, 120, STEADY),
            saw("minced_chicken", "minecraft:chicken", FARM + ":minced_chicken", 2, 120, STEADY),
            saw("fillet_from_cod", "minecraft:cod", FARM + ":fish_fillet", 2, 120, STEADY),
            saw("fillet_from_salmon", "minecraft:salmon", FARM + ":fish_fillet", 2, 120, STEADY),
            saw("cabbage_leaf", FARM + ":cabbage", FARM + ":cabbage_leaf", 3, 120, STEADY),
            saw("tomato_paste", FARM + ":tomato", FARM + ":tomato_paste", 2, 120, STEADY),
            saw("dough", "minecraft:wheat", FARM + ":dough", 1, 120, STEADY),
            saw("butter", "minecraft:milk_bucket", FARM + ":butter", 1, 160, STEADY),
            saw("barley_from_bale", "minecraft:hay_block", FARM + ":barley", 2, 140, STEADY));

    private static Step mill(String name, String in, String out, int count,
                             int ticks, KineticRules.Pace pace) {
        return new Step("milling", name, in, out, count, ticks, pace);
    }

    private static Step press(String name, String in, String out, int count,
                              int ticks, KineticRules.Pace pace) {
        return new Step("pressing", name, in, out, count, ticks, pace);
    }

    private static Step saw(String name, String in, String out, int count,
                            int ticks, KineticRules.Pace pace) {
        return new Step("sawing", name, in, out, count, ticks, pace);
    }

    private static Step stir(String name, String in, String out, int count,
                             int ticks, KineticRules.Pace pace) {
        return new Step("mixing", name, in, out, count, ticks, pace);
    }

    /** Every job every machine does, walked rather than listed. */
    public static final List<Step> EVERYTHING = everything();

    private static List<Step> everything() {
        List<Step> made = new ArrayList<>();
        made.addAll(MILLING);
        made.addAll(PRESSING);
        made.addAll(SAWING);
        made.addAll(MIXING);
        return List.copyOf(made);
    }

    /** Every job one machine does. */
    public static List<Step> on(String machine) {
        List<Step> made = new ArrayList<>();
        for (Step one : EVERYTHING) {
            if (one.machine().equals(machine)) {
                made.add(one);
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // The way back, which is almost always the game's own
    // ==================================================================

    /**
     * Every recipe the game itself ships that lives inside this graph.
     *
     * <p>Not a selection, and not the ones somebody thought of. {@code
     * validate.py} walks all 1,290 recipes in the game's own jar and fails if
     * any recipe whose result and one of whose ingredients are both nodes
     * here is missing from this list — and checks each of these against the
     * recipe file it names, counting a shaped recipe's pattern rather than
     * believing the number written here.
     *
     * <p>Three of them close a loop that comes back with exactly what it left
     * with: nine bamboo into a block and nine out again, nine wheat into a
     * bale and nine out again, and deepslate cobbled and smelted back. Those
     * three sit exactly on the line this rule draws, which is what makes the
     * line worth drawing.
     */
    public static final List<Route> THE_GAMES_ROUTES = List.of(
            new Route("minecraft:bamboo_block", "minecraft:bamboo", 9,
                    "minecraft:bamboo_block", 1),
            new Route("minecraft:blaze_powder", "minecraft:blaze_rod", 1,
                    "minecraft:blaze_powder", 2),
            new Route("minecraft:bone_meal", "minecraft:bone", 1,
                    "minecraft:bone_meal", 3),
            new Route("minecraft:deepslate", "minecraft:cobbled_deepslate", 1,
                    "minecraft:deepslate", 1),
            new Route("minecraft:green_dye", "minecraft:cactus", 1,
                    "minecraft:green_dye", 1),
            new Route("minecraft:hay_block", "minecraft:wheat", 9,
                    "minecraft:hay_block", 1),
            new Route("minecraft:paper", "minecraft:sugar_cane", 3,
                    "minecraft:paper", 3),
            new Route("minecraft:sandstone", "minecraft:sand", 4,
                    "minecraft:sandstone", 1),
            new Route("minecraft:stick", "#minecraft:planks", 2,
                    "minecraft:stick", 4),
            new Route("minecraft:stick_from_bamboo_item", "minecraft:bamboo", 2,
                    "minecraft:stick", 1),
            new Route("minecraft:sugar_from_sugar_cane", "minecraft:sugar_cane", 1,
                    "minecraft:sugar", 1),
            new Route("minecraft:wheat", "minecraft:hay_block", 1,
                    "minecraft:wheat", 9));

    /**
     * Which of our items belong to which of the tags our recipes read from.
     *
     * <p>A tag is a node in this graph like anything else, and an output that
     * is a member of a tag another recipe reads from is an edge nobody would
     * think to draw — which is exactly where a loop hides. Checked against
     * the game's own tag files.
     */
    public static final List<Membership> IN_TAG = List.of(
            new Membership("minecraft:oak_planks", "#minecraft:planks"));

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the processing. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInSteps(EVERYTHING));
        bad.addAll(faultsInLoops(EVERYTHING, THE_GAMES_ROUTES, IN_TAG));
        return List.copyOf(bad);
    }

    /**
     * The roster, one recipe at a time.
     *
     * <p>Kept apart from {@link #faultsInCoverage} on purpose. A method
     * holding both answers every control with whichever fires first — hand it
     * two recipes to prove it notices a duplicate name and it reports instead
     * that a machine has nothing to read, the control passes, and the rule
     * about names could be deleted with every check still green.
     */
    public static List<String> faultsInEachStep(List<Step> steps) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Step one : steps) {
            if (!seen.add(one.machine() + "/" + one.name())) {
                bad.add(one.machine() + "/" + one.name()
                        + " names two recipes, so one of them is a file the "
                        + "other overwrites");
            }
            if (!MACHINES.contains(one.machine())) {
                bad.add(one.name() + " is read by " + one.machine()
                        + ", which is not a machine");
            }
            if (one.pace() == KineticRules.Pace.STOPPED) {
                bad.add(one.name() + " runs on a stopped line, so it is a "
                        + "recipe that does not need a machine");
            }
            if (one.input().equals(one.output())) {
                bad.add(one.name() + " turns " + one.input() + " into itself, "
                        + "which is either a way of destroying it or a way of "
                        + "making more of it and is never a recipe");
            }
        }
        return bad;
    }

    /** Whether every machine has something to read. */
    public static List<String> faultsInCoverage(List<Step> steps) {
        List<String> bad = new ArrayList<>();
        if (steps.size() < MACHINES.size()) {
            bad.add("there are " + steps.size() + " recipe(s) for "
                    + MACHINES.size() + " machine(s), so one of them has "
                    + "nothing to read");
        }
        for (String machine : MACHINES) {
            boolean any = false;
            for (Step one : steps) {
                any |= one.machine().equals(machine);
            }
            if (!any) {
                bad.add(machine + " has no recipes at all, so it is a block "
                        + "that draws torque and does no work");
            }
        }
        return bad;
    }

    /** The roster: one name per machine, a real machine, a real pace. */
    public static List<String> faultsInSteps(List<Step> steps) {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInEachStep(steps));
        bad.addAll(faultsInCoverage(steps));
        return bad;
    }

    /**
     * Every loop that comes back with more than it started with.
     *
     * <p>In log space a product above one is a sum above zero, so weighting
     * each edge {@code -log(count)} turns "a loop that multiplies" into "a
     * cycle of negative weight", which Bellman-Ford finds. A source connected
     * to every node with weight zero makes it one pass rather than one per
     * starting point.
     *
     * @param steps  what the machines do
     * @param routes what the game does inside the same graph
     * @param inTag  which items are members of which tags read from
     */
    public static List<String> faultsInLoops(List<Step> steps,
                                             List<Route> routes,
                                             List<Membership> inTag) {
        List<String> bad = new ArrayList<>();
        List<String[]> edges = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Step one : steps) {
            edges.add(new String[] {one.input(), one.output()});
            weights.add(-Math.log(one.count()));
        }
        for (Route one : routes) {
            edges.add(new String[] {one.input(), one.output()});
            weights.add(-Math.log(one.rate()));
        }
        for (Membership one : inTag) {
            edges.add(new String[] {one.item(), one.tag()});
            weights.add(0.0D);
        }

        Set<String> nodes = new LinkedHashSet<>();
        for (String[] edge : edges) {
            nodes.add(edge[0]);
            nodes.add(edge[1]);
        }
        Map<String, Double> best = new HashMap<>();
        for (String node : nodes) {
            best.put(node, 0.0D);
        }
        // Bellman-Ford from a virtual source that reaches everything at zero.
        String moved = null;
        for (int round = 0; round < nodes.size(); round++) {
            moved = null;
            for (int at = 0; at < edges.size(); at++) {
                String[] edge = edges.get(at);
                double through = best.get(edge[0]) + weights.get(at);
                if (through < best.get(edge[1]) - 1.0E-9D) {
                    best.put(edge[1], through);
                    moved = edge[1];
                }
            }
            if (moved == null) {
                break;
            }
        }
        if (moved != null) {
            bad.add("a loop that comes back with more than it started with, "
                    + "somewhere through " + moved + ": a player with two "
                    + "machines and a hopper then has as much of it as they "
                    + "have patience for, which is the same bug as a line "
                    + "that gains torque and as an engine that makes energy");
        }
        return bad;
    }

    /** Every item these recipes make. */
    public static List<String> made() {
        Set<String> all = new LinkedHashSet<>();
        for (Step one : EVERYTHING) {
            all.add(one.output());
        }
        return List.copyOf(all);
    }

    /** Every item or tag these recipes take. */
    public static List<String> taken() {
        Set<String> all = new LinkedHashSet<>();
        for (Step one : EVERYTHING) {
            all.add(one.input());
        }
        return List.copyOf(all);
    }
}
