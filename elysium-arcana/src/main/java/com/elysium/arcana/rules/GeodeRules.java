package com.elysium.arcana.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Crystals that grow out of a wall, for every material this pack can supply.
 *
 * <h2>The same lesson the bees taught, applied before it bit</h2>
 *
 * The mod this replaces ships twenty-nine materials' worth of geodes, and the
 * list is the union of every mod it has ever supported: allthemodium, black
 * quartz, a dozen metals nothing here makes. In this pack most of those are
 * blocks with no way to obtain them and no recipe that mentions them.
 *
 * <p>So the list here is the materials the ground in <em>this</em> world
 * actually gives you — twenty the foundry mines and ten the game digs up —
 * and {@code validate.py} reads the foundry's own mined list to check it.
 *
 * <h2>What a geode is</h2>
 *
 * Seven blocks per material and two items, and the seven are not a taste: they
 * are the game's own amethyst chain. A budding block grows a small bud, which
 * becomes a medium, which becomes a large, which becomes a cluster you break
 * for shards. Leave a step out and the chain stops somewhere a player is
 * standing and waiting, which is the failure this file is written to refuse.
 */
public final class GeodeRules {

    private GeodeRules() {
    }

    /**
     * The solid block of the material, for storage.
     *
     * <p>One way, as the game's amethyst block is: four shards make one and
     * nothing makes four back. A found geode's wall is some 380 of these, and
     * a way back would make every geode 1,500 shards on the spot.
     */
    public static final String BLOCK = "block";

    /** The block buds grow out of. */
    public static final String BUDDING = "budding";

    /** What a large bud becomes, and what you break for shards. */
    public static final String CLUSTER = "cluster";

    /** The three sizes a bud passes through. */
    public static final String SMALL = "small_bud";
    public static final String MEDIUM = "medium_bud";
    public static final String LARGE = "large_bud";

    /** The material as glass you can see through. */
    public static final String GLASS = "tinted_glass";

    /** Every kind of block a material comes in. */
    public static final List<String> KINDS =
            List.of(BLOCK, BUDDING, SMALL, MEDIUM, LARGE, CLUSTER, GLASS);

    /**
     * The growth chain, in order.
     *
     * <p>Each grows into the next; the last is what you break. A chain with a
     * step missing stops somewhere a player is standing and waiting, and
     * nothing about the world tells them it has.
     */
    public static final List<String> CHAIN = List.of(SMALL, MEDIUM, LARGE, CLUSTER);

    /** The two items a material comes in. */
    public static final List<String> ITEMS = List.of("shard", "spyglass");

    /**
     * The kinds a pickaxe is for: all six the game's own amethyst comes in.
     *
     * <p>The block and the budding block are built on the game's amethyst
     * block, which asks for the right tool. For as long as nothing put them
     * in a tool's tag no tool was right, so each took seven seconds to break
     * and dropped nothing, whatever its loot table said. The crystals ask for
     * no tool and are here for the speed, as the game's own are.
     */
    public static final List<String> PICKAXE =
            List.of(BLOCK, BUDDING, SMALL, MEDIUM, LARGE, CLUSTER);

    /**
     * And the kind no tool is for. Glass breaks at a touch and asks for no
     * tool, and the game's own tinted glass is in no tool's tag.
     */
    public static final List<String> BY_HAND = List.of(GLASS);

    /**
     * Materials the foundry says come out of the ground.
     *
     * <p>Checked against the foundry's own source by {@code validate.py}: a
     * geode for a metal nothing mines is a wall of blocks nobody can reach.
     */
    public static final List<String> MINED = List.of(
            "iron", "copper", "gold", "tin", "zinc", "nickel", "silver", "lead",
            "aluminum", "osmium", "platinum", "lithium", "crimson_iron",
            "azure_silver", "uranium", "bort", "certus_quartz", "fluorite",
            "rime_shard", "uraninite");

    /** Materials the game itself digs up. */
    public static final List<String> THE_GAMES_OWN = List.of(
            "coal", "redstone", "lapis", "quartz", "amethyst", "diamond",
            "emerald", "ancient_debris", "glowstone", "obsidian");

    /** Every material there is a geode of. */
    public static List<String> materials() {
        List<String> all = new ArrayList<>(MINED);
        all.addAll(THE_GAMES_OWN);
        return List.copyOf(all);
    }

    /** One block: a material in one of the seven shapes. */
    public record Geode(String material, String kind) {

        public Geode {
            Objects.requireNonNull(material, "a geode of nothing");
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("no such kind of geode: " + kind);
            }
        }

