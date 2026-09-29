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
        READ,

        /** Make one of these at a bench: the recipe, not the having. */
        CRAFT,

        /** Break this many of these: mining and digging are the same verb here. */
        BREAK,

        /** Right-click one: open the machine, pull the lever, look at the readout. */
        USE,

        /** Eat this many of these. */
        EAT,

        /** Take this many of these out of a furnace. */
        SMELT,

        /** Reach this character level. */
        LEVEL,

        /** Hold this many nodes on the Ledger Web. */
        WEB;

        /**
         * Whether the kind is answered by a running count of events rather than
         * by looking at the player. A counted task is credited from the first
         * time it happens, whether or not the quest was open, so a player who
         * has already broken forty stone is not asked for forty more.
         */
        public boolean counted() {
            return this == PLACE || this == SLAY || this == BREAK || this == CRAFT
                    || this == USE || this == EAT || this == SMELT;
        }
    }

    /**
     * One thing a quest asks for.
     *
     * @param kind  what sort of asking it is
     * @param what  an item id, a block id, an entity id or a dimension id, or
     *              a tag as {@code #namespace:path} where a tag can stand in
     *              (anything but VISIT, LEVEL and WEB); empty for READ, LEVEL
     *              and WEB, whose subject is the player
     * @param count how many, for the kinds that count; the level or the node
     *              count for LEVEL and WEB
     */
    public record Task(Kind kind, String what, int count) {

        /** Whether the subject is a tag rather than one id. */
        public boolean tag() {
            return what.startsWith("#");
        }

        /** The id or tag id without the tag mark. */
        public String subject() {
            return tag() ? what.substring(1) : what;
        }

        public Task {
            boolean aboutThePlayer = kind == Kind.READ || kind == Kind.LEVEL || kind == Kind.WEB;
            if (!aboutThePlayer && what.isBlank()) {
                throw new IllegalArgumentException("a task that asks for nothing in "
                        + "particular cannot be completed by anything in particular");
            }
            if (what.startsWith("#") && (kind == Kind.VISIT || aboutThePlayer)) {
                throw new IllegalArgumentException("a tag cannot stand in for a world or "
                        + "for the player");
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

        /** Place this many: a wall is not one block. Counted from the first. */
        public static Task place(String block, int count) {
            return new Task(Kind.PLACE, block, count);
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

        public static Task craft(String item, int count) {
            return new Task(Kind.CRAFT, item, count);
        }

        /** Break this many of a block; "mine" because "break" is a word Java keeps. */
        public static Task mine(String block, int count) {
            return new Task(Kind.BREAK, block, count);
        }

        public static Task use(String block) {
            return new Task(Kind.USE, block, 1);
        }

        public static Task eat(String item, int count) {
            return new Task(Kind.EAT, item, count);
        }

        public static Task smelt(String item, int count) {
            return new Task(Kind.SMELT, item, count);
        }

        public static Task level(int level) {
            return new Task(Kind.LEVEL, "", level);
        }

        public static Task web(int nodes) {
            return new Task(Kind.WEB, "", nodes);
        }
    }

    // ==================================================================
    // Rewards
    // ==================================================================

    /** Something handed over: an item id and how many. */
    public record Give(String item, int count) {

        public Give {
            if (!item.contains(":")) {
                throw new IllegalArgumentException("a reward that is not a namespaced id "
                        + "is a reward nothing can hand over");
            }
            if (count < 1) {
                throw new IllegalArgumentException("a reward of none of something");
            }
        }
    }

    /**
     * What finishing a quest pays: character experience, and perhaps things.
     *
     * <h2>Why experience</h2>
     *
     * The character level is the one currency every other system already
     * reads — the stat points, the Ledger Web's points — so a book that pays
     * in it is a book that moves the character on rather than a book that
     * fills a chest. The items are the flourish: a stack of the thing the next
     * step needs, so a reward is also a direction.
     *
     * <p>Claimed in the book, not dropped on the floor, so a player who finished
     * a step by accident in a cave is not showered with iron there.
     */
    public record Reward(int xp, List<Give> gives) {

        public Reward {
            if (xp < 1) {
                throw new IllegalArgumentException("a quest that pays nothing is a "
                        + "checklist entry, and the reason the book has rewards at all "
                        + "is to not be one");
            }
            gives = List.copyOf(gives);
        }

        public static Reward of(int xp, Give... gives) {
            return new Reward(xp, List.of(gives));
        }

        /** Whether it hands anything over besides the experience. */
        public boolean things() {
            return !gives.isEmpty();
        }
    }

    /** Experience for a step with nothing behind it. */
    public static final int XP_BASE = 20;

    /** More for every step of chain behind a quest: the deeper, the dearer. */
    public static final int XP_PER_DEPTH = 10;

    /** More for every task beyond the first a quest asks for at once. */
    public static final int XP_PER_TASK = 10;

    /** The most a step may pay, however deep and however many-headed. */
    public static final int XP_MOST = 320;

    /**
     * The level curve, copied from the library so the harness can add the book
     * up without a game. The validator holds these against the library's own
     * numbers; if they drift, the build says so.
     */
    public static final int LEVEL_XP_BASE = 100;
    public static final int LEVEL_XP_PER_LEVEL = 10;

    /** Experience from one level to the next, the library's rule. */
    public static int xpToNext(int level) {
        return LEVEL_XP_BASE + LEVEL_XP_PER_LEVEL * Math.max(0, level);
    }

    /** The level a character starting at one reaches on this much experience. */
    public static int levelAfter(long xp) {
        int level = 1;
        long left = xp;
        while (left >= xpToNext(level) && level < 1000) {
            left -= xpToNext(level);
            level++;
        }
        return level;
    }

    /**
     * The rewards declared by hand: the ones that hand something over. Every
     * other quest pays by the rule below, so a new quest is never a quest that
     * pays nothing because nobody remembered to say.
     */
    public static final Map<String, Reward> REWARDS = Map.ofEntries(
            Map.entry("welcome", Reward.of(XP_BASE, new Give("minecraft:torch", 16))),
            Map.entry("crank", Reward.of(40, new Give("minecraft:oak_log", 8))),
            Map.entry("millstone", Reward.of(80, new Give("minecraft:cobblestone", 32))),
            Map.entry("water_wheel", Reward.of(80, new Give("minecraft:water_bucket", 1))),
            Map.entry("alternator", Reward.of(120, new Give("minecraft:copper_ingot", 16))),
            Map.entry("steel", Reward.of(160, new Give("minecraft:coal", 32))),
            Map.entry("controller", Reward.of(120, new Give("minecraft:hopper", 4))),
            Map.entry("reactor", Reward.of(240, new Give("minecraft:water_bucket", 4))),
            Map.entry("starsteel", Reward.of(XP_MOST, new Give("minecraft:diamond", 4))),
            Map.entry("luna", Reward.of(XP_MOST, new Give("minecraft:golden_apple", 2))),
            Map.entry("the_hollow_lord", Reward.of(XP_MOST, new Give("minecraft:diamond", 3))),
            Map.entry("the_grave", Reward.of(40, new Give("minecraft:bread", 8))),
            Map.entry("the_compass", Reward.of(60, new Give("minecraft:map", 1))),
            Map.entry("botany_pot", Reward.of(20, new Give("minecraft:carrot", 8))),
            Map.entry("two_more_fittings", Reward.of(20)),
            Map.entry("beds", Reward.of(20)),
            Map.entry("rich_loam", Reward.of(20, new Give("minecraft:bone_meal", 16))),
            Map.entry("void_silt", Reward.of(40)),
            Map.entry("colony", Reward.of(20)),
            Map.entry("accelerate", Reward.of(40, new Give("elysiumindustry:copper_wire", 8))),
            Map.entry("first_essence", Reward.of(20)),
            Map.entry("bright_essence", Reward.of(40, new Give("minecraft:glowstone_dust", 8))),
            Map.entry("radiant_essence", Reward.of(40)),
            Map.entry("keen_essence", Reward.of(80)),
            Map.entry("ascendant_essence", Reward.of(80, new Give("minecraft:netherite_scrap", 2))),
            Map.entry("altar", Reward.of(80)),
            Map.entry("insanium", Reward.of(80)),
            Map.entry("awakened_seed", Reward.of(160, new Give("elysiumagronomy:void_silt", 4))),
            Map.entry("make_rock", Reward.of(10, new Give("minecraft:hopper", 1))),
            Map.entry("driven_rock", Reward.of(40)),
            Map.entry("metal_veins", Reward.of(40)),
            Map.entry("industrial_rock", Reward.of(40)),
            Map.entry("rich_veins", Reward.of(80, new Give("minecraft:diamond", 2))),
            Map.entry("nest", Reward.of(20, new Give("elysiumagronomy:bee_jar", 2))),
            Map.entry("sting", Reward.of(20)),
            Map.entry("keep_bees", Reward.of(20)),
            Map.entry("breed", Reward.of(40)),
            Map.entry("comb_centrifuge", Reward.of(40, new Give("minecraft:glass_bottle", 8))),
            Map.entry("apiary", Reward.of(40)),
            Map.entry("upgrades", Reward.of(40)),
            Map.entry("industrial_apiary", Reward.of(80, new Give("elysiumagronomy:speed_upgrade", 1))),
            Map.entry("wild_crops", Reward.of(10, new Give("minecraft:bone_meal", 8))),
            Map.entry("harvest", Reward.of(20)),
            Map.entry("knife", Reward.of(10)),
            Map.entry("board", Reward.of(20)),
            Map.entry("cooking_pot", Reward.of(20, new Give("minecraft:bowl", 8))),
            Map.entry("skillet", Reward.of(20)),
            Map.entry("oven", Reward.of(40)),
            Map.entry("food_book", Reward.of(20)),
            Map.entry("varied_diet", Reward.of(160, new Give("minecraft:golden_apple", 2))),
            Map.entry("first_rod", Reward.of(10, new Give("elysiumagronomy:river_bait", 8))),
            Map.entry("catch", Reward.of(20)),
            Map.entry("hooks", Reward.of(20)),
            Map.entry("bobbers", Reward.of(20)),
            Map.entry("bait", Reward.of(20)),
            Map.entry("worm_farm", Reward.of(20)),
            Map.entry("trophies", Reward.of(10)),
            Map.entry("far_waters", Reward.of(80, new Give("elysiumagronomy:gilded_hook", 1))),
            Map.entry("coal_and_sun", Reward.of(20, new Give("minecraft:coal", 16))),
            Map.entry("run_a_line", Reward.of(20, new Give("elysiumspace:cable", 16))),
            Map.entry("black_water", Reward.of(20, new Give("minecraft:bucket", 2))),
            Map.entry("nodding_donkey", Reward.of(40)),
            Map.entry("crack_it", Reward.of(40, new Give("minecraft:glass_bottle", 8))),
            Map.entry("diesel_power", Reward.of(20)),
            Map.entry("bottle_fuel", Reward.of(20)),
            Map.entry("split_water", Reward.of(40)),
            Map.entry("dig_steel", Reward.of(20)),
            Map.entry("clad_the_yard", Reward.of(10)),
            Map.entry("rocket_parts", Reward.of(40)),
            Map.entry("rocket_built", Reward.of(40)),
            Map.entry("launch_pad", Reward.of(20, new Give("elysiumspace:launch_pad", 4))),
            Map.entry("first_flight", Reward.of(80, new Give("elysiumspace:fuel_canister", 4))),
            Map.entry("suit_up", Reward.of(40, new Give("elysiumspace:oxygen_canister", 2))),
            Map.entry("fill_a_room", Reward.of(40)),
            Map.entry("jet_suit", Reward.of(40)),
            Map.entry("moon_rock", Reward.of(20)),
            Map.entry("lunite", Reward.of(40)),
            Map.entry("moonfolk", Reward.of(40)),
            Map.entry("landing_sites", Reward.of(10)),
            Map.entry("pack_it_down", Reward.of(10)),
            Map.entry("dressed_stone", Reward.of(10)),
            Map.entry("large_tank", Reward.of(40, new Give("elysiumspace:fuel_bucket", 2))),
            Map.entry("hydrogen_motor", Reward.of(40)),
            Map.entry("cinderveil", Reward.of(80, new Give("elysiumspace:oxygen_canister", 2))),
            Map.entry("cindrium", Reward.of(40)),
            Map.entry("cinderlings", Reward.of(40)),
            Map.entry("cinder_ruins", Reward.of(10)),
            Map.entry("frostfall", Reward.of(80, new Give("elysiumspace:fuel_canister", 4))),
            Map.entry("frostite", Reward.of(40)),
            Map.entry("frost_raptor", Reward.of(20)),
            Map.entry("frost_temples", Reward.of(10)),
            Map.entry("heavier_motors", Reward.of(80, new Give("elysiumspace:rime_shard", 4))),
            Map.entry("emerald_bastion", Reward.of(160, new Give("minecraft:emerald", 16))),
            Map.entry("emgrove", Reward.of(20)),
            Map.entry("station", Reward.of(80)),
            Map.entry("bigger_station", Reward.of(80, new Give("elysiumspace:steel_block", 4))),
            Map.entry("waygate_found", Reward.of(20, new Give("minecraft:ender_eye", 1))),
            Map.entry("waygate_built", Reward.of(80)),
            Map.entry("freight", Reward.of(80)),
            Map.entry("fallen_things", Reward.of(10)),
            Map.entry("truck", Reward.of(40)),
            Map.entry("hauler", Reward.of(80)),
            Map.entry("the_hundred_and_forty", Reward.of(20)),
            Map.entry("settled_places", Reward.of(10)),
            Map.entry("old_places", Reward.of(10)),
            Map.entry("boulders", Reward.of(10)),
            Map.entry("worn_path", Reward.of(10)),
            Map.entry("on_the_road", Reward.of(20, new Give("minecraft:coal", 16))),
            Map.entry("bind_two", Reward.of(20, new Give("minecraft:ender_pearl", 1))),
            Map.entry("ledger_codex", Reward.of(10, new Give("minecraft:bread", 4))),
            Map.entry("ledger_declare", Reward.of(20)),
            Map.entry("ledger_origins", Reward.of(10)),
            Map.entry("ledger_callings", Reward.of(10)),
            Map.entry("ledger_research", Reward.of(10)),
            Map.entry("ledger_first_level", Reward.of(20, new Give("minecraft:torch", 16))),
            Map.entry("ledger_every_action", Reward.of(20)),
            Map.entry("ledger_stats", Reward.of(10)),
            Map.entry("ledger_web_tab", Reward.of(20)),
            Map.entry("ledger_level_five", Reward.of(40)),
            Map.entry("ledger_notable", Reward.of(40)),
            Map.entry("ledger_gem_dust", Reward.of(20, new Give("minecraft:paper", 4))),
            Map.entry("ledger_gem_case", Reward.of(20)),
            Map.entry("ledger_erasure", Reward.of(20, new Give("minecraft:book", 1))),
            Map.entry("ledger_keystone", Reward.of(80)),
            Map.entry("ledger_level_ten", Reward.of(160, new Give("elysium:gem_dust", 4))),
            Map.entry("empire_voidglass", Reward.of(20)),
            Map.entry("empire_noticed", Reward.of(20)),
            Map.entry("empire_aetherium", Reward.of(20)),
            Map.entry("empire_neutronium", Reward.of(40)),
            Map.entry("empire_ramp", Reward.of(20, new Give("minecraft:copper_ingot", 8))),
            Map.entry("empire_area_tools", Reward.of(20)),
            Map.entry("empire_imperial_gear", Reward.of(40)),
            Map.entry("empire_plate", Reward.of(80, new Give("elysium:neutronium_ingot", 2))),
            Map.entry("empire_runes", Reward.of(20)),
            Map.entry("empire_sockets", Reward.of(40)),
            Map.entry("empire_elemental_arms", Reward.of(40)),
            Map.entry("empire_cutting", Reward.of(40)),
            Map.entry("empire_reforge", Reward.of(40)),
            Map.entry("empire_rations", Reward.of(20, new Give("minecraft:bread", 6))),
            Map.entry("empire_hazards", Reward.of(10)),
            Map.entry("empire_wounds", Reward.of(10)),
            Map.entry("empire_scanner", Reward.of(40)),
            Map.entry("empire_claim", Reward.of(40, new Give("minecraft:obsidian", 4))),
            Map.entry("empire_archive", Reward.of(20)),
            Map.entry("empire_inspection", Reward.of(20)),
            Map.entry("empire_enforcer", Reward.of(40)),
            Map.entry("empire_raider", Reward.of(40, new Give("elysium:voidglass_ingot", 2))),
            Map.entry("empire_regalia", Reward.of(40)),
            Map.entry("empire_spawners", Reward.of(40)),
            Map.entry("empire_crown", Reward.of(160, new Give("elysium:neutronium_block", 1))),
            Map.entry("court_metal_benches", Reward.of(20, new Give("minecraft:copper_ingot", 8))),
            Map.entry("court_signal_benches", Reward.of(20)),
            Map.entry("court_void_benches", Reward.of(20)),
            Map.entry("court_memory", Reward.of(20)),
            Map.entry("court_wanderer", Reward.of(10)),
            Map.entry("court_arrival", Reward.of(20)),
            Map.entry("court_offices", Reward.of(10)),
            Map.entry("court_amnesty", Reward.of(40, new Give("minecraft:emerald", 4))),
            Map.entry("court_commission", Reward.of(80)),
            Map.entry("town_survey", Reward.of(10)),
            Map.entry("town_ruins", Reward.of(10)),
            Map.entry("town_charter", Reward.of(20)),
            Map.entry("town_found", Reward.of(20)),
            Map.entry("town_relief", Reward.of(10)),
            Map.entry("town_exchange", Reward.of(10)),
            Map.entry("town_command", Reward.of(20)),
            Map.entry("town_anchor", Reward.of(40, new Give("minecraft:iron_block", 2))),
            Map.entry("town_tether", Reward.of(40, new Give("minecraft:chain", 16))),
            Map.entry("town_counterweight", Reward.of(40)),
            Map.entry("town_legs", Reward.of(40)),
            Map.entry("town_spaceport", Reward.of(160, new Give("minecraft:emerald", 16))),
            Map.entry("longsword_shape", Reward.of(20)),
            Map.entry("cordon_blade", Reward.of(40, new Give("minecraft:iron_ingot", 8))),
            Map.entry("the_quick_shapes", Reward.of(40)),
            Map.entry("the_heavy_shapes", Reward.of(40)),
            Map.entry("top_of_the_ladder", Reward.of(160, new Give("minecraft:diamond", 4))),
            Map.entry("a_scythe", Reward.of(20, new Give("minecraft:bone_meal", 16))),
            Map.entry("the_broad_set", Reward.of(40)),
            Map.entry("a_legend_sigil", Reward.of(40, new Give("minecraft:amethyst_shard", 4))),
            Map.entry("named_in_diamond", Reward.of(80)),
            Map.entry("named_in_netherite", Reward.of(160, new Give("minecraft:netherite_scrap", 2))),
            Map.entry("three_ladders_climbed", Reward.of(40)),
            Map.entry("a_myth", Reward.of(40, new Give("minecraft:golden_apple", 1))),
            Map.entry("a_warded_thing", Reward.of(20)),
            Map.entry("kindreds", Reward.of(20)),
            Map.entry("a_court", Reward.of(80)),
            Map.entry("crowned_heads", Reward.of(160, new Give("minecraft:diamond", 3))),
            Map.entry("a_companion", Reward.of(20, new Give("minecraft:wheat_seeds", 16))),
            Map.entry("the_half_that_grazes", Reward.of(10)),
            Map.entry("the_vales_own", Reward.of(40)),
            Map.entry("the_outer_rings", Reward.of(80)),
            Map.entry("moving_them", Reward.of(20)),
            Map.entry("killing_them_for_you", Reward.of(80, new Give("minecraft:hopper", 2))),
            Map.entry("the_digging_table", Reward.of(20, new Give("minecraft:bone", 8))),
            Map.entry("brush_a_site", Reward.of(20)),
            Map.entry("half_a_skeleton", Reward.of(20)),
            Map.entry("a_whole_skeleton", Reward.of(40)),
            Map.entry("what_else_is_buried", Reward.of(20)),
            Map.entry("rotten_wood_cracked_brick", Reward.of(10)),
            Map.entry("totems_set_down", Reward.of(40)),
            Map.entry("totems_spent", Reward.of(20)),
            Map.entry("an_unidentified_lump", Reward.of(20)),
            Map.entry("what_it_turned_out_to_be", Reward.of(40)),
            Map.entry("weaponsmiths_hammer", Reward.of(20, new Give("minecraft:experience_bottle", 4))),
            Map.entry("tools_and_armour_regraded", Reward.of(20)),
            Map.entry("shortbow_and_sling", Reward.of(20, new Give("minecraft:flint", 16))),
            Map.entry("longbow_and_recurve", Reward.of(40)),
            Map.entry("repeater_and_crossbow", Reward.of(40)),
            Map.entry("a_netherite_brush", Reward.of(160, new Give("minecraft:diamond", 2))),
            Map.entry("a_band_on_the_road", Reward.of(20)),
            Map.entry("the_empires_own", Reward.of(20)),
            Map.entry("fast_and_frail", Reward.of(20)),
            Map.entry("heavy_and_unseen", Reward.of(40)),
            Map.entry("equipment_and_office", Reward.of(20)),
            Map.entry("doctrine_and_doorway", Reward.of(40)),
            Map.entry("three_even_shares", Reward.of(40)),
            Map.entry("built_for_the_room", Reward.of(20)),
            Map.entry("the_register", Reward.of(20)),
            Map.entry("the_choir", Reward.of(80, new Give("minecraft:golden_apple", 2))),
            Map.entry("the_praetor", Reward.of(80, new Give("minecraft:golden_apple", 2))),
            Map.entry("both_sides_have_an_opinion", Reward.of(160, new Give("minecraft:diamond", 3))),
            Map.entry("take_a_cage_apart", Reward.of(20)),
            Map.entry("carry_a_cage", Reward.of(40)),
            Map.entry("set_the_cage_down", Reward.of(20)),
            Map.entry("a_rune_on_it", Reward.of(20, new Give("minecraft:obsidian", 8))),
            Map.entry("faster_never_a_wall", Reward.of(40, new Give("minecraft:sugar", 16))),
            Map.entry("six_behaviours", Reward.of(40)),
            Map.entry("ask_the_spawner", Reward.of(10)),
            Map.entry("an_unstable_one", Reward.of(10)),
            Map.entry("capturing", Reward.of(40)),
            Map.entry("a_cage_that_never_sleeps", Reward.of(160, new Give("minecraft:experience_bottle", 16))),
            Map.entry("a_ring", Reward.of(20)),
            Map.entry("at_the_throat", Reward.of(20)),
            Map.entry("at_the_waist", Reward.of(20)),
            Map.entry("across_the_back", Reward.of(20)),
            Map.entry("carried", Reward.of(20)),
            Map.entry("on_the_hands", Reward.of(20)),
            Map.entry("on_the_head", Reward.of(20)),
            Map.entry("at_the_shoulder", Reward.of(20)),
            Map.entry("found_not_made", Reward.of(80, new Give("minecraft:compass", 1))),
            Map.entry("a_potion_charm", Reward.of(40, new Give("minecraft:glass_bottle", 4))),
            Map.entry("a_slot_is_a_question", Reward.of(160, new Give("minecraft:ender_pearl", 4))),
            Map.entry("a_kitchen", Reward.of(20, new Give("minecraft:bread", 8))),
            Map.entry("more_storage", Reward.of(20, new Give("minecraft:chest", 2))),
            Map.entry("balconies_and_bridges", Reward.of(40)),
            Map.entry("a_roof_that_closes", Reward.of(80, new Give("minecraft:lantern", 4))),
            Map.entry("the_garden_path", Reward.of(40)),
            Map.entry("open_the_grave", Reward.of(20)),
            Map.entry("a_place_to_sleep", Reward.of(20, new Give("minecraft:string", 8))),
            Map.entry("light_by_the_row", Reward.of(20, new Give("minecraft:candle", 4))),
            Map.entry("a_mailbox", Reward.of(20, new Give("minecraft:paper", 16))),
            Map.entry("payback", Reward.of(40)),
            Map.entry("pigeonhole", Reward.of(20, new Give("minecraft:wheat_seeds", 16))),
            Map.entry("trading_station", Reward.of(40)),
            Map.entry("breeder", Reward.of(20, new Give("minecraft:golden_carrot", 8))),
            Map.entry("farms", Reward.of(80, new Give("minecraft:iron_ingot", 8))),
            Map.entry("wild_loomflax", Reward.of(10)),
            Map.entry("loomflax_field", Reward.of(20, new Give("minecraft:bone_meal", 8))),
            Map.entry("wild_downbloom", Reward.of(10)),
            Map.entry("downbloom_field", Reward.of(20, new Give("minecraft:bone_meal", 8))),
            Map.entry("emberwood_found", Reward.of(20)),
            Map.entry("emberwood_planted", Reward.of(20, new Give("minecraft:bone_meal", 8))),
            Map.entry("emberwood_charcoal", Reward.of(40)),
            Map.entry("never_again_spiders", Reward.of(160, new Give("minecraft:lead", 4), new Give("minecraft:bow", 1))),
            Map.entry("solar_brass", Reward.of(20, new Give("minecraft:copper_ingot", 12))),
            Map.entry("condenser", Reward.of(40)),
            Map.entry("conduit", Reward.of(20, new Give("minecraft:copper_ingot", 6))),
            Map.entry("governor", Reward.of(40)),
            Map.entry("capstan", Reward.of(40)),
            Map.entry("reclaimer", Reward.of(20, new Give("minecraft:cauldron", 1))),
            Map.entry("bioreactor", Reward.of(40, new Give("minecraft:clay_ball", 16))),
            Map.entry("press_and_loom", Reward.of(40)),
            Map.entry("cane", Reward.of(20, new Give("minecraft:bamboo", 16))),
            Map.entry("harvesters", Reward.of(40, new Give("minecraft:oak_sapling", 8))),
            Map.entry("aquaponics", Reward.of(40)),
            Map.entry("verdigris", Reward.of(20, new Give("minecraft:moss_block", 4))),
            Map.entry("white_stone", Reward.of(80, new Give("minecraft:gold_nugget", 9))),
            Map.entry("the_commons_kept", Reward.of(160, new Give("minecraft:daylight_detector", 2))),
            Map.entry("seam_first_vein", Reward.of(10, new Give("minecraft:iron_pickaxe", 1))),
            Map.entry("seam_deeper", Reward.of(20)),
            Map.entry("seam_gems", Reward.of(20)),
            Map.entry("seam_experience", Reward.of(20, new Give("minecraft:experience_bottle", 4))),
            Map.entry("seam_cobalt", Reward.of(20)),
            Map.entry("seam_storage", Reward.of(20)),
            Map.entry("seam_nightgold", Reward.of(20, new Give("minecraft:gold_ingot", 4))),
            Map.entry("seam_nether", Reward.of(20)),
            Map.entry("seam_end", Reward.of(40)),
            Map.entry("seam_pale", Reward.of(40)),
            Map.entry("seam_frost", Reward.of(40)),
            Map.entry("seam_cinder", Reward.of(40)),
            Map.entry("seam_roots", Reward.of(40)),
            Map.entry("seam_alloys", Reward.of(40, new Give("minecraft:blaze_rod", 2))),
            Map.entry("seam_found", Reward.of(40)),
            Map.entry("seam_unify", Reward.of(10)),
            Map.entry("seam_top", Reward.of(160, new Give("minecraft:diamond", 3))),
            Map.entry("forge_stone_anvil", Reward.of(20, new Give("minecraft:paper", 6), new Give("minecraft:clay_ball", 2))),
            Map.entry("forge_blueprint", Reward.of(20)),
            Map.entry("forge_first_tool", Reward.of(40, new Give("minecraft:stick", 8))),
            Map.entry("forge_smithing_table", Reward.of(40, new Give("minecraft:paper", 12))),
            Map.entry("forge_weapons", Reward.of(20)),
            Map.entry("forge_wide", Reward.of(20)),
            Map.entry("forge_ranged", Reward.of(40)),
            Map.entry("forge_press", Reward.of(40)),
            Map.entry("forge_armour", Reward.of(40)),
            Map.entry("forge_shield_wings", Reward.of(80, new Give("minecraft:phantom_membrane", 4))),
            Map.entry("forge_fittings", Reward.of(40)),
            Map.entry("forge_alloy", Reward.of(40)),
            Map.entry("forge_grader", Reward.of(40, new Give("minecraft:emerald", 4))),
            Map.entry("forge_starlight", Reward.of(40, new Give("minecraft:amethyst_shard", 4))),
            Map.entry("forge_salvage", Reward.of(40)),
            Map.entry("forge_reforge", Reward.of(40)),
            Map.entry("forge_jewellery", Reward.of(40, new Give("minecraft:gold_ingot", 4))),
            Map.entry("forge_masterwork", Reward.of(160, new Give("minecraft:diamond", 4))),
            Map.entry("geode_shards", Reward.of(20)),
            Map.entry("geode_seed_given", Reward.of(40, new Give("minecraft:glow_ink_sac", 2))),
            Map.entry("geode_first_cluster", Reward.of(20)),
            Map.entry("geode_too_soon", Reward.of(10)),
            Map.entry("geode_seed_mined", Reward.of(80, new Give("minecraft:echo_shard", 1))),
            Map.entry("geode_glass", Reward.of(10, new Give("minecraft:glass", 8))),
            Map.entry("geode_spyglass", Reward.of(10)),
            Map.entry("geode_relic_iron", Reward.of(20, new Give("minecraft:gold_ingot", 4))),
            Map.entry("geode_relic_gold", Reward.of(40)),
            Map.entry("geode_relic_diamond", Reward.of(80)),
            Map.entry("geode_relic_emerald", Reward.of(160, new Give("minecraft:emerald", 8))),
            Map.entry("geode_transmuter", Reward.of(80)),
            Map.entry("geode_tenth", Reward.of(20)),
            Map.entry("tome_arcane_table", Reward.of(40, new Give("minecraft:lapis_lazuli", 16))),
            Map.entry("tome_eterna", Reward.of(20)),
            Map.entry("tome_quanta", Reward.of(20)),
            Map.entry("tome_arcana", Reward.of(40)),
            Map.entry("tome_rectification", Reward.of(40, new Give("minecraft:comparator", 2))),
            Map.entry("tome_blades", Reward.of(20)),
            Map.entry("tome_armour", Reward.of(20)),
            Map.entry("tome_tools", Reward.of(20)),
            Map.entry("tome_infusion", Reward.of(40)),
            Map.entry("tome_scriptorium", Reward.of(40, new Give("minecraft:book", 8))),
            Map.entry("tome_grand", Reward.of(80)),
            Map.entry("tome_patience", Reward.of(20, new Give("minecraft:clock", 1))),
            Map.entry("tome_three_more", Reward.of(40)),
            Map.entry("tome_tower", Reward.of(40, new Give("minecraft:experience_bottle", 8))),
            Map.entry("tome_room", Reward.of(160, new Give("minecraft:diamond", 2), new Give("minecraft:enchanted_book", 1))),
            Map.entry("rift_frame", Reward.of(20, new Give("minecraft:obsidian", 4))),
            Map.entry("rift_key", Reward.of(20, new Give("minecraft:ender_eye", 1))),
            Map.entry("rift_open", Reward.of(40)),
            Map.entry("rift_through", Reward.of(80)),
            Map.entry("rift_rooms", Reward.of(10)),
            Map.entry("rift_denizens", Reward.of(40)),
            Map.entry("rift_loot", Reward.of(40, new Give("minecraft:chest", 2))),
            Map.entry("rift_boss", Reward.of(160, new Give("minecraft:diamond", 3))),
            Map.entry("rift_leave", Reward.of(10)),
            Map.entry("rift_second_door", Reward.of(40)),
            Map.entry("rift_cartography", Reward.of(80)),
            Map.entry("wind_it_up", Reward.of(10)),
            Map.entry("ask_the_line", Reward.of(10)),
            Map.entry("gravel_pile", Reward.of(10, new Give("minecraft:bone", 8))),
            Map.entry("mill_race", Reward.of(20)),
            Map.entry("cogwheels", Reward.of(20, new Give("minecraft:oak_planks", 16))),
            Map.entry("turn_a_corner", Reward.of(20)),
            Map.entry("teeth_that_disagree", Reward.of(10)),
            Map.entry("sails", Reward.of(40, new Give("minecraft:white_wool", 8))),
            Map.entry("screw_press", Reward.of(20)),
            Map.entry("stir_it", Reward.of(20)),
            Map.entry("the_saws_kitchen", Reward.of(20)),
            Map.entry("burn_for_it", Reward.of(40, new Give("minecraft:coal", 16))),
            Map.entry("fuel_refinery", Reward.of(40)),
            Map.entry("creosote_engine", Reward.of(40)),
            Map.entry("top_of_the_fuels", Reward.of(80, new Give("minecraft:coal_block", 4))),
            Map.entry("a_bad_bargain", Reward.of(20)),
            Map.entry("keep_it_awake", Reward.of(40)),
            Map.entry("brick_kiln", Reward.of(40, new Give("minecraft:brick", 16))),
            Map.entry("a_better_lump", Reward.of(20, new Give("minecraft:coal", 16))),
            Map.entry("steel_by_fire", Reward.of(80)),
            Map.entry("on_a_water_wheel", Reward.of(20)),
            Map.entry("what_the_grid_says", Reward.of(10)),
            Map.entry("two_lines_one_wall", Reward.of(20)),
            Map.entry("a_patient_boiler", Reward.of(10)),
            Map.entry("a_starved_machine", Reward.of(10)),
            Map.entry("no_wire_at_all", Reward.of(20)),
            Map.entry("sheet_metal", Reward.of(20)),
            Map.entry("a_medium_wire", Reward.of(40, new Give("minecraft:gold_ingot", 4))),
            Map.entry("low_meets_medium", Reward.of(20)),
            Map.entry("steel_sheet", Reward.of(20, new Give("minecraft:iron_ingot", 8))),
            Map.entry("a_high_wire", Reward.of(40)),
            Map.entry("four_turbines_in_one", Reward.of(40)),
            Map.entry("the_old_hall_rewired", Reward.of(20)),
            Map.entry("a_wider_hold", Reward.of(80, new Give("elysiumkinetics:brass_shaft", 8))),
            Map.entry("brass_bands", Reward.of(20)),
            Map.entry("full_at_a_glance", Reward.of(10)),
            Map.entry("a_satchel", Reward.of(10, new Give("minecraft:leather", 8))),
            Map.entry("bigger_bags", Reward.of(20)),
            Map.entry("pockets", Reward.of(20)),
            Map.entry("a_bag_that_helps", Reward.of(20)),
            Map.entry("cooked_and_pressed", Reward.of(20)),
            Map.entry("sealed_and_sieved", Reward.of(20)),
            Map.entry("a_crate_of_parts", Reward.of(10)),
            Map.entry("strongbox_and_vault", Reward.of(40, new Give("minecraft:obsidian", 4))),
            Map.entry("nine_into_one", Reward.of(10)),
            Map.entry("again_and_again", Reward.of(20)),
            Map.entry("lay_the_bus", Reward.of(20, new Give("elysiumindustry:copper_wire", 8))),
            Map.entry("pull_it_in", Reward.of(20)),
            Map.entry("push_it_out", Reward.of(20)),
            Map.entry("a_chest_joins", Reward.of(20)),
            Map.entry("count_to_redstone", Reward.of(20, new Give("minecraft:redstone", 16))),
            Map.entry("a_face_to_take_from", Reward.of(20)),
            Map.entry("two_ends_of_a_pool", Reward.of(40, new Give("minecraft:ender_pearl", 2))),
            Map.entry("a_warehouse_wall", Reward.of(160, new Give("elysiumlogistics:steel_drawer", 4))),
            Map.entry("watch_the_work", Reward.of(20)),
            Map.entry("the_plan_first", Reward.of(20)),
            Map.entry("a_busy_assembler", Reward.of(20)),
            Map.entry("in_batches", Reward.of(10)),
            Map.entry("sixteen_hands", Reward.of(80)),
            Map.entry("hold_it_up", Reward.of(40, new Give("elysiumlogistics:assembler", 2))),
            Map.entry("let_it_press", Reward.of(40)),
            Map.entry("nonuple", Reward.of(160)),
            Map.entry("yellow_ore", Reward.of(20)),
            Map.entry("charging_draining", Reward.of(10)),
            Map.entry("a_factory_cell", Reward.of(40)),
            Map.entry("a_cell_like_a_wire", Reward.of(80)),
            Map.entry("noon_and_midnight", Reward.of(10)),
            Map.entry("change_one_thing", Reward.of(40)),
            Map.entry("four_notches", Reward.of(40)),
            Map.entry("wear_and_plate", Reward.of(20)),
            Map.entry("sixteen_hundred", Reward.of(10)),
            Map.entry("channels_by_colour", Reward.of(20)),
            Map.entry("a_hall_of_three", Reward.of(80)),
            Map.entry("heat_goes_as_the_cube", Reward.of(160, new Give("elysiumatomics:enriched_uranium", 4))),
            Map.entry("fed_and_turning", Reward.of(20)),
            Map.entry("three_from_one", Reward.of(20)),
            Map.entry("two_in_one_out", Reward.of(10)),
            Map.entry("the_first_tier", Reward.of(40)),
            Map.entry("alloys_of_alloys", Reward.of(40)),
            Map.entry("void_steel", Reward.of(80)),
            Map.entry("ground_fine", Reward.of(20)),
            Map.entry("rolled_thin", Reward.of(20)),
            Map.entry("ninths", Reward.of(10)),
            Map.entry("eleven_new_metals", Reward.of(20, new Give("minecraft:coal", 16))),
            Map.entry("four_gems", Reward.of(20)),
            Map.entry("teeth_and_rods", Reward.of(40)),
            Map.entry("steel_and_invar_casings", Reward.of(40)),
            Map.entry("what_the_rock_held", Reward.of(80, new Give("minecraft:raw_iron", 8))),
            Map.entry("the_cordon_metals", Reward.of(40)),
            Map.entry("every_shape_of_it", Reward.of(160, new Give("elysiumfoundry:starsteel_ingot", 4))));

    /**
     * What finishing a quest pays: the declared reward, or the rule.
     *
     * <p>The rule pays for depth and for breadth. A step ten deep is a step a
     * player built ten things to reach; a step asking for three beasts at once
     * is three hunts. Capped, because the deepest chain in the book is not
     * worth a level on its own.
     */
    public static Reward rewardOf(String id) {
        Reward declared = REWARDS.get(id);
        if (declared != null) {
            return declared;
        }
        Quest quest = BY_ID.get(id);
        int tasks = quest == null ? 1 : quest.tasks().size();
        int xp = XP_BASE + XP_PER_DEPTH * depthOf(id) + XP_PER_TASK * (tasks - 1);
        return Reward.of(Math.min(XP_MOST, xp));
    }

    /** Everything the whole book pays, for a player who does all of it. */
    public static long totalXp() {
        long total = 0;
        for (Quest quest : QUESTS) {
            total += rewardOf(quest.id()).xp();
        }
        return total;
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
            new Chapter("the_wilds", List.of("first_light")),

            // The chapters added when the book grew to the whole pack (28 September
            // 2026): one per part of it, each hanging off the chapter a player
            // has read by the time they need it.
            new Chapter("the_ledger", List.of("first_light")),
            new Chapter("the_seams", List.of("first_light")),
            new Chapter("the_grove", List.of("first_light")),
            new Chapter("the_commons", List.of("reconstruction")),
            new Chapter("the_forge", List.of("reconstruction")),
            new Chapter("the_geodes", List.of("reconstruction")),
            new Chapter("the_tomes", List.of("first_light")),
            new Chapter("the_dig", List.of("the_dark")),
            new Chapter("the_charms", List.of("the_dark")),
            new Chapter("the_bands", List.of("the_dark")),
            new Chapter("the_cages", List.of("the_dark")),
            new Chapter("the_empire", List.of("the_ledger")),
            new Chapter("the_court", List.of("the_empire")),
            new Chapter("the_town", List.of("reconstruction")),
            new Chapter("the_rift", List.of("the_dark")));

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
    private static final List<Quest> FIRST_BOOK = List.of(
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
                    Task.visit("elysiumspace:pale_moon")),

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
            quest("spallet", "the_dark", List.of("first_blade"),
                    Task.slay(BST + "spallet", 1)),
            quest("the_three_families", "the_dark", List.of("spallet"),
                    Task.slay(BST + "drayhulk", 1), Task.slay(BST + "carrier", 1)),
            quest("worth_the_trouble", "the_dark", List.of("the_three_families"),
                    Task.slay(BST + "shearjaw", 1), Task.slay(BST + "sidetone", 1),
                    Task.slay(BST + "pileframe", 1)),
            quest("the_hollow_lord", "the_dark",
                    List.of("worth_the_trouble", "the_heavy_answer"),
                    Task.slay(BST + "nullprime", 1)),
            quest("the_siege_beast", "the_dark", List.of("the_hollow_lord"),
                    Task.slay(BST + "siegehulk", 1)),
            quest("the_stalker", "the_dark", List.of("the_hollow_lord"),
                    Task.slay(BST + "prowlframe", 1)),
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

    /**
     * Every step in the book: the first book above, then each chapter's share
     * of the pack (added 28 September 2026), a method a chapter because a
     * class's static initialiser may hold no more than 64 kilobytes of
     * bytecode and the whole pack does not fit in one list.
     */
    public static final List<Quest> QUESTS = joined(FIRST_BOOK,
            addedFirstLight(),
            addedReconstruction(),
            addedWarehouse(),
            addedGarden(),
            addedAscent(),
            addedAutomation(),
            addedFoundry(),
            addedFrontier(),
            addedTheDark(),
            addedTheHomestead(),
            addedTheWilds(),
            addedTheLedger(),
            addedTheSeams(),
            addedTheGrove(),
            addedTheCommons(),
            addedTheForge(),
            addedTheGeodes(),
            addedTheTomes(),
            addedTheDig(),
            addedTheCharms(),
            addedTheBands(),
            addedTheCages(),
            addedTheEmpire(),
            addedTheCourt(),
            addedTheTown(),
            addedTheRift());

    @SafeVarargs
    private static List<Quest> joined(List<Quest>... parts) {
        List<Quest> all = new ArrayList<>();
        for (List<Quest> part : parts) {
            all.addAll(part);
        }
        return List.copyOf(all);
    }

    private static List<Quest> addedFirstLight() {
        return List.of(
                quest("wind_it_up", "first_light", List.of("crank"),
                        Task.place("elysiumkinetics:hand_crank", 1), Task.use("elysiumkinetics:hand_crank")),
                quest("ask_the_line", "first_light", List.of("shaft"),
                        Task.use("#elysiumalmanac:kinetics_shafts")),
                quest("gravel_pile", "first_light", List.of("millstone"),
                        Task.place("elysiumkinetics:millstone", 1), Task.have("minecraft:gravel", 16)),
                quest("mill_race", "first_light", List.of("water_wheel"),
                        Task.place("elysiumkinetics:water_wheel", 1)),
                quest("cogwheels", "first_light", List.of("shaft"),
                        Task.have("elysiumkinetics:small_cogwheel", 2), Task.have("elysiumkinetics:large_cogwheel", 1)),
                quest("turn_a_corner", "first_light", List.of("cogwheels"),
                        Task.place("elysiumkinetics:gearbox", 1)),
                quest("teeth_that_disagree", "first_light", List.of("turn_a_corner"),
                        Task.read()),
                quest("sails", "first_light", List.of("shaft"),
                        Task.place("elysiumkinetics:windmill_bearing", 1), Task.place("elysiumkinetics:sail", 8)),
                quest("screw_press", "first_light", List.of("millstone"),
                        Task.place("elysiumkinetics:press", 1)),
                quest("stir_it", "first_light", List.of("cogwheels"),
                        Task.place("elysiumkinetics:mixer", 1)),
                quest("the_saws_kitchen", "first_light", List.of("saw"),
                        Task.place("elysiumkinetics:saw", 1), Task.have("elysiumagronomy:minced_beef", 2)),
                quest("burn_for_it", "first_light", List.of("millstone"),
                        Task.place("elysiumkinetics:furnace_engine", 1)),
                quest("fuel_refinery", "first_light", List.of("burn_for_it"),
                        Task.place("elysiumkinetics:refinery", 1), Task.have("elysiumkinetics:creosote", 1)),
                quest("creosote_engine", "first_light", List.of("fuel_refinery", "brass_shaft"),
                        Task.place("elysiumkinetics:oil_engine", 1)),
                quest("top_of_the_fuels", "first_light", List.of("creosote_engine"),
                        Task.have("elysiumkinetics:distillate", 1), Task.place("elysiumkinetics:distillate_engine", 1)),
                quest("a_bad_bargain", "first_light", List.of("cogwheels"),
                        Task.place("elysiumkinetics:dynamo", 1)),
                quest("keep_it_awake", "first_light", List.of("brass_shaft"),
                        Task.place("elysiumkinetics:anchor", 1), Task.use("elysiumkinetics:anchor")));
    }

    private static List<Quest> addedReconstruction() {
        return List.of(
                quest("brick_kiln", "reconstruction", List.of("brass_shaft"),
                        Task.place("elysiumindustry:coke_brick", 25), Task.place("elysiumindustry:coke_oven", 1)),
                quest("a_better_lump", "reconstruction", List.of("brick_kiln"),
                        Task.have("elysiumindustry:coke", 8)),
                quest("steel_by_fire", "reconstruction", List.of("a_better_lump"),
                        Task.place("elysiumindustry:blast_brick", 33), Task.place("elysiumindustry:blast_furnace", 1), Task.have("elysiumindustry:steel_ingot", 1)),
                quest("on_a_water_wheel", "reconstruction", List.of("alternator"),
                        Task.place("elysiumindustry:alternator", 1), Task.use("elysiumindustry:alternator")),
                quest("what_the_grid_says", "reconstruction", List.of("copper_wire"),
                        Task.use("#elysiumalmanac:industry_wires")),
                quest("two_lines_one_wall", "reconstruction", List.of("copper_wire"),
                        Task.craft("elysiumindustry:copper_wire", 2)),
                quest("a_patient_boiler", "reconstruction", List.of("turbine"),
                        Task.use("elysiumindustry:steam_turbine")),
                quest("a_starved_machine", "reconstruction", List.of("crusher"),
                        Task.use("#elysiumalmanac:industry_grid_machines")),
                quest("no_wire_at_all", "reconstruction", List.of("crusher", "creosote_engine"),
                        Task.read()),
                quest("sheet_metal", "reconstruction", List.of("plate"),
                        Task.have("elysiumindustry:copper_plate", 4), Task.have("elysiumindustry:gold_plate", 4), Task.have("elysiumindustry:iron_plate", 8)),
                quest("a_medium_wire", "reconstruction", List.of("plate"),
                        Task.place("elysiumindustry:gold_wire", 8)),
                quest("low_meets_medium", "reconstruction", List.of("transformer", "a_medium_wire"),
                        Task.place("elysiumindustry:transformer", 1)),
                quest("steel_sheet", "reconstruction", List.of("steel"),
                        Task.have("elysiumindustry:steel_plate", 8)),
                quest("a_high_wire", "reconstruction", List.of("steel"),
                        Task.place("elysiumindustry:steel_wire", 8)),
                quest("four_turbines_in_one", "reconstruction", List.of("steel", "a_medium_wire"),
                        Task.have("elysiumindustry:diesel_engine", 1)),
                quest("the_old_hall_rewired", "reconstruction", List.of("motor"),
                        Task.place("elysiumindustry:motor", 1), Task.use("elysiumindustry:motor")),
                quest("a_wider_hold", "reconstruction", List.of("the_old_hall_rewired", "keep_it_awake"),
                        Task.place("elysiumkinetics:wide_anchor", 1), Task.use("elysiumkinetics:wide_anchor")));
    }

    private static List<Quest> addedWarehouse() {
        return List.of(
                quest("brass_bands", "warehouse", List.of("drawer"),
                        Task.have("elysiumlogistics:brass_drawer", 1)),
                quest("full_at_a_glance", "warehouse", List.of("drawer"),
                        Task.use("#elysiumalmanac:logistics_drawers")),
                quest("a_satchel", "warehouse", List.of("welcome"),
                        Task.have("elysiumlogistics:satchel", 1)),
                quest("bigger_bags", "warehouse", List.of("a_satchel"),
                        Task.have("#elysiumalmanac:logistics_bags", 1)),
                quest("pockets", "warehouse", List.of("a_satchel"),
                        Task.have("#elysiumalmanac:logistics_pockets", 1)),
                quest("a_bag_that_helps", "warehouse", List.of("a_satchel"),
                        Task.have("#elysiumalmanac:logistics_pack_helpers", 1)),
                quest("cooked_and_pressed", "warehouse", List.of("a_satchel"),
                        Task.have("elysiumlogistics:ember_plate", 1), Task.have("elysiumlogistics:press_screw", 1)),
                quest("sealed_and_sieved", "warehouse", List.of("a_satchel"),
                        Task.have("elysiumlogistics:void_seal", 1), Task.have("elysiumlogistics:sieve_mesh", 1)),
                quest("a_crate_of_parts", "warehouse", List.of("drawer"),
                        Task.place("elysiumlogistics:crate", 1)),
                quest("strongbox_and_vault", "warehouse", List.of("a_crate_of_parts"),
                        Task.have("elysiumlogistics:strongbox", 1), Task.have("elysiumlogistics:vault", 1)),
                quest("nine_into_one", "warehouse", List.of("drawer"),
                        Task.craft("#elysiumalmanac:logistics_compressed", 1)),
                quest("again_and_again", "warehouse", List.of("nine_into_one"),
                        Task.have("#elysiumalmanac:logistics_compressed_mid", 1)),
                quest("lay_the_bus", "warehouse", List.of("controller"),
                        Task.place("elysiumlogistics:bus", 16), Task.use("elysiumlogistics:controller")),
                quest("pull_it_in", "warehouse", List.of("lay_the_bus"),
                        Task.place("elysiumlogistics:importer", 1), Task.use("elysiumlogistics:importer")),
                quest("push_it_out", "warehouse", List.of("pull_it_in"),
                        Task.place("elysiumlogistics:exporter", 1)),
                quest("a_chest_joins", "warehouse", List.of("lay_the_bus"),
                        Task.place("elysiumlogistics:storage_bus", 1)),
                quest("count_to_redstone", "warehouse", List.of("lay_the_bus"),
                        Task.place("elysiumlogistics:detector", 1), Task.use("elysiumlogistics:detector")),
                quest("a_face_to_take_from", "warehouse", List.of("lay_the_bus"),
                        Task.place("elysiumlogistics:interface", 1)),
                quest("two_ends_of_a_pool", "warehouse", List.of("lay_the_bus"),
                        Task.place("elysiumlogistics:ender_link", 2)),
                quest("a_warehouse_wall", "warehouse", List.of("push_it_out", "a_chest_joins", "count_to_redstone"),
                        Task.place("elysiumlogistics:steel_drawer", 16)));
    }

    private static List<Quest> addedGarden() {
        return List.of(
                quest("botany_pot", "garden", List.of("pot"),
                        Task.place("#elysiumalmanac:agronomy_botany_pots", 1), Task.use("#elysiumalmanac:agronomy_botany_pots")),
                quest("two_more_fittings", "garden", List.of("botany_pot"),
                        Task.have("#elysiumalmanac:agronomy_hopper_pots", 1), Task.have("#elysiumalmanac:agronomy_waxed_pots", 1)),
                quest("beds", "garden", List.of("botany_pot"),
                        Task.have("#elysiumalmanac:agronomy_beds", 1)),
                quest("rich_loam", "garden", List.of("beds"),
                        Task.have("elysiumagronomy:rich_loam", 1)),
                quest("void_silt", "garden", List.of("rich_loam"),
                        Task.have("elysiumagronomy:void_silt", 1)),
                quest("colony", "garden", List.of("welcome"),
                        Task.have("elysiumagronomy:mushroom_colony", 1), Task.use("elysiumagronomy:mushroom_colony")),
                quest("accelerate", "garden", List.of("accelerator"),
                        Task.use("elysiumagronomy:accelerator")),
                quest("first_essence", "garden", List.of("seed"),
                        Task.have("elysiumagronomy:faint_essence", 4), Task.have("#elysiumalmanac:agronomy_seeds_first", 1), Task.have("#elysiumalmanac:agronomy_chunks", 1)),
                quest("bright_essence", "garden", List.of("first_essence"),
                        Task.have("elysiumagronomy:bright_essence", 4), Task.have("#elysiumalmanac:agronomy_seeds_second", 1)),
                quest("radiant_essence", "garden", List.of("bright_essence"),
                        Task.have("elysiumagronomy:radiant_essence", 4), Task.have("#elysiumalmanac:agronomy_seeds_third", 1)),
                quest("keen_essence", "garden", List.of("radiant_essence"),
                        Task.have("elysiumagronomy:keen_essence", 4), Task.have("#elysiumalmanac:agronomy_seeds_fourth", 1)),
                quest("ascendant_essence", "garden", List.of("keen_essence"),
                        Task.have("elysiumagronomy:ascendant_essence", 4), Task.have("elysiumagronomy:netherite_seed", 1)),
                quest("altar", "garden", List.of("ascendant_essence"),
                        Task.place("elysiumagronomy:awakening_altar", 1), Task.place("elysiumagronomy:essence_pedestal", 4), Task.have("elysiumagronomy:essence_relay", 1)),
                quest("insanium", "garden", List.of("altar"),
                        Task.have("elysiumagronomy:insanium_essence", 1)),
                quest("awakened_seed", "garden", List.of("insanium"),
                        Task.have("#elysiumalmanac:agronomy_awakened_seeds", 1)),
                quest("make_rock", "garden", List.of("rock"),
                        Task.place("elysiumagronomy:rock_generator", 1), Task.use("elysiumagronomy:rock_generator"), Task.have("elysiumagronomy:stone_generator", 1)),
                quest("driven_rock", "garden", List.of("make_rock", "copper_wire"),
                        Task.have("elysiumagronomy:driven_rock_generator", 1)),
                quest("metal_veins", "garden", List.of("driven_rock"),
                        Task.have("#elysiumalmanac:agronomy_veins_driven", 1)),
                quest("industrial_rock", "garden", List.of("driven_rock", "steel"),
                        Task.have("elysiumagronomy:industrial_rock_generator", 1)),
                quest("rich_veins", "garden", List.of("industrial_rock"),
                        Task.have("#elysiumalmanac:agronomy_veins_industrial", 1)),
                quest("nest", "garden", List.of("hive"),
                        Task.have("elysiumagronomy:bee_jar", 1), Task.use("#elysiumalmanac:agronomy_nests")),
                quest("sting", "garden", List.of("nest"),
                        Task.slay("#elysiumalmanac:agronomy_bees", 1), Task.have("elysiumagronomy:bee_smoker", 1)),
                quest("keep_bees", "garden", List.of("nest"),
                        Task.use("elysiumagronomy:hive"), Task.have("elysiumagronomy:bee_scoop", 1), Task.have("#elysiumalmanac:agronomy_combs", 1)),
                quest("breed", "garden", List.of("keep_bees"),
                        Task.craft("elysiumagronomy:bee_treat", 1), Task.have("elysiumagronomy:iron_comb", 1)),
                quest("comb_centrifuge", "garden", List.of("keep_bees", "copper_wire"),
                        Task.have("elysiumagronomy:centrifuge", 1), Task.have("elysiumagronomy:wax", 1), Task.craft("#elysiumalmanac:agronomy_comb_blocks", 1)),
                quest("apiary", "garden", List.of("breed"),
                        Task.have("elysiumagronomy:apiary", 1), Task.have("elysiumagronomy:bee", 1)),
                quest("upgrades", "garden", List.of("apiary"),
                        Task.have("#elysiumalmanac:agronomy_upgrades", 2), Task.have("elysiumagronomy:advanced_apiary", 1)),
                quest("industrial_apiary", "garden", List.of("upgrades", "steel"),
                        Task.have("elysiumagronomy:industrial_apiary", 1)),
                quest("wild_crops", "garden", List.of("welcome"),
                        Task.mine("#elysiumalmanac:agronomy_crops", 1), Task.have("#elysiumalmanac:agronomy_crop_seeds", 1)),
                quest("harvest", "garden", List.of("wild_crops"),
                        Task.have("#elysiumalmanac:agronomy_produce", 8), Task.craft("#elysiumalmanac:agronomy_crates", 1)),
                quest("knife", "garden", List.of("welcome"),
                        Task.have("#elysiumalmanac:agronomy_knives", 1), Task.have("elysiumagronomy:fillet_knife", 1)),
                quest("board", "garden", List.of("knife"),
                        Task.place("elysiumagronomy:cutting_board", 1), Task.have("#elysiumalmanac:agronomy_preparations", 1), Task.eat("#elysiumalmanac:agronomy_sandwiches", 1)),
                quest("cooking_pot", "garden", List.of("harvest"),
                        Task.place("elysiumagronomy:cooking_pot", 1), Task.eat("#elysiumalmanac:agronomy_soups", 1)),
                quest("skillet", "garden", List.of("board"),
                        Task.place("elysiumagronomy:skillet", 1), Task.eat("#elysiumalmanac:agronomy_fried", 1)),
                quest("oven", "garden", List.of("cooking_pot"),
                        Task.place("elysiumagronomy:stone_oven", 1), Task.eat("#elysiumalmanac:agronomy_baked", 1), Task.place("#elysiumalmanac:agronomy_cabinets", 1)),
                quest("food_book", "garden", List.of("cooking_pot"),
                        Task.have("elysiumagronomy:food_book", 1)),
                quest("varied_diet", "garden", List.of("food_book", "oven", "skillet"),
                        Task.eat("#elysiumalmanac:agronomy_soups", 3), Task.eat("#elysiumalmanac:agronomy_baked", 2), Task.eat("#elysiumalmanac:agronomy_fried", 2)),
                quest("first_rod", "garden", List.of("welcome"),
                        Task.have("#elysiumalmanac:agronomy_rods", 1)),
                quest("catch", "garden", List.of("first_rod"),
                        Task.have("#elysiumalmanac:agronomy_fish", 1), Task.slay("#elysiumalmanac:agronomy_fish_mobs", 1), Task.have("elysiumagronomy:measuring_line", 1)),
                quest("hooks", "garden", List.of("catch"),
                        Task.have("#elysiumalmanac:agronomy_hooks", 1), Task.place("elysiumagronomy:tackle_box", 1)),
                quest("bobbers", "garden", List.of("catch"),
                        Task.have("#elysiumalmanac:agronomy_bobbers", 1)),
                quest("bait", "garden", List.of("catch"),
                        Task.have("#elysiumalmanac:agronomy_baits", 1)),
                quest("worm_farm", "garden", List.of("bait"),
                        Task.place("elysiumagronomy:worm_farm", 1), Task.use("elysiumagronomy:worm_farm")),
                quest("trophies", "garden", List.of("catch"),
                        Task.have("#elysiumalmanac:agronomy_fish_buckets", 1), Task.place("#elysiumalmanac:agronomy_mounts", 1)),
                quest("far_waters", "garden", List.of("hooks", "bobbers", "bait"),
                        Task.have("elysiumagronomy:magma_carp", 1), Task.have("elysiumagronomy:steel_rod", 1)));
    }

    private static List<Quest> addedAscent() {
        return List.of(
                quest("yellow_ore", "ascent", List.of("cell"),
                        Task.have("#elysiumalmanac:atomics_raw_uranium", 1), Task.smelt("elysiumatomics:uranium_ingot", 1)),
                quest("charging_draining", "ascent", List.of("cell"),
                        Task.use("elysiumatomics:basic_cell")),
                quest("a_factory_cell", "ascent", List.of("charging_draining"),
                        Task.have("elysiumatomics:advanced_cell", 1)),
                quest("a_cell_like_a_wire", "ascent", List.of("a_factory_cell"),
                        Task.have("elysiumatomics:quantum_cell", 1)),
                quest("noon_and_midnight", "ascent", List.of("solar"),
                        Task.use("elysiumatomics:solar_panel")),
                quest("change_one_thing", "ascent", List.of("centrifuge"),
                        Task.have("elysiumatomics:reaction_chamber", 1), Task.use("elysiumatomics:centrifuge")),
                quest("four_notches", "ascent", List.of("reactor"),
                        Task.use("elysiumatomics:reactor_core"), Task.have("elysiumatomics:enriched_uranium", 4)),
                quest("wear_and_plate", "ascent", List.of("four_notches"),
                        Task.have("elysiumindustry:steel_plate", 4)),
                quest("sixteen_hundred", "ascent", List.of("four_notches"),
                        Task.read()),
                quest("channels_by_colour", "ascent", List.of("coolant"),
                        Task.craft("elysiumatomics:coolant_channel", 4)),
                quest("a_hall_of_three", "ascent", List.of("four_notches", "coolant"),
                        Task.place("elysiumatomics:reactor_casing", 25), Task.place("elysiumatomics:fuel_rod", 1), Task.place("elysiumatomics:reactor_port", 1)),
                quest("heat_goes_as_the_cube", "ascent", List.of("a_hall_of_three"),
                        Task.place("elysiumatomics:fuel_rod", 27), Task.use("elysiumatomics:reactor_port")),
                quest("fed_and_turning", "ascent", List.of("servo"),
                        Task.use("elysiumatomics:servo")),
                quest("three_from_one", "ascent", List.of("enricher"),
                        Task.use("elysiumatomics:enricher")));
    }

    private static List<Quest> addedAutomation() {
        return List.of(
                quest("watch_the_work", "automation", List.of("assembly_core"),
                        Task.place("elysiumlogistics:monitor", 1)),
                quest("the_plan_first", "automation", List.of("assembly_core"),
                        Task.use("elysiumlogistics:assembly_core")),
                quest("a_busy_assembler", "automation", List.of("assembler"),
                        Task.use("elysiumlogistics:assembler")),
                quest("in_batches", "automation", List.of("a_busy_assembler"),
                        Task.read()),
                quest("sixteen_hands", "automation", List.of("a_busy_assembler"),
                        Task.have("elysiumlogistics:assembler", 16)),
                quest("hold_it_up", "automation", List.of("terminal"),
                        Task.use("elysiumlogistics:terminal")),
                quest("let_it_press", "automation", List.of("a_busy_assembler", "again_and_again"),
                        Task.have("#elysiumalmanac:logistics_compressed_deep", 1)),
                quest("nonuple", "automation", List.of("let_it_press"),
                        Task.have("#elysiumalmanac:logistics_compressed_nonuple", 1)));
    }

    private static List<Quest> addedFoundry() {
        return List.of(
                quest("two_in_one_out", "foundry", List.of("smelter"),
                        Task.use("elysiumfoundry:alloy_smelter")),
                quest("the_first_tier", "foundry", List.of("smelter"),
                        Task.have("elysiumfoundry:brass_ingot", 4), Task.have("#elysiumalmanac:foundry_first_alloys", 12)),
                quest("alloys_of_alloys", "foundry", List.of("the_first_tier", "invar"),
                        Task.have("#elysiumalmanac:foundry_second_alloys", 4)),
                quest("void_steel", "foundry", List.of("stainless"),
                        Task.have("#elysiumalmanac:foundry_top_alloys", 2)),
                quest("ground_fine", "foundry", List.of("smelter"),
                        Task.have("#elysiumalmanac:foundry_dusts", 4)),
                quest("rolled_thin", "foundry", List.of("smelter"),
                        Task.have("#elysiumalmanac:foundry_plates", 4)),
                quest("ninths", "foundry", List.of("smelter"),
                        Task.have("#elysiumalmanac:foundry_nuggets", 9)),
                quest("eleven_new_metals", "foundry", List.of("smelter"),
                        Task.have("#elysiumalmanac:foundry_raw", 1), Task.smelt("#elysiumalmanac:foundry_mined_ingots", 4)),
                quest("four_gems", "foundry", List.of("eleven_new_metals"),
                        Task.have("#elysiumalmanac:foundry_gems", 1)),
                quest("teeth_and_rods", "foundry", List.of("invar"),
                        Task.have("#elysiumalmanac:foundry_gears", 1), Task.have("#elysiumalmanac:foundry_rods", 2)),
                quest("steel_and_invar_casings", "foundry", List.of("invar"),
                        Task.have("elysiumfoundry:tier_1_casing", 1), Task.have("elysiumfoundry:tier_2_casing", 1)),
                quest("what_the_rock_held", "foundry", List.of("stainless"),
                        Task.have("elysiumfoundry:tier_3_casing", 1), Task.have("elysiumfoundry:refinery", 1)),
                quest("the_cordon_metals", "foundry", List.of("smelter"),
                        Task.have("#elysiumalmanac:foundry_cordon_dusts", 1), Task.have("#elysiumalmanac:foundry_cordon_plates", 1)),
                quest("every_shape_of_it", "foundry", List.of("void_steel", "what_the_rock_held", "teeth_and_rods"),
                        Task.have("elysiumfoundry:starsteel_gear", 1), Task.have("elysiumfoundry:tier_4_casing", 2)));
    }

    private static List<Quest> addedFrontier() {
        return List.of(
                quest("coal_and_sun", "frontier", List.of("steel"),
                        Task.place("elysiumspace:coal_generator", 1), Task.place("elysiumspace:solar_panel", 1)),
                quest("run_a_line", "frontier", List.of("coal_and_sun"),
                        Task.have("elysiumspace:cable", 8), Task.have("elysiumspace:pipe", 8), Task.place("elysiumspace:fluid_tank", 1)),
                quest("black_water", "frontier", List.of("steel"),
                        Task.have("elysiumspace:oil_bucket", 1), Task.have("elysiumspace:oil_finder", 1)),
                quest("nodding_donkey", "frontier", List.of("black_water", "coal_and_sun"),
                        Task.place("elysiumspace:pumpjack", 1), Task.use("elysiumspace:pumpjack")),
                quest("crack_it", "frontier", List.of("nodding_donkey", "run_a_line"),
                        Task.place("elysiumspace:fuel_refinery", 1), Task.have("elysiumspace:fuel_bucket", 1), Task.have("elysiumspace:diesel_bucket", 1)),
                quest("diesel_power", "frontier", List.of("crack_it"),
                        Task.place("elysiumspace:diesel_generator", 1)),
                quest("bottle_fuel", "frontier", List.of("crack_it"),
                        Task.have("elysiumspace:fuel_canister", 4)),
                quest("split_water", "frontier", List.of("run_a_line"),
                        Task.place("elysiumspace:water_separator", 1), Task.have("elysiumspace:oxygen_canister", 1), Task.have("elysiumspace:hydrogen_canister", 1)),
                quest("dig_steel", "frontier", List.of("steel"),
                        Task.mine("#elysiumalmanac:space_steel_ores", 1), Task.smelt("elysiumspace:steel_ingot", 1)),
                quest("clad_the_yard", "frontier", List.of("dig_steel"),
                        Task.have("#elysiumalmanac:space_platings", 16)),
                quest("rocket_parts", "frontier", List.of("dig_steel"),
                        Task.have("elysiumspace:rocket_nose_cone", 1), Task.have("elysiumspace:rocket_fin", 4), Task.have("elysiumspace:rocket_engine", 1)),
                quest("rocket_built", "frontier", List.of("rocket_parts"),
                        Task.have("elysiumspace:rocket", 1)),
                quest("launch_pad", "frontier", List.of("rocket_built"),
                        Task.place("elysiumspace:launch_pad", 1)),
                quest("first_flight", "frontier", List.of("launch_pad", "bottle_fuel"),
                        Task.visit("elysiumspace:high_orbit")),
                quest("suit_up", "frontier", List.of("look_up"),
                        Task.have("#elysiumalmanac:space_suit", 4)),
                quest("fill_a_room", "frontier", List.of("split_water", "suit_up"),
                        Task.place("elysiumspace:oxygen_distributor", 1), Task.use("elysiumspace:oxygen_distributor")),
                quest("jet_suit", "frontier", List.of("suit_up", "rocket_parts", "bottle_fuel"),
                        Task.have("elysiumspace:jet_suit", 1)),
                quest("moon_rock", "frontier", List.of("luna"),
                        Task.mine("#elysiumalmanac:space_pale_ground", 1), Task.mine("#elysiumalmanac:space_pale_vines", 1)),
                quest("lunite", "frontier", List.of("luna"),
                        Task.mine("#elysiumalmanac:space_moon_ores", 1), Task.have("elysiumspace:lunite_ingot", 4), Task.have("elysiumspace:rime_shard", 1)),
                quest("moonfolk", "frontier", List.of("luna"),
                        Task.slay("#elysiumalmanac:space_moon_dwellers", 1), Task.slay("elysiumspace:void_crawler", 1)),
                quest("landing_sites", "frontier", List.of("luna"),
                        Task.read()),
                quest("pack_it_down", "frontier", List.of("lunite"),
                        Task.have("#elysiumalmanac:space_raw_metals", 1), Task.have("#elysiumalmanac:space_nuggets", 9), Task.have("#elysiumalmanac:space_storage_blocks", 1)),
                quest("dressed_stone", "frontier", List.of("moon_rock"),
                        Task.have("#elysiumalmanac:space_dressed_stone", 16)),
                quest("large_tank", "frontier", List.of("lunite"),
                        Task.have("elysiumspace:large_fuel_tank", 1)),
                quest("hydrogen_motor", "frontier", List.of("lunite", "split_water"),
                        Task.have("elysiumspace:hydrogen_motor", 1)),
                quest("cinderveil", "frontier", List.of("luna"),
                        Task.visit("elysiumspace:cinderveil")),
                quest("cindrium", "frontier", List.of("cinderveil"),
                        Task.mine("#elysiumalmanac:space_cinder_ground", 1), Task.mine("#elysiumalmanac:space_cinder_ores", 1), Task.have("elysiumspace:cindrium_ingot", 1)),
                quest("cinderlings", "frontier", List.of("cinderveil"),
                        Task.slay("#elysiumalmanac:space_cinderlings", 1), Task.slay("elysiumspace:cinder_boar", 1)),
                quest("cinder_ruins", "frontier", List.of("cinderveil"),
                        Task.read()),
                quest("frostfall", "frontier", List.of("cinderveil"),
                        Task.visit("elysiumspace:frostfall")),
                quest("frostite", "frontier", List.of("frostfall"),
                        Task.mine("#elysiumalmanac:space_frost_ground", 1), Task.mine("#elysiumalmanac:space_frost_ores", 1), Task.have("#elysiumalmanac:space_frost_ingots", 2)),
                quest("frost_raptor", "frontier", List.of("frostfall"),
                        Task.slay("elysiumspace:frost_raptor", 1)),
                quest("frost_temples", "frontier", List.of("frostfall"),
                        Task.read()),
                quest("heavier_motors", "frontier", List.of("frostite", "cindrium", "hydrogen_motor"),
                        Task.have("#elysiumalmanac:space_heavy_ingots", 3), Task.have("#elysiumalmanac:space_heavy_motors", 1)),
                quest("emerald_bastion", "frontier", List.of("frostfall", "large_tank"),
                        Task.visit("elysiumspace:emerald_bastion")),
                quest("emgrove", "frontier", List.of("emerald_bastion"),
                        Task.mine("#elysiumalmanac:space_emgrove_wild", 1), Task.have("elysiumspace:emgrove_propagule", 1), Task.have("#elysiumalmanac:space_emgrove_worked", 4)),
                quest("station", "frontier", List.of("first_flight", "lunite"),
                        Task.have("elysiumspace:small_station_blueprint", 1)),
                quest("bigger_station", "frontier", List.of("station"),
                        Task.have("#elysiumalmanac:space_big_blueprints", 1)),
                quest("waygate_found", "frontier", List.of("look_up"),
                        Task.use("elysiumspace:waygate_anchor")),
                quest("waygate_built", "frontier", List.of("waygate_found", "frostite", "lunite"),
                        Task.place("elysiumspace:waygate_frame", 15), Task.place("elysiumspace:waygate_anchor", 1)),
                quest("freight", "frontier", List.of("luna", "crack_it"),
                        Task.place("elysiumspace:freight_terminal", 1), Task.use("elysiumspace:freight_terminal")),
                quest("fallen_things", "frontier", List.of("look_up"),
                        Task.read()),
                quest("truck", "frontier", List.of("cart", "motor"),
                        Task.have("elysiumtransit:truck", 1)),
                quest("hauler", "frontier", List.of("truck"),
                        Task.have("elysiumtransit:hauler", 1)));
    }

    private static List<Quest> addedTheDark() {
        return List.of(
                quest("longsword_shape", "the_dark", List.of("first_blade"),
                        Task.have("#elysiumalmanac:arsenal_longswords", 1)),
                quest("cordon_blade", "the_dark", List.of("a_reason_to_choose"),
                        Task.craft("#elysiumalmanac:arsenal_cordon_melee", 1)),
                quest("the_quick_shapes", "the_dark", List.of("cordon_blade", "the_narrow_answer"),
                        Task.craft("#elysiumalmanac:arsenal_sabres", 1), Task.craft("#elysiumalmanac:arsenal_spears", 1), Task.craft("#elysiumalmanac:arsenal_estocs", 1)),
                quest("the_heavy_shapes", "the_dark", List.of("the_quick_shapes", "the_heavy_answer", "longsword_shape"),
                        Task.craft("#elysiumalmanac:arsenal_greatswords", 1), Task.craft("#elysiumalmanac:arsenal_warhammers", 1), Task.craft("#elysiumalmanac:arsenal_longswords", 1)),
                quest("top_of_the_ladder", "the_dark", List.of("the_heavy_shapes"),
                        Task.have("#elysiumalmanac:arsenal_nullsteel_melee", 1)),
                quest("a_scythe", "the_dark", List.of("broad_tool"),
                        Task.have("#elysiumalmanac:arsenal_scythes", 1), Task.mine("#minecraft:crops", 16)),
                quest("the_broad_set", "the_dark", List.of("a_scythe"),
                        Task.have("#elysiumalmanac:arsenal_excavators", 1), Task.have("#elysiumalmanac:arsenal_paxels", 1), Task.have("#elysiumalmanac:arsenal_hammers", 1)),
                quest("a_legend_sigil", "the_dark", List.of("the_long_answer"),
                        Task.have("elysiumarsenal:legend_sigil", 1), Task.have("#elysiumalmanac:arsenal_uniques_iron", 1)),
                quest("named_in_diamond", "the_dark", List.of("a_legend_sigil", "the_narrow_answer"),
                        Task.have("#elysiumalmanac:arsenal_uniques_diamond", 1)),
                quest("named_in_netherite", "the_dark", List.of("named_in_diamond", "the_heavy_answer"),
                        Task.have("#elysiumalmanac:arsenal_uniques_netherite", 1)),
                quest("three_ladders_climbed", "the_dark", List.of("worth_the_trouble"),
                        Task.slay("#elysiumalmanac:bestiary_prowlers", 3), Task.slay("#elysiumalmanac:bestiary_brutes", 3), Task.slay("#elysiumalmanac:bestiary_wights", 3)),
                quest("a_myth", "the_dark", List.of("the_three_families"),
                        Task.slay("#elysiumalmanac:bestiary_myths", 1)),
                quest("a_warded_thing", "the_dark", List.of("the_three_families"),
                        Task.slay("#elysiumalmanac:bestiary_warded", 1)),
                quest("kindreds", "the_dark", List.of("spallet"),
                        Task.slay("#elysiumalmanac:bestiary_kin", 3)),
                quest("a_court", "the_dark", List.of("worth_the_trouble"),
                        Task.slay("#elysiumalmanac:bestiary_court", 10), Task.slay("#elysiumalmanac:bestiary_heads_low", 1)),
                quest("crowned_heads", "the_dark", List.of("a_court", "the_hollow_lord"),
                        Task.slay("#elysiumalmanac:bestiary_heads_mid", 1), Task.slay("#elysiumalmanac:bestiary_heads_high", 1)),
                quest("a_companion", "the_dark", List.of("first_blade"),
                        Task.slay("#elysiumalmanac:bestiary_companions", 1), Task.have("minecraft:wheat_seeds", 8)),
                quest("the_half_that_grazes", "the_dark", List.of("first_blade"),
                        Task.slay("#elysiumalmanac:bestiary_fauna", 1), Task.have("minecraft:wheat", 8)),
                quest("the_vales_own", "the_dark", List.of("kindreds"),
                        Task.visit("elysiumwilds:vale"), Task.slay("#elysiumalmanac:bestiary_denizens_near", 1)),
                quest("the_outer_rings", "the_dark", List.of("the_vales_own", "three_ladders_climbed"),
                        Task.slay("#elysiumalmanac:bestiary_denizens_far", 3)),
                quest("moving_them", "the_dark", List.of("the_three_families"),
                        Task.place("#elysiumalmanac:bestiary_farm_movers", 1), Task.place("#elysiumalmanac:bestiary_farm_ground", 1)),
                quest("killing_them_for_you", "the_dark", List.of("moving_them"),
                        Task.place("#elysiumalmanac:bestiary_farm_killers", 1), Task.place("#elysiumalmanac:bestiary_farm_keepers", 1)));
    }

    private static List<Quest> addedTheHomestead() {
        return List.of(
                quest("sit_and_dine", "the_homestead", List.of("somewhere_to_sit"),
                        Task.place("#elysiumalmanac:atelier_chairs", 2), Task.place("#elysiumalmanac:atelier_stools", 2), Task.place("#elysiumalmanac:atelier_benches", 1)),
                quest("a_kitchen", "the_homestead", List.of("sit_and_dine"),
                        Task.place("#elysiumalmanac:atelier_tables", 1), Task.place("#elysiumalmanac:atelier_kitchen", 3), Task.place("#elysiumalmanac:atelier_planters", 1)),
                quest("a_bedroom", "the_homestead", List.of("sit_and_dine"),
                        Task.place("#elysiumalmanac:atelier_bedroom", 3), Task.place("#elysiumalmanac:atelier_rugs", 2), Task.place("#elysiumalmanac:atelier_drapes", 1)),
                quest("more_storage", "the_homestead", List.of("somewhere_to_put_things"),
                        Task.have("#elysiumalmanac:atelier_shelves", 4), Task.have("#elysiumalmanac:atelier_cabinets", 2)),
                quest("bones_of_a_wall", "the_homestead", List.of("somewhere_to_sit"),
                        Task.place("#elysiumalmanac:atelier_pillars", 4), Task.place("#elysiumalmanac:atelier_screens", 2), Task.place("#elysiumalmanac:atelier_facings", 2)),
                quest("things_that_open", "the_homestead", List.of("a_door_of_stone"),
                        Task.place("#elysiumalmanac:atelier_doors", 1), Task.place("#elysiumalmanac:atelier_trapdoors", 1), Task.place("#elysiumalmanac:atelier_gates", 1)),
                quest("things_that_reach", "the_homestead", List.of("a_wall_that_joins", "a_window"),
                        Task.place("#elysiumalmanac:atelier_fences", 4), Task.place("#elysiumalmanac:atelier_panes", 4), Task.place("#elysiumalmanac:atelier_walls", 4)),
                quest("the_cuts", "the_homestead", List.of("bones_of_a_wall"),
                        Task.have("#elysiumalmanac:atelier_slabs", 8), Task.have("#elysiumalmanac:atelier_stairs", 4), Task.place("#elysiumalmanac:atelier_step_stairs", 4)),
                quest("a_way_up", "the_homestead", List.of("bones_of_a_wall"),
                        Task.place("#elysiumalmanac:atelier_ladders", 4), Task.place("#elysiumalmanac:atelier_ropes", 2), Task.place("#elysiumalmanac:atelier_trellises", 2)),
                quest("balconies_and_bridges", "the_homestead", List.of("the_cuts"),
                        Task.place("#elysiumalmanac:atelier_balconies", 4), Task.place("#elysiumalmanac:atelier_edges", 4), Task.place("#elysiumalmanac:atelier_bridges", 6)),
                quest("a_roof_that_closes", "the_homestead", List.of("bones_of_a_wall", "the_cuts"),
                        Task.place("#elysiumalmanac:atelier_steep_roofs", 8), Task.place("#elysiumalmanac:atelier_shallow_roofs", 4), Task.place("#elysiumalmanac:atelier_elevation", 3)),
                quest("the_garden_path", "the_homestead", List.of("sit_and_dine"),
                        Task.place("#elysiumalmanac:atelier_paths", 8), Task.place("#elysiumalmanac:atelier_lights", 2), Task.place("#elysiumalmanac:atelier_diagonals", 4)),
                quest("open_the_grave", "the_homestead", List.of("the_grave"),
                        Task.use("elysiumcomforts:grave")),
                quest("your_own_roll", "the_homestead", List.of("the_grave"),
                        Task.read()),
                quest("a_place_to_sleep", "the_homestead", List.of("welcome"),
                        Task.have("#elysiumalmanac:comforts_sleeping_bags", 1), Task.have("#elysiumalmanac:comforts_hammocks", 1), Task.have("elysiumcomforts:rope_and_nail", 2)),
                quest("a_porch", "the_homestead", List.of("welcome"),
                        Task.place("#elysiumalmanac:comforts_chimes", 1), Task.place("elysiumcomforts:fire_pit", 1), Task.place("elysiumcomforts:doormat", 1)),
                quest("light_by_the_row", "the_homestead", List.of("welcome"),
                        Task.place("#elysiumalmanac:comforts_candles", 2), Task.place("#elysiumalmanac:comforts_lanterns", 1), Task.place("#elysiumalmanac:comforts_lamps", 1)),
                quest("a_mailbox", "the_homestead", List.of("welcome"),
                        Task.have("elysiumcomforts:address_tag", 1), Task.place("elysiumcomforts:mailbox", 1), Task.have("elysiumcomforts:letter", 1)),
                quest("parcels", "the_homestead", List.of("a_mailbox"),
                        Task.have("elysiumcomforts:paper_box", 1), Task.have("elysiumcomforts:package", 1)),
                quest("sealed", "the_homestead", List.of("parcels"),
                        Task.have("elysiumcomforts:seal_stamp", 1), Task.have("elysiumcomforts:sealed_letter", 1), Task.have("elysiumcomforts:sealed_package", 1)),
                quest("payback", "the_homestead", List.of("sealed"),
                        Task.have("elysiumcomforts:payback_package", 1), Task.have("elysiumcomforts:payback_box", 1), Task.have("elysiumcomforts:lost_mail", 1)),
                quest("pigeonhole", "the_homestead", List.of("a_mailbox"),
                        Task.place("#elysiumalmanac:comforts_pigeonholes", 1)),
                quest("trading_station", "the_homestead", List.of("the_crate"),
                        Task.have("elysiumcomforts:trader_crate", 1), Task.have("elysiumcomforts:trader_crate_full", 1), Task.place("elysiumcomforts:trading_station", 1)),
                quest("auto_trader", "the_homestead", List.of("trading_station"),
                        Task.place("elysiumcomforts:auto_trader", 1), Task.place("elysiumcomforts:inventory_viewer", 1)),
                quest("breeder", "the_homestead", List.of("trading_station"),
                        Task.place("elysiumcomforts:breeder", 1), Task.place("elysiumcomforts:incubator", 1), Task.place("elysiumcomforts:converter", 1)),
                quest("farms", "the_homestead", List.of("breeder"),
                        Task.place("elysiumcomforts:villager_farm", 1), Task.place("elysiumcomforts:iron_farm", 1)));
    }

    private static List<Quest> addedTheWilds() {
        return List.of(
                quest("the_hundred_and_forty", "the_wilds", List.of("the_compass"),
                        Task.have("elysiumwilds:biome_compass", 1), Task.read()),
                quest("settled_places", "the_wilds", List.of("the_compass"),
                        Task.read()),
                quest("old_places", "the_wilds", List.of("settled_places"),
                        Task.read()),
                quest("boulders", "the_wilds", List.of("the_compass"),
                        Task.read()),
                quest("worn_path", "the_wilds", List.of("a_road"),
                        Task.place("elysiumtransit:gravel_path", 16)),
                quest("on_the_road", "the_wilds", List.of("a_road"),
                        Task.have("elysiumtransit:cart", 1), Task.place("elysiumtransit:paving", 32)),
                quest("bind_two", "the_wilds", List.of("somewhere_else"),
                        Task.place("elysiumtransit:waystone", 1), Task.use("elysiumtransit:waystone")));
    }

    private static List<Quest> addedTheLedger() {
        return List.of(
                quest("ledger_codex", "the_ledger", List.of("welcome"),
                        Task.have("elysium:imperial_codex", 1)),
                quest("ledger_declare", "the_ledger", List.of("ledger_codex"),
                        Task.web(2)),
                quest("ledger_origins", "the_ledger", List.of("ledger_declare"),
                        Task.read()),
                quest("ledger_callings", "the_ledger", List.of("ledger_declare"),
                        Task.read()),
                quest("ledger_research", "the_ledger", List.of("ledger_declare"),
                        Task.read()),
                quest("ledger_first_level", "the_ledger", List.of("ledger_declare"),
                        Task.level(2)),
                quest("ledger_every_action", "the_ledger", List.of("ledger_first_level"),
                        Task.level(3)),
                quest("ledger_stats", "the_ledger", List.of("ledger_first_level"),
                        Task.read()),
                quest("ledger_web_tab", "the_ledger", List.of("ledger_first_level"),
                        Task.web(5)),
                quest("ledger_level_five", "the_ledger", List.of("ledger_every_action"),
                        Task.level(5)),
                quest("ledger_notable", "the_ledger", List.of("ledger_web_tab"),
                        Task.web(15)),
                quest("ledger_gem_dust", "the_ledger", List.of("ledger_level_five"),
                        Task.have("elysium:gem_dust", 2)),
                quest("ledger_gem_case", "the_ledger", List.of("ledger_gem_dust"),
                        Task.have("elysium:gem_case", 1)),
                quest("ledger_erasure", "the_ledger", List.of("ledger_gem_dust", "ledger_notable"),
                        Task.have("elysium:ledger_erasure", 1)),
                quest("ledger_keystone", "the_ledger", List.of("ledger_notable"),
                        Task.web(25)),
                quest("ledger_level_ten", "the_ledger", List.of("ledger_level_five"),
                        Task.level(10)));
    }

    private static List<Quest> addedTheSeams() {
        return List.of(
                quest("seam_first_vein", "the_seams", List.of("welcome"),
                        Task.mine("#elysiumalmanac:ores_stone", 1)),
                quest("seam_deeper", "the_seams", List.of("seam_first_vein"),
                        Task.mine("#elysiumalmanac:ores_deepslate", 1)),
                quest("seam_gems", "the_seams", List.of("seam_first_vein"),
                        Task.have("#elysiumalmanac:ores_gems", 3), Task.have("#elysiumalmanac:ores_stone", 1)),
                quest("seam_experience", "the_seams", List.of("seam_gems"),
                        Task.mine("#elysiumalmanac:ores_deepslate", 8)),
                quest("seam_cobalt", "the_seams", List.of("seam_deeper"),
                        Task.have("#elysiumalmanac:ores_raw", 2), Task.smelt("#elysiumalmanac:ores_dug_ingots", 2)),
                quest("seam_storage", "the_seams", List.of("seam_cobalt"),
                        Task.craft("#elysiumalmanac:ores_raw_blocks", 1), Task.craft("#elysiumalmanac:ores_nuggets", 9), Task.craft("#elysiumalmanac:ores_storage_blocks", 1)),
                quest("seam_nightgold", "the_seams", List.of("seam_cobalt"),
                        Task.have("elysiumores:nightgold_ingot", 2)),
                quest("seam_nether", "the_seams", List.of("seam_deeper"),
                        Task.mine("#elysiumalmanac:ores_netherrack", 1)),
                quest("seam_end", "the_seams", List.of("seam_nether"),
                        Task.mine("#elysiumalmanac:ores_end_stone", 1)),
                quest("seam_pale", "the_seams", List.of("seam_end"),
                        Task.visit("elysiumspace:pale_moon"), Task.mine("#elysiumalmanac:ores_pale", 1)),
                quest("seam_frost", "the_seams", List.of("seam_end"),
                        Task.visit("elysiumspace:frostfall"), Task.mine("#elysiumalmanac:ores_frost", 1)),
                quest("seam_cinder", "the_seams", List.of("seam_end"),
                        Task.visit("elysiumspace:cinderveil"), Task.mine("#elysiumalmanac:ores_cinder", 1)),
                quest("seam_roots", "the_seams", List.of("seam_nightgold"),
                        Task.have("#elysiumalmanac:ores_roots", 1)),
                quest("seam_alloys", "the_seams", List.of("seam_roots"),
                        Task.have("#elysiumalmanac:ores_alloys", 1)),
                quest("seam_found", "the_seams", List.of("seam_roots"),
                        Task.have("#elysiumalmanac:ores_found", 1), Task.have("elysiumores:mireweave", 2)),
                quest("seam_unify", "the_seams", List.of("seam_cobalt"),
                        Task.read()),
                quest("seam_top", "the_seams", List.of("seam_alloys", "seam_found"),
                        Task.have("elysiumores:nullsteel_ingot", 4), Task.have("elysiumores:lattice", 1), Task.have("elysiumores:pyratite_plate", 1)));
    }

    private static List<Quest> addedTheGrove() {
        return List.of(
                quest("wild_loomflax", "the_grove", List.of("welcome"),
                        Task.mine("elysiumflora:wild_loomflax", 1), Task.have("elysiumflora:loomflax_seeds", 2)),
                quest("loomflax_field", "the_grove", List.of("wild_loomflax"),
                        Task.place("elysiumflora:loomflax", 8), Task.have("elysiumflora:loomflax_fibre", 3)),
                quest("string_from_flax", "the_grove", List.of("loomflax_field"),
                        Task.craft("minecraft:string", 4)),
                quest("wild_downbloom", "the_grove", List.of("welcome"),
                        Task.mine("elysiumflora:wild_downbloom", 1), Task.have("elysiumflora:downbloom_seeds", 2)),
                quest("downbloom_field", "the_grove", List.of("wild_downbloom"),
                        Task.place("elysiumflora:downbloom", 8), Task.have("elysiumflora:downbloom_tuft", 4), Task.have("elysiumflora:downbloom_bale", 1)),
                quest("the_yield", "the_grove", List.of("loomflax_field"),
                        Task.read()),
                quest("emberwood_found", "the_grove", List.of("welcome"),
                        Task.mine("#elysiumalmanac:flora_emberwood_logs", 4), Task.have("elysiumflora:emberwood_sapling", 1)),
                quest("emberwood_planted", "the_grove", List.of("emberwood_found"),
                        Task.place("elysiumflora:emberwood_sapling", 1), Task.have("elysiumflora:emberwood_leaves", 1)),
                quest("emberwood_charcoal", "the_grove", List.of("emberwood_found"),
                        Task.smelt("elysiumflora:emberwood_charcoal", 8)),
                quest("charcoal_block", "the_grove", List.of("emberwood_charcoal"),
                        Task.have("elysiumflora:emberwood_charcoal_block", 1)),
                quest("emberwood_planks", "the_grove", List.of("emberwood_found"),
                        Task.craft("elysiumflora:emberwood_planks", 16)),
                quest("emberwood_joinery", "the_grove", List.of("emberwood_planks"),
                        Task.place("#elysiumalmanac:flora_emberwood_carpentry", 4)),
                quest("never_again_spiders", "the_grove", List.of("string_from_flax", "downbloom_field", "charcoal_block"),
                        Task.have("minecraft:string", 64), Task.have("elysiumflora:emberwood_charcoal", 64)));
    }

    private static List<Quest> addedTheCommons() {
        return List.of(
                quest("solar_brass", "the_commons", List.of("welcome"),
                        Task.have("elysiumsolarpunk:solar_brass_ingot", 9), Task.have("elysiumsolarpunk:solar_brass_block", 1)),
                quest("bloom", "the_commons", List.of("solar_brass"),
                        Task.have("elysiumsolarpunk:bloom_resin", 2), Task.place("elysiumsolarpunk:bloom_glass", 1), Task.place("elysiumsolarpunk:bloom_lantern", 1)),
                quest("condenser", "the_commons", List.of("solar_brass", "copper_wire"),
                        Task.place("elysiumsolarpunk:solar_condenser", 1), Task.use("elysiumsolarpunk:solar_condenser"), Task.place("elysiumsolarpunk:greenhouse_frame", 6)),
                quest("conduit", "the_commons", List.of("condenser"),
                        Task.place("elysiumsolarpunk:power_conduit", 8), Task.use("elysiumsolarpunk:power_conduit")),
                quest("governor", "the_commons", List.of("conduit"),
                        Task.place("elysiumsolarpunk:power_governor", 1), Task.use("elysiumsolarpunk:power_governor")),
                quest("capstan", "the_commons", List.of("conduit"),
                        Task.place("elysiumsolarpunk:wind_capstan", 1)),
                quest("reclaimer", "the_commons", List.of("conduit"),
                        Task.place("elysiumsolarpunk:water_reclaimer", 1), Task.use("elysiumsolarpunk:water_reclaimer")),
                quest("bioreactor", "the_commons", List.of("reclaimer"),
                        Task.place("elysiumsolarpunk:algae_bioreactor", 1), Task.have("elysiumsolarpunk:biomass_briquette", 4), Task.place("elysiumsolarpunk:ceramic_kiln", 1)),
                quest("press_and_loom", "the_commons", List.of("conduit"),
                        Task.place("elysiumsolarpunk:mycelium_press", 1), Task.have("elysiumsolarpunk:plant_fibre", 4), Task.place("elysiumsolarpunk:fibre_loom", 1)),
                quest("cane", "the_commons", List.of("press_and_loom"),
                        Task.have("elysiumsolarpunk:woven_cane", 4), Task.place("#elysiumalmanac:solarpunk_cane", 4)),
                quest("harvesters", "the_commons", List.of("conduit"),
                        Task.place("elysiumsolarpunk:harvester", 1), Task.place("elysiumsolarpunk:tree_harvester", 1)),
                quest("aquaponics", "the_commons", List.of("reclaimer"),
                        Task.place("elysiumsolarpunk:aquaponics_tank", 1), Task.use("elysiumsolarpunk:aquaponics_tank")),
                quest("earth_and_plaster", "the_commons", List.of("welcome"),
                        Task.have("#elysiumalmanac:solarpunk_rammed_earth", 16), Task.have("#elysiumalmanac:solarpunk_pale_plaster", 16), Task.place("elysiumsolarpunk:copper_grating", 2)),
                quest("verdigris", "the_commons", List.of("solar_brass"),
                        Task.have("elysiumsolarpunk:verdigris", 2), Task.have("#elysiumalmanac:solarpunk_verdigris_copper", 4), Task.have("#elysiumalmanac:solarpunk_mosaic", 4)),
                quest("white_stone", "the_commons", List.of("welcome"),
                        Task.mine("#elysiumalmanac:solarpunk_white_stones", 16), Task.have("#elysiumalmanac:solarpunk_cut_stone", 8), Task.have("#elysiumalmanac:solarpunk_inlays", 4)),
                quest("the_commons_kept", "the_commons", List.of("governor", "capstan", "bioreactor", "press_and_loom"),
                        Task.read()));
    }

    private static List<Quest> addedTheForge() {
        return List.of(
                quest("forge_stone_anvil", "the_forge", List.of("welcome"),
                        Task.place("elysiumarmory:stone_anvil", 1), Task.use("elysiumarmory:stone_anvil")),
                quest("forge_blueprint", "the_forge", List.of("forge_stone_anvil"),
                        Task.have("elysiumarmory:blueprint_paper", 3), Task.have("elysiumarmory:blueprint", 1)),
                quest("forge_first_tool", "the_forge", List.of("forge_blueprint"),
                        Task.have("#elysiumalmanac:armory_tool_parts", 1), Task.have("elysiumarmory:rod", 1), Task.craft("#elysiumalmanac:armory_tools", 1)),
                quest("forge_smithing_table", "the_forge", List.of("forge_blueprint"),
                        Task.place("elysiumarmory:gear_smithing_table", 1), Task.use("elysiumarmory:gear_smithing_table"), Task.have("elysiumarmory:plan_book", 1)),
                quest("forge_weapons", "the_forge", List.of("forge_smithing_table", "forge_first_tool"),
                        Task.have("#elysiumalmanac:armory_weapon_parts", 1), Task.have("#elysiumalmanac:armory_weapons", 1)),
                quest("forge_wide", "the_forge", List.of("forge_first_tool", "forge_smithing_table"),
                        Task.have("#elysiumalmanac:armory_wide_parts", 1), Task.have("#elysiumalmanac:armory_wide_tools", 1)),
                quest("forge_ranged", "the_forge", List.of("forge_smithing_table"),
                        Task.have("#elysiumalmanac:armory_ranged_parts", 1), Task.have("#elysiumalmanac:armory_string_parts", 1), Task.have("#elysiumalmanac:armory_ranged", 1)),
                quest("forge_press", "the_forge", List.of("forge_smithing_table"),
                        Task.place("elysiumarmory:metal_press", 1), Task.have("elysiumarmory:sheet_metal", 4)),
                quest("forge_armour", "the_forge", List.of("forge_press"),
                        Task.have("#elysiumalmanac:armory_armour_parts", 1), Task.have("#elysiumalmanac:armory_armour", 1)),
                quest("forge_shield_wings", "the_forge", List.of("forge_armour"),
                        Task.have("#elysiumalmanac:armory_odd_parts", 2), Task.have("elysiumarmory:shield", 1), Task.have("elysiumarmory:elytra", 1)),
                quest("forge_fittings", "the_forge", List.of("forge_first_tool"),
                        Task.have("#elysiumalmanac:armory_fittings", 2), Task.have("elysiumarmory:mod_kit", 1), Task.have("#elysiumalmanac:armory_upgrades", 1)),
                quest("forge_alloy", "the_forge", List.of("forge_smithing_table"),
                        Task.place("elysiumarmory:alloy_forge", 1), Task.use("elysiumarmory:alloy_forge")),
                quest("forge_grader", "the_forge", List.of("forge_alloy"),
                        Task.place("elysiumarmory:material_grader", 1), Task.use("elysiumarmory:material_grader")),
                quest("forge_starlight", "the_forge", List.of("forge_smithing_table"),
                        Task.place("elysiumarmory:starlight_charger", 1), Task.use("elysiumarmory:starlight_charger")),
                quest("forge_salvage", "the_forge", List.of("forge_fittings"),
                        Task.place("elysiumarmory:salvager", 1), Task.use("elysiumarmory:salvager"), Task.have("elysiumarmory:affix_essence", 1)),
                quest("forge_reforge", "the_forge", List.of("forge_salvage"),
                        Task.place("elysiumarmory:reforging_table", 1), Task.use("elysiumarmory:reforging_table")),
                quest("forge_jewellery", "the_forge", List.of("forge_smithing_table"),
                        Task.have("#elysiumalmanac:armory_jewellery_parts", 2), Task.have("#elysiumalmanac:armory_jewellery", 1)),
                quest("forge_masterwork", "the_forge", List.of("forge_grader", "forge_starlight", "forge_reforge", "forge_shield_wings"),
                        Task.craft("#elysiumalmanac:armory_armour", 4), Task.craft("#elysiumalmanac:armory_weapons", 1)));
    }

    private static List<Quest> addedTheGeodes() {
        return List.of(
                quest("geode_shards", "the_geodes", List.of("welcome"),
                        Task.have("#elysiumalmanac:arcana_shards", 4), Task.craft("#elysiumalmanac:arcana_blocks", 1)),
                quest("geode_seed_given", "the_geodes", List.of("geode_shards"),
                        Task.place("#elysiumalmanac:arcana_budding_given", 1)),
                quest("geode_first_cluster", "the_geodes", List.of("geode_seed_given"),
                        Task.mine("#elysiumalmanac:arcana_clusters", 1)),
                quest("geode_too_soon", "the_geodes", List.of("geode_seed_given"),
                        Task.mine("#elysiumalmanac:arcana_buds", 1)),
                quest("geode_seed_mined", "the_geodes", List.of("geode_first_cluster"),
                        Task.place("#elysiumalmanac:arcana_budding_mined", 1)),
                quest("geode_glass", "the_geodes", List.of("geode_shards"),
                        Task.craft("#elysiumalmanac:arcana_tinted_glass", 2)),
                quest("geode_spyglass", "the_geodes", List.of("geode_shards"),
                        Task.have("#elysiumalmanac:arcana_spyglasses", 1)),
                quest("geode_relic_iron", "the_geodes", List.of("geode_first_cluster"),
                        Task.have("#elysiumalmanac:arcana_relics_iron", 1)),
                quest("geode_relic_gold", "the_geodes", List.of("geode_relic_iron"),
                        Task.have("#elysiumalmanac:arcana_relics_gold", 1)),
                quest("geode_relic_diamond", "the_geodes", List.of("geode_relic_gold", "geode_seed_mined"),
                        Task.have("#elysiumalmanac:arcana_relics_diamond", 1)),
                quest("geode_relic_emerald", "the_geodes", List.of("geode_relic_diamond"),
                        Task.have("#elysiumalmanac:arcana_relics_emerald", 1)),
                quest("geode_transmuter", "the_geodes", List.of("geode_shards"),
                        Task.use("elysiumarcana:transmuter")),
                quest("geode_tenth", "the_geodes", List.of("geode_transmuter"),
                        Task.read()));
    }

    private static List<Quest> addedTheTomes() {
        return List.of(
                quest("tome_arcane_table", "the_tomes", List.of("welcome"),
                        Task.place("elysiumenchanting:arcane_table", 1), Task.use("elysiumenchanting:arcane_table")),
                quest("tome_eterna", "the_tomes", List.of("tome_arcane_table"),
                        Task.place("elysiumenchanting:hellshelf", 4), Task.place("elysiumenchanting:archive_column", 1)),
                quest("tome_quanta", "the_tomes", List.of("tome_arcane_table"),
                        Task.place("elysiumenchanting:deepshelf", 2)),
                quest("tome_arcana", "the_tomes", List.of("tome_arcane_table"),
                        Task.place("elysiumenchanting:sightshelf", 2), Task.place("elysiumenchanting:endshelf", 1)),
                quest("tome_rectification", "the_tomes", List.of("tome_quanta", "tome_arcana"),
                        Task.place("elysiumenchanting:keepers_shelf", 2)),
                quest("tome_blades", "the_tomes", List.of("tome_arcane_table"),
                        Task.read(), Task.use("elysiumenchanting:arcane_table")),
                quest("tome_armour", "the_tomes", List.of("tome_arcane_table"),
                        Task.read(), Task.use("elysiumenchanting:arcane_table")),
                quest("tome_tools", "the_tomes", List.of("tome_arcane_table"),
                        Task.read(), Task.use("elysiumenchanting:arcane_table")),
                quest("tome_infusion", "the_tomes", List.of("tome_eterna"),
                        Task.place("elysiumenchanting:infusion_basin", 1), Task.use("elysiumenchanting:infusion_basin")),
                quest("tome_scriptorium", "the_tomes", List.of("tome_arcane_table"),
                        Task.place("elysiumenchanting:scriptorium", 1), Task.use("elysiumenchanting:scriptorium")),
                quest("tome_grand", "the_tomes", List.of("tome_scriptorium"),
                        Task.place("elysiumenchanting:grand_scriptorium", 1)),
                quest("tome_patience", "the_tomes", List.of("tome_arcane_table"),
                        Task.have("elysiumenchanting:patience_tome", 1)),
                quest("tome_three_more", "the_tomes", List.of("tome_patience"),
                        Task.have("elysiumenchanting:scrapping_tome", 1), Task.have("elysiumenchanting:transcription_tome", 1), Task.have("elysiumenchanting:concentration_tome", 1)),
                quest("tome_tower", "the_tomes", List.of("tome_patience"),
                        Task.read(), Task.have("#elysiumalmanac:enchanting_tomes", 2)),
                quest("tome_room", "the_tomes", List.of("tome_rectification", "tome_infusion", "tome_grand"),
                        Task.place("#elysiumalmanac:enchanting_shelves", 24)));
    }

    private static List<Quest> addedTheDig() {
        return List.of(
                quest("the_digging_table", "the_dig", List.of("broad_tool"),
                        Task.place("elysiumarsenal:archeology_table", 1), Task.have("#elysiumalmanac:arsenal_brushes", 1)),
                quest("brush_a_site", "the_dig", List.of("the_digging_table"),
                        Task.mine("#elysiumalmanac:arsenal_sites", 1), Task.have("elysiumarsenal:artifact_shard", 1)),
                quest("half_a_skeleton", "the_dig", List.of("brush_a_site"),
                        Task.have("#elysiumalmanac:arsenal_fossil_heads", 1), Task.have("#elysiumalmanac:arsenal_fossil_bodies", 1)),
                quest("a_whole_skeleton", "the_dig", List.of("half_a_skeleton"),
                        Task.place("#elysiumalmanac:arsenal_fossils_whole", 1)),
                quest("what_else_is_buried", "the_dig", List.of("brush_a_site"),
                        Task.have("elysiumarsenal:chiseled_bone_block", 1), Task.have("elysiumarsenal:music_disc_relic", 1)),
                quest("rotten_wood_cracked_brick", "the_dig", List.of("brush_a_site"),
                        Task.have("#elysiumalmanac:arsenal_rotten_wood", 1), Task.have("#elysiumalmanac:arsenal_mud_bricks", 1)),
                quest("totems_set_down", "the_dig", List.of("brush_a_site"),
                        Task.place("elysiumarsenal:growth_totem", 1), Task.place("elysiumarsenal:radiance_totem", 1)),
                quest("totems_spent", "the_dig", List.of("totems_set_down"),
                        Task.have("#elysiumalmanac:arsenal_held_totems", 1)),
                quest("an_unidentified_lump", "the_dig", List.of("brush_a_site"),
                        Task.craft("elysiumarsenal:unidentified_artifact", 1)),
                quest("what_it_turned_out_to_be", "the_dig", List.of("an_unidentified_lump"),
                        Task.have("#elysiumalmanac:arsenal_identified", 1)),
                quest("weaponsmiths_hammer", "the_dig", List.of("first_blade"),
                        Task.have("elysiumarsenal:weaponsmiths_hammer", 1)),
                quest("tools_and_armour_regraded", "the_dig", List.of("weaponsmiths_hammer"),
                        Task.have("elysiumarsenal:toolsmiths_hammer", 1), Task.have("elysiumarsenal:armourers_hammer", 1)),
                quest("shortbow_and_sling", "the_dig", List.of("first_blade"),
                        Task.have("#elysiumalmanac:arsenal_shortbows", 1), Task.have("#elysiumalmanac:arsenal_slings", 1), Task.have("#elysiumalmanac:arsenal_plain_arrows", 16)),
                quest("longbow_and_recurve", "the_dig", List.of("shortbow_and_sling"),
                        Task.have("#elysiumalmanac:arsenal_longbows", 1), Task.have("#elysiumalmanac:arsenal_recurves", 1), Task.craft("#elysiumalmanac:arsenal_effect_arrows", 4)),
                quest("repeater_and_crossbow", "the_dig", List.of("longbow_and_recurve"),
                        Task.have("#elysiumalmanac:arsenal_repeaters", 1), Task.have("#elysiumalmanac:arsenal_crossbows", 1), Task.have("#elysiumalmanac:arsenal_bolts", 8)),
                quest("a_netherite_brush", "the_dig", List.of("a_whole_skeleton", "what_it_turned_out_to_be", "totems_spent"),
                        Task.have("elysiumarsenal:netherite_brush", 1)));
    }

    private static List<Quest> addedTheCharms() {
        return List.of(
                quest("a_ring", "the_charms", List.of("first_blade"),
                        Task.have("#elysiumalmanac:trinkets_rings", 1)),
                quest("at_the_throat", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_necklaces", 1)),
                quest("at_the_waist", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_belts", 1)),
                quest("across_the_back", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_back", 1)),
                quest("carried", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_charms", 1)),
                quest("on_the_hands", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_hands", 1)),
                quest("on_the_head", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_head", 1)),
                quest("at_the_shoulder", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_companions", 1)),
                quest("found_not_made", "the_charms", List.of("a_ring"),
                        Task.have("#elysiumalmanac:trinkets_found", 1)),
                quest("a_potion_charm", "the_charms", List.of("carried"),
                        Task.craft("elysiumtrinkets:potion_charm", 1), Task.have("minecraft:potion", 1)),
                quest("a_slot_is_a_question", "the_charms", List.of("found_not_made", "at_the_throat", "at_the_waist", "across_the_back", "on_the_hands", "on_the_head", "at_the_shoulder", "a_potion_charm"),
                        Task.have("#elysiumalmanac:trinkets_found", 3)));
    }

    private static List<Quest> addedTheBands() {
        return List.of(
                quest("a_band_on_the_road", "the_bands", List.of("the_three_families"),
                        Task.slay("#elysiumalmanac:mobs_unsworn", 1)),
                quest("the_empires_own", "the_bands", List.of("a_band_on_the_road"),
                        Task.slay("#elysiumalmanac:mobs_imperial", 1)),
                quest("fast_and_frail", "the_bands", List.of("a_band_on_the_road"),
                        Task.slay("elysiummobs:scavenger", 2), Task.slay("elysiummobs:harrier", 2)),
                quest("heavy_and_unseen", "the_bands", List.of("fast_and_frail"),
                        Task.slay("elysiummobs:reaver", 1), Task.slay("elysiummobs:whisper", 1)),
                quest("equipment_and_office", "the_bands", List.of("the_empires_own"),
                        Task.slay("elysiummobs:drone", 2), Task.slay("elysiummobs:lictor", 1)),
                quest("doctrine_and_doorway", "the_bands", List.of("equipment_and_office"),
                        Task.slay("elysiummobs:adept", 1), Task.slay("elysiummobs:bulwark", 1)),
                quest("three_even_shares", "the_bands", List.of("heavy_and_unseen", "doctrine_and_doorway"),
                        Task.read(), Task.slay("#elysiumalmanac:mobs_all", 10)),
                quest("built_for_the_room", "the_bands", List.of("a_band_on_the_road"),
                        Task.level(10), Task.slay("#elysiumalmanac:mobs_all", 5)),
                quest("the_register", "the_bands", List.of("the_empires_own"),
                        Task.read()),
                quest("the_choir", "the_bands", List.of("three_even_shares"),
                        Task.slay("elysiummobs:choir", 1)),
                quest("the_praetor", "the_bands", List.of("three_even_shares", "the_register"),
                        Task.slay("elysiummobs:praetor", 1)),
                quest("both_sides_have_an_opinion", "the_bands", List.of("the_choir", "the_praetor", "built_for_the_room"),
                        Task.slay("#elysiumalmanac:mobs_all", 50)));
    }

    private static List<Quest> addedTheCages() {
        return List.of(
                quest("take_a_cage_apart", "the_cages", List.of("the_three_families"),
                        Task.mine("minecraft:spawner", 1)),
                quest("carry_a_cage", "the_cages", List.of("take_a_cage_apart"),
                        Task.have("elysiumspawners:spawner", 1)),
                quest("set_the_cage_down", "the_cages", List.of("carry_a_cage"),
                        Task.place("elysiumspawners:spawner", 1), Task.use("elysiumspawners:spawner")),
                quest("a_rune_on_it", "the_cages", List.of("set_the_cage_down"),
                        Task.craft("#elysiumalmanac:spawners_stat_runes", 1)),
                quest("faster_never_a_wall", "the_cages", List.of("a_rune_on_it"),
                        Task.have("elysiumspawners:rune_of_haste", 3), Task.have("elysiumspawners:rune_of_swarming", 3)),
                quest("six_behaviours", "the_cages", List.of("a_rune_on_it"),
                        Task.craft("#elysiumalmanac:spawners_behaviour_runes", 1)),
                quest("ask_the_spawner", "the_cages", List.of("set_the_cage_down"),
                        Task.read()),
                quest("an_unstable_one", "the_cages", List.of("take_a_cage_apart"),
                        Task.read()),
                quest("capturing", "the_cages", List.of("set_the_cage_down"),
                        Task.have("minecraft:zombie_spawn_egg", 1)),
                quest("a_cage_that_never_sleeps", "the_cages", List.of("faster_never_a_wall", "six_behaviours", "capturing"),
                        Task.have("elysiumspawners:rune_of_indifference", 1), Task.have("elysiumspawners:rune_of_haste", 12)));
    }

    private static List<Quest> addedTheEmpire() {
        return List.of(
                quest("empire_voidglass", "the_empire", List.of("ledger_first_level"),
                        Task.mine("elysium:voidglass_ore", 1), Task.have("elysium:voidglass_ingot", 4)),
                quest("empire_noticed", "the_empire", List.of("empire_voidglass"),
                        Task.mine("#c:ores", 24)),
                quest("empire_aetherium", "the_empire", List.of("empire_voidglass"),
                        Task.mine("elysium:aetherium_ore", 1), Task.have("elysium:aetherium_ingot", 4)),
                quest("empire_neutronium", "the_empire", List.of("empire_voidglass"),
                        Task.mine("elysium:neutronium_ore", 1), Task.have("elysium:neutronium_ingot", 4), Task.have("elysium:neutronium_block", 1)),
                quest("empire_ramp", "the_empire", List.of("ledger_first_level"),
                        Task.have("#elysiumalmanac:core_ramp_tools", 1), Task.have("#elysiumalmanac:core_ramp_armour", 1)),
                quest("empire_area_tools", "the_empire", List.of("empire_ramp"),
                        Task.have("#elysiumalmanac:core_area_tools", 1), Task.have("#elysiumalmanac:core_foreign_gear", 1)),
                quest("empire_imperial_gear", "the_empire", List.of("empire_aetherium", "empire_area_tools"),
                        Task.have("#elysiumalmanac:core_voidglass_gear", 1), Task.have("#elysiumalmanac:core_aetherium_gear", 1)),
                quest("empire_plate", "the_empire", List.of("empire_neutronium", "empire_imperial_gear"),
                        Task.have("#elysiumalmanac:core_neutronium_gear", 1), Task.have("#elysiumalmanac:core_neutronium_plate", 4), Task.have("elysium:voidweave_aegis", 1)),
                quest("empire_runes", "the_empire", List.of("empire_voidglass"),
                        Task.craft("#elysiumalmanac:core_elemental_runes", 1), Task.have("#elysiumalmanac:core_utility_runes", 1)),
                quest("empire_sockets", "the_empire", List.of("empire_runes", "empire_neutronium"),
                        Task.use("elysium:rune_socket_table"), Task.use("elysium:ascension_forge")),
                quest("empire_elemental_arms", "the_empire", List.of("empire_runes", "empire_neutronium"),
                        Task.have("#elysiumalmanac:core_elemental_blades", 1), Task.have("#elysiumalmanac:core_heavy_arms", 1)),
                quest("empire_cutting", "the_empire", List.of("empire_sockets", "ledger_gem_dust"),
                        Task.use("elysium:gem_cutting_table"), Task.have("#elysiumalmanac:core_sigils", 1), Task.use("elysium:augmenting_table")),
                quest("empire_reforge", "the_empire", List.of("empire_sockets"),
                        Task.use("elysium:reforge_table"), Task.have("elysium:elysium_reforge", 1), Task.have("#elysiumalmanac:core_convergence_templates", 1)),
                quest("empire_rations", "the_empire", List.of("ledger_first_level"),
                        Task.eat("#elysiumalmanac:core_rations", 1)),
                quest("empire_hazards", "the_empire", List.of("empire_rations"),
                        Task.read()),
                quest("empire_wounds", "the_empire", List.of("ledger_first_level"),
                        Task.read()),
                quest("empire_scanner", "the_empire", List.of("empire_runes", "empire_neutronium"),
                        Task.have("elysium:field_scanner", 1)),
                quest("empire_claim", "the_empire", List.of("empire_noticed", "empire_aetherium"),
                        Task.place("elysium:registry_beacon", 1), Task.use("elysium:registry_beacon")),
                quest("empire_archive", "the_empire", List.of("empire_noticed"),
                        Task.use("elysium:imperial_archive")),
                quest("empire_inspection", "the_empire", List.of("empire_noticed"),
                        Task.use("elysium:inspection_beacon")),
                quest("empire_enforcer", "the_empire", List.of("empire_noticed"),
                        Task.slay("elysium:imperial_enforcer", 1)),
                quest("empire_raider", "the_empire", List.of("empire_voidglass"),
                        Task.slay("elysium:unsworn_raider", 1)),
                quest("empire_regalia", "the_empire", List.of("empire_raider"),
                        Task.have("#elysiumalmanac:core_regalia", 1)),
                quest("empire_spawners", "the_empire", List.of("empire_enforcer", "empire_raider"),
                        Task.mine("#elysiumalmanac:core_rogue_spawners", 1), Task.mine("elysium:caged_invader", 1)),
                quest("empire_crown", "the_empire", List.of("empire_enforcer", "empire_plate"),
                        Task.have("elysium:emperor_crown", 1)));
    }

    private static List<Quest> addedTheCourt() {
        return List.of(
                quest("court_metal_benches", "the_court", List.of("ledger_declare"),
                        Task.place("elysiumnpcs:gear_bench", 1), Task.place("elysiumnpcs:salvage_rig", 1)),
                quest("court_signal_benches", "the_court", List.of("ledger_declare"),
                        Task.place("elysiumnpcs:signal_mast", 1), Task.place("elysiumnpcs:splicing_vat", 1)),
                quest("court_void_benches", "the_court", List.of("ledger_declare"),
                        Task.place("elysiumnpcs:void_crucible", 1), Task.place("elysiumnpcs:hydroponics_rack", 1)),
                quest("court_memory", "the_court", List.of("court_metal_benches", "court_signal_benches", "court_void_benches"),
                        Task.read()),
                quest("court_wanderer", "the_court", List.of("court_memory"),
                        Task.read()),
                quest("court_arrival", "the_court", List.of("empire_noticed"),
                        Task.read()),
                quest("court_offices", "the_court", List.of("court_arrival"),
                        Task.read()),
                quest("court_amnesty", "the_court", List.of("court_arrival"),
                        Task.have("elysiumnpcs:writ_of_amnesty", 1)),
                quest("court_commission", "the_court", List.of("court_offices", "court_amnesty"),
                        Task.read()));
    }

    private static List<Quest> addedTheTown() {
        return List.of(
                quest("town_survey", "the_town", List.of("welcome"),
                        Task.read()),
                quest("town_ruins", "the_town", List.of("town_survey"),
                        Task.read()),
                quest("town_charter", "the_town", List.of("town_survey"),
                        Task.read()),
                quest("town_found", "the_town", List.of("town_charter"),
                        Task.read()),
                quest("town_relief", "the_town", List.of("town_found"),
                        Task.read()),
                quest("town_exchange", "the_town", List.of("town_found"),
                        Task.read()),
                quest("town_command", "the_town", List.of("town_relief", "town_exchange"),
                        Task.read()),
                quest("town_anchor", "the_town", List.of("steel", "town_found"),
                        Task.place("elysiumcolonies:elevator_anchor", 1)),
                quest("town_tether", "the_town", List.of("town_anchor"),
                        Task.have("elysiumcolonies:elevator_tether", 64)),
                quest("town_counterweight", "the_town", List.of("town_tether"),
                        Task.place("elysiumcolonies:elevator_counterweight", 1)),
                quest("town_legs", "the_town", List.of("town_anchor"),
                        Task.place("elysiumcolonies:elevator_deck", 1), Task.place("elysiumcolonies:elevator_pylon", 1), Task.place("elysiumcolonies:elevator_collar", 1)),
                quest("town_spaceport", "the_town", List.of("town_counterweight", "town_legs", "town_command"),
                        Task.use("elysiumcolonies:elevator_anchor")));
    }

    private static List<Quest> addedTheRift() {
        return List.of(
                quest("rift_frame", "the_rift", List.of("first_blade"),
                        Task.have("elysiumdungeons:rift_frame", 10)),
                quest("rift_key", "the_rift", List.of("first_blade"),
                        Task.have("elysiumdungeons:rift_key", 1)),
                quest("rift_open", "the_rift", List.of("rift_frame", "rift_key"),
                        Task.place("elysiumdungeons:rift_frame", 10), Task.use("elysiumdungeons:rift_portal")),
                quest("rift_through", "the_rift", List.of("rift_open"),
                        Task.visit("elysiumdungeons:dungeon")),
                quest("rift_rooms", "the_rift", List.of("rift_through"),
                        Task.read()),
                quest("rift_denizens", "the_rift", List.of("rift_through"),
                        Task.slay("#elysiumalmanac:dungeons_denizens", 5)),
                quest("rift_loot", "the_rift", List.of("rift_rooms"),
                        Task.read(), Task.level(10)),
                quest("rift_boss", "the_rift", List.of("rift_denizens", "rift_loot"),
                        Task.slay("#elysiumalmanac:dungeons_bosses", 1)),
                quest("rift_leave", "the_rift", List.of("rift_through"),
                        Task.read()),
                quest("rift_second_door", "the_rift", List.of("rift_leave"),
                        Task.place("elysiumdungeons:rift_frame", 24), Task.use("elysiumdungeons:rift_portal")),
                quest("rift_cartography", "the_rift", List.of("rift_second_door", "rift_boss"),
                        Task.read(), Task.visit("elysiumdungeons:dungeon")));
    }

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
