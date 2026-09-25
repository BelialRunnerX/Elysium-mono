package com.elysium.foundry.rules;

import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.rules.IndustryRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The material chain, in numbers. The JDK and the three ages below it.
 *
 * <h2>What the fourth age is, and what it deliberately is not</h2>
 *
 * It adds <b>no new physics</b>. Age I is rotation, Age II is a divided grid,
 * Age III is a buffered one; there is no fourth mechanism and this age does not
 * invent one. What it adds is the thing those three were built to pay for: a
 * material chain deep enough that a factory is a <em>supply</em> problem rather
 * than a crafting problem.
 *
 * <h2>The two properties a material chain has to have</h2>
 *
 * A chain like this is the part of a tech pack most likely to be quietly
 * broken, because nobody can hold four hundred recipes in their head:
 *
 * <ol>
 *   <li><b>No cycles.</b> If steel is made from iron and iron from steel, then
 *       either nothing can be made or — far worse — a loop exists that turns
 *       one unit into two, and the whole economy leaks. Every pack that has
 *       shipped a duplication bug shipped a cycle.</li>
 *   <li><b>No matter out of nothing.</b> Every step must cost at least as much
 *       material as it gives back, measured in one unit everything is priced
 *       in. The only exception in the whole pack is ore processing, which
 *       multiplies by a number three ages have already agreed on.</li>
 * </ol>
 *
 * Both are proved by execution over the entire declared chain, so a recipe
 * added years from now either satisfies them or stops the build.
 */
public final class FoundryRules {

    private FoundryRules() {
    }

    // ==================================================================
    // What a material is worth
    // ==================================================================

    /**
     * The unit everything is priced in.
     *
     * <p>One ingot's worth, divided into ninths so that a nugget is a whole
     * number — which is what makes "a plate is one ingot" and "a gear is four"
     * expressible without fractions, and what lets every recipe in this file
     * be checked with integer arithmetic instead of floating point.
     */
    public static final int UNIT = 9;

    /** What a shape of metal is worth, and what it takes to make one. */
    public enum Form {

        /** A ninth. The smallest thing worth having. */
        NUGGET(1),

        /** The unit itself. */
        INGOT(UNIT),

        /** Rolled flat. What every machine casing is skinned in. */
        PLATE(UNIT),

        /**
         * An ingot drawn out. Two of them make a shaft.
         *
         * One ingot each rather than the half-ingot some packs use, because
         * every number in this file is an integer count of ninths and a rod
         * worth four and a half of them would be the one recipe that has to be
         * checked in floating point.
         */
        ROD(UNIT),

        /** Cut with teeth. The expensive shape, and the one machines want. */
        GEAR(UNIT * 4),

        /** Nine ingots, for storage and for the things that need a block. */
        BLOCK(UNIT * 9);

        private final int worth;

        Form(int worth) {
            this.worth = worth;
        }

        /** What one of these is worth, in units. */
        public int worth() {
            return worth;
        }
    }

    /**
     * A material: a name, how hard it is to work, and what it is made of.
     *
     * @param name    what it is called
     * @param tier    how far up the chain it sits; nothing may be made of
     *                something above it
     * @param madeOf  the materials one unit of it takes, by name to units, or
     *                empty for something the ground gives you
     */
    public record Material(String name, int tier, Map<String, Integer> madeOf) {

        public Material {
            if (tier < 0) {
                throw new IllegalArgumentException("a material below the first tier is "
                        + "not a material");
            }
            madeOf = Map.copyOf(madeOf);
        }

        /** Whether this is something a player digs up rather than makes. */
        public boolean mined() {
            return madeOf.isEmpty();
        }

        /** What one unit of it costs, in units of everything it is made of. */
        public int costsInUnits() {
            int total = 0;
            for (int part : madeOf.values()) {
                total += part;
            }
            return total;
        }
    }

    /** How many tiers of material there are. */
    public static final int TIERS = 4;

    private static Material mined(String name, int tier) {
        return new Material(name, tier, Map.of());
    }

