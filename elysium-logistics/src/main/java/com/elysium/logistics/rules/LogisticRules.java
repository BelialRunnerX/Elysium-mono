package com.elysium.logistics.rules;

import com.elysium.industry.rules.IndustryRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Storage, in numbers. The JDK and Age II's grid, and nothing else.
 *
 * <h2>Why a tech pack lives or dies on this file</h2>
 *
 * A player will spend more hours moving items than making power. Every mod
 * this module replaces knows that, and each of them answers it differently:
 * one drawer per item and thousands of it; a bag with upgrades; a digital
 * network with channels and autocrafting. Seventeen jars, four ideas.
 *
 * <p>The four ideas are kept and the seventeen implementations are not,
 * because the thing that makes a storage mod good is not the feature list, it
 * is whether the numbers behind it are ones a player can hold. So:
 *
 * <ol>
 *   <li><b>A drawer</b> holds one thing and a great deal of it, costs nothing
 *       to run, and you can see what is in it. That is the whole of the early
 *       game and it needs no power.</li>
 *   <li><b>A network</b> indexes drawers, and is a machine on Age II's grid
 *       like any other: it draws power, it runs at whatever share the grid can
 *       afford, and it slows rather than stopping — which is the decision this
 *       pack has now made three times and will not stop making.</li>
 *   <li><b>A core</b> stores what a warehouse could not, in types rather than
 *       in blocks, and belongs to Age III because it wants a buffered grid
 *       behind it.</li>
 *   <li><b>Autocrafting</b>, which is the one genuinely hard thing here, and
 *       the reason most of this file is about proving a plan terminates.</li>
 * </ol>
 *
 * <h2>The two properties everything else rests on</h2>
 *
 * Items are never created and never destroyed except by something a player
 * asked for; and a crafting plan always terminates, whatever nonsense is in
 * the recipe set. Both are proved by execution rather than by reading.
 */
public final class LogisticRules {

    private LogisticRules() {
    }

    // ==================================================================
    // What a drawer is
    // ==================================================================

    /** How many items are in a stack, for everything that stacks at all. */
    public static final int STACK = 64;

    /**
     * A drawer: some number of kinds, and some number of stacks of each.
     *
     * <p>Kept as stacks rather than as items because that is the number a
     * player can picture — "two hundred and fifty-six stacks" is a shelf,
     * "16384" is a spreadsheet.
     *
     * @param kinds  how many different items it will hold at once
     * @param stacks how many stacks of each
     */
    public record Drawer(int kinds, int stacks) {

        public Drawer {
            if (kinds <= 0) {
                throw new IllegalArgumentException("a drawer that holds no kind of thing "
                        + "is a block");
            }
            if (stacks <= 0) {
                throw new IllegalArgumentException("a drawer with no room in it is a "
                        + "block");
            }
        }

        /** How many items of one kind it will take. */
        public int room() {
            return stacks * STACK;
        }

        /** Everything it will hold, across all its kinds. */
        public int total() {
            return kinds * stacks * STACK;
        }
    }

    /** Timber and a glass front. The first thing anybody builds. */
    public static final Drawer TIMBER_DRAWER = new Drawer(1, 32);

    /** How much more each tier of drawer holds, and how many more kinds. */
    public static final int DRAWER_STEP = 2;

    /** Brass bands. Two kinds, twice the depth. */
    public static final Drawer BRASS_DRAWER = new Drawer(
            TIMBER_DRAWER.kinds() * DRAWER_STEP, TIMBER_DRAWER.stacks() * DRAWER_STEP);

    /** Steel. What a factory's wall is made of. */
    public static final Drawer STEEL_DRAWER = new Drawer(
            BRASS_DRAWER.kinds() * DRAWER_STEP, BRASS_DRAWER.stacks() * DRAWER_STEP);

