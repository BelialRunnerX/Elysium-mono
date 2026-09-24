package com.elysium.almanac.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quest graph. The JDK and nothing else at all.
 *
 * <h2>What a quest book is for, and what it usually is instead</h2>
 *
 * A modpack's quest book exists for one reason: a player who has just
 * installed it has no idea what any of it is, what order to do it in, or which
 * of the four hundred new items is the one that unlocks the next thing. It is
 * a <em>direction</em>, not a reward scheme.
 *
 * <p>Which is why it is written as a graph and proved rather than typed out.
 * The two ways a quest book fails are both invisible to whoever wrote it:
 *
 * <ol>
 *   <li><b>A step that can never be reached.</b> A quest whose prerequisite
 *       nobody can complete, or which nothing leads to. The author sees it in
 *       their editor; the player never sees it at all.</li>
 *   <li><b>A step that is its own prerequisite.</b> Two quests each waiting on
 *       the other. Nothing in a book's format stops it and no play-test finds
 *       it, because the player simply concludes the chapter is not finished
 *       yet and stops looking.</li>
 * </ol>
 *
 * <p>Both are graph properties, both are checked over the whole book on every
 * build, and neither can be checked by reading it.
 *
 * <h2>Why the book names nothing it depends on</h2>
 *
 * Every task is a string — an item id, a block id, a dimension — looked up at
 * runtime. The almanac therefore depends on no module it describes: a chapter
 * about a module nobody has installed is a chapter that is not shown, rather
 * than a game that will not load.
 */
public final class AlmanacRules {

    private AlmanacRules() {
    }

    /** What a quest asks a player to do. */
    public enum Kind {

        /** Have this in your inventory, or have had it: the usual one. */
        HAVE,

        /** Put one down in the world, which is different from owning one. */
        PLACE,

        /** Stand in this world. */
        VISIT,

        /**
         * Kill one of these.
         *
         * <p>Separate from {@link #HAVE} on whatever it drops, because half
         * the point of pointing a player at a beast is that they have not met
         * one, and a quest asking for three bones tells them nothing about
         * where bones come from.
         */
        SLAY,

        /** Nothing: a page of instructions with a button on it. */
        READ
    }

    /**
     * One thing a quest asks for.
     *
     * @param kind  what sort of asking it is
     * @param what  an item id, a block id or a dimension id; empty for READ
     * @param count how many, for the kinds that count
     */
    public record Task(Kind kind, String what, int count) {

        public Task {
            if (kind != Kind.READ && what.isBlank()) {
                throw new IllegalArgumentException("a task that asks for nothing in "
                        + "particular cannot be completed by anything in particular");
            }
            if (count < 1) {
                throw new IllegalArgumentException("a task that wants none of something "
                        + "is already done");
            }
        }

        /** The usual one: have this many of that. */
        public static Task have(String item, int count) {
            return new Task(Kind.HAVE, item, count);
        }

        public static Task place(String block) {
            return new Task(Kind.PLACE, block, 1);
        }

        public static Task visit(String dimension) {
            return new Task(Kind.VISIT, dimension, 1);
        }

        public static Task slay(String entity, int count) {
            return new Task(Kind.SLAY, entity, count);
        }

        public static Task read() {
            return new Task(Kind.READ, "", 1);
        }
    }

    /**
     * One step.
     *
     * @param id      what it is called, which is also its translation key stem
     * @param chapter which chapter it belongs to
     * @param needs   the quests that must be done first
     * @param tasks   what it asks for; all of them, not any of them
     */
    public record Quest(String id, String chapter, List<String> needs, List<Task> tasks) {

        public Quest {
            if (id.isBlank()) {
                throw new IllegalArgumentException("a quest with no name cannot be "
                        + "referred to by anything");
            }
            if (tasks.isEmpty()) {
                throw new IllegalArgumentException("a quest that asks for nothing is a "
                        + "quest that is already done");
            }
            needs = List.copyOf(needs);
            tasks = List.copyOf(tasks);
        }
    }