    private static Material alloy(String name, int tier, Object... parts) {
        Map<String, Integer> madeOf = new LinkedHashMap<>();
        for (int index = 0; index < parts.length; index += 2) {
            madeOf.put((String) parts[index], (Integer) parts[index + 1]);
        }
        return new Material(name, tier, madeOf);
    }

    /**
     * The whole chain, in one place.
     *
     * <p>Declared here rather than in data files for the same reason the ore
     * ladder is: it is the thing every other module's recipes are priced
     * against, and a graph spread across four hundred JSON files is a graph
     * nobody can check. The data files are generated <em>from</em> this.
     */
    public static final List<Material> MATERIALS = List.of(
            // Tier 0 — the ground gives them to you. Every one of these is a
            // family elysium-ores already generates, because an ore that
            // drops nothing is the state this pack is left in once the mods
            // that used to supply them are retired.
            mined("iron", 0),
            mined("copper", 0),
            mined("gold", 0),
            mined("tin", 0),
            mined("zinc", 0),
            mined("nickel", 0),
            mined("silver", 0),
            mined("lead", 0),
            mined("aluminum", 0),
            mined("osmium", 0),
            mined("platinum", 0),
            mined("lithium", 0),
            mined("crimson_iron", 0),
            mined("azure_silver", 0),
            mined("uranium", 0),
            mined("coal_dust", 0),
            mined("redstone_dust", 0),

            // Five families elysium-ores generates that nothing in the pack
            // supplied. They come in one shape each and go no further up the
            // chain; they are here because an ore that drops nothing is the
            // state the pack is left in otherwise.
            mined("bort", 0),
            mined("certus_quartz", 0),
            mined("fluorite", 0),
            mined("rime_shard", 0),
            mined("uraninite", 0),

            // Tier 1 — two things from the ground, melted together. Every one
            // of these is a tag elysium-ores declares and nothing installed
            // fills once the old jars go.
            alloy("bronze", 1, "copper", 3 * UNIT, "tin", UNIT),
            alloy("brass", 1, "copper", 3 * UNIT, "zinc", UNIT),
            alloy("steel", 1, "iron", UNIT, "coal_dust", UNIT),
            alloy("cast_iron", 1, "iron", 4 * UNIT, "coal_dust", UNIT),
            alloy("electrum", 1, "gold", UNIT, "silver", UNIT),
            alloy("invar", 1, "iron", 2 * UNIT, "nickel", UNIT),
            alloy("constantan", 1, "copper", UNIT, "nickel", UNIT),

            // Tier 2 — an alloy of an alloy, which is where a furnace stops
            // being enough and the age starts needing its own machines.
            alloy("stainless", 2, "steel", 3 * UNIT, "nickel", UNIT),
            alloy("signal_alloy", 2, "electrum", UNIT, "redstone_dust", 2 * UNIT),
            alloy("hardened_bronze", 2, "bronze", 2 * UNIT, "invar", UNIT),

            // Tier 3 — the top, and each wants something the third age makes.
            alloy("void_steel", 3, "stainless", UNIT, "uranium", UNIT),
            alloy("starsteel", 3, "stainless", 2 * UNIT, "platinum", UNIT),

            // The Cordon's (docs/SPEC_FOUNDRY.md §4; elysium-ores'
            // CordonMaterials is the roster). Two dug metals, six gems dug
            // whole, six things a creature or a ruin leaves — roots, because
            // they cannot be made — and seven alloys, each of two things.
            // elysium-ores ships the ingots; this age prices them and ships
            // the worked forms.
            mined("cobalt", 0),
            mined("pearlium", 0),
            mined("jade", 0),
            mined("pyratite", 0),
            mined("amber", 0),
            mined("ruby", 0),
            mined("sapphire", 0),
            mined("null_amethyst", 0),
            mined("ember_slag", 0),
            mined("bio_culture", 0),
            mined("pressure_shell", 0),
            mined("blight_crystal", 0),
            mined("null_shard", 0),
            mined("relic_shard", 0),
            alloy("nightgold", 1, "gold", UNIT, "cobalt", UNIT),
            alloy("relicsteel", 1, "relic_shard", 4 * UNIT),
            alloy("slagsteel", 2, "cobalt", UNIT, "ember_slag", UNIT),
            alloy("biolith", 2, "cobalt", UNIT, "bio_culture", UNIT),
            alloy("bathysteel", 2, "cobalt", UNIT, "pressure_shell", UNIT),
            alloy("hemalite", 3, "nightgold", UNIT, "blight_crystal", UNIT),
            alloy("nullsteel", 3, "nightgold", UNIT, "null_shard", UNIT));