    /**
     * How many of the smaller thing make the larger, at every step of a
     * compression chain the game actually has.
     *
     * Nine, because that is what a crafting grid does: nine nuggets to an
     * ingot, nine ingots to a block. A drawer that understands compression
     * holds one pool and shows it in whichever of the three you ask for, which
     * is the single most useful thing a storage mod ever did and costs nothing
     * but this number.
     */
    public static final int COMPRESSION = 9;

    /**
     * How many of the smallest unit a pool of {@code amount} at
     * {@code steps} up the chain is worth.
     */
    public static long inSmallest(long amount, int steps) {
        long worth = amount;
        for (int step = 0; step < Math.max(0, steps); step++) {
            worth *= COMPRESSION;
        }
        return worth;
    }

    /**
     * How much of the thing at {@code steps} up the chain a pool of the
     * smallest unit is worth, and what is left over.
     *
     * Two numbers rather than one, because a drawer showing "7 blocks" when it
     * holds 7 blocks and 4 ingots is a drawer that has quietly eaten four
     * ingots as far as the player can tell.
     */
    public static long[] fromSmallest(long smallest, int steps) {
        long divisor = inSmallest(1, steps);
        return new long[] {smallest / divisor, smallest % divisor};
    }

    // ==================================================================
    // What a network is
    // ==================================================================

    /**
     * What one device on a network costs to keep indexed, in FE per tick.
     *
     * <p>Small, and it has to be: a warehouse wall is fifty drawers, and a
     * player who has to choose between lighting their factory and being able
     * to find a stick has been given a bad choice. What it is <em>for</em> is
     * that a network is visibly a load on the grid, so that the moment a
     * player builds one they have a reason to care about Age II.
     */
    public static final float DEVICE_DRAW = 1.5F;

    /**
     * How many devices one controller will index.
     *
     * <p>A hard number rather than a soft slowdown, because "your network is
     * full, build another controller" is a sentence a player can act on and
     * "everything is mysteriously slower now" is not. This is the one place
     * this pack prefers a wall to a gradient, and the reason is legibility.
     */
    public static final int MOST_DEVICES = 64;

    /** Items a fully fed network moves per tick. */
    public static final float BANDWIDTH = 16.0F;

    /**
     * A network: how many things are on it, and what they cost.
     *
     * @param devices how many slots on the controller are taken — a drawer
     *                is one, and so is any device, however dear
     * @param extra   what the dear devices pay beyond the one draw every slot
     *                pays: {@link NetRules#surcharge}. Zero for a network of
     *                drawers alone.
     */
    public record Network(int devices, int extra) {

        public Network {
            if (devices < 0) {
                throw new IllegalArgumentException("a network with a negative number of "
                        + "devices on it is an accounting error");
            }
            if (extra < 0) {
                throw new IllegalArgumentException("a network whose devices pay less "
                        + "than a slot costs is an accounting error");
            }
        }

        /** A network of drawers alone, which pay nothing beyond their slot. */
        public Network(int devices) {
            this(devices, 0);
        }

        /** Whether this controller can index what has been hung off it. */
        public boolean overloaded() {
            return devices > MOST_DEVICES;
        }

        /**
         * What it asks the grid for.
         *
         * A constant, given the devices — never a function of what the grid
         * can afford. A network that asked for less when the grid was short
         * would change what the grid could afford, which would change what the
         * network asked for; that oscillates and never settles, and this pack
         * has made the mistake impossible in three modules now.
         */
        public IndustryRules.Machine machine() {
            return new IndustryRules.Machine(devices * DEVICE_DRAW + extra * DEVICE_DRAW,
                    IndustryRules.Voltage.LOW);
        }

        /** How many items a tick it moves, at the share the grid can afford. */
        public float movesAt(float share) {
            if (overloaded() || share <= 0.0F) {
                return 0.0F;
            }
            return BANDWIDTH * Math.min(1.0F, share);
        }

        /** Everything the drawers on it will hold, if they are all this kind. */
        public long holds(Drawer drawer) {
            return (long) Math.min(devices, MOST_DEVICES) * drawer.total();
        }
    }

