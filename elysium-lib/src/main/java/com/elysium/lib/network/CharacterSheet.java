package com.elysium.lib.network;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.stats.ElysiumStats;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * A character, flattened to one string and back.
 *
 * <pre>{@code   race|class|level|xp|xpNext|stat=value,stat=value,...|favor|suspicion   }</pre>
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
                Integer.toString(ElysiumStanding.getSuspicion(player))));
    }

    /** The client-side view of a packed sheet. */
    public record Parsed(ElysiumRace race,
                         ElysiumClass job,
                         int level,
                         int xp,
                         int xpNext,
                         Map<ElysiumStat, Integer> stats,
                         int favor,
                         int suspicion) {

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
        return new Parsed(null, null, 1, 0, 1, new java.util.LinkedHashMap<>(), 0, 0);
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

        return new Parsed(race, job, level, xp, Math.max(1, xpNext), stats, favor, suspicion);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