    private static final Map<String, Material> BY_NAME = new HashMap<>();

    static {
        for (Material material : MATERIALS) {
            BY_NAME.put(material.name(), material);
        }
    }

    /** The material of that name, or null. */
    public static Material material(String name) {
        return BY_NAME.get(name);
    }

    /** Every material there is, by name. */
    public static Map<String, Material> everything() {
        return Map.copyOf(BY_NAME);
    }

    // ==================================================================
    // No cycles
    // ==================================================================

    /**
     * Every material that is, however indirectly, made out of itself.
     *
     * <p>Empty, or the build stops. A cycle in a material chain is either a
     * thing nobody can ever make, or — if any step of it multiplies — a loop
     * that turns one unit into two and leaks the whole economy. Every pack
     * that has shipped a duplication bug shipped one of these.
     */
    public static Set<String> cycles() {
        Set<String> caught = new HashSet<>();
        for (Material material : MATERIALS) {
            if (needs(material.name(), material.name())) {
                caught.add(material.name());
            }
        }
        return caught;
    }

    /**
     * Whether {@code from} is made of {@code to}, however far down.
     *
     * Breadth first with a memory, which is what makes it safe to ask on a
     * chain that might already contain a cycle — the question being asked is
     * usually "does this need itself", and a walker without a memory would be
     * the very bug it is looking for.
     */
    public static boolean needs(String from, String to) {
        Material start = BY_NAME.get(from);
        if (start == null) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(start.madeOf().keySet());
        while (!pending.isEmpty()) {
            String here = pending.poll();
            if (here.equals(to)) {
                return true;
            }
            if (!seen.add(here)) {
                continue;
            }
            Material step = BY_NAME.get(here);
            if (step != null) {
                pending.addAll(step.madeOf().keySet());
            }
        }
        return false;
    }

    /** Everything a material is ultimately dug out of the ground as. */
    public static Set<String> rootsOf(String name) {
        Set<String> roots = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(name);
        while (!pending.isEmpty()) {
            String here = pending.poll();
            if (!seen.add(here)) {
                continue;
            }
            Material step = BY_NAME.get(here);
            if (step == null || step.mined()) {
                roots.add(here);
                continue;
            }
            pending.addAll(step.madeOf().keySet());
        }
        return roots;
    }

    // ==================================================================
    // No matter out of nothing
    // ==================================================================

    /**
     * What one unit of a material is ultimately worth, in units of the things
     * it is dug out of the ground as.
     *
     * <p>The number that makes the second property checkable. An alloy that
     * cost less than the sum of its parts would be a machine that makes matter
     * — and one that cost a great deal more would be a recipe nobody uses, so
     * the harness checks both ends.
     */
    public static int deepCost(String name) {
        Material material = BY_NAME.get(name);
        if (material == null || material.mined()) {
            return UNIT;
        }
        int total = 0;
        for (Map.Entry<String, Integer> part : material.madeOf().entrySet()) {
            total += part.getValue() * deepCost(part.getKey()) / UNIT;
        }
        return total;
    }

    /**
     * How much of a material one ore gives, all told.
     *
     * The ore ladder, and this age does not extend it: Age I gives you the
     * metal the ground had, Age II doubles it, Age III triples it, and the
     * fourth age is about what you make out of the metal rather than how much
     * of it there is. Written against Age III's number so the two cannot
     * drift.
     */
    public static int fromOneOre() {
        return AtomicRules.ENRICHER_MULTIPLIER * UNIT;
    }

    // ==================================================================
    // Machines, and what they are made of
    // ==================================================================