    /**
     * A chapter: a heading, and the chapters a player should have read first.
     *
     * <h2>Why a chapter may follow more than one</h2>
     *
     * A pack of this size does not branch once and rejoin. Autocrafting needs
     * a warehouse to index <em>and</em> a grid that can hold a charge, and
     * those are two different chapters neither of which comes before the
     * other. With a single predecessor there is nowhere to put it: either the
     * chapter claims to follow the warehouse and then waits on the ascent, or
     * the reverse, and both are a book that lies about its own shape.
     *
     * <p>So a chapter names all of them and the ordering rule stays exactly as
     * strict as it was: a quest may only wait on quests in its own chapter or
     * in a chapter a player has, by the book's own account, already read.
     */
    public record Chapter(String id, List<String> after) {

        public Chapter {
            after = List.copyOf(after);
        }

        /** Whether this is where a player starts. */
        public boolean first() {
            return after.isEmpty();
        }
    }

    // ==================================================================
    // The book
    // ==================================================================

    public static final List<Chapter> CHAPTERS = List.of(
            new Chapter("first_light", List.of()),
            new Chapter("reconstruction", List.of("first_light")),
            new Chapter("warehouse", List.of("reconstruction")),
            new Chapter("garden", List.of("reconstruction")),
            new Chapter("ascent", List.of("reconstruction")),
            new Chapter("automation", List.of("warehouse", "ascent")),
            new Chapter("foundry", List.of("ascent")),
            new Chapter("frontier", List.of("ascent")),

            // Three that hang off the very beginning rather than off the
            // tech ladder, because none of them needs power and all three are
            // things a player wants on their first night rather than their
            // fiftieth. A quest book whose only spine is the machines is a
            // quest book that tells a player nothing about the half of the
            // pack they are standing in.
            new Chapter("the_dark", List.of("first_light")),
            new Chapter("the_homestead", List.of("first_light")),
            new Chapter("the_wilds", List.of("first_light")));

    private static Quest quest(String id, String chapter, List<String> needs,
                               Task... tasks) {
        return new Quest(id, chapter, needs, List.of(tasks));
    }

    private static final String KIN = "elysiumkinetics:";
    private static final String IND = "elysiumindustry:";
    private static final String ATO = "elysiumatomics:";
    private static final String LOG = "elysiumlogistics:";
    private static final String AGR = "elysiumagronomy:";
    private static final String FND = "elysiumfoundry:";
    private static final String TRA = "elysiumtransit:";
    private static final String ARC = "elysiumarcana:";
    private static final String ARS = "elysiumarsenal:";
    private static final String BST = "elysiumbestiary:";
    private static final String ATE = "elysiumatelier:";
    private static final String CMF = "elysiumcomforts:";
    private static final String WLD = "elysiumwilds:";

