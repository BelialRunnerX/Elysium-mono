package com.elysium.lib.network;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.research.ElysiumResearch;
import com.elysium.lib.research.ElysiumResearchField;
import com.elysium.lib.research.ElysiumResearchRank;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.stats.ElysiumStats;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * A character, flattened to one string and back.
 *
 * <pre>{@code   race|class|level|xp|xpNext|stats|favor|suspicion|research|catalogued   }</pre>
 *
 * Deliberately boring. The alternative — a fifteen-field packet — has to agree
 * on field order between two sides that ship independently, and gets a field
 * added every time a stat does. This format survives a stat being added (the
 * old client ignores what it does not know) and a stat being removed (the
 * value parses to a name nothing matches, and is dropped).
 *
 * It is a display snapshot and nothing more. Every value here is recomputed
 * server-side before it is ever acted on; nothing a client sends back is
 * trusted.
 */
public final class CharacterSheet {

    private CharacterSheet() {
    }

    private static final String FIELD = "\\|";
    private static final String PAIR = ",";

    public static String pack(Player player) {
        ElysiumRace race = ElysiumCharacter.getRace(player);
        ElysiumClass job = ElysiumCharacter.getElysiumClass(player);
        int level = ElysiumCharacter.getLevel(player);

        StringBuilder stats = new StringBuilder();
        ElysiumStatBlock total = ElysiumStats.total(player);
        for (ElysiumStat stat : ElysiumStat.REGISTRY.all()) {
            if (stats.length() > 0) {
                stats.append(PAIR);
            }
            stats.append(stat.getSerialisedName()).append('=').append(total.get(stat));
        }

        return String.join("|", java.util.List.of(
                race == null ? "" : race.getSerialisedName(),
                job == null ? "" : job.getSerialisedName(),
                Integer.toString(level),
                Integer.toString(ElysiumCharacter.getXp(player)),
                Integer.toString(ElysiumCharacter.xpToNext(level)),
                stats.toString(),
                // Favor and Suspicion ride along here rather than in a packet
                // of their own. They are data attachments, and an attachment is
                // server-side unless it is explicitly synced — a client that
                // asked the local player for its Favor would get the default,
                // zero, and draw a confident zero on a meter really sitting at
                // eighty. Two more fields on a string that already travels
                // whenever anything changes is the cheap answer.
                Integer.toString(ElysiumStanding.getFavor(player)),
                Integer.toString(ElysiumStanding.getSuspicion(player)),
                // Fields 8 and 9, added with the research tracks. Appending is
                // exactly what this format is for: a client on an older build
                // reads seven fields and ignores the rest, and a client on a
                // newer one reads zero research from an older server rather
                // than failing to parse the sheet at all.
                research(player),
                Integer.toString(com.elysium.lib.discovery.ElysiumDiscoveries
                        .catalogued(player)),
                // Fields 10 to 12, the Ledger Web: what is held (starts included),
                // the points free, and the erasures in the bag — read the same
                // forgiving way as everything before them.
                String.join(PAIR, com.elysium.lib.tree.ElysiumTreeState.held(player)),
                Integer.toString(com.elysium.lib.tree.ElysiumTreeState.free(player)),
                Integer.toString(com.elysium.lib.tree.ElysiumTreeState.erasures(player))));
    }