    /**
     * A casing: what a machine of a given tier is built out of, and what it
     * costs to run.
     *
     * @param tier      which rung
     * @param madeOf    the material its plates are
     * @param draw      what a machine in it draws from the grid
     */
    public record Casing(int tier, String madeOf, IndustryRules.Machine draw) {
    }

    public static final List<Casing> CASINGS = List.of(
            new Casing(1, "steel",
                    new IndustryRules.Machine(64.0F, IndustryRules.Voltage.LOW)),
            new Casing(2, "invar",
                    new IndustryRules.Machine(256.0F, IndustryRules.Voltage.MEDIUM)),
            new Casing(3, "stainless",
                    new IndustryRules.Machine(1024.0F, IndustryRules.Voltage.HIGH)),
            new Casing(4, "starsteel",
                    new IndustryRules.Machine(4096.0F, IndustryRules.Voltage.EXTREME)));

    /** The casing of a given tier, or null. */
    public static Casing casing(int tier) {
        for (Casing casing : CASINGS) {
            if (casing.tier() == tier) {
                return casing;
            }
        }
        return null;
    }

    /**
     * Which tier of casing a material may be used in.
     *
     * A machine may be made of anything at or below its own rung, so a player
     * is never stopped from over-building — but a casing made of something
     * above it would be a tier that skips the one under it, and a ladder with
     * a skippable rung has one fewer rung than it says.
     */
    public static boolean fitsIn(String material, int tier) {
        Material made = BY_NAME.get(material);
        return made != null && made.tier() <= Math.max(0, tier);
    }

    // ==================================================================
    // Recovery: what the fourth age alone gets back (docs/SPEC_FOUNDRY.md §3)
    // ==================================================================

    /**
     * How much of an ore's companion the refinery gets back per ore, in
     * nuggets. A third of a unit: the second and last exception to "no matter
     * out of nothing", bounded here and checked to stay under a unit.
     */
    public static final int RECOVERY_NUGGETS = 3;

    /** How long a refinery takes over one ore at a full share. */
    public static final int REFINE_TICKS = 200;

    /** The casing a refinery is built to; its draw is the refinery's. */
    public static final int REFINERY_TIER = 3;

    /**
     * Each dug metal's companion: the metal the rock held beside it, which
     * Ages I to III throw away. One each, never itself, always a metal the
     * chain has a nugget of. Gems, dusts and uraninite have none.
     */
    public static final Map<String, String> COMPANIONS = companions();

    private static Map<String, String> companions() {
        Map<String, String> made = new LinkedHashMap<>();
        made.put("iron", "nickel");
        made.put("copper", "gold");
        made.put("gold", "silver");
        made.put("tin", "zinc");
        made.put("zinc", "tin");
        made.put("nickel", "platinum");
        made.put("silver", "lead");
        made.put("lead", "silver");
        made.put("aluminum", "lithium");
        made.put("lithium", "aluminum");
        made.put("osmium", "platinum");
        made.put("platinum", "osmium");
        made.put("uranium", "lead");
        made.put("crimson_iron", "iron");
        made.put("azure_silver", "silver");
        made.put("cobalt", "nickel");
        made.put("pearlium", "silver");
        return Map.copyOf(made);
    }

    /** The companion of a dug metal, or null for something with none. */
    public static String companionOf(String name) {
        return COMPANIONS.get(name);
    }

    /** Whether the refinery takes this ore at all. */
    public static boolean refinable(String name) {
        Material material = BY_NAME.get(name);
        return material != null && material.mined() && COMPANIONS.containsKey(name);
    }

    /** What one ore recovers, in units: under one, always. */
    public static int recoveredPerOre() {
        return RECOVERY_NUGGETS * Form.NUGGET.worth();
    }

    /** What a refinery asks the grid for: the third casing's draw. */
    public static IndustryRules.Machine refineryDraw() {
        return casing(REFINERY_TIER).draw();
    }

    /** Every material of a given tier, in the order they were declared. */
    public static List<Material> atTier(int tier) {
        List<Material> found = new ArrayList<>();
        for (Material material : MATERIALS) {
            if (material.tier() == tier) {
                found.add(material);
            }
        }
        return found;
    }
}