    /**
     * Every step, in one place.
     *
     * <p>Deliberately not a data file. A quest graph spread across forty JSON
     * files is a graph nobody can check, and the two ways this fails are both
     * graph properties. The data files are generated <em>from</em> this, so a
     * pack author still edits text rather than code.
     */
    public static final List<Quest> QUESTS = List.of(
            // --- First Light: rotation, which needs no power at all --------
            quest("welcome", "first_light", List.of(), Task.read()),
            quest("crank", "first_light", List.of("welcome"),
                    Task.have(KIN + "hand_crank", 1)),
            quest("shaft", "first_light", List.of("crank"),
                    Task.place(KIN + "timber_shaft")),
            quest("millstone", "first_light", List.of("shaft"),
                    Task.have(KIN + "millstone", 1)),
            quest("water_wheel", "first_light", List.of("shaft"),
                    Task.have(KIN + "water_wheel", 1)),
            quest("brass_shaft", "first_light", List.of("water_wheel"),
                    Task.have(KIN + "brass_shaft", 1)),
            quest("saw", "first_light", List.of("millstone"),
                    Task.have(KIN + "saw", 1)),

            // --- The Reconstruction: electricity ---------------------------
            quest("alternator", "reconstruction", List.of("brass_shaft"),
                    Task.have(IND + "alternator", 1)),
            quest("copper_wire", "reconstruction", List.of("alternator"),
                    Task.place(IND + "copper_wire")),
            quest("turbine", "reconstruction", List.of("copper_wire"),
                    Task.have(IND + "steam_turbine", 1)),
            quest("crusher", "reconstruction", List.of("turbine"),
                    Task.have(IND + "crusher", 1)),
            quest("rolling_mill", "reconstruction", List.of("turbine"),
                    Task.have(IND + "rolling_mill", 1)),
            quest("plate", "reconstruction", List.of("rolling_mill"),
                    Task.have(IND + "iron_plate", 8)),
            quest("arc_furnace", "reconstruction", List.of("plate"),
                    Task.have(IND + "arc_furnace", 1)),
            quest("steel", "reconstruction", List.of("arc_furnace"),
                    Task.have(IND + "steel_ingot", 4)),
            quest("transformer", "reconstruction", List.of("steel"),
                    Task.have(IND + "transformer", 1)),
            quest("separator", "reconstruction", List.of("transformer"),
                    Task.have(IND + "separator", 1)),
            quest("motor", "reconstruction", List.of("steel"),
                    Task.have(IND + "motor", 1)),
            quest("waystone", "reconstruction", List.of("copper_wire"),
                    Task.place(TRA + "waystone")),
            quest("road", "reconstruction", List.of("waystone"),
                    Task.have(TRA + "paving", 64)),
            quest("cart", "reconstruction", List.of("road"),
                    Task.have(TRA + "cart", 1)),

            // --- The Warehouse: storage ------------------------------------
            quest("drawer", "warehouse", List.of("welcome"),
                    Task.have(LOG + "timber_drawer", 1)),
            quest("steel_drawer", "warehouse", List.of("drawer", "steel"),
                    Task.have(LOG + "steel_drawer", 1)),
            quest("controller", "warehouse", List.of("steel_drawer"),
                    Task.place(LOG + "controller")),

            // --- The Garden: things that grow ------------------------------
            quest("pot", "garden", List.of("welcome"), Task.have(AGR + "pot", 1)),
            quest("seed", "garden", List.of("pot"), Task.have(AGR + "iron_seed", 1)),
            quest("accelerator", "garden", List.of("seed", "copper_wire"),
                    Task.have(AGR + "accelerator", 1)),
            quest("hive", "garden", List.of("pot"), Task.have(AGR + "hive", 1)),
            quest("rock", "garden", List.of("welcome"),
                    Task.have(AGR + "rock_generator", 1)),

            // --- The Ascent: keeping what you make --------------------------
            quest("cell", "ascent", List.of("separator"),
                    Task.have(ATO + "basic_cell", 1)),
            quest("solar", "ascent", List.of("cell"),
                    Task.have(ATO + "solar_panel", 4)),
            quest("centrifuge", "ascent", List.of("cell"),
                    Task.have(ATO + "centrifuge", 1)),
            quest("fuel", "ascent", List.of("centrifuge"),
                    Task.have(ATO + "enriched_uranium", 1)),
            quest("coolant", "ascent", List.of("cell"),
                    Task.have(ATO + "coolant_channel", 4)),
            quest("reactor", "ascent", List.of("fuel", "coolant"),
                    Task.place(ATO + "reactor_core")),
            quest("enricher", "ascent", List.of("reactor"),
                    Task.have(ATO + "enricher", 1)),
            quest("servo", "ascent", List.of("reactor"),
                    Task.have(ATO + "servo", 1)),

            // --- Automation: the warehouse builds things for you -----------
            quest("assembly_core", "automation", List.of("controller", "cell"),
                    Task.place(LOG + "assembly_core")),
            quest("assembler", "automation", List.of("assembly_core"),
                    Task.have(LOG + "assembler", 4)),
            quest("terminal", "automation", List.of("assembler"),
                    Task.place(LOG + "terminal")),

            // --- The Foundry: the material chain ---------------------------
            quest("smelter", "foundry", List.of("enricher"),
                    Task.have(FND + "alloy_smelter", 1)),
            quest("bronze", "foundry", List.of("smelter"),
                    Task.have(FND + "bronze_ingot", 4)),
            quest("invar", "foundry", List.of("bronze"),
                    Task.have(FND + "invar_ingot", 4)),
            quest("stainless", "foundry", List.of("invar"),
                    Task.have(FND + "stainless_ingot", 4)),
            quest("starsteel", "foundry", List.of("stainless"),
                    Task.have(FND + "starsteel_ingot", 1)),
            quest("casing", "foundry", List.of("starsteel"),
                    Task.have(FND + "tier_4_casing", 1)),

            quest("transmuter", "foundry", List.of("casing"),
                    Task.place(ARC + "transmuter")),

            // --- The Frontier: somewhere else entirely ---------------------
            quest("look_up", "frontier", List.of("cell"), Task.read()),
            quest("luna", "frontier", List.of("look_up"),
                    Task.visit("elysiumspace:luna")),

            // --- The Dark: something to hit things with, and the things ----
            quest("first_blade", "the_dark", List.of("welcome"),
                    Task.have(ARS + "stone_sabre", 1)),
            quest("a_reason_to_choose", "the_dark", List.of("first_blade"),
                    Task.have(ARS + "iron_spear", 1)),
            quest("the_long_answer", "the_dark", List.of("a_reason_to_choose"),
                    Task.have(ARS + "iron_greatsword", 1)),
            quest("the_narrow_answer", "the_dark", List.of("a_reason_to_choose"),
                    Task.have(ARS + "diamond_estoc", 1)),
            quest("the_heavy_answer", "the_dark", List.of("the_long_answer"),
                    Task.have(ARS + "diamond_warhammer", 1)),
            quest("shardling", "the_dark", List.of("first_blade"),
                    Task.slay(BST + "shardling", 1)),
            quest("the_three_families", "the_dark", List.of("shardling"),
                    Task.slay(BST + "husk_ox", 1), Task.slay(BST + "mourner", 1)),
            quest("worth_the_trouble", "the_dark", List.of("the_three_families"),
                    Task.slay(BST + "ripper", 1), Task.slay(BST + "keener", 1),
                    Task.slay(BST + "breaker", 1)),
            quest("the_hollow_lord", "the_dark",
                    List.of("worth_the_trouble", "the_heavy_answer"),
                    Task.slay(BST + "hollow_lord", 1)),
            quest("the_siege_beast", "the_dark", List.of("the_hollow_lord"),
                    Task.slay(BST + "siege_beast", 1)),
            quest("the_stalker", "the_dark", List.of("the_hollow_lord"),
                    Task.slay(BST + "stalker", 1)),
            quest("broad_tool", "the_dark", List.of("first_blade"),
                    Task.have(ARS + "iron_hammer", 1)),

            // --- The Homestead: somewhere to come back to ------------------
            quest("somewhere_to_sit", "the_homestead", List.of("welcome"),
                    Task.have(ATE + "oak_chair", 1)),
            quest("somewhere_to_put_things", "the_homestead",
                    List.of("somewhere_to_sit"), Task.have(ATE + "oak_shelf", 1)),
            quest("a_door_of_stone", "the_homestead", List.of("somewhere_to_sit"),
                    Task.place(ATE + "stone_door")),
            quest("a_wall_that_joins", "the_homestead", List.of("a_door_of_stone"),
                    Task.have(ATE + "deepslate_fence", 8),
                    Task.have(ATE + "deepslate_gate", 1)),
            quest("a_window", "the_homestead", List.of("a_door_of_stone"),
                    Task.have(ATE + "quartz_pane", 8)),
            quest("the_grave", "the_homestead", List.of("welcome"), Task.read()),
            quest("the_crate", "the_homestead", List.of("somewhere_to_put_things"),
                    Task.have(CMF + "trader_crate", 1)),

            // --- The Wilds: knowing where anything is ----------------------
            quest("the_compass", "the_wilds", List.of("welcome"),
                    Task.have(WLD + "biome_compass", 1)),
            quest("somewhere_else", "the_wilds", List.of("the_compass"),
                    Task.have(TRA + "waystone", 1)),
            quest("a_road", "the_wilds", List.of("somewhere_else"),
                    Task.have(TRA + "paving", 16)));