        /**
         * The block id.
         *
         * <p>The budding block reads the other way round — {@code
         * budding_amethyst}, not {@code amethyst_budding} — because that is
         * how the game names its own and a player should not have to learn a
         * second convention for the same idea.
         */
        public String id() {
            return BUDDING.equals(kind) ? BUDDING + "_" + material
                    : material + "_" + kind;
        }

        /** Whether this one is a step in the growth chain. */
        public boolean grows() {
            return CHAIN.contains(kind);
        }

        /** What this one grows into, or empty if it is the last step. */
        public String next() {
            int at = CHAIN.indexOf(kind);
            return at < 0 || at + 1 >= CHAIN.size() ? "" : CHAIN.get(at + 1);
        }
    }

    /** Every block there is. */
    public static List<Geode> catalogue() {
        List<Geode> made = new ArrayList<>();
        for (String material : materials()) {
            for (String kind : KINDS) {
                made.add(new Geode(material, kind));
            }
        }
        return List.copyOf(made);
    }

    /** Every item there is, as material-and-kind pairs. */
    public static List<String> items() {
        List<String> made = new ArrayList<>();
        for (String material : materials()) {
            for (String kind : ITEMS) {
                made.add(material + "_" + kind);
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // Whether the catalogue hangs together
    // ==================================================================

    /** Every material missing one of the seven shapes. */
    public static List<String> halfMade() {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>();
        for (Geode geode : catalogue()) {
            have.add(geode.material() + "/" + geode.kind());
        }
        for (String material : materials()) {
            for (String kind : KINDS) {
                if (!have.contains(material + "/" + kind)) {
                    bad.add(material + " has no " + kind);
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every material whose growth chain stops somewhere. */
    public static List<String> brokenChain() {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>();
        for (Geode geode : catalogue()) {
            have.add(geode.material() + "/" + geode.kind());
        }
        for (String material : materials()) {
            if (!have.contains(material + "/" + BUDDING)) {
                bad.add(material + " has buds and nothing to grow them from");
            }
            for (String step : CHAIN) {
                if (!have.contains(material + "/" + step)) {
                    bad.add(material + "'s chain stops before " + step
                            + ", so a player waits at a bud that never changes");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every block id that another block also answers to. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Geode geode : catalogue()) {
            if (!seen.add(geode.id())) {
                twice.add(geode.id());
            }
        }
        return List.copyOf(twice);
    }

    /** Every material named twice across the two lists. */
    public static List<String> namedTwice() {
        Set<String> mined = new TreeSet<>(MINED);
        List<String> both = new ArrayList<>();
        for (String material : THE_GAMES_OWN) {
            if (mined.contains(material)) {
                both.add(material + " is listed as both mined here and the game's own");
            }
        }
        return List.copyOf(both);
    }

    /** Every step of the chain that does not lead anywhere. */
    public static List<String> deadSteps() {
        List<String> bad = new ArrayList<>();
        for (int step = 0; step < CHAIN.size() - 1; step++) {
            Geode one = new Geode(materials().get(0), CHAIN.get(step));
            if (!CHAIN.get(step + 1).equals(one.next())) {
                bad.add(CHAIN.get(step) + " does not grow into " + CHAIN.get(step + 1));
            }
        }
        Geode last = new Geode(materials().get(0), CHAIN.get(CHAIN.size() - 1));
        if (!last.next().isEmpty()) {
            bad.add("the last step of the chain grows into " + last.next()
                    + ", so the chain has no end");
        }
        return List.copyOf(bad);
    }

    /**
     * Every kind nothing says how to mine, or says two things about.
     *
     * <p>A kind in neither list is mined at a fist's pace by every tool, and
     * if it asks for the right tool it drops nothing at all. A kind in both
     * is in the pickaxe's tag and on the list of things no tool is for.
     */
    public static List<String> untooled(List<String> kinds, List<String> pickaxe,
                                        List<String> byHand) {
        List<String> bad = new ArrayList<>();
        for (String kind : kinds) {
            boolean mined = pickaxe.contains(kind);
            boolean touched = byHand.contains(kind);
            if (!mined && !touched) {
                bad.add(kind + " is in no tool's tag and not broken by hand either, "
                        + "so nothing says what mines it");
            }
            if (mined && touched) {
                bad.add(kind + " is both mined by a pickaxe and broken by hand, so "
                        + "the tag says one thing and the exemption another");
            }
        }
        List<String> decided = new ArrayList<>(pickaxe);
        decided.addAll(byHand);
        for (String kind : decided) {
            if (!kinds.contains(kind)) {
                bad.add(kind + " is given a tool and is not a kind of geode block");
            }
        }
        return List.copyOf(bad);
    }
}
