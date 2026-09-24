package com.elysium.solarpunk.power;

/**
 * What a machine is <em>for</em>, so that a short supply can be spent on the
 * things that matter first.
 *
 * <h2>Why a class and not a number</h2>
 *
 * A per-machine priority number is the obvious design and it is the one that
 * rots. Every new machine has to be given a number relative to every machine
 * that already exists, the numbers drift as machines are added, and a player
 * looking at a stalled base cannot say why one thing ran and another did not —
 * the answer is "47 beats 46", which is not a reason.
 *
 * Five named classes are a reason. Water for a base is critical; a loom is
 * industry; a loom stops so the water does not. A player can predict that
 * before building, which is the whole point of a priority system — it is a
 * promise about what happens under pressure, and a promise nobody can state is
 * not one.
 *
 * <h2>The ranks are the spec's, in the spec's order</h2>
 *
 * Part 9.3 names them Critical, Defense, Logistics, Industry, Comfort. Rank is
 * the ordinal and is used directly, so the declaration order in this file
 * <em>is</em> the priority order and there is no second table to disagree with
 * it.
 *
 * <h2>Defense is empty today, and that is deliberate</h2>
 *
 * No solarpunk machine defends anything yet; sieges are D3 and have not been
 * built. The class ships anyway because the ordering is the part that has to be
 * settled before there is anything to order — inserting a band between Critical
 * and Logistics later would silently re-rank every base a player had already
 * tuned. An empty band costs one enum constant and a line of documentation.
 *
 * <h2>No Minecraft here</h2>
 *
 * This file imports nothing. That is what lets the harness execute the
 * allocation in {@link PowerBudget} without a world, a registry or a server,
 * and it is why the class-and-rank decision lives here rather than on the block
 * entity that happens to use it.
 */
public enum PowerClass {

    /** Life support: water, air, food. The last thing to lose power. */
    CRITICAL("critical"),

    /** Wards, turrets and whatever answers a siege. Unoccupied until D3. */
    DEFENSE("defense"),

    /** Moving things: harvesters, conveyors, sorters. */
    LOGISTICS("logistics"),

    /** Turning things into other things. Most machines are this. */
    INDUSTRY("industry"),

    /** Light, warmth and ornament. The first thing to lose power. */
    COMFORT("comfort");

    /**
     * What an unlabelled receiver is treated as.
     *
     * A machine from another mod on an Elysium grid has no class and cannot be
     * given one. Industry is the honest guess: it is what most machines are,
     * and it puts a foreign machine neither above a base's water supply nor
     * below its lamps. Ranking foreign machines last would have been the easy
     * choice and would have made this mod's conduit quietly hostile to every
     * other mod in the pack.
     */
    public static final PowerClass UNLABELLED = INDUSTRY;

    private final String id;

    PowerClass(String id) {
        this.id = id;
    }

    /** The lower the earlier it is served. Critical is 0. */
    public int rank() {
        return ordinal();
    }

    /** Lower-case, stable, and what the translation key is built from. */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "elysiumsolarpunk.power.class." + id;
    }

    /** Whether this class is served before {@code other}. */
    public boolean outranks(PowerClass other) {
        return rank() < other.rank();
    }
}