    private static final Map<String, Quest> BY_ID = new LinkedHashMap<>();

    static {
        for (Quest quest : QUESTS) {
            BY_ID.put(quest.id(), quest);
        }
    }

    public static Quest quest(String id) {
        return BY_ID.get(id);
    }

    /** Every quest in a chapter, in the order they were declared. */
    public static List<Quest> inChapter(String chapter) {
        List<Quest> found = new ArrayList<>();
        for (Quest quest : QUESTS) {
            if (quest.chapter().equals(chapter)) {
                found.add(quest);
            }
        }
        return found;
    }

    public static Chapter chapter(String id) {
        for (Chapter chapter : CHAPTERS) {
            if (chapter.id().equals(id)) {
                return chapter;
            }
        }
        return null;
    }

    // ==================================================================
    // The two properties
    // ==================================================================

    /**
     * Every quest that is, however far back, its own prerequisite.
     *
     * Empty, or the build stops. Nothing in a book's format prevents it and no
     * play-test finds it: a player simply concludes the chapter is not
     * finished yet and stops looking.
     */
    public static Set<String> cycles() {
        Set<String> caught = new HashSet<>();
        for (Quest quest : QUESTS) {
            if (waitsOn(quest.id(), quest.id())) {
                caught.add(quest.id());
            }
        }
        return caught;
    }

