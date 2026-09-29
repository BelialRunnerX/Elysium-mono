package com.elysium.logistics.rules;

import com.elysium.industry.rules.IndustryRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Storage, executed.
 *
 * <p>Two claims here would ruin a save and neither would be visible until it
 * had: that items are never created or destroyed except when a player asked
 * for it, and that a crafting plan always terminates whatever is in the recipe
 * set. Both are swept rather than spot-checked — the first over a hundred
 * thousand random transfers, the second over randomly generated recipe sets
 * including deliberately circular ones.
 */
public final class LogisticHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    private static void near(float got, float want, float slack, String what) {
        check(Math.abs(got - want) <= slack,
                what + ": expected about " + want + ", got " + got);
    }

    public static void main(String[] args) {
        theDrawers();
        theCompression();
        theConservation();
        theSealAndTheKey();
        theNetwork();
        thePlanner();
        theOrder();
        theCycles();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: nothing is created or destroyed except by "
                + "a voiding upgrade, a drawer's seal comes back as the one that went in, "
                + "a compressed pool reads the same however it is "
                + "asked for, a network is a constant load that slows rather than "
                + "stopping, no recipe set can make a plan that does not terminate, and "
                + "no plan ever asks an assembler for something that has not been "
                + "made yet");
    }

    // ==================================================================

    private static void theDrawers() {
        List<LogisticRules.Drawer> ladder = List.of(LogisticRules.TIMBER_DRAWER,
                LogisticRules.BRASS_DRAWER, LogisticRules.STEEL_DRAWER);
        for (int index = 1; index < ladder.size(); index++) {
            LogisticRules.Drawer below = ladder.get(index - 1);
            LogisticRules.Drawer here = ladder.get(index);
            check(here.kinds() > below.kinds(),
                    "a drawer tier holds no more kinds than the one under it");
            check(here.stacks() > below.stacks(),
                    "a drawer tier holds no more of each than the one under it");
            check(here.total() == below.total() * LogisticRules.DRAWER_STEP
                            * LogisticRules.DRAWER_STEP,
                    "the drawer ladder is not the same step at every rung, so a player "
                            + "cannot tell what the next one buys them");
        }
        for (LogisticRules.Drawer drawer : ladder) {
            check(drawer.room() == drawer.stacks() * LogisticRules.STACK,
                    "a drawer's room for one kind is not its stacks");
            check(drawer.total() == drawer.kinds() * drawer.room(),
                    "a drawer's total is not its kinds times its room");
            check(drawer.total() > 0, "a drawer holds nothing");
        }
        check(LogisticRules.TIMBER_DRAWER.kinds() == 1,
                "the first drawer holds more than one kind, so the idea a drawer is "
                        + "built on — one thing, a great deal of it, visible — arrives "
                        + "already blurred");

        boolean refused = false;
        try {
            new LogisticRules.Drawer(0, 1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a drawer that holds no kind of thing can be declared");
        refused = false;
        try {
            new LogisticRules.Drawer(1, 0);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a drawer with no room in it can be declared");
    }

    // ==================================================================

    private static void theCompression() {
        check(LogisticRules.COMPRESSION == 9,
                "compression is not nine, which is what a crafting grid does and "
                        + "therefore the only number a player will expect");
        check(LogisticRules.inSmallest(1, 0) == 1, "a thing is not worth itself");
        check(LogisticRules.inSmallest(1, 1) == 9, "an ingot is not nine nuggets");
        check(LogisticRules.inSmallest(1, 2) == 81, "a block is not eighty-one nuggets");
        check(LogisticRules.inSmallest(5, 3) == 5 * 729,
                "three steps up is not nine cubed");

        Random random = new Random(20260924L);
        boolean lost = false;
        boolean wrongRemainder = false;
        for (int round = 0; round < 50_000; round++) {
            long smallest = (long) (random.nextDouble() * 2_000_000_000L);
            int steps = random.nextInt(4);
            long[] split = LogisticRules.fromSmallest(smallest, steps);
            long back = LogisticRules.inSmallest(split[0], steps) + split[1];
            if (back != smallest) {
                lost = true;
            }
            if (split[1] < 0 || split[1] >= LogisticRules.inSmallest(1, steps)) {
                wrongRemainder = true;
            }
        }
        check(!lost, "a compressed pool does not read back as what was put into it, so a "
                + "drawer quietly eats the remainder");
        check(!wrongRemainder, "a compressed pool's remainder is bigger than the thing it "
                + "is a remainder of, so it should have been another whole unit");

        // The example that matters: a drawer holding seven blocks and four
        // ingots must not report seven blocks and nothing else.
        long held = LogisticRules.inSmallest(7, 2) + LogisticRules.inSmallest(4, 1);
        long[] asBlocks = LogisticRules.fromSmallest(held, 2);
        check(asBlocks[0] == 7 && asBlocks[1] == 36,
                "seven blocks and four ingots does not read as seven blocks and "
                        + "thirty-six nuggets, so four ingots have gone missing as far "
                        + "as the player can see");
    }

    // ==================================================================

    private static void theConservation() {
        Random random = new Random(1234567L);
        boolean created = false;
        boolean destroyed = false;
        boolean overfilled = false;
        for (int round = 0; round < 200_000; round++) {
            int offered = random.nextInt(2000) - 500;
            int room = random.nextInt(2000) - 500;
            int taken = LogisticRules.accepted(offered, room);
            int left = LogisticRules.leftOver(offered, room);
            if (taken + left != Math.max(0, offered)) {
                if (taken + left > Math.max(0, offered)) {
                    created = true;
                } else {
                    destroyed = true;
                }
            }
            if (taken > Math.max(0, room)) {
                overfilled = true;
            }
            if (taken < 0 || left < 0) {
                destroyed = true;
            }
        }
        check(!created, "a transfer put more into a store and a hand together than was "
                + "offered, which is duplication");
        check(!destroyed, "a transfer lost items between the hand and the store");
        check(!overfilled, "a store took more than it had room for, so the remainder was "
                + "destroyed");

        check(LogisticRules.accepted(64, 0) == 0, "a full store takes items anyway");
        check(LogisticRules.leftOver(64, 0) == 64, "a full store swallows what it refused");
        check(LogisticRules.accepted(0, 64) == 0, "an empty hand hands something over");
        check(!LogisticRules.voids(false),
                "a drawer nobody upgraded destroys what will not fit, which is the one "
                        + "thing in this module that must be asked for");
        check(LogisticRules.voids(true),
                "a voiding upgrade does not void, so the one exception is not available "
                        + "either");
    }

    // ==================================================================

    /**
     * What a player fits to a drawer: one void seal, handed back whole, and a
     * key that locks it to what it holds.
     *
     * <p>The seal is walked rather than spot-checked. A player fits seals and
     * takes them out, and breaks drawers with seals in them, in whatever
     * order a random walk picks, and at every step the seals in the hand and
     * the seal in the drawer add up to what the hand started with.
     */
    private static void theSealAndTheKey() {
        PackRules.Part seal = PackRules.part(LogisticRules.DRAWER_SEAL);
        check(seal != null && PackRules.VOID.equals(seal.kind()),
                "a drawer is sealed with " + LogisticRules.DRAWER_SEAL + ", which is not "
                        + "the pack's void part, so a player who has learned what a seal "
                        + "does to a bag has learned nothing about a drawer");
        check(PackRules.part(LogisticRules.DRAWER_KEY) == null
                        && PackRules.body(LogisticRules.DRAWER_KEY) == null,
                "the drawer key shares its id with a pack or a part, and two items with "
                        + "one id is one of them not existing");

        check(LogisticRules.takesSeal(false),
                "a drawer with no seal refuses one, so voiding is still a flag only a "
                        + "command can set");
        check(!LogisticRules.takesSeal(true),
                "a sealed drawer takes another seal, which does nothing and costs the "
                        + "player the seal");
        check(LogisticRules.sealsIn(true) == 1,
                "a sealed drawer gives back " + LogisticRules.sealsIn(true) + " seal(s) "
                        + "for the one it took");
        check(LogisticRules.sealsIn(false) == 0,
                "a drawer with no seal gives one back, which is a seal out of nothing");
        for (boolean sealed : new boolean[] {false, true}) {
            check(LogisticRules.voids(sealed) == (LogisticRules.sealsIn(sealed) > 0),
                    "a drawer " + (sealed ? "with" : "without") + " a seal "
                            + (sealed ? "does not void" : "voids") + ", so what destroys "
                            + "things is not the part a player fitted");
        }

        // Fitting takes one off the hand if the drawer will have it; taking it
        // out and breaking the drawer both give back whatever is in it.
        Random random = new Random(20260929L);
        int start = 3;
        int hand = start;
        boolean voiding = false;
        int wrong = 0;
        for (int step = 0; step < 50_000; step++) {
            if (random.nextBoolean()) {
                if (hand > 0 && LogisticRules.takesSeal(voiding)) {
                    voiding = true;
                    hand -= 1;
                }
            } else {
                hand += LogisticRules.sealsIn(voiding);
                voiding = false;
            }
            if (hand + LogisticRules.sealsIn(voiding) != start) {
                wrong++;
            }
        }
        check(wrong == 0, wrong + " step(s) of fitting, taking out and breaking did not "
                + "add up to the seals the hand started with, so a seal was made or "
                + "destroyed along the way");

        check(LogisticRules.keepsKind(true, 0),
                "a locked drawer forgets what it held when it runs dry, so the lock does "
                        + "nothing a hopper would notice");
        check(!LogisticRules.keepsKind(false, 0),
                "an unlocked drawer that has run dry keeps its kind, so it is locked "
                        + "whether or not anybody turned the key");
        for (int count : new int[] {1, LogisticRules.STACK, LogisticRules.STEEL_DRAWER.room()}) {
            check(LogisticRules.keepsKind(false, count) && LogisticRules.keepsKind(true, count),
                    "a drawer holding " + count + " of something forgets what it is, so "
                            + "two kinds of thing end up counted as one");
        }
        check(!LogisticRules.mayLock(0),
                "the rules let a key lock a drawer holding nothing, which then refuses "
                        + "everything and looks broken");
        for (LogisticRules.Drawer drawer : List.of(LogisticRules.TIMBER_DRAWER,
                LogisticRules.BRASS_DRAWER, LogisticRules.STEEL_DRAWER)) {
            for (int kinds = 1; kinds <= drawer.kinds(); kinds++) {
                check(LogisticRules.mayLock(kinds), "a key will not lock a drawer holding "
                        + kinds + " kind(s), so nothing can keep it sorted");
            }
        }
    }

    // ==================================================================

    private static void theNetwork() {
        LogisticRules.Network small = new LogisticRules.Network(8);
        LogisticRules.Network big = new LogisticRules.Network(LogisticRules.MOST_DEVICES);
        LogisticRules.Network over =
                new LogisticRules.Network(LogisticRules.MOST_DEVICES + 1);

        check(!small.overloaded() && !big.overloaded(),
                "a controller cannot index the number of devices it says it can");
        check(over.overloaded(),
                "a controller indexes more than its stated limit, so the limit is not one");
        near(big.movesAt(1.0F), LogisticRules.BANDWIDTH, 0.001F,
                "a full network at a full share does not move what it says it moves");
        near(over.movesAt(1.0F), 0.0F, 0.001F, "an overloaded network still works");

        // The same decision this pack has now made three times.
        near(small.movesAt(0.5F), LogisticRules.BANDWIDTH / 2.0F, 0.001F,
                "a network on a half-fed grid does not move half as much; it is supposed "
                        + "to slow rather than stop, like everything else on a grid");
        near(small.movesAt(0.0F), 0.0F, 0.001F, "a network on a dead grid still moves items");
        near(small.movesAt(2.0F), LogisticRules.BANDWIDTH, 0.001F,
                "a grid with more than the network needs makes it faster than full, so "
                        + "over-building is an upgrade nobody documented");

        // And the rule that stops it oscillating.
        for (float share : new float[] {0.0F, 0.25F, 1.0F}) {
            near(small.machine().draw(), 8 * LogisticRules.DEVICE_DRAW, 0.001F,
                    "a network's draw moved when the share did, which is a loop with no "
                            + "fixed point");
        }
        check(small.machine().draw() < big.machine().draw(),
                "a bigger network costs no more to run, so the device limit is the only "
                        + "thing making a player think about size");
        check(big.machine().draw() < IndustryRules.STEAM_TURBINE.output(),
                "a full network wants " + big.machine().draw() + " and a steam turbine "
                        + "makes " + IndustryRules.STEAM_TURBINE.output()
                        + ", so 'one turbine runs one full network' is not true and "
                        + "there is no simple sentence to replace it with");
        check(big.machine().draw() > IndustryRules.STEAM_TURBINE.output() / 2.0F,
                "a full network costs less than half a turbine, so it never registers as "
                        + "a load and the player has no reason to care about Age II");
        check(small.machine().draw() < IndustryRules.STEAM_TURBINE.output() / 4.0F,
                "a first network of eight drawers already wants a quarter of a turbine, "
                        + "so building one is a choice between storage and light");
        check(big.machine().rated() == IndustryRules.Voltage.LOW,
                "a network needs a wire a player would not have when they build one");

        check(big.holds(LogisticRules.TIMBER_DRAWER)
                        < big.holds(LogisticRules.STEEL_DRAWER),
                "a wall of steel drawers holds no more than a wall of timber ones");
        check(over.holds(LogisticRules.STEEL_DRAWER)
                        == big.holds(LogisticRules.STEEL_DRAWER),
                "an overloaded network counts the devices it cannot index towards what "
                        + "it holds");

        boolean refused = false;
        try {
            new LogisticRules.Network(-1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a network with a negative number of devices can be declared");
    }

    // ==================================================================

    private static LogisticRules.Step step(String output, int makes, Object... needs) {
        Map<String, Integer> wants = new HashMap<>();
        for (int index = 0; index < needs.length; index += 2) {
            wants.put((String) needs[index], (Integer) needs[index + 1]);
        }
        return new LogisticRules.Step(output, makes, wants);
    }

    private static void thePlanner() {
        Map<String, LogisticRules.Step> steps = new HashMap<>();
        steps.put("plank", step("plank", 4, "log", 1));
        steps.put("stick", step("stick", 4, "plank", 2));
        steps.put("table", step("table", 1, "plank", 4));
        steps.put("pick", step("pick", 1, "plank", 3, "stick", 2));

        LogisticRules.Plan one = LogisticRules.plan(steps, "pick", 1);
        check(one.possible(), "a plain three-deep plan was refused: " + one.refused());
        check(one.runs().get("pick") == 1L, "one pick takes other than one run");
        // Three planks for the head and one run of sticks (which makes four)
        // needing two planks: four planks, which is one run of planks.
        check(one.runs().get("stick") == 1L, "one pick takes other than one run of sticks");
        check(one.runs().get("plank") == 2L,
                "a pick needs three planks for itself and two for the sticks, which is "
                        + "five and therefore two runs of four — got "
                        + one.runs().get("plank"));
        check(one.missing().get("log") == 2L,
                "two runs of planks take two logs — got " + one.missing().get("log"));

        // Rounding up, which is the difference between a plan and a haunting.
        LogisticRules.Plan five = LogisticRules.plan(steps, "table", 5);
        check(five.runs().get("table") == 5L, "five tables is not five runs");
        check(five.runs().get("plank") == 5L,
                "five tables need twenty planks, which is five runs of four — got "
                        + five.runs().get("plank"));
        LogisticRules.Plan two = LogisticRules.plan(steps, "stick", 1);
        check(two.runs().get("stick") == 1L,
                "wanting one stick out of a recipe that makes four takes other than one "
                        + "run, so the planner deals in fractions of a craft");

        // Nothing is planned for nothing.
        LogisticRules.Plan none = LogisticRules.plan(steps, "table", 0);
        check(none.possible() && none.runs().isEmpty(),
                "wanting none of something is a plan with steps in it");

        // Something nobody can make is missing, not refused.
        LogisticRules.Plan raw = LogisticRules.plan(steps, "diamond", 7);
        check(raw.possible() && raw.missing().get("diamond") == 7L,
                "a thing with no recipe is refused rather than reported as something to "
                        + "go and find");

        // The cycle, which is the whole reason this is a file and not a lambda.
        Map<String, LogisticRules.Step> circular = new HashMap<>(steps);
        circular.put("log", step("log", 1, "plank", 4));
        LogisticRules.Plan looped = LogisticRules.plan(circular, "pick", 1);
        check(!looped.possible(),
                "a recipe set where logs are made of planks and planks of logs planned "
                        + "without complaint, which means it looped");
        check(looped.refused() != null && looped.refused().contains("made out of itself"),
                "a refused cycle does not say it is a cycle, so a player cannot act on it");

        // Something that is simply very deep.
        Map<String, LogisticRules.Step> deep = new HashMap<>();
        for (int level = 0; level < LogisticRules.MOST_DEPTH + 5; level++) {
            deep.put("tier" + level, step("tier" + level, 1, "tier" + (level + 1), 1));
        }
        LogisticRules.Plan far = LogisticRules.plan(deep, "tier0", 1);
        check(!far.possible(),
                "a chain deeper than the limit planned anyway, so the limit is not one");
        check(far.refused().contains("deep"), "a plan refused for depth does not say so");

        // And the property that matters most: it always comes back.
        Random random = new Random(987654321L);
        int refusedPlans = 0;
        for (int round = 0; round < 3_000; round++) {
            Map<String, LogisticRules.Step> made = new HashMap<>();
            int things = 3 + random.nextInt(12);
            for (int index = 0; index < things; index++) {
                Map<String, Integer> needs = new HashMap<>();
                int count = random.nextInt(3);
                for (int need = 0; need < count; need++) {
                    // Deliberately allowed to point anywhere, including back at
                    // itself: a recipe set from a real pack is not a tree.
                    needs.put("item" + random.nextInt(things), 1 + random.nextInt(4));
                }
                made.put("item" + index,
                        new LogisticRules.Step("item" + index, 1 + random.nextInt(4), needs));
            }
            LogisticRules.Plan plan = LogisticRules.plan(made, "item0",
                    1 + random.nextInt(1000));
            if (!plan.possible()) {
                refusedPlans++;
            }
        }
        check(refusedPlans > 0,
                "three thousand random recipe sets produced no circular one, so the test "
                        + "is not testing what it claims to");
        checks++;                                  // it returned at all, three thousand times
        System.out.println("  planner: " + refusedPlans
                + " of 3000 random recipe sets refused, and every one of them returned");
    }

    /**
     * The order a plan has to happen in, and the proof that following it works.
     *
     * <p>{@link LogisticRules.Plan#runs} says what and says nothing about when.
     * Everything below is about the difference: an autocrafter that assembles
     * the gear before the plates exist does not crash — it stalls holding
     * somebody's iron, which is the single most complained-about behaviour in
     * every storage mod this module replaces.
     */
    private static void theOrder() {
        Map<String, LogisticRules.Step> steps = new HashMap<>();
        steps.put("plank", new LogisticRules.Step("plank", 4, Map.of("log", 1)));
        steps.put("stick", new LogisticRules.Step("stick", 4, Map.of("plank", 2)));
        steps.put("table", new LogisticRules.Step("table", 1, Map.of("plank", 4)));
        steps.put("pick", new LogisticRules.Step("pick", 1,
                Map.of("stick", 2, "plank", 3)));

        LogisticRules.Plan one = LogisticRules.plan(steps, "pick", 1);
        check(one.possible(), "a perfectly ordinary plan was refused");
        check(one.jobs().size() == one.runs().size(),
                "the order has " + one.jobs().size() + " job(s) for " + one.runs().size()
                        + " step(s); every step is a job and every job is a step");
        check(one.totalRuns() > 0, "a plan that makes something runs nothing");

        List<String> so_far = new ArrayList<>();
        for (LogisticRules.Job job : one.jobs()) {
            LogisticRules.Step step = steps.get(job.output());
            for (String need : step.needs().keySet()) {
                check(!one.runs().containsKey(need) || so_far.contains(need),
                        job.output() + " is made before " + need + ", which it is made "
                                + "of");
            }
            so_far.add(job.output());
        }
        check(so_far.indexOf("plank") < so_far.indexOf("stick"),
                "sticks are cut before there are planks to cut them from");
        check(so_far.indexOf("stick") < so_far.indexOf("pick"),
                "the pick is assembled before its sticks exist");

        // The same plan twice. A factory that used a different assembler for
        // the same job on every world load is not wrong, but it is a base that
        // never looks the same way twice.
        check(LogisticRules.plan(steps, "pick", 1).jobs().equals(one.jobs()),
                "the same request planned twice comes out in two different orders");

        // And the proof: follow the order with nothing but what the plan said
        // would have to be found, and see whether it works.
        followed(steps, "pick", 1);
        followed(steps, "pick", 999);
        followed(steps, "table", 5);
        followed(steps, "stick", 1);
        followed(steps, "log", 12);            // nothing to make, everything to find
        followed(steps, "table", 0);

        Random random = new Random(24680135L);
        int followedSets = 0;
        for (int round = 0; round < 4_000; round++) {
            Map<String, LogisticRules.Step> made = new HashMap<>();
            int things = 3 + random.nextInt(12);
            for (int index = 0; index < things; index++) {
                Map<String, Integer> needs = new HashMap<>();
                int count = random.nextInt(3);
                for (int need = 0; need < count; need++) {
                    needs.put("item" + random.nextInt(things), 1 + random.nextInt(4));
                }
                made.put("item" + index, new LogisticRules.Step("item" + index,
                        1 + random.nextInt(4), needs));
            }
            if (followed(made, "item0", 1 + random.nextInt(500))) {
                followedSets++;
            }
        }
        check(followedSets > 500,
                "only " + followedSets + " of four thousand random recipe sets could be "
                        + "planned at all, so the simulation above is mostly testing "
                        + "refusals");

        // A recipe set with no cycle in it, no great depth, and far too many
        // ways through. Every real Minecraft recipe tree is this shape, and
        // before there was a budget this call did not come back.
        Map<String, LogisticRules.Step> wide = new HashMap<>();
        int levels = 20;
        int across = 4;
        for (int level = 0; level < levels; level++) {
            for (int index = 0; index < across; index++) {
                Map<String, Integer> needs = new HashMap<>();
                for (int below = 0; below < across; below++) {
                    needs.put("l" + (level + 1) + "i" + below, 1);
                }
                needs = level + 1 < levels ? needs : Map.of();
                wide.put("l" + level + "i" + index,
                        new LogisticRules.Step("l" + level + "i" + index, 1, needs));
            }
        }
        long began = System.nanoTime();
        LogisticRules.Plan enormous = LogisticRules.plan(wide, "l0i0", 1);
        long took = (System.nanoTime() - began) / 1_000_000L;
        check(!enormous.possible(),
                "a recipe set with " + Math.round(Math.pow(across, levels - 1)) + " ways "
                        + "through it was planned rather than refused");
        check(enormous.refused().contains("refused rather than waited for"),
                "a plan that ran out of budget does not say that is what happened");
        check(took < 5_000,
                "refusing an over-wide recipe set took " + took + "ms, so the budget is "
                        + "not what is stopping it");

        // A batch never overflows a set of slots, never exceeds what is left,
        // and is never nothing while there is something to do. A batch of zero
        // with work outstanding is an autocrafter that has quietly stopped.
        Random sizes = new Random(112233L);
        for (int round = 0; round < 200_000; round++) {
            long remaining = sizes.nextInt(100_000);
            int perRun = sizes.nextInt(200);
            int batch = LogisticRules.batchSize(remaining, perRun);
            check(batch >= 0, "a negative batch of " + batch);
            check(batch <= remaining, "a batch of " + batch + " for " + remaining
                    + " runs outstanding");
            check(remaining <= 0 || batch >= 1,
                    "nothing dispatched with " + remaining + " runs still to do");
            check(perRun > LogisticRules.STACK || batch <= 0
                            || (long) batch * Math.max(1, perRun) <= LogisticRules.STACK,
                    "a batch of " + batch + " at " + perRun + " each is "
                            + ((long) batch * Math.max(1, perRun))
                            + " items, which will not fit in a slot");
        }
        check(LogisticRules.batchSize(0, 4) == 0, "a finished job was dispatched anyway");
        check(LogisticRules.jobsAtOnce(0) == 0,
                "a core with no assemblers runs jobs regardless, so they are made "
                        + "nowhere");
        check(LogisticRules.jobsAtOnce(1000) == LogisticRules.MOST_JOBS,
                "a thousand assemblers run a thousand jobs, so the limit is not one");
        check(LogisticRules.assembleTicks(1, 1.0F) == LogisticRules.ASSEMBLE_TICKS,
                "one run at full power does not take the stated time");
        check(LogisticRules.assembleTicks(1, 0.25F) > LogisticRules.ASSEMBLE_TICKS,
                "a browned-out assembler is no slower than a fed one");
        check(LogisticRules.assembleTicks(10, 1.0F)
                        == 10 * LogisticRules.ASSEMBLE_TICKS,
                "ten runs do not take ten times as long as one");

        System.out.println("  order: " + followedSets + " of 4000 random plans followed "
                + "to the end with no ingredient ever going short, and a "
                + Math.round(Math.pow(across, levels - 1)) + "-path set refused in "
                + took + "ms");
    }

    /**
     * Follow a plan in the order it gives, starting from what it said would
     * have to be found, and complain if anything ever goes short.
     *
     * @return whether there was a plan to follow at all
     */
    private static boolean followed(Map<String, LogisticRules.Step> steps, String target,
                                    long wanted) {
        LogisticRules.Plan plan = LogisticRules.plan(steps, target, wanted);
        if (!plan.possible()) {
            return false;
        }
        Map<String, Long> have = new HashMap<>(plan.missing());
        for (LogisticRules.Job job : plan.jobs()) {
            LogisticRules.Step step = steps.get(job.output());
            check(step != null, "the plan contains a job for " + job.output()
                    + ", which is not a recipe");
            if (step == null) {
                return true;
            }
            for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
                long asked = job.runs() * need.getValue();
                long held = have.getOrDefault(need.getKey(), 0L);
                check(held >= asked, "making " + job.output() + " asked for " + asked
                        + " of " + need.getKey() + " and only " + held + " existed by "
                        + "then, so the plan under-counted or the order is wrong");
                have.put(need.getKey(), held - asked);
            }
            have.merge(job.output(), job.runs() * step.makes(), Long::sum);
        }
        check(have.getOrDefault(target, 0L) >= wanted,
                "following the whole plan produced " + have.getOrDefault(target, 0L)
                        + " of " + target + " and " + wanted + " were asked for");
        for (Map.Entry<String, Long> left : have.entrySet()) {
            check(left.getValue() >= 0,
                    left.getKey() + " ended at " + left.getValue()
                            + ", which is items that were spent twice");
        }
        return true;
    }

    private static void theCycles() {
        Map<String, LogisticRules.Step> clean = new HashMap<>();
        clean.put("plank", step("plank", 4, "log", 1));
        clean.put("stick", step("stick", 4, "plank", 2));
        check(LogisticRules.cyclesIn(clean).isEmpty(),
                "a plain recipe set is reported as circular");

        Map<String, LogisticRules.Step> circular = new HashMap<>(clean);
        circular.put("log", step("log", 1, "plank", 4));
        Set<String> caught = LogisticRules.cyclesIn(circular);
        check(caught.contains("log") && caught.contains("plank"),
                "a two-step cycle is not found by the check that exists to find it "
                        + "without planning anything");
        check(!caught.contains("stick"),
                "something merely downstream of a cycle is reported as part of it, so a "
                        + "player is told to fix a recipe that is fine");
        check(LogisticRules.reaches(circular, "stick", "log"),
                "a stick is not seen to need a log, three steps down");
        check(!LogisticRules.reaches(circular, "stick", "stick"),
                "a stick is seen to need itself, which it does not — and asking that "
                        + "question on a recipe set that does contain a cycle is exactly "
                        + "where a walker without a memory would hang");
        check(!LogisticRules.reaches(clean, "plank", "stick"),
                "a plank is seen to need a stick, which is the dependency backwards");

        Map<String, LogisticRules.Step> itself = new HashMap<>();
        itself.put("loop", step("loop", 1, "loop", 1));
        check(LogisticRules.cyclesIn(itself).contains("loop"),
                "a recipe that needs itself is not reported as a cycle");

        // The two ways of finding a cycle have to agree.
        Random random = new Random(24680L);
        boolean disagreed = false;
        for (int round = 0; round < 2_000; round++) {
            Map<String, LogisticRules.Step> made = new HashMap<>();
            int things = 3 + random.nextInt(8);
            for (int index = 0; index < things; index++) {
                Map<String, Integer> needs = new HashMap<>();
                for (int need = 0; need < random.nextInt(3); need++) {
                    needs.put("item" + random.nextInt(things), 1);
                }
                made.put("item" + index, new LogisticRules.Step("item" + index, 1, needs));
            }
            Set<String> found = LogisticRules.cyclesIn(made);
            LogisticRules.Plan plan = LogisticRules.plan(made, "item0", 1);
            boolean plannerSaidCycle = !plan.possible()
                    && plan.refused().contains("made out of itself");
            if (plannerSaidCycle && found.isEmpty()) {
                disagreed = true;
            }
        }
        check(!disagreed, "the planner found a cycle the standing cycle check says is not "
                + "there, so a network would refuse a craft it had already told the "
                + "player was fine");

        List<String> everything = new ArrayList<>(LogisticRules.cyclesIn(Map.of()));
        check(everything.isEmpty(), "an empty recipe set contains a cycle");
    }
}