    // ==================================================================
    // Autocrafting
    // ==================================================================

    /**
     * One step of a plan: this much of this, out of that much of those.
     *
     * <p>Deliberately not a Minecraft recipe. The thing worth proving about
     * autocrafting is that a plan terminates and that its material demands
     * multiply out exactly, and neither of those has anything to do with
     * ingredients or item stacks — so they are proved here, on a structure
     * a harness can build a thousand of, rather than in a block entity.
     *
     * @param output what it makes
     * @param makes  how many of it one run makes
     * @param needs  what one run consumes, as name to count
     */
    public record Step(String output, int makes, Map<String, Integer> needs) {

        public Step {
            if (makes <= 0) {
                throw new IllegalArgumentException("a recipe that makes nothing is not a "
                        + "recipe");
            }
            needs = Map.copyOf(needs);
        }
    }

    /**
     * How deep a plan may go before we stop planning.
     *
     * <p>Not a performance limit. A recipe set can be circular — a mod that
     * turns a block into nine ingots and nine ingots back into a block is a
     * cycle, and every pack has dozens — and a planner that followed one would
     * hang the server. This is the backstop; {@link #plan} refuses a cycle on
     * its own before it ever reaches this, and the depth limit is what catches
     * the shapes nobody thought of.
     */
    public static final int MOST_DEPTH = 32;

    /** How many separate steps a single plan may contain. */
    public static final int MOST_STEPS = 4096;

    /**
     * What a plan came to.
     *
     * @param runs     how many times each step must run, by output name
     * @param missing  what has to be found rather than made, by name
     * @param refused  why it could not be planned, or null if it could
     */
    public record Plan(Map<String, Long> runs, Map<String, Long> missing,
                       List<Job> jobs, String refused) {

        public boolean possible() {
            return refused == null;
        }

        /** How many machine-runs the whole thing comes to. */
        public long totalRuns() {
            long total = 0;
            for (Job job : jobs) {
                total += job.runs();
            }
            return total;
        }
    }

    /**
     * One entry of a plan, in the order it has to happen.
     *
     * <p>{@link Plan#runs} says <em>what</em> and says nothing at all about
     * <em>when</em>, and a map has no order. An autocrafter that read it in
     * whatever order a hash table produced would try to assemble the gear
     * before the plates existed, find the ingredients missing, and either
     * stall or — far worse — quietly drop the job. So the order is worked out
     * once, here, where a harness can check it against the plan it came from.
     *
     * @param output what this job makes
     * @param runs   how many times the recipe for it has to run
     */
    public record Job(String output, long runs) {

        public Job {
            if (runs <= 0) {
                throw new IllegalArgumentException("a job that runs no times is not a "
                        + "job");
            }
        }
    }

    /**
     * How many recipes one plan may look at before it gives up.
     *
     * <h2>Why a depth limit is not enough</h2>
     *
     * {@link #MOST_DEPTH} bounds how <em>deep</em> a plan goes and
     * {@link #MOST_STEPS} bounds how many different things are in it. Neither
     * bounds how long it takes, because the walk is over paths rather than
     * over recipes: a recipe set where five things each need the same four
     * ingredients, four deep, has a few dozen recipes in it and hundreds of
     * thousands of paths through them. Every real Minecraft recipe tree is
     * shaped exactly like that.
     *
     * <p>So there is a budget, and running out of it is a refusal with a
     * sentence in it rather than a server that stopped answering. This pack
     * would rather say "that is too complicated" than hang.
     */
    public static final int MOST_WALKS = 200_000;