    /**
     * Whether {@code from} waits on {@code to}, however far back.
     *
     * With a memory, which is what makes it safe to ask on a book that might
     * already contain a cycle — the question being asked is usually "does this
     * wait on itself", and a walker that forgot where it had been would be the
     * very bug it is looking for.
     */
    public static boolean waitsOn(String from, String to) {
        Quest start = BY_ID.get(from);
        if (start == null) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(start.needs());
        while (!pending.isEmpty()) {
            String here = pending.poll();
            if (here.equals(to)) {
                return true;
            }
            if (!seen.add(here)) {
                continue;
            }
            Quest step = BY_ID.get(here);
            if (step != null) {
                pending.addAll(step.needs());
            }
        }
        return false;
    }

    /**
     * Every quest a player could never arrive at.
     *
     * One that waits on something that does not exist, or that is behind a
     * cycle. A quest nobody reaches is content the author can see and the
     * player cannot, which is the worse of the two failures because it looks
     * fine from every angle except the one that matters.
     */
    public static Set<String> unreachable() {
        Set<String> reached = new HashSet<>();
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Quest quest : QUESTS) {
                if (reached.contains(quest.id())) {
                    continue;
                }
                boolean ready = true;
                for (String need : quest.needs()) {
                    if (!reached.contains(need)) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    reached.add(quest.id());
                    moved = true;
                }
            }
        }
        Set<String> stuck = new HashSet<>(BY_ID.keySet());
        stuck.removeAll(reached);
        return stuck;
    }

    /** Every prerequisite named by some quest that is not itself a quest. */
    public static Set<String> danglingNeeds() {
        Set<String> missing = new HashSet<>();
        for (Quest quest : QUESTS) {
            for (String need : quest.needs()) {
                if (!BY_ID.containsKey(need)) {
                    missing.add(need);
                }
            }
        }
        return missing;
    }

    /**
     * How many steps deep a quest is: how long the longest chain behind it is.
     *
     * What the book sorts a chapter by, so a player reads it in the order they
     * can actually do it rather than in the order somebody typed it.
     */
    public static int depthOf(String id) {
        Quest quest = BY_ID.get(id);
        if (quest == null || quest.needs().isEmpty()) {
            return 0;
        }
        int deepest = 0;
        for (String need : quest.needs()) {
            deepest = Math.max(deepest, depthOf(need) + 1);
        }
        return deepest;
    }

    /** Which quests a player can start right now, given what they have done. */
    public static List<Quest> openTo(Set<String> done) {
        List<Quest> open = new ArrayList<>();
        for (Quest quest : QUESTS) {
            if (done.contains(quest.id())) {
                continue;
            }
            boolean ready = true;
            for (String need : quest.needs()) {
                if (!done.contains(need)) {
                    ready = false;
                    break;
                }
            }
            if (ready) {
                open.add(quest);
            }
        }
        return open;
    }

    /** Whether a chapter has anything in it a player could start. */
    public static boolean started(String chapter, Set<String> done) {
        for (Quest quest : inChapter(chapter)) {
            if (done.contains(quest.id())) {
                return true;
            }
            boolean ready = true;
            for (String need : quest.needs()) {
                if (!done.contains(need)) {
                    ready = false;
                    break;
                }
            }
            if (ready) {
                return true;
            }
        }
        return false;
    }

    /** How far through a chapter a player is, from nothing to one. */
    public static float through(String chapter, Set<String> done) {
        List<Quest> quests = inChapter(chapter);
        if (quests.isEmpty()) {
            return 0.0F;
        }
        int finished = 0;
        for (Quest quest : quests) {
            if (done.contains(quest.id())) {
                finished++;
            }
        }
        return (float) finished / quests.size();
    }
}
