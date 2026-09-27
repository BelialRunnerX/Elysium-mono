package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eleven creatures whose point is doing something a block would otherwise do.
 *
 * <h2>The third kind</h2>
 *
 * {@link BeastRules} is a ladder and {@link MythRules} is eight ways past
 * armour. This is neither: a glare tells you where the dark is, a crab lets you
 * reach further, a tuff golem holds a thing, a moobloom is a flower that walks.
 * None of them is a fight. Every one of them is a <em>block's job</em>, done by
 * something alive.
 *
 * <h2>Which is exactly the danger</h2>
 *
 * A creature that does a block's job better than the block kills the block. A
 * glare that marks spawnable ground more reliably than a torch is the end of
 * torches; a golem that holds more than a chest is the end of chests; a crab
 * that reaches further than scaffolding is the end of building upwards. And
 * none of that shows up in a screenshot — the pack simply gets smaller, one
 * category at a time, and by the time anybody notices nobody can say when.
 *
 * <p>So every one of the eleven names the thing it competes with, and
 * {@link #faultsInCost} holds every one of them to costing something the block
 * does not: it has to be tamed, or fed, or stood near, or all three. A block
 * sits there. That is the block's whole advantage and it is not allowed to be
 * taken away.
 *
 * <h2>And nothing gives for ever</h2>
 *
 * {@link #faultsInFading} holds what a companion gives to running out after it
 * stops giving it. A gift that outlasts the creature is a stat a player keeps,
 * and a stat a player keeps is not a companion — it is an upgrade with legs,
 * and they will kill it for the upgrade the moment they work that out.
 */
public final class CompanionRules {

    private CompanionRules() {
    }

    // ==================================================================
    // What they do for you
    // ==================================================================

    /** It shows you where the dark is. */
    public static final String SHOWS_DARK = "shows_dark";

    /** You can reach further. */
    public static final String REACH = "reach";

    /** It holds one thing where you put it. */
    public static final String HOLDS = "holds";

    /** It presses what you tell it to press. */
    public static final String PRESSES = "presses";

    /** Flowers, without a farm. */
    public static final String BLOOMS = "blooms";

    /** It finds what is buried. */
    public static final String DIGS = "digs";

    /** It hides, and finding it three times pays. */
    public static final String HIDES = "hides";

    /** It drops cold on whatever you are fighting. */
    public static final String CHILLS = "chills";

    /** There appear to be several of it. */
    public static final String DOUBLES = "doubles";

    /** It bites what bites you. */
    public static final String BITES = "bites";

    /** It burns what stands too close to it. */
    public static final String BURNS = "burns";

    /** The eleven, in the order everything walks them. */
    public static final List<String> GIFTS = List.of(
            SHOWS_DARK, REACH, HOLDS, PRESSES, BLOOMS, DIGS, HIDES, CHILLS,
            DOUBLES, BITES, BURNS);

    /**
     * One creature that does a block's job.
     *
     * @param id     what it is called
     * @param gift   the one thing it does
     * @param rivals the block or item whose job that is, named so the pack can
     *               be asked whether it really makes one
     * @param tamed  whether it has to be earned before it does anything
     * @param upkeep how many items a day keeping one costs; 0 for none
     * @param reach  how near you have to be, in blocks
     * @param fades  how long the gift lasts after it stops, in ticks
     * @param weight how often it is chosen when something spawns
     */
    public record Companion(String id, String gift, String rivals, boolean tamed,
                            int upkeep, double reach, int fades, int weight) {

        public Companion {
            if (reach <= 0.0D) {
                throw new IllegalArgumentException(id + " has to be within "
                        + reach + " blocks, which is nowhere, so it could never "
                        + "do anything for anybody");
            }
            if (upkeep < 0 || fades < 0) {
                throw new IllegalArgumentException(id + " costs " + upkeep
                        + " a day and fades in " + fades + ", and neither is an "
                        + "amount of anything");
            }
            if (weight < 1) {
                throw new IllegalArgumentException(id + " is chosen " + weight
                        + " times when something spawns, so it is a creature "
                        + "nobody meets");
            }
            if (rivals == null || rivals.isEmpty()) {
                throw new IllegalArgumentException(id + " competes with nothing, "
                        + "so there is nothing to hold it under and it can "
                        + "quietly replace whatever it likes");
            }
        }

        /** Whether keeping one costs anything at all. */
        public boolean costsSomething() {
            return tamed || upkeep > 0 || reach <= MOST_REACH;
        }
    }

    /** The furthest a companion may work from, in blocks. */
    public static final double MOST_REACH = 24.0D;

    /** And the nearest, because something you must stand on is not a companion. */
    public static final double LEAST_REACH = 2.0D;

    /** The longest a gift may outlast the creature, in ticks. */
    public static final int LONGEST_FADE = 200;

    /** The rarest one may be and still be met. */
    public static final int LEAST_WEIGHT = 6;

    /**
     * The eleven.
     *
     * <p>Every one of them costs something a block does not. A block sits
     * there; that is its whole advantage, and none of these is allowed to take
     * it away.
     */
    public static final List<Companion> COMPANIONS = List.of(
            new Companion("lumen", SHOWS_DARK, "minecraft:torch", true, 1, 8.0D,
                    60, 30),
            new Companion("tether", REACH, "minecraft:scaffolding", false, 0, 6.0D,
                    40, 40),
            new Companion("holdfast", HOLDS, "minecraft:item_frame", false, 0,
                    4.0D, 0, 14),
            new Companion("tapper", PRESSES, "minecraft:lever", false, 0,
                    12.0D, 0, 12),
            new Companion("seeder", BLOOMS, "minecraft:flower_pot", false, 0,
                    5.0D, 0, 26),
            new Companion("borer", DIGS, "minecraft:iron_shovel", true, 2, 6.0D,
                    0, 18),
            new Companion("stasher", HIDES, "minecraft:chest", false, 0, 16.0D, 0,
                    10),
            new Companion("chiller", CHILLS, "minecraft:powder_snow_bucket",
                    false, 0, 20.0D, 100, 8),
            new Companion("doubler", DOUBLES, "minecraft:potion", false, 0,
                    18.0D, 120, 8),
            new Companion("snapper", BITES, "minecraft:wolf_spawn_egg", true, 3,
                    10.0D, 0, 22),
            new Companion("igniter", BURNS, "minecraft:campfire", false, 0,
                    14.0D, 80, 7));

    /** The companion of this name, or null. */
    public static Companion companion(String id) {
        for (Companion one : COMPANIONS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the companions. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(COMPANIONS));
        bad.addAll(faultsInGifts(COMPANIONS, GIFTS));
        bad.addAll(faultsInGiftNames(COMPANIONS));
        bad.addAll(faultsInGaps(COMPANIONS, GIFTS));
        bad.addAll(faultsInCost(COMPANIONS, MOST_REACH));
        bad.addAll(faultsInReach(COMPANIONS, LEAST_REACH, MOST_REACH));
        bad.addAll(faultsInFading(COMPANIONS, LONGEST_FADE));
        bad.addAll(faultsInRarity(COMPANIONS, LEAST_WEIGHT));
        bad.addAll(faultsInUpkeep(COMPANIONS));
        return List.copyOf(bad);
    }

    /** Whether two companions are written under one name. */
    public static List<String> faultsInNames(List<Companion> companions) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Companion one : companions) {
            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows is "
                        + "a set of numbers nothing reads");
            }
        }
        return bad;
    }

    /** Whether every companion does one of the things this module knows. */
    public static List<String> faultsInGifts(List<Companion> companions,
                                             List<String> gifts) {
        List<String> bad = new ArrayList<>();
        for (Companion one : companions) {
            if (!gifts.contains(one.gift())) {
                bad.add(one.id() + " does " + one.gift() + ", which is not one "
                        + "of the things this module has, so nothing anywhere "
                        + "makes it happen");
            }
        }
        return bad;
    }

    /**
     * Whether two companions do the same thing.
     *
     * <p>Two creatures with one job are one creature with two models: whichever
     * is easier to keep is the one everybody keeps, and the other is a mob that
     * exists so the list is longer.
     */
    public static List<String> faultsInGiftNames(List<Companion> companions) {
        List<String> bad = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (Companion one : companions) {
            if (!done.add(one.gift())) {
                bad.add(one.id() + " does " + one.gift() + " and so does "
                        + "something else, so whichever is easier to keep is the "
                        + "one everybody keeps and the other is a longer list");
            }
        }
        return bad;
    }

    /** Whether a thing this module knows how to do has nothing that does it. */
    public static List<String> faultsInGaps(List<Companion> companions,
                                            List<String> gifts) {
        List<String> bad = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (Companion one : companions) {
            done.add(one.gift());
        }
        for (String gift : gifts) {
            if (!done.contains(gift)) {
                bad.add("nothing does " + gift + ", so a thing this module knows "
                        + "how to do is written down and never happens");
            }
        }
        return bad;
    }

    /**
     * Whether anything here does a block's job for free.
     *
     * <p>The rule this file exists for. A block sits there, and that is the
     * whole of what a block has over something alive. A companion that has to
     * be neither tamed nor fed nor stood near is strictly better than the thing
     * it replaces, and the thing it replaces stops being built — quietly, one
     * category at a time, with nothing in the pack saying when.
     */
    public static List<String> faultsInCost(List<Companion> companions,
                                            double mostReach) {
        List<String> bad = new ArrayList<>();
        for (Companion one : companions) {
            boolean earned = one.tamed();
            boolean fed = one.upkeep() > 0;
            boolean near = one.reach() <= mostReach;
            if (!earned && !fed && !near) {
                bad.add(one.id() + " does the job of " + one.rivals()
                        + " and costs nothing a block does not — it is neither "
                        + "tamed nor fed nor kept close — so nobody builds one "
                        + "of those again and nothing says when that happened");
            }
        }
        return bad;
    }

    /** Whether anything works from nowhere, or only underfoot. */
    public static List<String> faultsInReach(List<Companion> companions,
                                             double least, double most) {
        List<String> bad = new ArrayList<>();
        for (Companion one : companions) {
            if (one.reach() < least) {
                bad.add(one.id() + " works from " + one.reach() + " blocks, "
                        + "which is close enough to stand on, so keeping one is "
                        + "a thing you do instead of playing");
            }
            if (one.reach() > most) {
                bad.add(one.id() + " works from " + one.reach() + " blocks, past "
                        + "the " + most + " this module allows, so it is a "
                        + "permanent effect with a mob somewhere behind it");
            }
        }
        return bad;
    }

    /**
     * Whether a gift outlasts the thing that gave it.
     *
     * <p>A gift that lingers is fine and a gift that stays is not: a stat a
     * player keeps is not a companion, it is an upgrade with legs, and they
     * will kill it for the upgrade the moment they work that out.
     */
    public static List<String> faultsInFading(List<Companion> companions,
                                              int longest) {
        List<String> bad = new ArrayList<>();
        for (Companion one : companions) {
            if (one.fades() > longest) {
                bad.add(one.id() + " goes on giving for " + one.fades()
                        + " ticks after it has stopped, past the " + longest
                        + " this module allows — so what it gives is a stat a "
                        + "player keeps, and they will kill it for that");
            }
        }
        return bad;
    }

    /** Whether anything here is so rare a player only reads about it. */
    public static List<String> faultsInRarity(List<Companion> companions,
                                              int least) {
        List<String> bad = new ArrayList<>();
        for (Companion one : companions) {
            if (one.weight() < least) {
                bad.add(one.id() + " is chosen " + one.weight() + " times where "
                        + least + " is the fewest this module allows, so it is a "
                        + "creature a player reads about and never meets");
            }
        }
        return bad;
    }

    /**
     * Whether something that must be fed is also something that must be tamed.
     *
     * <p>Feeding a creature that will wander off is a cost with nothing at the
     * end of it. Either it is yours and you keep it, or it is wild and it costs
     * you nothing but standing near it.
     */
    public static List<String> faultsInUpkeep(List<Companion> companions) {
        List<String> bad = new ArrayList<>();
        for (Companion one : companions) {
            if (one.upkeep() > 0 && !one.tamed() && one.reach() > LEAST_REACH * 2) {
                bad.add(one.id() + " costs " + one.upkeep() + " a day and is "
                        + "neither tamed nor kept underfoot, so a player feeds "
                        + "something that is free to wander off with what they "
                        + "fed it");
            }
        }
        return bad;
    }
}