    /**
     * Put the steps of a plan in an order they can actually be done in.
     *
     * <p>Every job whose ingredients are themselves jobs comes after them.
     * Settled repeatedly rather than by recursion, and over a sorted view of
     * the runs, so that the same plan comes out in the same order twice — an
     * autocrafter that used a different assembler for the same job on every
     * world load is not wrong, but it is a factory that never looks the same
     * way twice.
     *
     * @return the jobs in a workable order, or an empty list if there is no
     *         such order, which can only mean a cycle
     */
    public static List<Job> order(Map<String, Step> steps, Map<String, Long> runs) {
        List<Job> out = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        boolean moved = true;
        while (moved && placed.size() < runs.size()) {
            moved = false;
            for (Map.Entry<String, Long> entry : new TreeMap<>(runs).entrySet()) {
                if (placed.contains(entry.getKey())) {
                    continue;
                }
                Step step = steps.get(entry.getKey());
                boolean ready = true;
                if (step != null) {
                    for (String need : step.needs().keySet()) {
                        if (runs.containsKey(need) && !placed.contains(need)) {
                            ready = false;
                            break;
                        }
                    }
                }
                if (ready) {
                    placed.add(entry.getKey());
                    out.add(new Job(entry.getKey(), entry.getValue()));
                    moved = true;
                }
            }
        }
        return placed.size() == runs.size() ? List.copyOf(out) : List.of();
    }

    /**
     * Work out what making {@code wanted} of {@code target} would take.
     *
     * <h2>How it refuses rather than hangs</h2>
     *
     * Depth first, carrying the path it took. Anything already on that path is
     * a cycle and the plan is refused by name — which is a sentence a player
     * can act on, unlike a server that stopped responding. The depth and step
     * limits catch everything else.
     *
     * <p>Counts are {@code long} and rounded <b>up</b> at every step: making
     * one of something that needs three of a thing made four at a time takes
     * one run of that thing, not three quarters of one. Rounding down would
     * produce plans that come up short, which is the bug that makes an
     * autocrafter look haunted.
     *
     * @param steps  every recipe available, by what it makes
     * @param target what to make
     * @param wanted how many
     */
    public static Plan plan(Map<String, Step> steps, String target, long wanted) {
        Map<String, Long> runs = new HashMap<>();
        Map<String, Long> missing = new HashMap<>();
        String refused = walk(steps, target, wanted, new ArrayList<>(), runs, missing, 0,
                new int[]{MOST_WALKS});
        List<Job> jobs = refused == null ? order(steps, runs) : List.of();
        if (refused == null && jobs.isEmpty() && !runs.isEmpty()) {
            // Two mechanisms, deliberately independent: the walk refuses a
            // cycle by the path it is standing on, and the ordering refuses
            // one by never settling. If they ever disagree, the answer is not
            // to pick a winner — it is to refuse, and say so.
            refused = "the plan for " + target + " cannot be put in any order, which "
                    + "means there is a cycle in it the walk did not stand on";
        }
        return new Plan(runs, missing, jobs, refused);
    }

    private static String walk(Map<String, Step> steps, String want, long count,
                               List<String> path, Map<String, Long> runs,
                               Map<String, Long> missing, int depth, int[] budget) {
        if (count <= 0) {
            return null;
        }
        if (--budget[0] < 0) {
            return "working out how to make " + want + " took more than " + MOST_WALKS
                    + " steps, so it is being refused rather than waited for";
        }
        if (depth > MOST_DEPTH) {
            return "the plan for " + want + " is more than " + MOST_DEPTH + " deep";
        }
        Step step = steps.get(want);
        if (step == null) {
            missing.merge(want, count, Long::sum);
            return null;
        }
        if (path.contains(want)) {
            return want + " is made out of itself, by way of "
                    + String.join(", ", path);
        }
        if (runs.size() >= MOST_STEPS) {
            return "the plan needs more than " + MOST_STEPS + " different steps";
        }
        // Rounded up: a run is a run. Rounding down makes plans that come up
        // short, which is the bug that makes an autocrafter look haunted.
        long times = (count + step.makes() - 1) / step.makes();
        runs.merge(want, times, Long::sum);
        path.add(want);
        for (Map.Entry<String, Integer> need : step.needs().entrySet()) {
            String refused = walk(steps, need.getKey(), times * need.getValue(), path,
                    runs, missing, depth + 1, budget);
            if (refused != null) {
                path.remove(path.size() - 1);
                return refused;
            }
        }
        path.remove(path.size() - 1);
        return null;
    }

