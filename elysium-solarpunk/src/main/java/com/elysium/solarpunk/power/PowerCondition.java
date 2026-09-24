package com.elysium.solarpunk.power;

/**
 * When a rule is in force. The whole of the language's left-hand side.
 *
 * <h2>Eight, and deliberately not nine</h2>
 *
 * This is the finite vocabulary §9.5 asks for. Every one of these is a fact a
 * player can see for themselves without opening a screen — whether it is dark,
 * whether it is raining, roughly how full their batteries are — because a
 * condition a player cannot verify is a rule they cannot debug. "Storage above
 * eighty percent" is checkable by looking at the machines; "the refinery has
 * been idle for four minutes" is not, and is the sort of thing that turns an
 * automation system into a mystery.
 *
 * The charge thresholds are quarters rather than an arbitrary number the player
 * types. A number needs a screen to enter, a screen needs a menu and a packet,
 * and the difference between 78% and 80% has never been the thing standing
 * between somebody and a working base.
 *
 * <h2>No Minecraft here</h2>
 *
 * Conditions read a {@link GridState} and nothing else, so the harness can run
 * every condition against every state without a world. That is also why this is
 * not a {@code StringRepresentable} enum backed by a blockstate property — the
 * block stores an index instead. See {@code PowerGovernorBlock}.
 */
public enum PowerCondition {

    /** Always in force. A permanent hold — the base's main switch. */
    ALWAYS("always"),

    /** While the sun is down. */
    NIGHT("night"),

    /** While the sun is up. */
    DAY("day"),

    /** While it is raining or storming — when the condensers are worth least. */
    RAINING("raining"),

    /** While the sky is clear. */
    CLEAR("clear"),

    /** While the grid's buffers are under a quarter full. */
    CHARGE_UNDER_QUARTER("charge_under_quarter"),

    /** While they are under half. */
    CHARGE_UNDER_HALF("charge_under_half"),

    /** While they are over three quarters — the surplus case. */
    CHARGE_OVER_THREE_QUARTERS("charge_over_three_quarters");

    private final String id;

    PowerCondition(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "elysiumsolarpunk.power.condition." + id;
    }

    /** Whether this condition is in force right now. */
    public boolean holds(GridState state) {
        return switch (this) {
            case ALWAYS -> true;
            case NIGHT -> !state.daylight();
            case DAY -> state.daylight();
            case RAINING -> state.raining();
            case CLEAR -> !state.raining();
            case CHARGE_UNDER_QUARTER -> state.charge() < 0.25F;
            case CHARGE_UNDER_HALF -> state.charge() < 0.5F;
            case CHARGE_OVER_THREE_QUARTERS -> state.charge() > 0.75F;
        };
    }

    /** By index, wrapping. What the block's property stores. */
    public static PowerCondition byIndex(int index) {
        PowerCondition[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