    /** The six tracks, packed the same way the stats are. */
    private static String research(Player player) {
        StringBuilder out = new StringBuilder();
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            if (out.length() > 0) {
                out.append(PAIR);
            }
            out.append(field.id()).append('=')
                    .append(ElysiumResearch.points(player, field));
        }
        return out.toString();
    }

    /** The client-side view of a packed sheet. */
    public record Parsed(ElysiumRace race,
                         ElysiumClass job,
                         int level,
                         int xp,
                         int xpNext,
                         Map<ElysiumStat, Integer> stats,
                         int favor,
                         int suspicion,
                         Map<ElysiumResearchField, Integer> research,
                         int catalogued,
                         java.util.List<String> held,
                         int treeFree,
                         int erasures) {

        /** Whether a node of the Ledger Web is held, as the server last said. */
        public boolean holds(String node) {
            return held.contains(node);
        }

        public int points(ElysiumResearchField field) {
            Integer held = research.get(field);
            return held == null ? 0 : held;
        }

        public ElysiumResearchRank rank(ElysiumResearchField field) {
            return ElysiumResearchRank.of(points(field));
        }

        /**
         * Whether this player has earned an unlock, as far as the client knows.
         *
         * For drawing only. Every place that acts on an unlock asks
         * {@code ElysiumResearch.has} on the server, because a client that told
         * the server what it had researched would be a client that decides what
         * it has researched.
         */
        public boolean has(com.elysium.lib.research.ElysiumUnlock unlock) {
            return unlock.metBy(points(unlock.field()));
        }

        public int get(ElysiumStat stat) {
            Integer value = stats.get(stat);
            return value == null ? 0 : value;
        }

        public boolean chosen() {
            return race != null && job != null;
        }

        /** How far along the bar the level is, for the header. */
        public float levelProgress() {
            return xpNext <= 0 ? 0.0F : Math.min(1.0F, (float) xp / (float) xpNext);
        }
    }

    /** The sheet a client draws before the server has told it anything. */
    public static Parsed empty() {
        return new Parsed(null, null, 1, 0, 1, new java.util.LinkedHashMap<>(), 0, 0,
                new java.util.LinkedHashMap<>(), 0, java.util.List.of(), 0, 0);
    }

    /**
     * Parses a packed sheet.
     *
     * Tolerant on purpose: a short or malformed string yields an empty sheet
     * rather than an exception, because the one thing worse than a wrong
     * character screen is a client that disconnects trying to draw one.
     */
    public static Parsed parse(String packed) {
        Map<ElysiumStat, Integer> stats = new java.util.LinkedHashMap<>();
        if (packed == null || packed.isEmpty()) {
            return empty();
        }

        String[] fields = packed.split(FIELD, -1);
        ElysiumRace race = fields.length > 0 ? ElysiumRace.REGISTRY.get(fields[0]) : null;
        ElysiumClass job = fields.length > 1 ? ElysiumClass.REGISTRY.get(fields[1]) : null;
        int level = fields.length > 2 ? parseInt(fields[2], 1) : 1;
        int xp = fields.length > 3 ? parseInt(fields[3], 0) : 0;
        int xpNext = fields.length > 4 ? parseInt(fields[4], 1) : 1;

        if (fields.length > 5 && !fields[5].isEmpty()) {
            for (String entry : fields[5].split(PAIR)) {
                // '=' rather than ':' — every stat id now contains a colon of
                // its own, and splitting on that took "elysiumlib" for a name.
                int equals = entry.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                ElysiumStat stat = ElysiumStat.REGISTRY.get(entry.substring(0, equals));
                if (stat != null) {
                    stats.put(stat, parseInt(entry.substring(equals + 1), 0));
                }
            }
        }

        // Fields 6 and 7 arrived after the format did. A sheet packed by an
        // older build simply does not have them, and reads as standing zero
        // rather than as a parse failure — which is the whole reason this is a
        // delimited string and not a fixed-width packet.
        int favor = fields.length > 6 ? parseInt(fields[6], 0) : 0;
        int suspicion = fields.length > 7 ? parseInt(fields[7], 0) : 0;

        // Fields 8 and 9, added with the research tracks, and read the same
        // forgiving way: a sheet from a server that predates them is a player
        // with no research rather than a screen that will not draw.
        Map<ElysiumResearchField, Integer> research = new java.util.LinkedHashMap<>();
        if (fields.length > 8 && !fields[8].isEmpty()) {
            for (String entry : fields[8].split(PAIR)) {
                int equals = entry.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                ElysiumResearchField field =
                        ElysiumResearchField.byId(entry.substring(0, equals));
                if (field != null) {
                    research.put(field, parseInt(entry.substring(equals + 1), 0));
                }
            }
        }
        int catalogued = fields.length > 9 ? parseInt(fields[9], 0) : 0;
        java.util.List<String> held = new java.util.ArrayList<>();
        if (fields.length > 10 && !fields[10].isEmpty()) {
            for (String id : fields[10].split(PAIR)) {
                if (!id.isEmpty()) {
                    held.add(id);
                }
            }
        }
        int treeFree = fields.length > 11 ? parseInt(fields[11], 0) : 0;
        int erasures = fields.length > 12 ? parseInt(fields[12], 0) : 0;

        return new Parsed(race, job, level, xp, Math.max(1, xpNext), stats,
                favor, suspicion, research, catalogued, held, treeFree, erasures);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