    /**
     * Whether a recipe set contains a cycle at all, without planning anything.
     *
     * <p>What a network checks once when its recipes change, so that a plan
     * refused for a cycle can say so instantly rather than after walking half
     * the tree.
     *
     * <h2>Only the cycle, not everything behind it</h2>
     *
     * Taking away everything that depends on nothing left — the standard
     * method — leaves the cycles <em>and</em> everything downstream of them,
     * because a recipe that needs a circular ingredient can never be settled
     * either. That is the right answer to "what cannot be planned" and the
     * wrong answer to "what is broken": a stick made of planks is a perfectly
     * good recipe, and telling a player to go and fix it while the real fault
     * is two steps away is worse than saying nothing.
     *
     * <p>So the leftovers are filtered down to the ones that can actually
     * reach themselves. Those are the recipes with the fault in them, and they
     * are the ones worth naming.
     */
    public static Set<String> cyclesIn(Map<String, Step> steps) {
        Map<String, Set<String>> waitingOn = new HashMap<>();
        for (Map.Entry<String, Step> entry : steps.entrySet()) {
            Set<String> inside = new HashSet<>();
            for (String need : entry.getValue().needs().keySet()) {
                if (steps.containsKey(need)) {
                    inside.add(need);
                }
            }
            waitingOn.put(entry.getKey(), inside);
        }
        Deque<String> settled = new ArrayDeque<>();
        for (Map.Entry<String, Set<String>> entry : waitingOn.entrySet()) {
            if (entry.getValue().isEmpty()) {
                settled.add(entry.getKey());
            }
        }
        Set<String> clear = new HashSet<>();
        while (!settled.isEmpty()) {
            String done = settled.poll();
            if (!clear.add(done)) {
                continue;
            }
            for (Map.Entry<String, Set<String>> entry : waitingOn.entrySet()) {
                if (entry.getValue().remove(done) && entry.getValue().isEmpty()) {
                    settled.add(entry.getKey());
                }
            }
        }
        Set<String> stuck = new HashSet<>(steps.keySet());
        stuck.removeAll(clear);
        Set<String> caught = new HashSet<>();
        for (String name : stuck) {
            if (reaches(steps, name, name)) {
                caught.add(name);
            }
        }
        return caught;
    }

    /**
     * Whether {@code from} needs {@code to}, however far down.
     *
     * Depth first with a visited set, which is what makes it safe to ask on a
     * recipe set that already contains cycles — the question being asked is
     * usually "does this thing need itself", and a walker that did not
     * remember where it had been would be the very bug it is looking for.
     */
    public static boolean reaches(Map<String, Step> steps, String from, String to) {
        Step start = steps.get(from);
        if (start == null) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(start.needs().keySet());
        while (!pending.isEmpty()) {
            String here = pending.poll();
            if (here.equals(to)) {
                return true;
            }
            if (!seen.add(here)) {
                continue;
            }
            Step step = steps.get(here);
            if (step != null) {
                pending.addAll(step.needs().keySet());
            }
        }
        return false;
    }

    // ==================================================================
    // The hands that do it
    // ==================================================================

    /**
     * What one assembler asks the grid for.
     *
     * <h2>Why autocrafting is a load and not a convenience</h2>
     *
     * Every mod this replaces makes autocrafting free once built, which turns
     * the end of a tech tree into a menu. Here it is a bank of machines with a
     * bill: sixteen assemblers is a kilowatt and change, which at Age II is a
     * turbine of its own and at Age III is a thing you plan around.
     *
     * <p>Constant given the machine, never a function of what the grid can
     * afford — the same rule this pack has now enforced in five modules,
     * because a machine that asked for less when the grid was short would
     * change what the grid could afford, which would change what it asked for.
     */
    public static final IndustryRules.Machine ASSEMBLER =
            new IndustryRules.Machine(96.0F, IndustryRules.Voltage.LOW);

