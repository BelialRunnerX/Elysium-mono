package com.elysium.colonies.rules;

import java.util.List;
import java.util.Map;

/**
 * Where towns found themselves, how they are called, and how fast they build.
 *
 * <h2>Siting</h2>
 *
 * A settlement is a structure the world places: one site in every square of
 * {@link #SPACING} chunks, never two closer than {@link #SEPARATION} chunks,
 * and none within {@link #FROM_SPAWN} chunks of the world spawn, which is the
 * ground a new player is going to want for their own. The numbers are the
 * structure set's, read from here by the generator so the two cannot drift.
 *
 * <h2>Building</h2>
 *
 * A town nobody plays supplies nobody's requests, so it does not wait for a
 * builder with planks: it completes an order the way a creative player would,
 * whole, after {@link #buildDays(int)} days, and it founds its next building
 * every {@link #FOUND_EVERY} days rather than saving development for it. Both
 * are slower than a player who is paying attention, and faster than one who
 * is not, which is what a living background ought to be.
 */
public final class SettleRules {

    private SettleRules() {
    }

    /** One settlement site per square this many chunks across. */
    public static final int SPACING = 40;

    /** Two sites are never closer than this many chunks. */
    public static final int SEPARATION = 16;

    /** The structure set's salt: fixed, so the same seed gives the same sites. */
    public static final int SALT = 94_771_205;

    /** Chunks around the world spawn where nobody settles. */
    public static final int FROM_SPAWN = 24;

    /** The pack a world without a style of its own builds in. */
    public static final String DEFAULT_PACK = "Medieval Oak";

    /** Days between one building a natural town lays out and the next. */
    public static final int FOUND_EVERY = 2;

    /** MineColonies shows a colony name in a fixed box; longer names are cut. */
    public static final int NAME_LIMIT = 24;

    /** Whether a site this far from spawn, in chunks, may be settled. */
    public static boolean farEnoughFromSpawn(int chunksX, int chunksZ) {
        return Math.max(Math.abs(chunksX), Math.abs(chunksZ)) >= FROM_SPAWN;
    }

    /**
     * Days a natural town takes to complete an order to this level.
     *
     * Level one goes up in a day, level two in two, and so on: a bigger
     * building takes longer, and a town nobody watches still visibly changes
     * from one visit to the next.
     */
    public static int buildDays(int targetLevel) {
        return Math.max(1, targetLevel);
    }

    /** Whether enough days have passed since the last order finished to file another. */
    public static boolean mayFound(int day, int lastFinished) {
        return day - lastFinished >= FOUND_EVERY;
    }

    /** Whether an order filed on {@code filedDay} is finished on {@code day}. */
    public static boolean finished(int day, int filedDay, int targetLevel) {
        return day - filedDay >= buildDays(targetLevel);
    }

    // --- names ------------------------------------------------------------

    private static final List<String> PLAIN_FIRST = List.of(
            "Ash", "Elm", "Fen", "Ford", "Mill", "Oak", "Reed", "Thorn", "Wick", "Wold",
            "Barrow", "Hollow", "Bram", "Cold", "Dun", "Hare", "Kings", "Lark", "Moss", "Stone");
    private static final List<String> PLAIN_SECOND = List.of(
            "ford", "stead", "wick", "bridge", "haven", "field", "moor", "gate", "hollow",
            "reach", "well", "market", "cross", "dale", "worth");

    private static final Map<String, List<String>> STYLED_FIRST = Map.of(
            "Bastion", List.of("Emerald", "Obsidian", "Verdant", "Onyx", "Jade", "Gloam",
                    "Basalt", "Sable", "Viridian", "Ember"),
            "Pale Reach", List.of("Regolith", "Umbra", "Sere", "Lunar", "Grey", "Still",
                    "Mare", "Crater", "Dust", "Silent"),
            "Cinderhold", List.of("Cinder", "Ash", "Scoria", "Ember", "Brim", "Kiln",
                    "Slag", "Char", "Furnace", "Smoulder"),
            "Frostfall", List.of("Rime", "Hoar", "Sleet", "Glacier", "Frost", "Whiteout",
                    "Icefall", "Winter", "Bleak", "Shiver"),
            "High Orbit", List.of("Apogee", "Zenith", "Lagrange", "Kepler", "Halo", "Orbit",
                    "Perigee", "Vector", "Beacon", "Relay"));
    private static final Map<String, List<String>> STYLED_SECOND = Map.of(
            "Bastion", List.of("Bastion", "Ward", "Keep", "Spire", "Gate", "Reach", "Watch",
                    "Court"),
            "Pale Reach", List.of("Station", "Dome", "Outpost", "Shelf", "Landing", "Hold",
                    "Berth", "Vault"),
            "Cinderhold", List.of("hold", "forge", "gate", "works", "reach", "vent", "hearth"),
            "Frostfall", List.of("hold", "fall", "reach", "camp", "watch", "refuge", "cairn"),
            "High Orbit", List.of("Station", "Ring", "Dock", "Platform", "Anchor", "Spar",
                    "Array"));

    /** Whether a style has a naming of its own; the rest are named plainly. */
    public static boolean hasNaming(String style) {
        return style != null && STYLED_FIRST.containsKey(style);
    }

    /**
     * What a settlement is called, from its seed and its world's style.
     *
     * Deterministic: the same seed and style always give the same name, so a
     * town keeps its name across a reload and two players agree on it.
     * Styled worlds join their words with a space ("Emerald Ward"); the plain
     * world runs them together ("Ashford"), as English place names do.
     */
    public static String name(long seed, String style) {
        long mixed = mix(seed ^ (style == null ? 0L : style.hashCode() * 0x9E37_79B9L));
        List<String> first = hasNaming(style) ? STYLED_FIRST.get(style) : PLAIN_FIRST;
        List<String> second = hasNaming(style) ? STYLED_SECOND.get(style) : PLAIN_SECOND;
        String one = first.get((int) Math.floorMod(mixed, first.size()));
        String two = second.get((int) Math.floorMod(mixed >>> 20, second.size()));
        if (!hasNaming(style)) {
            return trim(one + two);
        }
        return trim(one + " " + two);
    }

    private static String trim(String name) {
        return name.length() <= NAME_LIMIT ? name : name.substring(0, NAME_LIMIT);
    }

    /** A 64-bit mixer (splitmix64's finaliser), so nearby seeds do not give nearby names. */
    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return value ^ (value >>> 31);
    }

    /** How many distinct names a style can give. */
    public static int namesIn(String style) {
        List<String> first = hasNaming(style) ? STYLED_FIRST.get(style) : PLAIN_FIRST;
        List<String> second = hasNaming(style) ? STYLED_SECOND.get(style) : PLAIN_SECOND;
        return first.size() * second.size();
    }
}