    /**
     * How long one run of one recipe takes, at full power.
     *
     * Two seconds. Deliberately slower than a player crafting by hand, because
     * the thing being bought is <em>not having to be there</em>, and a machine
     * that also beat you at your own bench would make the bench pointless.
     */
    public static final int ASSEMBLE_TICKS = 40;

    /** The work one run of a recipe comes to, in Age II's units. */
    public static int assembleWork() {
        return IndustryRules.workFor(ASSEMBLE_TICKS);
    }

    /**
     * How long a job of this many runs takes at this share of the grid.
     *
     * Age II's model exactly, and on purpose: an assembler browning out looks
     * the same as a crusher browning out, so a player who has understood one
     * has understood the other. A starved assembler is slow and never stopped.
     */
    public static int assembleTicks(long runs, float share) {
        long work = Math.max(1L, runs) * assembleWork();
        int perTick = IndustryRules.workPerTick(share);
        long ticks = (work + perTick - 1) / perTick;
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }

    /**
     * What the core that does the planning asks the grid for.
     *
     * <p>Medium, and on purpose: the planning half of autocrafting wants a
     * buffered grid behind it, which is Age III. A player can build a wall of
     * drawers and a controller in Age II and index a factory with it; making
     * the factory build itself is the next age's privilege, and the voltage is
     * how the pack says so without a single gate or advancement.
     */
    public static final IndustryRules.Machine ASSEMBLY_CORE =
            new IndustryRules.Machine(256.0F, IndustryRules.Voltage.MEDIUM);

    /**
     * How many runs of one job to hand an assembler at a time.
     *
     * <h2>Why a job is not dispatched whole</h2>
     *
     * A request for a stack of something can come to hundreds of runs, and an
     * assembler handed all of them at once would be holding hundreds of
     * ingredients — out of the warehouse, out of reach of everything else, and
     * gone from the world if somebody mined the machine. So a job goes out in
     * batches small enough to sit in one set of slots, and what is not yet
     * dispatched stays in the drawers where a player can still use it.
     *
     * @param remaining  runs of this job still to do
     * @param mostPerRun the largest number of any one ingredient a single run
     *                   consumes
     */
    public static int batchSize(long remaining, int mostPerRun) {
        if (remaining <= 0) {
            return 0;
        }
        int perRun = Math.max(1, mostPerRun);
        int fits = Math.max(1, STACK / perRun);
        return (int) Math.min(remaining, fits);
    }

    /**
     * How many jobs one core will have in the air at once.
     *
     * <p>A wall rather than a gradient, for the same reason
     * {@link #MOST_DEVICES} is: "all your assemblers are busy" is a sentence a
     * player can act on by building another one. It is also what keeps a core
     * from emptying an entire warehouse into half-finished jobs the moment
     * somebody asks for a stack of something expensive.
     */
    public static final int MOST_JOBS = 16;

    /**
     * What a core with this many assemblers can have running.
     *
     * Never more than there are assemblers — a job with nowhere to be made is
     * a job that holds its ingredients hostage.
     */
    public static int jobsAtOnce(int assemblers) {
        return Math.max(0, Math.min(MOST_JOBS, assemblers));
    }

    /**
     * Whether a plan is small enough to be worth starting.
     *
     * <p>A plan is refused <em>before</em> any ingredient moves if it would
     * need more machine-runs than a core can get through in an hour of game
     * time at full power. Not a performance limit: a player who asks for a
     * stack of something forty steps deep should be told that is a factory
     * rather than a request, while their warehouse is still full.
     */
    public static boolean worthStarting(Plan plan, int assemblers) {
        if (!plan.possible() || plan.jobs().isEmpty()) {
            return false;
        }
        long most = (long) jobsAtOnce(assemblers) * (TICKS_PER_HOUR / ASSEMBLE_TICKS);
        return plan.totalRuns() <= Math.max(1L, most);
    }

    /** An hour of game time, which is what a plan is measured against. */
    public static final int TICKS_PER_HOUR = 72_000;

    // ==================================================================
    // Moving things, and never making them
    // ==================================================================

    /**
     * How much of {@code offered} a store with this much room will take.
     *
     * <p>The one piece of arithmetic in this file that a bug in would be
     * invisible and catastrophic: a store that took more than it had room for
     * would destroy the remainder, and one that reported taking more than it
     * did would duplicate it. Both have shipped in real storage mods.
     */
    public static int accepted(int offered, int room) {
        return Math.max(0, Math.min(Math.max(0, offered), Math.max(0, room)));
    }

    /**
     * What is left in the player's hand after a store took what it could.
     *
     * Written as its own function and checked against {@link #accepted} rather
     * than left to a subtraction at every call site, because "the leftover"
     * and "what was taken" adding up to what was offered is the whole of the
     * conservation property and it deserves one place to be wrong.
     */
    public static int leftOver(int offered, int room) {
        return Math.max(0, offered) - accepted(offered, room);
    }

    /**
     * Whether a store is allowed to destroy what will not fit.
     *
     * <p>False by default and true only for a drawer a player has explicitly
     * upgraded to void, by fitting it with a {@link #DRAWER_SEAL}. It is the
     * single exception to "items are never destroyed", it is opt-in, and it
     * is worth a named constant so that the harness can assert every other
     * path conserves.
     */
    public static boolean voids(boolean upgraded) {
        return upgraded;
    }

    // ==================================================================
    // What a player fits to a drawer
    // ==================================================================

    /**
     * The part that makes a drawer void: the pack's own void seal.
     *
     * <p>The same part and not a second one, because a player who has learned
     * what a seal does to a bag has then learned what it does to a drawer,
     * and one recipe is one thing to remember. The harness holds it to being
     * the pack's void part.
     */
    public static final String DRAWER_SEAL = "void_seal";

    /**
     * The key that locks a drawer to what it holds, and unlocks it again.
     *
     * <p>Never spent by turning. A lock a player pays for every time is a
     * lock nobody turns, and then a hopper re-types the drawer the first time
     * it runs dry.
     */
    public static final String DRAWER_KEY = "drawer_key";

    /**
     * Whether a drawer takes a seal: one, and only while it has none.
     *
     * <p>A second would do nothing, and a seal spent on nothing is eight
     * obsidian and an ender pearl gone; so it is refused and stays in the
     * hand.
     */
    public static boolean takesSeal(boolean voiding) {
        return !voiding;
    }

    /**
     * How many seals a drawer gives back, taken out or broken: the one it
     * took, or none.
     *
     * <p>The other half of {@link #takesSeal}, and the two together are why a
     * seal is never destroyed: fitting spends one, and whatever happens to
     * the drawer afterwards hands that one back.
     */
    public static int sealsIn(boolean voiding) {
        return voiding ? 1 : 0;
    }

    /**
     * Whether a slot keeps its kind: while it holds any of it, or while the
     * drawer is locked.
     *
     * <p>That is the whole of what a lock is. An unlocked drawer that runs dry
     * forgets what it held, so the next thing a hopper brings becomes its
     * kind; a locked one keeps the kind at nought and takes nothing new, so a
     * wall of drawers stays sorted however often it is emptied.
     */
    public static boolean keepsKind(boolean locked, int count) {
        return locked || count > 0;
    }

    /**
     * Whether a key locks a drawer that holds or keeps this many kinds.
     *
     * <p>Not an empty one. Locked with no kind in it, a drawer refuses
     * everything, and a drawer that refuses everything looks broken.
     * Unlocking is always allowed, and is not asked about here.
     */
    public static boolean mayLock(int kinds) {
        return kinds > 0;
    }
}
